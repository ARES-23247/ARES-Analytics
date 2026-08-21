package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
            val headerControlHeight = if (AresThemeSettings.touchOptimizedMode) 48.dp else 36.dp
            ResponsiveBuilderHeader(
                identity = {
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
                },
                steps = {
                    if (state.draft != null) {
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
                                modifier = Modifier.height(headerControlHeight),
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
                },
                actions = {
                    OutlinedButton(
                        onClick = { showAiAssistantDrawer = true },
                        modifier = Modifier.height(headerControlHeight),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.Default.AutoAwesome, "Open AI subsystem assistant", modifier = Modifier.size(16.dp), tint = AresCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("AI Assistant", fontSize = 11.sp)
                    }
                    if (state.draft != null) {
                        IconButton(
                            onClick = viewModel::undo,
                            enabled = state.canUndo,
                            modifier = Modifier.size(headerControlHeight),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last subsystem edit", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = viewModel::redo,
                            enabled = state.canRedo,
                            modifier = Modifier.size(headerControlHeight),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo subsystem edit", modifier = Modifier.size(18.dp))
                        }
                        OutlinedButton(
                            onClick = { showSpecSummaryModal = true },
                            modifier = Modifier.height(headerControlHeight),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.TableChart, "Open subsystem specification", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Spec", fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { viewModel.reload() },
                            modifier = Modifier.size(headerControlHeight),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload subsystem", modifier = Modifier.size(18.dp), tint = AresTextSecondary)
                        }
                        Button(
                            onClick = { viewModel.save() },
                            enabled = state.canSave,
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                            modifier = Modifier.height(headerControlHeight),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Text("Save subsystem", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )

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

            // Subsystem AI Proposal Review Dialog
            state.aiProposal?.let { review ->
                SubsystemAiProposalDialog(
                    review = review,
                    onApply = { viewModel.applyAiProposal() },
                    onDismiss = { viewModel.dismissAiProposal() },
                )
            }

            // Subsystem Template Picker Modal
            if (state.showTemplatePicker) {
                SubsystemTemplatePickerDialog(
                    currentTemplate = doc.template,
                    onApplyTemplate = { tpl ->
                        viewModel.applyTemplate(tpl)
                        viewModel.setTemplatePickerVisible(false)
                    },
                    onDismiss = { viewModel.setTemplatePickerVisible(false) },
                )
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = AresSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Describe your subsystem requirements in plain language.",
                    color = AresTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Text(
                    "Gemini will generate a structured proposal with hardware devices, state fields, control laws, and live tuning parameters.",
                    color = AresTextSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("What should this subsystem do?") },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            placeholder = { Text("e.g. Dual-motor intake with current-based jam detection and automatic reverse") },
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
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (state.aiProposalInProgress) "Generating proposal…" else "Generate AI Proposal")
        }

        state.aiProposalError?.let { Text(it, color = AresError, fontSize = 12.sp) }

        Surface(
            color = AresBackground.copy(alpha = 0.5f),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                "Privacy: Only your prompt and current subsystem form are sent using the configured AI provider. Your logs and credentials are never transmitted.",
                color = AresTextTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun SubsystemAiProposalDialog(
    review: com.ares.analytics.viewmodel.SubsystemAiProposalReview,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review Gemini's Subsystem Proposal") },
        text = {
            Column(
                Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(review.proposal.summary, color = AresTextPrimary, fontWeight = FontWeight.SemiBold)
                review.proposal.explanations.forEach { explanation ->
                    Text("• $explanation", color = AresTextSecondary, fontSize = 12.sp)
                }
                if (review.problems.isNotEmpty()) {
                    Text("Validation Review", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    review.problems.forEach { problem ->
                        Text(
                            "${if (problem.severity == com.ares.analytics.viewmodel.SubsystemProblemSeverity.ERROR) "Blocking" else "Warning"}: ${problem.message}",
                            color = if (problem.severity == com.ares.analytics.viewmodel.SubsystemProblemSeverity.ERROR) AresError else AresGold,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (review.diff.isNotEmpty()) {
                    Text("Proposed Form Changes", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    Column(
                        Modifier.fillMaxWidth().background(AresBackground, RoundedCornerShape(6.dp))
                            .border(1.dp, AresBorder, RoundedCornerShape(6.dp)).padding(8.dp),
                    ) {
                        review.diff.forEach { line ->
                            val color = when (line.kind) {
                                com.ares.analytics.viewmodel.SubsystemDiffLineKind.ADDED -> AresGreen
                                com.ares.analytics.viewmodel.SubsystemDiffLineKind.REMOVED -> AresRed
                                com.ares.analytics.viewmodel.SubsystemDiffLineKind.CONTEXT -> AresTextSecondary
                            }
                            Text(line.text, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                enabled = review.canApply,
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            ) {
                Text("Apply Proposal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = AresTextSecondary)
            }
        },
    )
}

@Composable
private fun SubsystemTemplatePickerDialog(
    currentTemplate: com.areslib.subsystem.SubsystemTemplate,
    onApplyTemplate: (com.areslib.subsystem.SubsystemTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingTemplate by remember(currentTemplate) { mutableStateOf(currentTemplate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Select Subsystem Starter Template", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Choose an archetype to preview. Applying it replaces the current draft, but you can immediately Undo after closing this dialog.",
                    color = AresTextSecondary,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.ares.analytics.viewmodel.subsystemTemplateOptions.groupBy { it.category }.forEach { (category, options) ->
                    Text(category.uppercase(), color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    options.forEach { tplOption ->
                    val isSelected = pendingTemplate == tplOption.template
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) AresCyan else AresBorder,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                pendingTemplate = tplOption.template
                            },
                        color = if (isSelected) AresCyan.copy(alpha = 0.08f) else AresSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tplOption.label,
                                    color = if (isSelected) AresCyan else AresTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    tplOption.description,
                                    color = AresTextSecondary,
                                    fontSize = 11.sp,
                                )
                                if (tplOption.beginnerRecommended) {
                                    Text("Recommended starting point", color = AresGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (isSelected) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AresCyan.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "ACTIVE",
                                        color = AresCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplyTemplate(pendingTemplate) },
                enabled = pendingTemplate != currentTemplate,
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            ) {
                Text("Replace draft with selected starter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AresTextSecondary)
            }
        },
    )
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
