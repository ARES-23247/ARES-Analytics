package com.ares.analytics.ui.input

import com.ares.analytics.di.KeyboardDriveState
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDriveKeyDispatcherTest {
    @Test
    fun `drive keys are ignored until control is armed then tracked independently of deadman`() {
        val state = KeyboardDriveState()

        assertFalse(applyDesktopDriveKey(state, true, KeyEvent.VK_SPACE, true))
        state.enabled = true
        assertTrue(applyDesktopDriveKey(state, true, KeyEvent.VK_W, true))
        assertTrue(state.isWPressed)

        assertTrue(applyDesktopDriveKey(state, true, KeyEvent.VK_SPACE, true))
        assertTrue(state.deadmanPressed)
        assertTrue(applyDesktopDriveKey(state, true, KeyEvent.VK_W, true))
        assertTrue(state.isWPressed)
    }

    @Test
    fun `releasing deadman stops authorization without losing held movement keys`() {
        val state = KeyboardDriveState().apply { enabled = true }
        applyDesktopDriveKey(state, true, KeyEvent.VK_SPACE, true)
        applyDesktopDriveKey(state, true, KeyEvent.VK_W, true)
        applyDesktopDriveKey(state, true, KeyEvent.VK_LEFT, true)

        assertTrue(applyDesktopDriveKey(state, true, KeyEvent.VK_SPACE, false))
        assertTrue(state.enabled)
        assertFalse(state.deadmanPressed)
        assertTrue(state.isWPressed)
        assertTrue(state.isLeftPressed)
    }

    @Test
    fun `dispatcher does not steal shortcuts or gamepad mode`() {
        val state = KeyboardDriveState().apply { enabled = true }

        assertFalse(applyDesktopDriveKey(state, true, KeyEvent.VK_D, true, controlDown = true))
        state.useGamepad = true
        assertFalse(applyDesktopDriveKey(state, true, KeyEvent.VK_SPACE, true))
        assertFalse(state.deadmanPressed)
    }

    @Test
    fun `control modified release still clears a previously held drive key`() {
        val state = KeyboardDriveState().apply { enabled = true }

        assertTrue(applyDesktopDriveKey(state, true, KeyEvent.VK_W, true))
        assertTrue(state.isWPressed)
        assertTrue(applyDesktopDriveKey(state, true, KeyEvent.VK_W, false, controlDown = true))
        assertFalse(state.isWPressed)
    }
}
