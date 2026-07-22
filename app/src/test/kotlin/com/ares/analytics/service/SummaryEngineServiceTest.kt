package com.ares.analytics.service

import com.ares.analytics.shared.Session
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SummaryEngineServiceTest class.
 */
class SummaryEngineServiceTest {

    @Test
    /**
     * testGenerateSummary fun.
     */
    fun testGenerateSummary() = runTest {
        val tempDb = File.createTempFile("summary_db_test", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val driverAnalysisService = DriverAnalysisService(databaseService, sysIdService)
        val summaryEngine = SummaryEngineService(databaseService, sysIdService, driverAnalysisService)
        val session = Session(
            sessionId = "summary-session",
            teamId = "23247",
            seasonId = "2026",
            robotId = "ares-bot",
            createdAt = 1000L,
            durationMs = 120000L,
            tags = listOf("teleop", "battery-A")
        )

        // Insert mock telemetry frames
        val frames = listOf(
            TelemetryFrame(1100L, session.sessionId, "/Battery/Voltage", 12.6),
            TelemetryFrame(1200L, session.sessionId, "/Battery/Voltage", 11.2),
            TelemetryFrame(1300L, session.sessionId, "/Battery/Voltage", 0.0), // noise to be ignored
            TelemetryFrame(1100L, session.sessionId, "/Drive/EkfDrift", 0.05),
            TelemetryFrame(1200L, session.sessionId, "/Drive/EkfDrift", 0.18),
            TelemetryFrame(1100L, session.sessionId, "/Robot/LoopTimeMs", 20.0),
            TelemetryFrame(1200L, session.sessionId, "/Robot/LoopTimeMs", 24.0),
            TelemetryFrame(1300L, session.sessionId, "/Robot/LoopTimeMs", 18.0),
            TelemetryFrame(1100L, session.sessionId, "/Drive/MotorFL/Current", 8.0),
            TelemetryFrame(1200L, session.sessionId, "/Drive/MotorFL/Current", 12.0),
            TelemetryFrame(1100L, session.sessionId, "/Vision/AcceptanceRate", 0.95),
            TelemetryFrame(1200L, session.sessionId, "/Vision/AcceptanceRate", 0.85)
        )

        databaseService.insertTelemetryFrames(frames)
        val summary = summaryEngine.generateSummary(session)

        assertEquals("summary-session", summary.sessionId)
        assertEquals(11.2, summary.minBatteryVoltage)
        assertEquals(0.18, summary.maxEkfDrift)
        assertEquals(20.67, summary.avgLoopTimeMs, 0.01)
        assertEquals(10.0, summary.motorCurrentAverages["MotorFL"])
        assertEquals(0.90, summary.visionAcceptanceRate, 0.01)
        assertEquals(2, summary.tags.size)
        assertTrue(summary.tags.contains("battery-A"))

        // Verify summary was saved in DB
        val saved = databaseService.getSessionSummary(session.sessionId)
        assertTrue(saved != null)
        assertEquals(11.2, saved.minBatteryVoltage)
        tempDb.delete()
    }
}
