package com.ares.analytics.service

import com.ares.analytics.service.log.HootDecoderService
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HootDecoderServiceTest class.
 */
class HootDecoderServiceTest {

    @Test
    /**
     * testParseAndInsertTelemetry fun.
     */
    fun testParseAndInsertTelemetry() = runTest {
        val tempDb = File.createTempFile("hoot_db_test", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val driverAnalysisService = DriverAnalysisService(databaseService, sysIdService)
        val summaryEngineService = SummaryEngineService(databaseService, sysIdService, driverAnalysisService)
        val hootDecoderService = HootDecoderService(databaseService, summaryEngineService, sysIdService)

        // Create a mock CSV file mimicking owlet's output (timestamps in seconds)
        val tempCsv = File.createTempFile("hoot_test_log", ".csv").apply { deleteOnExit() }
        tempCsv.writeText(
            """
            time, /Drive/MotorFL/Voltage, /Drive/MotorFL/Velocity, /Drive/MotorFL/Current
            0.0, 1.0, 2.0, 3.0
            0.02, 1.1, 2.1, 3.1
            0.04, 1.2, 2.2, 3.2
            """.trimIndent()
        )
        val sessionId = "test-hoot-session"
        val (firstTime, lastTime, keys) = hootDecoderService.parseAndInsertTelemetry(tempCsv, sessionId)

        // Verify bounds (0.0s -> 0ms, 0.04s -> 40ms)
        assertEquals(0L, firstTime)
        assertEquals(40L, lastTime)
        
        // Verify keys
        assertTrue(keys.contains("/Drive/MotorFL/Voltage"))
        assertTrue(keys.contains("/Drive/MotorFL/Velocity"))
        assertTrue(keys.contains("/Drive/MotorFL/Current"))

        // Query database to verify values are correctly batch-inserted
        val voltages = databaseService.getTelemetryForKey(sessionId, "/Drive/MotorFL/Voltage")
        assertEquals(3, voltages.size)
        assertEquals(0L, voltages[0].timestampMs)
        assertEquals(1.0, voltages[0].value, 0.001)
        assertEquals(20L, voltages[1].timestampMs)
        assertEquals(1.1, voltages[1].value, 0.001)
        assertEquals(40L, voltages[2].timestampMs)
        assertEquals(1.2, voltages[2].value, 0.001)

        tempCsv.delete()
        tempDb.delete()
    }
}
