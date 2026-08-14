package com.ares.analytics.viewmodel.robotstudio

import com.ares.analytics.service.BuildExecutionPhase
import com.ares.analytics.service.BuildExecutionState
import com.ares.analytics.service.RobotProjectReadinessEvidence
import com.ares.analytics.service.drivebase.DrivebaseKind
import com.ares.analytics.shared.League
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RobotStudioModelTest {
    @Test
    fun `complete canonical evidence enables build and simulation actions without claiming success`() {
        val stages = evaluateRobotStudioStages(completeEvidence(), RobotStudioRuntimeEvidence())

        assertEquals(RobotStudioStageStatus.READY, stages.status(RobotStudioStageId.DRIVEBASE))
        assertEquals(RobotStudioStageStatus.READY, stages.status(RobotStudioStageId.CONTROLS))
        assertEquals(RobotStudioStageStatus.NEEDS_ACTION, stages.status(RobotStudioStageId.GENERATE_VERIFY))
        assertEquals(RobotStudioStageStatus.NEEDS_ACTION, stages.status(RobotStudioStageId.SIMULATE))
        assertTrue(stages.first { it.id == RobotStudioStageId.GENERATE_VERIFY }.explanation.contains("not proof"))
    }

    @Test
    fun `missing required authoring blocks build and simulation`() {
        val noDrivebase = evaluateRobotStudioStages(
            completeEvidence().copy(
                drivebaseKind = null,
                drivebaseNoCodeSupported = false,
                localizationConfigured = false,
            ),
            RobotStudioRuntimeEvidence(),
        )
        assertEquals(RobotStudioStageStatus.NEEDS_ACTION, noDrivebase.status(RobotStudioStageId.DRIVEBASE))
        assertEquals(RobotStudioStageStatus.BLOCKED, noDrivebase.status(RobotStudioStageId.GENERATE_VERIFY))
        assertEquals(RobotStudioStageStatus.BLOCKED, noDrivebase.status(RobotStudioStageId.SIMULATE))

        val noControls = evaluateRobotStudioStages(
            completeEvidence().copy(controlSchemeCount = 0),
            RobotStudioRuntimeEvidence(),
        )
        assertEquals(RobotStudioStageStatus.NEEDS_ACTION, noControls.status(RobotStudioStageId.CONTROLS))
        assertEquals(RobotStudioStageStatus.BLOCKED, noControls.status(RobotStudioStageId.GENERATE_VERIFY))
    }

    @Test
    fun `missing or invalid metadata routes students to reviewed project identity setup`() {
        val missing = evaluateRobotStudioStages(
            completeEvidence().copy(metadataPresent = false, metadataLeagueMatches = false),
            RobotStudioRuntimeEvidence(),
        )
        val invalidPlatform = evaluateRobotStudioStages(
            completeEvidence().copy(metadataLeagueMatches = false),
            RobotStudioRuntimeEvidence(),
        )

        assertEquals(
            RobotStudioAction.OPEN_PROJECT_IDENTITY,
            missing.first { it.id == RobotStudioStageId.WORKSPACE }.action,
        )
        assertEquals(
            "Set up project identity",
            missing.first { it.id == RobotStudioStageId.WORKSPACE }.actionLabel,
        )
        assertEquals(
            RobotStudioAction.OPEN_PROJECT_IDENTITY,
            invalidPlatform.first { it.id == RobotStudioStageId.PLATFORM }.action,
        )
    }

    @Test
    fun `unsupported drive runtime is visibly code required and blocks no-code workflow`() {
        val stages = evaluateRobotStudioStages(
            completeEvidence().copy(
                drivebaseKind = DrivebaseKind.DIFFERENTIAL,
                drivebaseNoCodeSupported = false,
            ),
            RobotStudioRuntimeEvidence(),
        )
        val studio = RobotStudioState(stages = stages)

        assertEquals(RobotStudioStageStatus.CODE_REQUIRED, stages.status(RobotStudioStageId.DRIVEBASE))
        assertEquals(RobotStudioStageStatus.BLOCKED, stages.status(RobotStudioStageId.GENERATE_VERIFY))
        assertTrue(studio.blockingCount >= 2)
        assertTrue(stages.first { it.id == RobotStudioStageId.DRIVEBASE }.explanation.contains("no no-code runtime adapter"))
    }

    @Test
    fun `typed metadata and capability diagnostics are surfaced instead of text guessed`() {
        val stages = evaluateRobotStudioStages(
            completeEvidence().copy(
                metadataErrors = listOf("project.json: invalid league"),
                capabilityErrors = listOf("action-catalog.json: duplicate action"),
            ),
            RobotStudioRuntimeEvidence(),
        )

        assertEquals(RobotStudioStageStatus.INVALID, stages.status(RobotStudioStageId.WORKSPACE))
        assertEquals(RobotStudioStageStatus.INVALID, stages.status(RobotStudioStageId.CAPABILITIES))
        assertTrue(stages.first { it.id == RobotStudioStageId.WORKSPACE }.issues.any { "invalid league" in it })
        assertTrue(stages.first { it.id == RobotStudioStageId.CAPABILITIES }.issues.any { "duplicate action" in it })
    }

    @Test
    fun `runtime and imported run evidence report only what was observed`() {
        val stages = evaluateRobotStudioStages(
            completeEvidence().copy(importedRunCount = 2),
            RobotStudioRuntimeEvidence(
                build = buildState(BuildExecutionPhase.RUNNING),
                simulatorRunning = true,
                localSimulatorOnline = true,
                nt4Connected = true,
            ),
        )

        assertEquals(RobotStudioStageStatus.RUNNING, stages.status(RobotStudioStageId.GENERATE_VERIFY))
        assertEquals(RobotStudioStageStatus.RUNNING, stages.status(RobotStudioStageId.SIMULATE))
        assertEquals(RobotStudioStageStatus.READY, stages.status(RobotStudioStageId.ANALYZE))
        assertEquals(RobotStudioAction.OPEN_GUIDED_ANALYSIS, stages.first { it.id == RobotStudioStageId.ANALYZE }.action)
    }

    @Test
    fun `successful verification is project correlated and explicitly compile only`() {
        val matching = evaluateRobotStudioStages(
            completeEvidence(),
            RobotStudioRuntimeEvidence(build = buildState(BuildExecutionPhase.SUCCEEDED)),
        )
        val otherProject = evaluateRobotStudioStages(
            completeEvidence(),
            RobotStudioRuntimeEvidence(
                build = buildState(BuildExecutionPhase.SUCCEEDED).copy(projectPath = "C:/fixture/another-robot"),
            ),
        )

        val matchingStage = matching.first { it.id == RobotStudioStageId.GENERATE_VERIFY }
        assertEquals(RobotStudioStageStatus.READY, matchingStage.status)
        assertTrue(matchingStage.explanation.contains("Nothing was deployed"))
        assertTrue(matchingStage.outcome.contains("without deploying"))
        assertEquals(RobotStudioStageStatus.NEEDS_ACTION, otherProject.status(RobotStudioStageId.GENERATE_VERIFY))
    }

    @Test
    fun `failed and canceled verification remain actionable without claiming readiness`() {
        val failed = evaluateRobotStudioStages(
            completeEvidence(),
            RobotStudioRuntimeEvidence(build = buildState(BuildExecutionPhase.FAILED, exitCode = 7)),
        ).first { it.id == RobotStudioStageId.GENERATE_VERIFY }
        val canceled = evaluateRobotStudioStages(
            completeEvidence(),
            RobotStudioRuntimeEvidence(build = buildState(BuildExecutionPhase.CANCELED)),
        ).first { it.id == RobotStudioStageId.GENERATE_VERIFY }

        assertEquals(RobotStudioStageStatus.INVALID, failed.status)
        assertTrue(failed.issues.single().contains("exit code 7"))
        assertEquals("Retry verification", failed.actionLabel)
        assertEquals(RobotStudioStageStatus.NEEDS_ACTION, canceled.status)
        assertEquals("Retry verification", canceled.actionLabel)
    }

    @Test
    fun `build execution state transitions evaluate appropriate stage statuses and action labels`() {
        val evidence = completeEvidence()

        // 1. Initial idle verification state
        val idle = evaluateRobotStudioStages(
            evidence,
            RobotStudioRuntimeEvidence(build = buildState(BuildExecutionPhase.IDLE)),
        ).first { it.id == RobotStudioStageId.GENERATE_VERIFY }
        assertEquals(RobotStudioStageStatus.NEEDS_ACTION, idle.status)
        assertEquals("Verify & build", idle.actionLabel)
        assertEquals(RobotStudioAction.RUN_BUILD, idle.action)
        assertTrue(idle.issues.isEmpty())

        // 2. Active build running state
        val running = evaluateRobotStudioStages(
            evidence,
            RobotStudioRuntimeEvidence(build = buildState(BuildExecutionPhase.RUNNING)),
        ).first { it.id == RobotStudioStageId.GENERATE_VERIFY }
        assertEquals(RobotStudioStageStatus.RUNNING, running.status)
        assertEquals("Verification running", running.actionLabel)
        assertEquals(RobotStudioAction.RUN_BUILD, running.action)
        assertTrue(running.issues.isEmpty())

        // 3. Timed out build execution state (failure with timeout diagnostics)
        val timedOutState = BuildExecutionState(
            phase = BuildExecutionPhase.FAILED,
            projectPath = "C:/fixture/robot",
            league = League.FTC,
            message = "Project verification timed out after 120 seconds. No deployment was performed.",
            exitCode = 124,
            requestId = 2L,
        )
        val timedOut = evaluateRobotStudioStages(
            evidence,
            RobotStudioRuntimeEvidence(build = timedOutState),
        ).first { it.id == RobotStudioStageId.GENERATE_VERIFY }
        assertEquals(RobotStudioStageStatus.INVALID, timedOut.status)
        assertEquals("Retry verification", timedOut.actionLabel)
        assertEquals(RobotStudioAction.RUN_BUILD, timedOut.action)
        assertEquals(listOf("Project verification timed out after 120 seconds. No deployment was performed."), timedOut.issues)
        assertTrue(timedOut.explanation.contains("timed out"))

        // 4. Retry / rebuild execution running state
        val retrying = evaluateRobotStudioStages(
            evidence,
            RobotStudioRuntimeEvidence(build = buildState(BuildExecutionPhase.RUNNING).copy(requestId = 3L)),
        ).first { it.id == RobotStudioStageId.GENERATE_VERIFY }
        assertEquals(RobotStudioStageStatus.RUNNING, retrying.status)
        assertEquals("Verification running", retrying.actionLabel)
        assertTrue(retrying.issues.isEmpty())

        // 5. Canceled build execution state
        val canceledState = BuildExecutionState(
            phase = BuildExecutionPhase.CANCELED,
            projectPath = "C:/fixture/robot",
            league = League.FTC,
            message = "Project verification was canceled. No deployment was performed.",
            exitCode = null,
            requestId = 4L,
        )
        val canceled = evaluateRobotStudioStages(
            evidence,
            RobotStudioRuntimeEvidence(build = canceledState),
        ).first { it.id == RobotStudioStageId.GENERATE_VERIFY }
        assertEquals(RobotStudioStageStatus.NEEDS_ACTION, canceled.status)
        assertEquals("Retry verification", canceled.actionLabel)
        assertEquals(RobotStudioAction.RUN_BUILD, canceled.action)
        assertTrue(canceled.issues.isEmpty())
        assertTrue(canceled.explanation.contains("canceled"))

        // 6. Succeeded build execution state
        val succeededState = BuildExecutionState(
            phase = BuildExecutionPhase.SUCCEEDED,
            projectPath = "C:/fixture/robot",
            league = League.FTC,
            message = "Verification passed. Nothing was deployed; rebuild after edits.",
            exitCode = 0,
            requestId = 5L,
        )
        val succeeded = evaluateRobotStudioStages(
            evidence,
            RobotStudioRuntimeEvidence(build = succeededState),
        ).first { it.id == RobotStudioStageId.GENERATE_VERIFY }
        assertEquals(RobotStudioStageStatus.READY, succeeded.status)
        assertEquals("Verify again", succeeded.actionLabel)
        assertEquals(RobotStudioAction.RUN_BUILD, succeeded.action)
        assertTrue(succeeded.issues.isEmpty())
        assertTrue(succeeded.explanation.contains("Nothing was deployed"))
    }

    private fun completeEvidence() = RobotProjectReadinessEvidence(
        projectPath = "C:/fixture/robot",
        league = League.FTC,
        metadataPresent = true,
        metadataLeagueMatches = true,
        drivebaseKind = DrivebaseKind.FTC_MECANUM,
        drivebaseNoCodeSupported = true,
        localizationConfigured = true,
        subsystemCount = 0,
        capabilityActionCount = 0,
        controlSchemeCount = 1,
        controllerProfileCount = 1,
        routineCount = 0,
        autonomousCatalogPresent = false,
        tuningDeclarationCount = 0,
        tuningProfileCount = 0,
        generatedProjectSourcePresent = true,
    )

    private fun List<RobotStudioStage>.status(id: RobotStudioStageId): RobotStudioStageStatus =
        first { it.id == id }.status

    private fun buildState(phase: BuildExecutionPhase, exitCode: Int? = null) = BuildExecutionState(
        phase = phase,
        projectPath = "C:/fixture/robot",
        league = League.FTC,
        message = when (phase) {
            BuildExecutionPhase.SUCCEEDED -> "Verification passed. Nothing was deployed; rebuild after edits."
            BuildExecutionPhase.FAILED -> "Project verification failed with exit code ${exitCode ?: 1}. Review the terminal."
            BuildExecutionPhase.CANCELED -> "Project verification was canceled. No deployment was performed."
            BuildExecutionPhase.RUNNING -> "Project verification is running. No deployment is performed."
            BuildExecutionPhase.IDLE -> "No verification has run."
        },
        exitCode = exitCode,
        requestId = 1L,
    )
}
