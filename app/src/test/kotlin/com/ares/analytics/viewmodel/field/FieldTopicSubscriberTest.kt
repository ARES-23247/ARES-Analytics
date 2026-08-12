package com.ares.analytics.viewmodel.field

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.viewmodel.FieldViewerState
import com.ares.analytics.viewmodel.FieldViewerIntent
import com.ares.analytics.viewmodel.FieldViewerViewModel
import com.ares.analytics.viewmodel.LivePoseState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FieldTopicSubscriberTest {
    @Test
    fun `alliance toggle updates the atomic frame selection`() = runTest {
        val databaseFile = File.createTempFile("field-alliance-toggle", ".duckdb")
        val database = DatabaseService(databaseFile.absolutePath)
        val nt4 = Nt4ClientService(database)
        try {
            val viewModel = FieldViewerViewModel(nt4, backgroundScope)
            viewModel.onIntent(FieldViewerIntent.ToggleAlliance)
            runCurrent()

            assertFalse(nt4.selectedRedAlliance.value)
            assertFalse(viewModel.state.value.isRedAlliance)
        } finally {
            nt4.stop()
            database.close()
            databaseFile.delete()
        }
    }

    @Test
    fun `recreated field view inherits the dashboard alliance selection`() = runTest {
        val databaseFile = File.createTempFile("field-alliance-lifecycle", ".duckdb")
        val database = DatabaseService(databaseFile.absolutePath)
        val nt4 = Nt4ClientService(database)
        try {
            nt4.selectRedAlliance(false)

            val firstView = FieldViewerViewModel(nt4, backgroundScope)
            val recreatedView = FieldViewerViewModel(nt4, backgroundScope)

            assertFalse(firstView.state.value.isRedAlliance)
            assertFalse(recreatedView.state.value.isRedAlliance)
        } finally {
            nt4.stop()
            database.close()
            databaseFile.delete()
        }
    }

    @Test
    fun `game piece count removes stale array entries`() = runTest {
        val databaseFile = File.createTempFile("field-topic-subscriber", ".duckdb")
        val database = DatabaseService(databaseFile.absolutePath)
        val nt4 = Nt4ClientService(database)
        try {
            val state = MutableStateFlow(FieldViewerState())
            val livePose = MutableStateFlow(LivePoseState())
            FieldTopicSubscriber(nt4, backgroundScope, state, livePose)
            runCurrent()

            nt4.handleIncomingText(
                """[
                    {"method":"announce","params":{"name":"/ARES/GamePieces","id":20,"type":"double[]"}},
                    {"method":"announce","params":{"name":"/ARES/GamePieces/Count","id":21,"type":"double"}}
                ]""".trimIndent(),
                "team", "season", "robot"
            )
            nt4.handleIncomingText(
                """[{"topic":20,"time":1000,"value":[1.0,2.0,0,0,0,0,0,3.0,4.0,0,0,0,0,0]}]""",
                "team", "season", "robot"
            )
            runCurrent()
            assertEquals(2, livePose.value.liveGamePieces.size)

            nt4.handleIncomingText(
                """[{"topic":21,"time":2000,"value":1.0}]""",
                "team", "season", "robot"
            )
            runCurrent()
            assertEquals(setOf(0), livePose.value.liveGamePieces.keys)

            nt4.handleIncomingText(
                """[{"topic":21,"time":3000,"value":0.0}]""",
                "team", "season", "robot"
            )
            runCurrent()
            assertTrue(livePose.value.liveGamePieces.isEmpty())
        } finally {
            nt4.stop()
            database.close()
            databaseFile.delete()
        }
    }
}
