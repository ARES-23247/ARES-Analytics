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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

enum class ReplayState {
    PLAYING, PAUSED, STOPPED
}

data class ReplayFrame(
    val timestampMs: Long,
    val values: Map<String, Double>
)

class ReplayEngineService(private val databaseService: DatabaseService) {

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

    // Replay telemetry flow — emits individual TelemetryFrame objects for dashboard widget consumption
    private val _replayTelemetryFlow = MutableSharedFlow<TelemetryFrame>(
        replay = 100,
        extraBufferCapacity = 65536,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val replayTelemetryFlow: SharedFlow<TelemetryFrame> = _replayTelemetryFlow.asSharedFlow()

    private var replayJob: Job? = null
    private var allFrames: List<TelemetryFrame> = emptyList()
    private var timestamps: List<Long> = emptyList()

    private var startTimestampMs: Long = 0L
    private var endTimestampMs: Long = 0L
    private var currentPlayheadMs: Long = 0L

    private val datagramSocket = DatagramSocket()
    private val loopbackAddress = InetAddress.getByName("127.0.0.1")
    private val broadcastPort = 5802 // AdvantageScope/dashboard loopback

    suspend fun loadSession(sessionId: String) = withContext(Dispatchers.IO) {
        stop()
        allFrames = databaseService.getTelemetryRange(sessionId, 0L, Long.MAX_VALUE)
        val allActions = databaseService.getActionsForSession(sessionId)
        _sessionActions.value = allActions

        if (allFrames.isEmpty() && allActions.isEmpty()) {
            timestamps = emptyList()
            startTimestampMs = 0
            endTimestampMs = 0
            currentPlayheadMs = 0
            _currentFrame.value = null
            _telemetryDensity.value = emptyList()
            return@withContext
        }

        val framesTimestamps = allFrames.map { it.timestampMs }
        val actionsTimestamps = allActions.map { it.timestampMs }
        
        timestamps = (framesTimestamps + actionsTimestamps).distinct().sorted()
        
        startTimestampMs = timestamps.first()
        endTimestampMs = timestamps.last()
        currentPlayheadMs = startTimestampMs
        _progress.value = 0.0
        
        _sessionStartTimestampMs.value = startTimestampMs
        _sessionDurationMs.value = endTimestampMs - startTimestampMs

        if (allFrames.isNotEmpty()) {
            _telemetryDensity.value = databaseService.getTelemetryDensity(sessionId, buckets = 100)
        } else {
            _telemetryDensity.value = emptyList()
        }

        updateFrameAtPlayhead()
    }

    fun play() {
        if (timestamps.isEmpty()) return
        if (_state.value == ReplayState.PLAYING) return

        _state.value = ReplayState.PLAYING
        replayJob = CoroutineScope(Dispatchers.Default).launch {
            var lastRealTime = System.currentTimeMillis()
            while (isActive && _state.value == ReplayState.PLAYING) {
                val nowRealTime = System.currentTimeMillis()
                val deltaReal = nowRealTime - lastRealTime
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
        if (timestamps.isNotEmpty()) {
            currentPlayheadMs = startTimestampMs
            _progress.value = 0.0
            updateFrameAtPlayhead()
        }
    }

    fun setSpeed(newSpeed: Double) {
        _speed.value = newSpeed
    }

    fun stepForward() {
        if (timestamps.isEmpty()) return
        pause()
        val index = timestamps.binarySearch(currentPlayheadMs)
        val nextIndex = if (index >= 0) index + 1 else -index - 1
        if (nextIndex < timestamps.size) {
            currentPlayheadMs = timestamps[nextIndex]
            updateFrameAtPlayhead()
        }
    }

    fun stepBackward() {
        if (timestamps.isEmpty()) return
        pause()
        val index = timestamps.binarySearch(currentPlayheadMs)
        val prevIndex = if (index >= 0) index - 1 else -index - 2
        if (prevIndex >= 0) {
            currentPlayheadMs = timestamps[prevIndex]
            updateFrameAtPlayhead()
        }
    }

    fun scrubTo(percentage: Double) {
        if (timestamps.isEmpty()) return
        val clamped = percentage.coerceIn(0.0, 1.0)
        val totalDuration = endTimestampMs - startTimestampMs
        currentPlayheadMs = startTimestampMs + (totalDuration * clamped).toLong()
        updateFrameAtPlayhead()
    }

    private var emitJob: Job? = null

    private fun updateFrameAtPlayhead() {
        if (timestamps.isEmpty()) return

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

        // Aggregate values up to this target timestamp
        val valuesMap = mutableMapOf<String, Double>()
        for (frame in allFrames) {
            if (frame.timestampMs > targetTimestamp) break
            valuesMap[frame.key] = frame.value
        }

        val frame = ReplayFrame(targetTimestamp, valuesMap)
        _currentFrame.value = frame

        // 3. Emit individual TelemetryFrame objects for dashboard widget consumption
        val sessionId = "replay"
        emitJob?.cancel()
        emitJob = CoroutineScope(Dispatchers.Default).launch {
            for ((key, value) in valuesMap) {
                val normalizedKey = key.removePrefix("/")
                val telemetryFrame = TelemetryFrame(
                    timestampMs = targetTimestamp,
                    sessionId = sessionId,
                    key = normalizedKey,
                    value = value
                )
                _replayTelemetryFlow.emit(telemetryFrame)
            }
        }

        // 4. Re-broadcast via UDP loopback for AdvantageScope / telemetry viewer compatibility
        broadcastTelemetry(frame)
    }

    private fun broadcastTelemetry(frame: ReplayFrame) {
        try {
            val jsonStr = Json.encodeToString(frame.values)
            val bytes = jsonStr.toByteArray()
            val packet = DatagramPacket(bytes, bytes.size, loopbackAddress, broadcastPort)
            datagramSocket.send(packet)
        } catch (e: Exception) {
            // Ignore socket broadcast errors
        }
    }
}
