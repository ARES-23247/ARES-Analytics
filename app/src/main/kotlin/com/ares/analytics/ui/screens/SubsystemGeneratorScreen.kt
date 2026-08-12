package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary
import com.ares.analytics.viewmodel.SubsystemGeneratorState
import com.ares.analytics.viewmodel.SubsystemGeneratorViewModel
import com.ares.analytics.viewmodel.SubsystemDiffLineKind
import com.ares.analytics.viewmodel.SubsystemFileChange
import com.ares.analytics.viewmodel.SubsystemPreviewFile
import com.ares.analytics.viewmodel.SubsystemProblemSeverity
import com.ares.analytics.viewmodel.subsystemTemplateOptions
import com.areslib.codegen.GeneratedSubsystemSourceSet
import com.areslib.codegen.SubsystemArtifactGroup
import com.areslib.codegen.SubsystemArtifactOwnership
import com.areslib.subsystem.SubsystemControlLoopDocument
import com.areslib.subsystem.SubsystemControlStrategy
import com.areslib.subsystem.SubsystemFieldRole
import com.areslib.subsystem.SubsystemHardwareConnection
import com.areslib.subsystem.SubsystemHardwareDocument
import com.areslib.subsystem.SubsystemHardwareKind
import com.areslib.subsystem.SubsystemMeasurementSource
import com.areslib.subsystem.SubsystemMeasurementDocument
import com.areslib.subsystem.SubsystemPlatform
import com.areslib.subsystem.SubsystemStateFieldDocument
import com.areslib.subsystem.SubsystemValueType
import com.areslib.subsystem.compatibleMeasurementSources
import com.areslib.subsystem.valueType

/** Visual editor for project-backed subsystem DSL documents and their generated Kotlin. */
@Composable
fun SubsystemGeneratorScreen(viewModel: SubsystemGeneratorViewModel) {
    val state by viewModel.state.collectAsState()
    var workspaceTab by remember { mutableStateOf(0) }
    var confirmReload by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubsystemHeader(state, viewModel) {
            if (state.dirty) confirmReload = true else viewModel.reload()
        }
        state.status?.let { StatusBanner(it, false) }
        state.generationMessage?.let {
            StatusBanner(it, state.generationPhase == AresGenerationPhase.FAILED)
        }
        val loadError = state.loadError
        if (loadError != null) {
            StatusBanner(loadError, true)
            return@Column
        }
        val draft = state.draft ?: return@Column
        BuilderProgress(draft)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(.8f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DocumentList(state, viewModel)
                TemplateCard(state, viewModel)
                ArchitectureCard(state, viewModel)
            }
            Column(
                Modifier.weight(2.2f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(workspaceTab == 0, { workspaceTab = 0 }, { Text("Configure") })
                    FilterChip(workspaceTab == 1, { workspaceTab = 1 }, { Text("Generated Kotlin") })
                }
                if (workspaceTab == 0) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeFlowCard()
                        GeneralInspector(state, viewModel)
                        SafetyInspector(state, viewModel)
                        ArtifactPlan(state, viewModel)
                        when {
                            state.selectedHardwareId != null -> draft.hardware.firstOrNull {
                                it.hardwareId == state.selectedHardwareId
                            }?.let { HardwareInspector(state, it, viewModel) }
                            state.selectedFieldId != null -> draft.stateFields.firstOrNull {
                                it.fieldId == state.selectedFieldId
                            }?.let { StateFieldInspector(it, viewModel) }
                            state.selectedLoopId != null -> draft.controlLoops.firstOrNull {
                                it.loopId == state.selectedLoopId
                            }?.let { ControlInspector(state, it, viewModel) }
                        }
                        ProblemsCard(state)
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArtifactPlan(state, viewModel)
                        CodePreview(state, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }

    if (confirmReload) {
        AlertDialog(
            onDismissRequest = { confirmReload = false },
            title = { Text("Discard unsaved subsystem changes?") },
            text = { Text("Reload restores the last saved project revision. Your current edits cannot be recovered.") },
            confirmButton = {
                Button(onClick = { confirmReload = false; viewModel.reload() }) { Text("Discard and reload") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmReload = false }) { Text("Keep editing") } }
        )
    }
    if (state.pendingStarterReplacements.isNotEmpty()) {
        StarterReplacementDialog(
            files = state.pendingStarterReplacements,
            onConfirm = viewModel::confirmStarterReplacement,
            onDismiss = viewModel::cancelStarterReplacement,
        )
    }
}

@Composable
private fun SubsystemHeader(
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
    onReload: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Memory, null, tint = AresCyan, modifier = Modifier.size(22.dp))
                Text("Subsystem Builder", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Text(
                "Visual for beginners, readable DSL for learners, direct IO/Redux escape hatches for advanced students.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReload) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Reload")
            }
            Button(
                onClick = { viewModel.save() },
                enabled = state.canSave,
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Save", color = AresBackground)
            }
            Button(
                onClick = viewModel::generate,
                enabled = state.canSave || state.canGenerate,
                colors = ButtonDefaults.buttonColors(containerColor = AresGreen),
            ) {
                Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (state.dirty) "Save & Generate" else "Generate Kotlin", color = AresBackground)
            }
        }
    }
}

@Composable
private fun BuilderProgress(document: com.areslib.subsystem.SubsystemDocument) {
    val hasHardware = document.hardware.isNotEmpty()
    val hasState = document.stateFields.isNotEmpty()
    val hasControl = document.controlLoops.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().background(AresSurface, RoundedCornerShape(7.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(7.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressLabel("1", "Add hardware", hasHardware)
        ProgressLabel("2", "Define state", hasState)
        ProgressLabel("3", "Connect behavior", hasControl)
        ProgressLabel("4", "Generate Kotlin", false)
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
private fun DocumentList(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    EditorCard("Project subsystems", Icons.Default.Settings) {
        state.documents.sortedBy { it.name.lowercase() }.forEach { document ->
            val selected = document.documentId == state.selectedDocumentId
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) AresCyan.copy(alpha = .12f) else AresSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, if (selected) AresCyan else AresBorder, RoundedCornerShape(6.dp))
                    .clickable { viewModel.selectDocument(document.documentId) }
                    .padding(9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(document.name, color = AresTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(document.documentId, color = AresTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                Text("r${document.revision}", color = AresTextTertiary, fontSize = 10.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedButton(onClick = viewModel::newSubsystem, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("New from selected template")
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
        Text(selected.description, color = AresTextSecondary, fontSize = 10.sp)
        Text(
            "Templates configure behavior and safety capabilities; they never collapse architectural boundaries.",
            color = AresTextTertiary,
            fontSize = 9.sp,
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
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ArtifactPlan(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    if (state.previewFiles.isEmpty()) return
    EditorCard("Artifact plan", Icons.Default.Code) {
        val starterCount = state.previewFiles.count { it.ownership == SubsystemArtifactOwnership.GENERATED_STARTER }
        val generatedCount = state.previewFiles.count { it.ownership == SubsystemArtifactOwnership.GENERATED_DO_NOT_EDIT }
        Text(
            "$starterCount customization starters · $generatedCount generated plumbing/verification files",
            color = AresTextSecondary,
            fontSize = 10.sp,
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
                Text("${files.size}", color = AresTextTertiary, fontSize = 10.sp)
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
            Text(file.path.substringAfterLast('/'), color = AresTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(file.ownership.displayName(), color = ownershipColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Text(file.description, color = AresTextSecondary, fontSize = 9.sp)
        Text(file.moduleName, color = AresCyan, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        Text(file.projectRelativePath, color = AresTextTertiary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        if (file.change != SubsystemFileChange.UNCHANGED && file.change != SubsystemFileChange.CREATE) {
            Text(file.change.displayName(), color = if (file.change == SubsystemFileChange.PROTECTED_USER_OWNED) AresError else AresGold, fontSize = 8.sp)
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ArchitectureCard(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft ?: return
    EditorCard("Architecture", Icons.Default.Memory) {
        Text("SENSORS / ACTUATORS", color = AresTextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        document.hardware.forEach { device ->
            SelectableRow(
                title = device.displayName,
                subtitle = "${device.kind.name.replace('_', ' ').lowercase()} · ${device.connectionLabel(document.platform)}",
                selected = state.selectedHardwareId == device.hardwareId,
                onClick = { viewModel.selectHardware(device.hardwareId) },
            )
        }
        OutlinedButton(onClick = viewModel::addHardware, modifier = Modifier.fillMaxWidth()) { Text("+ Hardware") }

        FlowArrow("cached read")
        Text("IMMUTABLE STATE", color = AresTextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        document.stateFields.forEach { field ->
            SelectableRow(
                field.displayName,
                "${field.role.name.lowercase()} · ${field.type.name.lowercase()}${field.unit?.let { " ($it)" }.orEmpty()}",
                state.selectedFieldId == field.fieldId,
            ) { viewModel.selectField(field.fieldId) }
        }
        OutlinedButton(onClick = viewModel::addStateField, modifier = Modifier.fillMaxWidth()) { Text("+ State value") }

        FlowArrow("controller")
        Text("OUTPUT RULES", color = AresTextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        document.controlLoops.forEach { loop ->
            SelectableRow(
                loop.displayName,
                "${loop.strategy.name.replace('_', ' ').lowercase()} → ${loop.actuatorId}",
                state.selectedLoopId == loop.loopId,
            ) { viewModel.selectLoop(loop.loopId) }
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
            Text("Add an actuator and a numeric target state first.", color = AresTextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun GeneralInspector(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft ?: return
    EditorCard("Subsystem definition", Icons.Default.Settings) {
        TextInput("Stable document ID", document.documentId, enabled = false) { }
        Text("The ID is fixed after creation so saved revisions and generated references cannot split.", color = AresTextSecondary, fontSize = 10.sp)
        TextInput("Kotlin class name", document.name) { value -> viewModel.edit { it.copy(name = value) } }
        TextInput("Description", document.description, singleLine = false) { value -> viewModel.edit { it.copy(description = value) } }
        ToggleRow("Required at robot startup", document.requiredAtStartup) { value ->
            viewModel.edit { it.copy(requiredAtStartup = value) }
        }
        ToggleRow("Generate desktop/mock IO", document.generateMockIo) { value -> viewModel.edit { it.copy(generateMockIo = value) } }
        ToggleRow("Generate a starter test", document.generateTest) { value -> viewModel.edit { it.copy(generateTest = value) } }
        Text(
            "Runtime package: ${if (document.platform == SubsystemPlatform.FTC) "org.firstinspires.ftc.teamcode" else "com.areslib.frc"}.subsystems.${document.documentId.replace('-', '_')}",
            color = AresTextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun SafetyInspector(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft ?: return
    val safety = document.safety
    val digitalInputs = document.hardware.filter { it.kind == SubsystemHardwareKind.DIGITAL_INPUT }.map { it.hardwareId }
    EditorCard("Safety contract", Icons.Default.Warning) {
        Text(
            "Non-neutral output is permitted only while every applicable safety condition below is healthy.",
            color = AresTextSecondary,
            fontSize = 10.sp,
        )
        NullableLongInput("Feedback timeout (ms)", safety.feedbackTimeoutMs) { value ->
            viewModel.edit { it.copy(safety = it.safety.copy(feedbackTimeoutMs = value)) }
        }
        ToggleRow("Require homing", safety.requiresHoming) { value ->
            viewModel.edit {
                it.copy(safety = it.safety.copy(requiresHoming = value, homingSensorId = it.safety.homingSensorId.takeIf { value }))
            }
        }
        if (safety.requiresHoming) {
            EnumNullableSelector("Homing sensor", safety.homingSensorId, digitalInputs) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(homingSensorId = value)) }
            }
            if (digitalInputs.isEmpty()) {
                Text("Add a digital input before enabling motion on a homed mechanism.", color = AresGold, fontSize = 10.sp)
            }
        }
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

@Composable
private fun HardwareInspector(
    state: SubsystemGeneratorState,
    device: SubsystemHardwareDocument,
    viewModel: SubsystemGeneratorViewModel,
) {
    val document = state.draft ?: return
    EditorCard("Hardware · ${device.hardwareId}", Icons.Default.Memory) {
        TextInput("Display name", device.displayName) { value ->
            viewModel.updateHardware(device.hardwareId) { it.copy(displayName = value) }
        }
        EnumSelector("Type", device.kind, SubsystemHardwareKind.entries) { kind ->
            viewModel.updateHardware(device.hardwareId) { current ->
                current.copy(
                    kind = kind,
                    currentLimitAmps = current.currentLimitAmps.takeIf { kind == SubsystemHardwareKind.MOTOR },
                    measurements = emptyList(),
                    safeOutput = when (kind) {
                        SubsystemHardwareKind.MOTOR, SubsystemHardwareKind.CONTINUOUS_SERVO -> 0.0
                        SubsystemHardwareKind.POSITIONAL_SERVO -> 0.5
                        else -> null
                    },
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
            Text("CACHED INPUT SNAPSHOT", color = AresTextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
            Text("Each hardware signal is read once per loop into the reusable snapshot.", color = AresTextSecondary, fontSize = 10.sp)
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
            ToggleRow("Inverted", device.inverted) { value ->
                viewModel.updateHardware(device.hardwareId) { it.copy(inverted = value) }
            }
        }
        DeleteButton("Delete hardware") { viewModel.removeHardware(device.hardwareId) }
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
private fun StateFieldInspector(field: SubsystemStateFieldDocument, viewModel: SubsystemGeneratorViewModel) {
    EditorCard("State · ${field.fieldId}", Icons.Default.Code) {
        TextInput("Display name", field.displayName) { value ->
            viewModel.updateStateField(field.fieldId) { it.copy(displayName = value) }
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
        DeleteButton("Delete state value") { viewModel.removeStateField(field.fieldId) }
    }
}

@Composable
private fun ControlInspector(
    state: SubsystemGeneratorState,
    loop: SubsystemControlLoopDocument,
    viewModel: SubsystemGeneratorViewModel,
) {
    val document = state.draft ?: return
    val actuators = document.hardware.filter { it.kind.isActuator() }.map { it.hardwareId }
    val numericFields = document.stateFields.filter { it.type == SubsystemValueType.DOUBLE || it.type == SubsystemValueType.INT }
    EditorCard("Control · ${loop.loopId}", Icons.Default.Settings) {
        TextInput("Display name", loop.displayName) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(displayName = value) }
        }
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
            DoubleInput("kP", loop.kP) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kP = value) } }
            DoubleInput("kI", loop.kI) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kI = value) } }
            DoubleInput("kD", loop.kD) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kD = value) } }
            DoubleInput("kS", loop.kS) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kS = value) } }
            DoubleInput("kV", loop.kV) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kV = value) } }
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
        }
        if (loop.strategy.requiresMeasurement()) {
            DoubleInput("Tolerance", loop.tolerance) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(tolerance = value) } }
        }
        DoubleInput("Minimum output", loop.minimumOutput) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(minimumOutput = value) }
        }
        DoubleInput("Maximum output", loop.maximumOutput) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(maximumOutput = value) }
        }
        DeleteButton("Delete control rule") { viewModel.removeControlLoop(loop.loopId) }
    }
}

@Composable
private fun ProblemsCard(state: SubsystemGeneratorState) {
    if (state.problems.isEmpty()) return
    EditorCard("Checks", Icons.Default.Warning) {
        state.problems.forEach { problem ->
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        if (problem.severity == SubsystemProblemSeverity.ERROR) AresError.copy(alpha = .09f) else AresGold.copy(alpha = .09f),
                        RoundedCornerShape(5.dp),
                    )
                    .padding(8.dp),
            ) {
                Text(problem.path, color = AresTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
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
    var selectedPath by remember(state.draft?.documentId, state.previewFiles) {
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
                Text("${state.previewFiles.size} files", color = AresTextSecondary, fontSize = 10.sp)
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
                    fontSize = 9.sp,
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
                        fontSize = 10.sp,
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
                Text(selected.projectRelativePath, color = AresTextTertiary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
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
                            Text(prefix + line.text, color = color, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
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
            Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            HorizontalDivider(color = AresBorder)
            content()
        }
    }
}

@Composable
private fun SelectableRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(if (selected) AresCyan.copy(alpha = .12f) else AresSurface, RoundedCornerShape(5.dp))
            .border(1.dp, if (selected) AresCyan else AresBorder, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Text(title, color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = AresTextSecondary, fontSize = 9.sp)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun FlowArrow(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
        Text("↓ $label", color = AresCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AresTextPrimary, fontSize = 11.sp)
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
            trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
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
