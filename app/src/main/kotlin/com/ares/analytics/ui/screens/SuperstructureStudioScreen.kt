package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.superstructure.*
import com.areslib.subsystem.InterlockComparison
import com.areslib.subsystem.SubsystemValueType
import com.areslib.superstructure.*
import java.util.Locale
import kotlin.math.*

@Composable
fun SuperstructureStudioScreen(viewModel: SuperstructureStudioViewModel) {
    val state by viewModel.state.collectAsState()
    var createOpen by remember { mutableStateOf(false) }

    if (createOpen) CreateSuperstructureDialog(
        onDismiss = { createOpen = false },
        onCreate = { id, name -> viewModel.create(id, name); createOpen = false },
    )
    state.pendingSelectionId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDiscard,
            title = { Text("Discard unsaved coordinator changes?") },
            text = { Text("The current draft will be replaced. Saved project files and history will remain unchanged.") },
            confirmButton = { Button(viewModel::confirmDiscard) { Text("Discard draft") } },
            dismissButton = { OutlinedButton(viewModel::cancelDiscard) { Text("Keep editing") } },
        )
    }
    state.review?.let { review ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReview,
            title = { Text("Review generated coordinator") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ARES will write one canonical document and an immutable history snapshot. It will not command a robot.")
                    review.summary.forEach { Text("• $it") }
                    Text("Before: ${review.expectedContentHash?.take(12) ?: "new file"}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("After: ${review.candidateContentHash.take(12)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmSave(review.confirmationToken) },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                ) { Text("Write reviewed file") }
            },
            dismissButton = { OutlinedButton(viewModel::dismissReview) { Text("Keep editing") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioHeader(state, viewModel, onCreate = { createOpen = true })
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = AresCyan)
                    Text("Loading subsystems, actions, and coordinators before editing…", color = AresTextSecondary)
                }
            }
            return@Column
        }
        state.error?.let { StudioBanner("Action needed", it, AresError) }
        if (state.status.isNotBlank()) StudioBanner("Saved state", state.status, AresGreen)
        if (state.generatedSubsystems.isEmpty()) {
            StudioBanner(
                "Generated mechanisms required",
                "This runtime-safe coordinator can currently reference generated subsystem descriptors only. Create a generated mechanism first; hand-authored Kotlin needs an explicit typed adapter and is never guessed.",
                AresGold,
            )
        }

        val draft = state.draft
        if (draft == null) {
            EmptyStudio(onCreate = { createOpen = true })
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioStepRail(state.step, viewModel, Modifier.width(190.dp).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state.step) {
                        SuperstructureStudioStep.OVERVIEW -> OverviewStep(state, draft, viewModel)
                        SuperstructureStudioStep.STATE_PRESETS -> StatePresetsStep(state, draft, viewModel)
                        SuperstructureStudioStep.TRANSITIONS -> TransitionsStep(state, draft, viewModel)
                        SuperstructureStudioStep.INTERLOCKS -> InterlocksStep(state, draft, viewModel)
                        SuperstructureStudioStep.LOOKUP_TABLES -> LookupTablesStep(draft, viewModel)
                        SuperstructureStudioStep.SIMULATION -> SimulationStep(state, draft, viewModel)
                        SuperstructureStudioStep.REVIEW -> ReviewStep(state, draft, viewModel)
                    }
                }
                ValidationRail(state, Modifier.width(280.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun StudioHeader(state: SuperstructureStudioState, viewModel: SuperstructureStudioViewModel, onCreate: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Superstructure Studio", color = AresTextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Coordinate several generated mechanisms as complete safe postures and reviewed transitions.", color = AresTextSecondary, fontSize = 12.sp)
            Text("RUNTIME FLOW · action or fresh sensor evidence → Redux coordinator → complete target preset → generated subsystem tasks → IO", color = AresCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("PROJECT · ${state.projectPath}", color = AresTextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            state.draft?.let {
                Text("CANONICAL · .ares/superstructures/${it.superstructureId}.aressuperstructure", color = AresTextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("GENERATED · robot module build/generated/ares/superstructures (never hand-edit)", color = AresTextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.documents.isNotEmpty()) {
                StudioDropdown(
                    label = state.documents.singleOrNull { it.superstructureId == state.selectedId }?.displayName ?: "Choose coordinator",
                    options = state.documents.map { it.superstructureId to it.displayName },
                    onSelect = viewModel::select,
                )
            }
            OutlinedButton(onClick = onCreate, enabled = !state.loading) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("New coordinator")
            }
            OutlinedButton(onClick = { viewModel.reload() }, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Reload")
            }
            Button(
                onClick = viewModel::reviewSave,
                enabled = state.canSave,
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Review & save")
            }
        }
    }
}

@Composable
private fun EmptyStudio(onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = AresSurface), border = BorderStroke(1.dp, AresBorder)) {
            Column(Modifier.widthIn(max = 620.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No mechanism coordinator yet", color = AresTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Use this only when two or more mechanisms must move as one safe posture—for example, raise an arm before extending an intake. A single mechanism belongs in Subsystem Builder.", color = AresTextSecondary)
                Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent)) { Text("Create a coordinator") }
            }
        }
    }
}

@Composable
private fun StudioStepRail(step: SuperstructureStudioStep, viewModel: SuperstructureStudioViewModel, modifier: Modifier) {
    val labels = mapOf(
        SuperstructureStudioStep.OVERVIEW to "Purpose & identity",
        SuperstructureStudioStep.STATE_PRESETS to "Complete postures",
        SuperstructureStudioStep.TRANSITIONS to "Transitions",
        SuperstructureStudioStep.INTERLOCKS to "Interlocks",
        SuperstructureStudioStep.LOOKUP_TABLES to "Lookup tables",
        SuperstructureStudioStep.SIMULATION to "Trace & fault lab",
        SuperstructureStudioStep.REVIEW to "Review & generate",
    )
    Column(modifier.studioCard(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("BUILD STEPS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        SuperstructureStudioStep.entries.forEachIndexed { index, candidate ->
            Text(
                "${index + 1}. ${labels.getValue(candidate)}",
                color = if (candidate == step) AresOnAccent else AresTextPrimary,
                fontSize = 11.sp,
                fontWeight = if (candidate == step) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth()
                    .background(if (candidate == step) AresCyan else Color.Transparent, RoundedCornerShape(7.dp))
                    .clickable { viewModel.selectStep(candidate) }
                    .padding(9.dp)
                    .semantics { contentDescription = "Step ${index + 1}: ${labels.getValue(candidate)}" },
            )
        }
    }
}

@Composable
private fun OverviewStep(state: SuperstructureStudioState, draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    StudioSection("What this coordinator owns", "It chooses complete desired postures. Subsystem controllers still own feedback control, homing, current limits, neutral recovery, and hardware writes.") {
        OutlinedTextField(
            draft.displayName,
            { viewModel.updateMetadata(it, draft.description) },
            label = { Text("Student-facing name") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Shown in Robot Studio; changing this does not rename generated Kotlin symbols.") },
        )
        OutlinedTextField(
            draft.description,
            { viewModel.updateMetadata(draft.displayName, it) },
            label = { Text("What should these mechanisms accomplish together?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusCount("Generated mechanisms", state.generatedSubsystems.size, if (state.generatedSubsystems.isEmpty()) AresGold else AresGreen)
            StatusCount("Available actions", state.parameterlessActions.size, if (state.parameterlessActions.isEmpty()) AresGold else AresGreen)
            StatusCount("Complete postures", draft.states.size, if (draft.states.size < 2) AresGold else AresGreen)
        }
    }
    StudioSection("Why complete postures?", "A transition never updates only one mechanism and leaves another at an old target. Every state explicitly commands the same target fields. The fault posture is forced back to each subsystem's declared neutral.") {
        Text("Example: STOWED → SCORE_HIGH can command arm angle, elevator height, wrist angle, and roller state together. If any generated task is unavailable, the runtime enters FAULT and attempts the complete neutral posture.", color = AresTextSecondary, fontSize = 12.sp)
    }
    StudioSection("Disabled behavior", "Choose what the logical coordinator does when Driver Station disables the robot. Subsystem IO still independently enforces neutral output.") {
        StudioDropdown(
            "Policy: ${draft.disabledPolicy.name.replace('_', ' ')}",
            SuperstructureDisabledPolicy.entries.map { policy -> policy.name to when (policy) {
                SuperstructureDisabledPolicy.FORCE_SAFE_AND_REJECT_REQUESTS -> "Move to the reviewed neutral disabled posture"
                SuperstructureDisabledPolicy.RETAIN_LOGICAL_STATE_WITH_NEUTRAL_OUTPUT -> "Remember the logical posture; hardware remains neutral"
            } },
            onSelect = { viewModel.setDisabledPolicy(SuperstructureDisabledPolicy.valueOf(it)) },
        )
        StudioDropdown(
            "Disabled posture: ${draft.disabledStateId}",
            draft.states.map { it.stateId to it.displayName.ifBlank { it.stateId } },
            onSelect = viewModel::setDisabledState,
        )
        Text(
            if (draft.disabledPolicy == SuperstructureDisabledPolicy.FORCE_SAFE_AND_REJECT_REQUESTS) {
                "Recommended for novice projects: disable rejects queued requests and enters a complete neutral posture."
            } else {
                "Advanced: re-enable resumes the remembered logical posture. Use only when every subsystem's re-enable contract has been reviewed."
            },
            color = if (draft.disabledPolicy == SuperstructureDisabledPolicy.FORCE_SAFE_AND_REJECT_REQUESTS) AresTextSecondary else AresGold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun StatePresetsStep(state: SuperstructureStudioState, draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    var newStateId by remember(draft.superstructureId) { mutableStateOf("") }
    var newStateName by remember(draft.superstructureId) { mutableStateOf("") }
    val selected = draft.states.singleOrNull { it.stateId == state.selectedStateId } ?: draft.states.first()
    StudioSection("State presets", "A preset is a complete desired posture—not a timeline and not a hardware command.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StudioDropdown(selected.displayName.ifBlank { selected.stateId }, draft.states.map { it.stateId to it.displayName.ifBlank { it.stateId } }, viewModel::selectState)
            if (selected.stateId == draft.initialStateId) StatusChip("INITIAL", AresGreen)
            if (selected.stateId == draft.faultStateId) StatusChip("FAULT / NEUTRAL", AresError)
            if (selected.stateId == draft.disabledStateId) StatusChip("DISABLED / NEUTRAL", AresGold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = viewModel::removeSelectedState, enabled = draft.states.size > 2 && selected.stateId !in setOf(draft.initialStateId, draft.faultStateId, draft.disabledStateId)) {
                Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(16.dp)); Text(" Remove")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ viewModel.setInitialState(selected.stateId) }, enabled = selected.stateId != draft.initialStateId) { Text("Use as startup posture") }
            OutlinedButton({ viewModel.setFaultState(selected.stateId) }, enabled = selected.stateId != draft.faultStateId) { Text("Use as fault neutral") }
            OutlinedButton({ viewModel.setDisabledState(selected.stateId) }, enabled = selected.stateId != draft.disabledStateId) { Text("Use when disabled") }
        }
        OutlinedTextField(
            selected.displayName,
            { viewModel.updateSelectedStateDetails(it, selected.description) },
            label = { Text("Posture name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            selected.description,
            { viewModel.updateSelectedStateDetails(selected.displayName, it) },
            label = { Text("What is this complete posture for?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Text("Stable state ID · ${selected.stateId}", color = AresTextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        HorizontalDivider(color = AresBorder)
        Text("TARGET VALUES IN ${selected.displayName.uppercase(Locale.ROOT)}", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        if (selected.subsystemTargets.isEmpty()) Text("Add at least one generated subsystem target below.", color = AresGold)
        selected.subsystemTargets.forEach { target -> TargetEditor(state, draft, selected, target, viewModel) }
        val used = selected.subsystemTargets.map { it.target }.toSet()
        val remaining = state.targetFields.filter { it.reference !in used }
        if (remaining.isNotEmpty()) {
            StudioDropdown("+ Add a target to every posture", remaining.map { key(it) to it.label }, onSelect = { selectedKey ->
                remaining.single { key(it) == selectedKey }.let { viewModel.addTarget(it.reference) }
            })
        }
    }
    StudioSection("Timing and lifecycle", "Timeouts are supervisory fallbacks. Entry/exit actions run once through the project task scheduler, exit before entry. Use an intermediate guarded posture—not hooks—for safety-critical motion ordering.") {
        var timeoutEnabled by remember(selected.stateId, selected.timeoutSeconds) { mutableStateOf(selected.timeoutSeconds != null) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(timeoutEnabled, { enabled ->
                timeoutEnabled = enabled
                viewModel.updateSelectedStateTimeout(if (enabled) 1.0 else null, if (enabled) draft.faultStateId else null)
            })
            Text(if (timeoutEnabled) "Maximum time in this posture is enabled" else "No posture timeout", color = AresTextPrimary)
        }
        if (timeoutEnabled) {
            NumberEditor(
                "Maximum time (seconds)",
                selected.timeoutSeconds ?: 1.0,
                onValue = { viewModel.updateSelectedStateTimeout(it, selected.timeoutTargetStateId ?: draft.faultStateId) },
                onValidity = { valid -> viewModel.setEditorError("state:${selected.stateId}:timeout", if (valid) null else "${selected.displayName} timeout must be numeric.") },
            )
            StudioDropdown(
                "Timeout posture: ${selected.timeoutTargetStateId ?: draft.faultStateId}",
                draft.states.filter { it.stateId != selected.stateId }.map { it.stateId to it.displayName.ifBlank { it.stateId } },
                onSelect = { viewModel.updateSelectedStateTimeout(selected.timeoutSeconds ?: 1.0, it) },
            )
        }
        LifecycleActionEditor("On exit", selected.onExitActionKeys, state, onAdd = { viewModel.addSelectedStateLifecycleAction(false, it) }, onRemove = { viewModel.removeSelectedStateLifecycleAction(false, it) })
        LifecycleActionEditor("On entry", selected.onEntryActionKeys, state, onAdd = { viewModel.addSelectedStateLifecycleAction(true, it) }, onRemove = { viewModel.removeSelectedStateLifecycleAction(true, it) })
    }
    StudioSection("Add another posture", "ARES copies the same target set and starts every value at its declared neutral so you cannot accidentally inherit a stale command.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newStateId, { newStateId = it }, label = { Text("Stable state ID") }, modifier = Modifier.weight(1f))
            OutlinedTextField(newStateName, { newStateName = it }, label = { Text("Display name") }, modifier = Modifier.weight(1f))
            Button(
                onClick = { viewModel.addState(newStateId, newStateName); newStateId = ""; newStateName = "" },
                enabled = newStateId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            ) { Text("Add posture") }
        }
    }
}

@Composable
private fun TargetEditor(
    state: SuperstructureStudioState,
    draft: SuperstructureDocument,
    selected: SuperstructureStatePreset,
    target: SuperstructureSubsystemTarget,
    viewModel: SuperstructureStudioViewModel,
) {
    val option = state.targetFields.singleOrNull { it.reference == target.target }
    if (option == null) return
    val fault = selected.stateId == draft.faultStateId ||
        (draft.disabledPolicy == SuperstructureDisabledPolicy.FORCE_SAFE_AND_REJECT_REQUESTS && selected.stateId == draft.disabledStateId)
    Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, AresBorder)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(option.label, color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    Text(option.field.description.ifBlank { "Immutable Redux target consumed by the generated subsystem controller." }, color = AresTextSecondary, fontSize = 10.sp)
                }
                if (!fault) IconButton({ viewModel.removeTarget(option.reference) }) { Icon(Icons.Default.Delete, "Remove ${option.label} from every posture") }
            }
            if (fault) {
                Text("Locked to the subsystem's declared safe neutral.", color = AresError, fontSize = 11.sp)
            } else {
                val targetModes = if (option.field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT)) {
                    SuperstructureTargetMode.entries
                } else {
                    listOf(SuperstructureTargetMode.CONSTANT, SuperstructureTargetMode.PASS_THROUGH)
                }
                StudioDropdown("Mode: ${target.targetMode.name.replace('_', ' ')}", targetModes.map { it.name to it.name.replace('_', ' ') }, onSelect = { mode ->
                    val selectedMode = SuperstructureTargetMode.valueOf(mode)
                    val updated = when (selectedMode) {
                        SuperstructureTargetMode.CONSTANT -> neutralFor(option)
                        SuperstructureTargetMode.PASS_THROUGH -> target.copy(targetMode = selectedMode, constantDoubleValue = null, constantBooleanValue = null, constantStringValue = null, lutId = null, source = state.sourceFields.firstOrNull { it.field.type == option.field.type }?.reference)
                        SuperstructureTargetMode.DYNAMIC_LUT -> target.copy(targetMode = selectedMode, constantDoubleValue = null, constantBooleanValue = null, constantStringValue = null, lutId = draft.luts.firstOrNull()?.lutId, source = state.sourceFields.firstOrNull { it.field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT) }?.reference)
                    }
                    viewModel.updateSelectedTarget(updated)
                })
                when (target.targetMode) {
                    SuperstructureTargetMode.CONSTANT -> ConstantTargetEditor(option, selected.stateId, target, viewModel)
                    SuperstructureTargetMode.PASS_THROUGH -> {
                        val choices = state.sourceFields.filter { it.field.type == option.field.type }
                        StudioDropdown(
                            label = "Source: ${choices.singleOrNull { it.reference == target.source }?.label ?: "choose a matching field"}",
                            options = choices.map { key(it) to it.label },
                            onSelect = { selectedKey ->
                                val source = choices.single { key(it) == selectedKey }
                                viewModel.updateSelectedTarget(target.copy(source = source.reference))
                            },
                        )
                    }
                    SuperstructureTargetMode.DYNAMIC_LUT -> {
                        val numeric = state.sourceFields.filter { it.field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT) }
                        StudioDropdown("Input: ${numeric.singleOrNull { it.reference == target.source }?.label ?: "choose numeric evidence"}", numeric.map { key(it) to it.label }, onSelect = { selectedKey ->
                            viewModel.updateSelectedTarget(target.copy(source = numeric.single { key(it) == selectedKey }.reference))
                        })
                        StudioDropdown("Table: ${target.lutId ?: "create a lookup table first"}", draft.luts.map { it.lutId to it.displayName.ifBlank { it.lutId } }, onSelect = { id ->
                            viewModel.updateSelectedTarget(target.copy(lutId = id))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConstantTargetEditor(option: SuperstructureFieldOption, stateId: String, target: SuperstructureSubsystemTarget, viewModel: SuperstructureStudioViewModel) {
    when (option.field.type) {
        SubsystemValueType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(target.constantBooleanValue == true, { viewModel.updateSelectedTarget(target.copy(constantBooleanValue = it)) })
            Text(if (target.constantBooleanValue == true) "Enabled" else "Disabled", color = AresTextPrimary)
        }
        SubsystemValueType.STRING -> OutlinedTextField(
            target.constantStringValue.orEmpty(),
            { viewModel.updateSelectedTarget(target.copy(constantStringValue = it.take(120))) },
            label = { Text("Requested text") },
            modifier = Modifier.fillMaxWidth(),
        )
        SubsystemValueType.DOUBLE, SubsystemValueType.INT -> {
            var raw by remember(target.target.subsystemUid, target.target.fieldUid, target.constantDoubleValue) { mutableStateOf(target.constantDoubleValue?.toString().orEmpty()) }
            val parsed = raw.toDoubleOrNull()
            val invalid = parsed == null || option.field.minimum?.let { parsed < it } == true || option.field.maximum?.let { parsed > it } == true
            OutlinedTextField(
                raw,
                { value ->
                    raw = value
                    val numeric = value.toDoubleOrNull()
                    val error = when {
                        numeric == null -> "Enter a numeric value for ${option.label}."
                        option.field.minimum?.let { numeric < it } == true || option.field.maximum?.let { numeric > it } == true -> "${option.label} is outside its declared bounds."
                        else -> null
                    }
                    viewModel.setEditorError("target:$stateId:${key(option)}", error)
                    if (error == null) viewModel.updateSelectedTarget(target.copy(constantDoubleValue = numeric))
                },
                label = { Text("Requested value${option.field.unit?.let { " ($it)" }.orEmpty()}") },
                modifier = Modifier.fillMaxWidth(),
                isError = invalid,
                supportingText = { Text("Allowed: ${option.field.minimum ?: "unbounded"} to ${option.field.maximum ?: "unbounded"}") },
            )
        }
    }
}

@Composable
private fun TransitionsStep(state: SuperstructureStudioState, draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    var source by remember(draft.superstructureId) { mutableStateOf(draft.initialStateId) }
    var target by remember(draft.superstructureId) { mutableStateOf(draft.states.firstOrNull { it.stateId != draft.initialStateId }?.stateId ?: draft.initialStateId) }
    var kind by remember(draft.superstructureId) { mutableStateOf(TransitionTriggerKind.ACTION_REQUEST) }
    var action by remember(draft.superstructureId) { mutableStateOf(state.parameterlessActions.firstOrNull()?.key.orEmpty()) }
    var sensorKey by remember(draft.superstructureId) { mutableStateOf(state.sourceFields.firstOrNull()?.let(::key).orEmpty()) }
    var seconds by remember(draft.superstructureId) { mutableStateOf("1.0") }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("State Transitions & Choreography", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Model transitions between robot postures visually on the Stateflow canvas or via structured lists.", color = AresTextSecondary, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.stateflowGraphMode,
                onClick = { viewModel.setStateflowGraphMode(true) },
                label = { Text("Stateflow Canvas") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AresCyan.copy(alpha = 0.2f),
                    selectedLabelColor = AresCyan,
                ),
            )
            FilterChip(
                selected = !state.stateflowGraphMode,
                onClick = { viewModel.setStateflowGraphMode(false) },
                label = { Text("Transition List") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AresCyan.copy(alpha = 0.2f),
                    selectedLabelColor = AresCyan,
                ),
            )
        }
    }

    if (state.stateflowGraphMode) {
        StateflowGraphCanvas(
            state = state,
            draft = draft,
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth().height(400.dp),
        )
    }

    StudioSection("Add a transition", "A transition changes from one complete posture to another. Driver/autonomous actions come from the real project catalog; sensors come from cached generated state.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioDropdown("From: $source", draft.states.map { it.stateId to it.displayName.ifBlank { it.stateId } }, { source = it }, Modifier.weight(1f))
            StudioDropdown("To: $target", draft.states.filter { it.stateId != source }.map { it.stateId to it.displayName.ifBlank { it.stateId } }, { target = it }, Modifier.weight(1f))
            StudioDropdown("Trigger: ${kind.name.replace('_', ' ')}", TransitionTriggerKind.entries.map { it.name to triggerLabel(it) }, { kind = TransitionTriggerKind.valueOf(it) }, Modifier.weight(1f))
        }
        when (kind) {
            TransitionTriggerKind.ACTION_REQUEST -> StudioDropdown(
                "Action: ${state.parameterlessActions.singleOrNull { it.key == action }?.displayName ?: "choose a project action"}",
                state.parameterlessActions.map { it.key to "${it.category} · ${it.displayName}" },
                { action = it },
            )
            TransitionTriggerKind.SENSOR_CONDITION_AUTO -> StudioDropdown(
                "Evidence: ${state.sourceFields.singleOrNull { key(it) == sensorKey }?.label ?: "choose cached evidence"}",
                state.sourceFields.map { key(it) to it.label },
                { sensorKey = it },
            )
            TransitionTriggerKind.TIME_ELAPSED -> OutlinedTextField(seconds, { seconds = it }, label = { Text("Seconds in source posture") })
        }
        Button(
            onClick = {
                when (kind) {
                    TransitionTriggerKind.ACTION_REQUEST -> viewModel.addActionTransition(source, target, action)
                    TransitionTriggerKind.SENSOR_CONDITION_AUTO -> state.sourceFields.singleOrNull { key(it) == sensorKey }?.let { viewModel.addSensorTransition(source, target, it) }
                    TransitionTriggerKind.TIME_ELAPSED -> seconds.toDoubleOrNull()?.let { viewModel.addTimedTransition(source, target, it) }
                }
            },
            enabled = source != target && when (kind) {
                TransitionTriggerKind.ACTION_REQUEST -> action.isNotBlank()
                TransitionTriggerKind.SENSOR_CONDITION_AUTO -> sensorKey.isNotBlank()
                TransitionTriggerKind.TIME_ELAPSED -> seconds.toDoubleOrNull()?.let { it > 0.0 } == true
            },
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
        ) { Text("Add transition") }
    }

    if (!state.stateflowGraphMode) {
        draft.transitions.forEach { edge -> TransitionCard(state, draft, edge, viewModel) }
        if (draft.transitions.isEmpty()) StudioBanner("No transitions yet", "Only the initial posture can be reached. Add explicit routes to every other posture.", AresGold)
    }
}

@Composable
private fun StateflowGraphCanvas(
    state: SuperstructureStudioState,
    draft: SuperstructureDocument,
    viewModel: SuperstructureStudioViewModel,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val nodeWidth = 190f
    val nodeHeight = 80f

    val nodePositions = remember(draft.states, draft.nodeLayouts) {
        val map = mutableMapOf<String, Offset>()
        val cols = 3
        draft.states.forEachIndexed { index, statePreset ->
            val layout = draft.nodeLayouts[statePreset.stateId]
            if (layout != null) {
                map[statePreset.stateId] = Offset(layout.x.toFloat(), layout.y.toFloat())
            } else {
                val col = index % cols
                val row = index / cols
                map[statePreset.stateId] = Offset(40f + col * 230f, 30f + row * 130f)
            }
        }
        map
    }

    var draggedNodeId by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(draft.states, draft.nodeLayouts) {
                        detectTapGestures { tapOffset ->
                            val clicked = nodePositions.entries.firstOrNull { (_, pos) ->
                                tapOffset.x >= pos.x && tapOffset.x <= pos.x + nodeWidth &&
                                tapOffset.y >= pos.y && tapOffset.y <= pos.y + nodeHeight
                            }
                            if (clicked != null) {
                                viewModel.selectState(clicked.key)
                            }
                        }
                    }
                    .pointerInput(draft.states, draft.nodeLayouts) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val hit = nodePositions.entries.firstOrNull { (_, pos) ->
                                    startOffset.x >= pos.x && startOffset.x <= pos.x + nodeWidth &&
                                    startOffset.y >= pos.y && startOffset.y <= pos.y + nodeHeight
                                }
                                if (hit != null) {
                                    draggedNodeId = hit.key
                                    viewModel.selectState(hit.key)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedNodeId?.let { id ->
                                    val currentPos = nodePositions[id] ?: Offset.Zero
                                    val newPos = currentPos + dragAmount
                                    val clampedX = max(10f, min(1400f, newPos.x))
                                    val clampedY = max(10f, min(900f, newPos.y))
                                    viewModel.moveStateNode(id, clampedX.toDouble(), clampedY.toDouble())
                                }
                            },
                            onDragEnd = { draggedNodeId = null },
                            onDragCancel = { draggedNodeId = null },
                        )
                    }
            ) {
                clipRect {
                    // 1. Background dot grid
                    val dotSpacing = 24f
                    val dotColor = AresBorder.copy(alpha = 0.35f)
                    var x = 0f
                    while (x < size.width) {
                        var y = 0f
                        while (y < size.height) {
                            drawCircle(dotColor, radius = 1f, center = Offset(x, y))
                            y += dotSpacing
                        }
                        x += dotSpacing
                    }

                    // 2. Directed Bezier Transition Curves
                    draft.transitions.forEach { edge ->
                        val srcPos = nodePositions[edge.sourceStateId] ?: return@forEach
                        val tgtPos = nodePositions[edge.targetStateId] ?: return@forEach

                        val srcCenter = Offset(srcPos.x + nodeWidth / 2f, srcPos.y + nodeHeight / 2f)
                        val tgtCenter = Offset(tgtPos.x + nodeWidth / 2f, tgtPos.y + nodeHeight / 2f)

                        val dx = tgtCenter.x - srcCenter.x
                        val dy = tgtCenter.y - srcCenter.y
                        val dist = hypot(dx, dy)
                        if (dist < 1f) return@forEach

                        val nx = dx / dist
                        val ny = dy / dist

                        val startPt = Offset(srcCenter.x + nx * (nodeWidth / 2.2f), srcCenter.y + ny * (nodeHeight / 2.2f))
                        val endPt = Offset(tgtCenter.x - nx * (nodeWidth / 2.2f), tgtCenter.y - ny * (nodeHeight / 2.2f))

                        val perpX = -ny * 30f
                        val perpY = nx * 30f

                        val ctrl1 = Offset(startPt.x + dx * 0.35f + perpX, startPt.y + dy * 0.35f + perpY)
                        val ctrl2 = Offset(startPt.x + dx * 0.65f + perpX, startPt.y + dy * 0.65f + perpY)

                        val curveColor = when (edge.triggerKind) {
                            TransitionTriggerKind.ACTION_REQUEST -> AresCyan
                            TransitionTriggerKind.SENSOR_CONDITION_AUTO -> AresGreen
                            TransitionTriggerKind.TIME_ELAPSED -> AresGold
                        }

                        val path = Path().apply {
                            moveTo(startPt.x, startPt.y)
                            cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, endPt.x, endPt.y)
                        }
                        drawPath(path, color = curveColor.copy(alpha = 0.85f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))

                        // Arrowhead
                        val arrowAngle = atan2(endPt.y - ctrl2.y, endPt.x - ctrl2.x)
                        val arrowSize = 10f
                        val p1 = endPt
                        val p2 = Offset(endPt.x - arrowSize * cos(arrowAngle - 0.45f), endPt.y - arrowSize * sin(arrowAngle - 0.45f))
                        val p3 = Offset(endPt.x - arrowSize * cos(arrowAngle + 0.45f), endPt.y - arrowSize * sin(arrowAngle + 0.45f))

                        val arrowPath = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            close()
                        }
                        drawPath(arrowPath, color = curveColor, style = Fill)

                        // Trigger badge
                        val midPt = Offset((ctrl1.x + ctrl2.x) / 2f, (ctrl1.y + ctrl2.y) / 2f)
                        val labelText = when (edge.triggerKind) {
                            TransitionTriggerKind.ACTION_REQUEST -> edge.actionKey ?: "action"
                            TransitionTriggerKind.SENSOR_CONDITION_AUTO -> "auto guard"
                            TransitionTriggerKind.TIME_ELAPSED -> "${edge.timeoutSeconds ?: 1.0}s"
                        }
                        val measured = textMeasurer.measure(labelText, TextStyle(color = AresTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
                        val badgeWidth = measured.size.width + 10f
                        val badgeHeight = measured.size.height + 4f
                        val badgeTopLeft = Offset(midPt.x - badgeWidth / 2f, midPt.y - badgeHeight / 2f)

                        drawRoundRect(
                            color = AresSurfaceElevated.copy(alpha = 0.95f),
                            topLeft = badgeTopLeft,
                            size = Size(badgeWidth, badgeHeight),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                        drawRoundRect(
                            color = curveColor.copy(alpha = 0.5f),
                            topLeft = badgeTopLeft,
                            size = Size(badgeWidth, badgeHeight),
                            cornerRadius = CornerRadius(4f, 4f),
                            style = Stroke(width = 1f),
                        )
                        drawText(
                            textMeasurer,
                            labelText,
                            topLeft = Offset(badgeTopLeft.x + 5f, badgeTopLeft.y + 2f),
                            style = TextStyle(color = curveColor, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        )
                    }

                    // 3. State Node Cards
                    draft.states.forEach { statePreset ->
                        val pos = nodePositions[statePreset.stateId] ?: return@forEach
                        val isSelected = state.selectedStateId == statePreset.stateId
                        val isInitial = draft.initialStateId == statePreset.stateId
                        val isFault = draft.faultStateId == statePreset.stateId
                        val isDisabled = draft.disabledStateId == statePreset.stateId

                        val borderColor = when {
                            isSelected -> AresCyan
                            isInitial -> AresCyan.copy(alpha = 0.85f)
                            isFault -> AresError.copy(alpha = 0.85f)
                            isDisabled -> AresGold.copy(alpha = 0.85f)
                            else -> AresBorder
                        }

                        drawRoundRect(
                            color = AresSurfaceElevated,
                            topLeft = pos,
                            size = Size(nodeWidth, nodeHeight),
                            cornerRadius = CornerRadius(8f, 8f),
                        )
                        drawRoundRect(
                            color = borderColor,
                            topLeft = pos,
                            size = Size(nodeWidth, nodeHeight),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = if (isSelected) 2.2f else 1.2f),
                        )

                        // Top accent line
                        drawRoundRect(
                            color = borderColor,
                            topLeft = pos,
                            size = Size(nodeWidth, 3.5f),
                            cornerRadius = CornerRadius(8f, 8f),
                        )

                        // State Title
                        val title = statePreset.displayName.ifBlank { statePreset.stateId }
                        drawText(
                            textMeasurer,
                            title,
                            topLeft = Offset(pos.x + 10f, pos.y + 10f),
                            style = TextStyle(color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        )

                        // State ID
                        drawText(
                            textMeasurer,
                            statePreset.stateId,
                            topLeft = Offset(pos.x + 10f, pos.y + 26f),
                            style = TextStyle(color = AresTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                        )

                        // Targets count
                        drawText(
                            textMeasurer,
                            "${statePreset.subsystemTargets.size} targets · ${statePreset.onEntryActionKeys.size} entry",
                            topLeft = Offset(pos.x + 10f, pos.y + 50f),
                            style = TextStyle(color = AresCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Medium),
                        )

                        val roleTag = when {
                            isInitial -> "INITIAL"
                            isFault -> "FAULT"
                            isDisabled -> "DISABLED"
                            else -> ""
                        }
                        if (roleTag.isNotBlank()) {
                            val tagMeasured = textMeasurer.measure(roleTag, TextStyle(color = borderColor, fontSize = 7.5.sp, fontWeight = FontWeight.Bold))
                            val tagW = tagMeasured.size.width + 6f
                            val tagH = tagMeasured.size.height + 3f
                            val tagPos = Offset(pos.x + nodeWidth - tagW - 6f, pos.y + 8f)

                            drawRoundRect(
                                color = borderColor.copy(alpha = 0.15f),
                                topLeft = tagPos,
                                size = Size(tagW, tagH),
                                cornerRadius = CornerRadius(3f, 3f),
                            )
                            drawText(
                                textMeasurer,
                                roleTag,
                                topLeft = Offset(tagPos.x + 3f, tagPos.y + 1.5f),
                                style = TextStyle(color = borderColor, fontSize = 7.5.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionCard(state: SuperstructureStudioState, draft: SuperstructureDocument, edge: StateTransitionEdge, viewModel: SuperstructureStudioViewModel) {
    StudioSection("${edge.sourceStateId} → ${edge.targetStateId}", triggerLabel(edge.triggerKind)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(edge.triggerKind.name.replace('_', ' '), AresCyan)
            edge.actionKey?.let { Text(state.actions.singleOrNull { action -> action.key == it }?.displayName ?: it, color = AresTextPrimary) }
            Spacer(Modifier.weight(1f))
            IconButton({ viewModel.removeTransition(edge.transitionId) }) { Icon(Icons.Default.Delete, "Remove transition ${edge.transitionId}") }
        }
        if (edge.guards.isNotEmpty()) {
            edge.guards.forEach { guard ->
                val source = state.sourceFields.singleOrNull { it.reference == guard.source }
                val expectedDouble = guard.expectedDoubleValue
                val expectedBoolean = guard.expectedBooleanValue
                val expectedString = guard.expectedStringValue
                Text("${guard.source.healthRequirement.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercaseChar)} · ${source?.label ?: "${guard.source.subsystemUid}.${guard.source.fieldUid}"}", color = AresTextSecondary, fontSize = 11.sp)
                StudioDropdown(
                    "Evidence health: ${guard.source.healthRequirement.name.replace('_', ' ')}",
                    SuperstructurePortHealthRequirement.entries.map { it.name to when (it) {
                        SuperstructurePortHealthRequirement.VALUE_ONLY -> "Value only (advanced)"
                        SuperstructurePortHealthRequirement.FRESH_VALID -> "Fresh and valid"
                        SuperstructurePortHealthRequirement.CONTROL_READY -> "Control ready (recommended)"
                    } },
                    onSelect = { selected ->
                        viewModel.updateTransition(edge.copy(guards = edge.guards.map {
                            if (it.guardId == guard.guardId) it.copy(source = it.source.copy(healthRequirement = SuperstructurePortHealthRequirement.valueOf(selected))) else it
                        }))
                    },
                )
                when {
                    expectedDouble != null -> {
                        StudioDropdown(
                            "Comparison: ${guard.comparison.name.replace('_', ' ')}",
                            listOf(InterlockComparison.LESS_THAN, InterlockComparison.GREATER_THAN).map { it.name to it.name.replace('_', ' ') },
                            onSelect = { selected -> viewModel.updateTransition(edge.copy(guards = edge.guards.map { if (it.guardId == guard.guardId) it.copy(comparison = InterlockComparison.valueOf(selected)) else it })) },
                        )
                        var raw by remember(guard.guardId, expectedDouble) { mutableStateOf(expectedDouble.toString()) }
                        OutlinedTextField(raw, { value ->
                            raw = value
                            value.toDoubleOrNull()?.let { parsed ->
                                viewModel.updateTransition(edge.copy(guards = edge.guards.map { if (it.guardId == guard.guardId) it.copy(expectedDoubleValue = parsed) else it }))
                            }
                        }, label = { Text("Expected numeric threshold") })
                    }
                    expectedBoolean != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        StudioDropdown(
                            "Comparison: ${guard.comparison.name.replace('_', ' ')}",
                            listOf(InterlockComparison.EQUALS_STATE, InterlockComparison.NOT_EQUALS_STATE).map { it.name to it.name.replace('_', ' ') },
                            onSelect = { selected -> viewModel.updateTransition(edge.copy(guards = edge.guards.map { if (it.guardId == guard.guardId) it.copy(comparison = InterlockComparison.valueOf(selected)) else it })) },
                            modifier = Modifier.weight(1f),
                        )
                        Switch(expectedBoolean, { checked -> viewModel.updateTransition(edge.copy(guards = edge.guards.map { if (it.guardId == guard.guardId) it.copy(expectedBooleanValue = checked) else it })) })
                        Text("Expected $expectedBoolean", color = AresTextPrimary)
                    }
                    expectedString != null -> {
                        StudioDropdown(
                            "Comparison: ${guard.comparison.name.replace('_', ' ')}",
                            listOf(InterlockComparison.EQUALS_STATE, InterlockComparison.NOT_EQUALS_STATE).map { it.name to it.name.replace('_', ' ') },
                            onSelect = { selected -> viewModel.updateTransition(edge.copy(guards = edge.guards.map { if (it.guardId == guard.guardId) it.copy(comparison = InterlockComparison.valueOf(selected)) else it })) },
                        )
                        OutlinedTextField(
                            expectedString,
                            { text -> viewModel.updateTransition(edge.copy(guards = edge.guards.map { if (it.guardId == guard.guardId) it.copy(expectedStringValue = text) else it })) },
                            label = { Text("Expected text") },
                        )
                    }
                }
                TextButton({ viewModel.removeGuard(edge.transitionId, guard.guardId) }) { Text("Remove this guard") }
            }
        }
        val unusedSources = state.sourceFields.filter { candidate -> edge.guards.none { it.source == candidate.reference } }
        if (unusedSources.isNotEmpty() && edge.triggerKind != TransitionTriggerKind.TIME_ELAPSED) {
            StudioDropdown(
                "+ Require fresh evidence",
                unusedSources.map { key(it) to it.label },
                onSelect = { selected -> unusedSources.single { key(it) == selected }.let { viewModel.addGuard(edge.transitionId, it) } },
            )
        }
        if (edge.triggerKind == TransitionTriggerKind.SENSOR_CONDITION_AUTO) {
            NumberEditor("Priority (lower runs first)", edge.priority.toDouble(), onValue = { viewModel.updateTransition(edge.copy(priority = it.toInt().coerceIn(0, 10_000))) }, onValidity = { valid -> viewModel.setEditorError("transition:${edge.transitionId}:priority", if (valid) null else "${edge.transitionId} priority must be numeric.") })
            NumberEditor("Debounce (ms)", edge.debounceMs.toDouble(), onValue = { viewModel.updateTransition(edge.copy(debounceMs = it.toLong().coerceIn(0, 60_000))) }, onValidity = { valid -> viewModel.setEditorError("transition:${edge.transitionId}:debounce", if (valid) null else "${edge.transitionId} debounce must be numeric.") })
        }
        if (edge.triggerKind == TransitionTriggerKind.TIME_ELAPSED) {
            NumberEditor("Priority (lower runs first)", edge.priority.toDouble(), onValue = { viewModel.updateTransition(edge.copy(priority = it.toInt().coerceIn(0, 10_000))) }, onValidity = { valid -> viewModel.setEditorError("transition:${edge.transitionId}:priority", if (valid) null else "${edge.transitionId} priority must be numeric.") })
            NumberEditor("Seconds", edge.timeoutSeconds ?: 1.0, onValue = { viewModel.updateTransition(edge.copy(timeoutSeconds = it)) }, onValidity = { valid -> viewModel.setEditorError("transition:${edge.transitionId}:seconds", if (valid) null else "${edge.transitionId} time must be numeric.") })
        }
        if (edge.triggerKind == TransitionTriggerKind.ACTION_REQUEST && edge.guards.isNotEmpty()) {
            Text("Guarded requests need a deadline that falls back to a safe posture.", color = AresGold, fontSize = 11.sp)
            NumberEditor("Request deadline (seconds)", edge.timeoutSeconds ?: 1.0, onValue = { value ->
                viewModel.updateTransition(edge.copy(timeoutSeconds = value, timeoutTargetStateId = edge.timeoutTargetStateId ?: draft.faultStateId))
            }, onValidity = { valid -> viewModel.setEditorError("transition:${edge.transitionId}:deadline", if (valid) null else "${edge.transitionId} deadline must be numeric.") })
            StudioDropdown("Timeout posture: ${edge.timeoutTargetStateId ?: draft.faultStateId}", draft.states.map { it.stateId to it.displayName.ifBlank { it.stateId } }, onSelect = {
                viewModel.updateTransition(edge.copy(timeoutSeconds = edge.timeoutSeconds ?: 1.0, timeoutTargetStateId = it))
            })
        }
    }
}

@Composable
private fun InterlocksStep(state: SuperstructureStudioState, draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    val numericSources = state.sourceFields.filter { it.field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT) }
    val numericTargets = state.targetFields.filter { it.field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT) }
    var sourceKey by remember(draft.superstructureId) { mutableStateOf(numericSources.firstOrNull()?.let(::key).orEmpty()) }
    var targetKey by remember(draft.superstructureId) { mutableStateOf(numericTargets.firstOrNull()?.let(::key).orEmpty()) }
    StudioSection("Add a cross-mechanism interlock", "Example: while the arm is below 30°, clamp elevator extension. The runtime evaluates cached Redux values and adjusts the complete target preset before dispatch.") {
        StudioDropdown("Evidence: ${numericSources.singleOrNull { key(it) == sourceKey }?.label ?: "choose numeric evidence"}", numericSources.map { key(it) to it.label }, { sourceKey = it })
        StudioDropdown("Clamp: ${numericTargets.singleOrNull { key(it) == targetKey }?.label ?: "choose numeric target"}", numericTargets.map { key(it) to it.label }, { targetKey = it })
        Button(
            onClick = {
                val source = numericSources.singleOrNull { key(it) == sourceKey }
                val target = numericTargets.singleOrNull { key(it) == targetKey }
                if (source != null && target != null) viewModel.addInterlock(source, target)
            },
            enabled = sourceKey.isNotBlank() && targetKey.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
        ) { Text("Add interlock") }
    }
    draft.interlocks.forEach { rule ->
        StudioSection(rule.ruleId, rule.description) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioDropdown("When: ${rule.conditionComparison.name.replace('_', ' ')}", InterlockComparison.entries.map { it.name to it.name.replace('_', ' ') }, { viewModel.updateInterlock(rule.copy(conditionComparison = InterlockComparison.valueOf(it))) }, Modifier.weight(1f))
                NumberEditor("Evidence threshold", rule.conditionThreshold, { viewModel.updateInterlock(rule.copy(conditionThreshold = it)) }, Modifier.weight(1f), onValidity = { valid -> viewModel.setEditorError("interlock:${rule.ruleId}:threshold", if (valid) null else "${rule.ruleId} threshold must be numeric.") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberEditor("Clamp minimum", rule.clampMinimum ?: 0.0, { viewModel.updateInterlock(rule.copy(clampMinimum = it)) }, Modifier.weight(1f), onValidity = { valid -> viewModel.setEditorError("interlock:${rule.ruleId}:min", if (valid) null else "${rule.ruleId} minimum must be numeric.") })
                NumberEditor("Clamp maximum", rule.clampMaximum ?: 0.0, { viewModel.updateInterlock(rule.copy(clampMaximum = it)) }, Modifier.weight(1f), onValidity = { valid -> viewModel.setEditorError("interlock:${rule.ruleId}:max", if (valid) null else "${rule.ruleId} maximum must be numeric.") })
                IconButton({ viewModel.removeInterlock(rule.ruleId) }) { Icon(Icons.Default.Delete, "Remove interlock ${rule.ruleId}") }
            }
        }
    }
    StudioSection("Sensor-health fallbacks", "A health policy supervises one cached typed port before transitions or targets run. CONTROL READY includes freshness, validity, configuration, homing/calibration, current validity, and output health.") {
        val available = state.sourceFields.filter { candidate -> draft.healthFallbacks.none { it.source.subsystemUid == candidate.reference.subsystemUid && it.source.fieldUid == candidate.reference.fieldUid } }
        if (available.isNotEmpty()) {
            StudioDropdown("+ Add a cached-port health policy", available.map { key(it) to it.label }, onSelect = { selectedKey ->
                available.single { key(it) == selectedKey }.let(viewModel::addHealthFallback)
            })
        } else if (state.sourceFields.isEmpty()) {
            Text("Create a generated subsystem with cached state fields first.", color = AresGold, fontSize = 11.sp)
        }
    }
    draft.healthFallbacks.forEach { policy ->
        val option = state.sourceFields.singleOrNull {
            it.reference.subsystemUid == policy.source.subsystemUid && it.reference.fieldUid == policy.source.fieldUid
        }
        StudioSection(policy.policyId, policy.description.ifBlank { option?.label ?: "Cached port health policy" }) {
            Text(option?.label ?: "${policy.source.subsystemUid}.${policy.source.fieldUid}", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            StudioDropdown(
                "Required health: ${policy.source.healthRequirement.name.replace('_', ' ')}",
                SuperstructurePortHealthRequirement.entries.map { it.name to when (it) {
                    SuperstructurePortHealthRequirement.VALUE_ONLY -> "Value only (advanced; no freshness guarantee)"
                    SuperstructurePortHealthRequirement.FRESH_VALID -> "Fresh and valid cached sample"
                    SuperstructurePortHealthRequirement.CONTROL_READY -> "Control ready (recommended)"
                } },
                onSelect = { requirement -> viewModel.updateHealthFallback(policy.copy(source = policy.source.copy(healthRequirement = SuperstructurePortHealthRequirement.valueOf(requirement)))) },
            )
            StudioDropdown("Fallback posture: ${policy.fallbackStateId}", draft.states.map { it.stateId to it.displayName.ifBlank { it.stateId } }, onSelect = { viewModel.updateHealthFallback(policy.copy(fallbackStateId = it)) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(policy.latchFault, { viewModel.updateHealthFallback(policy.copy(latchFault = it)) })
                Text(if (policy.latchFault) "Latch fault until an explicit legal recovery request" else "Allow automatic recovery when healthy", color = AresTextPrimary)
                Spacer(Modifier.weight(1f))
                IconButton({ viewModel.removeHealthFallback(policy.policyId) }) { Icon(Icons.Default.Delete, "Remove health policy ${policy.policyId}") }
            }
        }
    }
}

@Composable
private fun LookupTablesStep(draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    StudioSection("Lookup tables", "Use a reviewed table when one mechanism target depends on a measured value, such as shooter speed from distance. This editor is not a physics simulation.") {
        Button(viewModel::addLut, colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent)) { Text("Add lookup table") }
    }
    draft.luts.forEach { lut ->
        StudioSection(lut.displayName.ifBlank { lut.lutId }, "Inputs must be strictly increasing. Runtime interpolation is deterministic and allocation-free.") {
            OutlinedTextField(lut.displayName, { viewModel.updateLut(lut.copy(displayName = it.take(80))) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(lut.inputUnit, { viewModel.updateLut(lut.copy(inputUnit = it.trim().take(24))) }, label = { Text("Canonical input unit") }, supportingText = { Text("Must match the selected source port") }, modifier = Modifier.weight(1f))
                OutlinedTextField(lut.outputUnit, { viewModel.updateLut(lut.copy(outputUnit = it.trim().take(24))) }, label = { Text("Canonical output unit") }, supportingText = { Text("Must match the target port") }, modifier = Modifier.weight(1f))
            }
            StudioDropdown("Interpolation: ${lut.interpolation.name.replace('_', ' ')}", LutInterpolationMethod.entries.map { it.name to it.name.replace('_', ' ') }, onSelect = {
                viewModel.updateLut(lut.copy(interpolation = LutInterpolationMethod.valueOf(it)))
            })
            lut.controlPoints.forEachIndexed { index, point ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberEditor("Input ${index + 1}", point.inputX, { value -> viewModel.updateLut(lut.copy(controlPoints = lut.controlPoints.mapIndexed { i, p -> if (i == index) p.copy(inputX = value) else p }.sortedBy { it.inputX })) }, Modifier.weight(1f), onValidity = { valid -> viewModel.setEditorError("lut:${lut.lutId}:input:$index", if (valid) null else "${lut.displayName} input ${index + 1} must be numeric.") })
                    NumberEditor("Output ${index + 1}", point.outputY, { value -> viewModel.updateLut(lut.copy(controlPoints = lut.controlPoints.mapIndexed { i, p -> if (i == index) p.copy(outputY = value) else p })) }, Modifier.weight(1f), onValidity = { valid -> viewModel.setEditorError("lut:${lut.lutId}:output:$index", if (valid) null else "${lut.displayName} output ${index + 1} must be numeric.") })
                    IconButton({ if (lut.controlPoints.size > 2) viewModel.updateLut(lut.copy(controlPoints = lut.controlPoints.filterIndexed { i, _ -> i != index })) }) { Icon(Icons.Default.Delete, "Remove lookup point ${index + 1}") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    val last = lut.controlPoints.lastOrNull() ?: LutControlPoint(0.0, 0.0)
                    viewModel.updateLut(lut.copy(controlPoints = lut.controlPoints + LutControlPoint(last.inputX + 1.0, last.outputY)))
                }) { Text("Add point") }
                OutlinedButton({ viewModel.removeLut(lut.lutId) }) { Text("Remove table") }
            }
        }
    }
}

@Composable
private fun SimulationStep(state: SuperstructureStudioState, draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    val preview = state.preview
    StudioSection("Deterministic transition lab", "This runs the production state-machine evaluator against editable cached values. It does not model mechanism physics, wiring, current draw, or prove physical safety.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(viewModel::startPreview, colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent)) {
                Text(if (preview == null) "Start preview" else "Reset preview")
            }
            if (preview != null) {
                OutlinedButton({ viewModel.advancePreview(20L) }) { Text("+20 ms") }
                OutlinedButton({ viewModel.advancePreview(100L) }) { Text("+100 ms") }
                OutlinedButton({ viewModel.advancePreview(1_000L) }) { Text("+1 s") }
                Spacer(Modifier.weight(1f))
                Text("Robot enabled", color = AresTextPrimary, fontSize = 11.sp)
                Switch(preview.isEnabled, { viewModel.setPreviewEnabled(it) })
            }
        }
    }
    if (preview == null) {
        StudioBanner("Preview not running", "Resolve validation errors, then start the lab. All injected faults remain inside this editor session.", AresGold)
        return
    }
    StudioSection("Runtime trace", "Every row below comes from the same immutable runtime state emitted on a robot or simulator.") {
        ReviewLine("Time / state age", "${preview.nowMs} ms / ${preview.stateAgeMs} ms")
        ReviewLine("State", "${preview.previousStateId} → ${preview.currentStateId}")
        ReviewLine("Transition sequence", preview.transitionSequence.toString())
        ReviewLine("Debounce candidate", preview.candidateTransitionId ?: "none")
        ReviewLine("Faulted", if (preview.isFaulted) "YES · ${preview.faultReason.orEmpty()}" else "NO")
        preview.lastRejectionReason?.let { Text("REJECTED · $it", color = AresGold, fontSize = 11.sp) }
        preview.lastLifecycleError?.let { Text("LIFECYCLE FAILURE · $it", color = AresError, fontSize = 11.sp) }
        if (preview.lifecycleActions.isNotEmpty()) Text("Lifecycle order · ${preview.lifecycleActions.joinToString(" → ")}", color = AresTextSecondary, fontSize = 11.sp)
        val availableRequests = draft.transitions.filter {
            it.sourceStateId == preview.currentStateId && it.triggerKind == TransitionTriggerKind.ACTION_REQUEST
        }.mapNotNull { it.actionKey }.distinct()
        if (availableRequests.isNotEmpty()) {
            StudioDropdown(
                "Request a legal action from ${preview.currentStateId}",
                availableRequests.map { key -> key to (state.actions.singleOrNull { it.key == key }?.displayName ?: key) },
                onSelect = viewModel::requestPreviewAction,
            )
        } else Text("No action-request transition leaves this posture.", color = AresTextSecondary, fontSize = 11.sp)
    }
    StudioSection("Cached ports and fault injection", "A healthy false/zero value is different from stale or invalid communication. Changing a value refreshes its sample timestamp.") {
        preview.ports.forEach { port ->
            Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, AresBorder)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(port.label, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("${port.condition.name.replace('_', ' ')} · age ${port.ageMs} ms · health bits 0x${port.healthBits.toString(16)}", color = if (port.condition == PreviewPortCondition.HEALTHY) AresGreen else AresGold, fontSize = 10.sp)
                    when (port.type) {
                        SubsystemValueType.DOUBLE, SubsystemValueType.INT -> NumberEditor(
                            "Cached value${port.unit?.let { " ($it)" }.orEmpty()}",
                            port.numericValue ?: 0.0,
                            onValue = { viewModel.setPreviewNumeric(port.reference, it) },
                        )
                        SubsystemValueType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(port.booleanValue == true, { viewModel.setPreviewBoolean(port.reference, it) })
                            Text(if (port.booleanValue == true) "TRUE" else "FALSE", color = AresTextPrimary)
                        }
                        SubsystemValueType.STRING -> OutlinedTextField(
                            port.stringValue.orEmpty(),
                            { viewModel.setPreviewString(port.reference, it) },
                            label = { Text("Cached text") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    StudioDropdown(
                        "Inject condition: ${port.condition.name.replace('_', ' ')}",
                        PreviewPortCondition.entries.map { it.name to it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercaseChar) },
                        onSelect = { viewModel.injectPreview(port.reference, PreviewPortCondition.valueOf(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(state: SuperstructureStudioState, draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    StudioSection("Build integration", "Saving updates the canonical input only. The normal robot build regenerates mechanical Kotlin, validates every field/action reference, compiles the runtime, and runs generated contract tests.") {
        ReviewLine("Coordinator ID", draft.superstructureId)
        ReviewLine("Startup posture", draft.initialStateId)
        ReviewLine("Fault posture", draft.faultStateId)
        ReviewLine("Disabled posture", "${draft.disabledStateId} · ${draft.disabledPolicy.name.replace('_', ' ').lowercase()}")
        ReviewLine("Complete target fields", draft.states.firstOrNull()?.subsystemTargets?.size?.toString() ?: "0")
        ReviewLine("Postures / transitions", "${draft.states.size} / ${draft.transitions.size}")
        ReviewLine("Interlocks / lookup tables", "${draft.interlocks.size} / ${draft.luts.size}")
        ReviewLine("Health fallbacks / lifecycle actions", "${draft.healthFallbacks.size} / ${draft.states.sumOf { it.onEntryActionKeys.size + it.onExitActionKeys.size }}")
        ReviewLine("Current saved hash", state.savedContentHash?.take(12) ?: "new document")
        HorizontalDivider(color = AresBorder)
        Text("Runtime guarantees", color = AresTextPrimary, fontWeight = FontWeight.Bold)
        listOf(
            "At most one transition is consumed per robot loop.",
            "All target tasks are preflighted before any target is dispatched.",
            "Missing tasks or failed application enter the explicit fault posture.",
            "Only cached generated state is read; hardware is never read from the coordinator.",
            "Typed ports validate stable IDs, canonical units, freshness, and configured health before generation.",
            "Steady unchanged evaluation is covered by a warmed-up zero-byte allocation regression; transitions may allocate tasks and immutable Redux events.",
        ).forEach { Text("✓ $it", color = AresTextSecondary, fontSize = 11.sp) }
        Button(
            viewModel::reviewSave,
            enabled = state.canSave,
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
        ) { Text("Review exact save") }
    }
}

@Composable
private fun ValidationRail(state: SuperstructureStudioState, modifier: Modifier) {
    Column(modifier.studioCard().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PROJECT CHECK", color = AresTextPrimary, fontWeight = FontWeight.Bold)
        val errorCount = state.validationErrors.size + state.editorErrors.size
        StatusChip(if (errorCount == 0) "READY TO REVIEW" else "$errorCount ERRORS", if (errorCount == 0) AresGreen else AresError)
        if (state.dirty) Text("UNSAVED DRAFT", color = AresGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        state.validationErrors.forEach { Text("✕ $it", color = AresError, fontSize = 10.sp) }
        state.editorErrors.values.distinct().forEach { Text("✕ $it", color = AresError, fontSize = 10.sp) }
        state.validationWarnings.forEach { Text("! $it", color = AresGold, fontSize = 10.sp) }
        if (errorCount == 0) Text("The current draft passes the same project-reference checks used before code generation.", color = AresTextSecondary, fontSize = 10.sp)
        val related = state.diagnostics.filter { it.kind.name in setOf("SUBSYSTEM", "SUPERSTRUCTURE", "CAPABILITY_CATALOG") }
        if (related.isNotEmpty()) {
            HorizontalDivider(color = AresBorder)
            Text("OTHER PROJECT FILES", color = AresTextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            related.take(12).forEach { Text("${it.file.name}: ${it.message}", color = AresGold, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun LifecycleActionEditor(
    label: String,
    selected: List<String>,
    state: SuperstructureStudioState,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(Locale.ROOT), color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        selected.forEach { key ->
            val descriptor = state.parameterlessActions.singleOrNull { it.key == key }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(descriptor?.displayName ?: key, color = AresTextPrimary, fontSize = 11.sp)
                    Text(key, color = AresTextTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                IconButton({ onRemove(key) }) { Icon(Icons.Default.Delete, "Remove $label action $key") }
            }
        }
        val available = state.parameterlessActions.filter { it.key !in selected }
        StudioDropdown(
            "+ Add ${label.lowercase()} action",
            available.map { it.key to "${it.category} · ${it.displayName}" },
            onSelect = onAdd,
        )
    }
}

@Composable
private fun CreateSuperstructureDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a mechanism coordinator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Use a coordinator only for mechanisms that must share complete postures, transitions, or interlocks.")
                OutlinedTextField(name, { value -> name = value; if (id.isBlank()) id = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') }, label = { Text("Student-facing name") })
                OutlinedTextField(id, { id = it }, label = { Text("Stable file ID") }, supportingText = { Text("Lowercase letters, numbers, and hyphens; this becomes the file name.") })
            }
        },
        confirmButton = { Button({ onCreate(id, name) }, enabled = id.isNotBlank()) { Text("Create draft") } },
        dismissButton = { OutlinedButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StudioSection(title: String, explanation: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AresSurface), border = BorderStroke(1.dp, AresBorder)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(explanation, color = AresTextSecondary, fontSize = 11.sp)
            content()
        }
    }
}

@Composable
private fun StudioBanner(title: String, message: String, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.09f)), border = BorderStroke(1.dp, accent.copy(alpha = 0.7f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(message, color = AresTextPrimary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatusCount(label: String, count: Int, color: Color) = Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, color)) {
    Text("$count $label", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun StatusChip(label: String, color: Color) = Surface(color = color.copy(alpha = 0.13f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, color)) {
    Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun ReviewLine(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = AresTextSecondary, fontSize = 11.sp)
    Text(value, color = AresTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun StudioDropdown(
    label: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, display) ->
                DropdownMenuItem(text = { Text(display) }, onClick = { expanded = false; onSelect(key) })
            }
        }
    }
}

@Composable
private fun NumberEditor(
    label: String,
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onValidity: (Boolean) -> Unit = {},
) {
    var raw by remember(label, value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        raw,
        { text ->
            raw = text
            val parsed = text.toDoubleOrNull()
            onValidity(parsed != null)
            parsed?.let(onValue)
        },
        label = { Text(label) },
        isError = raw.toDoubleOrNull() == null,
        modifier = modifier,
    )
}

private fun Modifier.studioCard(): Modifier = background(AresSurface, RoundedCornerShape(10.dp)).padding(10.dp)

private fun key(option: SuperstructureFieldOption): String = "${option.subsystem.uid}.${option.field.uid}"

private fun triggerLabel(kind: TransitionTriggerKind): String = when (kind) {
    TransitionTriggerKind.ACTION_REQUEST -> "Driver/autonomous action request"
    TransitionTriggerKind.SENSOR_CONDITION_AUTO -> "Fresh sensor condition"
    TransitionTriggerKind.TIME_ELAPSED -> "Time elapsed in posture"
}

private fun neutralFor(option: SuperstructureFieldOption): SuperstructureSubsystemTarget = when (option.field.type) {
    SubsystemValueType.DOUBLE -> SuperstructureSubsystemTarget(option.reference, constantDoubleValue = option.field.defaultNumber ?: 0.0)
    SubsystemValueType.INT -> SuperstructureSubsystemTarget(option.reference, constantDoubleValue = (option.field.defaultInt ?: 0).toDouble())
    SubsystemValueType.BOOLEAN -> SuperstructureSubsystemTarget(option.reference, constantBooleanValue = option.field.defaultBoolean ?: false)
    SubsystemValueType.STRING -> SuperstructureSubsystemTarget(option.reference, constantStringValue = option.field.defaultText.orEmpty())
}
