package com.ares.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import com.ares.analytics.ui.components.CommandPalette
import com.ares.analytics.ui.components.NavigationTarget
import com.ares.analytics.ui.components.SectionNavigationBar
import com.ares.analytics.ui.components.Sidebar
import com.ares.analytics.ui.components.core.TargetSelection
import com.ares.analytics.ui.components.core.ExecutionToolbar
import com.ares.analytics.ui.components.terminal.TerminalDrawer
import com.ares.analytics.ui.help.LearningCatalog
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.*
import com.ares.analytics.viewmodel.drivebase.DrivebaseBuilderViewModel
import kotlinx.coroutines.*

/**
 * Root UI frame container and screen routing shell for the ARES Analytics desktop application.
 *
 * Manages navigation sidebar targets ([NavigationTarget]), execution toolbars (Gradle build/deploy, ADB logcat, Sim launcher),
 * terminal output drawers, and global keyboard shortcuts (`Ctrl+Shift+B`, `Ctrl+Shift+R`).
 *
 * @param services Primary dependency container [ServiceRegistry].
 * @param currentConfig Active workspace configuration state.
 * @param onUpdateConfig Callback for saving modified workspace settings.
 *
 * @see NavigationTarget
 * @see com.ares.analytics.service.ProcessManagerService
 */
@Composable
fun MainScreen(services: ServiceRegistry) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val mainViewModel = remember {
        MainViewModel(
            environmentService = services.environmentService,
            eventApiService = services.eventApiService,
            keybindingParserService = services.keybindingParserService,
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
    val gamepad1State by services.gamepadService.gamepad1State.collectAsState()
    val gamepad2State by services.gamepadService.gamepad2State.collectAsState()
    var commandPaletteOpen by remember { mutableStateOf(false) }
    var workspacePendingDeletion by remember { mutableStateOf<Pair<String, String>?>(null) }
    var requestedLessonId by remember { mutableStateOf<String?>(null) }

    // Trigger update check on startup
    LaunchedEffect(Unit) {
        services.updateCheckerService.checkForUpdates()
    }
    val autoImportService = services.autoImportService

    LaunchedEffect(config) {
        services.transitionAutoImport(config) {
            if (config != null) {
                mainViewModel.onIntent(MainIntent.TriggerRunsIndexReload)
            }
        }
    }
    val currentConfig = config

    LaunchedEffect(
        currentConfig?.colorblindMode,
        currentConfig?.highContrastMode,
        currentConfig?.touchOptimizedMode,
        currentConfig?.largeTextMode
    ) {
        if (currentConfig != null) {
            AresThemeSettings.colorblindMode = currentConfig.colorblindMode
            AresThemeSettings.highContrastMode = currentConfig.highContrastMode
            AresThemeSettings.touchOptimizedMode = currentConfig.touchOptimizedMode
            AresThemeSettings.largeTextMode = currentConfig.largeTextMode
        }
    }

    // Global 50Hz Drive Input Loop (Keyboard & Gamepad)
    val isNt4Connected by services.nt4ClientService.isConnected.collectAsState()
    LaunchedEffect(isNt4Connected, activeNav) {
        if (isNt4Connected) {
            val driveFrame = DoubleArray(8)
            val driveSessionNonce = services.nt4ClientService.nextDriveSessionNonce()
            var driveSequence = 0L
            var sentNeutralHandshake = false

            while (true) {
                val ks = services.keyboardDriveState
                val g1 = services.gamepadService.gamepad1State.value
                val controlSurfaceActive = activeNav == NavigationTarget.DASHBOARD && ks.enabled
                val deadmanActive = if (ks.useGamepad) {
                    g1.connected && g1.leftTrigger > 0.5f
                } else {
                    ks.deadmanPressed
                }
                val localInputActive = controlSurfaceActive && deadmanActive

                val (vx, vy, omega) = if (localInputActive && ks.useGamepad && g1.connected) {
                    val rawY = com.areslib.math.InputMath.applyDeadband(g1.leftStickY.toDouble(), 0.02)
                    val rawX = com.areslib.math.InputMath.applyDeadband(g1.leftStickX.toDouble(), 0.02)
                    val rawRot = com.areslib.math.InputMath.applyDeadband(g1.rightStickX.toDouble(), 0.02)
                    val activeVx = com.areslib.math.InputMath.applyCurve(rawY, 1.2) * 4.0
                    val activeVy = com.areslib.math.InputMath.applyCurve(rawX, 1.2) * -4.0
                    val activeOmega = com.areslib.math.InputMath.applyCurve(rawRot, 1.2) * -4.0
                    Triple(activeVx, activeVy, activeOmega)
                } else if (localInputActive) {
                    val activeVx = when {
                        ks.isWPressed || ks.isUpPressed -> 4.0
                        ks.isSPressed || ks.isDownPressed -> -4.0
                        else -> 0.0
                    }
                    val activeVy = when {
                        ks.isAPressed -> 4.0
                        ks.isDPressed -> -4.0
                        else -> 0.0
                    }
                    val activeOmega = when {
                        ks.isLeftPressed -> 4.0
                        ks.isRightPressed -> -4.0
                        else -> 0.0
                    }
                    Triple(activeVx, activeVy, activeOmega)
                } else {
                    Triple(0.0, 0.0, 0.0)
                }

                val qPressed = localInputActive && if (ks.useGamepad && g1.connected) g1.leftBumper else ks.isQPressed
                val ePressed = localInputActive && if (ks.useGamepad && g1.connected) g1.rightBumper else ks.isEPressed
                val shiftPressed = localInputActive && if (ks.useGamepad && g1.connected) g1.rightTrigger > 0.5f else ks.isShiftPressed
                val jPressed = localInputActive && if (ks.useGamepad && g1.connected) g1.a else ks.isJPressed
                val lPressed = localInputActive && if (ks.useGamepad && g1.connected) g1.b else ks.isLPressed
                val uPressed = localInputActive && if (ks.useGamepad && g1.connected) g1.x else ks.isUPressed

                // Complete v2 command contract. Every new connection starts with a neutral
                // actuation frame; consumers may arm only on a later sequence in this session.
                var flags = 0L
                if (sentNeutralHandshake && qPressed) flags = flags or (1L shl 0)
                if (sentNeutralHandshake && ePressed) flags = flags or (1L shl 1)
                if (sentNeutralHandshake && shiftPressed) flags = flags or (1L shl 2)
                flags = flags or (1L shl 3) // teleop mode
                // Bit 4 (field-centric) remains clear until the UI exposes an explicit setting.
                if (services.nt4ClientService.selectedRedAlliance.value) flags = flags or (1L shl 5)
                if (sentNeutralHandshake && jPressed) flags = flags or (1L shl 6)
                if (sentNeutralHandshake && lPressed) flags = flags or (1L shl 7)
                if (sentNeutralHandshake && uPressed) flags = flags or (1L shl 8)
                // Bit 9 (pose reset) is edge-triggered and currently has no global shortcut.

                driveFrame[0] = 2.0
                driveFrame[1] = driveSessionNonce
                driveFrame[2] = driveSequence.toDouble()
                driveFrame[3] = (System.nanoTime() / 1_000_000L).toDouble()
                driveFrame[4] = if (sentNeutralHandshake) vx else 0.0
                driveFrame[5] = if (sentNeutralHandshake) vy else 0.0
                driveFrame[6] = if (sentNeutralHandshake) omega else 0.0
                driveFrame[7] = flags.toDouble()
                val driveFrameTransmitted = services.nt4ClientService.publishDriveFrame(driveFrame)
                if (!driveFrameTransmitted) {
                    // isConnected becomes true before the NT4 clock offset is established. Keep
                    // retrying the neutral sequence and do not emit legacy motion in that window.
                    delay(20)
                    continue
                }
                sentNeutralHandshake = true
                driveSequence++

                delay(20)
            }
        }
    }

    if (currentConfig == null) {
        val onboardingViewModel = remember {
            OnboardingViewModel(services.environmentService, services.syncEngineService, scope) { loaded ->
                mainViewModel.onIntent(MainIntent.SaveConfig(loaded))
            }
        }
        val showCancel = mainState.workspaces.isNotEmpty()
        OnboardingScreen(
            viewModel = onboardingViewModel,
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
        PathPlannerViewModel(
            scope = scope,
            nt4ClientService = services.nt4ClientService,
            projectGenerator = services.processManagerService
        )
    }
    val fieldEditorViewModel = remember {
        FieldEditorViewModel(scope = scope, nt4ClientService = services.nt4ClientService)
    }
    val sysIdViewModel = remember {
        SysIdViewModel(
            databaseService = services.databaseService,
            sysIdService = services.sysIdService,
            driverAnalysisService = services.driverAnalysisService,
            autoTunerService = services.autoTunerService,
            nt4ClientService = services.nt4ClientService,
            scope = scope,
            tuningProposalInbox = services.tuningProposalInbox
        )
    }
    val tuningViewModel = remember {
        TuningViewModel(
            nt4ClientService = services.nt4ClientService,
            scope = scope,
            repository = services.tuningProfileRepository,
            proposalInbox = services.tuningProposalInbox
        )
    }
    LaunchedEffect(currentConfig.league) {
        sysIdViewModel.onIntent(SysIdIntent.ConfigurePlatform(currentConfig.league == League.FTC))
    }
    LaunchedEffect(activeNav) {
        if (activeNav != NavigationTarget.TUNING) {
            sysIdViewModel.onIntent(SysIdIntent.DisarmCalibration("Left the Tuning screen"))
        }
    }
    val profileViewModel = remember {
        ProfileViewModel(
            oauthService = services.oauthService,
            syncEngineService = services.syncEngineService,
            scope = scope
        )
    }
    val cloudViewModel = remember {
        com.ares.analytics.viewmodel.CloudViewModel(
            databaseService = services.databaseService,
            syncEngineService = services.syncEngineService,
            oauthService = services.oauthService,
            nt4ClientService = services.nt4ClientService,
            logParserService = services.logParserService,
            scope = scope
        )
    }
    val importCenterViewModel = remember(currentConfig.projectPath) {
        ImportCenterViewModel(
            archiveService = com.ares.analytics.service.ImportArchiveService(),
            projectPath = currentConfig.projectPath ?: "",
            scope = scope
        )
    }
    val controlsEditorViewModel = remember(currentConfig.projectPath, currentConfig.league) {
        com.ares.analytics.viewmodel.controls.ControlsEditorViewModel(
            projectPath = currentConfig.projectPath,
            league = currentConfig.league,
            projectGenerator = services.processManagerService,
            designAssistant = com.ares.analytics.service.ControlsDesignAssistant { current, context, request ->
                services.syncEngineService.requestControlsDesignProposal(current, context, request)
            },
        )
    }
    val controlsEditorState by controlsEditorViewModel.state.collectAsState()
    DisposableEffect(controlsEditorViewModel) {
        onDispose { controlsEditorViewModel.close() }
    }
    LaunchedEffect(autoImportService, importCenterViewModel) {
        autoImportService.importNotifications.collect {
            importCenterViewModel.onIntent(ImportCenterIntent.Refresh)
        }
    }
    DisposableEffect(cloudViewModel) {
        onDispose {
            // CloudViewModel owns its own HttpClient; close it on screen exit to avoid
            // leaking the CIO engine + connection pool across navigations.
            cloudViewModel.dispose()
        }
    }
    val subsystemGeneratorViewModel = remember(currentConfig.projectPath, currentConfig.league) {
        SubsystemGeneratorViewModel(
            projectPath = currentConfig.projectPath ?: "",
            league = currentConfig.league,
            projectGenerator = services.processManagerService,
            designAssistant = com.ares.analytics.service.SubsystemDesignAssistant { current, request ->
                services.syncEngineService.requestSubsystemDesignProposal(current, request)
            },
        )
    }
    DisposableEffect(subsystemGeneratorViewModel) {
        onDispose { subsystemGeneratorViewModel.close() }
    }
    val drivebaseBuilderViewModel = remember(currentConfig.projectPath, currentConfig.robotId) {
        DrivebaseBuilderViewModel(
            projectPath = currentConfig.projectPath ?: "",
            projectId = currentConfig.robotId,
            scope = scope,
            repository = services.drivebaseProjectRepository,
            designAssistant = com.ares.analytics.service.DrivebaseDesignAssistant { current, request ->
                services.syncEngineService.requestDrivebaseDesignProposal(current, request)
            },
        )
    }
    // This ViewModel owns no independent scope or hardware/service resource. Its jobs run in the
    // screen's Compose scope and are cancelled automatically when MainScreen leaves composition.
    val dashboardState by dashboardViewModel.state.collectAsState()
    val primarySessionId = dashboardState.primarySessionId
    val compareSessionId = dashboardState.compareSessionId
    val isConnected by services.nt4ClientService.isConnected.collectAsState()
    val adbConnected by services.processManagerService.adbConnected.collectAsState()
    val isSimRunning by services.processManagerService.isSimRunning.collectAsState()
    val isBuildRunning by services.processManagerService.isBuildRunning.collectAsState()
    var targetSelection by remember { mutableStateOf(TargetSelection.LIVE_ROBOT) }
    var liveRobotIp by remember(currentConfig.nt4Host) {
        mutableStateOf(currentConfig.nt4Host ?: "192.168.43.1")
    }
    LaunchedEffect(activeNav, targetSelection, isNt4Connected) {
        if (activeNav != NavigationTarget.DASHBOARD || !isNt4Connected) {
            services.keyboardDriveState.disarm()
        } else {
            services.keyboardDriveState.releaseAll()
        }
    }
    val isLiveRobotOnline by services.targetScannerService.isLiveRobotOnline.collectAsState()
    val isLocalSimOnline by services.targetScannerService.isLocalSimOnline.collectAsState()

    LaunchedEffect(liveRobotIp) {
        services.targetScannerService.startScanning(liveRobotIp)
    }

    // Auto-switch based on Most Recently Booted / Online status
    LaunchedEffect(isLocalSimOnline, isSimRunning, isLiveRobotOnline) {
        if (isLocalSimOnline || isSimRunning) {
            targetSelection = TargetSelection.LOCAL_SIM
        } else if (isLiveRobotOnline) {
            targetSelection = TargetSelection.LIVE_ROBOT
        }
    }

    // Start NT4 connection once config is resolved or target/simulator status changes
    LaunchedEffect(currentConfig, targetSelection, liveRobotIp, isSimRunning) {
        println("[MainScreen LaunchedEffect] RUNNING: config=$currentConfig (hash=${System.identityHashCode(currentConfig)}), targetSelection=$targetSelection, liveRobotIp=$liveRobotIp, isSimRunning=$isSimRunning")
        focusRequester.requestFocus()
        val host = if (targetSelection == TargetSelection.LOCAL_SIM) {
            "127.0.0.1"
        } else {
            liveRobotIp
        }
        println("[MainScreen LaunchedEffect] Computed host=$host")
        services.nt4ClientService.start(
            host = host,
            teamId = currentConfig.teamId,
            seasonId = currentConfig.seasonId,
            robotId = currentConfig.robotId
        )
        services.phoenixDiagnosticsService.start(host = host)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val ks = services.keyboardDriveState
                val isCtrl = keyEvent.isCtrlPressed
                if (keyEvent.type == KeyEventType.KeyDown && isCtrl) {
                    when (keyEvent.key) {
                        Key.B -> {
                            services.processManagerService.runBuild(currentConfig.projectPath, currentConfig.league)
                            mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                            true
                        }
                        Key.D -> {
                            services.processManagerService.runSimulation(currentConfig.projectPath, currentConfig.league, currentConfig.simulatorCommand)
                            mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                            true
                        }
                        Key.K -> {
                            if (keyEvent.isShiftPressed) {
                                services.processManagerService.killActiveBuild()
                                services.processManagerService.killActiveSim()
                            } else {
                                commandPaletteOpen = true
                            }
                            true
                        }
                        else -> false
                    }
                } else if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        commandPaletteOpen -> { commandPaletteOpen = false; true }
                        isTerminalOpen -> { mainViewModel.onIntent(MainIntent.SetTerminalOpen(false)); true }
                        else -> false
                    }
                } else if (ks.enabled && activeNav == NavigationTarget.DASHBOARD) {
                    val isPressed = keyEvent.type == KeyEventType.KeyDown
                    if (keyEvent.key == Key.Spacebar) {
                        ks.deadmanPressed = isPressed
                        if (!isPressed) ks.releaseAll()
                        true
                    } else if (!ks.deadmanPressed) {
                        false
                    } else when (keyEvent.key) {
                        Key.W -> { ks.isWPressed = isPressed; true }
                        Key.S -> { ks.isSPressed = isPressed; true }
                        Key.A -> { ks.isAPressed = isPressed; true }
                        Key.D -> { ks.isDPressed = isPressed; true }
                        Key.DirectionUp -> { ks.isWPressed = isPressed; true }
                        Key.DirectionDown -> { ks.isSPressed = isPressed; true }
                        Key.DirectionLeft -> { ks.isLeftPressed = isPressed; true }
                        Key.DirectionRight -> { ks.isRightPressed = isPressed; true }
                        Key.Q -> { ks.isQPressed = isPressed; true }
                        Key.E -> { ks.isEPressed = isPressed; true }
                        Key.J -> { ks.isJPressed = isPressed; true }
                        Key.L -> { ks.isLPressed = isPressed; true }
                        Key.U -> { ks.isUPressed = isPressed; true }
                        Key.I -> { ks.isIPressed = isPressed; true }
                        Key.ShiftLeft, Key.ShiftRight -> { ks.isShiftPressed = isPressed; true }
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
                onNavigate = {
                    if (it == NavigationTarget.ACADEMY) requestedLessonId = null
                    mainViewModel.onIntent(MainIntent.SetActiveNav(it))
                },
                onOpenCommandPalette = { commandPaletteOpen = true },
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                                        val displayName = workspace.robotName.ifBlank {
                                                            "${workspace.robotId} (Team ${workspace.teamId})"
                                                        }
                                                        workspacePendingDeletion = workspace.id to displayName
                                                        dropdownExpanded = false
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remove workspace",
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

                        SectionNavigationBar(
                            activeTarget = activeNav,
                            onNavigate = { mainViewModel.onIntent(MainIntent.SetActiveNav(it)) },
                            modifier = Modifier.weight(1f)
                        )

                        ExecutionToolbar(
                            targetSelection = targetSelection,
                            targetIp = if (targetSelection == TargetSelection.LOCAL_SIM || isSimRunning) "127.0.0.1" else liveRobotIp,
                            isLiveRobotOnline = isLiveRobotOnline,
                            isLocalSimOnline = isLocalSimOnline,
                            isBuildRunning = isBuildRunning,
                            isSimRunning = isSimRunning,
                            onTargetChanged = { targetSelection = it },
                            onTargetIpChanged = { ip ->
                                if (targetSelection == TargetSelection.LIVE_ROBOT) {
                                    liveRobotIp = ip
                                }
                            },
                            onRunBuild = {
                                services.processManagerService.runBuild(currentConfig.projectPath, currentConfig.league)
                                mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                            },
                            onRunSim = {
                                services.processManagerService.runSimulation(currentConfig.projectPath, currentConfig.league, currentConfig.simulatorCommand)
                                mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                            },
                            onStopAll = {
                                services.processManagerService.killActiveBuild()
                                services.processManagerService.killActiveSim()
                            }
                        )

                        if (activeNav != NavigationTarget.ACADEMY) LearningCatalog.lessonFor(activeNav)?.let { lesson ->
                            OutlinedButton(
                                onClick = {
                                    requestedLessonId = lesson.id
                                    mainViewModel.onIntent(MainIntent.SetActiveNav(NavigationTarget.ACADEMY))
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Help", fontSize = 12.sp)
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
                                onImportSuccess = { mainViewModel.onIntent(MainIntent.TriggerRunsIndexReload) },
                                onOpenKeybindings = { mainViewModel.onIntent(MainIntent.SetActiveNav(NavigationTarget.CONTROLS)) },
                                onOpenRunHistory = { mainViewModel.onIntent(MainIntent.SetActiveNav(NavigationTarget.RUN_HISTORY)) },
                                onOpenHelp = { mainViewModel.onIntent(MainIntent.SetActiveNav(NavigationTarget.ACADEMY)) }
                            )
                            NavigationTarget.PATH_PLANNER -> PathPlannerScreen(
                                viewModel = pathPlannerViewModel,
                                league = currentConfig.league,
                                projectPath = currentConfig.projectPath,
                                robotDimensions = com.ares.analytics.viewmodel.pathing.RobotDimensions(
                                    lengthMeters = currentConfig.robotLengthMeters
                                        ?: com.ares.analytics.viewmodel.pathing.RobotDimensions
                                            .defaultFor(currentConfig.league).lengthMeters,
                                    widthMeters = currentConfig.robotWidthMeters
                                        ?: com.ares.analytics.viewmodel.pathing.RobotDimensions
                                            .defaultFor(currentConfig.league).widthMeters
                                ),
                                onProjectPathChanged = { selectedPath ->
                                    mainViewModel.onIntent(
                                        MainIntent.SaveConfig(currentConfig.copy(projectPath = selectedPath))
                                    )
                                },
                                onRobotDimensionsChanged = { dimensions ->
                                    mainViewModel.onIntent(
                                        MainIntent.SaveConfig(
                                            currentConfig.copy(
                                                robotLengthMeters = dimensions.lengthMeters,
                                                robotWidthMeters = dimensions.widthMeters
                                            )
                                        )
                                    )
                                }
                            )
                            NavigationTarget.CLOUD -> CloudScreen(
                                viewModel = cloudViewModel,
                                teamId = currentConfig.teamId,
                                seasonId = currentConfig.seasonId,
                                robotId = currentConfig.robotId
                            )
                            NavigationTarget.IMPORT_CENTER -> ImportCenterScreen(
                                viewModel = importCenterViewModel,
                                projectPath = currentConfig.projectPath.orEmpty(),
                                onOpenHelp = {
                                    requestedLessonId = "bring-in-run"
                                    mainViewModel.onIntent(MainIntent.SetActiveNav(NavigationTarget.ACADEMY))
                                }
                            )
                            NavigationTarget.FIELD_EDITOR -> FieldEditorScreen(
                                viewModel = fieldEditorViewModel,
                                league = currentConfig.league,
                                projectPath = currentConfig.projectPath
                            )
                            NavigationTarget.ACADEMY -> AcademyScreen(
                                progressService = services.learningProgressService,
                                onOpenScreen = { destination ->
                                    mainViewModel.onIntent(MainIntent.SetActiveNav(destination))
                                },
                                onStartSimulator = {
                                    services.processManagerService.runSimulation(
                                        currentConfig.projectPath,
                                        currentConfig.league,
                                        currentConfig.simulatorCommand
                                    )
                                    mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                                },
                                initialLessonId = requestedLessonId
                            )
                            NavigationTarget.KDOC_VIEWER -> KDocViewerScreen()
                            NavigationTarget.PIT_DIAGNOSTICS -> HardwareSelfTestWizard(nt4ClientService = services.nt4ClientService)
                            NavigationTarget.MATCH_STRATEGY -> MatchStrategyScreen()
                            NavigationTarget.RUN_HISTORY -> RunHistoryScreen(
                                databaseService = services.databaseService,
                                syncEngineService = services.syncEngineService,
                                onOpenImports = {
                                    mainViewModel.onIntent(MainIntent.SetActiveNav(NavigationTarget.IMPORT_CENTER))
                                },
                                onOpenHelp = {
                                    mainViewModel.onIntent(MainIntent.SetActiveNav(NavigationTarget.ACADEMY))
                                }
                            )
                            NavigationTarget.DATABASE_VIEWER -> DatabaseViewerScreen(
                                databaseService = services.databaseService
                            )

                            NavigationTarget.TUNING -> TuningScreen(
                                viewModel = tuningViewModel,
                                sysIdViewModel = sysIdViewModel,
                                projectPath = currentConfig.projectPath ?: ""
                            )
                            NavigationTarget.CONTROLS -> com.ares.analytics.ui.components.controls.ControlsEditorPanel(
                                state = controlsEditorState,
                                viewModel = controlsEditorViewModel,
                                gamepad1State = gamepad1State,
                                gamepad2State = gamepad2State,
                                modifier = Modifier.fillMaxSize()
                            )
                            NavigationTarget.SUBSYSTEM_GEN -> SubsystemGeneratorScreen(subsystemGeneratorViewModel)
                            NavigationTarget.DRIVEBASE_BUILDER -> DrivebaseBuilderScreen(drivebaseBuilderViewModel)
                            NavigationTarget.PROFILE -> ProfileScreen(
                                viewModel = profileViewModel,
                                config = currentConfig,
                                onConfigChanged = { newConfig ->
                                    mainViewModel.onIntent(MainIntent.SaveConfig(newConfig))
                                }
                            )
                            NavigationTarget.ADMIN -> AdminScreen(
                                syncEngineService = services.syncEngineService,
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

                // Critical Emergency Fault Alert Overlay (Pop-up Banner for Motor Stalls, Brownouts, Disconnects)
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    com.ares.analytics.ui.components.dashboard.CriticalAlertOverlay(
                        alertEngineService = services.alertEngineService
                    )
                }
            }
        }

        if (commandPaletteOpen) {
            CommandPalette(
                developerMode = currentConfig.developerMode,
                onDismiss = { commandPaletteOpen = false },
                onNavigate = {
                    if (it == NavigationTarget.ACADEMY) requestedLessonId = null
                    mainViewModel.onIntent(MainIntent.SetActiveNav(it))
                }
            )
        }

        workspacePendingDeletion?.let { (workspaceId, displayName) ->
            AlertDialog(
                onDismissRequest = { workspacePendingDeletion = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = AresError
                    )
                },
                title = { Text("Remove this workspace?") },
                text = {
                    Text(
                        "ARES will remove the saved workspace settings for $displayName. " +
                            "Your robot project files and imported run data will not be deleted."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            mainViewModel.onIntent(MainIntent.DeleteWorkspace(workspaceId))
                            workspacePendingDeletion = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AresError, contentColor = AresOnAccent)
                    ) {
                        Text("Remove workspace")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { workspacePendingDeletion = null }) {
                        Text("Keep workspace")
                    }
                }
            )
        }

        // ── Update Notification Banner ──────────────────────────────────────────
        val currentUpdateState = updateState
        if (currentUpdateState is UpdateCheckerService.UpdateState.UpdateAvailable && showUpdateBanner) {
            com.ares.analytics.ui.components.layout.UpdateNotificationBanner(
                updateState = currentUpdateState,
                onDismiss = { mainViewModel.onIntent(MainIntent.SetShowUpdateBanner(false)) }
            )
        }
    }
}
