package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.AresGenerationPhase
import com.ares.analytics.ui.components.core.*
import com.ares.analytics.ui.components.subsystems.*
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.SubsystemBuilderStage
import com.ares.analytics.viewmodel.SubsystemGeneratorState
import com.ares.analytics.viewmodel.SubsystemGeneratorViewModel
import com.areslib.subsystem.SubsystemDocument

/** Modular visual editor for project-backed subsystem DSL documents and generated Kotlin. */
@Composable
fun SubsystemGeneratorScreen(
    viewModel: SubsystemGeneratorViewModel,
    onContinueToPortMap: (() -> Unit)? = null,
    onBackToDrivetrain: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showSpecSummaryModal by remember { mutableStateOf(false) }
    var showAiAssistantDrawer by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Sleek Top Header Bar
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
                    // Left: Subsystem Identity & Selector
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
                                "SUBSYSTEM",
                                color = AresCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            state.draft?.document?.displayName ?: "No Subsystem",
                            color = AresTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Stepper Horizontal Tabs (4 Consolidated Stages)
                    if (state.draft != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val activeStage = state.activeStage
                            val stages = listOf(
                                SubsystemBuilderStage.PURPOSE to "1. Purpose & Template",
                                SubsystemBuilderStage.HARDWARE to "2. Hardware & IO",
                                SubsystemBuilderStage.STATE_AND_BEHAVIOR to "3. Stateflow & Control",
                                SubsystemBuilderStage.REVIEW to "4. Tuning & Review",
                            )

                            stages.forEach { (stage, label) ->
                                val selected = when (stage) {
                                    SubsystemBuilderStage.PURPOSE -> activeStage == SubsystemBuilderStage.PURPOSE
                                    SubsystemBuilderStage.HARDWARE -> activeStage == SubsystemBuilderStage.HARDWARE
                                    SubsystemBuilderStage.STATE_AND_BEHAVIOR -> activeStage in setOf(SubsystemBuilderStage.STATE_AND_BEHAVIOR, SubsystemBuilderStage.SAFETY)
                                    SubsystemBuilderStage.REVIEW -> activeStage in setOf(SubsystemBuilderStage.TUNING, SubsystemBuilderStage.CAPABILITIES, SubsystemBuilderStage.SIMULATION_AND_TESTING, SubsystemBuilderStage.REVIEW)
                                    else -> false
                                }

                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectStage(stage) },
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
                    }

                    // Right Action Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { showAiAssistantDrawer = true },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(12.dp), tint = AresCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("AI Assistant", fontSize = 11.sp)
                        }

                        if (state.draft != null) {
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
                                onClick = { viewModel.reload() },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(14.dp), tint = AresTextSecondary)
                            }

                            Button(
                                onClick = { viewModel.save() },
                                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) {
                                Text("Save Subsystem", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            state.status?.let { StatusBanner(it, AresGreen) }
            state.loadError?.let { StatusBanner(it, AresError) }

            val document = state.draft?.document
            if (document == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No Subsystem Loaded", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Create a new mechanism subsystem (Intake, Arm, Lift, Shooter) to get started.", color = AresTextSecondary, fontSize = 11.sp)
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
                    when (state.activeStage) {
                        SubsystemBuilderStage.PURPOSE -> SubsystemPurposeSection(state, viewModel)
                        SubsystemBuilderStage.HARDWARE -> SubsystemHardwareSection(state, viewModel)
                        SubsystemBuilderStage.STATE_AND_BEHAVIOR, SubsystemBuilderStage.SAFETY -> SubsystemStateflowSection(state, viewModel)
                        SubsystemBuilderStage.TUNING, SubsystemBuilderStage.CAPABILITIES, SubsystemBuilderStage.SIMULATION_AND_TESTING, SubsystemBuilderStage.REVIEW -> SubsystemTuningReviewSection(state, viewModel)
                    }
                }
            }
        }

        // Slide-out Hardware Device Inspector Drawer
        val doc = state.draft?.document
        if (doc != null) {
            doc.hardware.firstOrNull { it.uid == state.selectedHardwareUid }?.let { device ->
                AresInspectorDrawer(
                    isOpen = true,
                    title = device.displayName,
                    categoryBadge = device.kind.name,
                    stableId = device.hardwareId,
                    icon = Icons.Default.Settings,
                    onDismiss = { viewModel.selectHardware(null) },
                    onDone = { viewModel.selectHardware(null) },
                    onDelete = { viewModel.removeHardware(device.hardwareId) },
                    deleteButtonText = "Delete Hardware",
                ) {
                    HardwareInspectorBody(state, device, viewModel)
                }
            }

            // Slide-out State Field Inspector Drawer
            doc.stateFields.firstOrNull { it.uid == state.selectedFieldUid }?.let { field ->
                AresInspectorDrawer(
                    isOpen = true,
                    title = field.displayName,
                    categoryBadge = field.role.name,
                    stableId = field.fieldId,
                    icon = Icons.Default.Memory,
                    onDismiss = { viewModel.selectField(null) },
                    onDone = { viewModel.selectField(null) },
                    onDelete = { viewModel.removeStateField(field.fieldId) },
                    deleteButtonText = "Delete State Field",
                ) {
                    StateFieldInspectorBody(field, viewModel)
                }
            }

            // Slide-out Control Loop Inspector Drawer
            doc.controlLoops.firstOrNull { it.uid == state.selectedLoopUid }?.let { loop ->
                AresInspectorDrawer(
                    isOpen = true,
                    title = loop.displayName,
                    categoryBadge = loop.strategy.name,
                    stableId = loop.loopId,
                    icon = Icons.Default.Build,
                    onDismiss = { viewModel.selectLoop(null) },
                    onDone = { viewModel.selectLoop(null) },
                    onDelete = { viewModel.removeControlLoop(loop.loopId) },
                    deleteButtonText = "Delete Controller Rule",
                ) {
                    ControlInspectorBody(state, loop, viewModel)
                }
            }

            // Slide-out Tuning Parameter Inspector Drawer
            doc.tuningParameters.firstOrNull { it.uid == state.selectedTuningParameterUid }?.let { param ->
                AresInspectorDrawer(
                    isOpen = true,
                    title = param.displayName,
                    categoryBadge = param.type.name,
                    stableId = param.key,
                    icon = Icons.Default.Tune,
                    onDismiss = { viewModel.selectTuningParameter(null) },
                    onDone = { viewModel.selectTuningParameter(null) },
                    onDelete = { viewModel.removeTuningParameter(param.uid) },
                    deleteButtonText = "Delete Parameter",
                ) {
                    TuningParameterInspectorBody(param, viewModel)
                }
            }

            // AI Assistant Slide-Out Drawer
            AresInspectorDrawer(
                isOpen = showAiAssistantDrawer,
                title = "Subsystem AI Assistant",
                categoryBadge = "GEMINI",
                stableId = "subsystem-assistant",
                icon = Icons.Default.AutoAwesome,
                onDismiss = { showAiAssistantDrawer = false },
                onDone = { showAiAssistantDrawer = false },
            ) {
                SubsystemAiAssistantDrawerContent(state, viewModel)
            }

            // Specification Summary Modal
            AresSpecSummaryModal(
                isOpen = showSpecSummaryModal,
                title = "${doc.displayName} Subsystem Specification",
                subtitle = "Mechanism Subsystem · .ares/subsystems/${doc.documentId}.aressubsystem",
                sections = generateSubsystemSpecSections(doc),
                onDismiss = { showSpecSummaryModal = false },
            )
        }
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

@Composable
private fun SubsystemAiAssistantDrawerContent(
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
) {
    var prompt by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ask Gemini to generate or suggest subsystem structures, gains, or safety rules.", color = AresTextSecondary, fontSize = 11.sp)
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("What should this subsystem do?") },
            modifier = Modifier.fillMaxWidth().height(100.dp),
        )
        Button(
            onClick = {
                if (prompt.isNotBlank()) {
                    viewModel.requestAiProposal(prompt)
                }
            },
            enabled = prompt.isNotBlank() && !state.aiProposalInProgress,
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.aiProposalInProgress) "Generating Proposal…" else "Generate AI Proposal")
        }
    }
}

private fun generateSubsystemSpecSections(document: SubsystemDocument): List<AresSpecSection> = listOf(
    AresSpecSection(
        title = "Hardware Devices (${document.hardware.size})",
        rows = document.hardware.map { dev ->
            AresSpecRow(
                id = dev.hardwareId,
                primaryLabel = dev.displayName,
                secondaryLabel = "id: ${dev.hardwareId}",
                badge = dev.kind.name,
                columns = listOf(
                    "Connection" to dev.connectionLabel(document.platform),
                    "Required" to (if (dev.required) "Yes" else "No"),
                )
            )
        }
    ),
    AresSpecSection(
        title = "State Fields & Controllers (${document.stateFields.size} fields, ${document.controlLoops.size} loops)",
        rows = document.stateFields.map { fld ->
            AresSpecRow(
                id = fld.fieldId,
                primaryLabel = fld.displayName,
                secondaryLabel = "field: ${fld.fieldId}",
                badge = fld.role.name,
                columns = listOf(
                    "Type" to fld.type.name.lowercase(),
                    "Unit" to (fld.unit ?: "None"),
                )
            )
        }
    )
)
