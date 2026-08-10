package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGold
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.viewmodel.PathPlannerIntent
import com.ares.analytics.viewmodel.PathPlannerState
import com.ares.analytics.viewmodel.pathing.RobotDimensions
import com.areslib.auto.AutoPose
import com.areslib.auto.AutoStep
import com.areslib.auto.AutoStepKind
import com.areslib.auto.AutoValidationSeverity
import com.areslib.pathing.NamedCommandDescriptor
import com.areslib.pathing.TrajectoryPreset
import java.util.Locale

/**
 * GUI-first native ARES auto editor.
 *
 * Students manipulate field goals and robot capabilities. Path schemas, Redux actions, task
 * classes, and trajectory engines remain implementation details unless advanced settings are used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEditorPanel(
    state: PathPlannerState,
    projectPath: String?,
    league: League,
    onRobotDimensionsChanged: (RobotDimensions) -> Unit,
    onIntent: (PathPlannerIntent) -> Unit
) {
    var openExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    val routine = state.aresAuto
    val hasErrors = state.aresAutoValidation.any { it.severity == AutoValidationSeverity.ERROR }

    Column(
        modifier = Modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = routine.name,
                    onValueChange = { onIntent(PathPlannerIntent.UpdatePathName(it)) },
                    label = { Text("Auto name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = editorTextFieldColors()
                )
                Box {
                    OutlinedButton(onClick = { openExpanded = true }) {
                        Text("Open")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = openExpanded,
                        onDismissRequest = { openExpanded = false }
                    ) {
                        if (state.availableAresAutos.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No saved ARES autos") },
                                onClick = { openExpanded = false },
                                enabled = false
                            )
                        }
                        state.availableAresAutos.forEach { saved ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(saved.name)
                                        Text(
                                            "Revision ${saved.revision}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AresTextSecondary
                                        )
                                    }
                                },
                                onClick = {
                                    onIntent(
                                        PathPlannerIntent.LoadAresAuto(
                                            projectPath,
                                            league,
                                            saved.documentId
                                        )
                                    )
                                    openExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onIntent(PathPlannerIntent.CreateNewAuto()) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                    Button(
                        onClick = { onIntent(PathPlannerIntent.SaveAresAuto(projectPath, league)) },
                        enabled = projectPath != null && !hasErrors,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AresCyan,
                            contentColor = AresBackground
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }

                Box {
                    TextButton(
                        onClick = { historyExpanded = true },
                        enabled = state.aresAutoRevisions.isNotEmpty()
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Revision ${routine.revision}")
                    }
                    DropdownMenu(
                        expanded = historyExpanded,
                        onDismissRequest = { historyExpanded = false }
                    ) {
                        state.aresAutoRevisions.forEach { revision ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (revision.revision == routine.revision) {
                                            "Revision ${revision.revision} (current)"
                                        } else {
                                            "Restore revision ${revision.revision}"
                                        }
                                    )
                                },
                                enabled = revision.contentHash != routine.parentContentHash &&
                                    revision.revision != routine.revision,
                                onClick = {
                                    onIntent(
                                        PathPlannerIntent.RestoreAresAuto(
                                            projectPath,
                                            league,
                                            revision.contentHash
                                        )
                                    )
                                    historyExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${formatNumber(state.estimatedDuration)} s estimated",
                    style = MaterialTheme.typography.labelMedium,
                    color = AresTextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onIntent(PathPlannerIntent.TogglePlayback) }) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause preview" else "Play preview",
                            tint = AresCyan
                        )
                    }
                    Text("${formatNumber(state.playbackTime)} s", color = AresTextSecondary)
                }
            }

            if (state.saveStatus.isNotBlank()) {
                Text(
                    state.saveStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.saveStatus.contains("failed", ignoreCase = true)) AresError else AresTextSecondary
                )
            }
            Text(
                state.capabilityStatus,
                style = MaterialTheme.typography.labelSmall,
                color = if (state.capabilityStatus.contains("missing", ignoreCase = true)) AresGold else AresTextSecondary
            )
        }

        HorizontalDivider(color = AresBorder)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.aresAutoValidation.isNotEmpty()) {
                item {
                    ValidationCard(state)
                }
            }
            item {
                RobotFootprintCard(
                    dimensions = state.robotDimensions,
                    onDimensionsChanged = onRobotDimensionsChanged
                )
            }
            item {
                StartingPoseCard(
                    pose = routine.startingPose,
                    onPoseChanged = { onIntent(PathPlannerIntent.UpdateAresStartingPose(it, league)) }
                )
            }
            if (routine.steps.isEmpty()) {
                item {
                    EmptyAutoCard(hasCapabilities = state.commandCatalog.isNotEmpty())
                }
            }
            itemsIndexed(routine.steps, key = { index, step -> "$index-${step.kind}" }) { index, step ->
                AutoStepCard(
                    index = index,
                    step = step,
                    stepCount = routine.steps.size,
                    commandCatalog = state.commandCatalog,
                    onUpdate = { onIntent(PathPlannerIntent.UpdateAresStep(index, it, league)) },
                    onMove = { direction ->
                        onIntent(PathPlannerIntent.MoveAresStep(index, direction, league))
                    },
                    onRemove = { onIntent(PathPlannerIntent.RemoveAresStep(index, league)) }
                )
            }
        }

        HorizontalDivider(color = AresBorder)
        AddStepBar(
            hasCapabilities = state.commandCatalog.isNotEmpty(),
            commandCatalog = state.commandCatalog,
            onAddDrive = { onIntent(PathPlannerIntent.AddAresDriveGoal(league)) },
            onAddWait = { onIntent(PathPlannerIntent.AddAresWait(league)) },
            onAddCommand = { key -> onIntent(PathPlannerIntent.AddAresCommand(key, league)) }
        )
    }
}

@Composable
private fun RobotFootprintCard(
    dimensions: RobotDimensions,
    onDimensionsChanged: (RobotDimensions) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = BorderStroke(1.dp, AresBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Robot footprint", fontWeight = FontWeight.Bold, color = AresTextPrimary)
            Text(
                "The complete robot stays inside the field, including when it is rotated.",
                style = MaterialTheme.typography.bodySmall,
                color = AresTextSecondary
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalEditor(
                    value = dimensions.lengthMeters,
                    label = "Length",
                    suffix = "m",
                    modifier = Modifier.weight(1f),
                    onValueChanged = {
                        onDimensionsChanged(dimensions.copy(lengthMeters = it).normalized())
                    }
                )
                DecimalEditor(
                    value = dimensions.widthMeters,
                    label = "Width",
                    suffix = "m",
                    modifier = Modifier.weight(1f),
                    onValueChanged = {
                        onDimensionsChanged(dimensions.copy(widthMeters = it).normalized())
                    }
                )
            }
        }
    }
}

@Composable
private fun ValidationCard(state: PathPlannerState) {
    val errors = state.aresAutoValidation.filter { it.severity == AutoValidationSeverity.ERROR }
    val warnings = state.aresAutoValidation.filter { it.severity != AutoValidationSeverity.ERROR }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = (if (errors.isNotEmpty()) AresError else AresGold).copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, if (errors.isNotEmpty()) AresError else AresGold)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (errors.isNotEmpty()) "Needs attention" else "Review before deployment",
                color = if (errors.isNotEmpty()) AresError else AresGold,
                fontWeight = FontWeight.Bold
            )
            (errors + warnings).take(4).forEach { issue ->
                Text("• ${issue.message}", style = MaterialTheme.typography.bodySmall, color = AresTextPrimary)
            }
        }
    }
}

@Composable
private fun StartingPoseCard(pose: AutoPose, onPoseChanged: (AutoPose) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = BorderStroke(1.dp, AresBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("1. Place the robot", fontWeight = FontWeight.Bold, color = AresTextPrimary)
            Text(
                "Drag the first field marker or enter an exact starting pose.",
                style = MaterialTheme.typography.bodySmall,
                color = AresTextSecondary
            )
            PoseEditors(
                pose = pose,
                onPoseChanged = onPoseChanged
            )
        }
    }
}

@Composable
private fun EmptyAutoCard(hasCapabilities: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresCyan.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("2. Add what the robot should do", fontWeight = FontWeight.Bold, color = AresCyan)
            Text(
                "Add a drive goal, wait, or robot action. Drive goals appear on the field and can be dragged.",
                style = MaterialTheme.typography.bodySmall,
                color = AresTextPrimary
            )
            if (!hasCapabilities) {
                Text(
                    "Declare auto actions in the project capability manifest or Kotlin catalog. No robot connection is required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AresGold
                )
            }
        }
    }
}

@Composable
private fun AutoStepCard(
    index: Int,
    step: AutoStep,
    stepCount: Int,
    commandCatalog: List<NamedCommandDescriptor>,
    onUpdate: (AutoStep) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = BorderStroke(1.dp, AresBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${index + 2}. ${stepTitle(step)}",
                        fontWeight = FontWeight.Bold,
                        color = AresTextPrimary
                    )
                    Text(stepSubtitle(step), style = MaterialTheme.typography.labelSmall, color = AresTextSecondary)
                }
                Row {
                    IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move earlier", tint = AresTextSecondary)
                    }
                    IconButton(onClick = { onMove(1) }, enabled = index < stepCount - 1) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move later", tint = AresTextSecondary)
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, "Remove", tint = AresError)
                    }
                }
            }

            when (step.kind) {
                AutoStepKind.DRIVE -> {
                    val drive = step.drive ?: return@Column
                    PoseEditors(
                        pose = drive.target,
                        onPoseChanged = { onUpdate(step.copy(drive = drive.copy(target = it))) }
                    )
                    PresetPicker(
                        selected = drive.preset,
                        onSelected = { onUpdate(step.copy(drive = drive.copy(preset = it))) }
                    )
                    CapabilityPicker(
                        label = "Run while driving",
                        catalog = commandCatalog,
                        onSelected = { descriptor ->
                            onUpdate(
                                step.copy(
                                    drive = drive.copy(
                                        duringCommands = drive.duringCommands + descriptor.key.value
                                    )
                                )
                            )
                        }
                    )
                    drive.duringCommands.forEach { key ->
                        RemovableActionRow("While driving", key) {
                            onUpdate(
                                step.copy(
                                    drive = drive.copy(duringCommands = drive.duringCommands - key)
                                )
                            )
                        }
                    }
                    CapabilityPicker(
                        label = "Run on arrival",
                        catalog = commandCatalog,
                        onSelected = { descriptor ->
                            onUpdate(
                                step.copy(
                                    drive = drive.copy(
                                        arrivalCommands = drive.arrivalCommands + descriptor.key.value
                                    )
                                )
                            )
                        }
                    )
                    drive.arrivalCommands.forEach { key ->
                        RemovableActionRow("On arrival", key) {
                            onUpdate(
                                step.copy(
                                    drive = drive.copy(arrivalCommands = drive.arrivalCommands - key)
                                )
                            )
                        }
                    }
                }

                AutoStepKind.COMMAND -> {
                    CapabilityPicker(
                        label = "Robot action",
                        catalog = commandCatalog,
                        selectedKey = step.commandKey,
                        onSelected = { onUpdate(step.copy(commandKey = it.key.value)) }
                    )
                    commandCatalog.firstOrNull { it.key.value == step.commandKey }?.let { descriptor ->
                        Text(descriptor.description, style = MaterialTheme.typography.bodySmall, color = AresTextSecondary)
                    }
                }

                AutoStepKind.WAIT -> DecimalEditor(
                    value = step.durationSeconds ?: 0.0,
                    label = "Wait",
                    suffix = "s",
                    onValueChanged = { onUpdate(step.copy(durationSeconds = it.coerceAtLeast(0.0))) }
                )

                AutoStepKind.TOGETHER,
                AutoStepKind.FIRST_TO_FINISH -> Text(
                    "This advanced group contains ${step.children.size} steps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AresTextSecondary
                )
            }
        }
    }
}

@Composable
private fun PoseEditors(pose: AutoPose, onPoseChanged: (AutoPose) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DecimalEditor(
            value = pose.xMeters,
            label = "X",
            suffix = "m",
            modifier = Modifier.weight(1f),
            onValueChanged = { onPoseChanged(pose.copy(xMeters = it)) }
        )
        DecimalEditor(
            value = pose.yMeters,
            label = "Y",
            suffix = "m",
            modifier = Modifier.weight(1f),
            onValueChanged = { onPoseChanged(pose.copy(yMeters = it)) }
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onPoseChanged(pose.withHeadingDegrees(pose.headingDegrees() - 15.0)) }
        ) { Text("−15°") }
        DecimalEditor(
            value = pose.headingDegrees(),
            label = "Robot heading",
            suffix = "°",
            modifier = Modifier.weight(1f),
            onValueChanged = { onPoseChanged(pose.withHeadingDegrees(it)) }
        )
        OutlinedButton(
            onClick = { onPoseChanged(pose.withHeadingDegrees(pose.headingDegrees() + 15.0)) }
        ) { Text("+15°") }
    }
}

private fun AutoPose.headingDegrees(): Double = Math.toDegrees(headingRadians)

private fun AutoPose.withHeadingDegrees(degrees: Double): AutoPose {
    val wrapped = ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    return copy(headingRadians = Math.toRadians(wrapped))
}

@Composable
private fun DecimalEditor(
    value: Double,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier,
    onValueChanged: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { updated ->
            text = updated
            updated.toDoubleOrNull()?.takeIf(Double::isFinite)?.let(onValueChanged)
        },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        modifier = modifier,
        colors = editorTextFieldColors()
    )
}

@Composable
private fun PresetPicker(selected: TrajectoryPreset, onSelected: (TrajectoryPreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Motion: ${presetLabel(selected)}", color = AresTextPrimary)
                Text(presetDescription(selected), style = MaterialTheme.typography.labelSmall, color = AresTextSecondary)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TrajectoryPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(presetLabel(preset))
                            Text(presetDescription(preset), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = {
                        onSelected(preset)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CapabilityPicker(
    label: String,
    catalog: List<NamedCommandDescriptor>,
    selectedKey: String? = null,
    onSelected: (NamedCommandDescriptor) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = catalog.firstOrNull { it.key.value == selectedKey }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = catalog.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selected?.displayName ?: if (catalog.isEmpty()) "No project actions declared" else label)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            catalog.groupBy(NamedCommandDescriptor::category).forEach { (category, descriptors) ->
                DropdownMenuItem(
                    text = { Text(category, color = AresCyan, fontWeight = FontWeight.Bold) },
                    onClick = {},
                    enabled = false
                )
                descriptors.forEach { descriptor ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(descriptor.displayName)
                                Text(descriptor.description, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        onClick = {
                            onSelected(descriptor)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RemovableActionRow(prefix: String, key: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(AresBackground.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$prefix: $key", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, "Remove action", tint = AresError, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddStepBar(
    hasCapabilities: Boolean,
    commandCatalog: List<NamedCommandDescriptor>,
    onAddDrive: () -> Unit,
    onAddWait: () -> Unit,
    onAddCommand: (String) -> Unit
) {
    var actionExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onAddDrive,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = Color.Black)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add drive goal", fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddWait, modifier = Modifier.weight(1f)) {
                Text("Add wait")
            }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { actionExpanded = true },
                    enabled = hasCapabilities,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (hasCapabilities) "Add robot action" else "No project actions")
                }
                DropdownMenu(expanded = actionExpanded, onDismissRequest = { actionExpanded = false }) {
                    commandCatalog.groupBy(NamedCommandDescriptor::category).forEach { (category, descriptors) ->
                        DropdownMenuItem(
                            text = { Text(category, color = AresCyan, fontWeight = FontWeight.Bold) },
                            onClick = {},
                            enabled = false
                        )
                        descriptors.forEach { descriptor ->
                            DropdownMenuItem(
                                text = { Text(descriptor.displayName) },
                                onClick = {
                                    onAddCommand(descriptor.key.value)
                                    actionExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun stepTitle(step: AutoStep): String = when (step.kind) {
    AutoStepKind.DRIVE -> "Drive to field goal"
    AutoStepKind.COMMAND -> "Run robot action"
    AutoStepKind.WAIT -> "Wait"
    AutoStepKind.TOGETHER -> "Run together"
    AutoStepKind.FIRST_TO_FINISH -> "First to finish"
}

private fun stepSubtitle(step: AutoStep): String = when (step.kind) {
    AutoStepKind.DRIVE -> step.drive?.let {
        "${formatNumber(it.target.xMeters)} m, ${formatNumber(it.target.yMeters)} m · ${presetLabel(it.preset)}"
    } ?: "Missing drive target"
    AutoStepKind.COMMAND -> step.commandKey ?: "Select an action"
    AutoStepKind.WAIT -> "${formatNumber(step.durationSeconds ?: 0.0)} seconds"
    AutoStepKind.TOGETHER,
    AutoStepKind.FIRST_TO_FINISH -> "${step.children.size} child steps"
}

private fun presetLabel(preset: TrajectoryPreset): String = when (preset) {
    TrajectoryPreset.SAFE -> "Safe"
    TrajectoryPreset.BALANCED -> "Balanced"
    TrajectoryPreset.FAST -> "Fast"
    TrajectoryPreset.ADAPTIVE -> "Adaptive"
}

private fun presetDescription(preset: TrajectoryPreset): String = when (preset) {
    TrajectoryPreset.SAFE -> "Maximum stability and generous tracking margin"
    TrajectoryPreset.BALANCED -> "Reliable match-speed motion"
    TrajectoryPreset.FAST -> "Use the best installed dynamics optimizer"
    TrajectoryPreset.ADAPTIVE -> "Allow runtime replanning around changes"
}

private fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)

@Composable
private fun editorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AresCyan,
    unfocusedBorderColor = AresBorder,
    focusedTextColor = AresTextPrimary,
    unfocusedTextColor = AresTextPrimary
)
