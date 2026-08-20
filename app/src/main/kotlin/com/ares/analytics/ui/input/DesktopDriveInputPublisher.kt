package com.ares.analytics.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ares.analytics.di.KeyboardDriveState
import com.ares.analytics.service.GamepadState
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.League
import com.areslib.math.InputMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

/** One fail-closed desktop control snapshot before protocol sequencing is applied. */
internal data class DesktopDriveIntent(
    val command: DesktopFieldDriveCommand,
    val modeFlags: Long,
    val actuationFlags: Long,
)

/**
 * Builds the atomic v2 drive frames for one leased connection session.
 *
 * The first successfully transmitted frame is always neutral. Motion and mechanism flags are
 * admitted only after that handshake, and sequence numbers advance only after transport accepts a
 * frame. The returned array is reused by this session and must be consumed synchronously.
 */
internal class DesktopDriveFrameSession(
    private val sessionNonce: Double,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val frame = DoubleArray(FRAME_VALUE_COUNT)
    private var sequence = 0L
    private var neutralHandshakeTransmitted = false

    fun frameFor(intent: DesktopDriveIntent): DoubleArray = frame.apply {
        this[VERSION_INDEX] = FRAME_VERSION
        this[SESSION_INDEX] = sessionNonce
        this[SEQUENCE_INDEX] = sequence.toDouble()
        this[CLIENT_TIME_INDEX] = clockMs().toDouble()
        this[VX_INDEX] = if (neutralHandshakeTransmitted) intent.command.vxMetersPerSecond else 0.0
        this[VY_INDEX] = if (neutralHandshakeTransmitted) intent.command.vyMetersPerSecond else 0.0
        this[OMEGA_INDEX] = if (neutralHandshakeTransmitted) intent.command.omegaRadiansPerSecond else 0.0
        this[FLAGS_INDEX] = (
            intent.modeFlags or if (neutralHandshakeTransmitted) intent.actuationFlags else 0L
        ).toDouble()
    }

    fun markTransmitted() {
        neutralHandshakeTransmitted = true
        sequence++
    }
}

/** Converts current keyboard/gamepad state into canonical league-aware field commands. */
internal fun desktopDriveIntent(
    keyboard: KeyboardDriveState,
    gamepad: GamepadState,
    controlSurfaceActive: Boolean,
    league: League,
    isRedAlliance: Boolean,
): DesktopDriveIntent {
    val armedSurface = controlSurfaceActive && keyboard.enabled
    val deadmanActive = if (keyboard.useGamepad) {
        gamepad.connected && gamepad.leftTrigger > 0.5f
    } else {
        keyboard.deadmanPressed
    }
    val inputActive = armedSurface && deadmanActive

    val command = when {
        !inputActive -> DesktopFieldDriveCommand(0.0, 0.0, 0.0)
        keyboard.useGamepad && gamepad.connected -> {
            val forward = InputMath.applyCurve(InputMath.applyDeadband(gamepad.leftStickY.toDouble(), 0.02), 1.2)
            val right = InputMath.applyCurve(InputMath.applyDeadband(gamepad.leftStickX.toDouble(), 0.02), 1.2)
            val counterClockwise = -InputMath.applyCurve(
                InputMath.applyDeadband(gamepad.rightStickX.toDouble(), 0.02),
                1.2,
            )
            mapDesktopFieldCentricDrive(league, forward, right, counterClockwise)
        }
        else -> mapDesktopFieldCentricDrive(
            league = league,
            forward = when {
                keyboard.isWPressed || keyboard.isUpPressed -> 1.0
                keyboard.isSPressed || keyboard.isDownPressed -> -1.0
                else -> 0.0
            },
            right = when {
                keyboard.isDPressed -> 1.0
                keyboard.isAPressed -> -1.0
                else -> 0.0
            },
            counterClockwise = when {
                keyboard.isLeftPressed -> 1.0
                keyboard.isRightPressed -> -1.0
                else -> 0.0
            },
        )
    }

    var actuationFlags = 0L
    if (inputActive && inputPressed(keyboard, gamepad, keyboard.isQPressed) { it.leftBumper }) {
        actuationFlags = actuationFlags or FLAG_INTAKE
    }
    if (inputActive && inputPressed(keyboard, gamepad, keyboard.isEPressed) { it.rightBumper }) {
        actuationFlags = actuationFlags or FLAG_FLYWHEEL
    }
    if (inputActive && inputPressed(keyboard, gamepad, keyboard.isShiftPressed) { it.rightTrigger > 0.5f }) {
        actuationFlags = actuationFlags or FLAG_TRANSFER
    }
    if (inputActive && inputPressed(keyboard, gamepad, keyboard.isJPressed) { it.a }) {
        actuationFlags = actuationFlags or FLAG_BUTTON_A
    }
    if (inputActive && inputPressed(keyboard, gamepad, keyboard.isLPressed) { it.b }) {
        actuationFlags = actuationFlags or FLAG_BUTTON_B
    }
    if (inputActive && inputPressed(keyboard, gamepad, keyboard.isUPressed) { it.x }) {
        actuationFlags = actuationFlags or FLAG_BUTTON_X
    }

    return DesktopDriveIntent(
        command = command,
        modeFlags = desktopDriveModeFlags(isRedAlliance),
        actuationFlags = actuationFlags,
    )
}

private inline fun inputPressed(
    keyboard: KeyboardDriveState,
    gamepad: GamepadState,
    keyboardPressed: Boolean,
    gamepadPressed: (GamepadState) -> Boolean,
): Boolean = if (keyboard.useGamepad && gamepad.connected) gamepadPressed(gamepad) else keyboardPressed

/** Owns the resilient 50 Hz desktop control publisher outside the root screen composition. */
@Composable
internal fun DesktopDriveInputPublisher(
    nt4ClientService: Nt4ClientService,
    keyboardState: KeyboardDriveState,
    gamepadState: StateFlow<GamepadState>,
    connected: Boolean,
    controlSurfaceActive: Boolean,
    league: League,
) {
    LaunchedEffect(connected, controlSurfaceActive, league) {
        if (!connected) return@LaunchedEffect
        while (currentCoroutineContext().isActive) {
            val session = DesktopDriveFrameSession(nt4ClientService.nextDriveSessionNonce())
            try {
                while (currentCoroutineContext().isActive) {
                    val intent = desktopDriveIntent(
                        keyboard = keyboardState,
                        gamepad = gamepadState.value,
                        controlSurfaceActive = controlSurfaceActive,
                        league = league,
                        isRedAlliance = nt4ClientService.selectedRedAlliance.value,
                    )
                    if (nt4ClientService.publishDriveFrame(session.frameFor(intent))) {
                        session.markTransmitted()
                    }
                    delay(PUBLISH_INTERVAL_MS)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                keyboardState.releaseAll()
                System.err.println(
                    "[DesktopDriveInput] Publisher session failed; restarting with a neutral frame: " +
                        "${error::class.simpleName}: ${error.message}"
                )
                error.printStackTrace(System.err)
                delay(RESTART_DELAY_MS)
            }
        }
    }
}

private const val PUBLISH_INTERVAL_MS = 20L
private const val RESTART_DELAY_MS = 250L
private const val FRAME_VALUE_COUNT = 8
private const val FRAME_VERSION = 2.0
private const val VERSION_INDEX = 0
private const val SESSION_INDEX = 1
private const val SEQUENCE_INDEX = 2
private const val CLIENT_TIME_INDEX = 3
private const val VX_INDEX = 4
private const val VY_INDEX = 5
private const val OMEGA_INDEX = 6
private const val FLAGS_INDEX = 7
private const val FLAG_INTAKE = 1L shl 0
private const val FLAG_FLYWHEEL = 1L shl 1
private const val FLAG_TRANSFER = 1L shl 2
private const val FLAG_BUTTON_A = 1L shl 6
private const val FLAG_BUTTON_B = 1L shl 7
private const val FLAG_BUTTON_X = 1L shl 8
