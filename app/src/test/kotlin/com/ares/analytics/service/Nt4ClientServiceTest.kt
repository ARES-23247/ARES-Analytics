package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Nt4ClientServiceTest {
    private lateinit var tempDb: File
    private lateinit var databaseService: DatabaseService
    private lateinit var nt4ClientService: Nt4ClientService

    @BeforeTest
    fun setUp() {
        tempDb = File.createTempFile("nt4_test_db", ".db").apply { deleteOnExit() }
        databaseService = DatabaseService(tempDb.absolutePath)
        nt4ClientService = Nt4ClientService(databaseService)
    }

    @AfterTest
    fun tearDown() {
        nt4ClientService.stop()
        tempDb.delete()
    }

    @Test
    fun testAnnounceAndUnannounce() = runBlocking {
        // 1. Send announce payload
        val announcePayload = """
            [
              {
                "method": "announce",
                "params": {
                  "name": "/Drive/Pose_X",
                  "id": 42,
                  "type": "double"
                }
              }
            ]
        """.trimIndent()

        nt4ClientService.handleIncomingText(announcePayload, "team-1", "season-1", "robot-1")
        val topic = nt4ClientService.topicMap[42]
        assertTrue(topic != null)
        assertEquals("/Drive/Pose_X", topic.name)
        assertEquals(42, topic.id)
        assertEquals("double", topic.type)

        // 2. Send unannounce payload
        val unannouncePayload = """
            [
              {
                "method": "unannounce",
                "params": {
                  "id": 42
                }
              }
            ]
        """.trimIndent()

        nt4ClientService.handleIncomingText(unannouncePayload, "team-1", "season-1", "robot-1")
        assertTrue(nt4ClientService.topicMap[42] == null)
    }

    @Test
    fun testSingleValueDataUpdate() = runBlocking {
        // Announce topic first
        val announcePayload = """
            [
              {"method": "announce", "params": {"name": "/Drive/Pose_X", "id": 10, "type": "double"}}
            ]
        """.trimIndent()
        nt4ClientService.handleIncomingText(announcePayload, "team-1", "season-1", "robot-1")

        // Send value frame
        val valuePayload = """
            [
              {"topic": 10, "time": 1000000, "value": 1.25}
            ]
        """.trimIndent()

        withTimeout(2000) {
            nt4ClientService.handleIncomingText(valuePayload, "team-1", "season-1", "robot-1")
            val frame = nt4ClientService.telemetryFlow.first()
            assertEquals("/Drive/Pose_X", frame.key)
            assertEquals(1.25, frame.value)
            assertEquals(1000L, frame.timestampMs) // 1000000 micros = 1000 ms
        }
    }

    @Test
    fun testArrayValueDataUpdate() = runBlocking {
        // Announce array topic
        val announcePayload = """
            [
              {"method": "announce", "params": {"name": "/Drive/EstimatedPose", "id": 20, "type": "double[]"}}
            ]
        """.trimIndent()
        nt4ClientService.handleIncomingText(announcePayload, "team-1", "season-1", "robot-1")

        // Send array update
        val valuePayload = """
            [
              {"topic": 20, "time": 2000000, "value": [1.5, -2.5, 3.14]}
            ]
        """.trimIndent()

        val results = mutableListOf<TelemetryFrame>()
        
        // Let's capture the emitted frames from telemetryFlow
        val job = launch {
            nt4ClientService.telemetryFlow.collect {
                results.add(it)
            }
        }

        nt4ClientService.handleIncomingText(valuePayload, "team-1", "season-1", "robot-1")
        kotlinx.coroutines.delay(200)
        job.cancel()

        assertEquals(3, results.size)
        
        assertEquals("/Drive/EstimatedPose/0", results[0].key)
        assertEquals(1.5, results[0].value)
        
        assertEquals("/Drive/EstimatedPose/1", results[1].key)
        assertEquals(-2.5, results[1].value)

        assertEquals("/Drive/EstimatedPose/2", results[2].key)
        assertEquals(3.14, results[2].value)
    }

    @Test
    fun testMalformedPayloadResilience() = runBlocking {
        // Verify that malformed JSON payloads do not propagate errors or crash the service
        nt4ClientService.handleIncomingText("{invalid_json", "team-1", "season-1", "robot-1")
        nt4ClientService.handleIncomingText("[{method: 'non-existing'}]", "team-1", "season-1", "robot-1")
        assertTrue(true) // Reached here without exception
    }
}
