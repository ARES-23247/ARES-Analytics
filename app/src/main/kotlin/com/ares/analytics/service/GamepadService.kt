package com.ares.analytics.service

import com.studiohartman.jamepad.ControllerManager
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

data class GamepadState(
    val connected: Boolean = false,
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

class GamepadService {
    private val controllerManager = ControllerManager(2)
    private var isInitialized = false

    private val _gamepad1State = MutableStateFlow(GamepadState())
    val gamepad1State: StateFlow<GamepadState> = _gamepad1State.asStateFlow()

    private val _gamepad2State = MutableStateFlow(GamepadState())
    val gamepad2State: StateFlow<GamepadState> = _gamepad2State.asStateFlow()

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (!isInitialized) {
            try {
                controllerManager.initSDLGamepad()
                isInitialized = true
            } catch (e: Throwable) {
                println("[GamepadService] SDL gamepad init failed (native library missing?): ${e.message}")
                return
            }
        }

        if (pollingJob?.isActive == true || !isInitialized) return

        pollingJob = scope.launch {
            while (isActive) {
                controllerManager.update()

                val state1 = controllerManager.getState(0)
                if (state1.isConnected) {
                    _gamepad1State.update {
                        GamepadState(
                            connected = true,
                            leftStickX = state1.leftStickX,
                            leftStickY = state1.leftStickY,
                            rightStickX = state1.rightStickX,
                            rightStickY = state1.rightStickY,
                            leftTrigger = state1.leftTrigger,
                            rightTrigger = state1.rightTrigger,
                            a = state1.a,
                            b = state1.b,
                            x = state1.x,
                            y = state1.y,
                            leftBumper = state1.lb,
                            rightBumper = state1.rb,
                            dpadUp = state1.dpadUp,
                            dpadDown = state1.dpadDown,
                            dpadLeft = state1.dpadLeft,
                            dpadRight = state1.dpadRight
                        )
                    }
                } else {
                    if (_gamepad1State.value.connected) {
                        _gamepad1State.update { GamepadState(connected = false) }
                    }
                }

                val state2 = controllerManager.getState(1)
                if (state2.isConnected) {
                    _gamepad2State.update {
                        GamepadState(
                            connected = true,
                            leftStickX = state2.leftStickX,
                            leftStickY = state2.leftStickY,
                            rightStickX = state2.rightStickX,
                            rightStickY = state2.rightStickY,
                            leftTrigger = state2.leftTrigger,
                            rightTrigger = state2.rightTrigger,
                            a = state2.a,
                            b = state2.b,
                            x = state2.x,
                            y = state2.y,
                            leftBumper = state2.lb,
                            rightBumper = state2.rb,
                            dpadUp = state2.dpadUp,
                            dpadDown = state2.dpadDown,
                            dpadLeft = state2.dpadLeft,
                            dpadRight = state2.dpadRight
                        )
                    }
                } else {
                    if (_gamepad2State.value.connected) {
                        _gamepad2State.update { GamepadState(connected = false) }
                    }
                }

                delay(20) // 50 Hz polling rate
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun dispose() {
        stop()
        if (isInitialized) {
            controllerManager.quitSDLGamepad()
            isInitialized = false
        }
    }
}
