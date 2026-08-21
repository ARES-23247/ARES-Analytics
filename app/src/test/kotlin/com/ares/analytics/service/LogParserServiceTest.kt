package com.ares.analytics.service

import com.ares.analytics.shared.RobotActionRecord
import com.ares.analytics.shared.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * LogParserServiceTest class.
 */
class LogParserServiceTest {

    @Test
    fun `Driver Station events selections resolve to one dslog primary`() {
        val directory = Files.createTempDirectory("driver-station-primary").toFile()
        try {
            val dslog = File(directory, "FRC_20260401_120000.dslog").apply { writeBytes(byteArrayOf(4)) }
            val events = File(directory, "FRC_20260401_120000.dsevents").apply { writeBytes(byteArrayOf(4)) }

            assertTrue(isDriverStationEventCompanionName(events.name))
            assertEquals(
                listOf(dslog.canonicalFile),
                canonicalLogImportFiles(listOf(dslog, events)),
            )
            assertEquals(
                listOf(dslog.canonicalFile),
                canonicalLogImportFiles(listOf(events)),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    /**
     * testParseJsonlLog fun.
     */
    fun testParseJsonlLog() = runTest {
        val tempDb = File.createTempFile("log_jsonl_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val driverAnalysisService = DriverAnalysisService(databaseService, sysIdService)
        val summaryEngineService = SummaryEngineService(databaseService, sysIdService, driverAnalysisService)
        val logParser = LogParserService(databaseService, summaryEngineService)
        val tempFile = File.createTempFile("log_test", ".jsonl")
        tempFile.deleteOnExit()
        val jsonLines = """
            {"timestampMs": 1000, "voltage": 12.5, "velocity": 2.1}
            {"timestampMs": 1020, "voltage": 12.4, "velocity": 2.2}
            {"timestampMs": 1040, "voltage": 12.3, "velocity": 2.3}
        """.trimIndent()
        tempFile.writeText(jsonLines)
        val imported = logParser.parseLogFileWithReport(
            file = tempFile,
            teamId = "23247",
            seasonId = "2026",
            robotId = "ares-bot",
            tags = listOf("jsonl-test")
        )
        val session = imported.session

        assertEquals("23247", session.teamId)
        assertEquals(40L, session.durationMs) // 1040 - 1000 = 40ms
        assertTrue(session.tags.contains("jsonl-test"))
        assertEquals(ImportStatus.SUCCESS, imported.report.status)
        assertEquals(6L, imported.report.acceptedRecords)
        assertEquals(listOf("velocity", "voltage"), imported.report.detectedTopics)
        assertEquals(1000L, imported.report.minTimestampMs)
        assertEquals(1040L, imported.report.maxTimestampMs)
        assertEquals(64, imported.report.sourceSha256.length)

        // Query telemetry from database
        val frames = databaseService.getTelemetryRange(session.sessionId, 0L, Long.MAX_VALUE).filter { !it.key.startsWith("Diagnostics/") }
        assertEquals(6, frames.size) // 3 timestamps * 2 keys each
        val firstVoltage = frames.first { it.timestampMs == 1000L && it.key == "voltage" }
        assertEquals(12.5, firstVoltage.value)

        tempFile.delete()
        tempDb.delete()
    }

    @Test
    /**
     * testParseCsvLog fun.
     */
    fun testParseCsvLog() = runTest {
        val tempDb = File.createTempFile("log_csv_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val driverAnalysisService = DriverAnalysisService(databaseService, sysIdService)
        val summaryEngineService = SummaryEngineService(databaseService, sysIdService, driverAnalysisService)
        val logParser = LogParserService(databaseService, summaryEngineService)
        val tempFile = File.createTempFile("log_test", ".csv")
        tempFile.deleteOnExit()
        val csvLines = """
            TimestampMs, voltage, velocity
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
        val frames = databaseService.getTelemetryRange(session.sessionId, 0L, Long.MAX_VALUE).filter { !it.key.startsWith("Diagnostics/") }
        assertEquals(6, frames.size)
        val firstVoltage = frames.first { it.timestampMs == 2000L && it.key == "voltage" }
        assertEquals(11.5, firstVoltage.value)

        tempFile.delete()
        tempDb.delete()
    }

    @Test
    fun `compressed ARES csv imports through bounded streaming decoder`() = runTest {
        val tempDb = File.createTempFile("log_csv_gzip_db", ".db")
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val summaryEngineService = SummaryEngineService(
            databaseService,
            sysIdService,
            DriverAnalysisService(databaseService, sysIdService)
        )
        val logParser = LogParserService(databaseService, summaryEngineService)
        val compressed = File.createTempFile("ares_log_test", ".csv.gz")
        val replayEngine = ReplayEngineService(databaseService)
        try {
            GZIPOutputStream(compressed.outputStream()).bufferedWriter().use { writer ->
                writer.write("TimestampMs,Drive/Pose_X,_ExtraFieldsJson\n")
                writer.write("1000,1.25,{}\n")
                writer.write("1020,1.50,\"{\"\"Late/Current\"\":12.5}\"\n")
            }

            val session = logParser.parseLogFile(
                file = compressed,
                teamId = "23247",
                seasonId = "2026",
                robotId = "ares-bot"
            )

            assertEquals(20L, session.durationMs)
            assertEquals(
                listOf(1.25, 1.50),
                databaseService.getTelemetryForKey(session.sessionId, "Drive/Pose_X").map { it.value }
            )
            assertEquals(
                12.5,
                databaseService.getTelemetryForKey(session.sessionId, "Late/Current").single().value
            )
            assertEquals("csv-gzip", logParser.decoderName(compressed))

            replayEngine.loadSession(session.sessionId)
            assertEquals(1.25, replayEngine.currentFrame.value?.values?.get("Drive/Pose_X"))
            assertTrue("Late/Current" !in replayEngine.currentFrame.value?.values.orEmpty())

            replayEngine.scrubTo(1.0)
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(2_000L) {
                    while (replayEngine.currentFrame.value?.values?.get("Drive/Pose_X") != 1.50) delay(10L)
                }
            }
            assertEquals(1.50, replayEngine.currentFrame.value?.values?.get("Drive/Pose_X"))
            assertEquals(12.5, replayEngine.currentFrame.value?.values?.get("Late/Current"))

            replayEngine.scrubTo(0.0)
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(2_000L) {
                    while (replayEngine.currentFrame.value?.values?.get("Drive/Pose_X") != 1.25) delay(10L)
                }
            }
            assertEquals(1.25, replayEngine.currentFrame.value?.values?.get("Drive/Pose_X"))
            assertTrue("Late/Current" !in replayEngine.currentFrame.value?.values.orEmpty())
        } finally {
            replayEngine.disposeAndJoin()
            databaseService.close()
            compressed.delete()
            tempDb.delete()
        }
    }

    @Test
    fun `failed multi-file import removes already inserted frames`() = runTest {
        val tempDb = File.createTempFile("log_cleanup_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val summaryEngineService = SummaryEngineService(
            databaseService,
            sysIdService,
            DriverAnalysisService(databaseService, sysIdService)
        )
        val logParser = LogParserService(databaseService, summaryEngineService)
        val validCsv = File.createTempFile("partial_import", ".csv").apply {
            writeText("TimestampMs,value\n1000,1.0\n1020,2.0")
            deleteOnExit()
        }
        val unsupported = File.createTempFile("partial_import", ".unsupported").apply { deleteOnExit() }

        assertFailsWith<IllegalArgumentException> {
            logParser.parseLogFiles(
                listOf(validCsv, unsupported),
                teamId = "23247",
                seasonId = "2026",
                robotId = "ares-bot"
            )
        }

        assertTrue(databaseService.getSessions().isEmpty())
        val frameCount = databaseService.executeQueryRaw("SELECT COUNT(*) FROM telemetry_frames")
        assertEquals("0", frameCount.rows.single().single())
        databaseService.close()
    }

    @Test
    fun `multi-file CSV import streams overlapping timestamp and key without replacement`() = runTest {
        val tempDb = File.createTempFile("log_csv_overlap_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val logParser = LogParserService(
            databaseService,
            SummaryEngineService(
                databaseService,
                sysIdService,
                DriverAnalysisService(databaseService, sysIdService)
            )
        )
        val first = File.createTempFile("overlap_first", ".csv").apply {
            writeText("TimestampMs,Drive/Velocity\n1000,1.0")
        }
        val second = File.createTempFile("overlap_second", ".csv").apply {
            writeText("TimestampMs,Drive/Velocity\n1000,2.0")
        }
        try {
            val session = logParser.parseLogFiles(
                listOf(first, second),
                teamId = "23247",
                seasonId = "2026",
                robotId = "ares-bot"
            )

            val samples = databaseService.getTelemetryForKey(session.sessionId, "Drive/Velocity")
            assertEquals(listOf(1.0, 2.0), samples.map { it.value })
            assertEquals(2, samples.map { it.sampleOrder }.distinct().size)
            assertTrue(samples[1].sampleOrder > samples[0].sampleOrder)
        } finally {
            databaseService.close()
            first.delete()
            second.delete()
            tempDb.delete()
        }
    }

    @Test
    fun `compressed WPILOG expansion is bounded before decoder import`() = runTest {
        val tempDb = File.createTempFile("log_xz_bomb_db", ".db").apply { deleteOnExit() }
        val compressed = File.createTempFile("oversized", ".wpilogxz").apply { deleteOnExit() }
        val zeros = ByteArray(64 * 1024)
        XZOutputStream(compressed.outputStream(), LZMA2Options()).use { output ->
            repeat(257) { output.write(zeros) } // 16 MiB floor plus one block.
        }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val parser = LogParserService(
            databaseService,
            SummaryEngineService(
                databaseService,
                sysIdService,
                DriverAnalysisService(databaseService, sysIdService),
            ),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            parser.parseLogFile(compressed, "23247", "2026", "ares-bot")
        }
        assertContains(requireNotNull(failure.message), "expands beyond")
        assertTrue(databaseService.getSessions().isEmpty())
        databaseService.close()
    }

    @Test
    fun `deleting a session also deletes its robot actions`() = runTest {
        val tempDb = File.createTempFile("session_delete_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sessionId = "delete-actions-session"
        databaseService.insertSession(Session(sessionId, "23247", "2026", "ares-bot", 1L))
        databaseService.insertRobotActionsBulk(
            listOf(
                RobotActionRecord(
                    timestampMs = 1L,
                    sessionId = sessionId,
                    runId = "run",
                    robotId = "ares-bot",
                    actionType = "Drive",
                    payloadJson = "{}"
                )
            )
        )

        assertEquals(1, databaseService.getActionsForSession(sessionId).size)
        databaseService.deleteSession(sessionId)
        assertTrue(databaseService.getActionsForSession(sessionId).isEmpty())
        databaseService.close()
    }
}
