package com.ares.analytics.service

import com.ares.analytics.service.log.*
import com.ares.analytics.shared.AppJson
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Integration tests for durable, duplicate-safe local log import. */
class AutoImportServiceTest {

    @Test
    fun testLocalLogsAutoImport() = runBlocking {
        val tempDb = File.createTempFile("auto_import_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val driverAnalysisService = DriverAnalysisService(databaseService, sysIdService)
        val summaryEngineService = SummaryEngineService(databaseService, sysIdService, driverAnalysisService)
        val logParserService = LogParserService(databaseService, summaryEngineService)
        val hootDecoderService = HootDecoderService(databaseService, summaryEngineService, sysIdService)

        // Mock ProcessManagerService
        val processManagerService = ProcessManagerService()

        // Create a temporary project path
        val tempProjectDir = File(System.getProperty("java.io.tmpdir"), "ares_project_test_${System.currentTimeMillis()}")
        tempProjectDir.mkdirs()
        val logsDir = File(tempProjectDir, "logs")
        logsDir.mkdirs()

        // Write a mock log file
        val mockLog = File(logsDir, "test_run.csv")
        val mockContents = """
            time, voltage, velocity
            1000, 12.0, 1.5
            2000, 11.8, 1.6
            """.trimIndent()
        mockLog.writeText(mockContents)
        val originalLastModified = mockLog.lastModified()
        val config = WorkspaceConfig(
            teamId = "1234",
            seasonId = "2026",
            robotId = "ares-test",
            projectPath = tempProjectDir.absolutePath,
            league = League.FTC
        )
        var importSuccessCalled = false
        val autoImportService = AutoImportService(
            logParserService = logParserService,
            hootDecoderService = hootDecoderService,
            processManagerService = processManagerService,
            configProvider = { config },
            scope = this,
            scanIntervalMs = 50L
        )

        // Start scanner and wait for import
        autoImportService.start {
            importSuccessCalled = true
        }

        // We run a single manual loop cycle inside the test instead of delay loop,
        // or we just call the private methods by exposing them, or since the service runs in a loop,
        // we can wait a moment or just verify the file moves after a short delay since it is running on a coroutine.
        // Let's delay the test thread slightly to allow the loop to run.
        var retries = 0
        while (!importSuccessCalled && retries < 50) {
            kotlinx.coroutines.delay(100)
            retries++
        }

        autoImportService.stop()

        // Verify the file was imported and moved
        assertTrue(importSuccessCalled, "onImportSuccess was not called")

        assertTrue(!mockLog.exists(), "Original log file was not deleted/moved")

        // Verify session was inserted into database
        val sessions = databaseService.getSessions()
        assertEquals(1, sessions.size)
        assertEquals("1234", sessions[0].teamId)
        assertEquals("ares-test", sessions[0].robotId)
        val reports = File(logsDir, "imported").listFiles { file ->
            file.name.endsWith(AutoImportService.IMPORT_REPORT_SUFFIX)
        }.orEmpty()
        assertEquals(1, reports.size)
        val report = AppJson.decodeFromString<ImportReport>(reports.single().readText())
        assertEquals(ImportStatus.SUCCESS, report.status)
        assertEquals(4L, report.acceptedRecords)
        assertEquals(listOf("velocity", "voltage"), report.detectedTopics)

        // Recreate the exact same source identity and restart the service. The durable
        // manifest must prevent a second session even though in-memory observations are new.
        mockLog.writeText(mockContents)
        mockLog.setLastModified(originalLastModified)
        val restarted = AutoImportService(
            logParserService = logParserService,
            hootDecoderService = hootDecoderService,
            processManagerService = processManagerService,
            configProvider = { config },
            scope = this,
            scanIntervalMs = 50L
        )
        restarted.start { }
        kotlinx.coroutines.delay(300)
        restarted.stop()
        assertEquals(1, databaseService.getSessions().size, "same source identity was imported twice")

        // Clean up
        tempProjectDir.deleteRecursively()
        tempDb.delete()
        processManagerService.shutdown()
    }

    @Test
    fun `quarantine persists rejection and suppresses the same fingerprint`() = runBlocking {
        val tempDb = File.createTempFile("auto_quarantine_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val sysIdService = SysIdService(databaseService)
        val summaryEngineService = SummaryEngineService(
            databaseService,
            sysIdService,
            DriverAnalysisService(databaseService, sysIdService)
        )
        val logParserService = LogParserService(databaseService, summaryEngineService)
        val processManagerService = ProcessManagerService()
        val projectDir = File(System.getProperty("java.io.tmpdir"), "ares_quarantine_test_${System.nanoTime()}")
        val logsDir = File(projectDir, "logs").apply { mkdirs() }
        val sourceFile = File(logsDir, "bad.csv").apply { writeText("not,a,valid,log") }
        val config = WorkspaceConfig(
            teamId = "1234",
            seasonId = "2026",
            robotId = "ares-test",
            projectPath = projectDir.absolutePath,
            league = League.FTC
        )
        val service = AutoImportService(
            logParserService,
            HootDecoderService(databaseService, summaryEngineService, sysIdService),
            processManagerService,
            configProvider = { config },
            scope = this,
            scanIntervalMs = 25L
        )
        service.start { error("Rejected imports must not trigger the success callback") }
        val quarantineDir = File(projectDir, "logs/quarantine")
        var attempts = 0
        while (quarantineDir.listFiles().orEmpty().none { it.name.endsWith(AutoImportService.IMPORT_REPORT_SUFFIX) } && attempts < 100) {
            kotlinx.coroutines.delay(25)
            attempts++
        }
        service.stop()

        assertTrue(sourceFile.exists(), "Rejected source should remain available for repair")
        val quarantined = quarantineDir.listFiles().orEmpty().single { it.name.endsWith("bad.csv") }
        val manifest = File(quarantineDir, AutoImportService.QUARANTINE_MANIFEST_NAME)
        assertTrue(manifest.isFile)
        val reportFile = File(quarantineDir, quarantined.name + AutoImportService.IMPORT_REPORT_SUFFIX)
        val report = AppJson.decodeFromString<ImportReport>(reportFile.readText())
        assertEquals(ImportStatus.REJECTED, report.status)
        assertEquals("bad.csv", report.sourceName)
        assertTrue(report.error.orEmpty().contains("no timestamp column"))
        assertEquals(0L, report.acceptedRecords)
        assertTrue(databaseService.getSessions().isEmpty())

        val reportModifiedAt = reportFile.lastModified()
        val restarted = AutoImportService(
            logParserService,
            HootDecoderService(databaseService, summaryEngineService, sysIdService),
            processManagerService,
            configProvider = { config },
            scope = this,
            scanIntervalMs = 25L
        )
        restarted.start { error("Quarantined fingerprint was retried as a success") }
        kotlinx.coroutines.delay(200)
        restarted.stop()
        assertEquals(reportModifiedAt, reportFile.lastModified(), "same rejected fingerprint was retried")
        assertEquals(1, quarantineDir.listFiles().orEmpty().count { it.name.endsWith(AutoImportService.IMPORT_REPORT_SUFFIX) })

        databaseService.close()
        processManagerService.shutdown()
        projectDir.deleteRecursively()
        tempDb.delete()
        Unit
    }
}
