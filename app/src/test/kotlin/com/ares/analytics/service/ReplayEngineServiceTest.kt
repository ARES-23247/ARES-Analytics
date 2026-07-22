package com.ares.analytics.service

import com.ares.analytics.shared.Session
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ReplayEngineServiceTest class.
 */
class ReplayEngineServiceTest {

    @Test
    /**
     * testReplayLifecycle fun.
     */
    fun testReplayLifecycle() = runTest {
        val tempDb = File.createTempFile("replay_db_test", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val replayEngine = ReplayEngineService(databaseService)
        val session = Session(
            sessionId = "replay-session",
            teamId = "23247",
            seasonId = "2026",
            robotId = "ares-bot",
            createdAt = 1000L
        )
        databaseService.insertSession(session)
        val frames = listOf(
            TelemetryFrame(1000L, session.sessionId, "/Test/Val", 1.0),
            TelemetryFrame(1100L, session.sessionId, "/Test/Val", 2.0),
            TelemetryFrame(1200L, session.sessionId, "/Test/Val", 3.0)
        )
        databaseService.insertTelemetryFrames(frames)

        // Load session
        replayEngine.loadSession(session.sessionId)
        assertEquals(ReplayState.STOPPED, replayEngine.state.value)
        assertEquals(0.0, replayEngine.progress.value)
        assertEquals(1.0, replayEngine.currentFrame.value?.values?.get("/Test/Val"))
        assertTrue(replayEngine.telemetryDensity.value.isNotEmpty())

        // Play
        replayEngine.play()
        assertEquals(ReplayState.PLAYING, replayEngine.state.value)

        // Delay to allow playback progress
        delay(200)

        // Pause
        replayEngine.pause()
        assertEquals(ReplayState.PAUSED, replayEngine.state.value)

        // Scrub
        replayEngine.scrubTo(0.5)
        assertEquals(1100L, replayEngine.currentFrame.value?.timestampMs ?: 0L)
        assertEquals(0.5, replayEngine.progress.value, 0.05)

        // Step forward
        replayEngine.stepForward()
        assertEquals(1200L, replayEngine.currentFrame.value?.timestampMs ?: 0L)

        // Step backward
        replayEngine.stepBackward()
        assertEquals(1100L, replayEngine.currentFrame.value?.timestampMs ?: 0L)

        // Stop
        replayEngine.stop()
        assertEquals(ReplayState.STOPPED, replayEngine.state.value)
        assertEquals(1000L, replayEngine.currentFrame.value?.timestampMs ?: 0L)

        tempDb.delete()
    }
}
