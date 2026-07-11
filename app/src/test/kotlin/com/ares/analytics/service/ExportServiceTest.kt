package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportServiceTest {

    @Test
    fun testExportToCsvList() = runTest {
        val tempDb = File.createTempFile("export_test_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val exportService = ExportService(databaseService)

        val sessionId = "session-csv-list"
        val frames = listOf(
            TelemetryFrame(1000L, sessionId, "/test/motor1", 1.5),
            TelemetryFrame(2000L, sessionId, "/test/motor1", 2.5)
        )
        databaseService.insertTelemetryFrames(frames)

        val tempCsv = File.createTempFile("export_list", ".csv").apply { deleteOnExit() }
        exportService.exportToCsvList(sessionId, listOf("/test/motor1"), tempCsv)

        val lines = tempCsv.readLines()
        assertEquals(3, lines.size)
        assertEquals("key,timestamp_ms,value", lines[0])
        assertEquals("/test/motor1,1000,1.5", lines[1])
        assertEquals("/test/motor1,2000,2.5", lines[2])

        tempCsv.delete()
        tempDb.delete()
    }

    @Test
    fun testExportToCsvTable() = runTest {
        val tempDb = File.createTempFile("export_test_db_2", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val exportService = ExportService(databaseService)

        val sessionId = "session-csv-table"
        val frames = listOf(
            TelemetryFrame(1000L, sessionId, "/test/motor1", 1.5),
            TelemetryFrame(1000L, sessionId, "/test/motor2", 0.5),
            TelemetryFrame(2000L, sessionId, "/test/motor1", 2.5)
        )
        databaseService.insertTelemetryFrames(frames)

        val tempCsv = File.createTempFile("export_table", ".csv").apply { deleteOnExit() }
        exportService.exportToCsvTable(sessionId, listOf("/test/motor1", "/test/motor2"), tempCsv)

        val lines = tempCsv.readLines()
        assertEquals(3, lines.size)
        assertEquals("timestamp_ms,/test/motor1,/test/motor2", lines[0])
        assertEquals("1000,1.5,0.5", lines[1])
        assertEquals("2000,2.5,0.5", lines[2]) // sample and hold fills motor2 with 0.5

        tempCsv.delete()
        tempDb.delete()
    }

    @Test
    fun testExportToWpiLog() = runTest {
        val tempDb = File.createTempFile("export_test_db_3", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val exportService = ExportService(databaseService)

        val sessionId = "session-wpilog"
        val frames = listOf(
            TelemetryFrame(1000L, sessionId, "/test/motor1", 1.5)
        )
        databaseService.insertTelemetryFrames(frames)

        val tempWpiLog = File.createTempFile("export_wpilog", ".wpilog").apply { deleteOnExit() }
        exportService.exportToWpiLog(sessionId, listOf("/test/motor1"), tempWpiLog)

        val bytes = tempWpiLog.readBytes()
        assertTrue(bytes.size > 12)
        // Check for WPILOG header (WPILOG)
        val header = String(bytes.copyOfRange(0, 6), Charsets.UTF_8)
        assertEquals("WPILOG", header)

        tempWpiLog.delete()
        tempDb.delete()
    }
}
