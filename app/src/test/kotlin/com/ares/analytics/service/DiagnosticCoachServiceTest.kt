package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticCoachServiceTest {
    @Test
    fun reportsEvidenceAndHypothesesWithoutClaimingRootCause() = runTest {
        withService { database, service ->
            database.insertTelemetryFrames(listOf(TelemetryFrame(100, "run", "Robot/BatteryVoltage", 9.2)))
            val result = service.analyze("run")
            val finding = result.findings.single()
            assertEquals(DiagnosticSeverity.URGENT, finding.severity)
            assertTrue(finding.observation.contains("9.20 V"))
            assertTrue(finding.possibleCauses.size > 1)
            assertFalse(finding.observation.contains("caused by", ignoreCase = true))
            assertTrue(result.evidenceNotice.contains("not root-cause"))
        }
    }

    @Test
    fun currentScreenDoesNotCallTheObservationAStall() = runTest {
        withService { database, service ->
            val frames = (0..5).map { index ->
                TelemetryFrame(index * 100L, "run", "Hardware/Motors/arm/CurrentAmps", 45.0)
            }
            database.insertTelemetryFrames(frames)
            val finding = service.analyze("run").findings.single()
            assertFalse(finding.title.contains("stall", ignoreCase = true))
            assertTrue(finding.thresholdContext.contains("does not establish a stall"))
        }
    }

    @Test
    fun missingSignalsAndNoFindingsNeverClaimHealth() = runTest {
        withService { _, service ->
            val result = service.analyze("empty")
            assertTrue(result.findings.isEmpty())
            assertEquals(listOf("Battery voltage", "Per-motor current"), result.missingSignals)
            assertFalse(result.evidenceNotice.contains("healthy", ignoreCase = true))
        }
    }

    private suspend fun withService(block: suspend (DatabaseService, DiagnosticCoachService) -> Unit) {
        val file = File.createTempFile("diagnostic-coach", ".db").apply { deleteOnExit() }
        val database = DatabaseService(file.absolutePath)
        try {
            block(database, DiagnosticCoachService(database))
        } finally {
            database.close()
            file.delete()
        }
    }
}
