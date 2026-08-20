package com.ares.analytics.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import com.ares.analytics.di.KeyboardDriveState
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * Applies one native desktop key transition to the armed local-control state.
 *
 * Compose controls such as buttons, menus, and text fields can become the focused AWT child and
 * consume a key before a root `Modifier.onPreviewKeyEvent` observes it. The desktop dispatcher is
 * intentionally installed at the window event-queue boundary; it remains inert until Dashboard is
 * active and local control has been explicitly armed.
 */
internal fun applyDesktopDriveKey(
    state: KeyboardDriveState,
    controlSurfaceActive: Boolean,
    keyCode: Int,
    isPressed: Boolean,
    controlDown: Boolean = false,
): Boolean {
    if (!controlSurfaceActive || !state.enabled || state.useGamepad) return false

    if (keyCode == KeyEvent.VK_SPACE) {
        if (controlDown && isPressed) return false
        state.deadmanPressed = isPressed
        return true
    }

    // Track physical key state while armed even when Space is not down. The drive publisher is
    // the authorization boundary and remains neutral until deadmanPressed is true. This makes
    // W+Space and Space+W equivalent, and it prevents a Ctrl-modified key release from leaving a
    // movement key latched. Focus loss still calls releaseAll() as the fail-safe reset.
    return when (keyCode) {
        KeyEvent.VK_W -> updateDriveKey(isPressed, controlDown) { state.isWPressed = it }
        KeyEvent.VK_S -> updateDriveKey(isPressed, controlDown) { state.isSPressed = it }
        KeyEvent.VK_A -> updateDriveKey(isPressed, controlDown) { state.isAPressed = it }
        KeyEvent.VK_D -> updateDriveKey(isPressed, controlDown) { state.isDPressed = it }
        KeyEvent.VK_UP -> updateDriveKey(isPressed, controlDown) { state.isUpPressed = it }
        KeyEvent.VK_DOWN -> updateDriveKey(isPressed, controlDown) { state.isDownPressed = it }
        KeyEvent.VK_LEFT -> updateDriveKey(isPressed, controlDown) { state.isLeftPressed = it }
        KeyEvent.VK_RIGHT -> updateDriveKey(isPressed, controlDown) { state.isRightPressed = it }
        KeyEvent.VK_Q -> updateDriveKey(isPressed, controlDown) { state.isQPressed = it }
        KeyEvent.VK_E -> updateDriveKey(isPressed, controlDown) { state.isEPressed = it }
        KeyEvent.VK_J -> updateDriveKey(isPressed, controlDown) { state.isJPressed = it }
        KeyEvent.VK_L -> updateDriveKey(isPressed, controlDown) { state.isLPressed = it }
        KeyEvent.VK_U -> updateDriveKey(isPressed, controlDown) { state.isUPressed = it }
        KeyEvent.VK_I -> updateDriveKey(isPressed, controlDown) { state.isIPressed = it }
        KeyEvent.VK_SHIFT -> updateDriveKey(isPressed, controlDown) { state.isShiftPressed = it }
        else -> false
    }
}

private inline fun updateDriveKey(
    isPressed: Boolean,
    controlDown: Boolean,
    update: (Boolean) -> Unit,
): Boolean {
    if (isPressed && controlDown) return false
    update(isPressed)
    return true
}

@Composable
fun DesktopDriveKeyDispatcher(
    state: KeyboardDriveState,
    controlSurfaceActive: Boolean,
) {
    val activeNow = rememberUpdatedState(controlSurfaceActive)

    DisposableEffect(state) {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val dispatcher = KeyEventDispatcher { event ->
            if (event.id != KeyEvent.KEY_PRESSED && event.id != KeyEvent.KEY_RELEASED) {
                false
            } else {
                applyDesktopDriveKey(
                    state = state,
                    controlSurfaceActive = activeNow.value,
                    keyCode = event.keyCode,
                    isPressed = event.id == KeyEvent.KEY_PRESSED,
                    controlDown = event.isControlDown,
                )
            }
        }
        focusManager.addKeyEventDispatcher(dispatcher)
        onDispose {
            focusManager.removeKeyEventDispatcher(dispatcher)
            state.releaseAll()
        }
    }
}
