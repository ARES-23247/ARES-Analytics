package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.shared.TransientClassification
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SysIdServiceTest class.
 */
class SysIdServiceTest {

    @Test
    /**
     * testPerformFftAnalysis fun.
     */
    fun testPerformFftAnalysis() {
        val tempDb = File.createTempFile("sysid_fft_test", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)

        // Generate a 10 Hz sine wave
        val sampleRate = 100.0
        val freq = 10.0
        val size = 128
        val values = DoubleArray(size) { i ->
            val t = i / sampleRate
            kotlin.math.sin(2 * kotlin.math.PI * freq * t)
        }
        val result = sysIdService.performFftAnalysis(values, sampleRate)
        assertEquals(10.0, result.dominantFrequency, 0.5)
        assertTrue(result.magnitudes.isNotEmpty())
        tempDb.delete()
    }

    @Test
    /**
     * testAnalyzeMotorData fun.
     */
    fun testAnalyzeMotorData() = runTest {
        val tempDb = File.createTempFile("sysid_motor_test", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val sessionId = "test-session"
        val voltageKey = "/motor/voltage"
        val velocityKey = "/motor/velocity"
        val accelerationKey = "/motor/accel"

        // Insert mock telemetry
        val kS = 1.0
        val kV = 2.0
        val kA = 0.5
        val frames = mutableListOf<TelemetryFrame>()
        // Generate both positive and negative velocity data with different time constants
        // to break collinearity of sign(v), v, and a
        for (i in 0 until 50) {
            val t = (i * 20).toLong() // 20ms step
            val direction = if (i < 25) 1.0 else -1.0
            val tLocal = if (i < 25) i else i - 25
            val velocity = direction * 3.0 * (1.0 - kotlin.math.exp(-tLocal / 10.0))
            val accel = direction * 0.3 * kotlin.math.exp(-tLocal / 5.0) // time constant = 5.0 (diff from 10.0)
            val sgn = kotlin.math.sign(velocity)
            val voltage = kS * sgn + kV * velocity + kA * accel

            frames.add(TelemetryFrame(t, sessionId, voltageKey, voltage))
            frames.add(TelemetryFrame(t, sessionId, velocityKey, velocity))
            frames.add(TelemetryFrame(t, sessionId, accelerationKey, accel))
        }

        databaseService.insertTelemetryFrames(frames)
        val result = sysIdService.analyzeMotorData(sessionId, voltageKey, velocityKey, accelerationKey)

        assertEquals(kS, result.kS, 0.25)
        assertEquals(kV, result.kV, 0.25)
        assertEquals(kA, result.kA, 0.25)
        assertTrue(result.rSquared > 0.8)
        tempDb.delete()
    }
}
