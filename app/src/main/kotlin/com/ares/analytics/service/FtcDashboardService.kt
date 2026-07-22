package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class FtcDashboardService(
    private val nt4ClientService: Nt4ClientService,
    private val client: HttpClient = HttpClient { install(WebSockets) }
) {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var wsJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var session: WebSocketSession? = null

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun start(host: String = "192.168.43.1", port: Int = 8000) {
        wsJob?.cancel()
        wsJob = serviceScope.launch {
            while (isActive) {
                try {
                    val url = "ws://$host:$port/dash"
                    client.webSocket(url) {
                        session = this
                        _isConnected.value = true
                        
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleIncomingMessage(text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    _isConnected.value = false
                    session = null
                    delay(3000)
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(text: String) {
        try {
            val json = Json.parseToJsonElement(text).jsonObject
            val type = json["type"]?.jsonPrimitive?.content ?: return
            val data = json["data"] ?: return

            when (type) {
                "receiveTelemetry" -> {
                    val dataObj = data.jsonObject
                    val telemetryObj = dataObj["telemetry"]?.jsonObject ?: return
                    val now = System.currentTimeMillis()
                    
                    for ((key, value) in telemetryObj) {
                        val doubleVal = value.jsonPrimitive.doubleOrNull ?: continue
                        val frame = TelemetryFrame(
                            timestampMs = now,
                            sessionId = "live-telemetry",
                            key = "/FtcDashboard/$key",
                            value = doubleVal
                        )
                        nt4ClientService.publishFrame(frame)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun sendConfigUpdate(configJson: String) {
        val wsSession = session
        if (wsSession != null && wsSession.isActive) {
            serviceScope.launch {
                try {
                    val message = """
                        {
                            "type": "saveConfig",
                            "data": $configJson
                        }
                    """.trimIndent()
                    wsSession.send(Frame.Text(message))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun stop() {
        wsJob?.cancel()
        session = null
        _isConnected.value = false
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun dispose() {
        stop()
        serviceScope.cancel()
        client.close()
    }
}
