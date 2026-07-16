package com.ares.analytics.service

import com.ares.analytics.shared.DriverProfile
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriverAnalysisServiceTest {

    @Test
    fun testProfilesCRUD() = runTest {
        val tempDb = File.createTempFile("driver_crud_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)

        val tempFile = File.createTempFile("driver_profiles", ".json")
        tempFile.delete() // Delete so DriverAnalysisService writes defaults

        val service = DriverAnalysisService(databaseService, sysIdService, tempFile.absolutePath)

        // Verify default profiles loaded
        val profiles = service.getProfiles()
        assertEquals(3, profiles.size)

        // Save new profile
        val profile = DriverProfile("Pro Driver", 1.4, 4.0)
        service.saveProfile(profile)

        val retrieved = service.getProfile("Pro Driver")
        assertEquals(1.4, retrieved?.deadbandExponent)

        // Delete profile
        service.deleteProfile("Pro Driver")
        kotlin.test.assertNull(service.getProfile("Pro Driver"))
        tempDb.delete()
    }

    @Test
    fun testAnalyzeDriverJitter() = runTest {
        val tempDb = File.createTempFile("driver_jitter_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)

        val tempFile = File.createTempFile("driver_profiles", ".json")
        tempFile.delete() // Delete so DriverAnalysisService writes defaults

        val service = DriverAnalysisService(databaseService, sysIdService, tempFile.absolutePath)

        val sessionId = "test-session"
        val gamepadX = "/Gamepad1/LeftX"

        // Generate telemetry with a 10 Hz oscillation (jitter) at 100 Hz sample rate
        val frames = mutableListOf<TelemetryFrame>()
        val sampleRate = 100.0
        val freq = 10.0 // 10 Hz
        for (i in 0 until 128) {
            val t = (i * (1000.0 / sampleRate)).toLong()
            val value = kotlin.math.sin(2.0 * kotlin.math.PI * freq * (i / sampleRate))
            frames.add(TelemetryFrame(t, sessionId, gamepadX, value))
        }

        databaseService.insertTelemetryFrames(frames)

        val result = service.analyzeDriverJitter(sessionId, gamepadX, "/Gamepad1/LeftY")
        assertTrue(result.hasJitter)
        assertEquals(10.0, result.peakFrequencyHz, 0.5)
        assertEquals(1.6, result.recommendedExponent)
        assertEquals(2.5, result.recommendedSlewRate)

        tempFile.delete()
        tempDb.delete()
    }

}
