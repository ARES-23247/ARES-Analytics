package com.ares.analytics.service

import com.ares.analytics.shared.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

open class Nt4ClientService(
    private val databaseService: DatabaseService,
    private val client: HttpClient = HttpClient { install(WebSockets) }
) {
    private val _isConnected = MutableStateFlow(false)
    open val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _telemetryFlow = MutableSharedFlow<TelemetryFrame>(replay = 100)
    open val telemetryFlow: SharedFlow<TelemetryFrame> = _telemetryFlow.asSharedFlow()

    private val _consoleFlow = MutableSharedFlow<ConsoleMessage>(replay = 100)
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
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    private var clientJob: Job? = null

    fun start(host: String, teamId: String, seasonId: String, robotId: String) {
        println("[Nt4ClientService] start() called with host=$host, teamId=$teamId, seasonId=$seasonId, robotId=$robotId")
        Thread.dumpStack()
        clientJob?.cancel()
        clientJob = CoroutineScope(Dispatchers.IO).launch {
            // Launch periodic flush job in background
            launch {
                while (isActive) {
                    delay(1000)
                    flushPendingFrames()
                }
            }

            while (isActive) {
                val url = "ws://$host:5810/nt/v4/websocket?client=ARES-Analytics-${System.currentTimeMillis()}"
                try {
                    println("[Nt4ClientService] Attempting to connect to $url")
                    client.webSocket(url) {
                        println("[Nt4ClientService] Connected to $url successfully!")
                        _isConnected.value = true
                        webSocketSession = this
                        topicMap.clear()

                        // 1. Announce input topics
                        val announceInputsMsg = """
                            [
                              {"method": "publish", "params": {"name": "/ARES/Input/vx", "pubuid": 1001, "type": "double"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/vy", "pubuid": 1002, "type": "double"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/omega", "pubuid": 1003, "type": "double"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/isIntaking", "pubuid": 1004, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/isFlywheelOn", "pubuid": 1005, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/isTransferring", "pubuid": 1006, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/isTeleopMode", "pubuid": 1007, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/isFieldCentric", "pubuid": 1008, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/isRedAlliance", "pubuid": 1009, "type": "boolean"}},
                              {"method": "publish", "params": {"name": "/ARES/Input/heartbeat", "pubuid": 1010, "type": "int"}}
                            ]
                        """.trimIndent()
                        send(Frame.Text(announceInputsMsg))

                        // 2. Subscribe to all topics (using empty string to match all topics as a prefix)
                        val subMsg = """
                            [
                              {
                                "method": "subscribe",
                                "params": {
                                  "topics": [""],
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
                            val reason = closeReason.await()
                            println("[Nt4ClientService] Connection to $url closed. Reason: ${reason?.message} (Code: ${reason?.code})")
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

    fun stop() {
        clientJob?.cancel()
        _isConnected.value = false
        // Flush remaining frames asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            flushPendingFrames()
        }
        client.close()
    }

    suspend fun publishFrame(frame: TelemetryFrame) {
        val session = _currentSession.value
        val finalFrame = if (session != null) {
            frame.copy(sessionId = session.sessionId)
        } else {
            frame
        }
        if (session != null) {
            pendingFrames.add(finalFrame)
        }
        _telemetryFlow.emit(finalFrame)
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
                            topicMap[id] = Nt4Topic(id, name, type)
                        }
                        "unannounce" -> {
                            val params = obj["params"]?.jsonObject ?: continue
                            val id = params["id"]?.jsonPrimitive?.intOrNull ?: continue
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
        try {
            var offset = 0
            while (offset < bytes.size) {
                val marker = bytes[offset].toInt() and 0xFF
                val (arrayLen, arrayHeaderSize) = getArrayLengthAndHeader(marker, bytes, offset)

                // Each NT4 message is an array of 4 elements: [topicId, timestampUs, typeId, value]
                if (arrayLen != 4) {
                    val size = getMsgPackValueLength(bytes, offset)
                    if (size == 0) break
                    offset += size
                    continue
                }

                var currentOffset = offset + arrayHeaderSize

                // 1. Topic ID
                val (topicIdJson, topicIdSize) = parseMsgPackValue(bytes, currentOffset, 2)
                val topicId = topicIdJson.jsonPrimitive.intOrNull ?: -1
                currentOffset += topicIdSize

                // 2. Timestamp (us)
                val (timestampJson, timestampSize) = parseMsgPackValue(bytes, currentOffset, 2)
                val timestampUs = timestampJson.jsonPrimitive.longOrNull ?: 0L
                currentOffset += timestampSize

                // 3. Type ID
                val (typeIdJson, typeIdSize) = parseMsgPackValue(bytes, currentOffset, 2)
                val typeId = typeIdJson.jsonPrimitive.intOrNull ?: 0
                currentOffset += typeIdSize

                // 4. Value
                val (valueElement, valueSize) = parseMsgPackValue(bytes, currentOffset, typeId)
                currentOffset += valueSize

                val timestampMs = timestampUs / 1000
                val ntTopic = topicMap[topicId]

                if (ntTopic != null) {
                    dispatchValue(ntTopic, valueElement, timestampMs, teamId, seasonId, robotId)
                }

                offset = currentOffset
            }
        } catch (e: Exception) {
            println("[Nt4ClientService] Error handling incoming binary: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun dispatchValue(
        ntTopic: Nt4Topic,
        valueElement: JsonElement,
        timestampMs: Long,
        teamId: String,
        seasonId: String,
        robotId: String
    ) {
        // Normalize key: strip leading '/' for consistent matching everywhere
        val normalizedName = ntTopic.name.removePrefix("/")

        if (discoveredKeys.add(normalizedName)) {
            println("[Nt4ClientService] Discovered telemetry key: $normalizedName (type=${ntTopic.type})")
        }

        // Skip input topics that the dashboard publishes — they echo back from the
        // simulator and cause 50Hz recomposition storms across all widgets
        if (normalizedName.startsWith("ARES/Input/")) return

        // Intercept log file path linkage
        if (normalizedName == "ARES/Session/LogFilePath") {
            try {
                val logFilePath = (valueElement as? JsonPrimitive)?.content ?: return
                val session = _currentSession.value
                if (session != null) {
                    CoroutineScope(Dispatchers.IO).launch {
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
                val topologyJson = valueElement.jsonPrimitive.content
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
                    CoroutineScope(Dispatchers.IO).launch {
                        databaseService.insertConsoleMessages(listOf(consoleMsg), sessionId)
                    }
                }
                _consoleFlow.emit(consoleMsg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (valueElement is JsonArray) {
            val session = _currentSession.value
            val sessionId = session?.sessionId ?: "live-telemetry"
            val frames = mutableListOf<TelemetryFrame>()
            
            for (idx in 0 until valueElement.size) {
                val doubleValue = valueElement[idx].jsonPrimitive.doubleOrNull ?: 0.0
                val frame = TelemetryFrame(
                    timestampMs = timestampMs,
                    sessionId = sessionId,
                    key = "$normalizedName/$idx",
                    value = doubleValue
                )
                frames.add(frame)
                _telemetryFlow.emit(frame)
            }
            
            if (session != null) {
                pendingFrames.addAll(frames)
            }
            return
        }

        // Extract double value
        val doubleValue = when {
            valueElement is JsonPrimitive && valueElement.isString -> {
                valueElement.content.toDoubleOrNull() ?: 0.0
            }
            valueElement is JsonPrimitive -> {
                valueElement.doubleOrNull ?: 0.0
            }
            else -> 0.0
        }

        val session = _currentSession.value
        val sessionId = session?.sessionId ?: "live-telemetry"

        val frame = TelemetryFrame(
            timestampMs = timestampMs,
            sessionId = sessionId,
            key = normalizedName,
            value = doubleValue
        )

        // Write to DB if recording
        if (session != null) {
            pendingFrames.add(frame)
        }

        // Emit to flow
        _telemetryFlow.emit(frame)
    }

    private fun parseMsgPackValue(bytes: ByteArray, offset: Int, typeId: Int): Pair<JsonElement, Int> {
        if (offset >= bytes.size) return Pair(JsonNull, 0)
        val marker = bytes[offset].toInt() and 0xFF

        // Boolean Type
        if (typeId == 0) {
            return when (marker) {
                0xc2 -> Pair(JsonPrimitive(false), 1)
                0xc3 -> Pair(JsonPrimitive(true), 1)
                else -> Pair(JsonPrimitive(false), 1)
            }
        }

        // Double Type
        if (typeId == 1) {
            if (marker == 0xcb && offset + 8 < bytes.size) {
                val bits = readInt64(bytes, offset + 1)
                val value = java.lang.Double.longBitsToDouble(bits)
                return Pair(JsonPrimitive(value), 9)
            }
            return Pair(JsonPrimitive(0.0), 1)
        }

        // Integer Type (NT4 Type = 2)
        if (typeId == 2) {
            return when (marker) {
                0xcc -> { // uint8
                    if (offset + 1 < bytes.size) {
                        val value = bytes[offset + 1].toInt() and 0xFF
                        Pair(JsonPrimitive(value), 2)
                    } else Pair(JsonPrimitive(0), 1)
                }
                0xcd -> { // uint16
                    if (offset + 2 < bytes.size) {
                        val bits = readInt16(bytes, offset + 1)
                        Pair(JsonPrimitive(bits), 3)
                    } else Pair(JsonPrimitive(0), 1)
                }
                0xce -> { // uint32
                    if (offset + 4 < bytes.size) {
                        val bits = readInt32(bytes, offset + 1)
                        Pair(JsonPrimitive(bits.toLong() and 0xFFFFFFFFL), 5)
                    } else Pair(JsonPrimitive(0), 1)
                }
                0xcf -> { // uint64
                    if (offset + 8 < bytes.size) {
                        val bits = readInt64(bytes, offset + 1)
                        Pair(JsonPrimitive(bits), 9)
                    } else Pair(JsonPrimitive(0), 1)
                }
                0xd0 -> { // int8
                    if (offset + 1 < bytes.size) {
                        val value = bytes[offset + 1].toInt()
                        Pair(JsonPrimitive(value), 2)
                    } else Pair(JsonPrimitive(0), 1)
                }
                0xd1 -> { // int16
                    if (offset + 2 < bytes.size) {
                        val bits = readInt16(bytes, offset + 1).toShort()
                        Pair(JsonPrimitive(bits), 3)
                    } else Pair(JsonPrimitive(0), 1)
                }
                0xd2 -> { // int32
                    if (offset + 4 < bytes.size) {
                        val bits = readInt32(bytes, offset + 1)
                        Pair(JsonPrimitive(bits), 5)
                    } else Pair(JsonPrimitive(0), 1)
                }
                0xd3 -> { // int64
                    if (offset + 8 < bytes.size) {
                        val bits = readInt64(bytes, offset + 1)
                        Pair(JsonPrimitive(bits), 9)
                    } else Pair(JsonPrimitive(0), 1)
                }
                else -> {
                    if (marker in 0x00..0x7f) {
                        Pair(JsonPrimitive(marker), 1)
                    } else if (marker in 0xe0..0xff) {
                        val value = (marker - 256)
                        Pair(JsonPrimitive(value), 1)
                    } else {
                        Pair(JsonPrimitive(0), 1)
                    }
                }
            }
        }
        
        // Float Type (NT4 Type = 3)
        if (typeId == 3) {
            if (marker == 0xca && offset + 4 < bytes.size) {
                val bits = readInt32(bytes, offset + 1)
                val value = java.lang.Float.intBitsToFloat(bits).toDouble()
                return Pair(JsonPrimitive(value), 5)
            }
            return Pair(JsonPrimitive(0.0), 1)
        }

        // String Type (NT4 Type = 4)
        if (typeId == 4) {
            val (len, headerSize) = getStringLengthAndHeader(marker, bytes, offset)
            if (offset + headerSize + len <= bytes.size) {
                val strValue = String(bytes, offset + headerSize, len, Charsets.UTF_8)
                return Pair(JsonPrimitive(strValue), headerSize + len)
            }
            return Pair(JsonPrimitive(""), headerSize)
        }

        // Arrays (Boolean Array = 16, Double Array = 17, Integer Array = 18, Float Array = 19, String Array = 20)
        if (typeId in 16..20) {
            val (arrayLen, headerSize) = getArrayLengthAndHeader(marker, bytes, offset)
            var currentOffset = offset + headerSize
            val jsonArray = buildJsonArray {
                for (i in 0 until arrayLen) {
                    val elemTypeId = when (typeId) {
                        16 -> 0 // boolean
                        17 -> 1 // double
                        18 -> 2 // integer
                        19 -> 3 // float
                        20 -> 4 // string
                        else -> 1
                    }
                    val (elem, size) = parseMsgPackValue(bytes, currentOffset, elemTypeId)
                    add(elem)
                    currentOffset += size
                }
            }
            return Pair(jsonArray, currentOffset - offset)
        }

        val size = getMsgPackValueLength(bytes, offset)
        return Pair(JsonNull, size)
    }

    private fun readInt16(bytes: ByteArray, offset: Int): Int {
        return (((bytes[offset].toInt() and 0xFF) shl 8) or
                (bytes[offset + 1].toInt() and 0xFF))
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int {
        return (((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF))
    }

    private fun readInt64(bytes: ByteArray, offset: Int): Long {
        return (((bytes[offset].toLong() and 0xFF) shl 56) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 48) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 40) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 32) or
                ((bytes[offset + 4].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 5].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 6].toLong() and 0xFF) shl 8) or
                (bytes[offset + 7].toLong() and 0xFF))
    }

    private fun getStringLengthAndHeader(marker: Int, bytes: ByteArray, offset: Int): Pair<Int, Int> {
        return when {
            marker in 0xa0..0xbf -> Pair(marker - 0xa0, 1)
            marker == 0xd9 -> {
                if (offset + 1 < bytes.size) Pair(bytes[offset + 1].toInt() and 0xFF, 2)
                else Pair(0, 2)
            }
            marker == 0xda -> {
                if (offset + 2 < bytes.size) Pair(readInt16(bytes, offset + 1), 3)
                else Pair(0, 3)
            }
            marker == 0xdb -> {
                if (offset + 4 < bytes.size) Pair(readInt32(bytes, offset + 1), 5)
                else Pair(0, 5)
            }
            else -> Pair(0, 1)
        }
    }

    private fun getArrayLengthAndHeader(marker: Int, bytes: ByteArray, offset: Int): Pair<Int, Int> {
        return when {
            marker in 0x90..0x9f -> Pair(marker - 0x90, 1)
            marker == 0xdc -> {
                if (offset + 2 < bytes.size) Pair(readInt16(bytes, offset + 1), 3)
                else Pair(0, 3)
            }
            marker == 0xdd -> {
                if (offset + 4 < bytes.size) Pair(readInt32(bytes, offset + 1), 5)
                else Pair(0, 5)
            }
            else -> Pair(0, 1)
        }
    }

    private fun getMsgPackValueLength(bytes: ByteArray, offset: Int): Int {
        if (offset >= bytes.size) return 0
        val marker = bytes[offset].toInt() and 0xFF
        return when {
            marker in 0x00..0x7f || marker in 0xe0..0xff -> 1
            marker in 0x80..0x8f -> {
                val size = marker - 0x80
                var len = 1
                for (i in 0 until size * 2) {
                    len += getMsgPackValueLength(bytes, offset + len)
                }
                len
            }
            marker in 0x90..0x9f -> {
                val size = marker - 0x90
                var len = 1
                for (i in 0 until size) {
                    len += getMsgPackValueLength(bytes, offset + len)
                }
                len
            }
            marker in 0xa0..0xbf -> 1 + (marker - 0xa0)
            marker == 0xc0 || marker == 0xc2 || marker == 0xc3 -> 1
            marker == 0xc4 || marker == 0xd9 -> {
                if (offset + 1 < bytes.size) 2 + (bytes[offset + 1].toInt() and 0xFF) else 2
            }
            marker == 0xc5 || marker == 0xda -> {
                if (offset + 2 < bytes.size) 3 + readInt16(bytes, offset + 1) else 3
            }
            marker == 0xc6 || marker == 0xdb -> {
                if (offset + 4 < bytes.size) 5 + readInt32(bytes, offset + 1) else 5
            }
            marker == 0xca -> 5
            marker == 0xcb -> 9
            marker == 0xcc || marker == 0xd0 -> 2
            marker == 0xcd || marker == 0xd1 -> 3
            marker == 0xce || marker == 0xd2 -> 5
            marker == 0xcf || marker == 0xd3 -> 9
            marker == 0xdc -> {
                if (offset + 2 < bytes.size) {
                    val size = readInt16(bytes, offset + 1)
                    var len = 3
                    for (i in 0 until size) {
                        len += getMsgPackValueLength(bytes, offset + len)
                    }
                    len
                } else 3
            }
            marker == 0xdd -> {
                if (offset + 4 < bytes.size) {
                    val size = readInt32(bytes, offset + 1)
                    var len = 5
                    for (i in 0 until size) {
                        len += getMsgPackValueLength(bytes, offset + len)
                    }
                    len
                } else 5
            }
            marker == 0xde -> {
                if (offset + 2 < bytes.size) {
                    val size = readInt16(bytes, offset + 1)
                    var len = 3
                    for (i in 0 until size * 2) {
                        len += getMsgPackValueLength(bytes, offset + len)
                    }
                    len
                } else 3
            }
            marker == 0xdf -> {
                if (offset + 4 < bytes.size) {
                    val size = readInt32(bytes, offset + 1)
                    var len = 5
                    for (i in 0 until size * 2) {
                        len += getMsgPackValueLength(bytes, offset + len)
                    }
                    len
                } else 5
            }
            else -> 1
        }
    }
}

data class Nt4Topic(
    val id: Int,
    val name: String,
    val type: String
)
