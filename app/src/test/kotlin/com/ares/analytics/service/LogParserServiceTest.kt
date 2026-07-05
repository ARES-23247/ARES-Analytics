package com.ares.analytics.service

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogParserServiceTest {

    @Test
    fun testParseJsonlLog() = runTest {
        val tempDb = File.createTempFile("log_jsonl_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val summaryEngineService = SummaryEngineService(databaseService)
        val logParser = LogParserService(databaseService, summaryEngineService)

        val tempFile = File.createTempFile("log_test", ".jsonl")
        tempFile.deleteOnExit()

        val jsonLines = """
            {"timestampMs": 1000, "voltage": 12.5, "velocity": 2.1}
            {"timestampMs": 1020, "voltage": 12.4, "velocity": 2.2}
            {"timestampMs": 1040, "voltage": 12.3, "velocity": 2.3}
        """.trimIndent()
        tempFile.writeText(jsonLines)

        val session = logParser.parseLogFile(
            file = tempFile,
            teamId = "23247",
            seasonId = "2026",
            robotId = "ares-bot",
            tags = listOf("jsonl-test")
        )

        assertEquals("23247", session.teamId)
        assertEquals(40L, session.durationMs) // 1040 - 1000 = 40ms
        assertTrue(session.tags.contains("jsonl-test"))

        // Query telemetry from database
        val frames = databaseService.getTelemetryRange(session.sessionId, 0L, Long.MAX_VALUE)
        assertEquals(6, frames.size) // 3 timestamps * 2 keys each
        val firstVoltage = frames.first { it.timestampMs == 1000L && it.key == "voltage" }
        assertEquals(12.5, firstVoltage.value)

        tempFile.delete()
        tempDb.delete()
    }

    @Test
    fun testParseCsvLog() = runTest {
        val tempDb = File.createTempFile("log_csv_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val summaryEngineService = SummaryEngineService(databaseService)
        val logParser = LogParserService(databaseService, summaryEngineService)

        val tempFile = File.createTempFile("log_test", ".csv")
        tempFile.deleteOnExit()

        val csvLines = """
            timestamp, voltage, velocity
            2000, 11.5, 1.1
            2020, 11.4, 1.2
            2040, 11.3, 1.3
        """.trimIndent()
        tempFile.writeText(csvLines)

        val session = logParser.parseLogFile(
            file = tempFile,
            teamId = "23247",
            seasonId = "2026",
            robotId = "ares-bot"
        )

        assertEquals("23247", session.teamId)
        assertEquals(40L, session.durationMs) // 2040 - 2000 = 40ms

        // Query telemetry from database
        val frames = databaseService.getTelemetryRange(session.sessionId, 0L, Long.MAX_VALUE)
        assertEquals(6, frames.size)
        val firstVoltage = frames.first { it.timestampMs == 2000L && it.key == "voltage" }
        assertEquals(11.5, firstVoltage.value)

        tempFile.delete()
        tempDb.delete()
    }
}
