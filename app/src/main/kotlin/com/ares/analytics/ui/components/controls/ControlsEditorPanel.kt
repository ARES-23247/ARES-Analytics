package com.ares.analytics.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.GamepadState
import com.ares.analytics.service.AresGenerationPhase
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGold
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresRed
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.viewmodel.controls.ControlsEditorState
import com.ares.analytics.viewmodel.controls.ControlsEditorViewModel
import com.ares.analytics.viewmodel.controls.ControlsProblemSeverity
import com.areslib.catalog.CapabilityParameterDescriptor
import com.areslib.catalog.CapabilityParameterType
import com.areslib.controls.ControlBindingDocument
import com.areslib.controls.ControlEvent
import com.areslib.controls.ControlSourceKind
import com.areslib.controls.ControlTargetKind
import com.areslib.controls.ControlThresholdDirection
import com.areslib.controls.ControllerControlDocument
import com.areslib.controls.ControllerControlTypeDocument
import com.areslib.controls.ControllerInputPlatform
import com.areslib.controls.ControllerSurfaceDocument
import com.areslib.controls.RoutineInvocationPolicy

@Composable
fun ControlsEditorPanel(
    state: ControlsEditorState,
    viewModel: ControlsEditorViewModel,
    gamepad1State: GamepadState,
    gamepad2State: GamepadState,
    modifier: Modifier = Modifier
) {
    val liveState = if (state.selectedControllerSlot == "operator") gamepad2State else gamepad1State
    LaunchedEffect(liveState.rawButtons, liveState.rawAxes, state.learning) {
        if (state.learning != null) viewModel.observeDesktopInput(liveState)
    }

    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectHeader(state, viewModel)
        if (state.loadError != null) {
            ProblemBanner(state.loadError, ControlsProblemSeverity.ERROR)
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                Modifier.weight(1.45f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SchemeToolbar(state, viewModel)
                SurfaceTabs(state.surface, viewModel::showSurface)
                state.selectedProfile?.let { profile ->
                    val isEditingChord = state.draftBinding?.source?.kind == ControlSourceKind.CHORD
                    val chord = state.draftBinding?.takeIf { isEditingChord }
                        ?.source?.controlIds.orEmpty().toSet()
                    val bound = state.selectedScheme?.bindings.orEmpty()
                        .flatMapTo(linkedSetOf()) { it.source.controlIds }
                    ControllerCanvas(
                        profile = profile,
                        surface = state.surface,
                        selectedControlId = state.selectedControlId,
                        chordControlIds = chord,
                        boundControlIds = bound,
                        targetPlatform = state.targetPlatform,
                        liveState = liveState,
                        onControlSelected = { viewModel.selectControl(it, appendToChord = isEditingChord) }
                    )
                    SelectedControlCard(state, viewModel, liveState)
                    AccessibleControlList(state, viewModel, liveState)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BindingList(state, viewModel)
                state.draftBinding?.let { BindingInspector(state, viewModel, it) }
                ProblemsCard(state)
            }
        }
    }
}

@Composable
private fun ProjectHeader(state: ControlsEditorState, viewModel: ControlsEditorViewModel) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Visual controls editor", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                "${state.league.name} project • ${state.projectPath} • offline authoring",
                color = AresTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Desktop learning writes DESKTOP_GLFW only. ${state.targetPlatform.name} mappings require separate verification.",
                color = AresGold,
                fontSize = 11.sp
            )
            state.projectMetadata?.let { metadata ->
                Text(
                    "${metadata.coordinateConvention.name} | robot ${metadata.robotLengthMeters} x ${metadata.robotWidthMeters} m",
                    color = AresTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::reload) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Reload")
            }
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Save")
            }
            Button(
                onClick = viewModel::saveAndGenerate,
                enabled = state.canGenerate,
                colors = ButtonDefaults.buttonColors(containerColor = AresGreen, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp))
                Text(if (state.generationPhase == AresGenerationPhase.RUNNING) "Generating..." else "Save & Generate")
            }
        }
    }
    state.status?.let { Text(it, color = AresTextSecondary, fontSize = 11.sp) }
    state.generationMessage?.let { message ->
        val color = when (state.generationPhase) {
            AresGenerationPhase.FAILED -> AresError
            AresGenerationPhase.SUCCEEDED -> AresGreen
            else -> AresTextSecondary
        }
        Text(message, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
    state.generatedContentHash?.let { hash ->
        Text("Generated content SHA-256: $hash", color = AresTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SchemeToolbar(state: ControlsEditorState, viewModel: ControlsEditorViewModel) {
    Column(cardModifier(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            SelectionMenu(
                label = "Scheme",
                selected = state.selectedScheme?.name ?: "No scheme",
                choices = state.schemes.map { it.documentId to it.name },
                modifier = Modifier.weight(1f),
                onSelect = viewModel::selectScheme
            )
            SelectionMenu(
                label = "Controller",
                selected = state.selectedController?.displayName ?: "No controller",
                choices = state.selectedScheme?.controllers.orEmpty().map { it.slot to it.displayName },
                modifier = Modifier.weight(1f),
                onSelect = viewModel::selectController
            )
            SelectionMenu(
                label = "Profile",
                selected = state.selectedProfile?.displayName ?: "No profile",
                choices = state.profiles.map { it.documentId to it.displayName },
                modifier = Modifier.weight(1.3f),
                onSelect = viewModel::assignProfile
            )
        }
    }
}

@Composable
private fun SurfaceTabs(surface: ControllerSurfaceDocument, onSurface: (ControllerSurfaceDocument) -> Unit) {
    TabRow(selectedTabIndex = surface.ordinal) {
        ControllerSurfaceDocument.entries.forEach { candidate ->
            Tab(
                selected = surface == candidate,
                onClick = { onSurface(candidate) },
                text = { Text(candidate.name.lowercase().replaceFirstChar(Char::uppercase)) }
            )
        }
    }
}

@Composable
private fun SelectedControlCard(
    state: ControlsEditorState,
    viewModel: ControlsEditorViewModel,
    liveState: GamepadState
) {
    val control = state.selectedControl ?: return
    val assignedBindings = state.selectedScheme?.bindings.orEmpty().filter { control.controlId in it.source.controlIds }
    val targetMapping = control.mappings.firstOrNull { it.platform == state.targetPlatform }
    var showHardwareSetup by remember(control.controlId) { mutableStateOf(false) }
    Column(cardModifier(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(control.displayName, color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (control.isActive(liveState)) "LIVE INPUT ACTIVE" else control.type.name,
                    color = if (control.isActive(liveState)) AresCyan else AresTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = viewModel::createBinding,
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(" Add action")
                }
            }
        }
        HorizontalDivider(color = AresBorder)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (assignedBindings.isEmpty()) "No action assigned yet" else
                        "${assignedBindings.size} assigned action${if (assignedBindings.size == 1) "" else "s"}",
                    color = if (assignedBindings.isEmpty()) AresTextSecondary else AresGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    if (targetMapping == null) {
                        "${state.targetPlatform.studentLabel()} input is not configured"
                    } else {
                        "Ready for ${state.targetPlatform.studentLabel()} generated code"
                    },
                    color = if (targetMapping == null) AresGold else AresTextSecondary,
                    fontSize = 11.sp
                )
            }
            OutlinedButton(onClick = { showHardwareSetup = !showHardwareSetup }) {
                Text(if (showHardwareSetup) "Hide hardware setup" else "Hardware setup", fontSize = 11.sp)
            }
        }
        if (assignedBindings.isNotEmpty()) {
            assignedBindings.take(3).forEach { binding ->
                Text("• ${binding.displayName}", color = AresTextPrimary, fontSize = 11.sp)
            }
        }
        if (showHardwareSetup) {
            HorizontalDivider(color = AresBorder)
            Text("Advanced hardware mapping", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                "Standard controls are configured automatically. Change these slots only for extra vendor buttons or a nonstandard Driver Station mapping.",
                color = AresTextSecondary,
                fontSize = 10.sp
            )
            listOf(state.targetPlatform, ControllerInputPlatform.DESKTOP_GLFW).distinct().forEach { platform ->
                val mapping = control.mappings.firstOrNull { it.platform == platform }
                MappingRow(
                    platform = platform,
                    index = mapping?.buttonIndex ?: mapping?.axisIndex,
                    isTarget = platform == state.targetPlatform,
                    onIndex = { viewModel.setMapping(control.controlId, platform, it) }
                )
            }
            OutlinedButton(onClick = { viewModel.beginDesktopLearning(liveState) }, enabled = liveState.connected) {
                Text(if (state.learning?.controlId == control.controlId) "Press or move the control now…" else "Detect from this computer")
            }
        }
    }
}

@Composable
private fun MappingRow(
    platform: ControllerInputPlatform,
    index: Int?,
    isTarget: Boolean,
    onIndex: (Int?) -> Unit
) {
    var raw by remember(platform, index) { mutableStateOf(index?.toString().orEmpty()) }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        Text(
            platform.studentLabel(),
            modifier = Modifier.width(120.dp),
            color = if (isTarget) AresGold else AresTextSecondary,
            fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp
        )
        OutlinedTextField(
            value = raw,
            onValueChange = { value ->
                if (value.isEmpty() || value.all(Char::isDigit)) {
                    raw = value
                }
            },
            modifier = Modifier.width(110.dp),
            singleLine = true,
            label = { Text("Input slot") }
        )
        OutlinedButton(onClick = { onIndex(raw.toIntOrNull()) }) {
            Text(
                when {
                    raw.isBlank() -> "Clear"
                    else -> "Save slot"
                },
                fontSize = 10.sp
            )
        }
        Text(
            when {
                index == null && isTarget -> "unverified target mapping"
                index == null -> "not mapped"
                else -> "configured"
            },
            color = if (index == null && isTarget) AresRed else AresTextSecondary,
            fontSize = 10.sp
        )
    }
}

private fun ControllerInputPlatform.studentLabel(): String = when (this) {
    ControllerInputPlatform.FTC -> "FTC"
    ControllerInputPlatform.FRC -> "FRC"
    ControllerInputPlatform.DESKTOP_GLFW -> "Desktop simulator"
}

@Composable
private fun AccessibleControlList(
    state: ControlsEditorState,
    viewModel: ControlsEditorViewModel,
    liveState: GamepadState
) {
    val profile = state.selectedProfile ?: return
    Column(cardModifier(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Accessible control list", color = AresTextPrimary, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = state.search,
            onValueChange = viewModel::setSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Find a control or binding") }
        )
        profile.controls.filter { control ->
            state.search.isBlank() || control.displayName.contains(state.search, true) ||
                control.controlId.contains(state.search, true) ||
                state.selectedScheme?.bindings.orEmpty().any { binding ->
                    control.controlId in binding.source.controlIds && binding.displayName.contains(state.search, true)
                }
        }.forEach { control ->
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.selectControl(control.controlId) }
                    .background(Color.Black.copy(alpha = .18f), RoundedCornerShape(6.dp)).padding(8.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Text(control.displayName, color = if (control.isActive(liveState)) AresCyan else AresTextPrimary)
                Text(
                    "${control.surface.name.lowercase()} • ${control.type.name.lowercase()}",
                    color = AresTextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun BindingList(state: ControlsEditorState, viewModel: ControlsEditorViewModel) {
    Column(cardModifier(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Bindings", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        val bindings = state.selectedScheme?.bindings.orEmpty()
        if (bindings.isEmpty()) {
            Text("Select a control, then add its first binding.", color = AresTextSecondary, fontSize = 11.sp)
        }
        bindings.forEach { binding ->
            val hasProblem = state.problems.any { it.bindingId == binding.bindingId }
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.editBinding(binding.bindingId) }
                    .background(Color.Black.copy(alpha = .22f), RoundedCornerShape(7.dp))
                    .border(1.dp, if (hasProblem) AresGold else AresBorder, RoundedCornerShape(7.dp))
                    .padding(9.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(binding.displayName, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "${binding.source.controlIds.joinToString(" + ")} • ${binding.event} → ${binding.target.key}",
                        color = AresTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(onClick = { viewModel.deleteBinding(binding.bindingId) }) {
                    Icon(Icons.Default.Delete, "Delete binding", tint = AresRed, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun BindingInspector(
    state: ControlsEditorState,
    viewModel: ControlsEditorViewModel,
    binding: ControlBindingDocument
) {
    Column(cardModifier(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(if (state.selectedBindingId == null) "New binding" else "Edit binding", color = AresCyan, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = viewModel::discardDraft) { Text("Close") }
        }
        OutlinedTextField(
            value = binding.displayName,
            onValueChange = { value -> viewModel.updateDraft { it.copy(displayName = value) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Binding name") },
            singleLine = true
        )
        val sourceKinds = if (state.selectedControl?.type == ControllerControlTypeDocument.AXIS) {
            listOf(ControlSourceKind.AXIS_THRESHOLD, ControlSourceKind.AXIS_VALUE, ControlSourceKind.AXIS_ZONE)
        } else {
            listOf(ControlSourceKind.BUTTON, ControlSourceKind.CHORD)
        }.let { allowed -> (allowed + binding.source.kind).distinct() }
        SelectionMenu(
            "Input type", binding.source.kind.friendlyName(),
            sourceKinds.map { it.name to it.friendlyName() },
            Modifier.fillMaxWidth()
        ) { viewModel.setSourceKind(ControlSourceKind.valueOf(it)) }
        if (binding.source.kind == ControlSourceKind.CHORD) {
            Text("Chord: ${binding.source.controlIds.joinToString(" + ").ifBlank { "select two controls" }}", color = AresGold, fontSize = 11.sp)
            Text("Click controls on the diagram to add or remove chord members.", color = AresTextSecondary, fontSize = 10.sp)
            NumberEditor("Chord window (s)", binding.source.chordWindowSeconds) { value ->
                viewModel.updateDraft { it.copy(source = it.source.copy(chordWindowSeconds = value)) }
            }
        }
        AnalogSourceFields(binding, viewModel)
        val events = allowedEvents(binding.source.kind)
        SelectionMenu(
            "Event", binding.event.friendlyName(), events.map { it.name to it.friendlyName() }, Modifier.fillMaxWidth()
        ) { selected -> viewModel.updateDraft { it.copy(event = ControlEvent.valueOf(selected)) } }
        TimingFields(binding, viewModel)
        TargetFields(state, binding, viewModel)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(binding.enabled, { value -> viewModel.updateDraft { it.copy(enabled = value) } })
                Text(" Enabled", color = AresTextPrimary, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(binding.suppressConstituentBindings, { value ->
                    viewModel.updateDraft { it.copy(suppressConstituentBindings = value) }
                })
                Text(" Suppress chord buttons", color = AresTextSecondary, fontSize = 10.sp)
            }
        }
        Button(
            onClick = viewModel::applyDraft,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = Color.Black)
        ) { Text(if (state.selectedBindingId == null) "Add binding" else "Apply changes") }
    }
}

@Composable
private fun AnalogSourceFields(binding: ControlBindingDocument, viewModel: ControlsEditorViewModel) {
    when (binding.source.kind) {
        ControlSourceKind.AXIS_THRESHOLD -> {
            NumberEditor("Press threshold", binding.source.pressThreshold ?: .65) { value ->
                viewModel.updateDraft { it.copy(source = it.source.copy(pressThreshold = value)) }
            }
            NumberEditor("Release threshold", binding.source.releaseThreshold ?: .50) { value ->
                viewModel.updateDraft { it.copy(source = it.source.copy(releaseThreshold = value)) }
            }
            SelectionMenu(
                "Direction", binding.source.thresholdDirection.name.lowercase(),
                ControlThresholdDirection.entries.map { it.name to it.name.lowercase() }, Modifier.fillMaxWidth()
            ) { selected ->
                viewModel.updateDraft { it.copy(source = it.source.copy(thresholdDirection = ControlThresholdDirection.valueOf(selected))) }
            }
        }
        ControlSourceKind.AXIS_ZONE -> {
            NumberEditor("Zone minimum", binding.source.zoneMinimum ?: -.25) { value ->
                viewModel.updateDraft { it.copy(source = it.source.copy(zoneMinimum = value)) }
            }
            NumberEditor("Zone maximum", binding.source.zoneMaximum ?: .25) { value ->
                viewModel.updateDraft { it.copy(source = it.source.copy(zoneMaximum = value)) }
            }
            NumberEditor("Zone hysteresis", binding.source.zoneHysteresis) { value ->
                viewModel.updateDraft { it.copy(source = it.source.copy(zoneHysteresis = value)) }
            }
        }
        ControlSourceKind.AXIS_VALUE -> {
            val policy = binding.analogPolicy ?: return
            NumberEditor("Change epsilon", policy.changeEpsilon) { value ->
                viewModel.updateDraft { it.copy(analogPolicy = it.analogPolicy?.copy(changeEpsilon = value)) }
            }
            NumberEditor("Re-arm neutral", policy.rearmNeutralThreshold) { value ->
                viewModel.updateDraft { it.copy(analogPolicy = it.analogPolicy?.copy(rearmNeutralThreshold = value)) }
            }
        }
        else -> Unit
    }
}

@Composable
private fun TimingFields(binding: ControlBindingDocument, viewModel: ControlsEditorViewModel) {
    HorizontalDivider(color = AresBorder)
    Text("Timing and safety", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    NumberEditor("Press debounce (s)", binding.timing.pressDebounceSeconds) { value ->
        viewModel.updateDraft { it.copy(timing = it.timing.copy(pressDebounceSeconds = value)) }
    }
    NumberEditor("Release debounce (s)", binding.timing.releaseDebounceSeconds) { value ->
        viewModel.updateDraft { it.copy(timing = it.timing.copy(releaseDebounceSeconds = value)) }
    }
    NullableNumberEditor("Hold after (s)", binding.timing.holdAfterSeconds) { value ->
        viewModel.updateDraft { it.copy(timing = it.timing.copy(holdAfterSeconds = value)) }
    }
    NullableNumberEditor("Repeat after (s)", binding.timing.repeatAfterSeconds) { value ->
        viewModel.updateDraft { it.copy(timing = it.timing.copy(repeatAfterSeconds = value)) }
    }
    NullableNumberEditor("Repeat every (s)", binding.timing.repeatEverySeconds) { value ->
        viewModel.updateDraft { it.copy(timing = it.timing.copy(repeatEverySeconds = value)) }
    }
    NumberEditor("Cooldown (s)", binding.timing.cooldownSeconds) { value ->
        viewModel.updateDraft { it.copy(timing = it.timing.copy(cooldownSeconds = value)) }
    }
    NullableNumberEditor("Maximum active (s)", binding.timing.maximumActiveSeconds) { value ->
        viewModel.updateDraft { it.copy(timing = it.timing.copy(maximumActiveSeconds = value)) }
    }
}

@Composable
private fun TargetFields(state: ControlsEditorState, binding: ControlBindingDocument, viewModel: ControlsEditorViewModel) {
    HorizontalDivider(color = AresBorder)
    Text("Target", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    SelectionMenu(
        "Target type", binding.target.kind.friendlyName(),
        ControlTargetKind.entries.map { it.name to it.friendlyName() }, Modifier.fillMaxWidth()
    ) { selected ->
        val kind = ControlTargetKind.valueOf(selected)
        val key = if (kind == ControlTargetKind.ACTION) state.actions.firstOrNull()?.key.orEmpty()
        else state.routineIds.firstOrNull().orEmpty()
        viewModel.setTarget(kind, key)
    }
    if (binding.target.kind == ControlTargetKind.ACTION) {
        ActionPicker(state, binding.target.key) { viewModel.setTarget(ControlTargetKind.ACTION, it) }
        state.selectedAction?.parameters.orEmpty().forEach { parameter ->
            TargetArgumentField(parameter, binding.target.arguments[parameter.key].orEmpty()) { value ->
                viewModel.setTargetArgument(parameter.key, value)
            }
        }
    } else {
        SelectionMenu(
            "Reusable routine", binding.target.key.ifBlank { "Choose routine" },
            state.routineIds.map { it to it }, Modifier.fillMaxWidth()
        ) { viewModel.setTarget(binding.target.kind, it) }
        SelectionMenu(
            "Invocation", binding.target.routinePolicy.friendlyName(),
            RoutineInvocationPolicy.entries.map { it.name to it.friendlyName() }, Modifier.fillMaxWidth()
        ) { selected ->
            viewModel.updateDraft { it.copy(target = it.target.copy(routinePolicy = RoutineInvocationPolicy.valueOf(selected))) }
        }
    }
}

@Composable
private fun ActionPicker(state: ControlsEditorState, selectedKey: String, onSelect: (String) -> Unit) {
    val selected = state.actions.firstOrNull { it.key == selectedKey }
    var query by remember(selectedKey) { mutableStateOf(selected?.displayName.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    val matches = state.actions.filter { action ->
        query.isBlank() || action.displayName.contains(query, true) || action.key.contains(query, true) ||
            action.category.contains(query, true)
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Catalog action") },
            placeholder = { Text("Search actions") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, "Show actions") }
            }
        )
        DropdownMenu(expanded, { expanded = false }) {
            matches.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(action.displayName, fontSize = 11.sp)
                            Text("${action.category} • ${action.key}", color = AresTextSecondary, fontSize = 9.sp)
                        }
                    },
                    onClick = {
                        query = action.displayName
                        expanded = false
                        onSelect(action.key)
                    }
                )
            }
        }
    }
}

@Composable
private fun TargetArgumentField(parameter: CapabilityParameterDescriptor, value: String, onValue: (String) -> Unit) {
    if (parameter.type == CapabilityParameterType.ENUM || parameter.type == CapabilityParameterType.BOOLEAN) {
        val choices = if (parameter.type == CapabilityParameterType.BOOLEAN) listOf("true", "false") else parameter.options
        SelectionMenu(
            parameter.displayName,
            value.ifBlank { "Choose" },
            choices.map { it to it },
            Modifier.fillMaxWidth(),
            onValue
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(parameter.displayName + parameter.unit?.let { " ($it)" }.orEmpty()) },
            supportingText = { Text(parameter.description, fontSize = 9.sp) },
            singleLine = true
        )
    }
}

@Composable
private fun ProblemsCard(state: ControlsEditorState) {
    if (state.problems.isEmpty()) return
    Column(cardModifier(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Checks", color = AresTextPrimary, fontWeight = FontWeight.Bold)
        state.problems.take(12).forEach { ProblemBanner(it.message, it.severity) }
        if (state.problems.size > 12) Text("+ ${state.problems.size - 12} more", color = AresTextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun ProblemBanner(message: String, severity: ControlsProblemSeverity) {
    val color = when (severity) {
        ControlsProblemSeverity.ERROR -> AresRed
        ControlsProblemSeverity.WARNING -> AresGold
        ControlsProblemSeverity.INFO -> AresCyan
    }
    Text(
        message,
        color = color,
        fontSize = 10.sp,
        modifier = Modifier.fillMaxWidth().background(color.copy(alpha = .08f), RoundedCornerShape(5.dp)).padding(7.dp)
    )
}

@Composable
private fun SelectionMenu(
    label: String,
    selected: String,
    choices: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = choices.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, color = AresTextSecondary, fontSize = 9.sp)
                Text(selected, maxLines = 1, fontSize = 11.sp)
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { (key, display) ->
                DropdownMenuItem(
                    text = { Text(display, fontSize = 11.sp) },
                    onClick = { expanded = false; onSelect(key) }
                )
            }
        }
    }
}

@Composable
private fun NumberEditor(label: String, value: Double, onValue: (Double) -> Unit) =
    NullableNumberEditor(label, value) { it?.let(onValue) }

@Composable
private fun NullableNumberEditor(label: String, value: Double?, onValue: (Double?) -> Unit) {
    var raw by remember(label, value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = raw,
        onValueChange = { text -> raw = text; onValue(text.toDoubleOrNull()) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true
    )
}

private fun cardModifier() = Modifier.fillMaxWidth()
    .background(AresSurfaceElevated, RoundedCornerShape(10.dp))
    .border(1.dp, AresBorder, RoundedCornerShape(10.dp))
    .padding(12.dp)

private fun ControlSourceKind.friendlyName() = when (this) {
    ControlSourceKind.BUTTON -> "Button"
    ControlSourceKind.CHORD -> "Chord"
    ControlSourceKind.AXIS_THRESHOLD -> "Analog threshold"
    ControlSourceKind.AXIS_VALUE -> "Continuous analog"
    ControlSourceKind.AXIS_ZONE -> "Analog zone"
}

private fun ControlEvent.friendlyName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun ControlTargetKind.friendlyName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun RoutineInvocationPolicy.friendlyName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun allowedEvents(kind: ControlSourceKind): List<ControlEvent> = when (kind) {
    ControlSourceKind.BUTTON, ControlSourceKind.CHORD, ControlSourceKind.AXIS_THRESHOLD ->
        listOf(ControlEvent.PRESS, ControlEvent.RELEASE, ControlEvent.HELD, ControlEvent.HOLD, ControlEvent.REPEAT)
    ControlSourceKind.AXIS_VALUE -> listOf(ControlEvent.VALUE)
    ControlSourceKind.AXIS_ZONE -> listOf(ControlEvent.ZONE_ENTER, ControlEvent.ZONE_ACTIVE, ControlEvent.ZONE_EXIT)
}
