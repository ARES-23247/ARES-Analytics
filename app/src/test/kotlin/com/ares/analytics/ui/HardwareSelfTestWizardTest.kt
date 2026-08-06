package com.ares.analytics.ui

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.Nt4ClientService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class HardwareSelfTestWizardTest {

    private lateinit var mockDbService: DatabaseService
    private lateinit var mockNt4Service: Nt4ClientService

    @Before
    fun setUp() {
        val tempDb = File.createTempFile("self_test_db", ".sqlite")
        mockDbService = DatabaseService(tempDb.absolutePath)
        mockNt4Service = Nt4ClientService(mockDbService)
    }

    @Test
    fun testSelfTestStepInitialization() {
        val steps = listOf(
            "1. Battery Health & Resistance",
            "2. Front-Left Motor ('fl') Pulse",
            "3. Front-Right Motor ('fr') Pulse",
            "4. Rear-Left Motor ('rl') Pulse",
            "5. Rear-Right Motor ('rr') Pulse",
            "6. GoBilda Pinpoint Odometry",
            "7. Limelight AprilTag Camera"
        )

        assertEquals(7, steps.size)
        assertTrue(steps[0].contains("Battery"))
        assertTrue(steps[5].contains("Pinpoint"))
    }
}
