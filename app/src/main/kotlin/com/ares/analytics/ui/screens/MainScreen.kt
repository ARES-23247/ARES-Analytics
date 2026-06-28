package com.ares.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.ares.analytics.di.ServiceRegistry
import com.ares.analytics.service.AutoImportService
import com.ares.analytics.service.MatchInfo
import com.ares.analytics.service.UpdateCheckerService
import com.ares.analytics.shared.*
import com.ares.analytics.ui.components.NavigationTarget
import com.ares.analytics.ui.components.Sidebar
import com.ares.analytics.ui.components.terminal.TerminalDrawer
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.*
import kotlinx.coroutines.launch

@Composable
fun MainScreen(services: ServiceRegistry) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val mainViewModel = remember {
        MainViewModel(
            environmentService = services.environmentService,
            eventApiService = services.eventApiService,
            scope = scope
        )
    }
    val mainState by mainViewModel.state.collectAsState()

    val config = mainState.config
    val activeNav = mainState.activeNav
    val matches = mainState.matches
    val runsIndexReloadTrigger = mainState.runsIndexReloadTrigger
    val diagnosticsResponse = mainState.diagnosticsResponse
    val isTerminalOpen = mainState.isTerminalOpen
    val showUpdateBanner = mainState.showUpdateBanner

    val updateState by services.updateCheckerService.updateState.collectAsState()

    // Trigger update check on startup
    LaunchedEffect(Unit) {
        services.updateCheckerService.checkForUpdates()
    }

    val autoImportService = remember {
        AutoImportService(
            databaseService = services.databaseService,
            logParserService = services.logParserService,
            hootDecoderService = services.hootDecoderService,
            processManagerService = services.processManagerService,
            configProvider = { config }
        )
    }

    LaunchedEffect(config) {
        if (config != null) {
            autoImportService.start {
                mainViewModel.onIntent(MainIntent.TriggerRunsIndexReload)
            }
        } else {
            autoImportService.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            autoImportService.stop()
        }
    }

    val currentConfig = config

    if (currentConfig == null) {
        val onboardingViewModel = remember {
            OnboardingViewModel(services.environmentService, scope) { loaded ->
                mainViewModel.onIntent(MainIntent.SaveConfig(loaded))
            }
        }
        val showCancel = mainState.workspaces.isNotEmpty()
        OnboardingScreen(
            viewModel = onboardingViewModel,
            teamApiService = services.teamApiService,
            oauthService = services.oauthService,
            onCancel = if (showCancel) { { mainViewModel.onIntent(MainIntent.CancelAddNewWorkspace) } } else null
        )
        return
    }

    // Instantiate ViewModels
    val dashboardViewModel = remember {
        DashboardViewModel(
            databaseService = services.databaseService,
            nt4ClientService = services.nt4ClientService,
            alertEngineService = services.alertEngineService,
            syncEngineService = services.syncEngineService,
            hootDecoderService = services.hootDecoderService,
            logParserService = services.logParserService,
            layoutPreferenceService = services.layoutPreferenceService,
            scope = scope
        )
    }

    val pathPlannerViewModel = remember {
        PathPlannerViewModel(scope = scope)
    }

    val fieldEditorViewModel = remember {
        FieldEditorViewModel(scope = scope)
    }

    val replayViewModel = remember {
        ReplayViewModel(
            databaseService = services.databaseService,
            replayEngineService = services.replayEngineService,
            scope = scope
        )
    }

    val sysIdViewModel = remember {
        SysIdViewModel(
            databaseService = services.databaseService,
            sysIdService = services.sysIdService,
            driverAnalysisService = services.driverAnalysisService,
            constantsParserService = services.constantsParserService,
            scope = scope
        )
    }

    val triageViewModel = remember {
        TriageViewModel(
            databaseService = services.databaseService,
            scope = scope
        )
    }

    val tuningViewModel = remember {
        TuningViewModel(
            constantsParserService = services.constantsParserService,
            scope = scope
        )
    }

    val profileViewModel = remember {
        ProfileViewModel(
            oauthService = services.oauthService,
            syncEngineService = services.syncEngineService,
            environmentService = services.environmentService,
            scope = scope
        )
    }

    val dashboardState by dashboardViewModel.state.collectAsState()
    val primarySessionId = dashboardState.primarySessionId
    val compareSessionId = dashboardState.compareSessionId

    // Start NT4 connection once config is resolved
    LaunchedEffect(currentConfig) {
        focusRequester.requestFocus()
        val host = currentConfig.nt4Host ?: "192.168.43.1"
        services.nt4ClientService.start(
            host = host,
            teamId = currentConfig.teamId,
            seasonId = currentConfig.seasonId,
            robotId = currentConfig.robotId
        )
        services.phoenixDiagnosticsService.start(host = host)
        services.ftcDashboardService.start(host = host)
    }

    val isConnected by services.nt4ClientService.isConnected.collectAsState()
    val adbConnected by services.processManagerService.adbConnected.collectAsState()
    val isSimRunning by services.processManagerService.isSimRunning.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isCtrl = keyEvent.isCtrlPressed
                    when (keyEvent.key) {
                        Key.B -> if (isCtrl) {
                            services.processManagerService.runBuild(currentConfig.projectPath, currentConfig.league)
                            mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                            true
                        } else false
                        Key.D -> if (isCtrl) {
                            services.processManagerService.runSimulation(currentConfig.projectPath, currentConfig.league)
                            mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                            true
                        } else false
                        Key.K -> if (isCtrl) {
                            services.processManagerService.killActiveBuild()
                            services.processManagerService.killActiveSim()
                            true
                        } else false
                        Key.Escape -> {
                            if (isTerminalOpen) {
                                mainViewModel.onIntent(MainIntent.SetTerminalOpen(false))
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(AresBackground)
        ) {
            // ── Sidebar ──────────────────────────────────────────────────────
            Sidebar(
                activeTarget = activeNav,
                isConnected = isConnected,
                adbConnected = adbConnected,
                isSimRunning = isSimRunning,
                league = currentConfig.league,
                onNavigate = { mainViewModel.onIntent(MainIntent.SetActiveNav(it)) },
                onToggleTerminal = { mainViewModel.onIntent(MainIntent.SetTerminalOpen(!isTerminalOpen)) }
            )

            // ── Content Area ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top header bar with run config info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dropdown Selector for active Workspace/Robot configuration
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
                                val badgeBg = if (currentConfig.league == League.FTC) AresGold else AresCyan
                                Text(
                                    text = currentConfig.league.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AresBackground,
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )

                                Text(
                                    text = "${currentConfig.robotId} (Team ${currentConfig.teamId})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AresTextPrimary
                                )

                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = AresTextSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
                            ) {
                                mainState.workspaces.forEach { workspace ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.width(220.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${workspace.robotId} (Team ${workspace.teamId})",
                                                        fontWeight = if (workspace.id == currentConfig.id) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (workspace.id == currentConfig.id) AresCyan else AresTextPrimary
                                                    )
                                                    Text(
                                                        text = "${workspace.league.name} • Season ${workspace.seasonId}",
                                                        fontSize = 11.sp,
                                                        color = AresTextSecondary
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        mainViewModel.onIntent(MainIntent.DeleteWorkspace(workspace.id))
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Profile",
                                                        tint = AresError.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            mainViewModel.onIntent(MainIntent.SelectWorkspace(workspace.id))
                                            dropdownExpanded = false
                                        }
                                    )
                                }

                                HorizontalDivider(color = AresBorder, modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = AresCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text("Add Robot Profile...", color = AresCyan, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = {
                                        mainViewModel.onIntent(MainIntent.AddNewWorkspace)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }

                        // Active Simulation & Recording Controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sim Controls
                            if (!isSimRunning) {
                                Button(
                                    onClick = {
                                        services.processManagerService.runSimulation(currentConfig.projectPath, currentConfig.league)
                                        mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AresGreen),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = AresBackground)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Launch Sim", color = AresBackground, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { services.processManagerService.killActiveSim() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AresAmber),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = AresBackground)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop Sim", color = AresBackground, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Recording Controls
                            val activeSession by services.nt4ClientService.currentSession.collectAsState()
                            if (activeSession == null) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            services.nt4ClientService.startRecordingSession(
                                                teamId = currentConfig.teamId,
                                                seasonId = currentConfig.seasonId,
                                                robotId = currentConfig.robotId
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = null, tint = AresBackground)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Start Record", color = AresBackground, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { scope.launch { services.nt4ClientService.stopRecordingSession() } },
                                    colors = ButtonDefaults.buttonColors(containerColor = AresRed),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = AresTextPrimary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop Record", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // ── Screen Router ────────────────────────────────────────
                    Box(modifier = Modifier.weight(1f)) {
                        when (activeNav) {
                            NavigationTarget.DASHBOARD -> DashboardScreen(
                                viewModel = dashboardViewModel,
                                services = services,
                                currentConfig = currentConfig,
                                matches = matches,
                                onForensicsCompleted = { mainViewModel.onIntent(MainIntent.SetDiagnosticsResponse(it)) },
                                onSelectMatch = { match, allianceColor ->
                                    if (primarySessionId != null) {
                                        scope.launch {
                                            val opponents = if (allianceColor == "red") match.blueAlliance else match.redAlliance
                                            services.databaseService.associateSessionWithMatch(
                                                sessionId = primarySessionId,
                                                matchNumber = match.matchNumber,
                                                allianceColor = allianceColor,
                                                opponentTeams = opponents
                                            )
                                            mainViewModel.onIntent(MainIntent.TriggerRunsIndexReload)
                                        }
                                    }
                                },
                                reloadTrigger = runsIndexReloadTrigger,
                                onImportSuccess = { mainViewModel.onIntent(MainIntent.TriggerRunsIndexReload) }
                            )
                            NavigationTarget.PATH_PLANNER -> PathPlannerScreen(
                                viewModel = pathPlannerViewModel,
                                league = currentConfig.league,
                                projectPath = currentConfig.projectPath
                            )
                            NavigationTarget.FIELD_EDITOR -> FieldEditorScreen(
                                viewModel = fieldEditorViewModel,
                                league = currentConfig.league,
                                projectPath = currentConfig.projectPath
                            )
                            NavigationTarget.REPLAY -> ReplayScreen(
                                viewModel = replayViewModel,
                                services = services,
                                sessionId = primarySessionId,
                                league = currentConfig.league,
                                projectPath = currentConfig.projectPath
                            )
                            NavigationTarget.SYSID -> SysIdScreen(
                                viewModel = sysIdViewModel,
                                projectPath = currentConfig.projectPath ?: "",
                                sessionId = primarySessionId
                            )
                            NavigationTarget.TRIAGE -> TriageScreen(
                                viewModel = triageViewModel,
                                league = currentConfig.league,
                                diagnosticsResponse = diagnosticsResponse,
                                robotId = currentConfig.robotId
                            )
                            NavigationTarget.TUNING -> TuningScreen(
                                viewModel = tuningViewModel,
                                projectPath = currentConfig.projectPath ?: ""
                            )
                            NavigationTarget.PROFILE -> ProfileScreen(
                                viewModel = profileViewModel,
                                config = currentConfig,
                                onConfigChanged = { newConfig ->
                                    mainViewModel.onIntent(MainIntent.SaveConfig(newConfig))
                                }
                            )
                            NavigationTarget.ADMIN -> AdminScreen(
                                teamApiService = services.teamApiService,
                                oauthService = services.oauthService,
                                config = currentConfig
                            )
                        }
                    }
                }

                // Collapsible Terminal drawer overlay
                TerminalDrawer(
                    processManagerService = services.processManagerService,
                    projectPath = currentConfig.projectPath,
                    league = currentConfig.league,
                    isOpen = isTerminalOpen,
                    onClose = { mainViewModel.onIntent(MainIntent.SetTerminalOpen(false)) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // ── Update Notification Banner ──────────────────────────────────────────
        val currentUpdateState = updateState
        if (currentUpdateState is UpdateCheckerService.UpdateState.UpdateAvailable && showUpdateBanner) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Card(
                    modifier = Modifier
                        .width(360.dp)
                        .animateContentSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AresSurface),
                    border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AresCyan
                            )
                            Text(
                                text = "Software Update Available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AresTextPrimary
                            )
                        }

                        Text(
                            text = "A new version (${currentUpdateState.latestVersion}) of ARES Analytics is available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AresTextSecondary
                        )

                        if (!currentUpdateState.releaseNotes.isNullOrEmpty()) {
                            Text(
                                text = currentUpdateState.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = AresTextSecondary.copy(alpha = 0.7f),
                                maxLines = 3,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { mainViewModel.onIntent(MainIntent.SetShowUpdateBanner(false)) }
                            ) {
                                Text("Dismiss", color = AresTextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        if (java.awt.Desktop.isDesktopSupported()) {
                                            java.awt.Desktop.getDesktop().browse(java.net.URI(currentUpdateState.downloadUrl))
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                            ) {
                                Text("Update", color = AresBackground)
                            }
                        }
                    }
                }
            }
        }
    }
}
