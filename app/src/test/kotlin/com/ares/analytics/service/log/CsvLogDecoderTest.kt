package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CsvLogDecoderTest {

    @Test
    fun `native import expands logger extra fields into telemetry keys`() = runTest {
        val tempDir = Files.createTempDirectory("ares-csv-decoder-test").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        try {
            val csv = tempDir.resolve("robot.csv")
            csv.writeText(
                """
                    TimestampMs,Drive/Pose_X,_ExtraFieldsJson
                    1000,1.25,{}
                    1020,1.50,"{""Late/Current"":12.5,""Late/State"":""ready"",""Late/Enabled"":true}"
                """.trimIndent()
            )

            CsvLogDecoder(database).parseCsvLogNative(csv, "session-1")

            val current = database.getTelemetryForKey("session-1", "Late/Current").single()
            val state = database.getTelemetryForKey("session-1", "Late/State").single()
            val enabled = database.getTelemetryForKey("session-1", "Late/Enabled").single()

            assertEquals(12.5, current.value)
            assertEquals("ready", state.stringValue)
            assertEquals(1.0, enabled.value)
        } finally {
            database.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `CSV without a timestamp column is rejected`() = runTest {
        val tempDir = Files.createTempDirectory("ares-csv-invalid-test").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        try {
            val csv = tempDir.resolve("invalid.csv")
            csv.writeText("Drive/Pose_X,Robot/Enabled\n1.25,true")

            assertFailsWith<IllegalArgumentException> {
                CsvLogDecoder(database).parseCsvLogNative(csv, "session-1")
            }
        } finally {
            database.close()
            tempDir.deleteRecursively()
        }
    }
}
