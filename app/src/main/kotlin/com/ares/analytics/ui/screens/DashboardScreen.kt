package com.ares.analytics.ui.screens

import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.di.ServiceRegistry
import com.ares.analytics.service.*
import com.ares.analytics.shared.*
import com.ares.analytics.ui.components.dashboard.*
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.DashboardIntent
import com.ares.analytics.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    services: ServiceRegistry,
    currentConfig: WorkspaceConfig,
    matches: List<MatchInfo>,
    onForensicsCompleted: (ForensicsResponse) -> Unit,
    onSelectMatch: (MatchInfo, String) -> Unit,
    reloadTrigger: Int,
    onImportSuccess: () -> Unit,
    onOpenKeybindings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var newLayoutName by remember { mutableStateOf("") }

    // Replay integration
    val replayEngine = services.replayEngineService
    val replayState by replayEngine.state.collectAsState()
    val replayProgress by replayEngine.progress.collectAsState()
    val replaySpeed by replayEngine.speed.collectAsState()
    val isReplayMode = state.primarySessionId != null && replayState != ReplayState.STOPPED

    // Load replay session when primarySessionId changes
    LaunchedEffect(state.primarySessionId) {
        val sessionId = state.primarySessionId
        if (sessionId != null) {
            replayEngine.loadSession(sessionId)
        } else {
            replayEngine.stop()
        }
    }

    // Bridge replay telemetry flow into the same nt4ClientService.telemetryFlow
    LaunchedEffect(Unit) {
        replayEngine.replayTelemetryFlow.collect { frame ->
            services.nt4ClientService.emitReplayFrame(frame)
        }
    }

    LaunchedEffect(state.importSuccess) {
        if (state.importSuccess) {
            onImportSuccess()
            viewModel.onIntent(DashboardIntent.ClearImportSuccess)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {



        // Configurable widgets area
        val layout = state.currentLayout
        if (layout != null) {
            val builders = mapOf<String, @Composable (WidgetConfig, Modifier) -> Unit>(
                "driver_station" to { _, mod ->
                    FtcDriverStationWidget(nt4Client = services.nt4ClientService, modifier = mod)
                },
                "runs_index" to { _, mod ->
                    RunsIndex(
                        databaseService = services.databaseService,
                        primarySessionId = state.primarySessionId,
                        compareSessionId = state.compareSessionId,
                        onSelectPrimary = { viewModel.onIntent(DashboardIntent.SelectPrimarySession(it)) },
                        onSelectCompare = { viewModel.onIntent(DashboardIntent.SelectCompareSession(it)) },
                        modifier = mod,
                        reloadTrigger = reloadTrigger
                    )
                },
                "alerts" to { _, mod ->
                    AlertPanel(services.alertEngineService, mod)
                },
                "telemetry_chart" to { widget, mod ->
                    TelemetryChartPanel(
                        nt4ClientService = services.nt4ClientService,
                        properties = widget.properties,
                        onPropertiesChanged = { newProps ->
                            viewModel.onIntent(DashboardIntent.UpdateLayout(layout.widgets.map {
                                if (it.id == widget.id) it.copy(properties = newProps) else it
                            }))
                        },
                        modifier = mod
                    )
                },
                "camera_stream" to { widget, mod ->
                    CameraStreamCard(
                        properties = widget.properties,
                        onPropertiesChanged = { newProps ->
                            viewModel.onIntent(DashboardIntent.UpdateLayout(layout.widgets.map {
                                if (it.id == widget.id) it.copy(properties = newProps) else it
                            }))
                        },
                        modifier = mod
                    )
                },
                "motor_health" to { _, mod ->
                    MotorHealthCard(services.databaseService, state.primarySessionId, mod)
                },
                "vision_quality" to { _, mod ->
                    VisionQualityCard(services.databaseService, state.primarySessionId, mod)
                },
                "ai_coach" to { _, mod ->
                    AiCoachPanel(services.databaseService, services.syncEngineService, state.primarySessionId, onForensicsCompleted, mod)
                },
                "match_schedule" to { _, mod ->
                    MatchScheduleCard(matches, currentConfig.teamId, onSelectMatch, mod)
                },
                "console_viewer" to { widget, mod ->
                    ConsoleViewer(services, widget, mod)
                },
                "swerve_animator" to { _, mod ->
                    SwerveModuleVisualizer(services.nt4ClientService, mod)
                },
                "joystick_visualizer" to { _, mod ->
                    JoystickVisualizer(
                        currentFrame = null, 
                        nt4ClientService = services.nt4ClientService, 
                        services = services, 
                        onOpenKeybindings = onOpenKeybindings,
                        modifier = mod
                    )
                },
                "mechanism_visualizer" to { _, mod ->
                    MechanismVisualizer(currentFrame = null, nt4ClientService = services.nt4ClientService, modifier = mod)
                },
                "mecanum_visualizer" to { _, mod ->
                    MecanumVisualizer(nt4ClientService = services.nt4ClientService, modifier = mod)
                },
                "field_viewer" to { widget, mod ->
                    FieldViewerCard(
                        nt4ClientService = services.nt4ClientService,
                        league = currentConfig.league,
                        projectPath = currentConfig.projectPath,
                        properties = widget.properties,
                        onPropertiesChanged = { newProps ->
                            viewModel.onIntent(DashboardIntent.UpdateLayout(layout.widgets.map {
                                if (it.id == widget.id) it.copy(properties = newProps) else it
                            }))
                        },
                        modifier = mod
                    )
                },
                "pose_viewer" to { _, mod ->
                    PoseViewerCard(services.nt4ClientService, mod)
                },
                "trends_card" to { _, mod ->
                    TrendsCard(services.databaseService, mod)
                },
                "battery_health" to { _, mod ->
                    BatteryHealthCard(services.databaseService, state.primarySessionId, mod)
                },
                "statistics_panel" to { _, mod ->
                    StatisticsPanel(services.databaseService, state.primarySessionId, mod)
                },
                "control_profiler" to { _, mod ->
                    ControlLoopProfilerCard(services.nt4ClientService, mod)
                },
                "state_tracker" to { _, mod ->
                    StateMachineTrackerCard(services.nt4ClientService, mod)
                },
                "system_health" to { _, mod ->
                    SystemHealthCard(services.nt4ClientService, mod)
                },
                "imu_visualizer" to { _, mod ->
                    IMUVisualizerCard(services.nt4ClientService, mod)
                },
                "power_distribution" to { _, mod ->
                    PowerDistributionCard(services.nt4ClientService, mod)
                }
            )

            WidgetGrid(
                widgets = layout.widgets,
                onLayoutChanged = { newWidgets ->
                    viewModel.onIntent(DashboardIntent.UpdateLayout(newWidgets))
                },
                onAddWidget = { viewModel.onIntent(DashboardIntent.SetPickerOpen(true)) },
                onRemoveWidget = { id ->
                    viewModel.onIntent(DashboardIntent.RemoveWidget(id))
                },
                widgetBuilders = builders,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        // Timeline Scrubber Bar
        val isConnected by services.nt4ClientService.isConnected.collectAsState()
        val isReplayActive by services.nt4ClientService.isReplayActive.collectAsState()

        if (state.primarySessionId != null || isConnected) {
            ReplayTimelineScrubber(
                replayEngine = replayEngine,
                replayState = replayState,
                progress = if (state.primarySessionId == null && !isReplayActive) 1.0 else replayProgress,
                speed = replaySpeed,
                isLiveConnection = state.primarySessionId == null,
                isReplayActive = isReplayActive,
                onSnapToRealtime = {
                    scope.launch {
                        services.nt4ClientService.isReplayActive.value = false
                        replayEngine.stop()
                    }
                },
                onScrubLive = { pct ->
                    scope.launch {
                        services.nt4ClientService.isReplayActive.value = true
                        replayEngine.loadSession("live-telemetry")
                        replayEngine.scrubTo(pct)
                    }
                },
                onPauseLive = {
                    scope.launch {
                        services.nt4ClientService.isReplayActive.value = true
                        replayEngine.loadSession("live-telemetry")
                        replayEngine.scrubTo(1.0)
                        replayEngine.pause()
                    }
                },
                onPlayLive = {
                    scope.launch {
                        services.nt4ClientService.isReplayActive.value = true
                        replayEngine.loadSession("live-telemetry")
                        replayEngine.play()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (state.isPickerOpen) {
        WidgetPicker(
            onDismiss = { viewModel.onIntent(DashboardIntent.SetPickerOpen(false)) },
            onSelectWidget = { type ->
                viewModel.onIntent(DashboardIntent.AddWidget(type))
            }
        )
    }
}

@Composable
private fun ReplayTimelineScrubber(
    replayEngine: ReplayEngineService,
    replayState: ReplayState,
    progress: Double,
    speed: Double,
    isLiveConnection: Boolean,
    isReplayActive: Boolean,
    onSnapToRealtime: () -> Unit,
    onScrubLive: (Double) -> Unit,
    onPauseLive: () -> Unit,
    onPlayLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier,
        color = AresSurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play / Pause
            IconButton(
                onClick = {
                    scope.launch {
                        if (isLiveConnection && !isReplayActive) {
                            onPauseLive()
                        } else {
                            when (replayState) {
                                ReplayState.PLAYING -> replayEngine.pause()
                                ReplayState.PAUSED -> replayEngine.play()
                                ReplayState.STOPPED -> replayEngine.play()
                            }
                        }
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isLiveConnection && !isReplayActive) Icons.Default.Pause
                                  else if (replayState == ReplayState.PLAYING) Icons.Default.Pause
                                  else Icons.Default.PlayArrow,
                    contentDescription = if (isLiveConnection && !isReplayActive) "Pause"
                                         else if (replayState == ReplayState.PLAYING) "Pause"
                                         else "Play",
                    tint = AresCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Stop / Live TV
            IconButton(
                onClick = {
                    if (isLiveConnection) {
                        onSnapToRealtime()
                    } else {
                        scope.launch { replayEngine.stop() }
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isLiveConnection) Icons.Default.LiveTv else Icons.Default.Stop,
                    contentDescription = if (isLiveConnection) "Realtime" else "Stop",
                    tint = if (isLiveConnection && !isReplayActive) AresCyan else AresTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Step backward
            IconButton(
                onClick = {
                    if (!isLiveConnection || isReplayActive) {
                        scope.launch { replayEngine.stepBackward() }
                    }
                },
                enabled = !isLiveConnection || isReplayActive,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Step Back",
                    tint = if (!isLiveConnection || isReplayActive) AresTextSecondary else AresBorder,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Step forward
            IconButton(
                onClick = {
                    if (!isLiveConnection || isReplayActive) {
                        scope.launch { replayEngine.stepForward() }
                    }
                },
                enabled = !isLiveConnection || isReplayActive,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Step Forward",
                    tint = if (!isLiveConnection || isReplayActive) AresTextSecondary else AresBorder,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Progress slider
            var sliderDragging by remember { mutableStateOf(false) }
            var localSliderValue by remember { mutableStateOf(0f) }

            Slider(
                value = if (sliderDragging) localSliderValue else progress.toFloat(),
                onValueChange = { newVal ->
                    sliderDragging = true
                    localSliderValue = newVal
                },
                onValueChangeFinished = {
                    sliderDragging = false
                    scope.launch {
                        if (isLiveConnection && !isReplayActive) {
                            onScrubLive(localSliderValue.toDouble())
                        } else {
                            replayEngine.scrubTo(localSliderValue.toDouble())
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = AresCyan,
                    activeTrackColor = AresCyan,
                    inactiveTrackColor = AresBorder
                )
            )

            // Time / Live Status display
            if (isLiveConnection && !isReplayActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AresGreen, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "LIVE",
                        color = AresGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "${formatTime((progress * 100).toLong())}%",
                    color = AresTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Snap to Realtime button (shown only in Live Rewind mode)
            if (isLiveConnection && isReplayActive) {
                Button(
                    onClick = onSnapToRealtime,
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = AresBackground, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Snap to Realtime", color = AresBackground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Speed selector
            var speedExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { speedExpanded = !speedExpanded }
                ) {
                    Text("${speed}×", color = AresAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = speedExpanded,
                    onDismissRequest = { speedExpanded = false }
                ) {
                    listOf(0.5, 1.0, 2.0, 4.0).forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s}×", color = AresTextPrimary) },
                            onClick = {
                                scope.launch { replayEngine.setSpeed(s) }
                                speedExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(percentage: Long): String {
    return "$percentage"
}
