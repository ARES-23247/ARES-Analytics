package com.ares.analytics.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.lwjgl.glfw.GLFW.*

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class GamepadState(
    val connected: Boolean = false,
    val name: String = "",
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val a: Boolean = false,
    val b: Boolean = false,
    val x: Boolean = false,
    val y: Boolean = false,
    val leftBumper: Boolean = false,
    val rightBumper: Boolean = false,
    val dpadUp: Boolean = false,
    val dpadDown: Boolean = false,
    val dpadLeft: Boolean = false,
    val dpadRight: Boolean = false
)

/**
 * Gamepad input service using LWJGL/GLFW instead of Jamepad/SDL.
 * GLFW bundles platform natives cleanly via Maven classifier JARs,
 * eliminating the org/libsdl/SDL ClassNotFoundError.
 *
 * Joystick polling in GLFW does NOT require a window context.
 */
class GamepadService {
    private var isInitialized = false

    private val _gamepad1State = MutableStateFlow(GamepadState())
    val gamepad1State: StateFlow<GamepadState> = _gamepad1State.asStateFlow()

    private val _gamepad2State = MutableStateFlow(GamepadState())
    val gamepad2State: StateFlow<GamepadState> = _gamepad2State.asStateFlow()

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun start() {
        if (!isInitialized) {
            try {
                if (!glfwInit()) {
                    println("[GamepadService] Failed to initialize GLFW. Gamepad support disabled.")
                    return
                }
                isInitialized = true
                println("[GamepadService] GLFW initialized successfully.")
            } catch (e: Throwable) {
                println("[GamepadService] GLFW init failed: ${e.message}")
                return
            }
        }

        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            val gamepadState = org.lwjgl.glfw.GLFWGamepadState.malloc()
            try {
                while (isActive) {
                    try {
                        pollJoystick(GLFW_JOYSTICK_1, gamepadState, _gamepad1State)
                        pollJoystick(GLFW_JOYSTICK_2, gamepadState, _gamepad2State)
                    } catch (e: Exception) {
                        // Swallow transient GLFW errors
                    }
                    delay(20) // 50 Hz polling rate
                }
            } finally {
                gamepadState.free()
            }
        }
    }

    private fun pollJoystick(
        joystickId: Int,
        gamepadState: org.lwjgl.glfw.GLFWGamepadState,
        stateFlow: MutableStateFlow<GamepadState>
    ) {
        if (!glfwJoystickPresent(joystickId)) {
            if (stateFlow.value.connected) {
                stateFlow.update { GamepadState(connected = false) }
            }
            return
        }
        val name = glfwGetJoystickName(joystickId) ?: "Unknown Gamepad"

        if (glfwJoystickIsGamepad(joystickId) && glfwGetGamepadState(joystickId, gamepadState)) {
            // Standardized gamepad API (Xbox/XInput mapping)
            val lx = gamepadState.axes(GLFW_GAMEPAD_AXIS_LEFT_X)
            val ly = gamepadState.axes(GLFW_GAMEPAD_AXIS_LEFT_Y)
            val rx = gamepadState.axes(GLFW_GAMEPAD_AXIS_RIGHT_X)
            val ry = gamepadState.axes(GLFW_GAMEPAD_AXIS_RIGHT_Y)
            val lt = gamepadState.axes(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER)
            val rt = gamepadState.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)

            stateFlow.update {
                GamepadState(
                    connected = true,
                    name = name,
                    leftStickX = applyDeadzone(lx),
                    leftStickY = applyDeadzone(-ly),
                    rightStickX = applyDeadzone(rx),
                    rightStickY = applyDeadzone(-ry),
                    leftTrigger = normalizeTrigger(lt),
                    rightTrigger = normalizeTrigger(rt),
                    a = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_A) == GLFW_PRESS.toByte(),
                    b = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_B) == GLFW_PRESS.toByte(),
                    x = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_X) == GLFW_PRESS.toByte(),
                    y = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_Y) == GLFW_PRESS.toByte(),
                    leftBumper = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) == GLFW_PRESS.toByte(),
                    rightBumper = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) == GLFW_PRESS.toByte(),
                    dpadUp = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_DPAD_UP) == GLFW_PRESS.toByte(),
                    dpadDown = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_DPAD_DOWN) == GLFW_PRESS.toByte(),
                    dpadLeft = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_DPAD_LEFT) == GLFW_PRESS.toByte(),
                    dpadRight = gamepadState.buttons(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) == GLFW_PRESS.toByte()
                )
            }
        } else {
            // Fallback: raw joystick axes/buttons (DirectInput, Bluetooth, etc.)
            val axes = glfwGetJoystickAxes(joystickId)
            val buttons = glfwGetJoystickButtons(joystickId)
            val lx = if (axes != null && axes.capacity() > 0) axes[0] else 0f
            val ly = if (axes != null && axes.capacity() > 1) axes[1] else 0f
            val rx = if (axes != null && axes.capacity() > 2) axes[2] else 0f
            val ry = if (axes != null && axes.capacity() > 3) axes[3] else 0f
            val lt = if (axes != null && axes.capacity() > 4) axes[4] else -1f
            val rt = if (axes != null && axes.capacity() > 5) axes[5] else -1f
            val cap = buttons?.capacity() ?: 0

            stateFlow.update {
                GamepadState(
                    connected = true,
                    name = name,
                    leftStickX = applyDeadzone(lx),
                    leftStickY = applyDeadzone(-ly),
                    rightStickX = applyDeadzone(rx),
                    rightStickY = applyDeadzone(-ry),
                    leftTrigger = normalizeTrigger(lt),
                    rightTrigger = normalizeTrigger(rt),
                    a = cap > 0 && buttons!![0] == GLFW_PRESS.toByte(),
                    b = cap > 1 && buttons!![1] == GLFW_PRESS.toByte(),
                    x = cap > 2 && buttons!![2] == GLFW_PRESS.toByte(),
                    y = cap > 3 && buttons!![3] == GLFW_PRESS.toByte(),
                    leftBumper = cap > 4 && buttons!![4] == GLFW_PRESS.toByte(),
                    rightBumper = cap > 5 && buttons!![5] == GLFW_PRESS.toByte(),
                    dpadUp = cap > 10 && buttons!![10] == GLFW_PRESS.toByte(),
                    dpadDown = cap > 12 && buttons!![12] == GLFW_PRESS.toByte(),
                    dpadLeft = cap > 13 && buttons!![13] == GLFW_PRESS.toByte(),
                    dpadRight = cap > 11 && buttons!![11] == GLFW_PRESS.toByte()
                )
            }
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun dispose() {
        stop()
        if (isInitialized) {
            // Don't call glfwTerminate() — other parts of the app may use GLFW
            isInitialized = false
        }
    }

    companion object {
        private const val DEADZONE = 0.08f

        /** Apply a circular deadzone to stick axes */
        private fun applyDeadzone(value: Float): Float {
            return if (kotlin.math.abs(value) < DEADZONE) 0f else value
        }

        /** GLFW triggers range from -1.0 (released) to 1.0 (pressed). Normalize to 0.0..1.0 */
        private fun normalizeTrigger(raw: Float): Float {
            return ((raw + 1f) / 2f).coerceIn(0f, 1f)
        }
    }
}
