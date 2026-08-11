package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RlogDecoderServiceTest {

    @Test
    fun `revision two preserves numeric and string updates at the same timestamp`() = runTest {
        val tempDir = Files.createTempDirectory("ares-rlog-decoder").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        try {
            val log = tempDir.resolve("sample.rlog")
            log.writeBytes(revisionTwoLog())
            val batcher = FrameBatcher(database)

            RlogDecoderService().decode(log, "session", batcher)
            batcher.flush()

            val voltage = database.getTelemetryForKey("session", "Robot/BatteryVoltage").single()
            val mode = database.getTelemetryForKey("session", "Robot/Mode").single()
            val firstArrayValue = database.getTelemetryForKey("session", "SysId/Data/0").single()
            val secondArrayValue = database.getTelemetryForKey("session", "SysId/Data/1").single()
            assertEquals(1500L, voltage.timestampMs)
            assertEquals(12.4, voltage.value)
            assertEquals("AUTO", mode.stringValue)
            assertEquals(1.25, firstArrayValue.value)
            assertEquals(2.5, secondArrayValue.value)
        } finally {
            database.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `truncated input is rejected instead of silently importing a prefix`() = runTest {
        val tempDir = Files.createTempDirectory("ares-rlog-truncated").toFile()
        try {
            val bytes = revisionTwoLog()
            val log = tempDir.resolve("truncated.rlog")
            log.writeBytes(bytes.copyOf(bytes.size - 4))
            val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
            try {
                val failure = runCatching {
                    RlogDecoderService().decode(log, "session", FrameBatcher(database))
                }.exceptionOrNull()
                assertNotNull(failure)
                assertIs<IllegalArgumentException>(failure)
            } finally {
                database.close()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `missing timestamp block terminator is rejected`() = runTest {
        val tempDir = Files.createTempDirectory("ares-rlog-boundary-truncated").toFile()
        try {
            val bytes = revisionTwoLog()
            val log = tempDir.resolve("missing-boundary.rlog")
            log.writeBytes(bytes.copyOf(bytes.size - 1))
            val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
            try {
                val failure = runCatching {
                    RlogDecoderService().decode(log, "session", FrameBatcher(database))
                }.exceptionOrNull()
                assertNotNull(failure)
                assertIs<IllegalArgumentException>(failure)
            } finally {
                database.close()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun revisionTwoLog(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(2)
            data.writeByte(0)
            data.writeDouble(1.5)

            data.writeKeyDeclaration(1, "Robot/BatteryVoltage", "double")
            data.writeKeyDeclaration(2, "Robot/Mode", "string")
            data.writeKeyDeclaration(3, "SysId/Data", "double[]")

            data.writeByte(2)
            data.writeShort(1)
            data.writeShort(8)
            data.writeDouble(12.4)

            val mode = "AUTO".toByteArray(Charsets.UTF_8)
            data.writeByte(2)
            data.writeShort(2)
            data.writeShort(mode.size)
            data.write(mode)

            data.writeByte(2)
            data.writeShort(3)
            data.writeShort(16)
            data.writeDouble(1.25)
            data.writeDouble(2.5)

            data.writeByte(0)
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeKeyDeclaration(id: Int, key: String, type: String) {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val typeBytes = type.toByteArray(Charsets.UTF_8)
        writeByte(1)
        writeShort(id)
        writeShort(keyBytes.size)
        write(keyBytes)
        writeShort(typeBytes.size)
        write(typeBytes)
    }
}
