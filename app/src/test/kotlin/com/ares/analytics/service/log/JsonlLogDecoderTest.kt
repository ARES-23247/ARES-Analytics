package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    @Test
    fun `booleans are numeric and imported keys are normalized`() = runTest {
        val tempDb = File.createTempFile("jsonl_boolean", ".db").apply { deleteOnExit() }
        val log = File.createTempFile("telemetry_boolean", ".jsonl").apply {
            deleteOnExit()
            writeText("""{"timestampMs":1000,"/Robot/Enabled":true}""")
        }
        val database = DatabaseService(tempDb.absolutePath)
        try {
            val batcher = FrameBatcher(database)
            val accepted = JsonlLogDecoder(database).parseJsonlLog(log, "session", batcher)
            batcher.flush()

            val frame = database.getTelemetryForKey("session", "Robot/Enabled").single()
            assertEquals(1, accepted)
            assertEquals(1.0, frame.value)
            assertNull(frame.stringValue)
        } finally {
            database.close()
            log.delete()
            tempDb.delete()
        }
    }

    @Test
    fun `log with no usable telemetry is rejected`() = runTest {
        val tempDb = File.createTempFile("jsonl_invalid", ".db").apply { deleteOnExit() }
        val log = File.createTempFile("telemetry_invalid", ".jsonl").apply {
            deleteOnExit()
            writeText("[]\nnot-json\n{\"missingTimestamp\":1}\n")
        }
        val database = DatabaseService(tempDb.absolutePath)
        try {
            val batcher = FrameBatcher(database)
            assertFailsWith<IllegalArgumentException> {
                JsonlLogDecoder(database).parseJsonlLog(log, "session", batcher)
            }
        } finally {
            database.close()
            log.delete()
            tempDb.delete()
        }
    }
}
