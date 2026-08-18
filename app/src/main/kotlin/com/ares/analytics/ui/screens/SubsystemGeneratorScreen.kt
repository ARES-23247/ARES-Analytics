package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import com.ares.analytics.ui.components.core.AresInspectorDrawer
import com.ares.analytics.ui.components.core.AresSpecRow
import com.ares.analytics.ui.components.core.AresSpecSection
import com.ares.analytics.ui.components.core.AresSpecSummaryModal
import com.areslib.subsystem.SubsystemDocument
import com.areslib.subsystem.SubsystemTemplate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.AresGenerationPhase
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGold
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary
import com.ares.analytics.viewmodel.SubsystemGeneratorState
import com.ares.analytics.viewmodel.SubsystemGeneratorViewModel
import com.ares.analytics.viewmodel.SubsystemBuilderStage
import com.ares.analytics.viewmodel.SubsystemDiffLineKind
import com.ares.analytics.viewmodel.SubsystemFileChange
import com.ares.analytics.viewmodel.SubsystemPreviewFile
import com.ares.analytics.viewmodel.SubsystemProblemSeverity
import com.ares.analytics.viewmodel.SubsystemTuningAuthoring
import com.ares.analytics.viewmodel.subsystemTemplateOptions
import com.areslib.codegen.GeneratedSubsystemSourceSet
import com.areslib.codegen.SubsystemArtifactGroup
import com.areslib.codegen.SubsystemArtifactOwnership
import com.areslib.subsystem.FaultRecoveryActionKind
import com.areslib.subsystem.InterlockComparison
import com.areslib.subsystem.SubsystemControlLoopDocument
import com.areslib.subsystem.SubsystemControlStrategy
import com.areslib.subsystem.SubsystemFieldRole
import com.areslib.subsystem.SubsystemFeedforwardKind
import com.areslib.subsystem.SubsystemFollowerTransform
import com.areslib.subsystem.SubsystemHardwareConnection
import com.areslib.subsystem.SubsystemHardwareDocument
import com.areslib.subsystem.SubsystemHardwareKind
import com.areslib.subsystem.SubsystemHomingComparison
import com.areslib.subsystem.SubsystemHomingEvidenceDocument
import com.areslib.subsystem.SubsystemHomingMethod
import com.areslib.subsystem.SubsystemImplementationKind
import com.areslib.subsystem.SubsystemMeasurementSource
import com.areslib.subsystem.SubsystemMeasurementDocument
import com.areslib.subsystem.SubsystemPlatform
import com.areslib.subsystem.SubsystemStateFieldDocument
import com.areslib.subsystem.SubsystemSimulationSupport
import com.areslib.subsystem.SubsystemTeachingLevel
import com.areslib.subsystem.SubsystemValueType
import com.areslib.subsystem.compatibleMeasurementSources
import com.areslib.subsystem.valueType
import com.areslib.tuning.TuningApplyPolicy
import com.areslib.tuning.TuningParameterDeclaration
import com.areslib.tuning.TuningParameterType
import com.areslib.tuning.TuningValue

/** Visual editor for project-backed subsystem DSL documents and their generated Kotlin. */
@Composable
fun SubsystemGeneratorScreen(
    viewModel: SubsystemGeneratorViewModel,
    onContinueToPortMap: (() -> Unit)? = null,
    onBackToDrivetrain: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    var workspaceTab by remember { mutableStateOf(0) }
    var confirmReload by remember { mutableStateOf(false) }
    var showSpecSummaryModal by remember { mutableStateOf(false) }
    var showAiAssistantDrawer by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubsystemHeader(
                state = state,
                viewModel = viewModel,
                onReload = {
                    if (state.dirty) confirmReload = true else viewModel.reload()
                },
                onOpenSpecSummary = { showSpecSummaryModal = true },
                onOpenAiAssistant = { showAiAssistantDrawer = true },
                onContinueToPortMap = onContinueToPortMap,
                onBackToDrivetrain = onBackToDrivetrain,
            )
            state.status?.let { StatusBanner(it, false) }
            state.generationMessage?.let {
                StatusBanner(it, state.generationPhase == AresGenerationPhase.FAILED)
            }
            val loadError = state.loadError
            if (loadError != null) {
                StatusBanner(loadError, true)
                return@Column
            }
            val draft = state.draft?.document ?: return@Column
            Box(Modifier.fillMaxWidth().weight(1f)) {
                BuilderEditor(
                    state = state,
                    viewModel = viewModel,
                    workspaceTab = workspaceTab,
                    onTabChange = { workspaceTab = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val document = state.draft?.document
        if (document != null) {
            // Slide-out Hardware Device Inspector Drawer
            document.hardware.firstOrNull { it.uid == state.selectedHardwareUid }?.let { device ->
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
            document.stateFields.firstOrNull { it.uid == state.selectedFieldUid }?.let { field ->
                AresInspectorDrawer(
                    isOpen = true,
                    title = field.displayName,
                    categoryBadge = "${field.role.name} · ${field.type.name}",
                    stableId = field.fieldId,
                    icon = Icons.Default.Memory,
                    onDismiss = { viewModel.selectField(null) },
                    onDone = { viewModel.selectField(null) },
                    onDelete = { viewModel.removeStateField(field.fieldId) },
                    deleteButtonText = "Delete State Value",
                ) {
                    StateFieldInspectorBody(field, viewModel)
                }
            }

            // Slide-out Controller Rule Inspector Drawer
            document.controlLoops.firstOrNull { it.uid == state.selectedLoopUid }?.let { loop ->
                AresInspectorDrawer(
                    isOpen = true,
                    title = loop.displayName,
                    categoryBadge = loop.strategy.name,
                    stableId = loop.actuatorId,
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
            document.tuningParameters.firstOrNull { it.uid == state.selectedTuningParameterUid }?.let { declaration ->
                AresInspectorDrawer(
                    isOpen = true,
                    title = declaration.displayName,
                    categoryBadge = declaration.type.name,
                    stableId = declaration.key,
                    icon = Icons.Default.Tune,
                    onDismiss = { viewModel.selectTuningParameter(null) },
                    onDone = { viewModel.selectTuningParameter(null) },
                    onDelete = { viewModel.removeTuningParameter(declaration.uid) },
                    deleteButtonText = "Delete Parameter",
                ) {
                    TuningParameterInspectorBody(document, declaration, viewModel)
                }
            }

            // Slide-out AI Subsystem Assistant Drawer
            AresInspectorDrawer(
                isOpen = showAiAssistantDrawer,
                title = "AI Subsystem Assistant",
                categoryBadge = "GEMINI",
                icon = Icons.Default.AutoAwesome,
                onDismiss = { showAiAssistantDrawer = false },
                width = 520.dp,
                doneButtonText = "Close",
                onDone = { showAiAssistantDrawer = false },
            ) {
                SubsystemAiAssistantContent(state, viewModel)
            }

            // At-a-Glance Spec Summary Modal
            AresSpecSummaryModal(
                isOpen = showSpecSummaryModal,
                title = "${document.displayName} Spec Summary",
                subtitle = "${document.platform.name} · .ares/subsystems/${document.documentId}.aressubsystem",
                sections = generateSubsystemSpecSections(
                    document = document,
                    onSelectHardware = { uid -> showSpecSummaryModal = false; viewModel.selectHardware(uid) },
                    onSelectField = { uid -> showSpecSummaryModal = false; viewModel.selectField(uid) },
                    onSelectLoop = { uid -> showSpecSummaryModal = false; viewModel.selectLoop(uid) },
                    onSelectTuning = { uid -> showSpecSummaryModal = false; viewModel.selectTuningParameter(uid) },
                ),
                onDismiss = { showSpecSummaryModal = false },
                rawMarkdownGenerator = { generateSubsystemMarkdown(document) },
            )
        }

        if (confirmReload) {
            AlertDialog(
                onDismissRequest = { confirmReload = false },
                title = { Text("Discard unsaved subsystem changes?") },
                text = { Text("Reload restores the last saved project revision. Your current edits cannot be recovered.") },
                confirmButton = {
                    Button(onClick = { confirmReload = false; viewModel.reload() }) { Text("Discard and reload") }
                },
                dismissButton = { OutlinedButton(onClick = { confirmReload = false }) { Text("Keep editing") } },
            )
        }
        if (state.pendingStarterReplacements.isNotEmpty()) {
            StarterReplacementDialog(
                files = state.pendingStarterReplacements,
                onConfirm = viewModel::confirmStarterReplacement,
                onDismiss = viewModel::cancelStarterReplacement,
            )
        }
        if (state.showTemplatePicker) {
            MechanismTemplatePickerDialog(
                onSelectTemplate = viewModel::newSubsystem,
                onDismiss = { viewModel.setTemplatePickerVisible(false) },
            )
        }
    }
}

@Composable
private fun MechanismTemplatePickerDialog(
    onSelectTemplate: (SubsystemTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Select Subsystem Archetype", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Choose a starting architecture template. You can customize all parameters and hardware later.", color = AresTextSecondary, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                subsystemTemplateOptions.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectTemplate(option.template) },
                        color = AresSurface,
                        border = BorderStroke(1.dp, AresBorder),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(option.label, color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AresCyan, modifier = Modifier.size(16.dp))
                            }
                            Text(option.description, color = AresTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SubsystemHeader(
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
    onReload: () -> Unit,
    onOpenSpecSummary: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onContinueToPortMap: (() -> Unit)? = null,
    onBackToDrivetrain: (() -> Unit)? = null,
) {
    val draft = state.draft?.document
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
            // Left: Name & Template Badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBackToDrivetrain != null) {
                    IconButton(onClick = onBackToDrivetrain, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Drivetrain", tint = AresTextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AresCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.3f)),
                ) {
                    Text(
                        (draft?.template?.name ?: "MECHANISM").replace("_", " "),
                        color = AresCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    draft?.displayName ?: "Subsystem",
                    color = AresTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Center: Stage Stepper Tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubsystemBuilderStage.entries.forEach { stage ->
                    val selected = stage == state.activeStage
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectStage(stage) },
                        label = {
                            Text(
                                stage.displayName,
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

            // Right: Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = viewModel::undo,
                    enabled = state.canUndo,
                    modifier = Modifier.height(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Undo", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = viewModel::redo,
                    enabled = state.canRedo,
                    modifier = Modifier.height(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Redo", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onOpenAiAssistant,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AresCyan),
                    border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.height(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(12.dp), tint = AresCyan)
                    Spacer(Modifier.width(4.dp))
                    Text("AI", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onOpenSpecSummary,
                    modifier = Modifier.height(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Default.TableChart, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Spec", fontSize = 11.sp)
                }
                Button(
                    onClick = { viewModel.save() },
                    enabled = state.canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                    modifier = Modifier.height(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = viewModel::generate,
                    enabled = state.canSave || state.canGenerate,
                    colors = ButtonDefaults.buttonColors(containerColor = AresGreen, contentColor = AresOnAccent),
                    modifier = Modifier.height(30.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Default.Build, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    val handAuthored = state.draft?.document?.implementation?.kind == SubsystemImplementationKind.HAND_AUTHORED
                    Text(
                        when {
                            state.dirty && handAuthored -> "Save & Refresh"
                            state.dirty -> "Save & Gen"
                            handAuthored -> "Refresh"
                            else -> "Generate"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AresOnAccent,
                    )
                }
                if (onContinueToPortMap != null) {
                    Button(
                        onClick = onContinueToPortMap,
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                        modifier = Modifier.height(30.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Port Map →", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderProgress(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    val hasHardware = document.hardware.isNotEmpty()
    val hasState = document.stateFields.isNotEmpty()
    val hasControl = document.controlLoops.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(7.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(7.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressLabel("1", "Purpose", true)
        ProgressLabel("2", "Hardware", hasHardware)
        ProgressLabel("3", "Behavior", hasState && (hasControl || document.hardware.none { it.kind.isActuator() }))
        ProgressLabel("4", "Safety", state.problems.none { it.path.startsWith("safety") && it.severity == SubsystemProblemSeverity.ERROR })
        Text(
            "Step ${state.activeStage.ordinal + 1} of ${SubsystemBuilderStage.entries.size}",
            color = AresTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { viewModel.selectStage(SubsystemBuilderStage.REVIEW) },
        )
    }
}

@Composable
private fun ProgressLabel(number: String, label: String, complete: Boolean) {
    Text(
        "$number. $label${if (complete) " ✓" else ""}",
        color = if (complete) AresGreen else AresTextSecondary,
        fontSize = 11.sp,
        fontWeight = if (complete) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun StageRail(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    EditorCard("Build steps", Icons.Default.Build) {
        SubsystemBuilderStage.entries.forEachIndexed { index, stage ->
            val selected = stage == state.activeStage
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) AresCyan.copy(alpha = .12f) else AresSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, if (selected) AresCyan else AresBorder, RoundedCornerShape(6.dp))
                    .clickable { viewModel.selectStage(stage) }
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "${index + 1}",
                    color = if (selected) AresOnAccent else AresTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(
                        if (selected) AresCyan else AresSurfaceElevated,
                        RoundedCornerShape(20.dp),
                    ).padding(horizontal = 7.dp, vertical = 3.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(stage.displayName, color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (selected) Text(stage.shortDescription, color = AresTextSecondary, fontSize = 11.sp, lineHeight = 12.sp)
                }
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun CurrentSubsystemSummary(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    EditorCard("At a glance", Icons.Default.Memory) {
        Text(document.displayName, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        OutlinedButton(
            onClick = { viewModel.selectStage(SubsystemBuilderStage.PURPOSE) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("Rename subsystem")
        }
        Text(
            "${document.hardware.size} devices · ${document.stateFields.size} state values · ${document.controlLoops.size} control rules",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        val errors = state.problems.count { it.severity == SubsystemProblemSeverity.ERROR }
        val warnings = state.problems.count { it.severity == SubsystemProblemSeverity.WARNING }
        Text(
            when {
                errors > 0 -> "$errors blocking issue${if (errors == 1) "" else "s"}"
                warnings > 0 -> "$warnings item${if (warnings == 1) "" else "s"} to review"
                else -> "Ready for review"
            },
            color = when {
                errors > 0 -> AresError
                warnings > 0 -> AresGold
                else -> AresGreen
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StageHeader(stage: SubsystemBuilderStage) {
    Column(
        Modifier.fillMaxWidth().background(AresSurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stage.displayName, color = AresTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(stage.shortDescription, color = AresTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun StageContent(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    when (state.activeStage) {
        SubsystemBuilderStage.PURPOSE -> {
            GeneralInspector(state, viewModel)
            if (state.draft?.document?.implementation?.kind == SubsystemImplementationKind.GENERATED_STARTER) {
                TemplateCard(state, viewModel)
            } else {
                ConceptCard(
                    title = "Existing implementation",
                    body = "This definition documents Kotlin your team already owns. ARES validates its files, classes, simulation support, and actions without generating replacement source.",
                )
            }
            RuntimeFlowCard()
        }
        SubsystemBuilderStage.HARDWARE -> HardwareStage(state, viewModel)
        SubsystemBuilderStage.STATE_AND_BEHAVIOR -> BehaviorStage(state, viewModel)
        SubsystemBuilderStage.TUNING -> TuningStage(state, viewModel)
        SubsystemBuilderStage.SAFETY -> {
            SafetyInspector(state, viewModel)
            state.draft?.document?.let { doc ->
                FaultRecoveryCard(doc, viewModel)
                InterlockMatrixCard(doc, state, viewModel)
            }
            ConceptCard(
                "Why safety is a separate step",
                "A controller decides what the mechanism wants to do. The safety contract decides whether that output is currently trustworthy and permitted.",
            )
        }
        SubsystemBuilderStage.CAPABILITIES -> CapabilityInspector(state)
        SubsystemBuilderStage.SIMULATION_AND_TESTING -> VerificationInspector(state, viewModel)
        SubsystemBuilderStage.REVIEW -> {
            ProblemsCard(state, viewModel)
            ArtifactPlan(state, viewModel)
            ConceptCard(
                "Ownership before generation",
                "ARES may refresh generated plumbing. Existing USER-OWNED source is protected, and changed GENERATED STARTER files require a reviewed diff and confirmation.",
            )
        }
    }
}

@Composable
private fun TuningStage(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    val tuningProblems = state.problems.filter { it.path.startsWith("tuningParameters") }
    if (tuningProblems.isNotEmpty()) {
        EditorCard("Fix tuning details", Icons.Default.Warning) {
            tuningProblems.forEach { problem ->
                OutlinedButton(
                    onClick = { viewModel.navigateToProblem(problem.path) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${if (problem.severity == SubsystemProblemSeverity.ERROR) "Error" else "Warning"}: ${problem.message}",
                        color = if (problem.severity == SubsystemProblemSeverity.ERROR) AresError else AresGold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
    EditorCard("Typed tuning parameters", Icons.Default.Settings) {
        Text(
            "A declaration explains what a value means and when it may change. Named robot profiles own the values; this subsystem owns their types and safety policy.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        if (document.tuningParameters.isEmpty()) {
            Text(
                "No tunable values are declared. That is valid—do not expose constants that students should not change.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
        }
        document.tuningParameters.forEach { declaration ->
            SelectableRow(
                title = declaration.displayName,
                subtitle = "${declaration.type.name.lowercase()} · ${declaration.applyPolicy.name.replace('_', ' ').lowercase()} · ${declaration.componentUid}",
                selected = state.selectedTuningParameterUid == declaration.uid,
                onClick = { viewModel.selectTuningParameter(declaration.uid) },
            )
        }
        OutlinedButton(onClick = viewModel::addTuningParameter, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add typed parameter")
        }
    }
    TuningPresetCard(document, viewModel)
    ConceptCard(
        "Profiles own values",
        "This form declares meaning, type, bounds, and apply policy. Robot-owned .arestuning profiles choose canonical values, while local experiments remain non-authoritative.",
    )
}

@Composable
private fun TuningPresetCard(document: com.areslib.subsystem.SubsystemDocument, viewModel: SubsystemGeneratorViewModel) {
    val options = document.controlLoops.flatMap { loop ->
        SubsystemTuningAuthoring.availablePresets(loop).map { loop to it }
    }
    EditorCard("Optional safe presets", Icons.Default.Add) {
        Text(
            "Presets copy only gains already present in a compatible controller. They never add a control mode, guess units, or force a parameter into the subsystem.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        if (options.isEmpty()) {
            Text("No PID or feedforward controller is configured, so there are no truthful presets to offer.", color = AresTextSecondary, fontSize = 12.sp)
        }
        options.forEach { (loop, preset) ->
            Column(
                Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(5.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("${loop.displayName}: ${preset.displayName}", color = AresTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(preset.explanation, color = AresTextSecondary, fontSize = 11.sp)
                OutlinedButton(onClick = { viewModel.applyTuningPreset(loop.uid, preset) }) {
                    Text("Add ${preset.displayName}")
                }
            }
        }
    }
}

@Composable
private fun TuningParameterInspectorBody(
    document: com.areslib.subsystem.SubsystemDocument,
    declaration: TuningParameterDeclaration,
    viewModel: SubsystemGeneratorViewModel,
) {
    val owners = listOf(document.uid) + document.hardware.map { it.uid } + document.controlLoops.map { it.uid }
    val index = document.tuningParameters.indexOfFirst { it.uid == declaration.uid }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StableIdLabel(
            "Parameter UID",
            declaration.uid,
            "Permanent identity used by profiles and runtime transport. Reordering or renaming the label does not change it.",
        )
        TextInput("Parameter key", declaration.key) { value ->
            viewModel.updateTuningParameter(declaration.uid) { it.copy(key = value) }
        }
        DropdownSelector("Owning component", declaration.componentUid, owners) { owner ->
            viewModel.updateTuningParameter(declaration.uid) { it.copy(componentUid = owner) }
        }
        TextInput("Display name", declaration.displayName) { value ->
            viewModel.updateTuningParameter(declaration.uid) { it.copy(displayName = value) }
        }
        TextInput("What this parameter changes", declaration.description, singleLine = false) { value ->
            viewModel.updateTuningParameter(declaration.uid) { it.copy(description = value) }
        }
        EnumSelector("Parameter type", declaration.type, TuningParameterType.entries) {
            viewModel.changeTuningParameterType(declaration.uid, it)
        }
        if (declaration.type == TuningParameterType.DOUBLE || declaration.type == TuningParameterType.INT) {
            TextInput("Unit (optional)", declaration.unit.orEmpty()) { raw ->
                viewModel.updateTuningParameter(declaration.uid) { it.copy(unit = raw.trim().ifEmpty { null }) }
            }
            NullableDoubleInput("Minimum (optional)", declaration.minimum) { value ->
                viewModel.updateTuningParameter(declaration.uid) { it.copy(minimum = value) }
            }
            NullableDoubleInput("Maximum (optional)", declaration.maximum) { value ->
                viewModel.updateTuningParameter(declaration.uid) { it.copy(maximum = value) }
            }
        }
        if (declaration.type == TuningParameterType.ENUM) {
            TextInput("Allowed options (comma separated)", declaration.enumOptions.joinToString(", ")) { raw ->
                val options = SubsystemTuningAuthoring.parseEnumOptions(raw)
                viewModel.updateTuningParameter(declaration.uid) { current ->
                    current.copy(
                        enumOptions = options,
                        defaultValue = current.defaultValue.takeIf { it.textValue in options }
                            ?: TuningValue(textValue = options.firstOrNull().orEmpty()),
                    )
                }
            }
        }
        TuningDefaultEditor(declaration) { value ->
            viewModel.updateTuningParameter(declaration.uid) { it.copy(defaultValue = value) }
        }
        EnumSelector("Apply policy", declaration.applyPolicy, TuningApplyPolicy.entries) {
            viewModel.updateTuningParameter(declaration.uid) { current -> current.copy(applyPolicy = it) }
        }
        Text(tuningPolicyExplanation(declaration.applyPolicy), color = AresTextSecondary, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.moveTuningParameter(declaration.uid, -1) },
                enabled = index > 0,
                modifier = Modifier.weight(1f),
            ) { Text("Move up") }
            OutlinedButton(
                onClick = { viewModel.moveTuningParameter(declaration.uid, 1) },
                enabled = index in 0 until document.tuningParameters.lastIndex,
                modifier = Modifier.weight(1f),
            ) { Text("Move down") }
        }
    }
}

@Composable
private fun TuningDefaultEditor(declaration: TuningParameterDeclaration, onChange: (TuningValue) -> Unit) {
    when (declaration.type) {
        TuningParameterType.DOUBLE -> DoubleInput("Default value", declaration.defaultValue.doubleValue ?: 0.0) {
            onChange(TuningValue(doubleValue = it))
        }
        TuningParameterType.INT -> IntInput("Default value", declaration.defaultValue.intValue ?: 0) {
            onChange(TuningValue(intValue = it))
        }
        TuningParameterType.BOOLEAN -> ToggleRow("Default value", declaration.defaultValue.booleanValue == true) {
            onChange(TuningValue(booleanValue = it))
        }
        TuningParameterType.TEXT -> TextInput("Default value", declaration.defaultValue.textValue.orEmpty()) {
            onChange(TuningValue(textValue = it))
        }
        TuningParameterType.ENUM -> {
            val options = declaration.enumOptions
            if (options.isEmpty()) {
                Text("Add at least one allowed option before choosing a default.", color = AresError, fontSize = 11.sp)
            } else {
                EnumStringSelector(
                    "Default option",
                    declaration.defaultValue.textValue?.takeIf { it in options } ?: options.first(),
                    options,
                ) { onChange(TuningValue(textValue = it)) }
            }
        }
    }
}

private fun tuningPolicyExplanation(policy: TuningApplyPolicy): String = when (policy) {
    TuningApplyPolicy.LIVE_SAFE -> "May apply only while an explicit live-tuning session is armed. Use only for values proven safe to change while enabled."
    TuningApplyPolicy.DISABLED_ONLY -> "May apply only while the tuning session is armed and the robot is disabled. Recommended for ordinary controller gains."
    TuningApplyPolicy.RESTART_REQUIRED -> "The running robot rejects this change; restart before the new value can become active."
    TuningApplyPolicy.REBUILD_REQUIRED -> "The running robot rejects this change; regenerate and rebuild the project."
    TuningApplyPolicy.CALIBRATION_ONLY -> "May apply only inside an explicitly authorized calibration session for this parameter."
    TuningApplyPolicy.READ_ONLY_VENDOR -> "Shown for understanding but never changed by ARES; the vendor-owned source remains authoritative."
}

@Composable
private fun StageNavigation(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(
            onClick = viewModel::previousStage,
            enabled = state.activeStage != SubsystemBuilderStage.PURPOSE,
        ) { Text("Back") }
        Button(
            onClick = viewModel::nextStage,
            enabled = state.activeStage != SubsystemBuilderStage.REVIEW,
        ) { Text(if (state.activeStage == SubsystemBuilderStage.SIMULATION_AND_TESTING) "Review" else "Next") }
    }
}

@Composable
private fun ConceptCard(title: String, body: String) {
    EditorCard(title, Icons.Default.Memory) {
        Text(body, color = AresTextSecondary, fontSize = 12.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun HardwareStage(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    EditorCard("Devices", Icons.Default.Settings) {
        Text(
            "Hardware names must match the Robot Controller configuration. Every read is cached once per robot loop. Select any device to edit in the slide-out inspector.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        document.hardware.forEach { device ->
            SelectableRow(
                title = device.displayName,
                subtitle = "${device.kind.name.replace('_', ' ').lowercase()} · ${device.connectionLabel(document.platform)}",
                selected = state.selectedHardwareUid == device.uid,
                onClick = { viewModel.selectHardware(device.uid) },
            )
        }
        AddHardwareButton(viewModel, "+ Add hardware")
    }
    if (document.hardware.isEmpty()) {
        ConceptCard("Start with one physical device", "Choose Add hardware, then describe what it is and how the robot configuration identifies it.")
    }
}

@Composable
private fun BehaviorStage(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    EditorCard("Immutable state", Icons.Default.Memory) {
        Text(
            "Status values describe what sensors observed. Target values describe what driver or autonomous code wants. Select any value to edit in the slide-out inspector.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        document.stateFields.forEach { field ->
            SelectableRow(
                field.displayName,
                "${field.role.name.lowercase()} · ${field.type.name.lowercase()}${field.unit?.let { " ($it)" }.orEmpty()}",
                state.selectedFieldUid == field.uid,
            ) { viewModel.selectField(field.uid) }
        }
        AddStateValueButton(viewModel, "+ Add state value")
    }

    EditorCard("Controller rules", Icons.Default.Build) {
        Text(
            "A controller converts immutable state into bounded IO commands; it does not read hardware directly. Select any rule to edit in the slide-out inspector.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        document.controlLoops.forEach { loop ->
            SelectableRow(
                loop.displayName,
                "${loop.strategy.name.replace('_', ' ').lowercase()} → ${loop.actuatorId}",
                state.selectedLoopUid == loop.uid,
            ) { viewModel.selectLoop(loop.uid) }
        }
        val canAddControl = document.hardware.any { it.kind.isActuator() } && document.stateFields.any {
            it.role == SubsystemFieldRole.TARGET &&
                (it.type == SubsystemValueType.DOUBLE || it.type == SubsystemValueType.INT)
        }
        OutlinedButton(onClick = viewModel::addControlLoop, enabled = canAddControl, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add controller rule")
        }
        if (!canAddControl) Text("Add an actuator and numeric target value first.", color = AresTextSecondary, fontSize = 12.sp)
    }

    com.ares.analytics.ui.components.linkage.LinkageEditorCanvas(
        linkage = document.linkage,
        actuatorIds = document.hardware
            .filter { it.kind == SubsystemHardwareKind.MOTOR && it.following == null }
            .map { it.hardwareId },
        angleMeasurementFieldIds = document.stateFields
            .filter { it.role == SubsystemFieldRole.MEASUREMENT && it.type == SubsystemValueType.DOUBLE }
            .map { it.fieldId },
        onLinkageChanged = { newLinkage -> viewModel.edit { it.copy(linkage = newLinkage) } },
    )
}

@Composable
private fun CapabilityInspector(state: SubsystemGeneratorState) {
    val document = state.draft?.document ?: return
    if (document.implementation.kind == SubsystemImplementationKind.HAND_AUTHORED) {
        EditorCard("Existing driver and autonomous actions", Icons.Default.Build) {
            Text(
                "These keys must exist in .ares/action-catalog.json. ARES validates them but does not generate their runtime behavior.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            if (document.capabilityActionKeys.isEmpty()) {
                Text("No action keys are registered yet.", color = AresGold, fontSize = 12.sp)
            } else document.capabilityActionKeys.forEach { key ->
                Text(
                    key,
                    color = AresCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(5.dp)).padding(8.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        RuntimeFlowCard()
        return
    }
    val targets = document.stateFields.filter { it.role == SubsystemFieldRole.TARGET }
    EditorCard("Driver and autonomous actions", Icons.Default.Build) {
        Text(
            "Each writable target becomes a typed action that appears in Controller Bindings and routine authoring.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        if (targets.isEmpty()) {
            Text("No writable target values are defined yet.", color = AresGold, fontSize = 12.sp)
        } else targets.forEach { field ->
            Column(
                Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, AresBorder, RoundedCornerShape(6.dp)).padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("Set ${document.displayName} ${field.displayName}", color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("subsystem.${document.documentId}.set.${field.fieldId}", color = AresCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("Value: ${field.type.name.lowercase()}${field.unit?.let { " · $it" }.orEmpty()}", color = AresTextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(5.dp))
        }
        Text(
            "Hand-authored implementations can declare existing catalog actions instead of generating target setters.",
            color = AresTextTertiary,
            fontSize = 11.sp,
        )
    }
    RuntimeFlowCard()
}

@Composable
private fun VerificationInspector(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    if (document.implementation.kind == SubsystemImplementationKind.HAND_AUTHORED) {
        EditorCard("Hand-authored verification", Icons.Default.Code) {
            Text(
                "Simulation support: ${document.implementation.simulation.support.name.replace('_', ' ').lowercase()}",
                color = AresTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            document.implementation.simulation.adapterClassName?.let {
                Text(it, color = AresCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                "Hand-authored tests remain USER-OWNED. Verify safe startup, stop, invalid feedback, failed writes, recovery, parity, and cleanup where applicable.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
        }
        ArtifactPlan(state, viewModel)
        return
    }
    EditorCard("Simulation and verification", Icons.Default.Code) {
        ToggleRow("Generate desktop/mock IO", document.generateMockIo) { value ->
            viewModel.edit { it.copy(generateMockIo = value) }
        }
        Text("Mock IO lets students test behavior without connecting a robot.", color = AresTextSecondary, fontSize = 11.sp)
        ToggleRow("Generate a starter contract test", document.generateTest) { value ->
            viewModel.edit { it.copy(generateTest = value) }
        }
        Text(
            "Verification covers startup, neutral output, stale inputs, write failures, recovery, cleanup, and parity where applicable.",
            color = AresTextSecondary,
            fontSize = 11.sp,
        )
        Text(
            "Periodic allocation check: ${if (document.safety.zeroAllocationPeriodic) "required" else "not requested"}",
            color = if (document.safety.zeroAllocationPeriodic) AresGreen else AresGold,
            fontSize = 11.sp,
        )
    }
    FieldInteractionCard(document, viewModel)
    ArtifactPlan(state, viewModel)
}

@Composable
private fun FieldInteractionCard(document: com.areslib.subsystem.SubsystemDocument, viewModel: SubsystemGeneratorViewModel) {
    val sim = document.implementation.simulation
    val interaction = sim.interaction
    val triggerActuators = document.hardware.filter { it.following == null && it.kind.isActuator() }

    EditorCard("Field element interaction (Dyn4j physics)", Icons.Default.Build) {
        Text(
            "Configure how this subsystem physically interacts with field elements (e.g. game piece intake or projectile launch) in the Dyn4j simulator.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )

        EnumSelector(
            "Interaction role",
            interaction.role,
            com.areslib.subsystem.SimInteractionRole.entries,
        ) { role ->
            viewModel.edit { doc ->
                val currentSim = doc.implementation.simulation
                doc.copy(
                    implementation = doc.implementation.copy(
                        simulation = currentSim.copy(
                            interaction = currentSim.interaction.copy(
                                role = role,
                                triggerActuatorId = if (role == com.areslib.subsystem.SimInteractionRole.NONE) {
                                    null
                                } else {
                                    currentSim.interaction.triggerActuatorId ?: triggerActuators.firstOrNull()?.hardwareId
                                },
                            ),
                        ),
                    ),
                )
            }
        }

        if (interaction.role != com.areslib.subsystem.SimInteractionRole.NONE) {
            if (triggerActuators.isEmpty()) {
                Text(
                    "Add an independently controlled actuator before enabling a field interaction.",
                    color = AresGold,
                    fontSize = 12.sp,
                )
            } else {
                EnumNullableSelector(
                    "Applied-output trigger actuator",
                    interaction.triggerActuatorId,
                    triggerActuators.map { it.hardwareId },
                ) { actuatorId ->
                    viewModel.edit { doc ->
                        val currentSim = doc.implementation.simulation
                        doc.copy(
                            implementation = doc.implementation.copy(
                                simulation = currentSim.copy(
                                    interaction = currentSim.interaction.copy(triggerActuatorId = actuatorId),
                                ),
                            ),
                        )
                    }
                }
                DoubleInput("Trigger when accepted output exceeds", interaction.triggerThreshold) { value ->
                    viewModel.edit { doc ->
                        val currentSim = doc.implementation.simulation
                        doc.copy(
                            implementation = doc.implementation.copy(
                                simulation = currentSim.copy(
                                    interaction = currentSim.interaction.copy(triggerThreshold = value.coerceAtLeast(0.0)),
                                ),
                            ),
                        )
                    }
                }
                Text(
                    "Simulation reads the adapter's accepted output after safety checks—not the requested target—so interlocks, stale feedback, and fault latches also stop simulated collection or launch.",
                    color = AresTextSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        when (interaction.role) {
            com.areslib.subsystem.SimInteractionRole.INTAKE_COLLECTOR -> {
                DoubleInput("Capture range (m)", interaction.intakeDistanceMeters) { value ->
                    viewModel.edit { doc ->
                        val currentSim = doc.implementation.simulation
                        doc.copy(
                            implementation = doc.implementation.copy(
                                simulation = currentSim.copy(
                                    interaction = currentSim.interaction.copy(intakeDistanceMeters = value.coerceAtLeast(0.05)),
                                ),
                            ),
                        )
                    }
                }
                DoubleInput("Capture radius (m)", interaction.captureRadiusMeters) { value ->
                    viewModel.edit { doc ->
                        val currentSim = doc.implementation.simulation
                        doc.copy(
                            implementation = doc.implementation.copy(
                                simulation = currentSim.copy(
                                    interaction = currentSim.interaction.copy(captureRadiusMeters = value.coerceAtLeast(0.05)),
                                ),
                            ),
                        )
                    }
                }
                IntInput("Storage capacity", interaction.storageCapacity) { value ->
                    viewModel.edit { doc ->
                        val currentSim = doc.implementation.simulation
                        doc.copy(
                            implementation = doc.implementation.copy(
                                simulation = currentSim.copy(
                                    interaction = currentSim.interaction.copy(storageCapacity = value.coerceAtLeast(1)),
                                ),
                            ),
                        )
                    }
                }
            }
            com.areslib.subsystem.SimInteractionRole.PROJECTILE_LAUNCHER -> {
                DoubleInput("Launch velocity (m/s)", interaction.launchSpeedMps) { value ->
                    viewModel.edit { doc ->
                        val currentSim = doc.implementation.simulation
                        doc.copy(
                            implementation = doc.implementation.copy(
                                simulation = currentSim.copy(
                                    interaction = currentSim.interaction.copy(launchSpeedMps = value.coerceAtLeast(0.5)),
                                ),
                            ),
                        )
                    }
                }
                DoubleInput("Launch elevation (deg)", interaction.launchElevationDeg) { value ->
                    viewModel.edit { doc ->
                        val currentSim = doc.implementation.simulation
                        doc.copy(
                            implementation = doc.implementation.copy(
                                simulation = currentSim.copy(
                                    interaction = currentSim.interaction.copy(launchElevationDeg = value.coerceIn(0.0, 90.0)),
                                ),
                            ),
                        )
                    }
                }
            }
            com.areslib.subsystem.SimInteractionRole.CONVEYOR_INDEXER -> {
                IntInput("Storage capacity", interaction.storageCapacity) { value ->
                    viewModel.edit { doc ->
                        val currentSim = doc.implementation.simulation
                        doc.copy(
                            implementation = doc.implementation.copy(
                                simulation = currentSim.copy(
                                    interaction = currentSim.interaction.copy(storageCapacity = value.coerceAtLeast(1)),
                                ),
                            ),
                        )
                    }
                }
            }
            com.areslib.subsystem.SimInteractionRole.NONE -> Unit
        }
    }
}

@Composable
private fun DocumentList(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    EditorCard("Project subsystems", Icons.Default.Settings) {
        state.documents.sortedBy { it.displayName.lowercase() }.forEach { document ->
            val selected = document.documentId == state.selectedDocumentId
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) AresCyan.copy(alpha = .12f) else AresSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, if (selected) AresCyan else AresBorder, RoundedCornerShape(6.dp))
                    .clickable { viewModel.selectDocument(document.documentId) }
                    .padding(9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(document.displayName, color = AresTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(document.documentId, color = AresTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("r${document.revision}", color = AresTextTertiary, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = AresCyan, modifier = Modifier.size(12.dp))
                        Text("Edit", color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedButton(onClick = { viewModel.setTemplatePickerVisible(true) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Create generated subsystem")
        }
        OutlinedButton(onClick = viewModel::registerHandAuthoredSubsystem, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Code, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Register existing Kotlin")
        }
    }
}

@Composable
private fun TemplateCard(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val selected = subsystemTemplateOptions.first { it.template == state.selectedTemplate }
    EditorCard("Capability template", Icons.Default.Build) {
        DropdownSelector(
            label = "Starting capability",
            selected = selected.label,
            options = subsystemTemplateOptions.map { it.label },
        ) { label ->
            subsystemTemplateOptions.firstOrNull { it.label == label }?.let { viewModel.selectTemplate(it.template) }
        }
        Text(selected.description, color = AresTextSecondary, fontSize = 12.sp)
        Text(
            "Templates configure behavior and safety capabilities; they never collapse architectural boundaries.",
            color = AresTextTertiary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun RuntimeFlowCard() {
    EditorCard("Runtime flow", Icons.Default.Memory) {
        Text(
            "Input → Redux action/reducer → immutable state → controller → IO contract → FTC or simulated adapter",
            color = AresCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
        Text(
            "Hardware is read once into a cached snapshot. Reducers remain pure; controllers choose outputs; adapters perform IO.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ArtifactPlan(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    if (document.implementation.kind == SubsystemImplementationKind.HAND_AUTHORED) {
        EditorCard("USER-OWNED implementation", Icons.Default.Code) {
            Text(
                "ARES preserves these files and generates only project-level plumbing and validation.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            document.implementation.sourceFiles.forEach { path ->
                Column(
                    Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(5.dp))
                        .border(1.dp, AresBorder, RoundedCornerShape(5.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(path.substringAfterLast('/'), color = AresTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("USER-OWNED · ${document.implementation.modulePath.orEmpty()}", color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(path, color = AresTextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        return
    }
    if (state.previewFiles.isEmpty()) return
    EditorCard("Artifact plan", Icons.Default.Code) {
        val starterCount = state.previewFiles.count { it.ownership == SubsystemArtifactOwnership.GENERATED_STARTER }
        val generatedCount = state.previewFiles.count { it.ownership == SubsystemArtifactOwnership.GENERATED_DO_NOT_EDIT }
        Text(
            "$starterCount customization starters · $generatedCount generated plumbing/verification files",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        SubsystemArtifactGroup.entries.forEach { group ->
            val files = state.previewFiles.filter { it.group == group }
            if (files.isEmpty()) return@forEach
            val collapsible = group == SubsystemArtifactGroup.GENERATED_PLUMBING
            val expanded = !collapsible || state.generatedPlumbingExpanded
            Row(
                Modifier.fillMaxWidth().clickable(enabled = collapsible) {
                    viewModel.setGeneratedPlumbingExpanded(!state.generatedPlumbingExpanded)
                }.padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${if (collapsible) if (expanded) "▼ " else "▶ " else ""}${group.displayName()}",
                    color = AresTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("${files.size}", color = AresTextTertiary, fontSize = 12.sp)
            }
            if (expanded) files.forEach { ArtifactRow(it) }
        }
    }
}

@Composable
private fun ArtifactRow(file: SubsystemPreviewFile) {
    val ownershipColor = when (file.ownership) {
        SubsystemArtifactOwnership.USER_OWNED -> AresCyan
        SubsystemArtifactOwnership.GENERATED_STARTER -> AresGold
        SubsystemArtifactOwnership.GENERATED_DO_NOT_EDIT -> AresTextTertiary
    }
    Column(
        Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(5.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(5.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(file.path.substringAfterLast('/'), color = AresTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(file.ownership.displayName(), color = ownershipColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(file.description, color = AresTextSecondary, fontSize = 11.sp)
        Text(file.moduleName, color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(file.projectRelativePath, color = AresTextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        if (file.change != SubsystemFileChange.UNCHANGED && file.change != SubsystemFileChange.CREATE) {
            Text(file.change.displayName(), color = if (file.change == SubsystemFileChange.PROTECTED_USER_OWNED) AresError else AresGold, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ArchitectureCard(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    EditorCard("Architecture", Icons.Default.Memory) {
        Text("SENSORS / ACTUATORS", color = AresTextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        document.hardware.forEach { device ->
            SelectableRow(
                title = device.displayName,
                subtitle = "${device.kind.name.replace('_', ' ').lowercase()} · ${device.connectionLabel(document.platform)}",
                selected = state.selectedHardwareUid == device.uid,
                onClick = { viewModel.selectHardware(device.uid) },
            )
        }
        AddHardwareButton(viewModel, "+ Hardware")

        FlowArrow("cached read")
        Text("IMMUTABLE STATE", color = AresTextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        document.stateFields.forEach { field ->
            SelectableRow(
                field.displayName,
                "${field.role.name.lowercase()} · ${field.type.name.lowercase()}${field.unit?.let { " ($it)" }.orEmpty()}",
                state.selectedFieldUid == field.uid,
            ) { viewModel.selectField(field.uid) }
        }
        AddStateValueButton(viewModel, "+ State value")

        FlowArrow("controller")
        Text("OUTPUT RULES", color = AresTextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        document.controlLoops.forEach { loop ->
            SelectableRow(
                loop.displayName,
                "${loop.strategy.name.replace('_', ' ').lowercase()} → ${loop.actuatorId}",
                state.selectedLoopUid == loop.uid,
            ) { viewModel.selectLoop(loop.uid) }
        }
        val canAddControl = document.hardware.any { it.kind.isActuator() } && document.stateFields.any {
            it.role == SubsystemFieldRole.TARGET &&
                (it.type == SubsystemValueType.DOUBLE || it.type == SubsystemValueType.INT)
        }
        OutlinedButton(
            onClick = viewModel::addControlLoop,
            modifier = Modifier.fillMaxWidth(),
            enabled = canAddControl,
        ) { Text("+ Control rule") }
        if (!canAddControl) {
            Text("Add an actuator and a numeric target state first.", color = AresTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GeneralInspector(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    EditorCard("Name and purpose", Icons.Default.Edit) {
        Text(
            "The friendly name appears in ARES. The Kotlin type name controls generated class and file names.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        TextInput("Display name", document.displayName) { value -> viewModel.edit { it.copy(displayName = value) } }
        TextInput("Kotlin type name", document.kotlinTypeName) { value -> viewModel.edit { it.copy(kotlinTypeName = value) } }
        StableIdLabel("Document ID", document.documentId, "Fixed after creation so saved revisions and generated references stay connected.")
        TextInput("Description", document.description, singleLine = false) { value -> viewModel.edit { it.copy(description = value) } }
        ToggleRow("Required at robot startup", document.requiredAtStartup) { value ->
            viewModel.edit { it.copy(requiredAtStartup = value) }
        }
        Text(
            if (document.implementation.kind == SubsystemImplementationKind.HAND_AUTHORED) {
                "Implementation: hand-authored · USER-OWNED"
            } else {
                "Implementation: generated starter"
            },
            color = if (document.implementation.kind == SubsystemImplementationKind.HAND_AUTHORED) AresCyan else AresGold,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (document.implementation.kind == SubsystemImplementationKind.HAND_AUTHORED) {
            HandAuthoredInspector(document, viewModel)
        }
        val runtimeLocation = if (document.implementation.kind == SubsystemImplementationKind.HAND_AUTHORED) {
            "Runtime class: ${document.implementation.subsystemClassName.orEmpty().ifBlank { "Not specified" }} in ${document.implementation.modulePath ?: "module not specified"}"
        } else {
            "Runtime package: ${if (document.platform == SubsystemPlatform.FTC) "org.firstinspires.ftc.teamcode" else "com.areslib.frc"}.subsystems.${document.documentId.replace('-', '_')}"
        }
        Text(
            runtimeLocation,
            color = AresTextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SafetyInspector(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    val safety = document.safety
    val homing = safety.homing
    val measurements = document.hardware.flatMap { device -> device.measurements.map { it to device } }
    val motors = document.hardware.filter { it.kind == SubsystemHardwareKind.MOTOR }.map { it.hardwareId }
    var showAdvanced by remember(document.documentId) { mutableStateOf(false) }
    EditorCard("Safety contract", Icons.Default.Warning) {
        Text(
            "Non-neutral output is permitted only while every applicable safety condition below is healthy.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        NullableLongInput("Feedback timeout (ms)", safety.feedbackTimeoutMs) { value ->
            viewModel.edit { it.copy(safety = it.safety.copy(feedbackTimeoutMs = value)) }
        }
        EnumSelector("Homing method", homing.method, SubsystemHomingMethod.entries) { viewModel.setHomingMethod(it) }
        Text(
            when (homing.method) {
                SubsystemHomingMethod.NONE -> "No physical reference is required before normal control."
                SubsystemHomingMethod.DIGITAL_SENSOR -> "Move slowly until a limit or home switch remains active."
                SubsystemHomingMethod.CURRENT_STALL -> "Move slowly until fresh motor current remains above the threshold."
                SubsystemHomingMethod.VELOCITY_STALL -> "Move slowly until fresh motor velocity remains near zero."
                SubsystemHomingMethod.CURRENT_AND_VELOCITY_STALL -> "Safest sensorless option: require high current and near-zero velocity together."
                SubsystemHomingMethod.CUSTOM_MEASUREMENT -> "Advanced: combine explicit cached measurements into a homing condition."
            },
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        if (homing.method != SubsystemHomingMethod.NONE) {
            EnumNullableSelector("Homing motor", homing.actuatorId, motors) { value ->
                viewModel.edit { document ->
                    document.copy(safety = document.safety.copy(homing = document.safety.homing.copy(actuatorId = value)))
                }
            }
            NullableDoubleInput("Homing output (motor volts)", homing.searchOutput) { value ->
                viewModel.edit { document ->
                    document.copy(safety = document.safety.copy(homing = document.safety.homing.copy(searchOutput = value)))
                }
            }
            LongInput("Evidence dwell (ms)", homing.dwellMs) { value ->
                viewModel.edit { document ->
                    document.copy(safety = document.safety.copy(homing = document.safety.homing.copy(dwellMs = value)))
                }
            }
            LongInput("Attempt timeout (ms)", homing.timeoutMs) { value ->
                viewModel.edit { document ->
                    document.copy(safety = document.safety.copy(homing = document.safety.homing.copy(timeoutMs = value)))
                }
            }
            DoubleInput("Position assigned at home", homing.zeroPosition) { value ->
                viewModel.edit { document ->
                    document.copy(safety = document.safety.copy(homing = document.safety.homing.copy(zeroPosition = value)))
                }
            }
            homing.evidence.forEachIndexed { index, evidence ->
                val source = measurements.firstOrNull { it.first.fieldId == evidence.fieldId }?.first?.source
                Column(
                    Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(5.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Evidence ${index + 1}: ${evidence.fieldId}", color = AresTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("Cached source: ${source?.name?.replace('_', ' ')?.lowercase() ?: "not selected"}", color = AresTextSecondary, fontSize = 11.sp)
                    EnumSelector("Comparison", evidence.comparison, SubsystemHomingComparison.entries) { comparison ->
                        viewModel.edit { document ->
                            document.copy(safety = document.safety.copy(homing = document.safety.homing.copy(
                                evidence = document.safety.homing.evidence.mapIndexed { i, item ->
                                    if (i == index) item.copy(
                                        comparison = comparison,
                                        threshold = item.threshold.takeUnless {
                                            comparison == SubsystemHomingComparison.TRUE || comparison == SubsystemHomingComparison.FALSE
                                        },
                                    ) else item
                                }
                            )))
                        }
                    }
                    if (evidence.comparison != SubsystemHomingComparison.TRUE && evidence.comparison != SubsystemHomingComparison.FALSE) {
                        NullableDoubleInput("Threshold", evidence.threshold) { threshold ->
                            viewModel.edit { document ->
                                document.copy(safety = document.safety.copy(homing = document.safety.homing.copy(
                                    evidence = document.safety.homing.evidence.mapIndexed { i, item ->
                                        if (i == index) item.copy(threshold = threshold) else item
                                    }
                                )))
                            }
                        }
                    }
                }
            }
            if (motors.isEmpty() || homing.evidence.isEmpty()) {
                Text(
                    "Add a motor and the required cached current, velocity, switch, or custom measurement before saving.",
                    color = AresGold,
                    fontSize = 12.sp,
                )
            }
            Text(
                "Homing never succeeds from one spike. ARES requires fresh valid evidence for the full dwell, stops at timeout, and latches failures until a neutral cancel.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            HomingConceptLab(homing)
        }
        Text(
            buildList {
                if (safety.requiresConfigurationHealth) add("configuration")
                if (safety.requiresCurrentMonitoring) add("current")
                if (safety.latchOutputFaults) add("write-fault latch")
                if (safety.telemetryEnabled) add("telemetry")
            }.ifEmpty { listOf("no additional protections") }.joinToString(" · ", prefix = "Enabled: "),
            color = AresTextSecondary,
            fontSize = 11.sp,
        )
        OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAdvanced) "Hide advanced safety settings" else "Show advanced safety settings")
        }
        if (showAdvanced) {
            ToggleRow("Require calibration", safety.requiresCalibration) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(requiresCalibration = value)) }
            }
            ToggleRow("Require configuration health", safety.requiresConfigurationHealth) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(requiresConfigurationHealth = value)) }
            }
            ToggleRow("Require valid current monitoring", safety.requiresCurrentMonitoring) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(requiresCurrentMonitoring = value)) }
            }
            ToggleRow("Latch failed output writes", safety.latchOutputFaults) { value ->
                viewModel.edit {
                    it.copy(
                        safety = it.safety.copy(
                            latchOutputFaults = value,
                            requiresExplicitNeutralRecovery = it.safety.requiresExplicitNeutralRecovery && value,
                        )
                    )
                }
            }
            ToggleRow("Require explicit successful neutral recovery", safety.requiresExplicitNeutralRecovery) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(requiresExplicitNeutralRecovery = value)) }
            }
            ToggleRow("Publish safety telemetry", safety.telemetryEnabled) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(telemetryEnabled = value)) }
            }
            ToggleRow("Zero-allocation periodic path", safety.zeroAllocationPeriodic) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(zeroAllocationPeriodic = value)) }
            }
            TextInput("Autonomous resource key (optional)", document.autonomousResourceKey.orEmpty()) { value ->
                viewModel.edit { it.copy(autonomousResourceKey = value.ifBlank { null }) }
            }
        }
    }
}

@Composable
private fun FaultRecoveryCard(
    document: com.areslib.subsystem.SubsystemDocument,
    viewModel: SubsystemGeneratorViewModel,
) {
    val recovery = document.safety.faultRecovery
    val eligibleActuators = document.hardware.filter {
        it.following == null && it.kind in setOf(SubsystemHardwareKind.MOTOR, SubsystemHardwareKind.CONTINUOUS_SERVO)
    }
    val selectedActuator = eligibleActuators.firstOrNull { it.hardwareId == recovery.actuatorId }
    val currentFields = selectedActuator?.measurements
        ?.filter { it.source == SubsystemMeasurementSource.MOTOR_CURRENT_AMPS }
        ?.map { it.fieldId }
        .orEmpty()
    val canEnable = eligibleActuators.isNotEmpty() && eligibleActuators.any { hardware ->
        hardware.measurements.any { it.source == SubsystemMeasurementSource.MOTOR_CURRENT_AMPS }
    }
    EditorCard("Auto-Recovery & Anti-Jam Policy", Icons.Default.Build) {
        Text(
            "Automatic stall protection detects mechanical jams from motor current and triggers a recovery pulse without writing code.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        if (!canEnable) {
            Text(
                "Add an independently controlled motor and a cached Motor Current Amps measurement before enabling automatic recovery.",
                color = AresGold,
                fontSize = 12.sp,
            )
        } else {
            EnumNullableSelector(
                "Motor to recover",
                recovery.actuatorId,
                eligibleActuators.map { it.hardwareId },
            ) { actuatorId ->
                val defaultCurrent = eligibleActuators.firstOrNull { it.hardwareId == actuatorId }
                    ?.measurements
                    ?.firstOrNull { it.source == SubsystemMeasurementSource.MOTOR_CURRENT_AMPS }
                    ?.fieldId
                viewModel.edit { doc ->
                    doc.copy(
                        safety = doc.safety.copy(
                            faultRecovery = doc.safety.faultRecovery.copy(
                                actuatorId = actuatorId,
                                currentFieldId = defaultCurrent,
                            ),
                        ),
                    )
                }
            }
            EnumNullableSelector(
                "Cached current measurement",
                recovery.currentFieldId,
                currentFields,
            ) { fieldId ->
                viewModel.edit { doc ->
                    doc.copy(safety = doc.safety.copy(faultRecovery = doc.safety.faultRecovery.copy(currentFieldId = fieldId)))
                }
            }
            ToggleRow("Enable auto-recovery / anti-jam", recovery.enabled) { value ->
                val defaultActuator = recovery.actuatorId ?: eligibleActuators.first { hardware ->
                    hardware.measurements.any { it.source == SubsystemMeasurementSource.MOTOR_CURRENT_AMPS }
                }.hardwareId
                val defaultCurrent = recovery.currentFieldId ?: eligibleActuators
                    .first { it.hardwareId == defaultActuator }
                    .measurements
                    .first { it.source == SubsystemMeasurementSource.MOTOR_CURRENT_AMPS }
                    .fieldId
                viewModel.edit { doc ->
                    doc.copy(
                        safety = doc.safety.copy(
                            faultRecovery = doc.safety.faultRecovery.copy(
                                enabled = value,
                                actuatorId = defaultActuator,
                                currentFieldId = defaultCurrent,
                            ),
                        ),
                    )
                }
            }
        }
        if (recovery.enabled) {
            DoubleInput("Stall current threshold (Amps)", recovery.currentThresholdAmps) { value ->
                viewModel.edit { doc ->
                    doc.copy(safety = doc.safety.copy(faultRecovery = doc.safety.faultRecovery.copy(currentThresholdAmps = value)))
                }
            }
            LongInput("Stall duration before trigger (ms)", recovery.currentDurationMs) { value ->
                viewModel.edit { doc ->
                    doc.copy(safety = doc.safety.copy(faultRecovery = doc.safety.faultRecovery.copy(currentDurationMs = value)))
                }
            }
            EnumSelector(
                "Recovery action",
                recovery.recoveryAction,
                listOf(FaultRecoveryActionKind.REVERSE_BRIEFLY, FaultRecoveryActionKind.NEUTRAL_STOP),
            ) { action ->
                viewModel.edit { doc ->
                    doc.copy(safety = doc.safety.copy(faultRecovery = doc.safety.faultRecovery.copy(recoveryAction = action)))
                }
            }
            if (recovery.recoveryAction == FaultRecoveryActionKind.REVERSE_BRIEFLY) {
                LongInput("Reverse pulse duration (ms)", recovery.reverseDurationMs) { value ->
                    viewModel.edit { doc ->
                        doc.copy(safety = doc.safety.copy(faultRecovery = doc.safety.faultRecovery.copy(reverseDurationMs = value)))
                    }
                }
                DoubleInput("Reverse duty cycle (-1.0 to 1.0)", recovery.reverseDutyCycle) { value ->
                    viewModel.edit { doc ->
                        doc.copy(safety = doc.safety.copy(faultRecovery = doc.safety.faultRecovery.copy(reverseDutyCycle = value)))
                    }
                }
            }
            LongInput("Max retry attempts", recovery.maxRetries.toLong()) { value ->
                viewModel.edit { doc ->
                    doc.copy(safety = doc.safety.copy(faultRecovery = doc.safety.faultRecovery.copy(maxRetries = value.toInt().coerceIn(1, 10))))
                }
            }
        }
    }
}

@Composable
private fun InterlockMatrixCard(
    document: com.areslib.subsystem.SubsystemDocument,
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
) {
    val targetDocuments = state.documents
        .filter { it.uid != document.uid }
        .filter { it.implementation.kind == SubsystemImplementationKind.GENERATED_STARTER }
        .filter { it.stateFields.isNotEmpty() }
        .sortedBy { it.displayName.lowercase() }
    EditorCard("Mechanism Safety Interlocks (Collision Rules)", Icons.Default.Warning) {
        Text(
            "Interlock rules prevent physical mechanism collisions by locking out unsafe actions based on other subsystem states.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        if (document.interlocks.isEmpty()) {
            Text(
                "No interlocks configured for this mechanism. Add constraints if this mechanism physically intersects another.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
        }
        document.interlocks.forEachIndexed { index, interlock ->
            val targetDocument = targetDocuments.firstOrNull { it.uid == interlock.targetSubsystemUid }
            val targetField = targetDocument?.stateFields?.firstOrNull { it.fieldId == interlock.targetFieldId }
            val comparisons = when (targetField?.type) {
                SubsystemValueType.DOUBLE, SubsystemValueType.INT -> InterlockComparison.entries
                SubsystemValueType.BOOLEAN, SubsystemValueType.STRING -> listOf(
                    InterlockComparison.EQUALS_STATE,
                    InterlockComparison.NOT_EQUALS_STATE,
                )
                null -> emptyList()
            }
            Column(
                Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(5.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Interlock ${index + 1}: ${interlock.interlockId}", color = AresTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    IconButton(onClick = { viewModel.removeInterlock(interlock.interlockId) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete interlock", tint = AresError, modifier = Modifier.size(16.dp))
                    }
                }
                EnumStringSelector(
                    "Referenced subsystem",
                    targetDocument?.let { "${it.displayName} (${it.uid})" } ?: "Select a subsystem",
                    targetDocuments.map { "${it.displayName} (${it.uid})" },
                ) { selected ->
                    val target = targetDocuments.single { "${it.displayName} (${it.uid})" == selected }
                    val field = target.stateFields.first()
                    viewModel.updateInterlock(interlock.interlockId) {
                        it.copy(
                            targetSubsystemUid = target.uid,
                            targetFieldId = field.fieldId,
                            comparison = if (field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT)) {
                                InterlockComparison.LESS_THAN
                            } else {
                                InterlockComparison.EQUALS_STATE
                            },
                            targetStateName = when (field.type) {
                                SubsystemValueType.BOOLEAN -> "false"
                                SubsystemValueType.STRING -> ""
                                else -> null
                            },
                        )
                    }
                }
                if (targetDocument != null) EnumStringSelector(
                    "Referenced state value",
                    targetField?.let { "${it.displayName} (${it.fieldId})" } ?: "Select a value",
                    targetDocument.stateFields.map { "${it.displayName} (${it.fieldId})" },
                ) { selected ->
                    val field = targetDocument.stateFields.single { "${it.displayName} (${it.fieldId})" == selected }
                    viewModel.updateInterlock(interlock.interlockId) {
                        it.copy(
                            targetFieldId = field.fieldId,
                            comparison = if (field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT)) {
                                InterlockComparison.LESS_THAN
                            } else {
                                InterlockComparison.EQUALS_STATE
                            },
                            targetStateName = when (field.type) {
                                SubsystemValueType.BOOLEAN -> "false"
                                SubsystemValueType.STRING -> ""
                                else -> null
                            },
                        )
                    }
                }
                if (comparisons.isNotEmpty()) EnumSelector("Lockout condition", interlock.comparison, comparisons) { comp ->
                    viewModel.updateInterlock(interlock.interlockId) { it.copy(comparison = comp) }
                }
                when (targetField?.type) {
                    SubsystemValueType.DOUBLE, SubsystemValueType.INT -> DoubleInput("Unsafe comparison value", interlock.thresholdValue) { value ->
                        viewModel.updateInterlock(interlock.interlockId) { it.copy(thresholdValue = value) }
                    }
                    SubsystemValueType.BOOLEAN -> EnumStringSelector(
                        "Unsafe state",
                        interlock.targetStateName ?: "false",
                        listOf("false", "true"),
                    ) { value ->
                        viewModel.updateInterlock(interlock.interlockId) { it.copy(targetStateName = value) }
                    }
                    SubsystemValueType.STRING -> TextInput("Unsafe state value", interlock.targetStateName.orEmpty()) { value ->
                        viewModel.updateInterlock(interlock.interlockId) { it.copy(targetStateName = value) }
                    }
                    null -> Text("Choose a referenced subsystem and state value.", color = AresGold, fontSize = 12.sp)
                }
                TextInput("Constraint Description", interlock.forbiddenZoneDescription) { value ->
                    viewModel.updateInterlock(interlock.interlockId) { it.copy(forbiddenZoneDescription = value) }
                }
            }
        }
        OutlinedButton(
            onClick = viewModel::addInterlock,
            enabled = targetDocuments.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Add Mechanism Interlock Rule")
        }
        if (targetDocuments.isEmpty()) {
            Text("Create another generated subsystem with state values before adding a cross-mechanism interlock.", color = AresGold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HandAuthoredInspector(
    document: com.areslib.subsystem.SubsystemDocument,
    viewModel: SubsystemGeneratorViewModel,
) {
    val implementation = document.implementation
    Text(
        "ARES reads this explicit contract; it never scans or overwrites the Kotlin files.",
        color = AresTextSecondary,
        fontSize = 12.sp,
    )
    TextInput("Gradle module", implementation.modulePath.orEmpty()) { value ->
        viewModel.edit { it.copy(implementation = it.implementation.copy(modulePath = value.ifBlank { null })) }
    }
    TextInput("Subsystem class", implementation.subsystemClassName.orEmpty()) { value ->
        viewModel.edit { it.copy(implementation = it.implementation.copy(subsystemClassName = value.ifBlank { null })) }
    }
    TextInput("IO contract class", implementation.ioContractClassName.orEmpty()) { value ->
        viewModel.edit { it.copy(implementation = it.implementation.copy(ioContractClassName = value.ifBlank { null })) }
    }
    TextInput("Hardware adapter class", implementation.hardwareAdapterClassName.orEmpty()) { value ->
        viewModel.edit { it.copy(implementation = it.implementation.copy(hardwareAdapterClassName = value.ifBlank { null })) }
    }
    TextInput("USER-OWNED source files (one per line)", implementation.sourceFiles.joinToString("\n"), singleLine = false) { value ->
        viewModel.edit {
            it.copy(
                implementation = it.implementation.copy(
                    sourceFiles = value.lineSequence().map(String::trim).filter(String::isNotBlank).toList(),
                )
            )
        }
    }
    EnumSelector("Simulation support", implementation.simulation.support, SubsystemSimulationSupport.entries) { support ->
        viewModel.edit {
            it.copy(
                implementation = it.implementation.copy(
                    simulation = it.implementation.simulation.copy(
                        support = support,
                        adapterClassName = it.implementation.simulation.adapterClassName.takeUnless {
                            support == SubsystemSimulationSupport.UNAVAILABLE
                        },
                    )
                )
            )
        }
    }
    if (implementation.simulation.support != SubsystemSimulationSupport.UNAVAILABLE) {
        TextInput("Simulation/mock adapter class", implementation.simulation.adapterClassName.orEmpty()) { value ->
            viewModel.edit {
                it.copy(
                    implementation = it.implementation.copy(
                        simulation = it.implementation.simulation.copy(adapterClassName = value.ifBlank { null }),
                    )
                )
            }
        }
    }
    EnumSelector("Teaching level", implementation.teaching.level, SubsystemTeachingLevel.entries) { level ->
        viewModel.edit {
            it.copy(implementation = it.implementation.copy(teaching = it.implementation.teaching.copy(level = level)))
        }
    }
    TextInput("What students learn", implementation.teaching.summary, singleLine = false) { value ->
        viewModel.edit {
            it.copy(implementation = it.implementation.copy(teaching = it.implementation.teaching.copy(summary = value)))
        }
    }
    TextInput("Existing action keys (one per line)", document.capabilityActionKeys.joinToString("\n"), singleLine = false) { value ->
        viewModel.edit {
            it.copy(capabilityActionKeys = value.lineSequence().map(String::trim).filter(String::isNotBlank).toList())
        }
    }
}

@Composable
private fun HardwareInspectorBody(
    state: SubsystemGeneratorState,
    device: SubsystemHardwareDocument,
    viewModel: SubsystemGeneratorViewModel,
) {
    val document = state.draft?.document ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Use the name field below to rename what students see, then configure its robot connection.", color = AresTextSecondary, fontSize = 12.sp)
        TextInput("Hardware name", device.displayName) { value ->
            viewModel.updateHardware(device.hardwareId) { it.copy(displayName = value) }
        }
        StableIdLabel("Code ID", device.hardwareId, "Used by controller rules and generated Kotlin. Advanced renames update every known reference together.")
        TextInput("Rename code ID (advanced)", device.hardwareId) { value ->
            viewModel.renameHardwareId(device.hardwareId, value)
        }
        EnumSelector("Type", device.kind, SubsystemHardwareKind.entries) { kind ->
            viewModel.changeHardwareKind(device.hardwareId, kind)
        }
        Text(
            "ARES scaffolds the natural state and cached inputs for this device. They stay explicit in the saved descriptor and can be extended below.",
            color = AresTextSecondary,
            fontSize = 12.sp,
        )
        if (device.kind.isActuator()) {
            val eligibleLeaders = document.hardware.filter {
                it.hardwareId != device.hardwareId && it.kind == device.kind && it.following == null
            }
            val independentLabel = "Independent (has its own controller)"
            val leaderLabels = eligibleLeaders.associateBy { "Follow ${it.displayName} (${it.hardwareId})" }
            val selectedLeader = device.following?.leaderId?.let { leaderId ->
                leaderLabels.entries.firstOrNull { it.value.hardwareId == leaderId }?.key
            } ?: independentLabel
            DropdownSelector(
                label = "Command source",
                selected = selectedLeader,
                options = listOf(independentLabel) + leaderLabels.keys,
            ) { label ->
                viewModel.setHardwareFollower(device.hardwareId, leaderLabels[label]?.hardwareId)
            }
            device.following?.let { following ->
                val transforms = if (device.kind == SubsystemHardwareKind.POSITIONAL_SERVO) {
                    listOf(SubsystemFollowerTransform.SAME_DIRECTION, SubsystemFollowerTransform.MIRRORED_POSITION)
                } else {
                    listOf(SubsystemFollowerTransform.SAME_DIRECTION, SubsystemFollowerTransform.INVERTED)
                }
                EnumSelector("Follower direction", following.transform, transforms) { transform ->
                    viewModel.setHardwareFollower(device.hardwareId, following.leaderId, transform)
                }
                Text(
                    "This actuator receives the leader command and cannot own a competing controller rule. Follower direction is applied first; hardware reversal is applied afterward. Neutral and write faults still apply to both devices.",
                    color = AresTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        when (document.platform) {
            SubsystemPlatform.FTC -> TextInput("Hardware-map name", device.connection.hardwareMapName.orEmpty()) { value ->
                viewModel.updateHardware(device.hardwareId) {
                    it.copy(connection = SubsystemHardwareConnection(hardwareMapName = value))
                }
            }
            SubsystemPlatform.FRC -> if (device.kind == SubsystemHardwareKind.MOTOR) {
                IntInput("CAN ID", device.connection.canId ?: 0) { value ->
                    viewModel.updateHardware(device.hardwareId) { it.copy(connection = it.connection.copy(canId = value, channel = null)) }
                }
                TextInput("CAN bus", device.connection.canBus) { value ->
                    viewModel.updateHardware(device.hardwareId) { it.copy(connection = it.connection.copy(canBus = value)) }
                }
            } else {
                IntInput("Channel", device.connection.channel ?: 0) { value ->
                    viewModel.updateHardware(device.hardwareId) { it.copy(connection = it.connection.copy(channel = value, canId = null)) }
                }
            }
        }
        val measurementSources = device.kind.compatibleMeasurementSources()
        if (measurementSources.isNotEmpty()) {
            Text("CACHED INPUT SNAPSHOT", color = AresTextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            device.measurements.forEachIndexed { index, measurement ->
                CachedMeasurementEditor(document, device, index, measurement, measurementSources, viewModel)
            }
            val available = measurementSources.firstOrNull { source ->
                document.stateFields.any { field ->
                    field.fieldId !in device.measurements.map { it.fieldId } &&
                        (field.role == SubsystemFieldRole.MEASUREMENT || field.role == SubsystemFieldRole.STATUS) &&
                        field.type == source.valueType()
                }
            }
            OutlinedButton(
                onClick = {
                    val source = available ?: return@OutlinedButton
                    val field = document.stateFields.first {
                        it.fieldId !in device.measurements.map { measurement -> measurement.fieldId } &&
                            (it.role == SubsystemFieldRole.MEASUREMENT || it.role == SubsystemFieldRole.STATUS) &&
                            it.type == source.valueType()
                    }
                    viewModel.updateHardware(device.hardwareId) {
                        it.copy(measurements = it.measurements + SubsystemMeasurementDocument(field.fieldId, source))
                    }
                },
                enabled = available != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Cached measurement") }
            Text("Each hardware signal is read once per loop into the reusable snapshot.", color = AresTextSecondary, fontSize = 12.sp)
        }
        if (device.kind == SubsystemHardwareKind.MOTOR && document.platform == SubsystemPlatform.FRC) {
            NullableDoubleInput("Current limit (A)", device.currentLimitAmps) { value ->
                viewModel.updateHardware(device.hardwareId) { it.copy(currentLimitAmps = value) }
            }
        }
        ToggleRow("Required hardware", device.required) { value ->
            viewModel.updateHardware(device.hardwareId) { it.copy(required = value) }
        }
        if (device.kind.isActuator()) {
            DoubleInput("Safe neutral output", device.safeOutput ?: 0.0) { value ->
                viewModel.updateHardware(device.hardwareId) { it.copy(safeOutput = value) }
            }
            ToggleRow("Reverse hardware direction", device.inverted) { value ->
                viewModel.updateHardware(device.hardwareId) { it.copy(inverted = value) }
            }
            Text(
                "Use this when the device is physically mounted in the opposite direction. This is separate from a follower's Same, Opposite, or Mirrored relationship.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CachedMeasurementEditor(
    document: com.areslib.subsystem.SubsystemDocument,
    device: SubsystemHardwareDocument,
    index: Int,
    measurement: SubsystemMeasurementDocument,
    sources: List<SubsystemMeasurementSource>,
    viewModel: SubsystemGeneratorViewModel,
) {
    Column(
        Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(5.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EnumSelector("Hardware signal", measurement.source, sources) { source ->
            val compatible = document.stateFields.firstOrNull {
                (it.role == SubsystemFieldRole.MEASUREMENT || it.role == SubsystemFieldRole.STATUS) &&
                    it.type == source.valueType() && it.fieldId !in device.measurements.filterIndexed { i, _ -> i != index }.map { it.fieldId }
            }
            viewModel.updateHardware(device.hardwareId) { hardware ->
                hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                    if (i == index) current.copy(
                        source = source,
                        fieldId = compatible?.fieldId ?: current.fieldId,
                        scale = if (source.valueType() == SubsystemValueType.DOUBLE) current.scale else 1.0,
                        offset = if (source.valueType() == SubsystemValueType.DOUBLE) current.offset else 0.0,
                    ) else current
                })
            }
        }
        val fieldOptions = document.stateFields.filter {
            (it.role == SubsystemFieldRole.MEASUREMENT || it.role == SubsystemFieldRole.STATUS) &&
                it.type == measurement.source.valueType() &&
                it.fieldId !in device.measurements.filterIndexed { i, _ -> i != index }.map { existing -> existing.fieldId }
        }.map { it.fieldId }
        EnumStringSelector("Snapshot field", measurement.fieldId, fieldOptions) { fieldId ->
            viewModel.updateHardware(device.hardwareId) { hardware ->
                hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                    if (i == index) current.copy(fieldId = fieldId) else current
                })
            }
        }
        if (measurement.source.valueType() == SubsystemValueType.DOUBLE) {
            DoubleInput("Scale", measurement.scale) { value ->
                viewModel.updateHardware(device.hardwareId) { hardware ->
                    hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                        if (i == index) current.copy(scale = value) else current
                    })
                }
            }
            DoubleInput("Offset", measurement.offset) { value ->
                viewModel.updateHardware(device.hardwareId) { hardware ->
                    hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                        if (i == index) current.copy(offset = value) else current
                    })
                }
            }
            NullableLongInput("Freshness limit (ms)", measurement.maxAgeMs) { value ->
                viewModel.updateHardware(device.hardwareId) { hardware ->
                    hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                        if (i == index) current.copy(maxAgeMs = value) else current
                    })
                }
            }
            NullableDoubleInput("Valid minimum", measurement.validMinimum) { value ->
                viewModel.updateHardware(device.hardwareId) { hardware ->
                    hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                        if (i == index) current.copy(validMinimum = value) else current
                    })
                }
            }
            NullableDoubleInput("Valid maximum", measurement.validMaximum) { value ->
                viewModel.updateHardware(device.hardwareId) { hardware ->
                    hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                        if (i == index) current.copy(validMaximum = value) else current
                    })
                }
            }
        }
        OutlinedButton(
            onClick = {
                viewModel.updateHardware(device.hardwareId) { hardware ->
                    hardware.copy(measurements = hardware.measurements.filterIndexed { i, _ -> i != index })
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Remove cached measurement", color = AresError) }
    }
}

@Composable
private fun StateFieldInspectorBody(field: SubsystemStateFieldDocument, viewModel: SubsystemGeneratorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Use the name field below to rename what students see in the Builder and generated documentation.", color = AresTextSecondary, fontSize = 12.sp)
        TextInput("State value name", field.displayName) { value ->
            viewModel.updateStateField(field.fieldId) { it.copy(displayName = value) }
        }
        TextInput("What this value means", field.description, singleLine = false) { value ->
            viewModel.updateStateField(field.fieldId) { it.copy(description = value) }
        }
        StableIdLabel("Code ID", field.fieldId, "Used by cached inputs, controller rules, and action keys. Advanced renames update known references together.")
        TextInput("Rename code ID (advanced)", field.fieldId) { value ->
            viewModel.renameStateFieldId(field.fieldId, value)
        }
        EnumSelector("Role", field.role, SubsystemFieldRole.entries) { role ->
            viewModel.updateStateField(field.fieldId) { it.copy(role = role) }
        }
        EnumSelector("Type", field.type, SubsystemValueType.entries) { type ->
            viewModel.updateStateField(field.fieldId) { current -> current.withType(type) }
        }
        when (field.type) {
            SubsystemValueType.DOUBLE -> DoubleInput("Default", field.defaultNumber ?: 0.0) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(defaultNumber = value) }
            }
            SubsystemValueType.BOOLEAN -> ToggleRow("Default", field.defaultBoolean ?: false) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(defaultBoolean = value) }
            }
            SubsystemValueType.INT -> IntInput("Default", field.defaultInt ?: 0) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(defaultInt = value) }
            }
            SubsystemValueType.STRING -> TextInput("Default", field.defaultText.orEmpty()) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(defaultText = value) }
            }
        }
        if (field.type == SubsystemValueType.DOUBLE || field.type == SubsystemValueType.INT) {
            TextInput("Unit (optional)", field.unit.orEmpty()) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(unit = value.ifBlank { null }) }
            }
            NullableDoubleInput("Minimum", field.minimum) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(minimum = value) }
            }
            NullableDoubleInput("Maximum", field.maximum) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(maximum = value) }
            }
        }
    }
}

@Composable
private fun ControlInspectorBody(
    state: SubsystemGeneratorState,
    loop: SubsystemControlLoopDocument,
    viewModel: SubsystemGeneratorViewModel,
) {
    val document = state.draft?.document ?: return
    val actuators = document.hardware.filter { it.kind.isActuator() }.map { it.hardwareId }
    val numericFields = document.stateFields.filter { it.type == SubsystemValueType.DOUBLE || it.type == SubsystemValueType.INT }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Use the name field below to rename what students see, then choose how the target drives the actuator.", color = AresTextSecondary, fontSize = 12.sp)
        TextInput("Controller rule name", loop.displayName) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(displayName = value) }
        }
        StableIdLabel("Code ID", loop.loopId, "Used by generated controller code; kept stable to preserve references.")
        EnumSelector("Strategy", loop.strategy, SubsystemControlStrategy.entries) { strategy ->
            viewModel.updateControlLoop(loop.loopId) {
                it.copy(strategy = strategy, measurementFieldId = it.measurementFieldId.takeIf { strategy.requiresMeasurement() })
            }
        }
        EnumStringSelector("Actuator", loop.actuatorId, actuators) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(actuatorId = value) }
        }
        EnumStringSelector("Target state", loop.targetFieldId, numericFields.map { it.fieldId }) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(targetFieldId = value) }
        }
        if (loop.strategy.requiresMeasurement()) {
            EnumNullableSelector(
                "Measurement state", loop.measurementFieldId,
                numericFields.filter { it.role == SubsystemFieldRole.MEASUREMENT }.map { it.fieldId },
            ) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(measurementFieldId = value) } }
        }
        if (loop.strategy == SubsystemControlStrategy.POSITION_PID || loop.strategy == SubsystemControlStrategy.VELOCITY_PID) {
            Text("FEEDBACK (PID)", color = AresTextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            DoubleInput("kP", loop.kP) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kP = value) } }
            DoubleInput("kI", loop.kI) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kI = value) } }
            DoubleInput("kD", loop.kD) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kD = value) } }
            DoubleInput("Derivative filter (seconds)", loop.derivativeFilterTimeConstantSeconds) { value ->
                viewModel.updateControlLoop(loop.loopId) {
                    it.copy(derivativeFilterTimeConstantSeconds = value)
                }
            }
            Text(
                "The generator filters sensor noise and prevents integral windup when output is saturated.",
                color = AresTextSecondary,
                fontSize = 11.sp,
            )
            Text("FEEDFORWARD", color = AresTextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            EnumSelector(
                "Feedforward model",
                loop.feedforward.kind,
                SubsystemFeedforwardKind.entries.filter { it != SubsystemFeedforwardKind.FOUR_BAR_LINKAGE },
            ) { kind ->
                viewModel.updateControlLoop(loop.loopId) {
                    it.copy(feedforward = if (kind == SubsystemFeedforwardKind.NONE) {
                        com.areslib.subsystem.SubsystemFeedforwardDocument()
                    } else it.feedforward.copy(kind = kind))
                }
                if (kind == SubsystemFeedforwardKind.TWO_DOF_ARM && !document.linkage.enabled) {
                    viewModel.edit { it.copy(linkage = it.linkage.copy(enabled = true)) }
                }
            }
            Text(
                when (loop.feedforward.kind) {
                    SubsystemFeedforwardKind.NONE -> "Feedback corrects error without a predictive motor model."
                    SubsystemFeedforwardKind.SIMPLE_MOTOR -> "kS overcomes static friction; kV and kA predict velocity and acceleration effort."
                    SubsystemFeedforwardKind.ELEVATOR -> "Motor feedforward plus constant kG to oppose gravity."
                    SubsystemFeedforwardKind.ARM -> "Motor feedforward plus kG × cos(angle) for an arm measured in radians."
                    SubsystemFeedforwardKind.FOUR_BAR_LINKAGE -> "Closed-chain four-bars require an advanced hand-authored controller and are not generated."
                    SubsystemFeedforwardKind.TWO_DOF_ARM -> "2-joint serial arm gravity compensation uses both joint angles, masses, centers of mass, and link lengths."
                },
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            if (loop.feedforward.kind != SubsystemFeedforwardKind.NONE) {
                DoubleInput("kS · static friction (V)", loop.feedforward.kS) { value ->
                    viewModel.updateControlLoop(loop.loopId) { it.copy(feedforward = it.feedforward.copy(kS = value)) }
                }
                DoubleInput("kV · velocity gain", loop.feedforward.kV) { value ->
                    viewModel.updateControlLoop(loop.loopId) { it.copy(feedforward = it.feedforward.copy(kV = value)) }
                }
                DoubleInput("kA · acceleration gain", loop.feedforward.kA) { value ->
                    viewModel.updateControlLoop(loop.loopId) { it.copy(feedforward = it.feedforward.copy(kA = value)) }
                }
                if (loop.feedforward.kind in setOf(
                        SubsystemFeedforwardKind.ELEVATOR,
                        SubsystemFeedforwardKind.ARM,
                        SubsystemFeedforwardKind.TWO_DOF_ARM,
                    )
                ) {
                    DoubleInput(
                        if (loop.feedforward.kind == SubsystemFeedforwardKind.TWO_DOF_ARM) {
                            "kG · torque-to-output scale (V/N·m)"
                        } else {
                            "kG · gravity compensation (V)"
                        },
                        loop.feedforward.kG,
                    ) { value ->
                        viewModel.updateControlLoop(loop.loopId) { it.copy(feedforward = it.feedforward.copy(kG = value)) }
                    }
                }
                EnumNullableSelector(
                    "Desired velocity state (optional)",
                    loop.feedforward.velocityFieldId,
                    numericFields.map { it.fieldId },
                ) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(feedforward = it.feedforward.copy(velocityFieldId = value)) } }
                EnumNullableSelector(
                    "Desired acceleration state (optional)",
                    loop.feedforward.accelerationFieldId,
                    numericFields.map { it.fieldId },
                ) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(feedforward = it.feedforward.copy(accelerationFieldId = value)) } }
                if (loop.feedforward.kind == SubsystemFeedforwardKind.ARM) {
                    EnumNullableSelector(
                        "Arm angle measurement (radians)",
                        loop.feedforward.gravityAngleFieldId,
                        numericFields.filter { it.role == SubsystemFieldRole.MEASUREMENT }.map { it.fieldId },
                    ) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(feedforward = it.feedforward.copy(gravityAngleFieldId = value)) } }
                }
                if (loop.feedforward.kind == SubsystemFeedforwardKind.TWO_DOF_ARM) {
                    EnumStringSelector(
                        "Controlled linkage joint",
                        loop.feedforward.linkageJoint?.toString() ?: "Select joint",
                        listOf("1", "2"),
                    ) { value ->
                        viewModel.updateControlLoop(loop.loopId) {
                            it.copy(feedforward = it.feedforward.copy(linkageJoint = value.toInt()))
                        }
                    }
                    Text(
                        "The selected controller must command the same motor assigned to that joint in the 2-joint linkage card above.",
                        color = AresTextSecondary,
                        fontSize = 11.sp,
                    )
                }
                FeedforwardConceptLab(loop)
            }
        }
        ControlTheorySandboxLab(
            loop = loop,
            onApplyGains = { kp, ki, kd, ks, kv, kg ->
                viewModel.applyControlLoopGains(loop.loopId, kp, ki, kd, ks, kv, kg)
            }
        )
        if (loop.strategy.requiresMeasurement()) {
            DoubleInput("Tolerance", loop.tolerance) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(tolerance = value) } }
        }
        DoubleInput("Minimum output", loop.minimumOutput) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(minimumOutput = value) }
        }
        DoubleInput("Maximum output", loop.maximumOutput) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(maximumOutput = value) }
        }
    }
}

@Composable
private fun ProblemsCard(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    if (state.problems.isEmpty()) return
    EditorCard("Checks", Icons.Default.Warning) {
        state.problems.forEach { problem ->
            Column(
                Modifier.fillMaxWidth().clickable { viewModel.navigateToProblem(problem.path) }
                    .background(
                        if (problem.severity == SubsystemProblemSeverity.ERROR) AresError.copy(alpha = .09f) else AresGold.copy(alpha = .09f),
                        RoundedCornerShape(5.dp),
                    )
                    .padding(8.dp),
            ) {
                Text("Open field", color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(problem.path, color = AresTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text(
                    problem.message,
                    color = if (problem.severity == SubsystemProblemSeverity.ERROR) AresError else AresGold,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun CodePreview(state: SubsystemGeneratorState, modifier: Modifier) {
    var selectedPath by remember(state.draft?.document?.documentId, state.previewFiles) {
        mutableStateOf(state.previewFiles.firstOrNull()?.path)
    }
    val selected = state.previewFiles.firstOrNull { it.path == selectedPath } ?: state.previewFiles.firstOrNull()
    Card(modifier, colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, AresBorder)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = AresCyan, modifier = Modifier.size(17.dp))
                    Text("Generated DSL + runtime", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                }
                Text("${state.previewFiles.size} files", color = AresTextSecondary, fontSize = 12.sp)
            }
            if (state.previewFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Fix validation errors to preview generated Kotlin.", color = AresTextTertiary)
                }
            } else {
                DropdownSelector(
                    label = "Preview file",
                    selected = selected?.path.orEmpty(),
                    options = state.previewFiles.map { it.path },
                    onSelected = { selectedPath = it },
                )
                Text(
                    if (selected?.sourceSet == GeneratedSubsystemSourceSet.TEST) "TEST SOURCE" else "ROBOT SOURCE",
                    color = if (selected?.sourceSet == GeneratedSubsystemSourceSet.TEST) AresGold else AresGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    Modifier.fillMaxSize().background(AresBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                        .verticalScroll(rememberScrollState()).padding(10.dp),
                ) {
                    Text(
                        selected?.content.orEmpty(),
                        color = AresTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StarterReplacementDialog(
    files: List<SubsystemPreviewFile>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPath by remember(files) { mutableStateOf(files.first().path) }
    val selected = files.firstOrNull { it.path == selectedPath } ?: files.first()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace an existing generated starter?") },
        text = {
            Column(
                Modifier.fillMaxWidth().height(430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Generated starters are customization points. Confirming replaces the existing content shown in red with the proposed content shown in green.",
                    color = AresTextSecondary,
                    fontSize = 11.sp,
                )
                DropdownSelector(
                    "Starter with changes",
                    selected.path,
                    files.map { it.path },
                ) { selectedPath = it }
                Text(selected.projectRelativePath, color = AresTextTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Box(
                    Modifier.fillMaxSize().background(AresBackground, RoundedCornerShape(5.dp))
                        .border(1.dp, AresBorder, RoundedCornerShape(5.dp))
                        .verticalScroll(rememberScrollState()).padding(8.dp),
                ) {
                    Column {
                        selected.diff.forEach { line ->
                            val prefix = when (line.kind) {
                                SubsystemDiffLineKind.CONTEXT -> "  "
                                SubsystemDiffLineKind.ADDED -> "+ "
                                SubsystemDiffLineKind.REMOVED -> "- "
                            }
                            val color = when (line.kind) {
                                SubsystemDiffLineKind.CONTEXT -> AresTextSecondary
                                SubsystemDiffLineKind.ADDED -> AresGreen
                                SubsystemDiffLineKind.REMOVED -> AresError
                            }
                            Text(prefix + line.text, color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Replace shown starters") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Keep existing files") } },
    )
}

@Composable
private fun EditorCard(
    title: String,
    @Suppress("UNUSED_PARAMETER") icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, AresBorder)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                subsystemConceptExplanation(title)?.let { explanation ->
                    ConceptHelp(title, explanation, subsystemConceptAnchor(title))
                }
            }
            HorizontalDivider(color = AresBorder)
            content()
        }
    }
}

@Composable
private fun SelectableRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) AresCyan.copy(alpha = .12f) else AresSurface, RoundedCornerShape(5.dp))
            .border(1.dp, if (selected) AresCyan else AresBorder, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AresTextSecondary, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = AresCyan, modifier = Modifier.size(13.dp))
            Text(if (selected) "Editing below" else "Edit", color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun BuilderNavigation(
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
    modifier: Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DocumentList(state, viewModel)
        StageRail(state, viewModel)
        CurrentSubsystemSummary(state, viewModel)
    }
}

@Composable
private fun BuilderEditor(
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
    workspaceTab: Int,
    onTabChange: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tabColors = FilterChipDefaults.filterChipColors(
                containerColor = AresSurface,
                labelColor = AresTextPrimary,
                selectedContainerColor = AresCyan,
                selectedLabelColor = AresOnAccent,
            )
            FilterChip(
                selected = workspaceTab == 0,
                onClick = { onTabChange(0) },
                label = { Text("Configure") },
                colors = tabColors,
            )
            FilterChip(
                selected = workspaceTab == 1,
                onClick = { onTabChange(1) },
                label = { Text("Generated Kotlin") },
                colors = tabColors,
            )
        }
        if (workspaceTab == 0) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StageHeader(state.activeStage)
                StageContent(state, viewModel)
                StageNavigation(state, viewModel)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtifactPlan(state, viewModel)
                CodePreview(state, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun SubsystemAiAssistantContent(
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
) {
    val document = state.draft?.document ?: return
    var request by remember(document.uid) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = AresSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Describe your subsystem requirements in plain language.",
                    color = AresTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    "Gemini will generate a structured subsystem proposal with hardware devices, state fields, control laws, and tuning parameters for ${document.platform.name}. It proposes reviewed form changes only; it cannot save or edit Kotlin source directly.",
                    color = AresTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }

        TextInput(
            label = "What should this subsystem do?",
            value = request,
            singleLine = false,
            enabled = !state.aiProposalInProgress,
        ) { request = it.take(4_000) }

        Button(
            onClick = { viewModel.requestAiProposal(request) },
            enabled = request.isNotBlank() && !state.aiProposalInProgress,
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (state.aiProposalInProgress) "Preparing proposal…" else "Ask Gemini for a form proposal")
        }

        state.aiProposalError?.let { Text(it, color = AresError, fontSize = 12.sp) }

        Surface(
            color = AresBackground.copy(alpha = 0.5f),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                "Privacy: Only your prompt and current subsystem form are sent using the AI provider configured in Profile. Your robot logs, credentials, and source files are never transmitted.",
                color = AresTextTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
    state.aiProposal?.let { review ->
        SubsystemAiProposalDialog(
            review = review,
            onApply = viewModel::applyAiProposal,
            onDismiss = viewModel::dismissAiProposal,
        )
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
        title = { Text("Review Gemini's form proposal") },
        text = {
            Column(
                Modifier.fillMaxWidth().height(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(review.proposal.summary, color = AresTextPrimary, fontWeight = FontWeight.SemiBold)
                review.proposal.explanations.forEach { explanation ->
                    Text("• $explanation", color = AresTextSecondary, fontSize = 12.sp)
                }
                if (review.problems.isNotEmpty()) {
                    Text("Validation review", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    review.problems.forEach { problem ->
                        Text(
                            "${if (problem.severity == SubsystemProblemSeverity.ERROR) "Blocking" else "Review"}: ${problem.message}",
                            color = if (problem.severity == SubsystemProblemSeverity.ERROR) AresError else AresGold,
                            fontSize = 12.sp,
                        )
                    }
                }
                Text("Structured form diff", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                Column(
                    Modifier.fillMaxWidth().background(AresBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, AresBorder, RoundedCornerShape(6.dp)).padding(8.dp),
                ) {
                    review.diff.take(300).forEach { line ->
                        val prefix = when (line.kind) {
                            SubsystemDiffLineKind.ADDED -> "+ "
                            SubsystemDiffLineKind.REMOVED -> "− "
                            SubsystemDiffLineKind.CONTEXT -> "  "
                        }
                        Text(
                            prefix + line.text,
                            color = when (line.kind) {
                                SubsystemDiffLineKind.ADDED -> AresGreen
                                SubsystemDiffLineKind.REMOVED -> AresError
                                SubsystemDiffLineKind.CONTEXT -> AresTextSecondary
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                    if (review.diff.size > 300) {
                        Text("… ${review.diff.size - 300} more lines", color = AresTextTertiary, fontSize = 11.sp)
                    }
                }
                Text(
                    "Applying changes only updates the unsaved form and creates one Undo step. Save remains a separate action.",
                    color = AresTextSecondary,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = onApply, enabled = review.canApply) { Text("Apply to form") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Discard proposal") } },
    )
}

@Composable
private fun StableIdLabel(label: String, value: String, explanation: String) {
    Column(
        Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(5.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(5.dp)).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label.uppercase(), color = AresTextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = AresTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(explanation, color = AresTextSecondary, fontSize = 11.sp)
    }
}

private fun subsystemConceptExplanation(title: String): String? = when {
    title.contains("name", true) || title.contains("purpose", true) ->
        "The display name is for people; the stable ID and Kotlin type connect saved documents and generated code."
    title.contains("hardware", true) ->
        "Hardware declares robot connections and one cached snapshot. Actuators may be independent or follow a compatible leader."
    title.contains("state", true) || title.contains("behavior", true) ->
        "Immutable state separates observed measurements from requested targets. Hardware scaffolds its natural values explicitly."
    title.contains("controller", true) || title.contains("control", true) ->
        "Feedback corrects measured error; feedforward predicts the effort required for requested motion."
    title.contains("tuning", true) || title.contains("parameter", true) || title.contains("preset", true) ->
        "Typed declarations explain what may change, who owns it, valid values, and when a robot may safely apply a request."
    title.contains("safety", true) ->
        "Safety gates every non-neutral output using freshness, health, homing, current validity, and fault recovery."
    title.contains("simulation", true) || title.contains("verification", true) ->
        "Mocks and generated contract tests exercise the same limits, faults, homing, and cleanup without a physical robot."
    title.contains("capabil", true) ->
        "Capabilities are typed actions that controller bindings and autonomous routines can safely discover and validate."
    title.contains("runtime flow", true) ->
        "Intent flows through Redux immutable state before a controller reaches the cached IO boundary."
    title.contains("artifact", true) || title.contains("generated", true) ->
        "Ownership labels distinguish student customization points from deterministic generated plumbing."
    else -> null
}

private fun subsystemConceptAnchor(title: String): String = when {
    title.contains("safety", true) -> "homing"
    title.contains("controller", true) || title.contains("control", true) -> "feedforward"
    title.contains("tuning", true) || title.contains("parameter", true) || title.contains("preset", true) -> "typed-tuning-parameters"
    title.contains("hardware", true) -> "leader-and-follower-actuators"
    else -> "builder-workflow"
}

@Composable
private fun FlowArrow(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
        Text("↓ $label", color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FieldHelp(label: String) {
    val help = subsystemFieldHelp(label)
    ConceptHelp(label, help.first, help.second, compact = true)
}

/** Plain-language help for every editable field, including advanced fields revealed by templates. */
private fun subsystemFieldHelp(label: String): Pair<String, String> {
    val normalized = label.lowercase()
    return when {
        "what should this subsystem" in normalized ->
            "Describe the physical mechanism, what should move or be measured, important limits, and how it finds a safe reference. Gemini will propose form edits for review." to "ai-assisted-form-filling"
        "display name" in normalized || "hardware name" in normalized || "state value name" in normalized ||
            "controller rule name" in normalized ->
            "A human-readable label shown to students. Renaming it does not change the stable code connection." to "builder-workflow"
        "code id" in normalized || "kotlin type" in normalized ->
            "An identifier used by generated Kotlin and saved references. Advanced renames update known references together; use letters and numbers without spaces." to "builder-workflow"
        "parameter key" in normalized ->
            "A project-wide dotted key used by generated typed access. Keep it stable after profiles begin referring to this parameter." to "typed-tuning-parameters"
        "owning component" in normalized ->
            "The subsystem, hardware device, or controller rule that consumes this value. Ownership must name one stable UID declared in this subsystem." to "typed-tuning-parameters"
        "parameter type" in normalized || "apply policy" in normalized || "allowed options" in normalized ||
            "minimum (optional)" in normalized || "maximum (optional)" in normalized || "default option" in normalized ->
            "The type and bounds validate every profile and runtime request. Apply policy decides whether a live session, disabled robot, restart, rebuild, calibration, or vendor tool owns activation." to "typed-tuning-parameters"
        "hardware-map" in normalized ->
            "The exact FTC Robot Controller configuration name for this device. It must match the name configured on the Control Hub." to "io-contract-and-adapters"
        normalized == "can id" || normalized == "can bus" ->
            "The FRC CAN address and bus used to locate this controller. Every device on a bus needs a unique valid ID." to "io-contract-and-adapters"
        normalized == "channel" ->
            "The PWM, digital, or analog port where this device is connected." to "io-contract-and-adapters"
        "command source" in normalized || "follower direction" in normalized ->
            "Independent actuators own a controller. Followers reuse one leader command with a Same, Opposite, or Mirrored relationship and cannot fight it with another controller." to "leader-and-follower-actuators"
        "reverse hardware direction" in normalized ->
            "Corrects a device that is physically mounted backward. This is separate from follower direction and is applied after the follower relationship." to "leader-and-follower-actuators"
        "safe neutral" in normalized ->
            "The output used at startup, stop, disable, close, and fault recovery. Choose a value that leaves the real mechanism safe." to "io-contract-and-adapters"
        "current limit" in normalized || "valid current" in normalized ->
            "Current limits protect wiring and mechanisms. Current monitoring uses cached finite nonnegative samples; an unavailable reading is invalid, not zero." to "homing"
        "freshness" in normalized || "feedback timeout" in normalized ->
            "Maximum age of cached feedback before motion is blocked. Stale measurements must fail neutral rather than being trusted." to "homing"
        "homing" in normalized || "home" in normalized || "evidence dwell" in normalized ||
            "attempt timeout" in normalized || "threshold" in normalized || "comparison" in normalized ->
            "Homing uses bounded output and cached evidence that must stay true for a dwell period before assigning a reference position. Timeout and faults return to neutral." to "homing"
        normalized.startsWith("kp") || normalized.startsWith("ki") || normalized.startsWith("kd") ||
            "derivative filter" in normalized || "tolerance" in normalized ->
            "Feedback gains correct measured error. Start conservatively, keep output bounded, and validate in simulation before using hardware." to "feedforward"
        normalized.startsWith("ks") || normalized.startsWith("kv") || normalized.startsWith("ka") ||
            normalized.startsWith("kg") || "feedforward" in normalized || "desired velocity" in normalized ||
            "desired acceleration" in normalized || "arm angle" in normalized ->
            "Feedforward predicts required effort: kS handles static friction, kV velocity, kA acceleration, and kG gravity. Its field units must match the gains." to "feedforward"
        normalized == "strategy" || "target state" in normalized || "measurement state" in normalized ||
            normalized == "actuator" || "maximum output" in normalized || "minimum output" in normalized ->
            "The controller reads immutable target and measurement state, computes a bounded command, then writes through the IO safety boundary." to "controller"
        normalized == "role" || normalized == "type" || normalized == "default" ||
            "unit" in normalized || "what this value means" in normalized ->
            "State is explicit and immutable. Measurements describe observed hardware, targets describe requested behavior, and units make control gains and telemetry understandable." to "domain"
        "hardware signal" in normalized || "snapshot field" in normalized || normalized == "scale" ||
            normalized == "offset" || "valid minimum" in normalized || "valid maximum" in normalized ->
            "A hardware signal is read once per loop into a cached state field. Scale and offset convert native units; validity bounds reject impossible readings." to "io-contract-and-adapters"
        "simulation" in normalized || "mock" in normalized || "starter contract test" in normalized ||
            "zero-allocation" in normalized ->
            "Simulation and generated verification exercise the same safety and controller behavior without a physical robot. Periodic robot paths avoid allocations where required." to "verification-checklist"
        "source files" in normalized || "class" in normalized || "gradle module" in normalized ||
            "teaching level" in normalized || "what students learn" in normalized ->
            "Hand-authored metadata documents team-owned Kotlin without scanning or replacing it. Paths, classes, teaching notes, and simulation support must describe the live implementation." to "registering-a-subsystem-that-is-already-written-by-hand"
        "action keys" in normalized || "autonomous resource" in normalized || "required at robot startup" in normalized ->
            "Capabilities connect this subsystem to controller bindings and autonomous routines. Declared keys must exist in the project catalog and required resources must be available before startup." to "runtime-contract"
        "latch" in normalized || "configuration health" in normalized || "neutral recovery" in normalized ||
            "publish safety telemetry" in normalized || "require calibration" in normalized || "required hardware" in normalized ->
            "This safety gate decides whether non-neutral output is permitted. Failures latch when enabled and recovery requires a confirmed safe neutral when configured." to "verification-checklist"
        else ->
            "This setting is stored in the subsystem descriptor, validated before generation, and shown in the review step before any source is created." to "builder-workflow"
    }
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        enabled = enabled,
        trailingIcon = { FieldHelp(label) },
    )
}

@Composable
private fun DoubleInput(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text,
        { raw -> text = raw; raw.toDoubleOrNull()?.takeIf(Double::isFinite)?.let(onChange) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = text.toDoubleOrNull()?.isFinite() != true,
        trailingIcon = { FieldHelp(label) },
    )
}

@Composable
private fun NullableDoubleInput(label: String, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        text,
        { raw ->
            text = raw
            when {
                raw.isBlank() -> onChange(null)
                raw.toDoubleOrNull()?.isFinite() == true -> onChange(raw.toDouble())
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = text.isNotBlank() && text.toDoubleOrNull()?.isFinite() != true,
        trailingIcon = { FieldHelp(label) },
    )
}

@Composable
private fun IntInput(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text,
        { raw -> text = raw; raw.toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = text.toIntOrNull() == null,
        trailingIcon = { FieldHelp(label) },
    )
}

@Composable
private fun AddHardwareButton(viewModel: SubsystemGeneratorViewModel, label: String) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SubsystemHardwareKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(kind.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))
                            Text(
                                when (kind) {
                                    SubsystemHardwareKind.MOTOR -> "Adds target voltage plus cached position, velocity, and current."
                                    SubsystemHardwareKind.POSITIONAL_SERVO -> "Adds a 0–1 PWM position target (Prism-compatible)."
                                    SubsystemHardwareKind.CONTINUOUS_SERVO -> "Adds a -1–1 PWM power target."
                                    SubsystemHardwareKind.DIGITAL_INPUT -> "Adds a cached Boolean state."
                                    SubsystemHardwareKind.ANALOG_INPUT -> "Adds a cached voltage measurement."
                                    SubsystemHardwareKind.COLOR_SENSOR -> "Adds a cached ARGB color measurement."
                                },
                                color = AresTextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        viewModel.addHardware(kind)
                    },
                )
            }
        }
    }
}

@Composable
private fun AddStateValueButton(viewModel: SubsystemGeneratorViewModel, label: String) {
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(SubsystemFieldRole.STATUS) }
    var type by remember { mutableStateOf(SubsystemValueType.DOUBLE) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Add a state value") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Hardware-provided values are added automatically. Use this for an additional target, derived status, or configuration value.",
                        color = AresTextSecondary,
                        fontSize = 13.sp,
                    )
                    TextInput("Display name", name) { name = it }
                    EnumSelector("Role", role, SubsystemFieldRole.entries) { role = it }
                    EnumSelector("Type", type, SubsystemValueType.entries) { type = it }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addStateField(name.trim(), role, type)
                        name = ""
                        open = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Add value") }
            },
            dismissButton = { OutlinedButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LongInput(label: String, value: Long, onChange: (Long) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { it.toLongOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { FieldHelp(label) },
    )
}

@Composable
private fun NullableLongInput(label: String, value: Long?, onChange: (Long?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        text,
        { raw ->
            text = raw
            when {
                raw.isBlank() -> onChange(null)
                raw.toLongOrNull() != null -> onChange(raw.toLong())
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = text.isNotBlank() && text.toLongOrNull() == null,
        trailingIcon = { FieldHelp(label) },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AresTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            FieldHelp(label)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DeleteButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Delete, null, tint = AresError, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = AresError)
    }
}

@Composable
private fun <T : Enum<T>> EnumSelector(label: String, selected: T, options: List<T>, onSelected: (T) -> Unit) {
    DropdownSelector(label, selected.name.replace('_', ' '), options.map { it.name.replace('_', ' ') }) { display ->
        options.firstOrNull { it.name.replace('_', ' ') == display }?.let(onSelected)
    }
}

@Composable
private fun EnumStringSelector(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) =
    DropdownSelector(label, selected, options, onSelected)

@Composable
private fun EnumNullableSelector(label: String, selected: String?, options: List<String>, onSelected: (String?) -> Unit) {
    DropdownSelector(label, selected ?: "None", listOf("None") + options) { onSelected(it.takeUnless { value -> value == "None" }) }
}

@Composable
private fun DropdownSelector(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            selected,
            {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldHelp(label)
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, null) }
                }
            },
        )
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 11.sp) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(message: String, error: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(
            if (error) AresError.copy(alpha = .12f) else AresCyan.copy(alpha = .1f), RoundedCornerShape(6.dp)
        ).border(1.dp, if (error) AresError else AresCyan, RoundedCornerShape(6.dp)).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (error) Icon(Icons.Default.Warning, null, tint = AresError, modifier = Modifier.size(15.dp))
        Text(message, color = if (error) AresError else AresTextPrimary, fontSize = 11.sp)
    }
}

private fun SubsystemArtifactGroup.displayName(): String = when (this) {
    SubsystemArtifactGroup.DOMAIN -> "Domain"
    SubsystemArtifactGroup.CONTROL -> "Control"
    SubsystemArtifactGroup.HARDWARE -> "Hardware"
    SubsystemArtifactGroup.SIMULATION -> "Simulation"
    SubsystemArtifactGroup.GENERATED_PLUMBING -> "Generated Plumbing"
    SubsystemArtifactGroup.VERIFICATION -> "Verification"
}

private fun SubsystemArtifactOwnership.displayName(): String = when (this) {
    SubsystemArtifactOwnership.USER_OWNED -> "USER-OWNED"
    SubsystemArtifactOwnership.GENERATED_STARTER -> "GENERATED STARTER"
    SubsystemArtifactOwnership.GENERATED_DO_NOT_EDIT -> "GENERATED — DO NOT EDIT"
}

private fun SubsystemFileChange.displayName(): String = when (this) {
    SubsystemFileChange.CREATE -> "Will be created"
    SubsystemFileChange.UNCHANGED -> "Unchanged"
    SubsystemFileChange.UPDATE_GENERATED -> "Generated output will be refreshed"
    SubsystemFileChange.REPLACE_STARTER -> "Confirmation required before replacing this starter"
    SubsystemFileChange.PROTECTED_USER_OWNED -> "Protected USER-OWNED file differs; generation is blocked"
}

private fun SubsystemHardwareDocument.connectionLabel(platform: SubsystemPlatform): String = when (platform) {
    SubsystemPlatform.FTC -> connection.hardwareMapName ?: "unmapped"
    SubsystemPlatform.FRC -> if (kind == SubsystemHardwareKind.MOTOR) "CAN ${connection.canId ?: "?"}" else "channel ${connection.channel ?: "?"}"
}

private fun SubsystemHardwareKind.isActuator(): Boolean = this == SubsystemHardwareKind.MOTOR ||
    this == SubsystemHardwareKind.POSITIONAL_SERVO || this == SubsystemHardwareKind.CONTINUOUS_SERVO

private fun SubsystemControlStrategy.requiresMeasurement(): Boolean = this == SubsystemControlStrategy.POSITION_PID ||
    this == SubsystemControlStrategy.VELOCITY_PID || this == SubsystemControlStrategy.BANG_BANG

private fun SubsystemStateFieldDocument.withType(type: SubsystemValueType): SubsystemStateFieldDocument = copy(
    type = type,
    defaultNumber = if (type == SubsystemValueType.DOUBLE) 0.0 else null,
    defaultBoolean = if (type == SubsystemValueType.BOOLEAN) false else null,
    defaultInt = if (type == SubsystemValueType.INT) 0 else null,
    defaultText = if (type == SubsystemValueType.STRING) "" else null,
    minimum = minimum.takeIf { type == SubsystemValueType.DOUBLE || type == SubsystemValueType.INT },
    maximum = maximum.takeIf { type == SubsystemValueType.DOUBLE || type == SubsystemValueType.INT },
    unit = unit.takeIf { type == SubsystemValueType.DOUBLE || type == SubsystemValueType.INT },
)

private fun generateSubsystemSpecSections(
    document: SubsystemDocument,
    onSelectHardware: (String) -> Unit,
    onSelectField: (String) -> Unit,
    onSelectLoop: (String) -> Unit,
    onSelectTuning: (String) -> Unit,
): List<AresSpecSection> = listOf(
    AresSpecSection(
        title = "Physical Devices",
        rows = document.hardware.map { dev ->
            AresSpecRow(
                id = dev.uid,
                primaryLabel = dev.displayName,
                secondaryLabel = "${dev.hardwareId} · ${dev.connectionLabel(document.platform)}",
                badge = dev.kind.name,
                columns = listOfNotNull(
                    "Command source" to (dev.following?.let { "Follows ${it.leaderId} (${it.transform.name})" } ?: "Independent"),
                    "Required" to if (dev.required) "Yes" else "No",
                    "Direction" to if (dev.inverted) "Reversed" else "Normal",
                    dev.safeOutput?.let { "Safe output" to "$it V" },
                    dev.currentLimitAmps?.let { "Current limit" to "$it A" },
                    if (dev.measurements.isNotEmpty()) "Cached inputs" to dev.measurements.joinToString(", ") { "${it.source.name} → ${it.fieldId}" } else null,
                ),
                onEditClick = { onSelectHardware(dev.uid) },
            )
        },
    ),
    AresSpecSection(
        title = "Stateflow Fields",
        rows = document.stateFields.map { field ->
            AresSpecRow(
                id = field.uid,
                primaryLabel = field.displayName,
                secondaryLabel = "${field.fieldId} · ${field.type.name}${field.unit?.let { " ($it)" }.orEmpty()}",
                badge = field.role.name,
                columns = listOfNotNull(
                    "Role" to field.role.name,
                    "Type" to field.type.name,
                    field.unit?.let { "Unit" to it },
                    "Default" to (field.defaultNumber?.toString() ?: field.defaultBoolean?.toString() ?: field.defaultInt?.toString() ?: field.defaultText ?: "-"),
                    field.minimum?.let { "Min" to it.toString() },
                    field.maximum?.let { "Max" to it.toString() },
                    field.description?.takeIf { it.isNotBlank() }?.let { "Description" to it },
                ),
                onEditClick = { onSelectField(field.uid) },
            )
        },
    ),
    AresSpecSection(
        title = "Control Laws",
        rows = document.controlLoops.map { loop ->
            AresSpecRow(
                id = loop.uid,
                primaryLabel = loop.displayName,
                secondaryLabel = "${loop.actuatorId} ← target ${loop.targetFieldId}",
                badge = loop.strategy.name,
                columns = listOfNotNull(
                    "Actuator" to loop.actuatorId,
                    "Target field" to loop.targetFieldId,
                    loop.measurementFieldId?.let { "Measurement field" to it },
                    if (loop.strategy == SubsystemControlStrategy.POSITION_PID || loop.strategy == SubsystemControlStrategy.VELOCITY_PID) {
                        "PID Gains" to "kP=${loop.kP}, kI=${loop.kI}, kD=${loop.kD}"
                    } else null,
                    if (loop.feedforward.kind != SubsystemFeedforwardKind.NONE) {
                        "Feedforward" to "${loop.feedforward.kind.name} (kS=${loop.feedforward.kS}, kV=${loop.feedforward.kV}, kA=${loop.feedforward.kA}, kG=${loop.feedforward.kG})"
                    } else null,
                    "Output limits" to "[${loop.minimumOutput} .. ${loop.maximumOutput}]",
                    loop.tolerance?.let { "Tolerance" to it.toString() },
                ),
                onEditClick = { onSelectLoop(loop.uid) },
            )
        },
    ),
    AresSpecSection(
        title = "Tuning Parameters",
        rows = document.tuningParameters.map { param ->
            AresSpecRow(
                id = param.uid,
                primaryLabel = param.displayName,
                secondaryLabel = "${param.key} · ${param.applyPolicy.name}",
                badge = param.type.name,
                columns = listOfNotNull(
                    "Key" to param.key,
                    "Component" to param.componentUid,
                    "Type" to param.type.name,
                    param.unit?.let { "Unit" to it },
                    if (param.minimum != null || param.maximum != null) "Bounds" to "[${param.minimum ?: "-"} .. ${param.maximum ?: "-"}]" else null,
                    "Apply policy" to param.applyPolicy.name,
                    param.description?.takeIf { it.isNotBlank() }?.let { "Description" to it },
                ),
                onEditClick = { onSelectTuning(param.uid) },
            )
        },
    ),
)

private fun generateSubsystemMarkdown(document: SubsystemDocument): String = buildString {
    appendLine("# Subsystem Specification: ${document.displayName}")
    appendLine("Platform: ${document.platform.name}")
    appendLine("Document ID: ${document.documentId}")
    appendLine("Revision: r${document.revision}")
    appendLine()
    appendLine("## Physical Devices (${document.hardware.size})")
    appendLine("| Name | Code ID | Kind | Connection | Role |")
    appendLine("|---|---|---|---|---|")
    document.hardware.forEach { dev ->
        appendLine("| ${dev.displayName} | ${dev.hardwareId} | ${dev.kind.name} | ${dev.connectionLabel(document.platform)} | ${dev.following?.let { "Follows ${it.leaderId}" } ?: "Independent"} |")
    }
    appendLine()
    appendLine("## Stateflow Fields (${document.stateFields.size})")
    appendLine("| Name | Field ID | Role | Type | Unit | Default |")
    appendLine("|---|---|---|---|---|---|")
    document.stateFields.forEach { f ->
        appendLine("| ${f.displayName} | ${f.fieldId} | ${f.role.name} | ${f.type.name} | ${f.unit ?: "-"} | ${f.defaultNumber ?: f.defaultBoolean ?: f.defaultInt ?: f.defaultText ?: "-"} |")
    }
    appendLine()
    appendLine("## Control Laws (${document.controlLoops.size})")
    appendLine("| Name | Strategy | Actuator | Target | Gains |")
    appendLine("|---|---|---|---|---|")
    document.controlLoops.forEach { l ->
        appendLine("| ${l.displayName} | ${l.strategy.name} | ${l.actuatorId} | ${l.targetFieldId} | kP=${l.kP} kI=${l.kI} kD=${l.kD} |")
    }
    appendLine()
    appendLine("## Tuning Parameters (${document.tuningParameters.size})")
    appendLine("| Name | Key | Type | Safe Bounds | Apply Policy |")
    appendLine("|---|---|---|---|---|")
    document.tuningParameters.forEach { p ->
        appendLine("| ${p.displayName} | ${p.key} | ${p.type.name} | [${p.minimum ?: "-"} .. ${p.maximum ?: "-"}] | ${p.applyPolicy.name} |")
    }
}

