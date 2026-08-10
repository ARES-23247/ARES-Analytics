package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseServiceIntegrationTest {

    @Test
    fun `mixed live and persistent frames are routed by each frame session`() = runTest {
        withDatabase { database ->
            database.insertTelemetryFrames(
                listOf(
                    TelemetryFrame(1000, "live-telemetry", "Drive/Live", 1.0),
                    TelemetryFrame(1000, "recorded", "Drive/Recorded", 2.0)
                )
            )

            assertEquals(
                listOf(1.0),
                database.getTelemetryRange("live-telemetry", 0, Long.MAX_VALUE).map { it.value }
            )
            assertEquals(
                listOf(2.0),
                database.getTelemetryRange("recorded", 0, Long.MAX_VALUE).map { it.value }
            )
        }
    }

    @Test
    fun `persistent appender preserves null separately from an empty string`() = runTest {
        withDatabase { database ->
            database.insertTelemetryFrames(
                listOf(
                    TelemetryFrame(1000, "session", "Numeric", 4.0, null),
                    TelemetryFrame(1000, "session", "EmptyString", 0.0, "")
                )
            )

            assertNull(database.getTelemetryForKey("session", "Numeric").single().stringValue)
            assertEquals("", database.getTelemetryForKey("session", "EmptyString").single().stringValue)
        }
    }

    @Test
    fun `latest baseline is strict and returns one newest value per key`() = runTest {
        withDatabase { database ->
            database.insertTelemetryFrames(
                listOf(
                    TelemetryFrame(1000, "session", "A", 1.0),
                    TelemetryFrame(1100, "session", "A", 2.0),
                    TelemetryFrame(1050, "session", "B", 0.0, "READY"),
                    TelemetryFrame(1100, "session", "B", 0.0, "RUNNING")
                )
            )

            val baseline = database.getLatestTelemetryBefore("session", 1100).associateBy { it.key }

            assertEquals(1.0, baseline.getValue("A").value)
            assertEquals("READY", baseline.getValue("B").stringValue)
        }
    }

    @Test
    fun `microsecond timestamps and same-time duplicate samples are preserved in order`() = runTest {
        withDatabase { database ->
            database.insertTelemetryFrames(
                listOf(
                    TelemetryFrame(1000, "session", "/Drive/Velocity", 1.0, timestampUs = 1_000_001),
                    TelemetryFrame(1000, "session", "Drive/Velocity", 2.0, timestampUs = 1_000_002),
                    TelemetryFrame(1000, "session", "Drive/Velocity", 3.0, timestampUs = 1_000_002)
                )
            )

            val frames = database.getTelemetryForKey("session", "/Drive/Velocity")
            assertEquals(listOf(1.0, 2.0, 3.0), frames.map { it.value })
            assertEquals(listOf(1_000_001L, 1_000_002L, 1_000_002L), frames.map { it.timestampUs })
            assertTrue(frames.map { it.sampleOrder }.distinct().size == 3)
            assertEquals(listOf("Drive/Velocity"), database.getDistinctTelemetryKeys("session"))
        }
    }

    @Test
    fun `database browser accepts read-only metadata queries`() = runTest {
        withDatabase { database ->
            val result = database.executeQueryRaw("SHOW TABLES")
            assertTrue(result.rows.flatten().contains("telemetry_frames"))
        }
    }

    private suspend fun withDatabase(block: suspend (DatabaseService) -> Unit) {
        val tempDir = Files.createTempDirectory("ares-database-integration").toFile()
        val database = DatabaseService(tempDir.resolve("telemetry.duckdb").absolutePath)
        try {
            block(database)
        } finally {
            database.close()
            tempDir.deleteRecursively()
        }
    }
}
