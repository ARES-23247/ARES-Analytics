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
    onImportSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var newLayoutName by remember { mutableStateOf("") }

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
        // Layout Control Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().background(AresSurface).border(1.dp, AresBorder, RoundedCornerShape(8.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Dashboard Profile:", color = AresTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box {
                    TextButton(onClick = { viewModel.onIntent(DashboardIntent.SetProfileExpanded(true)) }) {
                        Text(state.currentRoleProfile, color = AresCyan, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AresCyan)
                    }
                    DropdownMenu(
                        expanded = state.profileExpanded,
                        onDismissRequest = { viewModel.onIntent(DashboardIntent.SetProfileExpanded(false)) },
                        modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
                    ) {
                        state.availableProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile, color = AresTextPrimary) },
                                onClick = {
                                    viewModel.onIntent(DashboardIntent.ChangeProfile(profile))
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Import Hoot Log Button
                Button(
                    onClick = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Select Log File(s)"
                            isMultiSelectionEnabled = true
                            fileFilter = FileNameExtensionFilter(
                                "Supported Log Files (*.wpilog, *.wpilogxz, *.hoot, *.csv, *.jsonl, *.log, *.rlog, *.revlog)",
                                "wpilog", "wpilogxz", "hoot", "csv", "jsonl", "log", "rlog", "revlog"
                            )
                        }
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            val selectedFiles = chooser.selectedFiles.toList()
                            if (selectedFiles.isNotEmpty()) {
                                viewModel.onIntent(
                                    DashboardIntent.ImportLogFiles(
                                        selectedFiles,
                                        currentConfig.teamId,
                                        currentConfig.seasonId,
                                        currentConfig.robotId
                                    )
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AresBackground, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Publish, contentDescription = null, tint = AresBackground, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Import Log File(s)", color = AresBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = newLayoutName,
                    onValueChange = { newLayoutName = it },
                    placeholder = { Text("Layout Name", fontSize = 11.sp, color = AresTextTertiary) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                    modifier = Modifier.width(130.dp).height(38.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AresCyan,
                        unfocusedBorderColor = AresBorder,
                        focusedContainerColor = AresSurfaceElevated,
                        unfocusedContainerColor = AresSurfaceElevated
                    )
                )

                Button(
                    onClick = {
                        if (newLayoutName.trim().isNotEmpty()) {
                            viewModel.onIntent(DashboardIntent.SaveLayoutAs(newLayoutName.trim()))
                            newLayoutName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = AresBackground, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save Layout", color = AresBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        viewModel.onIntent(DashboardIntent.ResetProfile)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AresBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AresTextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset Profile", color = AresTextPrimary, fontSize = 12.sp)
                }
            }
        }

        // Row 1: Live telemetry bar
        LiveTelemetryBar(services.nt4ClientService)

        // Configurable widgets area
        val layout = state.currentLayout
        if (layout != null) {
            val builders = mapOf<String, @Composable (WidgetConfig, Modifier) -> Unit>(
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
                    JoystickVisualizer(currentFrame = null, nt4ClientService = services.nt4ClientService, modifier = mod)
                },
                "mechanism_visualizer" to { _, mod ->
                    MechanismVisualizer(currentFrame = null, nt4ClientService = services.nt4ClientService, modifier = mod)
                },
                "mecanum_visualizer" to { _, mod ->
                    MecanumVisualizer(nt4ClientService = services.nt4ClientService, modifier = mod)
                },
                "field_viewer" to { _, mod ->
                    FieldViewerCard(services.nt4ClientService, currentConfig.league, mod)
                },
                "trends_card" to { _, mod ->
                    TrendsCard(services.databaseService, mod)
                },
                "battery_health" to { _, mod ->
                    BatteryHealthCard(services.databaseService, state.primarySessionId, mod)
                },
                "statistics_panel" to { _, mod ->
                    StatisticsPanel(services.databaseService, state.primarySessionId, mod)
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
