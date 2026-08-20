package com.ares.analytics.ui.input

import com.ares.analytics.di.KeyboardDriveState
import com.ares.analytics.service.GamepadState
import com.ares.analytics.shared.League
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopDriveInputPublisherTest {
    @Test
    fun `keyboard motion requires armed dashboard and deadman`() {
        val keyboard = KeyboardDriveState().apply {
            enabled = true
            isWPressed = true
        }

        val withoutDeadman = desktopDriveIntent(
            keyboard,
            GamepadState(),
            controlSurfaceActive = true,
            league = League.FTC,
            isRedAlliance = true,
        )
        assertEquals(DesktopFieldDriveCommand(0.0, 0.0, 0.0), withoutDeadman.command)

        keyboard.deadmanPressed = true
        val armed = desktopDriveIntent(
            keyboard,
            GamepadState(),
            controlSurfaceActive = true,
            league = League.FTC,
            isRedAlliance = true,
        )
        assertEquals(DesktopFieldDriveCommand(0.0, 4.0, 0.0), armed.command)
    }

    @Test
    fun `first accepted frame is neutral before motion and mechanism flags`() {
        val intent = DesktopDriveIntent(
            command = DesktopFieldDriveCommand(1.0, 2.0, 3.0),
            modeFlags = desktopDriveModeFlags(isRedAlliance = true),
            actuationFlags = (1L shl 0) or (1L shl 6),
        )
        val session = DesktopDriveFrameSession(sessionNonce = 77.0, clockMs = { 1234L })

        val neutral = session.frameFor(intent).copyOf()
        assertEquals(listOf(0.0, 0.0, 0.0), neutral.slice(4..6))
        assertEquals(intent.modeFlags.toDouble(), neutral[7])

        session.markTransmitted()
        val active = session.frameFor(intent).copyOf()
        assertEquals(listOf(1.0, 2.0, 3.0), active.slice(4..6))
        assertEquals((intent.modeFlags or intent.actuationFlags).toDouble(), active[7])
        assertEquals(1.0, active[2])
    }

    @Test
    fun `unaccepted transport attempt neither arms nor advances sequence`() {
        val session = DesktopDriveFrameSession(sessionNonce = 9.0, clockMs = { 50L })
        val intent = DesktopDriveIntent(
            command = DesktopFieldDriveCommand(4.0, 0.0, 0.0),
            modeFlags = desktopDriveModeFlags(isRedAlliance = false),
            actuationFlags = 1L shl 1,
        )

        val firstAttempt = session.frameFor(intent).copyOf()
        val retry = session.frameFor(intent).copyOf()

        assertEquals(0.0, firstAttempt[2])
        assertEquals(0.0, retry[2])
        assertEquals(0.0, retry[4])
        assertEquals(intent.modeFlags.toDouble(), retry[7])
    }

    @Test
    fun `gamepad mechanism flags require its deadman`() {
        val keyboard = KeyboardDriveState().apply {
            enabled = true
            useGamepad = true
        }
        val gamepad = GamepadState(
            connected = true,
            leftTrigger = 1.0f,
            leftStickY = 1.0f,
            leftBumper = true,
            a = true,
        )

        val intent = desktopDriveIntent(
            keyboard,
            gamepad,
            controlSurfaceActive = true,
            league = League.FRC,
            isRedAlliance = false,
        )

        assertTrue(intent.command.vxMetersPerSecond > 0.0)
        assertTrue(intent.actuationFlags and (1L shl 0) != 0L)
        assertTrue(intent.actuationFlags and (1L shl 6) != 0L)
    }
}
