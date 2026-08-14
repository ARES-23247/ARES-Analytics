@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ares.analytics.viewmodel

import com.ares.analytics.shared.League
import com.ares.analytics.viewmodel.project.RoutineProjectRepository
import com.areslib.routine.RoutineDocument
import com.areslib.routine.RoutineStep
import com.areslib.routine.RoutineStepKind
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineBuilderViewModelTest {
    @Test
    fun `new routine stays trigger neutral until autonomous is enabled`() = runTest {
        val viewModel = PathPlannerViewModel(this)

        viewModel.onIntent(PathPlannerIntent.CreateRoutine("Score preload"))
        advanceUntilIdle()
        assertEquals("Score preload", viewModel.state.value.routine.name)
        assertFalse(viewModel.state.value.availableInAutonomousSelector)
        assertEquals(null, viewModel.state.value.autonomousEntry)

        viewModel.onIntent(PathPlannerIntent.SetAutonomousAvailability(true, League.FTC))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.availableInAutonomousSelector)
        assertNotNull(viewModel.state.value.autonomousEntry)
        assertEquals(
            viewModel.state.value.routine.documentId,
            viewModel.state.value.autonomousEntry?.routineId
        )
    }

    @Test
    fun `all control flow nodes can be added without text code`() = runTest {
        val viewModel = PathPlannerViewModel(this)
        viewModel.onIntent(PathPlannerIntent.CreateRoutine("Flow demo"))
        advanceUntilIdle()

        RoutineStepKind.entries.forEach { kind ->
            viewModel.onIntent(PathPlannerIntent.AddRoutineStep(kind))
            advanceUntilIdle()
        }

        assertEquals(RoutineStepKind.entries, viewModel.state.value.routine.steps.map { it.kind })
    }

    @Test
    fun `multi timeline routines suppress fabricated route and duration previews`() = runTest {
        val cases = listOf(
            RoutineStepKind.BRANCH to "Branch",
            RoutineStepKind.TOGETHER to "Parallel group",
            RoutineStepKind.FIRST_TO_FINISH to "First-to-finish group",
            RoutineStepKind.DEADLINE to "Deadline group"
        )

        for ((kind, label) in cases) {
            val viewModel = PathPlannerViewModel(this)
            viewModel.onIntent(PathPlannerIntent.CreateRoutine("$label preview"))
            advanceUntilIdle()
            viewModel.onIntent(PathPlannerIntent.AddRoutineStep(kind))
            advanceUntilIdle()

            val state = viewModel.state.value
            val warning = assertNotNull(state.routinePreviewWarning)
            assertTrue(warning.contains(label))
            assertTrue(warning.contains("multiple possible timelines"))
            assertNull(state.trajectory)
            assertEquals(0.0, state.estimatedDuration)

            viewModel.onIntent(PathPlannerIntent.TogglePlayback)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.isPlaying)
            assertEquals(0.0, viewModel.state.value.playbackTime)
        }
    }

    @Test
    fun `preview rejects hostile repeat expansion before iterating it`() = runTest {
        val viewModel = PathPlannerViewModel(this)
        viewModel.onIntent(PathPlannerIntent.CreateRoutine("Bounded preview"))
        viewModel.onIntent(PathPlannerIntent.AddRoutineStep(RoutineStepKind.REPEAT))
        advanceUntilIdle()

        val repeat = viewModel.state.value.routine.steps.single().copy(repeatCount = 4_097)
        viewModel.onIntent(PathPlannerIntent.UpdateRoutineStep(repeat.stepId, repeat))
        advanceUntilIdle()

        val warning = assertNotNull(viewModel.state.value.routinePreviewWarning)
        assertTrue(warning.contains("repeat count exceeds 4096"))
        assertNull(viewModel.state.value.trajectory)
        assertEquals(0.0, viewModel.state.value.estimatedDuration)
    }

    @Test
    fun `project switch binds editor state and saves only to the loaded canonical path`() = runTest {
        val projectA = Files.createTempDirectory("ares-routine-a-").toFile()
        val projectB = Files.createTempDirectory("ares-routine-b-").toFile()
        try {
            val repository = RoutineProjectRepository()
            repository.save(
                projectA.path,
                RoutineDocument(
                    documentId = "routine-a",
                    name = "Routine A",
                    steps = listOf(RoutineStep.wait(0.1)),
                ),
            )
            repository.save(
                projectB.path,
                RoutineDocument(
                    documentId = "routine-b",
                    name = "Routine B",
                    steps = listOf(RoutineStep.wait(0.2)),
                ),
            )
            val viewModel = PathPlannerViewModel(this)
            viewModel.onIntent(PathPlannerIntent.RefreshProject(projectA.path, League.FTC))
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000) { viewModel.state.first { it.routine.documentId == "routine-a" } }
            }

            viewModel.onIntent(PathPlannerIntent.RefreshProject(projectB.path, League.FTC))
            // A save dispatched during the switch must not copy the prior project's editor into B.
            viewModel.onIntent(PathPlannerIntent.SaveRoutine(projectB.path))
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000) { viewModel.state.first { it.routine.documentId == "routine-b" } }
            }

            assertEquals("routine-b", repository.load(projectB.path, "routine-b").documentId)
            assertFalse(projectB.resolve(".ares/routines/routine-a.aresroutine").exists())
            assertEquals("routine-a", repository.load(projectA.path, "routine-a").documentId)
        } finally {
            projectA.deleteRecursively()
            projectB.deleteRecursively()
        }
    }

    @Test
    fun `marker progress validation rejects out-of-range progress values`() = runTest {
        val viewModel = PathPlannerViewModel(this)
        viewModel.onIntent(PathPlannerIntent.CreateRoutine("Marker test"))
        advanceUntilIdle()

        val driveStep = RoutineStep.driveTo(
            com.areslib.routine.RoutineDriveStep(
                target = com.areslib.routine.RoutinePose(0.5, 0.5, 0.0),
                markers = listOf(com.areslib.routine.RoutineDriveMarker(progress = 1.5, actionKey = "intake"))
            )
        )
        viewModel.onIntent(PathPlannerIntent.AddRoutineStep(RoutineStepKind.DRIVE_TO))
        advanceUntilIdle()
        val stepId = viewModel.state.value.routine.steps.first { it.kind == RoutineStepKind.DRIVE_TO }.stepId
        viewModel.onIntent(PathPlannerIntent.UpdateRoutineStep(stepId, driveStep.copy(stepId = stepId)))
        advanceUntilIdle()

        val issues = viewModel.state.value.routineValidation
        assertTrue(issues.any { it.code == "invalid_marker_progress" })
    }

    @Test
    fun `duration and timeout bounds are validated on routine steps`() = runTest {
        val viewModel = PathPlannerViewModel(this)
        viewModel.onIntent(PathPlannerIntent.CreateRoutine("Timeout test"))
        advanceUntilIdle()

        val waitStep = RoutineStep(
            kind = RoutineStepKind.WAIT,
            durationSeconds = 150.0
        )
        viewModel.onIntent(PathPlannerIntent.AddRoutineStep(RoutineStepKind.WAIT))
        advanceUntilIdle()
        val stepId = viewModel.state.value.routine.steps.first { it.kind == RoutineStepKind.WAIT }.stepId
        viewModel.onIntent(PathPlannerIntent.UpdateRoutineStep(stepId, waitStep.copy(stepId = stepId)))
        advanceUntilIdle()

        val issues = viewModel.state.value.routineValidation
        assertTrue(issues.any { it.code == "invalid_duration" })
    }

    @Test
    fun `deleting a step removes it from routine and clears its validation errors`() = runTest {
        val viewModel = PathPlannerViewModel(this)
        viewModel.onIntent(PathPlannerIntent.CreateRoutine("Deletion test"))
        advanceUntilIdle()

        viewModel.onIntent(PathPlannerIntent.AddRoutineStep(RoutineStepKind.WAIT))
        viewModel.onIntent(PathPlannerIntent.AddRoutineStep(RoutineStepKind.DRIVE_TO))
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.routine.steps.size)
        val driveStep = viewModel.state.value.routine.steps.first { it.kind == RoutineStepKind.DRIVE_TO }
        val invalidDriveStep = driveStep.copy(
            drive = com.areslib.routine.RoutineDriveStep(
                target = com.areslib.routine.RoutinePose(0.5, 0.5, 0.0),
                markers = listOf(
                    com.areslib.routine.RoutineDriveMarker(progress = -0.25, actionKey = "intake"),
                    com.areslib.routine.RoutineDriveMarker(progress = 1.25, actionKey = "outtake")
                )
            )
        )
        viewModel.onIntent(PathPlannerIntent.UpdateRoutineStep(driveStep.stepId, invalidDriveStep))
        advanceUntilIdle()

        val issuesBeforeDeletion = viewModel.state.value.routineValidation
        val markerIssues = issuesBeforeDeletion.filter { it.code == "invalid_marker_progress" }
        assertTrue(markerIssues.isNotEmpty())

        // Delete the drive step
        viewModel.onIntent(PathPlannerIntent.RemoveRoutineStep(driveStep.stepId))
        advanceUntilIdle()

        val stateAfter = viewModel.state.value
        assertEquals(1, stateAfter.routine.steps.size)
        assertEquals(RoutineStepKind.WAIT, stateAfter.routine.steps.single().kind)
        assertTrue(stateAfter.routineValidation.none { it.code == "invalid_marker_progress" })
    }

    @Test
    fun `moving steps at boundaries preserves order while valid moves reorder steps`() = runTest {
        val viewModel = PathPlannerViewModel(this)
        viewModel.onIntent(PathPlannerIntent.CreateRoutine("Move test"))
        viewModel.onIntent(PathPlannerIntent.AddRoutineStep(RoutineStepKind.WAIT))
        viewModel.onIntent(PathPlannerIntent.AddRoutineStep(RoutineStepKind.ACTION))
        viewModel.onIntent(PathPlannerIntent.AddRoutineStep(RoutineStepKind.DRIVE_TO))
        advanceUntilIdle()

        val steps = viewModel.state.value.routine.steps
        val firstId = steps[0].stepId
        val secondId = steps[1].stepId
        val thirdId = steps[2].stepId

        // Move first step up (-1) -> should be a no-op
        viewModel.onIntent(PathPlannerIntent.MoveRoutineStep(firstId, -1))
        advanceUntilIdle()
        assertEquals(listOf(firstId, secondId, thirdId), viewModel.state.value.routine.steps.map { it.stepId })

        // Move third step down (+1) -> should be a no-op
        viewModel.onIntent(PathPlannerIntent.MoveRoutineStep(thirdId, 1))
        advanceUntilIdle()
        assertEquals(listOf(firstId, secondId, thirdId), viewModel.state.value.routine.steps.map { it.stepId })

        // Move middle step up (-1) -> swaps with first
        viewModel.onIntent(PathPlannerIntent.MoveRoutineStep(secondId, -1))
        advanceUntilIdle()
        assertEquals(listOf(secondId, firstId, thirdId), viewModel.state.value.routine.steps.map { it.stepId })
    }

}
