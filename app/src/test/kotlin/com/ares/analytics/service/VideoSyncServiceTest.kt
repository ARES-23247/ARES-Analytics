package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class VideoSyncServiceTest {

    @Test
    fun `initial default state has correct property values`() = runTest {
        val tempDir = Files.createTempDirectory("ares-video-sync-defaults").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        val replay = ReplayEngineService(database)
        val videoSync = VideoSyncService(replay)
        try {
            assertEquals(120000L, videoSync.videoDurationMs.value)
            assertEquals(0L, videoSync.logOffsetMs.value)
            assertEquals(0L, videoSync.currentVideoTimeMs.value)
            assertNull(videoSync.videoFile.value)
        } finally {
            videoSync.dispose()
            replay.dispose()
            database.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `loadVideo duration estimation bounds across tiny and oversized files`() = runTest {
        val tempDir = Files.createTempDirectory("ares-video-sync-bounding").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        val replay = ReplayEngineService(database)
        val videoSync = VideoSyncService(replay)
        try {
            val tinyVideo = tempDir.resolve("tiny_0byte.mp4").apply {
                createNewFile()
            }
            assertEquals(0L, tinyVideo.length())
            videoSync.loadVideo(tinyVideo)
            assertEquals(tinyVideo, videoSync.videoFile.value)
            assertEquals(30000L, videoSync.videoDurationMs.value)
            assertEquals(0L, videoSync.currentVideoTimeMs.value)

            val oversizedVideo = tempDir.resolve("oversized_500mb.mp4").apply {
                RandomAccessFile(this, "rw").use { it.setLength(500L * 1024 * 1024) }
            }
            assertEquals(500L * 1024 * 1024, oversizedVideo.length())
            videoSync.loadVideo(oversizedVideo)
            assertEquals(oversizedVideo, videoSync.videoFile.value)
            assertEquals(300000L, videoSync.videoDurationMs.value)
            assertEquals(0L, videoSync.currentVideoTimeMs.value)
        } finally {
            videoSync.dispose()
            replay.dispose()
            database.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `seekVideo with unloaded or zero-duration session scrubs to progress 0 safely`() = runTest {
        val tempDir = Files.createTempDirectory("ares-video-sync-unloaded").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        val replay = ReplayEngineService(database)
        val videoSync = VideoSyncService(replay)
        try {
            // Replay engine session is not loaded (sessionDurationMs == 0L)
            assertEquals(0L, replay.sessionDurationMs.value)

            videoSync.seekVideo(5000L)
            assertEquals(5000L, videoSync.currentVideoTimeMs.value)
            assertEquals(0.0, replay.progress.value)
            assertFalse(replay.progress.value.isNaN())
            assertFalse(replay.progress.value.isInfinite())

            videoSync.seekVideo(0L)
            assertEquals(0L, videoSync.currentVideoTimeMs.value)
            assertEquals(0.0, replay.progress.value)
            assertFalse(replay.progress.value.isNaN())
        } finally {
            videoSync.dispose()
            replay.dispose()
            database.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `aligned video seek maps to replay percentage and clamps both ends`() = runTest {
        val tempDir = Files.createTempDirectory("ares-video-sync").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        val replay = ReplayEngineService(database)
        val videoSync = VideoSyncService(replay)
        try {
            database.insertTelemetryFrames(
                listOf(
                    TelemetryFrame(1000, "session", "Drive/X", 1.0),
                    TelemetryFrame(3000, "session", "Drive/X", 3.0)
                )
            )
            replay.loadSession("session")
            videoSync.setVideoDuration(10_000)
            videoSync.alignTimestamp(videoTimeMs = 500, logTimeMs = 1000)
            assertEquals(500L, videoSync.logOffsetMs.value)

            videoSync.seekVideo(1500)
            assertEquals(0.5, replay.progress.value, 1e-9)
            // Between samples replay correctly exposes the most recent actual frame.
            assertEquals(1000L, replay.currentFrame.value?.timestampMs)

            videoSync.seekVideo(-50)
            assertEquals(0.0, replay.progress.value, 1e-9)

            videoSync.seekVideo(50_000)
            assertEquals(1.0, replay.progress.value, 1e-9)

            // Test adjustOffset
            videoSync.adjustOffset(250)
            assertEquals(750L, videoSync.logOffsetMs.value)

            // Test play and pause delegation
            videoSync.play()
            assertEquals(ReplayState.PLAYING, replay.state.value)
            videoSync.pause()
            assertEquals(ReplayState.PAUSED, replay.state.value)

            // Test loadVideo
            val dummyVideo = tempDir.resolve("dummy_match.mp4").apply { writeBytes(ByteArray(1024 * 1024 * 10)) }
            videoSync.loadVideo(dummyVideo)
            assertEquals(dummyVideo, videoSync.videoFile.value)
            assertEquals(0L, videoSync.currentVideoTimeMs.value)
        } finally {
            videoSync.dispose()
            replay.dispose()
            database.close()
            tempDir.deleteRecursively()
        }
    }
}
