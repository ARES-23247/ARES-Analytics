package com.ares.analytics.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class AutoTunerServiceTest {

    private lateinit var autoTunerService: AutoTunerService
    private lateinit var mockNt4Service: Nt4ClientService

    @Before
    fun setUp() {
        val tempDb = File.createTempFile("mock_db_tuner", ".sqlite")
        val mockDb = DatabaseService(tempDb.absolutePath)
        mockNt4Service = Nt4ClientService(mockDb)
        autoTunerService = AutoTunerService(mockNt4Service)
    }

    @Test
    fun testAnalyzeLogFileAndDeriveGains() {
        val tempLogFile = File.createTempFile("sample_drive_log", ".jsonl")
        tempLogFile.writeText("""
            {"timestampMs": 100, "velocity": 2.5, "accel": 10.0}
            {"timestampMs": 120, "velocity": 3.0, "accel": 12.0}
        """.trimIndent())

        val rec = autoTunerService.analyzeLogFile(tempLogFile)

        assertNotNull(rec)
        assertTrue(rec?.mechanismName?.contains("sample_drive_log") == true)
        assertTrue((rec?.recommendedkV ?: 0.0) > 0.0)
        assertTrue((rec?.recommendedkA ?: 0.0) > 0.0)

        tempLogFile.delete()
    }

    @Test
    fun testApproveAndApplyGains() {
        runBlocking {
            val tempLogFile = File.createTempFile("sample_drive_log2", ".jsonl")
            tempLogFile.writeText("""
                {"timestampMs": 100, "velocity": 2.0}
            """.trimIndent())

            val rec = autoTunerService.analyzeLogFile(tempLogFile)
            assertNotNull(rec)

            autoTunerService.approveAndApplyGains(rec!!)
            val updatedRec = autoTunerService.currentRecommendation.value

            assertTrue(updatedRec?.studentApproved == true)
            tempLogFile.delete()
        }
    }
}
