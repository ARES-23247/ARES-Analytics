package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nt4ClientServiceTest class.
 */
class Nt4ClientServiceTest {
    private lateinit var tempDb: File
    private lateinit var databaseService: DatabaseService
    private lateinit var nt4ClientService: Nt4ClientService

    @BeforeTest
    /**
     * setUp fun.
     */
    fun setUp() {
        tempDb = File.createTempFile("nt4_test_db", ".db").apply { deleteOnExit() }
        databaseService = DatabaseService(tempDb.absolutePath)
        nt4ClientService = Nt4ClientService(databaseService)
    }

    @AfterTest
    /**
     * tearDown fun.
     */
    fun tearDown() {
        // stop() is now suspend (it cancelAndJoins the WS loop before closing clients).
        runBlocking { nt4ClientService.stop() }
        tempDb.delete()
    }

    @Test
    /**
     * testAnnounceAndUnannounce fun.
     */
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
    /**
     * testSingleValueDataUpdate fun.
     */
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
            assertEquals("Drive/Pose_X", frame.key)
            assertEquals(1.25, frame.value)
            assertEquals(1000L, frame.timestampMs) // 1000000 micros = 1000 ms
        }
    }

    @Test
    /**
     * testArrayValueDataUpdate fun.
     */
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
        
        assertEquals("Drive/EstimatedPose/0", results[0].key)
        assertEquals(1.5, results[0].value)
        
        assertEquals("Drive/EstimatedPose/1", results[1].key)
        assertEquals(-2.5, results[1].value)

        assertEquals("Drive/EstimatedPose/2", results[2].key)
        assertEquals(3.14, results[2].value)
    }

    @Test
    /**
     * testMalformedPayloadResilience fun.
     */
    fun testMalformedPayloadResilience() = runBlocking {
        // Verify that malformed JSON payloads do not propagate errors or crash the service
        nt4ClientService.handleIncomingText("{invalid_json", "team-1", "season-1", "robot-1")
        nt4ClientService.handleIncomingText("[{method: 'non-existing'}]", "team-1", "season-1", "robot-1")
        assertTrue(true) // Reached here without exception
    }

    @Test
    fun `binary publish uses NT4 outer update array`() {
        val encoded = nt4ClientService.encodeNt4BinaryUpdate(
            pubuid = 1001,
            timestampUs = 0x0102030405060708L,
            typeId = 1,
            valueBytes = byteArrayOf(0xca.toByte(), 0xfe.toByte())
        )

        assertEquals(0x91.toByte(), encoded[0], "single-update batch header")
        assertEquals(0x94.toByte(), encoded[1], "four-element update tuple header")
        assertEquals(0xcd.toByte(), encoded[2], "pubuid uint16 marker")
        assertEquals(0xcf.toByte(), encoded[5], "timestamp uint64 marker")
        assertEquals(1.toByte(), encoded[14], "NT4 type id")
        assertTrue(encoded.takeLast(2).toByteArray().contentEquals(byteArrayOf(0xca.toByte(), 0xfe.toByte())))
    }

    @Test
    fun `canonical subscription prefixes cover every ARES publisher family`() {
        val prefixes = Nt4ClientService.CANONICAL_SUBSCRIPTION_PREFIXES
        listOf(
            "/ARES", "/Drive", "/Robot", "/Hardware", "/Topology", "/Tuning",
            "/Profiling", "/Diagnostics", "/Vision", "/Path", "/Gamepad1", "/Gamepad2",
            "/Superstructure", "/Calibration", "/SysId", "/Swerve"
        ).forEach { prefix -> assertTrue(prefix in prefixes, "missing subscription for $prefix") }
    }

    @Test
    fun `boolean telemetry is coerced to numeric one and zero`() {
        assertEquals(1.0, nt4ClientService.coerceTelemetryValue(true).first)
        assertEquals(0.0, nt4ClientService.coerceTelemetryValue(false).first)
        assertEquals(1.0, nt4ClientService.coerceTelemetryValue(JsonPrimitive(true)).first)
        assertEquals(0.0, nt4ClientService.coerceTelemetryValue(JsonPrimitive(false)).first)
    }

    @Test
    fun `failed database flush retains frames for ordered retry`() = runBlocking {
        nt4ClientService.publishFrame(
            com.ares.analytics.shared.TelemetryFrame(100L, "ignored", "Drive/Pose_X", 1.0)
        )
        databaseService.close()

        assertTrue(!nt4ClientService.flushPendingFrames())
        assertEquals(1, nt4ClientService.retainedRetryFrameCount())
    }

    @Test
    fun `target reset clears topics latest values and history`() {
        nt4ClientService.topicMap[1] = com.ares.analytics.service.nt4.Nt4Topic(1, "/Old/Value", "double")
        val frame = com.ares.analytics.shared.TelemetryFrame(1L, "live-telemetry", "Old/Value", 2.0)
        nt4ClientService.latestValues[frame.key] = frame
        nt4ClientService.telemetryHistory[frame.key] = java.util.ArrayDeque<TelemetryFrame>().apply { add(frame) }

        nt4ClientService.clearLiveTargetState()

        assertTrue(nt4ClientService.topicMap.isEmpty())
        assertTrue(nt4ClientService.latestValues.isEmpty())
        assertTrue(nt4ClientService.telemetryHistory.isEmpty())
        assertTrue(nt4ClientService.getActiveTopics().isEmpty())
    }
}
