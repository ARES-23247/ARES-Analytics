package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonlLogDecoderTest {
    @Test
    fun `valid non-object line is skipped without stalling the reader`() = runTest {
        val tempDb = File.createTempFile("jsonl_decoder", ".db").apply { deleteOnExit() }
        val log = File.createTempFile("telemetry", ".jsonl").apply {
            deleteOnExit()
            writeText("[]\n{\"timestampMs\":1000,\"Drive/Pose_X\":1.5}\n")
        }
        val database = DatabaseService(tempDb.absolutePath)
        try {
            val batcher = FrameBatcher(database)
            withTimeout(2_000) {
                JsonlLogDecoder(database).parseJsonlLog(log, "session", batcher)
            }
            batcher.flush()

            val frames = database.getTelemetryForKey("session", "Drive/Pose_X")
            assertEquals(1, frames.size)
            assertEquals(1.5, frames.single().value)
        } finally {
            database.close()
            log.delete()
            tempDb.delete()
        }
    }
}
