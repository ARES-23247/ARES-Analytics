package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.superstructure.*
import com.areslib.subsystem.InterlockComparison
import com.areslib.subsystem.SubsystemValueType
import com.areslib.superstructure.*
import java.util.Locale

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
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = viewModel::removeSelectedState, enabled = draft.states.size > 2 && selected.stateId !in setOf(draft.initialStateId, draft.faultStateId)) {
                Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(16.dp)); Text(" Remove")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ viewModel.setInitialState(selected.stateId) }, enabled = selected.stateId != draft.initialStateId) { Text("Use as startup posture") }
            OutlinedButton({ viewModel.setFaultState(selected.stateId) }, enabled = selected.stateId != draft.faultStateId) { Text("Use as fault neutral") }
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
        val used = selected.subsystemTargets.map { it.subsystemId to it.fieldId }.toSet()
        val remaining = state.targetFields.filter { it.subsystem.documentId to it.field.fieldId !in used }
        if (remaining.isNotEmpty()) {
            StudioDropdown("+ Add a target to every posture", remaining.map { "${it.subsystem.documentId}.${it.field.fieldId}" to it.label }, onSelect = { key ->
                remaining.single { "${it.subsystem.documentId}.${it.field.fieldId}" == key }.let { viewModel.addTarget(it.reference) }
            })
        }
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
    val option = state.targetFields.singleOrNull { it.subsystem.documentId == target.subsystemId && it.field.fieldId == target.fieldId }
    if (option == null) return
    val fault = selected.stateId == draft.faultStateId
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
                            options = choices.map { "${it.subsystem.documentId}.${it.field.fieldId}" to it.label },
                            onSelect = { key ->
                            val source = choices.single { "${it.subsystem.documentId}.${it.field.fieldId}" == key }
                            viewModel.updateSelectedTarget(target.copy(source = source.reference))
                            },
                        )
                    }
                    SuperstructureTargetMode.DYNAMIC_LUT -> {
                        val numeric = state.sourceFields.filter { it.field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT) }
                        StudioDropdown("Input: ${numeric.singleOrNull { it.reference == target.source }?.label ?: "choose numeric evidence"}", numeric.map { "${it.subsystem.documentId}.${it.field.fieldId}" to it.label }, onSelect = { key ->
                            viewModel.updateSelectedTarget(target.copy(source = numeric.single { "${it.subsystem.documentId}.${it.field.fieldId}" == key }.reference))
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
            var raw by remember(target.subsystemId, target.fieldId, target.constantDoubleValue) { mutableStateOf(target.constantDoubleValue?.toString().orEmpty()) }
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
    var sensorKey by remember(draft.superstructureId) { mutableStateOf(state.sourceFields.firstOrNull()?.let { "${it.subsystem.documentId}.${it.field.fieldId}" }.orEmpty()) }
    var seconds by remember(draft.superstructureId) { mutableStateOf("1.0") }

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
                "Evidence: ${state.sourceFields.singleOrNull { "${it.subsystem.documentId}.${it.field.fieldId}" == sensorKey }?.label ?: "choose cached evidence"}",
                state.sourceFields.map { "${it.subsystem.documentId}.${it.field.fieldId}" to it.label },
                { sensorKey = it },
            )
            TransitionTriggerKind.TIME_ELAPSED -> OutlinedTextField(seconds, { seconds = it }, label = { Text("Seconds in source posture") })
        }
        Button(
            onClick = {
                when (kind) {
                    TransitionTriggerKind.ACTION_REQUEST -> viewModel.addActionTransition(source, target, action)
                    TransitionTriggerKind.SENSOR_CONDITION_AUTO -> state.sourceFields.singleOrNull { "${it.subsystem.documentId}.${it.field.fieldId}" == sensorKey }?.let { viewModel.addSensorTransition(source, target, it) }
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
    draft.transitions.forEach { edge -> TransitionCard(state, draft, edge, viewModel) }
    if (draft.transitions.isEmpty()) StudioBanner("No transitions yet", "Only the initial posture can be reached. Add explicit routes to every other posture.", AresGold)
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
                Text("Fresh evidence · ${source?.label ?: "${guard.source.subsystemId}.${guard.source.fieldId}"}", color = AresTextSecondary, fontSize = 11.sp)
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
            NumberEditor("Debounce (ms)", edge.debounceMs.toDouble(), onValue = { viewModel.updateTransition(edge.copy(debounceMs = it.toLong().coerceIn(0, 60_000))) }, onValidity = { valid -> viewModel.setEditorError("transition:${edge.transitionId}:debounce", if (valid) null else "${edge.transitionId} debounce must be numeric.") })
        }
        if (edge.triggerKind == TransitionTriggerKind.TIME_ELAPSED) {
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
    var sourceKey by remember(draft.superstructureId) { mutableStateOf(numericSources.firstOrNull()?.let { "${it.subsystem.documentId}.${it.field.fieldId}" }.orEmpty()) }
    var targetKey by remember(draft.superstructureId) { mutableStateOf(numericTargets.firstOrNull()?.let { "${it.subsystem.documentId}.${it.field.fieldId}" }.orEmpty()) }
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
}

@Composable
private fun LookupTablesStep(draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    StudioSection("Lookup tables", "Use a reviewed table when one mechanism target depends on a measured value, such as shooter speed from distance. This editor is not a physics simulation.") {
        Button(viewModel::addLut, colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent)) { Text("Add lookup table") }
    }
    draft.luts.forEach { lut ->
        StudioSection(lut.displayName.ifBlank { lut.lutId }, "Inputs must be strictly increasing. Runtime interpolation is deterministic and allocation-free.") {
            OutlinedTextField(lut.displayName, { viewModel.updateLut(lut.copy(displayName = it.take(80))) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
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
private fun ReviewStep(state: SuperstructureStudioState, draft: SuperstructureDocument, viewModel: SuperstructureStudioViewModel) {
    StudioSection("Build integration", "Saving updates the canonical input only. The normal robot build regenerates mechanical Kotlin, validates every field/action reference, compiles the runtime, and runs generated contract tests.") {
        ReviewLine("Coordinator ID", draft.superstructureId)
        ReviewLine("Startup posture", draft.initialStateId)
        ReviewLine("Fault posture", draft.faultStateId)
        ReviewLine("Complete target fields", draft.states.firstOrNull()?.subsystemTargets?.size?.toString() ?: "0")
        ReviewLine("Postures / transitions", "${draft.states.size} / ${draft.transitions.size}")
        ReviewLine("Interlocks / lookup tables", "${draft.interlocks.size} / ${draft.luts.size}")
        ReviewLine("Current saved hash", state.savedContentHash?.take(12) ?: "new document")
        HorizontalDivider(color = AresBorder)
        Text("Runtime guarantees", color = AresTextPrimary, fontWeight = FontWeight.Bold)
        listOf(
            "At most one transition is consumed per robot loop.",
            "All target tasks are preflighted before any target is dispatched.",
            "Missing tasks or failed application enter the explicit fault posture.",
            "Only cached generated state is read; hardware is never read from the coordinator.",
            "Steady-state runtime paths are covered by a zero-allocation regression.",
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

private fun key(option: SuperstructureFieldOption): String = "${option.subsystem.documentId}.${option.field.fieldId}"

private fun triggerLabel(kind: TransitionTriggerKind): String = when (kind) {
    TransitionTriggerKind.ACTION_REQUEST -> "Driver/autonomous action request"
    TransitionTriggerKind.SENSOR_CONDITION_AUTO -> "Fresh sensor condition"
    TransitionTriggerKind.TIME_ELAPSED -> "Time elapsed in posture"
}

private fun neutralFor(option: SuperstructureFieldOption): SuperstructureSubsystemTarget = when (option.field.type) {
    SubsystemValueType.DOUBLE -> SuperstructureSubsystemTarget(option.subsystem.documentId, option.field.fieldId, constantDoubleValue = option.field.defaultNumber ?: 0.0)
    SubsystemValueType.INT -> SuperstructureSubsystemTarget(option.subsystem.documentId, option.field.fieldId, constantDoubleValue = (option.field.defaultInt ?: 0).toDouble())
    SubsystemValueType.BOOLEAN -> SuperstructureSubsystemTarget(option.subsystem.documentId, option.field.fieldId, constantBooleanValue = option.field.defaultBoolean ?: false)
    SubsystemValueType.STRING -> SuperstructureSubsystemTarget(option.subsystem.documentId, option.field.fieldId, constantStringValue = option.field.defaultText.orEmpty())
}
