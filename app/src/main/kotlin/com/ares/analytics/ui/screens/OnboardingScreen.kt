package com.ares.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.OnboardingIntent
import com.ares.analytics.viewmodel.OnboardingState
import com.ares.analytics.viewmodel.OnboardingViewModel
import java.io.File
import javax.swing.JFileChooser

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    var projectPath by remember { mutableStateOf("") }
    var teamId by remember { mutableStateOf("") }
    var seasonId by remember { mutableStateOf("") }
    var robotId by remember { mutableStateOf("") }
    var league by remember { mutableStateOf(League.FTC) }
    var nt4Host by remember { mutableStateOf("192.168.43.1") }

    // Sync from state initially
    LaunchedEffect(state.projectPath) {
        if (state.projectPath.isNotEmpty()) projectPath = state.projectPath
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AresBackground),
        contentAlignment = Alignment.Center
    ) {
        // Subtle background glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AresCyanGlow, Color.Transparent),
                        radius = 800f
                    )
                )
        )

        // Glassmorphic main panel
        Surface(
            modifier = Modifier
                .width(550.dp)
                .wrapContentHeight()
                .border(1.dp, AresBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = AresSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(AresRed, AresRedDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PrecisionManufacturing,
                            contentDescription = null,
                            tint = AresTextPrimary
                        )
                    }
                    Text(
                        text = "ARES Mission Control Setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AresTextPrimary
                    )
                }

                Text(
                    text = "Welcome to ARES-Analytics. Configure your workspace parameters below to initialize the local telemetry environment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AresTextSecondary,
                    lineHeight = 20.sp
                )

                Divider(color = AresBorder, thickness = 1.dp)

                // Input fields
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
                        OutlinedTextField(
                            value = projectPath,
                            onValueChange = {
                                projectPath = it
                                viewModel.handleIntent(OnboardingIntent.UpdateFields(projectPath, teamId, seasonId, robotId, league, nt4Host))
                            },
                            label = { Text("Workspace Root Directory", color = AresTextSecondary) },
                            placeholder = { Text("C:\\Users\\...\\my-robot-project", color = AresTextTertiary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AresCyan,
                                unfocusedBorderColor = AresBorder,
                                focusedLabelColor = AresCyan
                            ),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val chooser = JFileChooser().apply {
                                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                    dialogTitle = "Select Robot Project Root"
                                }
                                val result = chooser.showOpenDialog(null)
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    projectPath = chooser.selectedFile.absolutePath
                                    viewModel.handleIntent(
                                        OnboardingIntent.UpdateFields(projectPath, teamId, seasonId, robotId, league, nt4Host)
                                    )
                                    viewModel.handleIntent(OnboardingIntent.DetectLeague)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AresBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Browse", tint = AresTextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Browse", color = AresTextPrimary)
                        }
                    }

                    // Row of Team, Season, Robot IDs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = teamId,
                            onValueChange = {
                                teamId = it
                                viewModel.handleIntent(OnboardingIntent.UpdateFields(projectPath, teamId, seasonId, robotId, league, nt4Host))
                            },
                            label = { Text("Team ID", color = AresTextSecondary) },
                            placeholder = { Text("23247", color = AresTextTertiary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AresCyan,
                                unfocusedBorderColor = AresBorder,
                                focusedLabelColor = AresCyan
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = seasonId,
                            onValueChange = {
                                seasonId = it
                                viewModel.handleIntent(OnboardingIntent.UpdateFields(projectPath, teamId, seasonId, robotId, league, nt4Host))
                            },
                            label = { Text("Season ID", color = AresTextSecondary) },
                            placeholder = { Text("2026", color = AresTextTertiary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AresCyan,
                                unfocusedBorderColor = AresBorder,
                                focusedLabelColor = AresCyan
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = robotId,
                            onValueChange = {
                                robotId = it
                                viewModel.handleIntent(OnboardingIntent.UpdateFields(projectPath, teamId, seasonId, robotId, league, nt4Host))
                            },
                            label = { Text("Robot ID", color = AresTextSecondary) },
                            placeholder = { Text("AresIII", color = AresTextTertiary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AresCyan,
                                unfocusedBorderColor = AresBorder,
                                focusedLabelColor = AresCyan
                            ),
                            singleLine = true
                        )
                    }

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
                                            league = League.FTC
                                            nt4Host = "192.168.43.1"
                                            viewModel.handleIntent(OnboardingIntent.UpdateFields(projectPath, teamId, seasonId, robotId, league, nt4Host))
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
                                            league = League.FRC
                                            nt4Host = "10.0.0.2" // fallback, let detect default it based on TeamId later
                                            viewModel.handleIntent(OnboardingIntent.UpdateFields(projectPath, teamId, seasonId, robotId, league, nt4Host))
                                            viewModel.handleIntent(OnboardingIntent.DetectLeague) // triggers TeamId based override
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("FRC (FIRST Robotics)", color = if (league == League.FRC) AresCyan else AresTextSecondary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // NT Host
                        OutlinedTextField(
                            value = nt4Host,
                            onValueChange = {
                                nt4Host = it
                                viewModel.handleIntent(OnboardingIntent.UpdateFields(projectPath, teamId, seasonId, robotId, league, nt4Host))
                            },
                            label = { Text("NT4 Host Address", color = AresTextSecondary) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AresCyan,
                                unfocusedBorderColor = AresBorder,
                                focusedLabelColor = AresCyan
                            ),
                            singleLine = true
                        )
                    }
                }

                // Java Environment Verification Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                        .background(AresSurfaceElevated)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val envValid = state.javaEnvValid
                        val icon = when (envValid) {
                            true -> Icons.Default.CheckCircle
                            false -> Icons.Default.Error
                            null -> Icons.Default.HourglassEmpty
                        }
                        val tint = when (envValid) {
                            true -> AresGreen
                            false -> AresError
                            null -> AresTextTertiary
                        }
                        Icon(imageVector = icon, contentDescription = null, tint = tint)
                        Column {
                            Text(
                                "JAVA_HOME Verification",
                                style = MaterialTheme.typography.labelMedium,
                                color = AresTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (state.isVerifyingJava) "Verifying JVM toolchain..." else state.javaEnvMsg.take(50) + "...",
                                style = MaterialTheme.typography.labelSmall,
                                color = AresTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.handleIntent(OnboardingIntent.VerifyJava) }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry Verification", tint = AresCyan)
                    }
                }

                // Error Message if any
                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = AresError,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Row containing buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onCancel != null) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AresTextPrimary)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Button(
                        onClick = { viewModel.handleIntent(OnboardingIntent.SubmitConfig) },
                        modifier = Modifier.weight(if (onCancel != null) 2f else 1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan, disabledContainerColor = AresBorder),
                        enabled = !state.isSaving && state.javaEnvValid == true,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AresBackground, strokeWidth = 2.dp)
                        } else {
                            Text("Initialize Workspace", color = AresBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
