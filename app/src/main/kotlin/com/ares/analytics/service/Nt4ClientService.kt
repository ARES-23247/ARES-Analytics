package com.ares.analytics.service

import com.ares.analytics.shared.*
import com.ares.analytics.service.nt4.Nt4Topic
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.withLock
import io.ktor.client.engine.okhttp.OkHttp

/**
 * High-performance **NetworkTables NT4 WebSocket Streaming Client**.
 *
 * Establishes real-time, non-blocking binary and JSON WebSocket streams over port `5810` with FRC roboRIOs,
 * FTC Control Hubs, and ARES Physics Simulators.
 *
 * ### NetworkTables 4 Protocol Specifications:
 * - **WebSocket Connection URI:** `ws://<host>:5810/nt/ARES-Analytics-<timestamp>`
 * - **Subscription Handshake:**
 *   $$\text{Subscribe} \iff \{ \text{"method": "subscribe"}, \text{"params": } \{ \text{"topics": } [\text{"/Drive/Pose_X"}, \text{"/Drive/Pose_Y"}, \dots] \} \}$$
 *
 * ### Performance & Memory Guarantees:
 * - **Streaming Rate:** $20\text{ Hz}$ live telemetry to $100\text{ Hz}$ high-density log replay
 * - **Backpressure:** bounded lossless buffers suspend the WebSocket reader when consumers or persistence fall behind.
 * - **Thread Safety:** Fully thread-safe state management via `ConcurrentHashMap` and atomic volatile references.
 *
 * @param databaseService DuckDB log persistence engine for historical telemetry recording.
 * @see TelemetryFrame
 * @see DatabaseService
 */
open class Nt4ClientService(
    private val databaseService: DatabaseService
) {
    @Volatile
    private var localClient: HttpClient? = null
    @Volatile
    private var remoteClient: HttpClient? = null

    private fun getOrCreateLocalClient(): HttpClient {
        var client = localClient
        if (client == null || !client.coroutineContext.isActive) {
            synchronized(this) {
                client = localClient
                if (client == null || !client.coroutineContext.isActive) {
                    client = HttpClient(OkHttp) { install(WebSockets) }
                    localClient = client
                }
            }
        }
        return requireNotNull(client)
    }

    private fun getOrCreateRemoteClient(): HttpClient {
        var client = remoteClient
        if (client == null || !client.coroutineContext.isActive) {
            synchronized(this) {
                client = remoteClient
                if (client == null || !client.coroutineContext.isActive) {
                    client = HttpClient(OkHttp) { install(WebSockets) }
                    remoteClient = client
                }
            }
        }
        return requireNotNull(client)
    }

    /** Select the appropriate engine based on target host */
    private fun clientFor(host: String): HttpClient = when (host) {
        "127.0.0.1", "localhost" -> getOrCreateLocalClient()
        else -> getOrCreateRemoteClient()
    }
    var serverIp: String = "127.0.0.1"

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e -> e.printStackTrace() })

    private val _isConnected = MutableStateFlow(false)
    open val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val _selectedRedAlliance = MutableStateFlow(true)
    /** Dashboard-owned alliance selection; survives view/navigation and NT4 reconnect lifecycles. */
    val selectedRedAlliance: StateFlow<Boolean> = _selectedRedAlliance.asStateFlow()
    val isReplayActive = MutableStateFlow(false)
    private val connectionAttempts = java.util.concurrent.atomic.AtomicLong()
    private val successfulConnections = java.util.concurrent.atomic.AtomicLong()
    internal val malformedTextFrameCount = java.util.concurrent.atomic.AtomicLong()

    fun connectionMetrics(): Nt4ConnectionMetrics = Nt4ConnectionMetrics(
        attempts = connectionAttempts.get(),
        successfulConnections = successfulConnections.get(),
        reconnects = (successfulConnections.get() - 1L).coerceAtLeast(0L),
        connected = _isConnected.value
    )

    val telemetryStore = TelemetryStore()
    open val telemetryFlow: SharedFlow<TelemetryFrame> = telemetryStore.updates

    private val _consoleFlow = MutableSharedFlow<ConsoleMessage>(
        replay = 100,
        extraBufferCapacity = 1024,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.SUSPEND
    )
    val consoleFlow: SharedFlow<ConsoleMessage> = _consoleFlow.asSharedFlow()

    /**
     * Injects a replay frame into the telemetry flow so dashboard widgets consume
     * replay data identically to live data. Called by the replay integration layer.
     */
    suspend fun emitReplayFrame(frame: TelemetryFrame) {
        telemetryStore.accept(frame)
    }

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _latestTopology = MutableStateFlow<HardwareTopology?>(null)
    val latestTopology: StateFlow<HardwareTopology?> = _latestTopology.asStateFlow()

    fun setLatestTopology(topology: HardwareTopology?) {
        _latestTopology.value = topology
    }

    private var webSocketSession: DefaultClientWebSocketSession? = null
    @Volatile private var serverTimeOffsetUs: Long? = null
    @Volatile private var bestClockRoundTripUs: Long = Long.MAX_VALUE

    // Topic ID to Topic Name mapping
    internal val topicMap = ConcurrentHashMap<Int, Nt4Topic>()
    private val discoveredKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** Direct latest-value view used by snapshot-oriented dashboard components. */
    val latestValues: ConcurrentHashMap<String, TelemetryFrame> = telemetryStore.latestFrames

    // Written by the WS reader coroutine on announce/unannounce invalidation, read from UI
    // threads by getActiveTopics(); volatile so readers observe invalidation promptly instead
    // of serving a stale list indefinitely.
    @Volatile private var cachedActiveTopics: List<String>? = null

    fun getActiveTopics(): List<String> {
        return cachedActiveTopics ?: run {
            val fromMap = topicMap.values.map { it.name.removePrefix("/") }
            val topics = (fromMap + discoveredKeys).distinct().filter { it.isNotEmpty() }.sorted()
            cachedActiveTopics = topics
            topics
        }
    }

    private val pendingFrames = kotlinx.coroutines.channels.Channel<TelemetryFrame>(capacity = 100_000)
    private val retryFrames = java.util.ArrayDeque<TelemetryFrame>()
    private val flushMutex = kotlinx.coroutines.sync.Mutex()
    private val sessionMutex = kotlinx.coroutines.sync.Mutex()
    private val driveFramePublishMutex = kotlinx.coroutines.sync.Mutex()
    private val driveFrameValidator = DriveFrameContractValidator()
    private val driveSessionNonceCounter = java.util.concurrent.atomic.AtomicLong(
        java.util.concurrent.ThreadLocalRandom.current().nextLong(1L, DriveFrameContractValidator.MAX_SAFE_INTEGER_LONG)
    )

    suspend fun flushPendingFrames(): Boolean = flushMutex.withLock {
        // Do not drain newer channel values behind a failed batch. Keeping one ordered retry
        // deque plus the bounded channel preserves arrival order and applies backpressure.
        if (retryFrames.isEmpty()) {
            while (true) {
                val frame = pendingFrames.tryReceive().getOrNull() ?: break
                retryFrames.addLast(frame)
            }
        }

        var latestLiveTimestamp: Long? = null
        while (retryFrames.isNotEmpty()) {
            val liveBatch = retryFrames.first().sessionId == LIVE_SESSION_ID
            val chunk = ArrayList<TelemetryFrame>(100)
            val iterator = retryFrames.iterator()
            while (iterator.hasNext() && chunk.size < 100) {
                val frame = iterator.next()
                if ((frame.sessionId == LIVE_SESSION_ID) != liveBatch) break
                chunk.add(frame)
            }

            try {
                databaseService.insertTelemetryFrames(chunk)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withLock false
            }
            repeat(chunk.size) { retryFrames.removeFirst() }
            if (liveBatch) {
                chunk.maxOfOrNull(TelemetryFrame::timestampMs)?.let { chunkMax ->
                    latestLiveTimestamp = maxOf(latestLiveTimestamp ?: Long.MIN_VALUE, chunkMax)
                }
            }
        }

        latestLiveTimestamp?.let { newestTimestamp ->
            try {
                databaseService.pruneTelemetryFrames(LIVE_SESSION_ID, newestTimestamp - LIVE_RETENTION_MS)
            } catch (e: Exception) {
                // Pruning is maintenance, not persistence. Frames were committed successfully.
                e.printStackTrace()
            }
        }
        true
    }

    internal suspend fun retainedRetryFrameCount(): Int = flushMutex.withLock { retryFrames.size }

    internal fun clearLiveTargetState() {
        topicMap.clear()
        discoveredKeys.clear()
        telemetryStore.clear()
        cachedActiveTopics = null
    }

    private var clientJob: Job? = null
    private var startJob: Job? = null
    private val lifecycleMonitor = Any()
    private val lifecycleGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    private val connectionMutex = kotlinx.coroutines.sync.Mutex()

    fun start(host: String, teamId: String, seasonId: String, robotId: String, port: Int = 5810) {
        println("[Nt4ClientService] start() called with host=$host, port=$port, teamId=$teamId, seasonId=$seasonId, robotId=$robotId")
        val generation = lifecycleGeneration.incrementAndGet()
        val nextStart = serviceScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            connectionMutex.withLock {
                if (generation != lifecycleGeneration.get()) return@withLock
                clientJob?.cancelAndJoin()
                while (isActive && generation == lifecycleGeneration.get() && !flushPendingFrames()) delay(250)
                if (generation != lifecycleGeneration.get()) return@withLock
                clearLiveTargetState()
                clientJob = launch {
            try {
                databaseService.deleteTelemetryFrames("live-telemetry")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Launch periodic flush job in background
            launch {
                while (isActive) {
                    delay(1000)
                    flushPendingFrames()
                }
            }

            var retryDelay = 1000L
            while (isActive) {
                var activeHost = host
                var connectedAtMs: Long? = null
                val clientName = "ARES-Analytics-${System.currentTimeMillis()}"
                val path = "/nt/$clientName"
                val url = "ws://$activeHost:$port$path"
                this@Nt4ClientService.serverIp = activeHost
                try {
                    connectionAttempts.incrementAndGet()
                    val activeEngine = "OkHttp"
                    println("[Nt4ClientService] Attempting to connect to $url (engine=$activeEngine)")
                    clientFor(activeHost).webSocket(
                        method = HttpMethod.Get,
                        host = activeHost,
                        port = port,
                        path = path,
                        request = {
                            header("Sec-WebSocket-Protocol", "v4.1.networktables.first.wpi.edu")
                        }
                    ) {
                        println("[Nt4ClientService] Connected to $url successfully!")
                        successfulConnections.incrementAndGet()
                        driveFramePublishMutex.withLock { driveFrameValidator.reset() }
                        _isConnected.value = true
                        webSocketSession = this
                        topicMap.clear()
                        serverTimeOffsetUs = null
                        bestClockRoundTripUs = Long.MAX_VALUE
                        connectedAtMs = System.currentTimeMillis()

                        // 1. Announce the single atomic, leased input topic. Individual
                        // scalar controls are intentionally unsupported: mixing retained
                        // values from different sessions cannot be made fail-safe.
                        val announceInputsMsg = """
                            [
                              {"method": "publish", "params": {"name": "ARES/Input/driveFrame", "pubuid": 1020, "type": "double[]"}},
                              {"method": "publish", "params": {"name": "ARES/DriverStation/Command", "pubuid": 1011, "type": "string"}},
                              {"method": "publish", "params": {"name": "ARES/DriverStation/SelectedOpMode", "pubuid": 1012, "type": "string"}},
                              {"method": "publish", "params": {"name": "ARES/DriverStation/MatchTime", "pubuid": 1013, "type": "double"}},
                              {"method": "publish", "params": {"name": "ARES/DriverStation/MatchState", "pubuid": 1014, "type": "string"}},
                              {"method": "publish", "params": {"name": "SysId/Command", "pubuid": 1015, "type": "string"}},
                              {"method": "publish", "params": {"name": "SysId/EnableToken", "pubuid": 1016, "type": "string"}},
                              {"method": "publish", "params": {"name": "SysId/EnableLease", "pubuid": 1017, "type": "double"}}
                            ]
                        """.trimIndent()
                        send(Frame.Text(announceInputsMsg))

                        // 2. Subscribe to all topics (using explicit prefixes to support both WPILib and Sim)
                        val subscriptionTopics = CANONICAL_SUBSCRIPTION_PREFIXES.joinToString(",") { "\"$it\"" }
                        val subMsg = """
                            [
                              {
                                "method": "subscribe",
                                "params": {
                                  "topics": [$subscriptionTopics],
                                  "subuid": 1,
                                  "options": {
                                    "prefix": true,
                                    "logging": true
                                  }
                                }
                              }
                            ]
                        """.trimIndent()
                        send(Frame.Text(subMsg))

                        // 2.5 Re-announce dynamic UI tuning topics
                        dynamicPubMutex.withLock {
                            for ((key, id) in dynamicPubUids) {
                                if (key in FIXED_PUBLISH_TOPICS) continue
                                val type = publisherTypes[key] ?: continue
                                send(Frame.Text(buildPublishMessage(key, id, type)))
                            }
                        }

                        // Establish the NT4 server-time offset before publishing controls. NT4
                        // value timestamps are always in the server time base; System.nanoTime()
                        // alone is only meaningful on this laptop.
                        val clockSyncJob = launch {
                            while (isActive) {
                                try {
                                    sendTimeSyncRequest()
                                } catch (_: Exception) {
                                    break
                                }
                                delay(1_000)
                            }
                        }

                        try {
                            // 3. Read frames
                            while (isActive) {
                                val frame = withTimeout(5000) { incoming.receive() }
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        handleIncomingText(text, teamId, seasonId, robotId)
                                    }
                                    is Frame.Binary -> {
                                        val bytes = frame.readBytes()
                                        handleIncomingBinary(bytes, teamId, seasonId, robotId)
                                    }
                                    else -> {}
                                }
                            }
                        } finally {
                            clockSyncJob.cancel()
                            val reason = withContext(NonCancellable) {
                                withTimeoutOrNull(CLOSE_HANDSHAKE_TIMEOUT_MS) { closeReason.await() }
                            }
                            if (reason != null) {
                                println("[Nt4ClientService] Connection to $url closed. Reason: ${reason?.message} (Code: ${reason?.code})")
                            } else {
                                // A server may remain healthy while the user disconnects. Do not keep stop()
                                // waiting forever for a peer-initiated WebSocket close frame.
                                println("[Nt4ClientService] Connection to $url closed without a peer close handshake.")
                            }
                            webSocketSession = null
                            serverTimeOffsetUs = null
                            _isConnected.value = false
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("[Nt4ClientService] Error connecting to $url: ${e.message}")
                    webSocketSession = null
                    _isConnected.value = false
                }
                if (isActive) {
                    val wasHealthy = connectedAtMs?.let {
                        System.currentTimeMillis() - it >= HEALTHY_CONNECTION_MS
                    } == true
                    val delayBeforeRetry = if (wasHealthy) INITIAL_RETRY_DELAY_MS else retryDelay
                    delay(delayBeforeRetry)
                    retryDelay = if (wasHealthy) {
                        INITIAL_RETRY_DELAY_MS
                    } else {
                        (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                    }
                }
            }
        }
        }
        }
        val previousStart = synchronized(lifecycleMonitor) {
            val previous = startJob
            startJob = nextStart
            previous
        }
        previousStart?.cancel()
        nextStart.start()
    }

    private suspend fun sendBinaryUpdate(pubuid: Int, typeId: Byte, valueBytes: ByteArray): Boolean {
        val offsetUs = serverTimeOffsetUs ?: return false
        val session = webSocketSession ?: return false
        val timestampUs = localMonotonicTimeUs() + offsetUs
        val buffer = encodeNt4BinaryUpdate(pubuid, timestampUs, typeId, valueBytes)

        session.send(Frame.Binary(true, buffer))
        return true
    }

    internal fun encodeNt4BinaryUpdate(
        pubuid: Int,
        timestampUs: Long,
        typeId: Byte,
        valueBytes: ByteArray
    ): ByteArray {
        require(pubuid in 0..0xffff) { "publisher UID must fit an unsigned 16-bit NT4 ID" }
        val buffer = ByteArray(14 + valueBytes.size)

        // NT4 binary frames are streams of complete 4-tuples, without an outer batch array.
        buffer[0] = 0x94.toByte()

        // Write pubuid (encoded as MsgPack uint16)
        buffer[1] = 0xcd.toByte()
        buffer[2] = (pubuid shr 8).toByte()
        buffer[3] = pubuid.toByte()

        // Write timestampUs (encoded as MsgPack uint64)
        buffer[4] = 0xcf.toByte()
        buffer[5] = (timestampUs shr 56).toByte()
        buffer[6] = (timestampUs shr 48).toByte()
        buffer[7] = (timestampUs shr 40).toByte()
        buffer[8] = (timestampUs shr 32).toByte()
        buffer[9] = (timestampUs shr 24).toByte()
        buffer[10] = (timestampUs shr 16).toByte()
        buffer[11] = (timestampUs shr 8).toByte()
        buffer[12] = timestampUs.toByte()

        // Write typeId (encoded as positive fixint since typeId < 128)
        buffer[13] = typeId

        // Write value bytes (already MsgPack encoded)
        System.arraycopy(valueBytes, 0, buffer, 14, valueBytes.size)
        return buffer
    }

    private fun buildPublishMessage(name: String, pubUid: Int, type: String): String =
        buildJsonArray {
            add(buildJsonObject {
                put("method", "publish")
                put("params", buildJsonObject {
                    put("name", name)
                    put("pubuid", pubUid)
                    put("type", type)
                })
            })
        }.toString()

    private suspend fun sendTimeSyncRequest() {
        val sentAtUs = localMonotonicTimeUs()
        val buffer = ByteArray(13)
        buffer[0] = 0x94.toByte() // four-element NT4 tuple
        buffer[1] = 0xff.toByte() // reserved RTT topic ID (-1)
        buffer[2] = 0x00 // request timestamp is always zero
        buffer[3] = 0x02 // int type
        buffer[4] = 0xd3.toByte() // signed int64 client timestamp value
        for (index in 0 until 8) {
            buffer[5 + index] = (sentAtUs shr (56 - index * 8)).toByte()
        }
        webSocketSession?.send(Frame.Binary(true, buffer))
    }

    private fun localMonotonicTimeUs(): Long = System.nanoTime() / 1_000L

    private val publishDoubleBuffer = ThreadLocal.withInitial { ByteArray(9) }

    suspend fun publishInputDouble(pubuid: Int, value: Double): Boolean {
        val bits = java.lang.Double.doubleToRawLongBits(value)
        val valueBytes = publishDoubleBuffer.get()
        valueBytes[0] = 0xcb.toByte() // MsgPack float64 marker
        valueBytes[1] = (bits shr 56).toByte()
        valueBytes[2] = (bits shr 48).toByte()
        valueBytes[3] = (bits shr 40).toByte()
        valueBytes[4] = (bits shr 32).toByte()
        valueBytes[5] = (bits shr 24).toByte()
        valueBytes[6] = (bits shr 16).toByte()
        valueBytes[7] = (bits shr 8).toByte()
        valueBytes[8] = bits.toByte()
        return sendBinaryUpdate(pubuid, 1.toByte(), valueBytes)
    }

    suspend fun publishInputString(pubuid: Int, value: String): Boolean {
        val strBytes = value.toByteArray(Charsets.UTF_8)
        val size = strBytes.size
        require(size <= MAX_STRING_BYTES) { "NT4 strings are limited to $MAX_STRING_BYTES UTF-8 bytes" }
        val headerBytes = when {
            size <= 31 -> byteArrayOf((0xa0 or size).toByte())
            size <= 255 -> byteArrayOf(0xd9.toByte(), size.toByte())
            size <= 65535 -> byteArrayOf(0xda.toByte(), (size shr 8).toByte(), size.toByte())
            else -> byteArrayOf(0xdb.toByte(), (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte())
        }
        val valueBytes = ByteArray(headerBytes.size + strBytes.size)
        System.arraycopy(headerBytes, 0, valueBytes, 0, headerBytes.size)
        System.arraycopy(strBytes, 0, valueBytes, headerBytes.size, strBytes.size)

        return sendBinaryUpdate(pubuid, 4.toByte(), valueBytes)
    }

    // fixed-array header plus eight float64 values (1 + 8 * 9 bytes)
    private val publishDoubleArrayBuffer = ThreadLocal.withInitial { ByteArray(73) }

    suspend fun publishInputDoubleArray(pubuid: Int, values: DoubleArray): Boolean {
        require(values.size == DriveFrameContractValidator.VALUE_COUNT) { "drive frame must contain 8 doubles" }
        val valueBytes = publishDoubleArrayBuffer.get()
        valueBytes[0] = (0x90 or values.size).toByte() // MsgPack fixed-array header
        var offset = 1
        for (value in values) {
            val bits = java.lang.Double.doubleToRawLongBits(value)
            valueBytes[offset++] = 0xcb.toByte()
            for (shift in 56 downTo 0 step 8) valueBytes[offset++] = (bits shr shift).toByte()
        }
        return sendBinaryUpdate(pubuid, 17.toByte(), valueBytes)
    }

    suspend fun stop(): Boolean {
        lifecycleGeneration.incrementAndGet()
        val pendingStart = synchronized(lifecycleMonitor) {
            val pending = startJob
            startJob = null
            pending
        }
        pendingStart?.cancelAndJoin()
        // cancelAndJoin (was a fire-and-forget cancel()) so the WebSocket receive/reconnect
        // loop fully unwinds before the HttpClients are closed below — otherwise the
        // still-running loop touches a closed client (use-after-close, AUDIT H14).
        connectionMutex.withLock {
            clientJob?.cancelAndJoin()
            clientJob = null
        }
        _isConnected.value = false
        webSocketSession = null
        var persisted = false
        for (attempt in 0 until SHUTDOWN_FLUSH_ATTEMPTS) {
            if (flushPendingFrames()) {
                persisted = true
                break
            }
            if (attempt + 1 < SHUTDOWN_FLUSH_ATTEMPTS) delay(SHUTDOWN_FLUSH_RETRY_MS)
        }
        synchronized(this) {
            localClient?.close()
            localClient = null
            remoteClient?.close()
            remoteClient = null
        }
        return persisted
    }

    suspend fun publishFrame(frame: TelemetryFrame) {
        val finalFrame = sessionMutex.withLock {
            val sessionId = _currentSession.value?.sessionId ?: LIVE_SESSION_ID
            frame.copy(sessionId = sessionId).also { pendingFrames.send(it) }
        }
        telemetryStore.accept(finalFrame, notifyConsumers = !isReplayActive.value)
    }

    suspend fun startRecordingSession(
        teamId: String,
        seasonId: String,
        robotId: String,
        matchNumber: Int? = null,
        allianceColor: String? = null,
        tags: List<String> = emptyList()
    ): Session {
        val session = Session(
            sessionId = UUID.randomUUID().toString(),
            teamId = teamId,
            seasonId = seasonId,
            robotId = robotId,
            createdAt = System.currentTimeMillis(),
            matchNumber = matchNumber,
            allianceColor = allianceColor,
            tags = tags
        )
        sessionMutex.withLock {
            databaseService.insertSession(session)
            _currentSession.value = session
        }
        return session
    }

    suspend fun stopRecordingSession() {
        sessionMutex.withLock {
            val session = _currentSession.value ?: return
            _currentSession.value = null
            if (!flushPendingFrames()) {
                _currentSession.value = session
                throw java.io.IOException("Failed to persist all pending telemetry frames")
            }
            val endTime = System.currentTimeMillis()
            val duration = endTime - session.createdAt
            databaseService.insertSession(session.copy(durationMs = duration))
        }
    }

    internal suspend fun handleIncomingText(
        text: String,
        teamId: String,
        seasonId: String,
        robotId: String
    ) {
        if (text.length > MAX_TEXT_FRAME_CHARS) {
            println("[Nt4ClientService] Rejected oversized text frame (${text.length} characters)")
            return
        }
        try {
            val parsed = Json.parseToJsonElement(text)
            val jsonArray = parsed as? JsonArray ?: return
            if (jsonArray.size > MAX_TEXT_FRAME_MESSAGES) return
            for (element in jsonArray) {
                val obj = element as? JsonObject ?: continue
                val method = obj["method"]?.jsonPrimitive?.content

                if (method != null) {
                    when (method) {
                        "announce" -> {
                            val params = obj["params"] as? JsonObject ?: continue
                            val name = params["name"]?.jsonPrimitive?.content ?: continue
                            val id = params["id"]?.jsonPrimitive?.intOrNull ?: continue
                            val type = params["type"]?.jsonPrimitive?.content ?: "double"
                            val propertiesJson = params["properties"] as? JsonObject
                            val props = mutableMapOf<String, String>()
                            propertiesJson?.forEach { (k, v) ->
                                props[k] = if (v is JsonPrimitive && v.isString) v.content else v.toString()
                            }

                            val expectedType = if (name.removePrefix("/") == "ARES/Input/driveFrame") {
                                "double[]"
                            } else {
                                null
                            }
                            if (expectedType != null && type != expectedType) {
                                println("[Nt4ClientService] WARN: Topic $name announced with type $type, expected $expectedType")
                            }

                            println("[Nt4ClientService] Server announced topic: $name (id=$id, type=$type)")
                            topicMap[id] = Nt4Topic(id, name, type, props)
                            cachedActiveTopics = null
                        }
                        "unannounce" -> {
                            val params = obj["params"] as? JsonObject ?: continue
                            val id = params["id"]?.jsonPrimitive?.intOrNull ?: continue
                            println("[Nt4ClientService] Server unannounced topic id: $id")
                            topicMap.remove(id)
                            cachedActiveTopics = null
                        }
                    }
                } else {
                    // This is a data update frame: {"topic": id, "time": timestamp, "value": value}
                    val topicId = obj["topic"]?.jsonPrimitive?.intOrNull ?: continue
                    val valueElement = obj["value"] ?: continue
                    val ntTopic = topicMap[topicId] ?: continue
                    val timestampUs = obj["time"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis() * 1_000
                    val timestampMs = timestampUs / 1_000

                    dispatchValue(ntTopic, valueElement, timestampMs, timestampUs, teamId, seasonId, robotId)
                }
            }
        } catch (e: Exception) {
            val rejectedCount = malformedTextFrameCount.incrementAndGet()
            if (rejectedCount == 1L || rejectedCount % MALFORMED_TEXT_LOG_INTERVAL == 0L) {
                println(
                    "[Nt4ClientService] Rejected malformed text frame " +
                        "(count=$rejectedCount, error=${e::class.java.simpleName})"
                )
            }
        }
    }

    private var binaryFrameCount = 0L
    private var lastBinaryDiagLog = System.currentTimeMillis()

    internal suspend fun handleIncomingBinary(
        bytes: ByteArray,
        teamId: String,
        seasonId: String,
        robotId: String
    ) {
        val messages = try {
            com.areslib.networktables.NT4WireProtocol.unpackMessageFrames(bytes)
        } catch (e: Exception) {
            println("ERROR decoding NT4 binary frame: ${e.message}")
            emptyList()
        }
        binaryFrameCount += messages.size
        val now = System.currentTimeMillis()
        if (now - lastBinaryDiagLog > 2000) {
            println("[Nt4ClientService] DIAG: $binaryFrameCount binary messages decoded in last 2s, topicMap.size=${topicMap.size}")
            lastBinaryDiagLog = now
            binaryFrameCount = 0
        }
        for (msg in messages) {
            if (msg.topicId == -1L) {
                val sentAtUs = (msg.value as? Number)?.toLong() ?: continue
                val receivedAtUs = localMonotonicTimeUs()
                val roundTripUs = receivedAtUs - sentAtUs
                if (roundTripUs >= 0 && roundTripUs < bestClockRoundTripUs) {
                    bestClockRoundTripUs = roundTripUs
                    serverTimeOffsetUs = msg.timestampUs + roundTripUs / 2L - receivedAtUs
                }
                continue
            }
            val timestampUs = if (msg.timestampUs <= 1L) System.currentTimeMillis() * 1_000L else msg.timestampUs
            val timestampMs = timestampUs / 1_000L
            val ntTopic = topicMap[msg.topicId.toInt()]
            if (ntTopic != null) {
                dispatchValue(ntTopic, msg.value, timestampMs, timestampUs, teamId, seasonId, robotId)
            }
        }
    }

    private suspend fun dispatchValue(
        ntTopic: Nt4Topic,
        valueElement: Any?,
        timestampMs: Long,
        timestampUs: Long,
        teamId: String,
        seasonId: String,
        robotId: String
    ) {
        // Normalize key: strip leading '/' for consistent matching everywhere
        val normalizedName = com.ares.analytics.service.log.TelemetryTopicExtractor.normalizeTopic(ntTopic.name.removePrefix("/"))

        if (discoveredKeys.add(normalizedName)) {
            println("[Nt4ClientService] Discovered telemetry key: $normalizedName (type=${ntTopic.type})")
            cachedActiveTopics = null
        }

        // Skip input topics that the dashboard publishes — they echo back from the
        // simulator and cause 50Hz recomposition storms across all widgets
        if (normalizedName.startsWith("ARES/Input/")) return

        // Note: ARES/Session/LogFilePath was previously linked to the session row, but
        // session↔logfile linkage is no longer tracked (the DuckDB session schema has no
        // log_file_path column), so the topic is now intentionally ignored.

        // Handle topology mapping directly
        if (normalizedName == "Topology/HardwareMap") {
            try {
                val topologyJson = if (valueElement is JsonPrimitive) valueElement.content else valueElement.toString()
                val topology = Json.decodeFromString<HardwareTopology>(topologyJson)
                _latestTopology.value = topology
                databaseService.insertTopology(topology)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        // Intercept and handle console log messages. Match a closed set of exact topic
        // names (case-insensitive equality) instead of a substring test — `contains("log")`
        // previously misclassified any telemetry topic containing "log" as console output (AUDIT H8).
        val lowerName = normalizedName.lowercase()
        if (lowerName == "ares/console" || lowerName == "robot/console" ||
            lowerName == "system/print" || lowerName == "robot/print") {
            try {
                val text = if (valueElement is JsonPrimitive) valueElement.content else valueElement.toString()
                val severity = when {
                    text.contains("[ERROR]", ignoreCase = true) || text.contains("error:", ignoreCase = true) -> "ERROR"
                    text.contains("[WARN]", ignoreCase = true) || text.contains("warning:", ignoreCase = true) -> "WARN"
                    else -> "INFO"
                }
                val session = _currentSession.value
                val sessionId = session?.sessionId ?: "live-telemetry"
                val consoleMsg = ConsoleMessage(timestampMs, text, severity)

                // Save in DB if session is active
                if (session != null) {
                    serviceScope.launch {
                        databaseService.insertConsoleMessages(listOf(consoleMsg), sessionId)
                    }
                }
                _consoleFlow.emit(consoleMsg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Console lines are persisted exclusively as console messages; falling through
            // here would double-persist each line as a telemetry frame under the same key.
            return
        }

        if (valueElement is JsonArray || valueElement is List<*> || valueElement is DoubleArray || valueElement is FloatArray || valueElement is Array<*>) {
            val size = when (valueElement) {
                is JsonArray -> valueElement.size
                is List<*> -> valueElement.size
                is DoubleArray -> valueElement.size
                is FloatArray -> valueElement.size
                is Array<*> -> valueElement.size
                else -> 0
            }
            if (size > MAX_INCOMING_ARRAY_ELEMENTS) {
                println("[Nt4ClientService] Rejected oversized array topic $normalizedName ($size elements)")
                return
            }

            val sb = StringBuilder(normalizedName).append("/")
            val baseLen = sb.length

            for (idx in 0 until size) {
                val element = when (valueElement) {
                    is JsonArray -> valueElement[idx]
                    is List<*> -> valueElement[idx]
                    is DoubleArray -> valueElement[idx]
                    is FloatArray -> valueElement[idx]
                    is Array<*> -> valueElement[idx]
                    else -> null
                }

                val (doubleValue, stringValue) = coerceTelemetryValue(element)
                sb.setLength(baseLen)
                val frameKey = sb.append(idx).toString()
                val frame = sessionMutex.withLock {
                    TelemetryFrame(
                        timestampMs = timestampMs,
                        sessionId = _currentSession.value?.sessionId ?: LIVE_SESSION_ID,
                        key = frameKey,
                        value = doubleValue,
                        stringValue = stringValue,
                        timestampUs = timestampUs
                    ).also { pendingFrames.send(it) }
                }
                telemetryStore.accept(frame, notifyConsumers = !isReplayActive.value)
            }
            return
        }

        // Extract double value and string value
        val (doubleValue, stringValue) = coerceTelemetryValue(valueElement)
        val frame = sessionMutex.withLock {
            TelemetryFrame(
                timestampMs = timestampMs,
                sessionId = _currentSession.value?.sessionId ?: LIVE_SESSION_ID,
                key = normalizedName,
                value = doubleValue,
                stringValue = stringValue,
                timestampUs = timestampUs
            ).also { pendingFrames.send(it) }
        }
        telemetryStore.accept(frame, notifyConsumers = !isReplayActive.value)
    }
    private var nextPubUid = 2000
    private val dynamicPubUids = ConcurrentHashMap<String, Int>().apply {
        put("ARES/DriverStation/Command", 1011)
        put("ARES/DriverStation/SelectedOpMode", 1012)
        put("ARES/DriverStation/MatchTime", 1013)
        put("ARES/DriverStation/MatchState", 1014)
        put("SysId/Command", 1015)
        put("SysId/EnableToken", 1016)
        put("SysId/EnableLease", 1017)
        put("ARES/Input/driveFrame", 1020)
    }
    private val publisherTypes = ConcurrentHashMap<String, String>().apply {
        put("ARES/DriverStation/Command", "string")
        put("ARES/DriverStation/SelectedOpMode", "string")
        put("ARES/DriverStation/MatchTime", "double")
        put("ARES/DriverStation/MatchState", "string")
        put("SysId/Command", "string")
        put("SysId/EnableToken", "string")
        put("SysId/EnableLease", "double")
        put("ARES/Input/driveFrame", "double[]")
    }
    private val dynamicPubMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun publishDouble(key: String, value: Double) {
        val cleanKey = key.removePrefix("/")
        require(!cleanKey.startsWith("ARES/Input/")) {
            "ARES/Input controls must use the atomic driveFrame publisher"
        }
        val pubuid = dynamicPubMutex.withLock {
            require(publisherTypes.putIfAbsent(cleanKey, "double") in arrayOf(null, "double")) {
                "NT4 topic $cleanKey was already published with a different type"
            }
            var id = dynamicPubUids[cleanKey]
            if (id == null) {
                id = nextPubUid++
                dynamicPubUids[cleanKey] = id
                webSocketSession?.send(Frame.Text(buildPublishMessage(cleanKey, id, "double")))
            }
            id
        }
        val frame = TelemetryFrame(
            timestampMs = System.currentTimeMillis(),
            sessionId = _currentSession.value?.sessionId ?: "live-telemetry",
            key = cleanKey,
            value = value
        )
        telemetryStore.accept(frame)

        publishInputDouble(pubuid, value)
    }

    /** Publishes a typed boolean topic; tuning must not encode booleans as doubles. */
    suspend fun publishBoolean(key: String, value: Boolean) {
        val cleanKey = key.removePrefix("/")
        require(!cleanKey.startsWith("ARES/Input/")) { "ARES/Input controls must use the atomic driveFrame publisher" }
        val pubuid = dynamicPubMutex.withLock {
            require(publisherTypes.putIfAbsent(cleanKey, "boolean") in arrayOf(null, "boolean")) {
                "NT4 topic $cleanKey was already published with a different type"
            }
            dynamicPubUids[cleanKey] ?: nextPubUid++.also { id ->
                dynamicPubUids[cleanKey] = id
                webSocketSession?.send(Frame.Text(buildPublishMessage(cleanKey, id, "boolean")))
            }
        }
        telemetryStore.accept(TelemetryFrame(System.currentTimeMillis(), _currentSession.value?.sessionId ?: "live-telemetry", cleanKey, if (value) 1.0 else 0.0))
        sendBinaryUpdate(pubuid, 0.toByte(), byteArrayOf(if (value) 0xc3.toByte() else 0xc2.toByte()))
    }

    suspend fun publishString(key: String, value: String) {
        val cleanKey = key.removePrefix("/")
        require(!cleanKey.startsWith("ARES/Input/") || cleanKey in ALLOWED_INPUT_STRING_TOPICS) {
            "ARES/Input controls must use driveFrame; only field-configuration strings are separate"
        }
        val pubuid = dynamicPubMutex.withLock {
            require(publisherTypes.putIfAbsent(cleanKey, "string") in arrayOf(null, "string")) {
                "NT4 topic $cleanKey was already published with a different type"
            }
            var id = dynamicPubUids[cleanKey]
            if (id == null) {
                id = nextPubUid++
                dynamicPubUids[cleanKey] = id
                webSocketSession?.send(Frame.Text(buildPublishMessage(cleanKey, id, "string")))
            }
            id
        }
        val frame = TelemetryFrame(
            timestampMs = System.currentTimeMillis(),
            sessionId = _currentSession.value?.sessionId ?: "live-telemetry",
            key = cleanKey,
            value = 0.0,
            stringValue = value
        )
        telemetryStore.accept(frame)

        publishInputString(pubuid, value)
    }

    /** Selects the alliance encoded into every subsequent atomic control frame. */
    fun selectRedAlliance(value: Boolean) {
        _selectedRedAlliance.value = value
    }

    /** Returns a process-unique safe integer nonce for a new control session. */
    fun nextDriveSessionNonce(): Double = driveSessionNonceCounter.getAndUpdate { current ->
        if (current >= DriveFrameContractValidator.MAX_SAFE_INTEGER_LONG) 1L else current + 1L
    }.toDouble()

    suspend fun publishDriveFrame(values: DoubleArray): Boolean {
        return driveFramePublishMutex.withLock {
            // Snapshot the caller-owned buffer before any suspension so the validated values,
            // wire bytes, and diagnostic telemetry cannot diverge under concurrent mutation.
            val frame = values.copyOf()
            val pendingState = driveFrameValidator.validate(frame)
            if (!publishInputDoubleArray(1020, frame)) return@withLock false
            driveFrameValidator.commit(pendingState)

            val now = System.currentTimeMillis()
            val sessionId = _currentSession.value?.sessionId ?: "live-telemetry"
            frame.forEachIndexed { index, value ->
                telemetryStore.accept(
                    TelemetryFrame(now, sessionId, "ARES/Input/driveFrame/$index", value)
                )
            }
            true
        }
    }

    fun subscribeDouble(key: String): Flow<Double> {
        return telemetryFlow.filter { it.key == key }.map { it.value }
    }

    internal fun coerceTelemetryValue(valueElement: Any?): Pair<Double, String?> = when (valueElement) {
        is JsonPrimitive -> when {
            valueElement.isString -> (valueElement.content.toDoubleOrNull() ?: 0.0) to valueElement.content
            valueElement.booleanOrNull != null -> (if (valueElement.boolean) 1.0 else 0.0) to null
            else -> (valueElement.doubleOrNull ?: 0.0) to null
        }
        is Boolean -> (if (valueElement) 1.0 else 0.0) to null
        is Number -> valueElement.toDouble() to null
        is String -> (valueElement.toDoubleOrNull() ?: 0.0) to valueElement
        else -> 0.0 to null
    }

    companion object {
        private const val MAX_INCOMING_ARRAY_ELEMENTS = 4_096
        private const val MAX_STRING_BYTES = 65_536
        private const val MAX_TEXT_FRAME_CHARS = 1_048_576
        private const val MAX_TEXT_FRAME_MESSAGES = 1_024
        private const val MALFORMED_TEXT_LOG_INTERVAL = 100L
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 10_000L
        private const val HEALTHY_CONNECTION_MS = 10_000L
        private const val CLOSE_HANDSHAKE_TIMEOUT_MS = 1_000L
        private const val SHUTDOWN_FLUSH_ATTEMPTS = 5
        private const val SHUTDOWN_FLUSH_RETRY_MS = 100L
        private val ALLOWED_INPUT_STRING_TOPICS = setOf(
            "ARES/Input/obstacles",
            "ARES/Input/fieldConfig"
        )
        private val FIXED_PUBLISH_TOPICS = setOf(
            "ARES/DriverStation/Command",
            "ARES/DriverStation/SelectedOpMode",
            "ARES/DriverStation/MatchTime",
            "ARES/DriverStation/MatchState",
            "SysId/Command",
            "SysId/EnableToken",
            "SysId/EnableLease",
            "ARES/Input/driveFrame"
        )
        internal const val LIVE_SESSION_ID = "live-telemetry"
        /** Amount of recent live telemetry intentionally retained in the ephemeral database. */
        internal const val LIVE_RETENTION_MS = 300_000L
        internal val CANONICAL_SUBSCRIPTION_PREFIXES = listOf(
            "/ARES", "/Drive", "/Robot", "/Hardware", "/Topology", "/Tuning",
            "/Profiling", "/Diagnostics", "/Vision", "/Path", "/Gamepad1", "/Gamepad2",
            "/Superstructure", "/Calibration", "/SysId", "/Swerve", "/Mechanism",
            "/LoopTimeMs", "/TimestampMs"
        )
    }
}

internal data class DriveFrameSendState(
    val sessionNonce: Double,
    val sequence: Double,
    val clientMonotonicMs: Double
)

/** Stateful sender-side mirror of the receiver's fail-closed v2 session contract. */
internal class DriveFrameContractValidator {
    private var committedState: DriveFrameSendState? = null

    fun validate(values: DoubleArray): DriveFrameSendState {
        require(values.size == VALUE_COUNT) { "drive frame must contain exactly 8 values" }
        require(values.all(Double::isFinite)) { "drive frame values must be finite" }
        require(values[0] == PROTOCOL_VERSION) { "unsupported drive frame protocol" }
        requireSafeInteger(values[1], positive = true, label = "session nonce")
        requireSafeInteger(values[2], positive = false, label = "sequence")
        requireSafeInteger(values[3], positive = false, label = "client monotonic time")
        requireSafeInteger(values[7], positive = false, label = "flags")
        require(values[7] <= MAX_CONTROL_FLAGS.toDouble()) { "drive flags contain unknown bits" }
        require(kotlin.math.abs(values[4]) <= MAX_TRANSLATION_MPS &&
            kotlin.math.abs(values[5]) <= MAX_TRANSLATION_MPS) {
            "drive translation exceeds $MAX_TRANSLATION_MPS m/s"
        }
        require(kotlin.math.abs(values[6]) <= MAX_ANGULAR_RPS) {
            "drive rotation exceeds $MAX_ANGULAR_RPS rad/s"
        }

        val next = DriveFrameSendState(values[1], values[2], values[3])
        val current = committedState
        if (current == null || current.sessionNonce != next.sessionNonce) {
            val flags = values[7].toLong()
            require(values[4] == 0.0 && values[5] == 0.0 && values[6] == 0.0) {
                "a new drive session must begin with neutral axes"
            }
            require((flags and NEUTRAL_REQUIRED_CLEAR_FLAGS) == 0L) {
                "a new drive session must begin with neutral actuator and edge flags"
            }
        } else {
            require(next.sequence > current.sequence) { "drive sequence must strictly increase" }
            require(next.clientMonotonicMs >= current.clientMonotonicMs) {
                "drive client monotonic time moved backwards"
            }
        }
        return next
    }

    fun commit(state: DriveFrameSendState) {
        committedState = state
    }

    fun reset() {
        committedState = null
    }

    private fun requireSafeInteger(value: Double, positive: Boolean, label: String) {
        require(value == kotlin.math.floor(value) && value <= MAX_SAFE_INTEGER_DOUBLE &&
            (if (positive) value > 0.0 else value >= 0.0)) {
            "drive $label must be ${if (positive) "a positive" else "a non-negative"} exactly representable integer"
        }
    }

    companion object {
        const val VALUE_COUNT = 8
        const val MAX_SAFE_INTEGER_LONG = 9_007_199_254_740_991L
        private const val PROTOCOL_VERSION = 2.0
        private const val MAX_SAFE_INTEGER_DOUBLE = 9_007_199_254_740_991.0
        private const val MAX_CONTROL_FLAGS = (1 shl 10) - 1
        private const val MAX_TRANSLATION_MPS = 8.0
        private const val MAX_ANGULAR_RPS = 12.566370614359172
        private const val NEUTRAL_REQUIRED_CLEAR_FLAGS = 0x3C7L
    }
}

data class Nt4ConnectionMetrics(
    val attempts: Long,
    val successfulConnections: Long,
    val reconnects: Long,
    val connected: Boolean
)
