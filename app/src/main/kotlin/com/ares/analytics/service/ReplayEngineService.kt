package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Operational play/pause state of the telemetry replay engine.
 */
enum class ReplayState {
    PLAYING, PAUSED, STOPPED
}

/**
 * Telemetry log replay frame container holding mapped topic values at a timestamp offset.
 *
 * @property timestampMs Log timestamp in milliseconds ($ms$).
 * @property values Key-value map binding NT4 topic names to numeric telemetry values.
 * @property stringValues String payloads for topics whose source frame carried text.
 */
data class ReplayFrame(
    val timestampMs: Long,
    val values: Map<String, Double>,
    val stringValues: Map<String, String> = emptyMap()
)

fun interface ReplayClock {
    fun nowMs(): Long
}

object SystemReplayClock : ReplayClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

internal suspend fun loadTelemetryWindowPages(
    databaseService: DatabaseService,
    sessionId: String,
    startMs: Long,
    endMs: Long,
    pageSize: Int
): List<TelemetryFrame> {
    require(pageSize > 0)
    val frames = ArrayList<TelemetryFrame>()
    var offset = 0L
    do {
        val page = databaseService.getTelemetryRangeBatched(
            sessionId,
            startMs,
            endMs,
            limit = pageSize.toLong(),
            offset = offset
        )
        frames.addAll(page)
        offset += page.size
    } while (page.size == pageSize)
    return frames
}

data class ReplayCacheMetrics(
    val windowStartMs: Long = -1,
    val windowEndMs: Long = -1,
    val cachedFrames: Int = 0,
    val hasPrefetchedWindow: Boolean = false,
    val windowLoads: Long = 0,
    val prefetchHits: Long = 0,
    val truncatedWindows: Long = 0,
    val droppedEmissionFrames: Long = 0
)

/**
 * High-performance match log replay and frame scrubbing engine.
 *
 * Replays historical DuckDB match telemetry records at configurable playback speed ratios ($0.25\times, 0.5\times, 1.0\times, 2.0\times, 4.0\times$),
 * streaming frame updates directly into [Nt4ClientService.emitReplayFrame] and optional UDP broadcast sockets (port 5810).
 *
 * ### Thread Safety & Performance Guarantees:
 * Runs replay timing loops asynchronously within [CoroutineScope] on `Dispatchers.Default`. Frame scrubbing executes seeking without database table scans.
 *
 * @param databaseService Primary DuckDB telemetry database service.
 * @param nt4ClientService Active NT4 client receiving injected replay telemetry frames.
 *
 * @see Nt4ClientService
 * @see ReplayState
 * @see ReplayFrame
 */
class ReplayEngineService(
    private val databaseService: DatabaseService,
    private val nt4ClientService: Nt4ClientService? = null,
    private val clock: ReplayClock = SystemReplayClock,
    replayDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ReplayState.STOPPED)
    val state: StateFlow<ReplayState> = _state.asStateFlow()

    private val _currentFrame = MutableStateFlow<ReplayFrame?>(null)
    val currentFrame: StateFlow<ReplayFrame?> = _currentFrame.asStateFlow()

    private val _speed = MutableStateFlow(1.0)
    val speed: StateFlow<Double> = _speed.asStateFlow()

    private val _progress = MutableStateFlow(0.0) // 0.0 to 1.0 percentage
    val progress: StateFlow<Double> = _progress.asStateFlow()

    private val _telemetryDensity = MutableStateFlow<List<Float>>(emptyList())
    val telemetryDensity: StateFlow<List<Float>> = _telemetryDensity.asStateFlow()

    private val _sessionActions = MutableStateFlow<List<com.ares.analytics.shared.RobotActionRecord>>(emptyList())
    val sessionActions: StateFlow<List<com.ares.analytics.shared.RobotActionRecord>> = _sessionActions.asStateFlow()

    private val _sessionStartTimestampMs = MutableStateFlow(0L)
    val sessionStartTimestampMs: StateFlow<Long> = _sessionStartTimestampMs.asStateFlow()

    private val _sessionDurationMs = MutableStateFlow(0L)
    val sessionDurationMs: StateFlow<Long> = _sessionDurationMs.asStateFlow()

    private val _cacheMetrics = MutableStateFlow(ReplayCacheMetrics())
    val cacheMetrics: StateFlow<ReplayCacheMetrics> = _cacheMetrics.asStateFlow()

    // Replay telemetry flow — emits individual TelemetryFrame objects for dashboard widget consumption
    private val _replayTelemetryFlow = MutableSharedFlow<TelemetryFrame>(
        replay = 100,
        extraBufferCapacity = 65536,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val replayTelemetryFlow: SharedFlow<TelemetryFrame> = _replayTelemetryFlow.asSharedFlow()

    // Process-lifetime scope for replay coroutines. Previously play()/updateFrameAtPlayhead()
    // spawned unparented CoroutineScope(Dispatchers.Default).launch{} (a fresh, untracked scope
    // at 50Hz) — those are now parented here and cancelled in stop()/dispose() (AUDIT H6).
    private val serviceScope = CoroutineScope(replayDispatcher + SupervisorJob())

    private var replayJob: Job? = null
    private var allFrames: List<TelemetryFrame> = emptyList()
    private var timestamps: List<Long> = emptyList()

    private var startTimestampMs: Long = 0L
    private var endTimestampMs: Long = 0L
    private var currentPlayheadMs: Long = 0L
    private var lastTargetTimestamp: Long = -1L
    private var lastFrameIndex: Int = 0
    private var lastActionIndex: Int = 0
    private val valuesMap = java.util.concurrent.ConcurrentHashMap<String, Double>()
    private val windowBaseline = java.util.concurrent.ConcurrentHashMap<String, Double>()
    private val stringValuesMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val windowStringBaseline = java.util.concurrent.ConcurrentHashMap<String, String>()

    // Opened lazily on first broadcast; a service instance that never broadcasts
    // (the common case) no longer holds an ephemeral UDP socket for its lifetime.
    private var datagramSocket: DatagramSocket? = null
    private val loopbackAddress = InetAddress.getByName("127.0.0.1")
    private val broadcastPort = 5802 // AdvantageScope/dashboard loopback

    private var currentSessionId: String = ""
    private var cachedWindowStartMs: Long = -1L
    private var cachedWindowEndMs: Long = -1L
    @Volatile private var prefetchedWindow: ReplayWindow? = null
    private var prefetchJob: Job? = null
    private var windowLoadJob: Job? = null
    private var windowLoadCount = 0L
    private var prefetchHitCount = 0L
    private var truncatedWindowCount = 0L
    private var droppedEmissionFrameCount = 0L

    suspend fun loadSession(sessionId: String) = withContext(Dispatchers.IO) {
        stop()
        resetReplayCache()
        currentSessionId = sessionId
        val frameTimestamps = databaseService.getDistinctTimestamps(sessionId)
        val allActions = databaseService.getActionsForSession(sessionId)
        _sessionActions.value = allActions

        if (frameTimestamps.isEmpty() && allActions.isEmpty()) {
            timestamps = emptyList()
            startTimestampMs = 0
            endTimestampMs = 0
            currentPlayheadMs = 0
            _currentFrame.value = null
            _telemetryDensity.value = emptyList()
            _sessionStartTimestampMs.value = 0L
            _sessionDurationMs.value = 0L
            return@withContext
        }
        val actionsTimestamps = allActions.map { it.timestampMs }

        timestamps = (frameTimestamps + actionsTimestamps).distinct().sorted()

        startTimestampMs = timestamps.first()
        endTimestampMs = timestamps.last()
        currentPlayheadMs = startTimestampMs
        _progress.value = 0.0

        _sessionStartTimestampMs.value = startTimestampMs
        _sessionDurationMs.value = endTimestampMs - startTimestampMs

        if (frameTimestamps.isNotEmpty()) {
            _telemetryDensity.value = databaseService.getTelemetryDensity(sessionId, buckets = 100)
        } else {
            _telemetryDensity.value = emptyList()
        }

        val initialWindow = loadWindowCenteredAt(currentPlayheadMs, sessionId)
        applyWindow(initialWindow)
        scheduleForwardPrefetch(initialWindow)
        updateFrameAtPlayhead()
    }

    fun play() {
        if (timestamps.isEmpty()) return
        if (_state.value == ReplayState.PLAYING) return

        _state.value = ReplayState.PLAYING
        replayJob = serviceScope.launch {
            var lastRealTime = clock.nowMs()
            while (isActive && _state.value == ReplayState.PLAYING) {
                val nowRealTime = clock.nowMs()
                val deltaReal = (nowRealTime - lastRealTime).coerceAtLeast(0L)
                lastRealTime = nowRealTime
                val deltaPlayback = (deltaReal * _speed.value).toLong()
                currentPlayheadMs += deltaPlayback

                if (currentPlayheadMs >= endTimestampMs) {
                    currentPlayheadMs = endTimestampMs
                    _state.value = ReplayState.STOPPED
                    updateFrameAtPlayhead()
                    break
                }

                updateFrameAtPlayhead()
                delay(20) // update at ~50fps
            }
        }
    }

    fun pause() {
        if (_state.value != ReplayState.PLAYING) return
        _state.value = ReplayState.PAUSED
        replayJob?.cancel()
    }

    fun stop() {
        _state.value = ReplayState.STOPPED
        replayJob?.cancel()
        emitJob?.cancel()
        prefetchJob?.cancel()
        windowLoadJob?.cancel()
        windowLoadJob = null
        try {
            datagramSocket?.close()
        } catch (e: Exception) {
        }
        if (timestamps.isNotEmpty()) {
            currentPlayheadMs = startTimestampMs
            _progress.value = 0.0
            updateFrameAtPlayhead(emitTelemetry = false, broadcast = false)
        }
    }

    /**
     * Final teardown — cancels the process-lifetime [serviceScope]. Use when the replay
     * engine is being discarded (e.g. ServiceRegistry shutdown). [stop] should be called
     * for ordinary pause/stop since it leaves [serviceScope] reusable.
     */
    fun dispose() {
        runBlocking { disposeAndJoin() }
    }

    suspend fun disposeAndJoin() {
        val pendingWindowLoad = windowLoadJob
        stop()
        serviceScope.cancel()
        replayJob?.cancelAndJoin()
        emitJob?.cancelAndJoin()
        prefetchJob?.cancelAndJoin()
        pendingWindowLoad?.cancelAndJoin()
        windowLoadJob?.cancelAndJoin()
        try {
            datagramSocket?.close()
        } catch (_: Exception) {
        }
        datagramSocket = null
    }

    fun setSpeed(newSpeed: Double) {
        _speed.value = newSpeed
    }

    // Playhead/index state is read and written by both the 50 Hz play loop (serviceScope)
    // and UI-thread scrub/step calls; this monitor keeps their mutations exclusive so a
    // scrub cannot race the loop's incremental aggregation indexes.
    private val playheadLock = Any()

    fun stepForward() {
        if (timestamps.isEmpty()) return
        pause()
        synchronized(playheadLock) {
            val index = timestamps.binarySearch(currentPlayheadMs)
            val nextIndex = if (index >= 0) index + 1 else -index - 1
            if (nextIndex < timestamps.size) {
                currentPlayheadMs = timestamps[nextIndex]
            } else {
                return
            }
        }
        updateFrameAtPlayhead()
    }

    fun stepBackward() {
        if (timestamps.isEmpty()) return
        pause()
        synchronized(playheadLock) {
            val index = timestamps.binarySearch(currentPlayheadMs)
            val prevIndex = if (index >= 0) index - 1 else -index - 2
            if (prevIndex >= 0) {
                currentPlayheadMs = timestamps[prevIndex]
            } else {
                return
            }
        }
        updateFrameAtPlayhead()
    }

    fun scrubTo(percentage: Double) {
        if (timestamps.isEmpty()) return
        val clamped = percentage.coerceIn(0.0, 1.0)
        synchronized(playheadLock) {
            val totalDuration = endTimestampMs - startTimestampMs
            currentPlayheadMs = startTimestampMs + (totalDuration * clamped).toLong()
        }
        updateFrameAtPlayhead()
    }

    private var emitJob: Job? = null

    private fun updateFrameAtPlayhead(emitTelemetry: Boolean = true, broadcast: Boolean = true) {
        // Single-writer derivation: the play loop and UI scrubs/steps both reach this
        // method; holding playheadLock across the (suspension-free) body keeps the
        // incremental aggregation indexes and playhead mutually consistent.
        synchronized(playheadLock) {
            if (timestamps.isEmpty()) return

            if (currentPlayheadMs < cachedWindowStartMs || currentPlayheadMs > cachedWindowEndMs) {
                if (!ensureReplayWindow(emitTelemetry, broadcast)) return
            }

            // 1. Calculate progress percent
            val totalDuration = endTimestampMs - startTimestampMs
            if (totalDuration > 0) {
                _progress.value = (currentPlayheadMs - startTimestampMs).toDouble() / totalDuration.toDouble()
            }

            // 2. Fetch or compute the current frame values (all values up to currentPlayheadMs)
            // For performance, we find the closest timestamp in our list that is <= currentPlayheadMs
            var index = timestamps.binarySearch(currentPlayheadMs)
            if (index < 0) {
                index = -index - 2
            }
            index = index.coerceIn(0, timestamps.size - 1)
            val targetTimestamp = timestamps[index]

            // Reset incremental cache if we seeked backwards or this is first run
            val seeked = lastTargetTimestamp == -1L ||
                kotlin.math.abs(targetTimestamp - lastTargetTimestamp) > 1000L ||
                (if (_speed.value >= 0) targetTimestamp < lastTargetTimestamp else targetTimestamp > lastTargetTimestamp)

            if (seeked) {
                lastFrameIndex = 0
                lastActionIndex = 0
                valuesMap.clear()
                valuesMap.putAll(windowBaseline)
                stringValuesMap.clear()
                stringValuesMap.putAll(windowStringBaseline)
            }
            lastTargetTimestamp = targetTimestamp
            val deltaMap = mutableMapOf<String, Double>()

            // Incrementally aggregate frame updates
            while (lastFrameIndex < allFrames.size) {
                val frame = allFrames[lastFrameIndex]
                if (frame.timestampMs > targetTimestamp) break
                valuesMap[frame.key] = frame.value
                deltaMap[frame.key] = frame.value
                val stringValue = frame.stringValue
                if (stringValue != null) {
                    stringValuesMap[frame.key] = stringValue
                } else {
                    stringValuesMap.remove(frame.key)
                }
                lastFrameIndex++
            }

            // Incrementally aggregate actions
            val actionsList = _sessionActions.value
            while (lastActionIndex < actionsList.size) {
                val action = actionsList[lastActionIndex]
                if (action.timestampMs > targetTimestamp) break
                try {
                    val payloadObj = jsonParser.parseToJsonElement(action.payloadJson).let {
                        if (it is JsonObject) it else null
                    }
                    if (payloadObj != null && action.actionType == "PoseUpdate") {
                        val x = payloadObj["xMeters"]?.let { if (it is JsonPrimitive) it.doubleOrNull else null }
                        val y = payloadObj["yMeters"]?.let { if (it is JsonPrimitive) it.doubleOrNull else null }
                        val heading = payloadObj["headingRadians"]?.let { if (it is JsonPrimitive) it.doubleOrNull else null }

                        if (x != null) {
                            valuesMap["ARES/EstimatedPose/0"] = x
                            valuesMap["Drive/Odom_X"] = x
                            stringValuesMap.remove("ARES/EstimatedPose/0")
                            stringValuesMap.remove("Drive/Odom_X")
                            deltaMap["ARES/EstimatedPose/0"] = x
                            deltaMap["Drive/Odom_X"] = x
                        }
                        if (y != null) {
                            valuesMap["ARES/EstimatedPose/1"] = y
                            valuesMap["Drive/Odom_Y"] = y
                            stringValuesMap.remove("ARES/EstimatedPose/1")
                            stringValuesMap.remove("Drive/Odom_Y")
                            deltaMap["ARES/EstimatedPose/1"] = y
                            deltaMap["Drive/Odom_Y"] = y
                        }
                        if (heading != null) {
                            valuesMap["ARES/EstimatedPose/2"] = heading
                            valuesMap["Drive/Odom_Heading"] = heading
                            stringValuesMap.remove("ARES/EstimatedPose/2")
                            stringValuesMap.remove("Drive/Odom_Heading")
                            deltaMap["ARES/EstimatedPose/2"] = heading
                            deltaMap["Drive/Odom_Heading"] = heading
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors for individual actions
                }
                lastActionIndex++
            }
            val mapToEmit = if (seeked) valuesMap.toMap() else deltaMap.toMap()

            // Expose a snapshot copy of the aggregated state map
            val currentValuesMap = valuesMap.toMap()
            val currentStringValuesMap = stringValuesMap.toMap()
            val frame = ReplayFrame(targetTimestamp, currentValuesMap, currentStringValuesMap)
            _currentFrame.value = frame

            // 3. Emit individual TelemetryFrame objects for dashboard widget consumption
            if (emitTelemetry) {
                val sessionId = "replay"
                if (emitJob?.isActive == true) droppedEmissionFrameCount += mapToEmit.size
                emitJob?.cancel()
                emitJob = serviceScope.launch {
                    for ((key, value) in mapToEmit) {
                        val normalizedKey = key.removePrefix("/")
                        val telemetryFrame = TelemetryFrame(
                            timestampMs = targetTimestamp,
                            sessionId = sessionId,
                            key = normalizedKey,
                            value = value,
                            stringValue = currentStringValuesMap[key]
                        )
                        _replayTelemetryFlow.emit(telemetryFrame)
                    }
                }
                publishCacheMetrics()
            }

            // 4. Re-broadcast via UDP loopback for AdvantageScope / telemetry viewer compatibility
            if (broadcast) broadcastTelemetry(ReplayFrame(targetTimestamp, mapToEmit))
    }
    }

    private fun resetReplayCache() {
        prefetchJob?.cancel()
        windowLoadJob?.cancel()
        windowLoadJob = null
        allFrames = emptyList()
        cachedWindowStartMs = -1L
        cachedWindowEndMs = -1L
        lastTargetTimestamp = -1L
        lastFrameIndex = 0
        lastActionIndex = 0
        valuesMap.clear()
        windowBaseline.clear()
        stringValuesMap.clear()
        windowStringBaseline.clear()
        prefetchedWindow = null
        windowLoadCount = 0
        prefetchHitCount = 0
        truncatedWindowCount = 0
        droppedEmissionFrameCount = 0
        _cacheMetrics.value = ReplayCacheMetrics()
    }

    /** Returns immediately; a cache miss is loaded off the caller/UI thread. */
    private fun ensureReplayWindow(emitTelemetry: Boolean, broadcast: Boolean): Boolean {
        val readyPrefetch = prefetchedWindow?.takeIf { currentPlayheadMs in it.startMs..it.endMs }
        if (readyPrefetch != null) {
            prefetchedWindow = null
            prefetchHitCount++
            applyWindow(readyPrefetch)
            scheduleForwardPrefetch(readyPrefetch)
            return true
        }
        if (windowLoadJob?.isActive == true) return false

        val requestedPlayhead = currentPlayheadMs
        val requestedSession = currentSessionId
        windowLoadJob = serviceScope.launch {
            val window = withContext(Dispatchers.IO) {
                loadWindowCenteredAt(requestedPlayhead, requestedSession)
            }
            if (currentSessionId == requestedSession) {
                applyWindow(window)
                scheduleForwardPrefetch(window)
                // Clear before reevaluating the current playhead. A rapid scrub may have moved
                // outside the window that was requested; updateFrameAtPlayhead must be able to
                // schedule the next window immediately instead of seeing this job as active.
                windowLoadJob = null
                updateFrameAtPlayhead(emitTelemetry, broadcast)
            } else {
                windowLoadJob = null
            }
        }
        return false
    }

    private suspend fun loadWindowCenteredAt(playheadMs: Long, sessionId: String): ReplayWindow {
        val start = (playheadMs - WINDOW_HISTORY_MS).coerceAtLeast(startTimestampMs)
        val end = (playheadMs + WINDOW_LOOKAHEAD_MS).coerceAtMost(endTimestampMs)
        return loadWindow(start, end, sessionId)
    }

    private suspend fun loadWindow(startMs: Long, endMs: Long, sessionId: String): ReplayWindow {
        val baseline = databaseService.getLatestTelemetryBefore(sessionId, startMs)
        val frames = loadTelemetryWindowPages(
            databaseService,
            sessionId,
            startMs,
            endMs,
            REPLAY_PAGE_SIZE
        )
        windowLoadCount++
        return ReplayWindow(startMs, endMs, baseline, frames)
    }

    private fun applyWindow(window: ReplayWindow) {
        allFrames = window.frames
        cachedWindowStartMs = window.startMs
        cachedWindowEndMs = window.endMs
        lastTargetTimestamp = -1L
        lastFrameIndex = 0
        valuesMap.clear()
        windowBaseline.clear()
        stringValuesMap.clear()
        windowStringBaseline.clear()
        for (frame in window.baseline) {
            windowBaseline[frame.key] = frame.value
            frame.stringValue?.let { windowStringBaseline[frame.key] = it }
        }
        valuesMap.putAll(windowBaseline)
        stringValuesMap.putAll(windowStringBaseline)
        publishCacheMetrics()
    }

    private fun scheduleForwardPrefetch(window: ReplayWindow) {
        if (window.endMs >= endTimestampMs) return
        prefetchJob?.cancel()
        val sessionAtSchedule = currentSessionId
        val nextStart = window.endMs + 1
        val nextEnd = (nextStart + WINDOW_HISTORY_MS + WINDOW_LOOKAHEAD_MS).coerceAtMost(endTimestampMs)
        prefetchJob = serviceScope.launch(Dispatchers.IO) {
            val loaded = loadWindow(nextStart, nextEnd, sessionAtSchedule)
            if (currentSessionId == sessionAtSchedule) {
                prefetchedWindow = loaded
                publishCacheMetrics()
            }
        }
    }

    private fun publishCacheMetrics() {
        _cacheMetrics.value = ReplayCacheMetrics(
            windowStartMs = cachedWindowStartMs,
            windowEndMs = cachedWindowEndMs,
            cachedFrames = allFrames.size,
            hasPrefetchedWindow = prefetchedWindow != null,
            windowLoads = windowLoadCount,
            prefetchHits = prefetchHitCount,
            truncatedWindows = truncatedWindowCount,
            droppedEmissionFrames = droppedEmissionFrameCount
        )
    }

    private fun broadcastTelemetry(frame: ReplayFrame) {
        try {
            if (datagramSocket?.isClosed != false) {
                datagramSocket = DatagramSocket()
            }
            val maxChunkSize = 500
            val entries = frame.values.entries.toList()
            for (i in entries.indices step maxChunkSize) {
                val chunkMap = entries.subList(i, minOf(i + maxChunkSize, entries.size)).associate { it.key to it.value }
                val jsonStr = Json.encodeToString(chunkMap)
                val bytes = jsonStr.toByteArray()
                val packet = DatagramPacket(bytes, bytes.size, loopbackAddress, broadcastPort)
                datagramSocket?.send(packet)
            }
        } catch (e: Exception) {
            // Ignore socket broadcast errors
        } finally {
            // If instructions strictly imply closing it after usage
            // datagramSocket?.close()
            // datagramSocket = null
        }
    }

    private data class ReplayWindow(
        val startMs: Long,
        val endMs: Long,
        val baseline: List<TelemetryFrame>,
        val frames: List<TelemetryFrame>
    )

    companion object {
        private const val WINDOW_HISTORY_MS = 2_500L
        private const val WINDOW_LOOKAHEAD_MS = 5_000L
        private const val REPLAY_PAGE_SIZE = 50_000
    }
}
