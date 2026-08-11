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
import com.ares.analytics.ui.components.CommandPalette
import com.ares.analytics.ui.components.NavigationTarget
import com.ares.analytics.ui.components.SectionNavigationBar
import com.ares.analytics.ui.components.Sidebar
import com.ares.analytics.ui.components.core.TargetSelection
import com.ares.analytics.ui.components.core.ExecutionToolbar
import com.ares.analytics.ui.components.terminal.TerminalDrawer
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.*
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
    val isKeybindingsOpen = mainState.isKeybindingsOpen
    val showUpdateBanner = mainState.showUpdateBanner
    val updateState by services.updateCheckerService.updateState.collectAsState()
    val gamepad1State by services.gamepadService.gamepad1State.collectAsState()
    val gamepad2State by services.gamepadService.gamepad2State.collectAsState()
    var commandPaletteOpen by remember { mutableStateOf(false) }

    // Trigger update check on startup
    LaunchedEffect(Unit) {
        services.updateCheckerService.checkForUpdates()
    }
    val currentConfigProvider = rememberUpdatedState(config)
    val autoImportService = remember {
        AutoImportService(
            logParserService = services.logParserService,
            hootDecoderService = services.hootDecoderService,
            processManagerService = services.processManagerService,
            configProvider = { currentConfigProvider.value }
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

    LaunchedEffect(currentConfig?.colorblindMode, currentConfig?.highContrastMode, currentConfig?.touchOptimizedMode) {
        if (currentConfig != null) {
            AresThemeSettings.colorblindMode = currentConfig.colorblindMode
            AresThemeSettings.highContrastMode = currentConfig.highContrastMode
            AresThemeSettings.touchOptimizedMode = currentConfig.touchOptimizedMode
        }
    }

    // Global 50Hz Drive Input Loop (Keyboard & Gamepad)
    val isNt4Connected by services.nt4ClientService.isConnected.collectAsState()
    LaunchedEffect(isNt4Connected) {
        if (isNt4Connected) {
            var lastVx: Double? = null
            var lastVy: Double? = null
            var lastOmega: Double? = null
            var lastQ: Boolean? = null
            var lastE: Boolean? = null
            var lastShift: Boolean? = null
            var lastJ: Boolean? = null
            var lastL: Boolean? = null
            var lastU: Boolean? = null

            services.nt4ClientService.publishBoolean("ARES/Input/isTeleopMode", true)
            services.nt4ClientService.publishBoolean("ARES/Input/isFieldCentric", false)
            services.nt4ClientService.publishBoolean("ARES/Input/isRedAlliance", true)

            while (true) {
                val ks = services.keyboardDriveState
                val g1 = services.gamepadService.gamepad1State.value

                val (vx, vy, omega) = if (ks.useGamepad && g1.connected) {
                    val rawY = com.areslib.math.InputMath.applyDeadband(g1.leftStickY.toDouble(), 0.02)
                    val rawX = com.areslib.math.InputMath.applyDeadband(g1.leftStickX.toDouble(), 0.02)
                    val rawRot = com.areslib.math.InputMath.applyDeadband(g1.rightStickX.toDouble(), 0.02)
                    val activeVx = com.areslib.math.InputMath.applyCurve(rawY, 1.2) * 4.0
                    val activeVy = com.areslib.math.InputMath.applyCurve(rawX, 1.2) * -4.0
                    val activeOmega = com.areslib.math.InputMath.applyCurve(rawRot, 1.2) * -4.0
                    Triple(activeVx, activeVy, activeOmega)
                } else if (ks.enabled) {
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
                        ks.isQPressed || ks.isLeftPressed -> 4.0
                        ks.isEPressed || ks.isRightPressed -> -4.0
                        else -> 0.0
                    }
                    Triple(activeVx, activeVy, activeOmega)
                } else {
                    Triple(0.0, 0.0, 0.0)
                }

                val qPressed = if (ks.useGamepad && g1.connected) g1.leftBumper else ks.isQPressed
                val ePressed = if (ks.useGamepad && g1.connected) g1.rightBumper else ks.isEPressed
                val shiftPressed = if (ks.useGamepad && g1.connected) g1.rightTrigger > 0.5f else ks.isShiftPressed
                val jPressed = if (ks.useGamepad && g1.connected) g1.a else ks.isJPressed
                val lPressed = if (ks.useGamepad && g1.connected) g1.b else ks.isLPressed
                val uPressed = if (ks.useGamepad && g1.connected) g1.x else ks.isUPressed

                if (vx != lastVx) { services.nt4ClientService.publishDouble("ARES/Input/vx", vx); lastVx = vx }
                if (vy != lastVy) { services.nt4ClientService.publishDouble("ARES/Input/vy", vy); lastVy = vy }
                if (omega != lastOmega) { services.nt4ClientService.publishDouble("ARES/Input/omega", omega); lastOmega = omega }
                if (qPressed != lastQ) { services.nt4ClientService.publishBoolean("ARES/Input/isIntaking", qPressed); lastQ = qPressed }
                if (ePressed != lastE) { services.nt4ClientService.publishBoolean("ARES/Input/isFlywheelOn", ePressed); lastE = ePressed }
                if (shiftPressed != lastShift) { services.nt4ClientService.publishBoolean("ARES/Input/isTransferring", shiftPressed); lastShift = shiftPressed }
                if (jPressed != lastJ) { services.nt4ClientService.publishBoolean("ARES/Input/isButtonAPressed", jPressed); lastJ = jPressed }
                if (lPressed != lastL) { services.nt4ClientService.publishBoolean("ARES/Input/isButtonBPressed", lPressed); lastL = lPressed }
                if (uPressed != lastU) { services.nt4ClientService.publishBoolean("ARES/Input/isButtonXPressed", uPressed); lastU = uPressed }

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
            scope = scope
        )
    }
    val tuningViewModel = remember {
        TuningViewModel(
            nt4ClientService = services.nt4ClientService,
            scope = scope
        )
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
            projectGenerator = services.processManagerService
        )
    }
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
        )
    }
    DisposableEffect(subsystemGeneratorViewModel) {
        onDispose { subsystemGeneratorViewModel.close() }
    }
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
        services.ftcDashboardService.start(host = host)
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
                } else if (ks.enabled) {
                    val isPressed = keyEvent.type == KeyEventType.KeyDown
                    when (keyEvent.key) {
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
                                onOpenKeybindings = { mainViewModel.onIntent(MainIntent.SetKeybindingsOpen(true)) }
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
                                seasonId = currentConfig.seasonId
                            )
                            NavigationTarget.IMPORT_CENTER -> ImportCenterScreen(importCenterViewModel)
                            NavigationTarget.FIELD_EDITOR -> FieldEditorScreen(
                                viewModel = fieldEditorViewModel,
                                league = currentConfig.league,
                                projectPath = currentConfig.projectPath
                            )
                            NavigationTarget.ACADEMY -> AcademyScreen(
                                onLaunchSimChallenge = { challengeId ->
                                    services.processManagerService.runSimulation(
                                        currentConfig.projectPath,
                                        currentConfig.league,
                                        currentConfig.simulatorCommand
                                    )
                                    mainViewModel.onIntent(MainIntent.SetTerminalOpen(true))
                                }
                            )
                            NavigationTarget.KDOC_VIEWER -> KDocViewerScreen()
                            NavigationTarget.PIT_DIAGNOSTICS -> HardwareSelfTestWizard(nt4ClientService = services.nt4ClientService)
                            NavigationTarget.MATCH_STRATEGY -> MatchStrategyScreen()
                            NavigationTarget.RUN_HISTORY -> RunHistoryScreen(
                                databaseService = services.databaseService,
                                syncEngineService = services.syncEngineService
                            )
                            NavigationTarget.DATABASE_VIEWER -> DatabaseViewerScreen(
                                databaseService = services.databaseService
                            )

                            NavigationTarget.TUNING -> TuningScreen(
                                viewModel = tuningViewModel,
                                sysIdViewModel = sysIdViewModel,
                                projectPath = currentConfig.projectPath ?: ""
                            )
                            NavigationTarget.SUBSYSTEM_GEN -> SubsystemGeneratorScreen(subsystemGeneratorViewModel)
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

                // Keybindings Sidebar overlay
                com.ares.analytics.ui.components.terminal.ControllerBindingsSidebar(
                    isOpen = isKeybindingsOpen,
                    viewModel = controlsEditorViewModel,
                    onClose = { mainViewModel.onIntent(MainIntent.SetKeybindingsOpen(false)) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    gamepad1State = gamepad1State,
                    gamepad2State = gamepad2State
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
                onNavigate = { mainViewModel.onIntent(MainIntent.SetActiveNav(it)) }
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
