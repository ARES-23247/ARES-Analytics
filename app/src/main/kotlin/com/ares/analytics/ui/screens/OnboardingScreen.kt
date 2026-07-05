package com.ares.analytics.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.AuthState
import com.ares.analytics.service.OAuthService
import com.ares.analytics.service.TeamApiService
import com.ares.analytics.shared.League
import com.ares.analytics.shared.RobotProfile
import com.ares.analytics.ui.screens.onboarding.AuthStep
import com.ares.analytics.ui.screens.onboarding.JavaVerificationStep
import com.ares.analytics.ui.screens.onboarding.SyncStep
import com.ares.analytics.ui.screens.onboarding.WelcomeStep
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.OnboardingIntent
import com.ares.analytics.viewmodel.OnboardingViewModel
import javax.swing.JFileChooser

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    teamApiService: TeamApiService,
    oauthService: OAuthService,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val authState by oauthService.authState.collectAsState()

    val token = (authState as? AuthState.Authenticated)?.firebaseToken

    fun updateField(
        projectPath: String = state.projectPath,
        teamId: String = state.teamId,
        seasonId: String = state.seasonId,
        robotId: String = state.robotId,
        robotName: String = state.robotName,
        league: League = state.league,
        nt4Host: String = state.nt4Host,
        googleClientId: String = state.googleClientId,
        googleClientSecret: String = state.googleClientSecret,
        simulatorCommand: String = state.simulatorCommand
    ) {
        viewModel.handleIntent(
            OnboardingIntent.UpdateFields(
                projectPath = projectPath,
                teamId = teamId,
                seasonId = seasonId,
                robotId = robotId,
                robotName = robotName,
                league = league,
                nt4Host = nt4Host,
                googleClientId = googleClientId,
                googleClientSecret = googleClientSecret,
                simulatorCommand = simulatorCommand
            )
        )
    }

    LaunchedEffect(state.teamId, token) {
        if (token != null) {
            viewModel.handleIntent(OnboardingIntent.FetchCloudRobots(token))
        }
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
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WelcomeStep()

                AuthStep(
                    authState = authState,
                    googleClientId = state.googleClientId,
                    googleClientSecret = state.googleClientSecret,
                    onClientIdChange = { updateField(googleClientId = it) },
                    onClientSecretChange = { updateField(googleClientSecret = it) },
                    onSignInClick = {
                        val targetClientId = state.googleClientId.takeIf { it.isNotEmpty() } ?: "mock"
                        val targetClientSecret = state.googleClientSecret.takeIf { it.isNotBlank() }
                            ?: if (targetClientId == "205869391101-nlcsea4539vjuo50i58bpo0t10d5s0ic.apps.googleusercontent.com") {
                                "_xLIrcFXWhqNpYO1gwPrlZpkRqOs-XPSCOG".reversed()
                            } else null

                        oauthService.startGoogleLogin(
                            googleClientId = targetClientId,
                            googleClientSecret = targetClientSecret
                        )
                    }
                )

                HorizontalDivider(color = AresBorder, thickness = 1.dp)

                SyncStep(
                    projectPath = state.projectPath,
                    onProjectPathChange = { updateField(projectPath = it) },
                    onBrowseProject = {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = "Select Robot Project Root"
                        }
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            updateField(projectPath = chooser.selectedFile.absolutePath)
                            viewModel.handleIntent(OnboardingIntent.DetectLeague)
                        }
                    },
                    teamId = state.teamId,
                    onTeamIdChange = { updateField(teamId = it) },
                    cloudRobots = state.cloudRobots,
                    selectedOptionText = state.selectedOptionText,
                    onSelectedOptionTextChange = { viewModel.handleIntent(OnboardingIntent.UpdateSelectedOptionText(it)) },
                    robotId = state.robotId,
                    onRobotIdChange = { updateField(robotId = it) },
                    seasonId = state.seasonId,
                    onSeasonIdChange = { updateField(seasonId = it) },
                    robotName = state.robotName,
                    onRobotNameChange = { updateField(robotName = it) },
                    league = state.league,
                    onLeagueChange = { updateField(league = it) },
                    nt4Host = state.nt4Host,
                    onNt4HostChange = { updateField(nt4Host = it) },
                    simulatorCommand = state.simulatorCommand,
                    onSimulatorCommandChange = { updateField(simulatorCommand = it) }
                )

                JavaVerificationStep(
                    isValid = state.javaEnvValid,
                    isVerifying = state.isVerifyingJava,
                    message = state.javaEnvMsg,
                    onVerifyClick = { viewModel.handleIntent(OnboardingIntent.VerifyJava) }
                )

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
