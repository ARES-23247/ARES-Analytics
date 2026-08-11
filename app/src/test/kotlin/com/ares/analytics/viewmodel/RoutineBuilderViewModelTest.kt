@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ares.analytics.viewmodel

import com.ares.analytics.shared.League
import com.areslib.routine.RoutineStepKind
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
}
