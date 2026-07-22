package com.ares.analytics.ui.components.core

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
enum class TargetSelection(val label: String) {
    LIVE_ROBOT("Live Robot"),
    LOCAL_SIM("Local Sim")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun ExecutionToolbar(
    targetSelection: TargetSelection,
    targetIp: String,
    isLiveRobotOnline: Boolean,
    isLocalSimOnline: Boolean,
    isBuildRunning: Boolean,
    isSimRunning: Boolean,
    onTargetChanged: (TargetSelection) -> Unit,
    onTargetIpChanged: (String) -> Unit,
    onRunBuild: () -> Unit,
    onRunSim: () -> Unit,
    onStopAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(AresSurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Target Dropdown
        var dropdownExpanded by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { dropdownExpanded = true }
                    .background(AresSurface)
                    .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (targetSelection == TargetSelection.LIVE_ROBOT) Icons.Default.PrecisionManufacturing else Icons.Default.Computer,
                    contentDescription = null,
                    tint = AresCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = targetSelection.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AresTextPrimary
                )

                // Status Dot
                val isOnline = if (targetSelection == TargetSelection.LIVE_ROBOT) isLiveRobotOnline else isLocalSimOnline
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isOnline) AresGreen else AresTextSecondary.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                )

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = AresTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
            ) {
                TargetSelection.entries.forEach { target ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (target == TargetSelection.LIVE_ROBOT) Icons.Default.PrecisionManufacturing else Icons.Default.Computer,
                                    contentDescription = null,
                                    tint = if (target == targetSelection) AresCyan else AresTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(target.label, color = AresTextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                val isTargetOnline = if (target == TargetSelection.LIVE_ROBOT) isLiveRobotOnline else isLocalSimOnline
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isTargetOnline) AresGreen else AresTextSecondary.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        },
                        onClick = {
                            onTargetChanged(target)
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))
        VerticalDivider(modifier = Modifier.height(24.dp), color = AresBorder)
        Spacer(modifier = Modifier.width(4.dp))

        // Build & Deploy Button
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("Build & Deploy (Ctrl+B)") } },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = onRunBuild,
                enabled = !isBuildRunning,
                modifier = Modifier.size(32.dp)
            ) {
                if (isBuildRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AresCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Build & Deploy",
                        tint = AresGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Launch Simulator Button
        val simIconTint by animateColorAsState(targetValue = if (isSimRunning) AresGreen else AresCyan)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("Launch Desktop Simulator (Ctrl+D)") } },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = onRunSim,
                enabled = !isSimRunning,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DesktopWindows,
                    contentDescription = "Launch Simulator",
                    tint = simIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))
        VerticalDivider(modifier = Modifier.height(20.dp), color = AresBorder.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.width(4.dp))

        // Target IP input and CLI Driver Launch
        BasicTextField(
            value = targetIp,
            onValueChange = onTargetIpChanged,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary, fontSize = 12.sp),
            cursorBrush = SolidColor(AresCyan),
            modifier = Modifier
                .width(90.dp)
                .height(26.dp)
                .background(AresSurface, RoundedCornerShape(4.dp))
                .border(1.dp, AresBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    innerTextField()
                }
            }
        )

        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("Launch Interactive CLI Driver Shell") } },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = {
                    val ip = targetIp.trim()
                    val argsStr = if (ip == "127.0.0.1") "" else " --args=\"$ip\""
                    val command = """cd /d c:\Users\david\dev\robotics\ftc\ARESLib-Kotlin && .\gradlew.bat :simulator:runFakeController --console=plain""" + argsStr
                    try {
                        ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", command).start()
                    } catch (e: Exception) {
                        System.err.println("Failed to launch CLI: ${e.message}")
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Launch CLI Driver",
                    tint = AresCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Stop Button
        val isAnyRunning = isBuildRunning || isSimRunning
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("Kill Active Process (Ctrl+K)") } },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = onStopAll,
                enabled = isAnyRunning,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = if (isAnyRunning) AresError else AresTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
