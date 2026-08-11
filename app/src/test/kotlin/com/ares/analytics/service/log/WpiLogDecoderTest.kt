package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WpiLogDecoderTest {

    @Test
    fun `decodes length-prefixed declarations extra header and wide timestamps`() = runTest {
        val tempDir = Files.createTempDirectory("ares-wpilog-test").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        try {
            val log = tempDir.resolve("robot.wpilog")
            val bytes = ByteArrayOutputStream()
            bytes.writeFileHeader("producer metadata")
            bytes.writeRecord(0, 0, controlStart(1, "/Drive/Pose_X", "double", "{}"))
            bytes.writeRecord(0, 0, controlStart(2, "/Robot/Mode", "string", "{}"))
            bytes.writeRecord(1, 1_234_567, doublePayload(2.75), timestampSize = 6)
            bytes.writeRecord(2, 1_235_000, "AUTO".toByteArray(Charsets.UTF_8), timestampSize = 6)
            log.writeBytes(bytes.toByteArray())

            val batcher = FrameBatcher(database, batchSize = 10)
            WpiLogDecoder().parseWpiLog(log, "session-1", batcher)
            batcher.flush()

            val pose = database.getTelemetryForKey("session-1", "/Drive/Pose_X").single()
            val mode = database.getTelemetryForKey("session-1", "/Robot/Mode").single()
            assertEquals(1_234L, pose.timestampMs)
            assertEquals(2.75, pose.value)
            assertEquals(1_235L, mode.timestampMs)
            assertEquals("AUTO", mode.stringValue)
        } finally {
            database.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rejects impossible payload before allocating it`() = runTest {
        val tempDir = Files.createTempDirectory("ares-wpilog-invalid-test").toFile()
        try {
            val log = tempDir.resolve("invalid.wpilog")
            val bytes = ByteArrayOutputStream()
            bytes.writeFileHeader("")
            bytes.writeRecordHeader(entryId = 1, payloadSize = 70_000_000, timestampMicros = 1)
            log.writeBytes(bytes.toByteArray())

            val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
            try {
                assertFailsWith<IOException> {
                    WpiLogDecoder().parseWpiLog(log, "session-1", FrameBatcher(database))
                }
            } finally {
                database.close()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rejects pre-format WPILOG versions`() = runTest {
        val tempDir = Files.createTempDirectory("ares-wpilog-version-test").toFile()
        try {
            val log = tempDir.resolve("old-version.wpilog")
            val bytes = ByteArrayOutputStream().apply { writeFileHeader("", version = 0x00ff) }
            log.writeBytes(bytes.toByteArray())
            val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
            try {
                assertFailsWith<IOException> {
                    WpiLogDecoder().parseWpiLog(log, "session-1", FrameBatcher(database))
                }
            } finally {
                database.close()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun ByteArrayOutputStream.writeFileHeader(extraHeader: String, version: Int = 0x0100) {
        write("WPILOG".toByteArray(Charsets.US_ASCII))
        writeLittleEndian(version.toLong(), 2)
        val extraBytes = extraHeader.toByteArray(Charsets.UTF_8)
        writeLittleEndian(extraBytes.size.toLong(), 4)
        write(extraBytes)
    }

    private fun ByteArrayOutputStream.writeRecord(
        entryId: Long,
        timestampMicros: Long,
        payload: ByteArray,
        timestampSize: Int = minimumSize(timestampMicros)
    ) {
        writeRecordHeader(entryId, payload.size.toLong(), timestampMicros, timestampSize)
        write(payload)
    }

    private fun ByteArrayOutputStream.writeRecordHeader(
        entryId: Long,
        payloadSize: Long,
        timestampMicros: Long,
        timestampSize: Int = minimumSize(timestampMicros)
    ) {
        val entrySize = minimumSize(entryId)
        val payloadSizeBytes = minimumSize(payloadSize)
        val descriptor = (entrySize - 1) or ((payloadSizeBytes - 1) shl 2) or ((timestampSize - 1) shl 4)
        write(descriptor)
        writeLittleEndian(entryId, entrySize)
        writeLittleEndian(payloadSize, payloadSizeBytes)
        writeLittleEndian(timestampMicros, timestampSize)
    }

    private fun ByteArrayOutputStream.writeLittleEndian(value: Long, size: Int) {
        repeat(size) { index -> write(((value ushr (index * 8)) and 0xFF).toInt()) }
    }

    private fun controlStart(entryId: Int, name: String, type: String, metadata: String): ByteArray =
        ByteArrayOutputStream().apply {
            write(0)
            writeLittleEndian(entryId.toLong(), 4)
            writeString(name)
            writeString(type)
            writeString(metadata)
        }.toByteArray()

    private fun ByteArrayOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeLittleEndian(bytes.size.toLong(), 4)
        write(bytes)
    }

    private fun doublePayload(value: Double): ByteArray = ByteArrayOutputStream().apply {
        writeLittleEndian(value.toBits(), 8)
    }.toByteArray()

    private companion object {
        fun minimumSize(value: Long): Int {
            for (size in 1..7) {
                if (value ushr (size * 8) == 0L) return size
            }
            return 8
        }
    }
}
