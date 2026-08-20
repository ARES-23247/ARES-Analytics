package com.ares.analytics.service

import com.areslib.networktables.NT4WireProtocol
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertTrue

/**
 * Connects to a LIVE running NT4Server on 127.0.0.1:5810
 * and monitors what frames arrive over 5 seconds.
 * Run this while the sim is running to diagnose real-world connectivity.
 */
class Nt4LiveWireDiagnosticTest {

    @Test
    fun testLiveWireConnectivity() = runBlocking {
        val client = HttpClient(OkHttp) { install(WebSockets) }
        val topicMap = ConcurrentHashMap<Int, String>()
        val receivedAnnounces = mutableListOf<String>()
        val receivedBinaryTopics = mutableListOf<String>()
        val latestValues = ConcurrentHashMap<String, Any?>()
        val updateCounts = ConcurrentHashMap<String, Int>()
        var textFrameCount = 0
        var binaryFrameCount = 0
        var announceCount = 0

        println("=== LIVE WIRE DIAGNOSTIC: Connecting to ws://127.0.0.1:5810 ===")

        val result = try {
            withTimeoutOrNull(7_000) {
                client.webSocket(
                    method = io.ktor.http.HttpMethod.Get,
                    host = "127.0.0.1",
                    port = 5810,
                    path = "/nt/ARES-Diagnostic-${System.currentTimeMillis()}",
                    request = {
                        headers.append("Sec-WebSocket-Protocol", "v4.1.networktables.first.wpi.edu")
                    }
                ) {
                    println("[DIAG] WebSocket connected!")

                    val subMsg = """
                        [{"method": "subscribe", "params": {"topics": [""], "subuid": 1, "options": {"prefix": true}}}]
                    """.trimIndent()
                    send(Frame.Text(subMsg))

                    if (System.getenv("ARES_LIVE_START_FTC_TELEOP") == "1") {
                        send(
                            Frame.Text(
                                """[{"method":"publish","params":{"name":"ARES/DriverStation/SelectedOpMode","pubuid":2091,"type":"string"}},{"method":"publish","params":{"name":"ARES/DriverStation/Command","pubuid":2092,"type":"string"}}]"""
                            )
                        )
                        delay(100)
                        send(
                            Frame.Binary(
                                true,
                                NT4WireProtocol.encodeValueMessage(
                                    2091,
                                    0,
                                    4,
                                    "org.firstinspires.ftc.teamcode.opmodes.ARESMecanumTeleOp",
                                ),
                            )
                        )
                        send(Frame.Binary(true, NT4WireProtocol.encodeValueMessage(2092, 0, 4, "INIT")))
                        delay(2_000)
                        send(Frame.Binary(true, NT4WireProtocol.encodeValueMessage(2092, 0, 4, "START")))
                        println("[DIAG] Requested FTC ARESMecanumTeleOp INIT -> START")
                    }

                    val readJob = launch {
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    textFrameCount++
                                    val text = frame.readText()
                                    try {
                                        val jsonArray = Json.parseToJsonElement(text).jsonArray
                                        for (element in jsonArray) {
                                            val obj = element.jsonObject
                                            val method = obj["method"]?.jsonPrimitive?.content
                                            if (method == "announce") {
                                                val params = obj["params"]?.jsonObject
                                                val name = params?.get("name")?.jsonPrimitive?.content ?: "?"
                                                val id = params?.get("id")?.jsonPrimitive?.intOrNull ?: -1
                                                val type = params?.get("type")?.jsonPrimitive?.content ?: "?"
                                                topicMap[id] = name
                                                receivedAnnounces.add("$name (id=$id, type=$type)")
                                                announceCount++
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                                is Frame.Binary -> {
                                    binaryFrameCount++
                                    val bytes = frame.readBytes()
                                    try {
                                        val messages = NT4WireProtocol.unpackMessageFrames(bytes)
                                        for (msg in messages) {
                                            val topicName = (topicMap[msg.topicId.toInt()] ?: "UNKNOWN(id=${msg.topicId})")
                                                .removePrefix("/")
                                            receivedBinaryTopics.add(topicName)
                                            msg.value?.let { latestValues[topicName] = it }
                                            updateCounts.compute(topicName) { _, count -> (count ?: 0) + 1 }
                                        }
                                    } catch (_: Exception) {}
                                }
                                else -> {}
                            }
                        }
                    }

                    delay(5_000)
                    readJob.cancel()
                }
                "OK"
            }
        } catch (_: Exception) {
            println("[DIAG] Live simulator server not running on port 5810 (skipping live-wire test).")
            null
        }

        if (result != null) {
            assertTrue(announceCount > 0, "Expected at least 1 announce from the server")
            println("[DIAG] Text frames=$textFrameCount, binary frames=$binaryFrameCount, announces=$announceCount")
            println(
                "[DIAG] Relevant announced topics=" +
                    topicMap.values
                        .filter { name ->
                            name.contains("ARES", ignoreCase = true) ||
                                name.contains("DriverStation", ignoreCase = true) ||
                                name.contains("Pose", ignoreCase = true)
                        }
                        .sorted()
                        .joinToString()
            )
            listOf(
                "ARES/Input/driveFrame",
                "ARES/DriverStation/Command",
                "ARES/DriverStation/ActiveOpModeState",
                "ARES/DriverStation/SelectedOpMode",
                "ARES/TruePose",
                "ARES/EstimatedPose/0",
                "ARES/EstimatedPose/1",
            ).forEach { topic ->
                println("[DIAG] $topic updates=${updateCounts[topic] ?: 0}, latest=${latestValues[topic]}")
            }
        }
        client.close()
    }
}
