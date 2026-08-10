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
 * @param databaseService SQLite log persistence engine for historical telemetry recording.
 * @see TelemetryFrame
 * @see DatabaseService
 */
open class Nt4ClientService(
    private val databaseService: DatabaseService
) {
    /**
     * Opt-in verbose NT4 frame logging (e.g. heartbeat hex dumps). Enabled with
     * `-Dares.debug.nt4=true`. Off by default to avoid the 50Hz allocation + println storm.
     */
    private val NT4_DEBUG: Boolean = java.lang.Boolean.getBoolean("ares.debug.nt4")

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
    val isReplayActive = MutableStateFlow(false)

    private val _telemetryFlow = MutableSharedFlow<TelemetryFrame>(
        replay = 100,
        extraBufferCapacity = 4096,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.SUSPEND
    )
    open val telemetryFlow: SharedFlow<TelemetryFrame> = _telemetryFlow.asSharedFlow()

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
        _telemetryFlow.emit(frame)
    }

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private var webSocketSession: DefaultClientWebSocketSession? = null

    // Topic ID to Topic Name mapping
    internal val topicMap = ConcurrentHashMap<Int, Nt4Topic>()
    private val discoveredKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val latestValues = ConcurrentHashMap<String, TelemetryFrame>()
    val telemetryHistory = ConcurrentHashMap<String, java.util.ArrayDeque<TelemetryFrame>>()

    private var cachedActiveTopics: List<String>? = null

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
                databaseService.pruneTelemetryFrames(LIVE_SESSION_ID, newestTimestamp - 300_000)
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
        latestValues.clear()
        telemetryHistory.clear()
        cachedActiveTopics = null
    }

    private var clientJob: Job? = null
    private val connectionMutex = kotlinx.coroutines.sync.Mutex()

    fun start(host: String, teamId: String, seasonId: String, robotId: String, port: Int = 5810) {
        println("[Nt4ClientService] start() called with host=$host, port=$port, teamId=$teamId, seasonId=$seasonId, robotId=$robotId")
        serviceScope.launch {
            connectionMutex.withLock {
                clientJob?.cancelAndJoin()
                while (isActive && !flushPendingFrames()) delay(250)
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
                val clientName = "ARES-Analytics-${System.currentTimeMillis()}"
                val path = "/nt/$clientName"
                val url = "ws://$activeHost:$port$path"
                this@Nt4ClientService.serverIp = activeHost
                try {
                    val activeEngine = if (activeHost == "127.0.0.1" || activeHost == "localhost") "CIO" else "OkHttp"
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
                        _isConnected.value = true
                        webSocketSession = this
                        topicMap.clear()
                        retryDelay = 1000L

                        // 1. Announce input topics
                        val announceInputsMsg = """
                            [
                              {"method": "publish", "params": {"name": "ARES/Input/vx", "pubuid": 1001, "type": "double"}},
                              {"method": "publish", "params": {"name": "ARES/Input/vy", "pubuid": 1002, "type": "double"}},
                              {"method": "publish", "params": {"name": "ARES/Input/omega", "pubuid": 1003, "type": "double"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isIntaking", "pubuid": 1004, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isFlywheelOn", "pubuid": 1005, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isTransferring", "pubuid": 1006, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isTeleopMode", "pubuid": 1007, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isFieldCentric", "pubuid": 1008, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isRedAlliance", "pubuid": 1009, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/heartbeat", "pubuid": 1010, "type": "int"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isButtonAPressed", "pubuid": 1016, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isButtonBPressed", "pubuid": 1017, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isButtonXPressed", "pubuid": 1018, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/Input/isPoseReset", "pubuid": 1019, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "ARES/DriverStation/Command", "pubuid": 1011, "type": "string"}},
                              {"method": "publish", "params": {"name": "ARES/DriverStation/SelectedOpMode", "pubuid": 1012, "type": "string"}},
                              {"method": "publish", "params": {"name": "ARES/DriverStation/MatchTime", "pubuid": 1013, "type": "double"}},
                              {"method": "publish", "params": {"name": "ARES/DriverStation/MatchState", "pubuid": 1014, "type": "string"}},
                              {"method": "publish", "params": {"name": "SysId/Command", "pubuid": 1015, "type": "string"}}
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
                                if (key.startsWith("ARES/Input/") || key.startsWith("ARES/DriverStation/") || key.startsWith("SysId/")) {
                                    continue
                                }
                                val announceMsg = "[{\"method\": \"publish\", \"params\": {\"name\": \"$key\", \"pubuid\": $id, \"type\": \"double\"}}]"
                                send(Frame.Text(announceMsg))
                            }
                        }

                        // Start connection-alive heartbeat loop at 50Hz (20ms interval)
                        val heartbeatJob = launch {
                            var heartbeat = 0L
                            while (isActive) {
                                try {
                                    publishInputLong(1010, heartbeat++)
                                } catch (e: Exception) {
                                    break
                                }
                                delay(20)
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
                            try {
                                val reason = withContext(NonCancellable) {
                                    closeReason.await()
                                }
                                println("[Nt4ClientService] Connection to $url closed. Reason: ${reason?.message} (Code: ${reason?.code})")
                            } catch (_: Exception) {
                                // The reconnect loop owns cleanup even when the close handshake is unavailable.
                            }
                            webSocketSession = null
                            _isConnected.value = false
                        }
                    }
                } catch (e: Exception) {
                    println("[Nt4ClientService] Error connecting to $url: ${e.message}")
                    webSocketSession = null
                    _isConnected.value = false
                    // Backoff delay before reconnect
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(10000L)
                }
            }
        }
        }
        }
    }

    private suspend fun sendBinaryUpdate(pubuid: Int, typeId: Byte, valueBytes: ByteArray) {
        val timestampUs = System.nanoTime() / 1000L
        val buffer = encodeNt4BinaryUpdate(pubuid, timestampUs, typeId, valueBytes)

        // Heartbeat hex-dump is gated behind the `ares.debug.nt4` system property. It ran
        // unconditionally on every 50Hz heartbeat (pubuid 1010), allocating a joinToString +
        // String.format per frame and flooding stdout.
        if (pubuid == 1010 && NT4_DEBUG) {
            val bytesStr = buffer.joinToString("") { String.format("%02x", it) }
            println("[Nt4ClientService] sendBinaryUpdate 1010 (heartbeat): timestampUs=$timestampUs, buffer=$bytesStr")
        }

        webSocketSession?.send(Frame.Binary(true, buffer))
    }

    internal fun encodeNt4BinaryUpdate(
        pubuid: Int,
        timestampUs: Long,
        typeId: Byte,
        valueBytes: ByteArray
    ): ByteArray {
        val buffer = ByteArray(15 + valueBytes.size)

        // NT4 binary messages are a MsgPack array of update tuples. Even a single
        // update therefore needs the outer array header followed by the 4-tuple.
        buffer[0] = 0x91.toByte()
        buffer[1] = 0x94.toByte()

        // Write pubuid (encoded as MsgPack uint16)
        buffer[2] = 0xcd.toByte()
        buffer[3] = (pubuid shr 8).toByte()
        buffer[4] = pubuid.toByte()

        // Write timestampUs (encoded as MsgPack uint64)
        buffer[5] = 0xcf.toByte()
        buffer[6] = (timestampUs shr 56).toByte()
        buffer[7] = (timestampUs shr 48).toByte()
        buffer[8] = (timestampUs shr 40).toByte()
        buffer[9] = (timestampUs shr 32).toByte()
        buffer[10] = (timestampUs shr 24).toByte()
        buffer[11] = (timestampUs shr 16).toByte()
        buffer[12] = (timestampUs shr 8).toByte()
        buffer[13] = timestampUs.toByte()

        // Write typeId (encoded as positive fixint since typeId < 128)
        buffer[14] = typeId

        // Write value bytes (already MsgPack encoded)
        System.arraycopy(valueBytes, 0, buffer, 15, valueBytes.size)
        return buffer
    }

    private val publishDoubleBuffer = ThreadLocal.withInitial { ByteArray(9) }

    suspend fun publishInputDouble(pubuid: Int, value: Double) {
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
        sendBinaryUpdate(pubuid, 1.toByte(), valueBytes)
    }

    suspend fun publishInputString(pubuid: Int, value: String) {
        val strBytes = value.toByteArray(Charsets.UTF_8)
        val size = strBytes.size
        val headerBytes = when {
            size <= 31 -> byteArrayOf((0xa0 or size).toByte())
            size <= 255 -> byteArrayOf(0xd9.toByte(), size.toByte())
            size <= 65535 -> byteArrayOf(0xda.toByte(), (size shr 8).toByte(), size.toByte())
            else -> byteArrayOf(0xdb.toByte(), (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte())
        }
        val valueBytes = ByteArray(headerBytes.size + strBytes.size)
        System.arraycopy(headerBytes, 0, valueBytes, 0, headerBytes.size)
        System.arraycopy(strBytes, 0, valueBytes, headerBytes.size, strBytes.size)

        sendBinaryUpdate(pubuid, 4.toByte(), valueBytes)
    }

    suspend fun publishInputBoolean(pubuid: Int, value: Boolean) {
        val valueBytes = byteArrayOf(if (value) 0xc3.toByte() else 0xc2.toByte()) // MsgPack true/false markers
        sendBinaryUpdate(pubuid, 0.toByte(), valueBytes)
    }

    suspend fun publishInputLong(pubuid: Int, value: Long) {
        val valueBytes = ByteArray(9)
        valueBytes[0] = 0xd3.toByte() // MsgPack int64 marker
        valueBytes[1] = (value shr 56).toByte()
        valueBytes[2] = (value shr 48).toByte()
        valueBytes[3] = (value shr 40).toByte()
        valueBytes[4] = (value shr 32).toByte()
        valueBytes[5] = (value shr 24).toByte()
        valueBytes[6] = (value shr 16).toByte()
        valueBytes[7] = (value shr 8).toByte()
        valueBytes[8] = value.toByte()
        sendBinaryUpdate(pubuid, 2.toByte(), valueBytes)
    }

    suspend fun stop() {
        // cancelAndJoin (was a fire-and-forget cancel()) so the WebSocket receive/reconnect
        // loop fully unwinds before the HttpClients are closed below — otherwise the
        // still-running loop touches a closed client (use-after-close, AUDIT H14).
        clientJob?.cancelAndJoin()
        clientJob = null
        _isConnected.value = false
        webSocketSession = null
        // Final flush on a dedicated job, awaited before client teardown so no background
        // work outlives the clients.
        val flushJob = serviceScope.launch { flushPendingFrames() }
        flushJob.join()
        synchronized(this) {
            localClient?.close()
            localClient = null
            remoteClient?.close()
            remoteClient = null
        }
    }

    suspend fun publishFrame(frame: TelemetryFrame) {
        val finalFrame = sessionMutex.withLock {
            val sessionId = _currentSession.value?.sessionId ?: LIVE_SESSION_ID
            frame.copy(sessionId = sessionId).also { pendingFrames.send(it) }
        }
        if (!isReplayActive.value) {
            _telemetryFlow.emit(finalFrame)
        }
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
        try {
            val parsed = Json.parseToJsonElement(text)
            val jsonArray = parsed as? JsonArray ?: return
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

                            val expectedType = when {
                                name.endsWith("/vx") || name.endsWith("/vy") || name.endsWith("/omega") -> "double"
                                name.startsWith("ARES/Input/is") -> "boolean"
                                else -> null
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
                    val timestampMs = (obj["time"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis() * 1000) / 1000

                    dispatchValue(ntTopic, valueElement, timestampMs, teamId, seasonId, robotId)
                }
            }
        } catch (e: Exception) {
            // Ignore malformed frames
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
            val timestampMs = msg.timestampUs / 1000
            val ntTopic = topicMap[msg.topicId.toInt()]
            if (ntTopic != null) {
                dispatchValue(ntTopic, msg.value, timestampMs, teamId, seasonId, robotId)
            }
        }
    }

    private suspend fun dispatchValue(
        ntTopic: Nt4Topic,
        valueElement: Any?,
        timestampMs: Long,
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
        }

        if (valueElement is JsonArray || valueElement is List<*> || valueElement is DoubleArray || valueElement is FloatArray || valueElement is Array<*>) {
            val frames = mutableListOf<TelemetryFrame>()

            val size = when (valueElement) {
                is JsonArray -> valueElement.size
                is List<*> -> valueElement.size
                is DoubleArray -> valueElement.size
                is FloatArray -> valueElement.size
                is Array<*> -> valueElement.size
                else -> 0
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
                        stringValue = stringValue
                    ).also { pendingFrames.send(it) }
                }
                frames.add(frame)
                latestValues[frame.key] = frame
                val history = telemetryHistory.getOrPut(frame.key) { java.util.ArrayDeque() }
                synchronized(history) {
                    history.add(frame)
                    val cutoff = frame.timestampMs - 120_000
                    while (history.isNotEmpty() && history.first().timestampMs < cutoff) {
                        history.removeFirst()
                    }
                    while (history.size > 2000) {
                        history.removeFirst()
                    }
                }
                if (!isReplayActive.value) {
                    _telemetryFlow.emit(frame)
                }
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
                stringValue = stringValue
            ).also { pendingFrames.send(it) }
        }
        latestValues[frame.key] = frame
        val history = telemetryHistory.getOrPut(frame.key) { java.util.ArrayDeque() }
        synchronized(history) {
            history.add(frame)
            val cutoff = frame.timestampMs - 120_000
            while (history.isNotEmpty() && history.first().timestampMs < cutoff) {
                history.removeFirst()
            }
            while (history.size > 2000) {
                history.removeFirst()
            }
        }
        if (!isReplayActive.value) {
            _telemetryFlow.emit(frame)
        }
    }
    private var nextPubUid = 2000
    private val dynamicPubUids = ConcurrentHashMap<String, Int>().apply {
        put("ARES/Input/vx", 1001)
        put("ARES/Input/vy", 1002)
        put("ARES/Input/omega", 1003)
        put("ARES/Input/isIntaking", 1004)
        put("ARES/Input/isFlywheelOn", 1005)
        put("ARES/Input/isTransferring", 1006)
        put("ARES/Input/isTeleopMode", 1007)
        put("ARES/Input/isFieldCentric", 1008)
        put("ARES/Input/isRedAlliance", 1009)
        put("ARES/Input/heartbeat", 1010)
        put("ARES/DriverStation/Command", 1011)
        put("ARES/DriverStation/SelectedOpMode", 1012)
        put("ARES/DriverStation/MatchTime", 1013)
        put("ARES/DriverStation/MatchState", 1014)
        put("SysId/Command", 1015)
        put("ARES/Input/isButtonAPressed", 1016)
        put("ARES/Input/isButtonBPressed", 1017)
        put("ARES/Input/isButtonXPressed", 1018)
        put("ARES/Input/isPoseReset", 1019)
    }
    private val dynamicPubMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun publishDouble(key: String, value: Double) {
        val pubuid = dynamicPubMutex.withLock {
            var id = dynamicPubUids[key]
            if (id == null) {
                id = nextPubUid++
                dynamicPubUids[key] = id
                val announceMsg = "[{\"method\": \"publish\", \"params\": {\"name\": \"$key\", \"pubuid\": $id, \"type\": \"double\"}}]"
                webSocketSession?.send(Frame.Text(announceMsg))
            }
            id
        }
        val cleanKey = key.removePrefix("/")
        val frame = TelemetryFrame(
            timestampMs = System.currentTimeMillis(),
            sessionId = _currentSession.value?.sessionId ?: "live-telemetry",
            key = cleanKey,
            value = value
        )
        latestValues[cleanKey] = frame
        _telemetryFlow.emit(frame)

        publishInputDouble(pubuid, value)
    }

    suspend fun publishString(key: String, value: String) {
        val pubuid = dynamicPubMutex.withLock {
            var id = dynamicPubUids[key]
            if (id == null) {
                id = nextPubUid++
                dynamicPubUids[key] = id
                val announceMsg = "[{\"method\": \"publish\", \"params\": {\"name\": \"$key\", \"pubuid\": $id, \"type\": \"string\"}}]"
                webSocketSession?.send(Frame.Text(announceMsg))
            }
            id
        }
        val cleanKey = key.removePrefix("/")
        val frame = TelemetryFrame(
            timestampMs = System.currentTimeMillis(),
            sessionId = _currentSession.value?.sessionId ?: "live-telemetry",
            key = cleanKey,
            value = 0.0,
            stringValue = value
        )
        latestValues[cleanKey] = frame
        _telemetryFlow.emit(frame)

        publishInputString(pubuid, value)
    }

    suspend fun publishBoolean(key: String, value: Boolean) {
        val pubuid = dynamicPubMutex.withLock {
            var id = dynamicPubUids[key]
            if (id == null) {
                id = nextPubUid++
                dynamicPubUids[key] = id
                val announceMsg = "[{\"method\": \"publish\", \"params\": {\"name\": \"$key\", \"pubuid\": $id, \"type\": \"boolean\"}}]"
                webSocketSession?.send(Frame.Text(announceMsg))
            }
            id
        }
        val cleanKey = key.removePrefix("/")
        val frame = TelemetryFrame(
            timestampMs = System.currentTimeMillis(),
            sessionId = _currentSession.value?.sessionId ?: "live-telemetry",
            key = cleanKey,
            value = if (value) 1.0 else 0.0
        )
        latestValues[cleanKey] = frame
        _telemetryFlow.emit(frame)

        publishInputBoolean(pubuid, value)
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
        internal const val LIVE_SESSION_ID = "live-telemetry"
        internal val CANONICAL_SUBSCRIPTION_PREFIXES = listOf(
            "/ARES", "/Drive", "/Robot", "/Hardware", "/Topology", "/Tuning",
            "/Profiling", "/Diagnostics", "/Vision", "/Path", "/Gamepad1", "/Gamepad2",
            "/Superstructure", "/Calibration", "/SysId", "/Swerve", "/Mechanism",
            "/LoopTimeMs", "/TimestampMs"
        )
    }
}
