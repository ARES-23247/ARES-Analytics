package com.ares.analytics.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.League
import com.ares.analytics.shared.RobotProfile
import com.ares.analytics.ui.components.forms.AresTextField
import com.ares.analytics.ui.theme.*
import javax.swing.JFileChooser

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun SyncStep(
    projectPath: String,
    onProjectPathChange: (String) -> Unit,
    onBrowseProject: () -> Unit,
    teamId: String,
    onTeamIdChange: (String) -> Unit,
    cloudRobots: List<RobotProfile>,
    selectedOptionText: String,
    onSelectedOptionTextChange: (String) -> Unit,
    robotId: String,
    onRobotIdChange: (String) -> Unit,
    seasonId: String,
    onSeasonIdChange: (String) -> Unit,
    robotName: String,
    onRobotNameChange: (String) -> Unit,
    league: League,
    onLeagueChange: (League) -> Unit,
    nt4Host: String,
    onNt4HostChange: (String) -> Unit,
    simulatorCommand: String,
    onSimulatorCommandChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Project Path Field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AresTextField(
                value = projectPath,
                onValueChange = onProjectPathChange,
                label = "Workspace Root Directory",
                placeholder = "C:\\Users\\...\\my-robot-project",
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onBrowseProject,
                colors = ButtonDefaults.buttonColors(containerColor = AresBorder),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Browse", tint = AresTextPrimary)
                Spacer(Modifier.width(6.dp))
                Text("Browse", color = AresTextPrimary)
            }
        }

        // Row of Team ID & Robot Profile Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AresTextField(
                value = teamId,
                onValueChange = {
                    onTeamIdChange(it)
                    onSelectedOptionTextChange("Select Robot Profile...")
                },
                label = "Team ID",
                placeholder = "23247",
                modifier = Modifier.weight(1f)
            )

            if (cloudRobots.isNotEmpty()) {
                var dropdownExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    AresTextField(
                        value = selectedOptionText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = "Robot Profile",
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresCyan),
                        modifier = Modifier.fillMaxWidth().clickable { dropdownExpanded = true },
                        trailingIcon = {
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = AresTextSecondary)
                        }
                    )

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
                    ) {
                        cloudRobots.forEach { robot ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(robot.name, color = AresTextPrimary, fontWeight = FontWeight.Bold)
                                        Text("${robot.league.name} • Season ${robot.seasonId}", color = AresTextSecondary, fontSize = 10.sp)
                                    }
                                },
                                onClick = {
                                    onSelectedOptionTextChange(robot.name)
                                    onRobotIdChange(robot.robotId)
                                    onSeasonIdChange(robot.seasonId)
                                    onLeagueChange(robot.league)
                                    dropdownExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Create Custom Profile (Offline)", color = AresTextSecondary) },
                            onClick = {
                                onSelectedOptionTextChange("Custom Profile (Offline)")
                                onRobotIdChange("")
                                onSeasonIdChange("2026")
                                onLeagueChange(League.FTC)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (cloudRobots.isEmpty() || selectedOptionText == "Custom Profile (Offline)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AresTextField(
                    value = seasonId,
                    onValueChange = onSeasonIdChange,
                    label = "Season ID",
                    placeholder = "2026",
                    modifier = Modifier.weight(1f)
                )

                AresTextField(
                    value = robotId,
                    onValueChange = onRobotIdChange,
                    label = "Robot ID",
                    placeholder = "AresIII",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            AresTextField(
                value = robotName,
                onValueChange = onRobotNameChange,
                label = "Robot Name (Optional)",
                placeholder = "e.g. ARES 2026 Into The Deep",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // League and NT Host row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // League selector
                Column(modifier = Modifier.weight(1f)) {
                    Text("League Type", style = MaterialTheme.typography.labelSmall, color = AresTextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (league == League.FTC) AresCyanGlow else Color.Transparent)
                                .clickable {
                                    onLeagueChange(League.FTC)
                                    onNt4HostChange("192.168.43.1")
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("FTC (FIRST Tech)", color = if (league == League.FTC) AresCyan else AresTextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (league == League.FRC) AresCyanGlow else Color.Transparent)
                                .clickable {
                                    onLeagueChange(League.FRC)
                                    onNt4HostChange("10.0.0.2")
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("FRC (FIRST Robotics)", color = if (league == League.FRC) AresCyan else AresTextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                AresTextField(
                    value = nt4Host,
                    onValueChange = onNt4HostChange,
                    label = "NT4 Host Address",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            AresTextField(
                value = simulatorCommand,
                onValueChange = onSimulatorCommandChange,
                label = "Simulator Command (Optional)",
                placeholder = "e.g. :TeamCode:runSim",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Profile summary block & NT Host row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AresCyan.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Roster Specs", fontSize = 10.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("$robotId (${league.name})", color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Season $seasonId", color = AresTextSecondary, fontSize = 10.sp)
                    }
                }

                AresTextField(
                    value = nt4Host,
                    onValueChange = onNt4HostChange,
                    label = "NT4 Host Address",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            AresTextField(
                value = simulatorCommand,
                onValueChange = onSimulatorCommandChange,
                label = "Simulator Command (Optional)",
                placeholder = "e.g. :TeamCode:runSim",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
