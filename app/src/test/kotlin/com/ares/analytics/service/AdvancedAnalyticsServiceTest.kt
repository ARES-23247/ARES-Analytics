package com.ares.analytics.service

import com.ares.analytics.shared.SessionSummary
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdvancedAnalyticsServiceTest {
    @Test
    fun `report compares baselines and builds evidence backed insights`() = runTest {
        val directory = Files.createTempDirectory("ares-advanced-analytics").toFile()
        val database = DatabaseService(directory.resolve("telemetry.duckdb").absolutePath)
        try {
            database.insertSessionSummary(summary("baseline", p95 = 10.0, voltage = 12.0, crossTrack = 0.10))
            database.insertSessionSummary(summary("current", p95 = 15.0, voltage = 10.0, crossTrack = 0.40))
            val frames = buildList {
                repeat(40) { index ->
                    val time = index * 50L
                    add(frame(time, "Hardware/Motors/fl/CurrentAmps", index.toDouble()))
                    add(frame(time, "Hardware/Motors/fl/Velocity", index * 2.0))
                    add(frame(time, "Robot/BatteryVoltage", 13.0 - index * 0.05))
                    add(frame(time, "ARES/Input/driveFrame/4", if (index % 2 == 0) 0.2 else 0.8))
                    add(frame(time, "ARES/Input/driveFrame/5", 0.3))
                    add(frame(time, "Drive/Pose_X", index * 0.05))
                    add(frame(time, "Drive/Pose_Y", index * 0.02))
                }
            }
            database.insertTelemetryFrames(frames)

            val service = AdvancedAnalyticsService(database)
            val report = service.analyze("current", listOf("baseline"))

            assertNotNull(report.comparison)
            assertTrue(report.regressions.any { it.metric == "p95 loop time" })
            assertTrue(report.correlations.any { it.rightTopic == "Robot/BatteryVoltage" })
            assertNotNull(report.driverScore)
            assertTrue(report.pathHeatmap.isNotEmpty())
            assertTrue(report.diagnostics.isNotEmpty())
            assertTrue(report.tuningSuggestions.all { it.confidence in 0.0..1.0 })
            assertTrue(service.renderDiagnosticMarkdown(report).contains("ARES analytics report"))
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `physical drive frame inputs score like equivalent normalized gamepad inputs`() = runTest {
        val directory = Files.createTempDirectory("ares-driver-input-normalization").toFile()
        val database = DatabaseService(directory.resolve("telemetry.duckdb").absolutePath)
        try {
            database.insertSessionSummary(summary("gamepad", p95 = 10.0, voltage = 12.0, crossTrack = 0.10))
            database.insertSessionSummary(summary("drive-frame", p95 = 10.0, voltage = 12.0, crossTrack = 0.10))
            val frames = buildList {
                repeat(40) { index ->
                    val time = index * 50L
                    val normalizedX = if (index % 2 == 0) 0.2 else 0.8
                    val normalizedY = 0.3
                    add(frame(time, "gamepad", "Gamepad1/LeftStickX", normalizedX))
                    add(frame(time, "gamepad", "Gamepad1/LeftStickY", normalizedY))
                    add(frame(time, "drive-frame", "ARES/Input/driveFrame/4", normalizedX * 4.0))
                    add(frame(time, "drive-frame", "ARES/Input/driveFrame/5", normalizedY * 4.0))
                }
            }
            database.insertTelemetryFrames(frames)

            val service = AdvancedAnalyticsService(database)
            val gamepad = assertNotNull(service.analyze("gamepad").driverScore)
            val driveFrame = assertNotNull(service.analyze("drive-frame").driverScore)

            assertEquals(gamepad.total, driveFrame.total, 1e-9)
            assertEquals(gamepad.smoothness, driveFrame.smoothness, 1e-9)
            assertEquals(gamepad.decisiveness, driveFrame.decisiveness, 1e-9)
            assertEquals(gamepad.consistency, driveFrame.consistency, 1e-9)
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    private fun summary(id: String, p95: Double, voltage: Double, crossTrack: Double) = SessionSummary(
        sessionId = id,
        teamId = "23247",
        seasonId = "2026",
        robotId = "bot",
        createdAt = 0,
        minBatteryVoltage = voltage,
        p95LoopTimeMs = p95,
        avgCrossTrackError = crossTrack,
        visionAcceptanceRate = 0.9
    )

    private fun frame(timestamp: Long, key: String, value: Double) = TelemetryFrame(
        timestampMs = timestamp,
        sessionId = "current",
        key = key,
        value = value
    )

    private fun frame(timestamp: Long, sessionId: String, key: String, value: Double) = TelemetryFrame(
        timestampMs = timestamp,
        sessionId = sessionId,
        key = key,
        value = value
    )
}
