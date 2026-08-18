package com.ares.analytics.ui.components.robotstudio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.AresAmber
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresRed
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary
import com.ares.analytics.viewmodel.robotstudio.RobotStudioState

@Composable
fun RobotContextInspector(
    selection: RobotStudioSelection,
    state: RobotStudioState,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onOpenSpecSummary: () -> Unit = {},
    onOpenAiAssistant: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(if (isCollapsed) 44.dp else 270.dp)
            .fillMaxHeight(),
        color = AresSurface,
        border = BorderStroke(1.dp, AresBorder),
    ) {
        if (isCollapsed) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Expand inspector", tint = AresCyan)
                }
            }
        } else {
            ExpandedInspectorPanel(
                selection = selection,
                state = state,
                onToggleCollapse = onToggleCollapse,
                onOpenSpecSummary = onOpenSpecSummary,
                onOpenAiAssistant = onOpenAiAssistant,
            )
        }
    }
}

@Composable
private fun ExpandedInspectorPanel(
    selection: RobotStudioSelection,
    state: RobotStudioState,
    onToggleCollapse: () -> Unit,
    onOpenSpecSummary: () -> Unit,
    onOpenAiAssistant: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header with Collapse Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "CONTEXT INSPECTOR",
                color = AresTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            IconButton(
                onClick = onToggleCollapse,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Collapse inspector",
                    tint = AresTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        HorizontalDivider(color = AresBorder)

        // Section Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
            border = BorderStroke(1.dp, AresBorder),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val title = when (selection) {
                    is RobotStudioSelection.Identity -> "Project & Identity"
                    is RobotStudioSelection.Drivetrain -> "Drivetrain Kinematics"
                    is RobotStudioSelection.Subsystem -> selection.displayName.ifBlank { "Subsystem Mechanism" }
                    is RobotStudioSelection.Superstructure -> "Superstructure Coordinator"
                    is RobotStudioSelection.Autonomous -> "Routines & Autonomous"
                    is RobotStudioSelection.Controls -> "TeleOp Gamepad Controls"
                    is RobotStudioSelection.PortMap -> "Port Map & Review"
                }
                val subtitle = when (selection) {
                    is RobotStudioSelection.Identity -> ".ares/project.json"
                    is RobotStudioSelection.Drivetrain -> ".ares/drivetrains/*.aresdrivetrain"
                    is RobotStudioSelection.Subsystem -> ".ares/subsystems/${selection.documentId}.aressubsystem"
                    is RobotStudioSelection.Superstructure -> ".ares/superstructures/*.aressuperstructure"
                    is RobotStudioSelection.Autonomous -> ".ares/routines/*.aresroutine"
                    is RobotStudioSelection.Controls -> ".ares/controls.json"
                    is RobotStudioSelection.PortMap -> ".ares/hardware-review.json"
                }

                Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = AresTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Live Issues / Validation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
            border = BorderStroke(1.dp, AresBorder),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("LIVE VALIDATION", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val issues = state.stages.flatMap { it.issues }
                    val allPass = issues.isEmpty()
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (allPass) AresGreen.copy(alpha = 0.12f) else AresAmber.copy(alpha = 0.12f),
                    ) {
                        Text(
                            if (allPass) "PASS" else "${issues.size} ISSUES",
                            color = if (allPass) AresGreen else AresAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                val issues = state.stages.flatMap { it.issues }
                if (issues.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AresGreen, modifier = Modifier.size(14.dp))
                        Text("No address or safety conflicts", color = AresTextPrimary, fontSize = 11.sp)
                    }
                } else {
                    for (issue in issues.take(3)) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AresAmber, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                            Text(issue, color = AresTextPrimary, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }

        // Quick Tools & Actions
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("STUDIO TOOLS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)

            OutlinedButton(
                onClick = onOpenAiAssistant,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AresCyan),
                border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AresCyan, modifier = Modifier.size(14.dp))
                    Text("AI Robotics Assistant", fontSize = 11.sp)
                }
            }

            OutlinedButton(
                onClick = onOpenSpecSummary,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("Spec Summary Table", fontSize = 11.sp)
                }
            }
        }
    }
}
