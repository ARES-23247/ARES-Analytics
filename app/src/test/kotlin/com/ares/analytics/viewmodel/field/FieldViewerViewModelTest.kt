package com.ares.analytics.viewmodel.field

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.viewmodel.FieldViewerIntent
import com.ares.analytics.viewmodel.FieldViewerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FieldViewerViewModelTest {
    @Test
    fun `select waypoint intent updates selected waypoint index`() = runTest {
        val databaseFile = File.createTempFile("field-viewer-test", ".duckdb")
        val database = DatabaseService(databaseFile.absolutePath)
        val nt4 = Nt4ClientService(database)
        try {
            val viewModel = FieldViewerViewModel(nt4, backgroundScope)
            assertNull(viewModel.state.value.selectedWaypointIndex)
            assertNull(viewModel.selectedWaypointIndex)

            viewModel.onIntent(FieldViewerIntent.SelectWaypoint(3))
            runCurrent()

            assertEquals(3, viewModel.state.value.selectedWaypointIndex)
            assertEquals(3, viewModel.selectedWaypointIndex)

            viewModel.onIntent(FieldViewerIntent.SelectWaypoint(null))
            runCurrent()

            assertNull(viewModel.state.value.selectedWaypointIndex)
            assertNull(viewModel.selectedWaypointIndex)
        } finally {
            nt4.stop()
            database.close()
            databaseFile.delete()
        }
    }

    @Test
    fun `toggle alliance intent toggles isRedAlliance and updates nt4 selectedRedAlliance`() = runTest {
        val databaseFile = File.createTempFile("field-viewer-toggle-alliance-test", ".duckdb")
        val database = DatabaseService(databaseFile.absolutePath)
        val nt4 = Nt4ClientService(database)
        try {
            val viewModel = FieldViewerViewModel(nt4, backgroundScope)
            assertTrue(viewModel.state.value.isRedAlliance)
            assertTrue(nt4.selectedRedAlliance.value)

            viewModel.onIntent(FieldViewerIntent.ToggleAlliance)
            runCurrent()

            assertFalse(viewModel.state.value.isRedAlliance)
            assertFalse(nt4.selectedRedAlliance.value)

            viewModel.onIntent(FieldViewerIntent.ToggleAlliance)
            runCurrent()

            assertTrue(viewModel.state.value.isRedAlliance)
            assertTrue(nt4.selectedRedAlliance.value)
        } finally {
            nt4.stop()
            database.close()
            databaseFile.delete()
        }
    }

    @Test
    fun `clear trace intent clears trace buffer`() = runTest {
        val databaseFile = File.createTempFile("field-viewer-clear-trace-test", ".duckdb")
        val database = DatabaseService(databaseFile.absolutePath)
        val nt4 = Nt4ClientService(database)
        try {
            val viewModel = FieldViewerViewModel(nt4, backgroundScope)
            advanceTimeBy(60)
            runCurrent()
            assertEquals(1, viewModel.state.value.poseHistory.size)

            nt4.publishFrame(TelemetryFrame(timestampMs = 1000, sessionId = "live-telemetry", key = "ARES/TruePose/0", value = 2.0))
            nt4.publishFrame(TelemetryFrame(timestampMs = 1000, sessionId = "live-telemetry", key = "ARES/TruePose/1", value = 3.0))
            runCurrent()
            advanceTimeBy(60)
            runCurrent()
            assertEquals(2, viewModel.state.value.poseHistory.size)

            viewModel.onIntent(FieldViewerIntent.ClearTrace)
            runCurrent()

            assertTrue(viewModel.state.value.poseHistory.isEmpty())
        } finally {
            nt4.stop()
            database.close()
            databaseFile.delete()
        }
    }
}
