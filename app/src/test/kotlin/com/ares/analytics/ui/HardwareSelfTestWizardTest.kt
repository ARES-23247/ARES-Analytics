package com.ares.analytics.ui

import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.ui.screens.SelfTestStatus
import com.ares.analytics.ui.screens.evaluateHardwareReadiness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HardwareSelfTestWizardTest {
    private fun frame(key: String, value: Double = 0.0, text: String? = null) =
        TelemetryFrame(1L, "live", key, value, text)

    @Test
    fun `disconnected preflight never reports success`() {
        val steps = evaluateHardwareReadiness(false, emptyMap())
        assertEquals(7, steps.size)
        assertTrue(steps.all { it.status == SelfTestStatus.WAITING })
        assertFalse(steps.any { it.details.contains("ready", ignoreCase = true) })
    }

    @Test
    fun `preflight reports only evidence actually received`() {
        val frames = listOf(
            frame("Robot/BatteryVoltage", 12.4),
            frame("Hardware/Motors/fl/CurrentAmps", 1.2),
            frame("Drive/Pinpoint_Status", text = "HEALTHY"),
            frame("Vision/Limelight/FPS", 20.4)
        ).associateBy(TelemetryFrame::key)

        val byId = evaluateHardwareReadiness(true, frames).associateBy { it.id }
        assertEquals(SelfTestStatus.OBSERVED, byId.getValue("battery").status)
        assertEquals(SelfTestStatus.OBSERVED, byId.getValue("motor_fl").status)
        assertEquals(SelfTestStatus.WAITING, byId.getValue("motor_fr").status)
        assertEquals(SelfTestStatus.OBSERVED, byId.getValue("pinpoint").status)
        assertEquals(SelfTestStatus.OBSERVED, byId.getValue("limelight").status)
    }

    @Test
    fun `low battery and low camera rate remain warnings`() {
        val frames = listOf(
            frame("Robot/BatteryVoltage", 10.2),
            frame("Vision/Limelight/FPS", 2.0)
        ).associateBy(TelemetryFrame::key)
        val byId = evaluateHardwareReadiness(true, frames).associateBy { it.id }
        assertEquals(SelfTestStatus.WARNING, byId.getValue("battery").status)
        assertEquals(SelfTestStatus.WARNING, byId.getValue("limelight").status)
    }
}
