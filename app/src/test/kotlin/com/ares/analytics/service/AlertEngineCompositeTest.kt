package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class AlertEngineCompositeTest {

    private lateinit var alertService: AlertEngineService
    private lateinit var mockNt4Service: Nt4ClientService
    private lateinit var mockDbService: DatabaseService

    @Before
    fun setUp() {
        val tempDbFile = File.createTempFile("test_alerts_db", ".sqlite")
        mockDbService = DatabaseService(tempDbFile.absolutePath)
        mockNt4Service = Nt4ClientService(mockDbService)

        val tempThresholds = File.createTempFile("thresholds_test", ".json")
        alertService = AlertEngineService(mockDbService, mockNt4Service, tempThresholds.absolutePath)
    }

    @Test
    fun testMotorStallAlertTriggering() {
        runBlocking {
            val pwrFrame = TelemetryFrame(100L, "live", "Hardware/Motors/fl/Power", 0.8)
            val velFrame = TelemetryFrame(100L, "live", "Hardware/Motors/fl/Velocity", 0.0)
            val curFrame = TelemetryFrame(100L, "live", "Hardware/Motors/fl/CurrentAmps", 10.0)

            mockNt4Service.emitReplayFrame(pwrFrame)
            mockNt4Service.emitReplayFrame(velFrame)
            repeat(3) {
                mockNt4Service.emitReplayFrame(curFrame)
            }

            kotlinx.coroutines.delay(250)

            val activeAlerts = alertService.alerts.value
            val stallAlert = activeAlerts.firstOrNull { it.ruleKey.contains("Hardware/Motors/fl/Stall") }

            assertNotNull(stallAlert)
            assertFalse(stallAlert!!.triaged)
        }
    }
}
