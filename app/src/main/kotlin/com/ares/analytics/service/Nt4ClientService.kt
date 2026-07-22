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

open class Nt4ClientService(
    private val databaseService: DatabaseService
) {
    /** OkHttp engine for localhost (clean WebSocket frames, no RSV bit crashes) */
    private val localClient: HttpClient = HttpClient(OkHttp) { install(WebSockets) }
    /** OkHttp engine for remote robot connections (clean WebSocket frames, no RSV bit crashes) */
    private val remoteClient: HttpClient = HttpClient(OkHttp) { install(WebSockets) }

    /** Select the appropriate engine based on target host */
    private fun clientFor(host: String): HttpClient = when (host) {
        "127.0.0.1", "localhost" -> localClient
        else -> remoteClient
    }
    var serverIp: String = "127.0.0.1"

    private val _isConnected = MutableStateFlow(false)
    open val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    val isReplayActive = MutableStateFlow(false)

    private val _telemetryFlow = MutableSharedFlow<TelemetryFrame>(
        replay = 100,

        extraBufferCapacity = 65536,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    open val telemetryFlow: SharedFlow<TelemetryFrame> = _telemetryFlow.asSharedFlow()

    private val _consoleFlow = MutableSharedFlow<ConsoleMessage>(
        replay = 100,
        extraBufferCapacity = 1024,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val consoleFlow: SharedFlow<ConsoleMessage> = _consoleFlow.asSharedFlow()

    /**
     * Injects a replay frame into the telemetry flow so dashboard widgets consume
     * replay data identically to live data. Called by the replay integration layer.
     */
    suspend fun emitReplayFrame(frame: TelemetryFrame) {
        _telemetryFlow.emit(frame)
        topicFlows[frame.key]?.value = frame.value
    }

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private var webSocketSession: DefaultClientWebSocketSession? = null

    // Topic ID to Topic Name mapping
    internal val topicMap = ConcurrentHashMap<Int, Nt4Topic>()
    private val discoveredKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val latestValues = ConcurrentHashMap<String, TelemetryFrame>()
    val telemetryHistory = ConcurrentHashMap<String, java.util.ArrayDeque<TelemetryFrame>>()

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun getActiveTopics(): List<String> {
        return topicMap.values.map { it.name.removePrefix("/") }.sorted()
    }

    private val pendingFrames = java.util.concurrent.ConcurrentLinkedQueue<TelemetryFrame>()

    suspend fun flushPendingFrames() {
        val framesToInsert = mutableListOf<TelemetryFrame>()
        while (true) {
            val frame = pendingFrames.poll() ?: break
            framesToInsert.add(frame)
        }
        if (framesToInsert.isNotEmpty()) {
            try {
                databaseService.insertTelemetryFrames(framesToInsert)
                val maxTimestamp = framesToInsert.filter { it.sessionId == "live-telemetry" }.maxOfOrNull { it.timestampMs }
                if (maxTimestamp != null) {
                    val cutoff = maxTimestamp - 300_000
                    databaseService.pruneTelemetryFrames("live-telemetry", cutoff)
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    private var clientJob: Job? = null

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun start(host: String, teamId: String, seasonId: String, robotId: String) {
        println("[Nt4ClientService] start() called with host=$host, teamId=$teamId, seasonId=$seasonId, robotId=$robotId")
        clientJob?.cancel()
        clientJob = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e -> e.printStackTrace() }).launch {
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

            while (isActive) {
                var activeHost = host
                val clientName = "ARES-Analytics-${System.currentTimeMillis()}"
                val path = "/nt/$clientName"
                val url = "ws://$activeHost:5810$path"
                this@Nt4ClientService.serverIp = activeHost
                try {
                    val activeEngine = if (activeHost == "127.0.0.1" || activeHost == "localhost") "CIO" else "OkHttp"
                    println("[Nt4ClientService] Attempting to connect to $url (engine=$activeEngine)")
                    clientFor(activeHost).webSocket(
                        method = HttpMethod.Get,
                        host = activeHost,
                        port = 5810,
                        path = path,
                        request = {
                            header("Sec-WebSocket-Protocol", "v4.1.networktables.first.wpi.edu")
                        }
                    ) {
                        println("[Nt4ClientService] Connected to $url successfully!")
                        _isConnected.value = true
                        webSocketSession = this
                        topicMap.clear()

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
                        val subMsg = """
                            [
                              {
                                "method": "subscribe",
                                "params": {
                                  "topics": ["", "ARES", "Tuning"],
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
                            for (frame in incoming) {
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
                            } catch (_: Exception) {}
                            webSocketSession = null
                            _isConnected.value = false
                        }
                    }
                } catch (e: Exception) {
                    println("[Nt4ClientService] Error connecting to $url: ${e.message}")
                    webSocketSession = null
                    _isConnected.value = false
                    // Backoff delay before reconnect
                    delay(3000)
                }
            }
        }
    }

    private suspend fun sendBinaryUpdate(pubuid: Int, typeId: Byte, valueBytes: ByteArray) {
        val timestampUs = System.currentTimeMillis() * 1000L
        val size = 14 + valueBytes.size
        val buffer = ByteArray(size)
        
        // Write array header (MsgPack array of 4 elements = 0x94)
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
        
        if (pubuid == 1010) {
            val bytesStr = buffer.joinToString("") { String.format("%02x", it) }
            println("[Nt4ClientService] sendBinaryUpdate 1010 (heartbeat): timestampUs=$timestampUs, buffer=$bytesStr")
        }
        
        webSocketSession?.send(Frame.Binary(true, buffer))
    }

    suspend fun publishInputDouble(pubuid: Int, value: Double) {
        val bits = java.lang.Double.doubleToRawLongBits(value)
        val valueBytes = ByteArray(9)
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

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun stop() {
        clientJob?.cancel()
        _isConnected.value = false
        // Flush remaining frames asynchronously
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e -> e.printStackTrace() }).launch {
            flushPendingFrames()
        }
        localClient.close()
        remoteClient.close()
    }

    suspend fun publishFrame(frame: TelemetryFrame) {
        val session = _currentSession.value
        val finalFrame = if (session != null) {
            frame.copy(sessionId = session.sessionId)
        } else {
            frame.copy(sessionId = "live-telemetry")
        }
        pendingFrames.add(finalFrame)
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
        databaseService.insertSession(session)
        _currentSession.value = session
        return session
    }

    suspend fun stopRecordingSession() {
        val session = _currentSession.value ?: return
        flushPendingFrames() // Flush any remaining buffered frames to SQLite
        val endTime = System.currentTimeMillis()
        val duration = endTime - session.createdAt
        val updated = session.copy(durationMs = duration)
        databaseService.insertSession(updated)
        _currentSession.value = null
    }

    internal suspend fun handleIncomingText(
        text: String,
        teamId: String,
        seasonId: String,
        robotId: String
    ) {
        try {
            val jsonArray = Json.parseToJsonElement(text).jsonArray
            for (element in jsonArray) {
                val obj = element.jsonObject
                val method = obj["method"]?.jsonPrimitive?.content

                if (method != null) {
                    when (method) {
                        "announce" -> {
                            val params = obj["params"]?.jsonObject ?: continue
                            val name = params["name"]?.jsonPrimitive?.content ?: continue
                            val id = params["id"]?.jsonPrimitive?.intOrNull ?: continue
                            val type = params["type"]?.jsonPrimitive?.content ?: "double"
                            println("[Nt4ClientService] Server announced topic: $name (id=$id, type=$type)")
                            topicMap[id] = Nt4Topic(id, name, type)
                        }
                        "unannounce" -> {
                            val params = obj["params"]?.jsonObject ?: continue
                            val id = params["id"]?.jsonPrimitive?.intOrNull ?: continue
                            println("[Nt4ClientService] Server unannounced topic id: $id")
                            topicMap.remove(id)
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

    internal suspend fun handleIncomingBinary(
        bytes: ByteArray,
        teamId: String,
        seasonId: String,
        robotId: String
    ) {
        val messages = com.areslib.networktables.NT4WireProtocol.unpackMessageFrames(bytes)
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
        }

        // Skip input topics that the dashboard publishes — they echo back from the
        // simulator and cause 50Hz recomposition storms across all widgets
        if (normalizedName.startsWith("ARES/Input/")) return

        // Intercept log file path linkage
        if (normalizedName == "ARES/Session/LogFilePath") {
            try {
                val logFilePath = when (valueElement) {
                    is JsonPrimitive -> valueElement.content
                    is String -> valueElement
                    else -> return
                }
                val session = _currentSession.value
                if (session != null) {
                    CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e -> e.printStackTrace() }).launch {
                        databaseService.updateSessionLogFilePath(session.sessionId, logFilePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

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

        // Intercept and handle console log messages
        val lowerName = normalizedName.lowercase()
        if (lowerName.contains("console") || lowerName.contains("log") || lowerName.contains("print")) {
            try {
                val text = if (valueElement is JsonPrimitive) valueElement.content else valueElement.toString()
                val severity = when {
                    text.contains("[ERROR]", ignoreCase = true) || text.contains("error:", ignoreCase = true) || lowerName.contains("error") -> "ERROR"
                    text.contains("[WARN]", ignoreCase = true) || text.contains("warning:", ignoreCase = true) || lowerName.contains("warn") -> "WARN"
                    else -> "INFO"
                }
                val session = _currentSession.value
                val sessionId = session?.sessionId ?: "live-telemetry"
                val consoleMsg = ConsoleMessage(timestampMs, text, severity)
                
                // Save in DB if session is active
                if (session != null) {
                    CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e -> e.printStackTrace() }).launch {
                        databaseService.insertConsoleMessages(listOf(consoleMsg), sessionId)
                    }
                }
                _consoleFlow.emit(consoleMsg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (valueElement is JsonArray || valueElement is List<*> || valueElement is DoubleArray || valueElement is FloatArray || valueElement is Array<*>) {
            val session = _currentSession.value
            val sessionId = session?.sessionId ?: "live-telemetry"
            val frames = mutableListOf<TelemetryFrame>()
            val list = when (valueElement) {
                is JsonArray -> valueElement.map { 
                    if (it is JsonPrimitive && it.isString) it.content else it.jsonPrimitive.doubleOrNull ?: 0.0 
                }
                is List<*> -> valueElement
                is DoubleArray -> valueElement.toList()
                is FloatArray -> valueElement.toList()
                is Array<*> -> valueElement.toList()
                else -> emptyList<Any?>()
            }
            
            for (idx in list.indices) {
                val element = list[idx]
                val doubleValue = when (element) {
                    is JsonPrimitive -> if (element.isString) element.content.toDoubleOrNull() ?: 0.0 else element.doubleOrNull ?: 0.0
                    is Number -> element.toDouble()
                    is String -> element.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
                val stringValue = when (element) {
                    is JsonPrimitive -> if (element.isString) element.content else null
                    is String -> element
                    else -> null
                }
                val frameKey = "$normalizedName/$idx"
                val frame = TelemetryFrame(
                    timestampMs = timestampMs,
                    sessionId = sessionId,
                    key = frameKey,
                    value = doubleValue,
                    stringValue = stringValue
                )
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
                topicFlows[frame.key]?.value = doubleValue
            }
            
            pendingFrames.addAll(frames)
            return
        }

        // Extract double value and string value
        val doubleValue = when (valueElement) {
            is JsonPrimitive -> if (valueElement.isString) valueElement.content.toDoubleOrNull() ?: 0.0 else valueElement.doubleOrNull ?: 0.0
            is Number -> valueElement.toDouble()
            is String -> valueElement.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        val stringValue = when (valueElement) {
            is JsonPrimitive -> if (valueElement.isString) valueElement.content else null
            is String -> valueElement
            else -> null
        }
        val session = _currentSession.value
        val sessionId = session?.sessionId ?: "live-telemetry"
        val frame = TelemetryFrame(
            timestampMs = timestampMs,
            sessionId = sessionId,
            key = normalizedName,
            value = doubleValue,
            stringValue = stringValue
        )

        pendingFrames.add(frame)
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
        topicFlows[frame.key]?.value = doubleValue
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
        topicFlows[cleanKey]?.value = value
        
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
        topicFlows[cleanKey]?.value = if (value) 1.0 else 0.0
        
        publishInputBoolean(pubuid, value)
    }


    private val topicFlows = ConcurrentHashMap<String, MutableStateFlow<Double>>()

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun subscribeDouble(key: String): Flow<Double> {
        val flow = topicFlows.getOrPut(key) {
            MutableStateFlow(latestValues[key]?.value ?: 0.0)
        }
        return flow.asStateFlow()
    }
}
