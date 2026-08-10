package com.ares.analytics.service

import com.areslib.control.assist.SysIdMechanism
import com.areslib.tuning.TuningTopics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.exp
import kotlin.math.sign

class AutoTunerServiceTest {
    private lateinit var autoTunerService: AutoTunerService
    private lateinit var mockNt4Service: Nt4ClientService

    @Before
    fun setUp() {
        val tempDb = File.createTempFile("mock_db_tuner", ".sqlite").apply { deleteOnExit() }
        val database = DatabaseService(tempDb.absolutePath)
        mockNt4Service = Nt4ClientService(database)
        autoTunerService = AutoTunerService(mockNt4Service, SysIdService(database))
    }

    @Test
    fun `measured plant produces feedforward and feedback recommendation`() {
        val recommendation = autoTunerService.analyzeSamples(SysIdMechanism.LINEAR, syntheticBidirectionalRun())

        assertNotNull(recommendation)
        assertEquals(0.45, recommendation!!.recommendedkS, 0.15)
        assertEquals(1.8, recommendation.recommendedkV, 0.2)
        assertEquals(0.25, recommendation.recommendedkA, 0.2)
        assertTrue(recommendation.rSquared > 0.9)
        assertTrue(recommendation.recommendedGains.kP > 0.0)
        assertTrue(recommendation.topicValues.containsKey(TuningTopics.DRIVE_FEEDFORWARD_KV))
    }

    @Test
    fun `structured JSONL reads named values rather than timestamps`() {
        val file = File.createTempFile("sample_drive_log", ".jsonl")
        file.writeText(syntheticBidirectionalRun().joinToString("\n") {
            """{"timestampMs":${it.timestampMs},"voltage":${it.voltage},"velocity":${it.velocity},"accel":${it.accel}}"""
        })

        val recommendation = autoTunerService.analyzeLogFile(file)

        assertNotNull(recommendation)
        assertEquals(1.8, recommendation!!.recommendedkV, 0.2)
        file.delete()
    }

    @Test
    fun `approved gains use canonical topics and can roll back`() = runBlocking {
        for (topic in listOf(
            TuningTopics.DRIVE_FEEDFORWARD_KS,
            TuningTopics.DRIVE_FEEDFORWARD_KV,
            TuningTopics.DRIVE_FEEDFORWARD_KA,
            TuningTopics.DRIVE_TRANSLATION_KP,
            TuningTopics.DRIVE_TRANSLATION_KI,
            TuningTopics.DRIVE_TRANSLATION_KD
        )) mockNt4Service.publishDouble(topic, 0.1)
        val recommendation = autoTunerService.analyzeSamples(SysIdMechanism.LINEAR, syntheticBidirectionalRun())!!

        autoTunerService.approveAndApplyGains(recommendation)
        assertEquals(TuningApplyPhase.APPLIED_AWAITING_VALIDATION, autoTunerService.applyState.value.phase)
        assertEquals(recommendation.recommendedkV, mockNt4Service.latestValues[TuningTopics.DRIVE_FEEDFORWARD_KV]!!.value, 1e-9)

        autoTunerService.rollback()
        assertEquals(TuningApplyPhase.ROLLED_BACK, autoTunerService.applyState.value.phase)
        assertEquals(0.1, mockNt4Service.latestValues[TuningTopics.DRIVE_FEEDFORWARD_KV]!!.value, 1e-9)
    }

    private fun syntheticBidirectionalRun(): List<AlignedDataRow> {
        val rows = ArrayList<AlignedDataRow>(120)
        var previousVelocity = 0.0
        for (i in 0 until 120) {
            val local = if (i < 60) i else i - 60
            val direction = if (i < 60) 1.0 else -1.0
            val velocity = direction * 3.0 * (1.0 - exp(-local / 9.0))
            val accel = if (i == 0 || i == 60) 0.0 else (velocity - previousVelocity) / 0.02
            val voltage = 0.45 * sign(velocity) + 1.8 * velocity + 0.25 * accel
            rows += AlignedDataRow(i * 20L, voltage, velocity, accel)
            previousVelocity = velocity
        }
        return rows
    }
}
