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

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private var webSocketSession: DefaultClientWebSocketSession? = null

    // Topic ID to Topic Name mapping
    private val topicMap = ConcurrentHashMap<Int, Nt4Topic>()

    fun getActiveTopics(): List<String> {
        return topicMap.values.map { it.name }.sorted()
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
                try {
                    val url = "ws://$host:5810/nt/v4/websocket"
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

                        // 2. Subscribe to all topics
                        val subMsg = """
                            [
                              {
                                "method": "subscribe",
                                "params": {
                                  "topics": ["/"],
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
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    handleIncomingText(text, teamId, seasonId, robotId)
                                }
                            }
                        } finally {
                            println("[Nt4ClientService] Connection to $url closed.")
                            webSocketSession = null
                            _isConnected.value = false
                        }
                    }
                } catch (e: Exception) {
                    println("[Nt4ClientService] Error connecting to ws://$host:5810/nt/v4/websocket: ${e.message}")
                    webSocketSession = null
                    _isConnected.value = false
                    // Backoff delay before reconnect
                    delay(3000)
                }
            }
        }
    }

    suspend fun publishInputDouble(pubuid: Int, value: Double) {
        val msg = """
            [
              {"method": "set", "params": {"pubuid": $pubuid, "val": $value}}
            ]
        """.trimIndent()
        webSocketSession?.send(Frame.Text(msg))
    }

    suspend fun publishInputBoolean(pubuid: Int, value: Boolean) {
        val msg = """
            [
              {"method": "set", "params": {"pubuid": $pubuid, "val": $value}}
            ]
        """.trimIndent()
        webSocketSession?.send(Frame.Text(msg))
    }

    suspend fun publishInputLong(pubuid: Int, value: Long) {
        val msg = """
            [
              {"method": "set", "params": {"pubuid": $pubuid, "val": $value}}
            ]
        """.trimIndent()
        webSocketSession?.send(Frame.Text(msg))
    }

    fun stop() {
        clientJob?.cancel()
        _isConnected.value = false
        // Flush remaining frames asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            flushPendingFrames()
        }
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

    private suspend fun handleIncomingText(
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

                    // Handle topology mapping directly
                    if (ntTopic.name == "/Topology/HardwareMap") {
                        try {
                            val topologyJson = valueElement.jsonPrimitive.content
                            val topology = Json.decodeFromString<HardwareTopology>(topologyJson)
                            databaseService.insertTopology(topology)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        continue
                    }

                    // Intercept and handle console log messages
                    val lowerName = ntTopic.name.lowercase()
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
                                key = "${ntTopic.name}/$idx",
                                value = doubleValue
                            )
                            frames.add(frame)
                            _telemetryFlow.emit(frame)
                        }
                        if (session != null) {
                            pendingFrames.addAll(frames)
                        }
                        continue
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
                        key = ntTopic.name,
                        value = doubleValue
                    )

                    // Write to DB if recording
                    if (session != null) {
                        pendingFrames.add(frame)
                    }

                    // Emit to flow
                    _telemetryFlow.emit(frame)
                }
            }
        } catch (e: Exception) {
            // Ignore malformed frames
        }
    }
}

data class Nt4Topic(
    val id: Int,
    val name: String,
    val type: String
)
