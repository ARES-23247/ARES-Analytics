package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.di.KeyboardDriveState
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.AresAmber
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val TELEOP_LIST_TOPIC = "ARES/DriverStation/TeleOpList"
private const val SELECTED_OPMODE_TOPIC = "ARES/DriverStation/SelectedOpMode"
private const val DRIVER_STATION_COMMAND_TOPIC = "ARES/DriverStation/Command"

internal fun preferredSimulatorTeleOp(teleOps: List<String>): String? =
    teleOps.firstOrNull { it.endsWith(".ARESMecanumTeleOp") || it == "ARESMecanumTeleOp" }
        ?: teleOps.firstOrNull { it.endsWith(".ARESRemoteDriveOpMode") || it == "ARESRemoteDriveOpMode" }
        ?: teleOps.firstOrNull()

internal fun decodeSimulatorTeleOps(value: String?): List<String> =
    value?.let { encoded ->
        runCatching { Json.decodeFromString<List<String>>(encoded) }.getOrDefault(emptyList())
    }.orEmpty()

internal enum class LocalSimulatorPrimaryAction(val label: String) {
    LAUNCH_SIMULATOR("Launch simulator"),
    VERIFY_AND_LAUNCH("Verify & launch"),
    VERIFYING_PROJECT("Building simulator"),
    WAIT_FOR_CONNECTION("Connecting"),
    START_DRIVING("Start driving"),
    STARTING_TELEOP("Starting"),
    TELEOP_RUNNING("Running"),
}

internal fun localSimulatorPrimaryAction(
    isConnected: Boolean,
    isSimulatorProcessRunning: Boolean,
    isLaunchPreparationRunning: Boolean,
    launchRequiresVerification: Boolean,
    isTeleOpStarting: Boolean,
    isTeleOpRunning: Boolean,
): LocalSimulatorPrimaryAction = when {
    !isConnected && isLaunchPreparationRunning -> LocalSimulatorPrimaryAction.VERIFYING_PROJECT
    !isConnected && isSimulatorProcessRunning -> LocalSimulatorPrimaryAction.WAIT_FOR_CONNECTION
    !isConnected && launchRequiresVerification -> LocalSimulatorPrimaryAction.VERIFY_AND_LAUNCH
    !isConnected -> LocalSimulatorPrimaryAction.LAUNCH_SIMULATOR
    isTeleOpStarting -> LocalSimulatorPrimaryAction.STARTING_TELEOP
    isTeleOpRunning -> LocalSimulatorPrimaryAction.TELEOP_RUNNING
    else -> LocalSimulatorPrimaryAction.START_DRIVING
}

internal enum class LocalSimulatorLaunchRequest {
    NONE,
    START_SIMULATOR,
    VERIFY_THEN_START,
}

internal fun localSimulatorLaunchRequest(
    canRunSimulation: Boolean,
    canRunBuild: Boolean,
    isBuildRunning: Boolean,
    isSimulatorRunning: Boolean,
    isSimulatorOnline: Boolean,
    isLaunchPending: Boolean,
): LocalSimulatorLaunchRequest = when {
    isBuildRunning || isSimulatorRunning || isSimulatorOnline || isLaunchPending -> LocalSimulatorLaunchRequest.NONE
    canRunSimulation -> LocalSimulatorLaunchRequest.START_SIMULATOR
    canRunBuild -> LocalSimulatorLaunchRequest.VERIFY_THEN_START
    else -> LocalSimulatorLaunchRequest.NONE
}

/**
 * An always-visible control path for the headless FTC simulator.
 *
 * Starting the simulator only creates the physics/NT4 server. Motion additionally requires a
 * running TeleOp and an armed local-control surface. The transport rejects drive frames for every
 * non-loopback target, so this strip cannot become a physical-robot control path.
 */
@Composable
fun LocalSimulatorControlBar(
    nt4Client: Nt4ClientService,
    keyboardDriveState: KeyboardDriveState,
    league: League,
    isConnected: Boolean,
    isSimulatorProcessRunning: Boolean,
    isLaunchPreparationRunning: Boolean,
    launchRequiresVerification: Boolean,
    canLaunchSimulator: Boolean,
    simulatorLaunchDisabledReason: String?,
    onLaunchSimulator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var teleOps by remember(nt4Client) {
        mutableStateOf(decodeSimulatorTeleOps(nt4Client.latestValues[TELEOP_LIST_TOPIC]?.stringValue))
    }
    var selectedOpMode by remember(nt4Client) {
        mutableStateOf(
            nt4Client.latestValues[SELECTED_OPMODE_TOPIC]?.stringValue
                ?.takeIf { it.isNotBlank() }
                ?: preferredSimulatorTeleOp(teleOps),
        )
    }
    var command by remember(nt4Client) {
        mutableStateOf(nt4Client.latestValues[DRIVER_STATION_COMMAND_TOPIC]?.stringValue ?: "STOP")
    }
    var starting by remember { mutableStateOf(false) }
    var selectorExpanded by remember { mutableStateOf(false) }
    var startJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val connectedNow by rememberUpdatedState(isConnected)

    LaunchedEffect(nt4Client) {
        nt4Client.uiTelemetryFlow.collect { frame ->
            when (frame.key.trimStart('/')) {
                TELEOP_LIST_TOPIC -> frame.stringValue?.let { encoded ->
                    teleOps = decodeSimulatorTeleOps(encoded)
                    if (selectedOpMode !in teleOps) selectedOpMode = preferredSimulatorTeleOp(teleOps)
                }
                SELECTED_OPMODE_TOPIC -> frame.stringValue?.takeIf { it.isNotBlank() }?.let {
                    selectedOpMode = it
                }
                DRIVER_STATION_COMMAND_TOPIC -> frame.stringValue?.let {
                    command = it
                    if (it == "START" || it == "STOP") starting = false
                }
            }
        }
    }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            startJob?.cancel()
            startJob = null
            starting = false
            keyboardDriveState.disarm()
        }
    }

    val isFtc = league == League.FTC
    val isRunning = isConnected && (!isFtc || command == "START") && !starting
    val primaryAction = localSimulatorPrimaryAction(
        isConnected = isConnected,
        isSimulatorProcessRunning = isSimulatorProcessRunning,
        isLaunchPreparationRunning = isLaunchPreparationRunning,
        launchRequiresVerification = launchRequiresVerification,
        isTeleOpStarting = starting,
        isTeleOpRunning = isRunning,
    )
    val statusText = when {
        !isConnected && isSimulatorProcessRunning -> "CONNECTING"
        !isConnected -> "OFFLINE"
        starting -> "STARTING"
        isRunning && isFtc -> "TELEOP RUNNING"
        isRunning && keyboardDriveState.enabled -> "CONTROL ARMED"
        isRunning -> "CONNECTED"
        command == "INIT" -> "INITIALIZED"
        else -> "WAITING FOR TELEOP"
    }
    val statusColor = when {
        !isConnected -> AresTextSecondary
        starting || command == "INIT" -> AresAmber
        isRunning -> AresGreen
        else -> AresCyan
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AresSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isRunning) AresGreen else AresBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                Text(
                    if (isFtc) "Local FTC Simulator" else "Local FRC Simulator",
                    color = AresTextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

                Box(Modifier.weight(1f)) {
                    Surface(
                        onClick = {
                            if (isFtc && isConnected && teleOps.isNotEmpty() && !starting) {
                                selectorExpanded = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        color = AresSurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AresBorder),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (!isFtc) {
                                    if (isConnected) "Enable TeleOp in the WPILib Simulation GUI" else "FRC simulator is not running"
                                } else {
                                    selectedOpMode?.substringAfterLast('.')
                                        ?: if (isConnected) "Waiting for TeleOp list…" else "Simulator is not running"
                                },
                                color = if (isFtc && selectedOpMode == null) AresTextSecondary else AresTextPrimary,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                            if (isFtc) {
                                Icon(Icons.Default.ArrowDropDown, null, tint = AresTextSecondary, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = selectorExpanded,
                        onDismissRequest = { selectorExpanded = false },
                        modifier = Modifier.background(AresSurfaceElevated),
                    ) {
                        teleOps.forEach { opMode ->
                            DropdownMenuItem(
                                text = { Text(opMode.substringAfterLast('.'), color = AresTextPrimary) },
                                onClick = {
                                    selectedOpMode = opMode
                                    selectorExpanded = false
                                },
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (
                            primaryAction == LocalSimulatorPrimaryAction.LAUNCH_SIMULATOR ||
                            primaryAction == LocalSimulatorPrimaryAction.VERIFY_AND_LAUNCH
                        ) {
                            onLaunchSimulator()
                            return@Button
                        }
                        if (!isFtc || primaryAction != LocalSimulatorPrimaryAction.START_DRIVING) return@Button
                        val opMode = selectedOpMode ?: return@Button
                        startJob?.cancel()
                        startJob = scope.launch {
                            starting = true
                            keyboardDriveState.disarm()
                            nt4Client.publishString(SELECTED_OPMODE_TOPIC, opMode)
                            nt4Client.publishString(DRIVER_STATION_COMMAND_TOPIC, "INIT")
                            command = "INIT"
                            delay(2_000)
                            if (!connectedNow) {
                                starting = false
                                return@launch
                            }
                            nt4Client.publishString(DRIVER_STATION_COMMAND_TOPIC, "START")
                            command = "START"
                            keyboardDriveState.enabled = true
                            starting = false
                        }
                    },
                    enabled = when (primaryAction) {
                        LocalSimulatorPrimaryAction.LAUNCH_SIMULATOR,
                        LocalSimulatorPrimaryAction.VERIFY_AND_LAUNCH -> canLaunchSimulator
                        LocalSimulatorPrimaryAction.START_DRIVING -> selectedOpMode != null
                        else -> false
                    },
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                ) {
                    if (
                        primaryAction == LocalSimulatorPrimaryAction.WAIT_FOR_CONNECTION ||
                        primaryAction == LocalSimulatorPrimaryAction.VERIFYING_PROJECT ||
                        primaryAction == LocalSimulatorPrimaryAction.STARTING_TELEOP
                    ) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = AresOnAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (
                                primaryAction == LocalSimulatorPrimaryAction.LAUNCH_SIMULATOR ||
                                primaryAction == LocalSimulatorPrimaryAction.VERIFY_AND_LAUNCH
                            ) {
                                Icons.Default.DesktopWindows
                            } else {
                                Icons.Default.PlayArrow
                            },
                            null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(primaryAction.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        startJob?.cancel()
                        startJob = null
                        starting = false
                        command = "STOP"
                        keyboardDriveState.disarm()
                        if (isFtc) {
                            scope.launch { nt4Client.publishString(DRIVER_STATION_COMMAND_TOPIC, "STOP") }
                        }
                    },
                    enabled = isConnected,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AresError),
                    border = BorderStroke(1.dp, AresError),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Default.Stop,
                        if (isFtc) "Stop simulated OpMode" else "Disarm FRC desktop control",
                        modifier = Modifier.size(16.dp),
                    )
                }

                OutlinedButton(
                    onClick = {
                        keyboardDriveState.releaseAll()
                        keyboardDriveState.useGamepad = !keyboardDriveState.useGamepad
                    },
                    enabled = isRunning,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AresTextPrimary),
                    border = BorderStroke(1.dp, AresBorder),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        if (keyboardDriveState.useGamepad) Icons.Default.Gamepad else Icons.Default.Keyboard,
                        if (keyboardDriveState.useGamepad) "Use keyboard input" else "Use gamepad input",
                        modifier = Modifier.size(16.dp),
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (keyboardDriveState.enabled) keyboardDriveState.disarm()
                        else keyboardDriveState.enabled = true
                    },
                    enabled = isRunning,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (keyboardDriveState.enabled) AresGreen else AresTextPrimary,
                    ),
                    border = BorderStroke(1.dp, if (keyboardDriveState.enabled) AresGreen else AresBorder),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(if (keyboardDriveState.enabled) "ARMED" else "Arm control", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                when {
                    !isConnected && isLaunchPreparationRunning ->
                        "Building and verifying the current robot project. The first launch can take about a minute; the simulator starts automatically when this finishes."
                    !isConnected && isSimulatorProcessRunning -> "Simulator process started. Waiting for NT4 on 127.0.0.1:5810…"
                    !isConnected && !canLaunchSimulator -> simulatorLaunchDisabledReason
                        ?.takeIf { it.isNotBlank() }
                        ?: "Verify & build the current robot project before launching its simulator."
                    !isConnected && launchRequiresVerification -> "Verify the current project, then launch its simulator automatically. No code is deployed."
                    !isConnected -> "Launch the physics server here. When it connects, choose a TeleOp and Start driving."
                    !isFtc -> if (keyboardDriveState.enabled) {
                        "FRC field-centric control armed: W crosses the field toward the opposing station (+/-X). Loopback only."
                    } else {
                        "Enable TeleOp in the WPILib Simulation GUI, then Arm control. Robot-centric axes remain league-independent."
                    }
                    !isRunning -> "Choose a TeleOp, then Start driving. The simulator can be online while no OpMode is running."
                    keyboardDriveState.useGamepad -> "Move the sticks directly while armed. Dashboard drive frames are blocked for non-loopback targets."
                    else -> "Field-centric: W drives toward the opposing station, A/D strafe, and ←/→ rotate. Loopback only."
                },
                color = if (isRunning && keyboardDriveState.enabled) AresGreen else AresTextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
    }
}
