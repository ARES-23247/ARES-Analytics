package com.ares.analytics.viewmodel.field

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.viewmodel.FieldViewerIntent
import com.ares.analytics.viewmodel.FieldViewerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
