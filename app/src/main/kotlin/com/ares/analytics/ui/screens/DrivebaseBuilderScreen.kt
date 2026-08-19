package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.drivebase.*
import com.ares.analytics.ui.components.core.AresInspectorDrawer
import com.ares.analytics.ui.components.core.AresSpecRow
import com.ares.analytics.ui.components.core.AresSpecSection
import com.ares.analytics.ui.components.core.AresSpecSummaryModal
import com.ares.analytics.ui.components.drivebase.*
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.drivebase.*

@Composable
fun DrivebaseBuilderScreen(
    viewModel: DrivebaseBuilderViewModel,
    onContinueToSubsystems: (() -> Unit)? = null,
    onBackToStudio: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showSpecSummaryModal by remember { mutableStateOf(false) }
    var showAiAssistantDrawer by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Compact Single-Row Sub-Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AresSurface,
                border = BorderStroke(1.dp, AresBorder),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: Kind & Display Name
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AresCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                state.draft.kind.name.uppercase(),
                                color = AresCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            state.draft.displayName,
                            color = AresTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Stepper Horizontal Tabs (5 clean steps)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DrivebaseBuilderStep.entries.forEach { step ->
                            val selected = step == state.step
                            val label = when (step) {
                                DrivebaseBuilderStep.DRIVE_TYPE -> "1. Drive Type"
                                DrivebaseBuilderStep.HARDWARE -> "2. Hardware"
                                DrivebaseBuilderStep.GEOMETRY -> "3. Geometry"
                                DrivebaseBuilderStep.LOCALIZATION -> "4. Localization"
                                DrivebaseBuilderStep.REVIEW -> "5. Safety & Review"
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.onIntent(DrivebaseBuilderIntent.SelectStep(step)) },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AresCyan,
                                    selectedLabelColor = AresOnAccent,
                                    containerColor = AresSurfaceElevated,
                                    labelColor = AresTextPrimary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (selected) AresCyan else AresBorder,
                                    selectedBorderColor = AresCyan,
                                    enabled = true,
                                    selected = selected,
                                ),
                            )
                        }
                    }

                    // Right Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = state.advanced,
                                onCheckedChange = { viewModel.onIntent(DrivebaseBuilderIntent.SetAdvanced(it)) },
                                modifier = Modifier.height(24.dp),
                            )
                            Text(" Adv", color = AresTextPrimary, fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { showAiAssistantDrawer = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AresCyan),
                            border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(12.dp), tint = AresCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("AI", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { showSpecSummaryModal = true },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.TableChart, null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Spec", fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { viewModel.onIntent(DrivebaseBuilderIntent.Reload) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(14.dp), tint = AresTextSecondary)
                        }
                        Button(
                            onClick = { viewModel.onIntent(DrivebaseBuilderIntent.ReviewSave) },
                            enabled = state.dirty,
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) {
                            Text("Save Draft", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            state.error?.let { StatusBanner(it, AresError) }
            if (state.status.isNotBlank()) StatusBanner(state.status, AresGreen)

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Loading drivebase configuration…", color = AresTextSecondary)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state.step) {
                        DrivebaseBuilderStep.DRIVE_TYPE -> DriveTypeStep(state, viewModel)
                        DrivebaseBuilderStep.HARDWARE -> HardwareStep(state, viewModel)
                        DrivebaseBuilderStep.GEOMETRY -> GeometryStep(state, viewModel)
                        DrivebaseBuilderStep.LOCALIZATION -> LocalizationStep(state, viewModel)
                        DrivebaseBuilderStep.REVIEW -> SafetyAndReviewStep(state, viewModel, onContinueToSubsystems, onBackToStudio)
                    }
                }
            }
        }

        // Slide-out Hardware Device Inspector Drawer
        val selected = state.draft.hardware.firstOrNull { it.id == state.selectedHardwareId }
        if (selected != null) {
            AresInspectorDrawer(
                isOpen = true,
                title = selected.displayName,
                categoryBadge = selected.role.name,
                stableId = selected.id,
                icon = Icons.Default.Settings,
                onDismiss = { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(null)) },
                onDone = { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(null)) },
                onDelete = if (state.advanced || state.draft.kind in setOf(DrivebaseKind.DIFFERENTIAL, DrivebaseKind.CUSTOM)) {
                    {
                        viewModel.onIntent(DrivebaseBuilderIntent.RemoveHardware(selected.id))
                        viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(null))
                    }
                } else null,
            ) {
                HardwareEditor(
                    device = selected,
                    hardware = state.draft.hardware,
                    advanced = state.advanced,
                    onUpdate = { viewModel.onIntent(DrivebaseBuilderIntent.UpdateHardware(it)) },
                    onRemove = {
                        viewModel.onIntent(DrivebaseBuilderIntent.RemoveHardware(selected.id))
                        viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(null))
                    },
                )
            }
        }

        AresSpecSummaryModal(
            isOpen = showSpecSummaryModal,
            title = "${state.draft.displayName} Drivetrain Specification",
            subtitle = "${state.league.name} · .ares/drivetrains/${state.draft.documentId}.aresdrivetrain",
            sections = generateDrivebaseSpecSections(state),
            onDismiss = { showSpecSummaryModal = false }
        )
    }
}

@Composable
private fun StatusBanner(message: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(message, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
    }
}

private fun generateDrivebaseSpecSections(state: DrivebaseBuilderState): List<AresSpecSection> {
    val draft = state.draft
    return listOf(
        AresSpecSection(
            title = "Drivetrain Overview",
            rows = listOf(
                AresSpecRow(
                    id = "display_name",
                    primaryLabel = "Display Name",
                    columns = listOf("Value" to draft.displayName)
                ),
                AresSpecRow(
                    id = "kind",
                    primaryLabel = "Drivebase Kind",
                    badge = draft.kind.name,
                    columns = listOf("League" to state.league.name)
                ),
                AresSpecRow(
                    id = "document_id",
                    primaryLabel = "Canonical Document ID",
                    columns = listOf("Path" to ".ares/drivetrains/${draft.documentId}.aresdrivetrain")
                ),
            )
        ),
        AresSpecSection(
            title = "Kinematics & Geometry",
            rows = listOf(
                AresSpecRow(
                    id = "wheel_radius",
                    primaryLabel = "Wheel Radius",
                    columns = listOf("Measurement" to "${draft.geometry.wheelRadiusMeters} m")
                ),
                AresSpecRow(
                    id = "track_width",
                    primaryLabel = "Track Width",
                    columns = listOf("Measurement" to "${draft.geometry.trackWidthMeters} m")
                ),
                AresSpecRow(
                    id = "wheelbase",
                    primaryLabel = "Wheelbase",
                    columns = listOf("Measurement" to "${draft.geometry.wheelBaseMeters} m")
                ),
                AresSpecRow(
                    id = "limits",
                    primaryLabel = "Speed Envelopes",
                    columns = listOf(
                        "Max Linear" to "${draft.safety.maxLinearSpeedMetersPerSecond} m/s",
                        "Max Angular" to "${draft.safety.maxAngularSpeedRadiansPerSecond} rad/s"
                    )
                ),
            )
        ),
        AresSpecSection(
            title = "Hardware Declarations (${draft.hardware.size} devices)",
            rows = draft.hardware.map { dev ->
                AresSpecRow(
                    id = dev.id,
                    primaryLabel = dev.displayName,
                    secondaryLabel = "hw: ${dev.hardwareName}",
                    badge = dev.role.name,
                    columns = listOf(
                        "Direction" to if (dev.inverted) "INVERTED" else "NORMAL",
                        "CAN ID" to (dev.canId?.toString() ?: "N/A"),
                        "CAN Bus" to (dev.canBus ?: "default")
                    )
                )
            }
        ),
        AresSpecSection(
            title = "Localization & Safety Rules",
            rows = listOf(
                AresSpecRow(
                    id = "localization",
                    primaryLabel = "Localization Providers",
                    columns = listOf("Configured" to draft.localization.joinToString { it.name })
                ),
                AresSpecRow(
                    id = "safety_rules",
                    primaryLabel = "Fail-Closed Safety Rules",
                    columns = listOf(
                        "Safe Neutral" to if (draft.safety.safeNeutralRequired) "REQUIRED" else "DISABLED",
                        "Config Health" to if (draft.safety.configurationHealthRequired) "REQUIRED" else "DISABLED",
                        "Neutral Recovery" to if (draft.safety.explicitNeutralRecoveryRequired) "REQUIRED" else "DISABLED",
                        "Current Monitor" to if (draft.safety.currentMonitoringRequired) "REQUIRED" else "DISABLED"
                    )
                ),
            )
        )
    )
}
