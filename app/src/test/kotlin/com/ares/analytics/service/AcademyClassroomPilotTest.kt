package com.ares.analytics.service

import com.ares.analytics.ui.help.AcademyRunAnalysisSnapshot
import com.ares.analytics.ui.help.AcademyRuntimeSnapshot
import com.ares.analytics.ui.help.AcademyClassroomToolkit
import com.ares.analytics.ui.help.FirstMissionCheckpointIds
import com.ares.analytics.ui.help.LearningCatalog
import com.ares.analytics.ui.help.LearningCheckpointEvidence
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcademyClassroomPilotTest {
    @Test
    fun `offline first mission through run review produces bounded export`() = runTest {
        val root = Files.createTempDirectory("academy-classroom-pilot").toFile()
        File(root, ".ares").mkdirs()
        val practicePack = AcademyPracticePackService().install(root)
        val progressFile = File(root, "learning-progress.json")
        val progress = LearningProgressService(progressFile)
        progress.updateStudentDisplayName("Pilot Student")
        progress.selectPath("first-mission")

        progress.startLesson("start-simulator")
        progress.observeRuntime(
            AcademyRuntimeSnapshot(
                isAvailable = true,
                isLocalSimulatorSelected = true,
                isSimulatorRunning = true,
                isLocalSimulatorOnline = true,
                isNt4Connected = true,
            ),
        )
        recordEveryReflection(progress, "start-simulator")
        progress.observeRuntime(AcademyRuntimeSnapshot(isAvailable = true, isLocalSimulatorSelected = true))
        progress.setPracticed("start-simulator", true)

        progress.observeRuntime(
            AcademyRuntimeSnapshot(
                isAvailable = true,
                runAnalysis = AcademyRunAnalysisSnapshot(
                    isAvailable = true,
                    hasWorkspaceRuns = true,
                    hasSelectedRun = true,
                    hasSourceEvidence = true,
                    hasGuidedReport = true,
                    hasQuantitativeEvidence = true,
                    hasBaselineComparison = true,
                    hasLimitations = true,
                    hasExportedReport = true,
                ),
            ),
        )
        recordEveryReflection(progress, "bring-in-run")
        progress.setPracticed("bring-in-run", true)
        recordEveryReflection(progress, "compare-run-evidence")
        progress.setPracticed("compare-run-evidence", true)

        val reportFile = File(root, "pilot-record.md")
        progress.exportMentorReport(reportFile, "first-mission", "Pilot Mentor")
        val summary = AcademyClassroomToolkit.pathSummary("first-mission", progress.progress.value)

        assertTrue(practicePack.files.any { it.name == "baseline-arm-run.csv" })
        assertTrue(FirstMissionCheckpointIds.LOCAL_SIM_CONNECTED in progress.progress.value.completedCheckpointIds)
        assertEquals("read-connection-state", summary.recommendedLesson?.id)
        assertTrue(reportFile.readText().contains("synthetic").not())
        assertTrue(reportFile.readText().contains("not a grade, certification, code review, or proof of physical robot safety"))
    }

    private suspend fun recordEveryReflection(service: LearningProgressService, lessonId: String) {
        LearningCatalog.lesson(lessonId)!!.checkpoints
            .filter { it.evidence == LearningCheckpointEvidence.SELF_REPORTED }
            .forEach { checkpoint ->
                service.recordReflection(
                    checkpoint.id,
                    "I named the evidence source and one limitation; this is not physical validation.",
                )
            }
    }
}
