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
import java.text.SimpleDateFormat
import java.util.Locale

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

    val undismissedAlerts = remember { mutableStateListOf<AlertRecord>() }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(state.alerts) {
        state.alerts.forEach { alert ->
            val isCritical = alert.ruleKey.contains("brownout", ignoreCase = true) ||
                             alert.ruleKey.contains("comms", ignoreCase = true) ||
                             alert.ruleKey.contains("can", ignoreCase = true) ||
                             alert.ruleKey.contains("battery", ignoreCase = true)
            
            if (isCritical && undismissedAlerts.none { it.alertId == alert.alertId }) {
                undismissedAlerts.add(alert)
            }
        }
    }

    LaunchedEffect(state.primarySessionId) {
        undismissedAlerts.clear()
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                "session_summary" to { _, mod ->
                    SessionSummaryCard(services.databaseService, state.primarySessionId, mod)
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
                },
                "tuning_card" to { _, mod ->
                    TuningCard(services.nt4ClientService, mod)
                },
                "ekf_telemetry" to { _, mod ->
                    EKFTelemetryCard(services.nt4ClientService, mod)
                },
                "path_tuning" to { _, mod ->
                    PathTuningVisualizer(services.nt4ClientService, mod)
                },
                "brownout_protection" to { _, mod ->
                    BrownoutProtectionCard(services.nt4ClientService, mod)
                },
                "profiling_diagnostics" to { _, mod ->
                    ProfilingDiagnosticsCard(services.nt4ClientService, mod)
                },
                "indicator_lights" to { _, mod ->
                    IndicatorLightsCard(services.nt4ClientService, mod)
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
                sessionMode = state.sessionMode,
                sessionId = state.primarySessionId,
                alerts = state.alerts,
                onSnapToRealtime = {
                    scope.launch {
                        services.nt4ClientService.isReplayActive.value = false
                        viewModel.onIntent(DashboardIntent.SetSessionMode(SessionMode.LIVE_STREAMING))
                        replayEngine.stop()
                    }
                },
                onScrubLive = { pct ->
                    scope.launch {
                        services.nt4ClientService.isReplayActive.value = true
                        viewModel.onIntent(DashboardIntent.SetSessionMode(SessionMode.LIVE_REWIND))
                        replayEngine.loadSession("live-telemetry")
                        replayEngine.scrubTo(pct)
                    }
                },
                onPauseLive = {
                    scope.launch {
                        services.nt4ClientService.isReplayActive.value = true
                        viewModel.onIntent(DashboardIntent.SetSessionMode(SessionMode.LIVE_REWIND))
                        replayEngine.loadSession("live-telemetry")
                        replayEngine.scrubTo(1.0)
                        replayEngine.pause()
                    }
                },
                onPlayLive = {
                    scope.launch {
                        services.nt4ClientService.isReplayActive.value = true
                        viewModel.onIntent(DashboardIntent.SetSessionMode(SessionMode.LIVE_REWIND))
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

    // Floating notifications / popout warning alerts
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .width(320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        undismissedAlerts.forEach { alert ->
            Card(
                colors = CardDefaults.cardColors(containerColor = AresError.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AresBackground,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                alert.ruleKey.contains("brownout", ignoreCase = true) -> "CRITICAL BROWNOUT"
                                alert.ruleKey.contains("comms", ignoreCase = true) -> "COMMS / PACKET LOSS"
                                alert.ruleKey.contains("can", ignoreCase = true) -> "CANBUS HARDWARE ERROR"
                                alert.ruleKey.contains("battery", ignoreCase = true) -> "LOW BATTERY ALERT"
                                else -> alert.ruleKey.uppercase()
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AresBackground
                        )
                        Text(
                            text = "Peak: ${String.format("%.2f", alert.peakValue)} | Triggered: ${timeFormat.format(java.util.Date(alert.triggerTimestampMs))}",
                            fontSize = 10.sp,
                            color = AresBackground.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(
                        onClick = { undismissedAlerts.remove(alert) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = AresBackground,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
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
    sessionMode: SessionMode,
    sessionId: String?,
    alerts: List<AlertRecord>,
    onSnapToRealtime: () -> Unit,
    onScrubLive: (Double) -> Unit,
    onPauseLive: () -> Unit,
    onPlayLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    val modeColor = when (sessionMode) {
        SessionMode.LIVE_STREAMING -> ModeLive
        SessionMode.LIVE_REWIND -> ModeRewind
        SessionMode.HISTORICAL_REPLAY -> ModeReplay
    }
    val modeGlow = when (sessionMode) {
        SessionMode.LIVE_STREAMING -> ModeLiveGlow
        SessionMode.LIVE_REWIND -> ModeRewindGlow
        SessionMode.HISTORICAL_REPLAY -> ModeReplayGlow
    }

    Surface(
        modifier = modifier,
        color = AresSurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, modeColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mode Pill
            Surface(
                color = modeGlow,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, modeColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(modeColor, RoundedCornerShape(3.dp)))
                    Text(
                        text = when (sessionMode) {
                            SessionMode.LIVE_STREAMING -> "LIVE"
                            SessionMode.LIVE_REWIND -> "LIVE REWIND"
                            SessionMode.HISTORICAL_REPLAY -> "REPLAY: ${sessionId?.take(8)}"
                        },
                        color = modeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
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
                    imageVector = when {
                        isLiveConnection && !isReplayActive -> Icons.Default.Pause
                        replayState == ReplayState.PLAYING -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        isLiveConnection && !isReplayActive -> "Pause"
                        replayState == ReplayState.PLAYING -> "Pause"
                        else -> "Play"
                    },
                    tint = modeColor,
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
                    tint = if (isLiveConnection && !isReplayActive) modeColor else AresTextSecondary,
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

            var sliderDragging by remember { mutableStateOf(false) }
            var localSliderValue by remember { mutableStateOf(0f) }
            val density by replayEngine.telemetryDensity.collectAsState()
            val actions by replayEngine.sessionActions.collectAsState()
            val sessionStart by replayEngine.sessionStartTimestampMs.collectAsState()
            val sessionDuration by replayEngine.sessionDurationMs.collectAsState()

            Box(modifier = Modifier.weight(1f).height(32.dp)) {
                // Histogram Canvas
                if (density.isNotEmpty() || actions.isNotEmpty() || alerts.isNotEmpty()) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        if (density.isNotEmpty()) {
                            val barWidth = size.width / density.size
                            density.forEachIndexed { i, value ->
                                val barHeight = size.height * value
                                val x = i * barWidth
                                val y = size.height - barHeight
                                drawRect(
                                    color = modeColor.copy(alpha = 0.3f),
                                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                    size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
                                )
                            }
                        }

                        // Draw action markers
                        if (actions.isNotEmpty() && sessionDuration > 0) {
                            actions.forEach { action ->
                                val proportion = (action.timestampMs - sessionStart).toDouble() / sessionDuration.toDouble()
                                val x = (proportion * size.width).toFloat()
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color(0xFF00E5FF), // Cyan marker
                                    radius = 3f,
                                    center = androidx.compose.ui.geometry.Offset(x, size.height / 2f)
                                )
                            }
                        }

                        // Draw alert markers (Red Flags)
                        if (alerts.isNotEmpty() && sessionDuration > 0) {
                            alerts.forEach { alert ->
                                val proportion = (alert.triggerTimestampMs - sessionStart).toDouble() / sessionDuration.toDouble()
                                if (proportion in 0.0..1.0) {
                                    val x = (proportion * size.width).toFloat()
                                    // Vertical flag line
                                    drawLine(
                                        color = androidx.compose.ui.graphics.Color(0xFFFF5252).copy(alpha = 0.7f),
                                        start = androidx.compose.ui.geometry.Offset(x, 6f),
                                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                                        strokeWidth = 2f
                                    )
                                    // Downward pointing triangle flag at the top
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(x, 0f)
                                        lineTo(x - 4f, 0f)
                                        lineTo(x, 6f)
                                        lineTo(x + 4f, 0f)
                                        close()
                                    }
                                    drawPath(
                                        path = path,
                                        color = androidx.compose.ui.graphics.Color(0xFFFF5252)
                                    )
                                }
                            }
                        }
                    }
                }

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
                    modifier = Modifier.fillMaxSize(),
                    colors = SliderDefaults.colors(
                        thumbColor = modeColor,
                        activeTrackColor = modeColor,
                        inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }

            // Time / Live Status display
            Text(
                text = "${formatTime((progress * 100).toLong())}%",
                color = AresTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            // Snap to Realtime button (shown only in Live Rewind mode)
            if (isLiveConnection && isReplayActive) {
                Button(
                    onClick = onSnapToRealtime,
                    colors = ButtonDefaults.buttonColors(containerColor = modeColor),
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
