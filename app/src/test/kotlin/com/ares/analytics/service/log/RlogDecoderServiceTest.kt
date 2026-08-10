package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

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
            assertEquals(1500L, voltage.timestampMs)
            assertEquals(12.4, voltage.value)
            assertEquals("AUTO", mode.stringValue)
        } finally {
            database.close()
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

            data.writeByte(2)
            data.writeShort(1)
            data.writeShort(8)
            data.writeDouble(12.4)

            val mode = "AUTO".toByteArray(Charsets.UTF_8)
            data.writeByte(2)
            data.writeShort(2)
            data.writeShort(mode.size)
            data.write(mode)

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
