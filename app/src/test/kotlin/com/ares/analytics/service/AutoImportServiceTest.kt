package com.ares.analytics.service

import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoImportServiceTest {

    @Test
    fun testLocalLogsAutoImport() = runBlocking {
        val tempDb = File.createTempFile("auto_import_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val summaryEngineService = SummaryEngineService(databaseService)
        val logParserService = LogParserService(databaseService, summaryEngineService)
        val sysIdService = SysIdService(databaseService)
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
        mockLog.writeText(
            """
            time, voltage, velocity
            1000, 12.0, 1.5
            2000, 11.8, 1.6
            """.trimIndent()
        )

        val config = WorkspaceConfig(
            teamId = "1234",
            seasonId = "2026",
            robotId = "ares-test",
            projectPath = tempProjectDir.absolutePath,
            league = League.FTC
        )

        var importSuccessCalled = false
        val autoImportService = AutoImportService(
            databaseService = databaseService,
            logParserService = logParserService,
            hootDecoderService = hootDecoderService,
            processManagerService = processManagerService,
            configProvider = { config },
            scope = this
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
        
        val importedFile = File(logsDir, "imported/test_run.csv")
        assertTrue(importedFile.exists(), "Log file was not moved to the 'imported' folder")
        assertTrue(!mockLog.exists(), "Original log file was not deleted/moved")

        // Verify session was inserted into database
        val sessions = databaseService.getSessions()
        assertEquals(1, sessions.size)
        assertEquals("1234", sessions[0].teamId)
        assertEquals("ares-test", sessions[0].robotId)

        // Clean up
        tempProjectDir.deleteRecursively()
        tempDb.delete()
        processManagerService.shutdown()
    }
}
