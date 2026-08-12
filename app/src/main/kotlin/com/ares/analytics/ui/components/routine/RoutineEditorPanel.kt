package com.ares.analytics.ui.components.routine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.PathPlannerIntent
import com.ares.analytics.viewmodel.PathPlannerState
import com.ares.analytics.viewmodel.pathing.RobotDimensions
import com.ares.analytics.service.AresGenerationPhase
import com.areslib.catalog.ActionDescriptor
import com.areslib.catalog.CapabilityParameterDescriptor
import com.areslib.catalog.CapabilityParameterType
import com.areslib.catalog.ConditionDescriptor
import com.areslib.routine.*
import java.util.Locale

/** Primary trigger-neutral routine editor shared by autonomous, teleop macros, and tests. */
@Composable
fun RoutineEditorPanel(
    state: PathPlannerState,
    projectPath: String?,
    league: League,
    onRobotDimensionsChanged: (RobotDimensions) -> Unit,
    onIntent: (PathPlannerIntent) -> Unit
) {
    var openExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    var setupExpanded by remember { mutableStateOf(false) }
    val hasErrors = state.routineValidation.any { it.severity == RoutineValidationSeverity.ERROR }
    val hasPlayablePreview = state.routinePreviewWarning == null &&
        state.trajectory != null && state.estimatedDuration > 0.0
    val generationStatus = when {
        state.generationPhase == AresGenerationPhase.RUNNING -> state.generationMessage ?: "Generating robot code..."
        state.generationPhase == AresGenerationPhase.FAILED -> state.generationMessage ?: "Robot code generation failed"
        state.saveStatus.contains("unsaved", ignoreCase = true) -> state.saveStatus
        state.generationPhase == AresGenerationPhase.SUCCEEDED -> "Robot code generated and ready to build"
        else -> state.saveStatus.takeIf(String::isNotBlank)
    }

    Column(
        Modifier.width(460.dp).fillMaxHeight()
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.routine.name,
                    onValueChange = { onIntent(PathPlannerIntent.UpdateRoutineName(it)) },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = routineTextFieldColors()
                )
                Box {
                    OutlinedButton(onClick = { openExpanded = true }) {
                        Text("Open")
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(openExpanded, { openExpanded = false }) {
                        if (state.availableRoutines.isEmpty()) {
                            DropdownMenuItem({ Text("No saved routines") }, {}, enabled = false)
                        }
                        state.availableRoutines.forEach { saved ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(saved.name)
                                        Text("Revision ${saved.revision}", style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = {
                                    onIntent(PathPlannerIntent.LoadRoutine(projectPath, saved.documentId))
                                    openExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { onIntent(PathPlannerIntent.CreateRoutine()) }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
                Button(
                    onClick = { onIntent(PathPlannerIntent.SaveAndGenerateRoutine(projectPath, league)) },
                    enabled = projectPath != null && !hasErrors && state.generationPhase != AresGenerationPhase.RUNNING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresBackground)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.generationPhase == AresGenerationPhase.RUNNING) "Generating..." else "Save & Generate")
                }
                Box {
                    TextButton(onClick = { historyExpanded = true }, enabled = state.routineRevisions.isNotEmpty()) {
                        Icon(Icons.Default.History, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("R${state.routine.revision}")
                    }
                    DropdownMenu(historyExpanded, { historyExpanded = false }) {
                        state.routineRevisions.forEach { revision ->
                            DropdownMenuItem(
                                text = { Text("Restore revision ${revision.revision}") },
                                enabled = revision.revision != state.routine.revision,
                                onClick = {
                                    onIntent(PathPlannerIntent.RestoreRoutine(projectPath, revision.contentHash))
                                    historyExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (state.routinePreviewWarning == null) {
                        "${state.routine.steps.size} ${if (state.routine.steps.size == 1) "step" else "steps"}  •  " +
                            "drive preview ${formatRoutineNumber(state.estimatedDuration)} s"
                    } else {
                        "${state.routine.steps.size} ${if (state.routine.steps.size == 1) "step" else "steps"}  •  preview unavailable"
                    },
                    color = AresTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onIntent(PathPlannerIntent.TogglePlayback) },
                        enabled = hasPlayablePreview,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            if (state.isPlaying) "Pause preview" else "Play preview",
                            tint = if (hasPlayablePreview) AresCyan else AresTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (state.routinePreviewWarning == null) {
                        Text("${formatRoutineNumber(state.playbackTime)} s", color = AresTextSecondary)
                    }
                }
            }
            state.routinePreviewWarning?.let { warning ->
                Text(
                    warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = AresGold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            generationStatus?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        status.contains("unsaved", ignoreCase = true) -> AresGold
                        state.generationPhase == AresGenerationPhase.FAILED -> AresError
                        state.generationPhase == AresGenerationPhase.SUCCEEDED -> AresGreen
                        else -> statusColor(status)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = AresBorder)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.routineValidation.isNotEmpty()) {
                item { RoutineValidationCard(state.routineValidation) }
            }
            item {
                RoutineSetupCard(
                    state = state,
                    projectPath = projectPath,
                    league = league,
                    expanded = setupExpanded,
                    onExpandedChanged = { setupExpanded = it },
                    onRobotDimensionsChanged = onRobotDimensionsChanged,
                    onIntent = onIntent
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("ROUTINE STEPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AresTextSecondary)
                    Text("${state.routine.steps.size}", style = MaterialTheme.typography.labelSmall, color = AresCyan)
                }
            }
            if (state.routine.steps.isEmpty()) {
                item { EmptyRoutineCard() }
            }
            itemsIndexed(state.routine.steps, key = { _, step -> step.stepId }) { index, step ->
                RoutineStepCard(
                    index = index,
                    step = step,
                    stepCount = state.routine.steps.size,
                    actions = state.routineActions,
                    conditions = state.routineConditions,
                    routines = state.availableRoutines.filter { it.documentId != state.routine.documentId },
                    issues = state.routineValidation.filter { it.path.contains("/${step.stepId}") },
                    onUpdate = { onIntent(PathPlannerIntent.UpdateRoutineStep(step.stepId, it)) },
                    onMove = { onIntent(PathPlannerIntent.MoveRoutineStep(step.stepId, it)) },
                    onRemove = { onIntent(PathPlannerIntent.RemoveRoutineStep(step.stepId)) },
                    onAddChild = { elseBranch, kind ->
                        onIntent(PathPlannerIntent.AddRoutineChild(step.stepId, elseBranch, kind))
                    },
                    onUpdateChild = { childStepId, _, updated ->
                        onIntent(PathPlannerIntent.UpdateRoutineChild(childStepId, updated))
                    },
                    onRemoveChild = { childStepId, _ ->
                        onIntent(PathPlannerIntent.RemoveRoutineChild(childStepId))
                    }
                )
            }
        }

        HorizontalDivider(color = AresBorder)
        AddRoutineStepBar(
            hasActions = state.routineActions.isNotEmpty(),
            hasConditions = state.routineConditions.isNotEmpty(),
            hasOtherRoutines = state.availableRoutines.any { it.documentId != state.routine.documentId },
            onAdd = { onIntent(PathPlannerIntent.AddRoutineStep(it)) }
        )
    }
}

@Composable
private fun RoutineSetupCard(
    state: PathPlannerState,
    projectPath: String?,
    league: League,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onRobotDimensionsChanged: (RobotDimensions) -> Unit,
    onIntent: (PathPlannerIntent) -> Unit
) {
    val entry = state.autonomousEntry
    val dimensions = state.robotDimensions
    val modeLabel = if (state.availableInAutonomousSelector) "Match autonomous" else "Reusable routine"
    val footprintLabel = "${formatRoutineNumber(dimensions.lengthMeters)} × ${formatRoutineNumber(dimensions.widthMeters)} m"

    Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, AresBorder)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Routine setup", fontWeight = FontWeight.Bold, color = AresTextPrimary)
                    Text(
                        "$modeLabel  •  $footprintLabel footprint",
                        style = MaterialTheme.typography.labelSmall,
                        color = AresTextSecondary
                    )
                }
                Text("Match auto", style = MaterialTheme.typography.labelSmall, color = AresTextSecondary)
                Switch(
                    checked = state.availableInAutonomousSelector,
                    onCheckedChange = { checked ->
                        if (checked) onExpandedChanged(true)
                        onIntent(PathPlannerIntent.SetAutonomousAvailability(checked, league))
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = { onExpandedChanged(!expanded) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (expanded) "Hide routine setup" else "Show routine setup"
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = AresBorder.copy(alpha = .7f))
                OutlinedTextField(
                    value = state.routine.description.orEmpty(),
                    onValueChange = { onIntent(PathPlannerIntent.UpdateRoutineDescription(it)) },
                    label = { Text("What this routine does (optional)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = routineTextFieldColors()
                )

                Text("Robot footprint", fontWeight = FontWeight.SemiBold, color = AresTextPrimary)
                Text(
                    "Used to keep every drive goal safely inside the field.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AresTextSecondary
                )
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    RoutineDecimalEditor(dimensions.lengthMeters, "Length", "m", Modifier.weight(1f)) {
                        if (it in RobotDimensions.MIN_SIZE_METERS..RobotDimensions.MAX_SIZE_METERS) {
                            onRobotDimensionsChanged(dimensions.copy(lengthMeters = it))
                        }
                    }
                    RoutineDecimalEditor(dimensions.widthMeters, "Width", "m", Modifier.weight(1f)) {
                        if (it in RobotDimensions.MIN_SIZE_METERS..RobotDimensions.MAX_SIZE_METERS) {
                            onRobotDimensionsChanged(dimensions.copy(widthMeters = it))
                        }
                    }
                }

                if (entry != null) {
                    HorizontalDivider(color = AresBorder.copy(alpha = .7f))
                    Text("Autonomous starting pose", color = AresCyan, fontWeight = FontWeight.SemiBold)
                    RoutinePoseEditors(entry.startingPose) {
                        onIntent(PathPlannerIntent.UpdateAutonomousEntry(entry.copy(startingPose = it), league))
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                        RoutineAlliancePicker(entry.authoredAlliance) {
                            onIntent(PathPlannerIntent.UpdateAutonomousEntry(entry.copy(authoredAlliance = it), league))
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = entry.mirrorForOppositeAlliance,
                                onCheckedChange = {
                                    onIntent(PathPlannerIntent.UpdateAutonomousEntry(entry.copy(mirrorForOppositeAlliance = it), league))
                                }
                            )
                            Text("Mirror alliance", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text(state.capabilityStatus, style = MaterialTheme.typography.labelSmall, color = AresTextSecondary)
            }
        }
    }
}

@Composable
private fun RoutineAlliancePicker(value: RoutineAlliance, onChange: (RoutineAlliance) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Authored for ${value.name.lowercase()}") }
        DropdownMenu(expanded, { expanded = false }) {
            RoutineAlliance.entries.forEach { alliance ->
                DropdownMenuItem({ Text(alliance.name.lowercase().replaceFirstChar(Char::uppercase)) }, {
                    onChange(alliance)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun RoutineValidationCard(issues: List<RoutineValidationIssue>) {
    val hasErrors = issues.any { it.severity == RoutineValidationSeverity.ERROR }
    val accent = if (hasErrors) AresError else AresGold
    Card(colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = .1f)), border = BorderStroke(1.dp, accent)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (hasErrors) "Needs attention" else "Review before deployment", color = accent, fontWeight = FontWeight.Bold)
            issues.take(5).forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall, color = AresTextPrimary) }
            if (issues.size > 5) Text("+ ${issues.size - 5} more", style = MaterialTheme.typography.labelSmall, color = AresTextSecondary)
        }
    }
}

@Composable
private fun EmptyRoutineCard() {
    Card(colors = CardDefaults.cardColors(containerColor = AresCyan.copy(alpha = .08f)), border = BorderStroke(1.dp, AresCyan.copy(alpha = .5f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Add what the robot should do", color = AresCyan, fontWeight = FontWeight.Bold)
            Text("A routine can become an autonomous choice, a controller macro, or a reusable building block later.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RoutineStepCard(
    index: Int,
    step: RoutineStep,
    stepCount: Int,
    actions: List<ActionDescriptor>,
    conditions: List<ConditionDescriptor>,
    routines: List<RoutineDocument>,
    issues: List<RoutineValidationIssue>,
    onUpdate: (RoutineStep) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onAddChild: (Boolean, RoutineStepKind) -> Unit,
    onUpdateChild: (String, Boolean, RoutineStep) -> Unit,
    onRemoveChild: (String, Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, AresBorder)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = AresCyan.copy(alpha = .14f),
                    border = BorderStroke(1.dp, AresCyan.copy(alpha = .55f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = AresCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(routineStepTitle(step.kind), fontWeight = FontWeight.Bold, color = AresTextPrimary)
                    Text(routineStepSubtitle(step), style = MaterialTheme.typography.labelSmall, color = AresTextSecondary)
                }
                Row {
                    IconButton({ onMove(-1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move earlier", modifier = Modifier.size(18.dp))
                    }
                    IconButton({ onMove(1) }, enabled = index < stepCount - 1, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move later", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Remove", tint = AresError, modifier = Modifier.size(17.dp))
                    }
                }
            }
            val stepErrors = issues.filter { it.severity == RoutineValidationSeverity.ERROR }
            if (stepErrors.isNotEmpty()) {
                Surface(
                    color = AresError.copy(alpha = .08f),
                    border = BorderStroke(1.dp, AresError.copy(alpha = .45f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        stepErrors.take(3).forEach { issue ->
                            Text(issue.message, color = AresError, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            when (step.kind) {
                RoutineStepKind.ACTION -> {
                    ActionPicker(actions, step.actionKey) { onUpdate(step.copy(actionKey = it.key, arguments = defaultsFor(it.parameters))) }
                    actions.firstOrNull { it.key == step.actionKey }?.let { descriptor ->
                        Text(descriptor.description, style = MaterialTheme.typography.bodySmall, color = AresTextSecondary)
                        ParameterEditors(descriptor.parameters, step.arguments, issues) { onUpdate(step.copy(arguments = it)) }
                    }
                }
                RoutineStepKind.DRIVE_TO -> step.drive?.let { drive ->
                    RoutinePoseEditors(drive.target) { onUpdate(step.copy(drive = drive.copy(target = it))) }
                    MotionPresetPicker(drive.motionPresetKey) { onUpdate(step.copy(drive = drive.copy(motionPresetKey = it))) }
                    DriveActionList("Run while driving", drive.duringActionKeys, actions) {
                        onUpdate(step.copy(drive = drive.copy(duringActionKeys = it)))
                    }
                    DriveActionList("Run on arrival", drive.arrivalActionKeys, actions) {
                        onUpdate(step.copy(drive = drive.copy(arrivalActionKeys = it)))
                    }
                }
                RoutineStepKind.WAIT -> RoutineDecimalEditor(step.durationSeconds ?: 0.0, "Duration", "s") {
                    onUpdate(step.copy(durationSeconds = it.coerceAtLeast(0.0)))
                }
                RoutineStepKind.WAIT_UNTIL -> {
                    ConditionPicker(conditions, step.conditionKey) {
                        onUpdate(step.copy(conditionKey = it.key, arguments = defaultsFor(it.parameters)))
                    }
                    RoutineDecimalEditor(step.timeoutSeconds ?: 0.0, "Safety timeout", "s") {
                        onUpdate(step.copy(timeoutSeconds = it.coerceAtLeast(.01)))
                    }
                    conditions.firstOrNull { it.key == step.conditionKey }?.let { descriptor ->
                        ParameterEditors(descriptor.parameters, step.arguments, issues) { onUpdate(step.copy(arguments = it)) }
                    }
                }
                RoutineStepKind.CALL -> RoutinePicker(routines, step.routineId) { onUpdate(step.copy(routineId = it.documentId)) }
                RoutineStepKind.REPEAT -> {
                    RoutineDecimalEditor((step.repeatCount ?: 1).toDouble(), "Repeat", "times") {
                        onUpdate(step.copy(repeatCount = it.toInt().coerceIn(1, 1000)))
                    }
                    ChildLane("Steps to repeat", index, step.children, false, actions, conditions, routines, onAddChild, onUpdateChild, onRemoveChild)
                }
                RoutineStepKind.BRANCH -> {
                    ConditionPicker(conditions, step.conditionKey) {
                        onUpdate(step.copy(conditionKey = it.key, arguments = defaultsFor(it.parameters)))
                    }
                    conditions.firstOrNull { it.key == step.conditionKey }?.let { descriptor ->
                        ParameterEditors(descriptor.parameters, step.arguments, issues) { onUpdate(step.copy(arguments = it)) }
                    }
                    ChildLane("When true", index, step.children, false, actions, conditions, routines, onAddChild, onUpdateChild, onRemoveChild)
                    ChildLane("Otherwise", index, step.elseChildren, true, actions, conditions, routines, onAddChild, onUpdateChild, onRemoveChild)
                }
                RoutineStepKind.DEADLINE -> {
                    Text("This step ends when its deadline finishes.", style = MaterialTheme.typography.bodySmall, color = AresTextSecondary)
                    step.deadline?.let { deadline ->
                        SimpleChildEditor(deadline, actions, conditions, routines, { onUpdate(step.copy(deadline = it)) }, {})
                    }
                    ChildLane("Run alongside deadline", index, step.children, false, actions, conditions, routines, onAddChild, onUpdateChild, onRemoveChild)
                }
                RoutineStepKind.TOGETHER,
                RoutineStepKind.FIRST_TO_FINISH -> ChildLane(
                    if (step.kind == RoutineStepKind.TOGETHER) "Parallel steps" else "Race steps",
                    index,
                    step.children,
                    false,
                    actions,
                    conditions,
                    routines,
                    onAddChild,
                    onUpdateChild,
                    onRemoveChild
                )
            }
        }
    }
}

@Composable
private fun ChildLane(
    label: String,
    parentIndex: Int,
    children: List<RoutineStep>,
    elseBranch: Boolean,
    actions: List<ActionDescriptor>,
    conditions: List<ConditionDescriptor>,
    routines: List<RoutineDocument>,
    onAdd: (Boolean, RoutineStepKind) -> Unit,
    onUpdate: (String, Boolean, RoutineStep) -> Unit,
    onRemove: (String, Boolean) -> Unit
) {
    Text(label, color = AresCyan, fontWeight = FontWeight.SemiBold)
    children.forEach { child ->
        SimpleChildEditor(
            child,
            actions,
            conditions,
            routines,
            { onUpdate(child.stepId, elseBranch, it) },
            { onRemove(child.stepId, elseBranch) }
        )
    }
    CompactStepPicker(
        label = "Add child step",
        unavailableReason = { kind ->
            when {
                kind == RoutineStepKind.ACTION && actions.isEmpty() -> "Declare a project action first"
                kind in setOf(RoutineStepKind.WAIT_UNTIL, RoutineStepKind.BRANCH) && conditions.isEmpty() -> "Declare a project condition first"
                kind == RoutineStepKind.CALL && routines.isEmpty() -> "Save another routine first"
                else -> null
            }
        }
    ) { onAdd(elseBranch, it) }
}

@Composable
private fun SimpleChildEditor(
    step: RoutineStep,
    actions: List<ActionDescriptor>,
    conditions: List<ConditionDescriptor>,
    routines: List<RoutineDocument>,
    onUpdate: (RoutineStep) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(AresBackground.copy(alpha = .45f), RoundedCornerShape(8.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(routineStepTitle(step.kind), style = MaterialTheme.typography.labelLarge)
            IconButton(onRemove, Modifier.size(28.dp)) { Icon(Icons.Default.Delete, "Remove child", tint = AresError, modifier = Modifier.size(16.dp)) }
        }
        when (step.kind) {
            RoutineStepKind.ACTION -> ActionPicker(actions, step.actionKey) { onUpdate(step.copy(actionKey = it.key, arguments = defaultsFor(it.parameters))) }
            RoutineStepKind.WAIT -> RoutineDecimalEditor(step.durationSeconds ?: 0.0, "Duration", "s") { onUpdate(step.copy(durationSeconds = it.coerceAtLeast(0.0))) }
            RoutineStepKind.DRIVE_TO -> step.drive?.let { drive -> RoutinePoseEditors(drive.target) { onUpdate(step.copy(drive = drive.copy(target = it))) } }
            RoutineStepKind.WAIT_UNTIL -> ConditionPicker(conditions, step.conditionKey) { onUpdate(step.copy(conditionKey = it.key, arguments = defaultsFor(it.parameters))) }
            RoutineStepKind.CALL -> RoutinePicker(routines, step.routineId) { onUpdate(step.copy(routineId = it.documentId)) }
            else -> Text("Nested ${routineStepTitle(step.kind).lowercase()} group", style = MaterialTheme.typography.bodySmall, color = AresTextSecondary)
        }
    }
}

@Composable
private fun RoutinePoseEditors(pose: RoutinePose, onChanged: (RoutinePose) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        RoutineDecimalEditor(pose.xMeters, "X", "m", Modifier.weight(1f)) { onChanged(pose.copy(xMeters = it)) }
        RoutineDecimalEditor(pose.yMeters, "Y", "m", Modifier.weight(1f)) { onChanged(pose.copy(yMeters = it)) }
        RoutineDecimalEditor(Math.toDegrees(pose.headingRadians), "Heading", "°", Modifier.weight(1f)) {
            val wrapped = ((it + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            onChanged(pose.copy(headingRadians = Math.toRadians(wrapped)))
        }
    }
}

@Composable
private fun ActionPicker(actions: List<ActionDescriptor>, selectedKey: String?, onSelected: (ActionDescriptor) -> Unit) =
    DescriptorPicker(
        selected = actions.firstOrNull { it.key == selectedKey }?.displayName,
        emptyLabel = "No project actions declared",
        placeholder = "Choose robot action",
        items = actions,
        category = ActionDescriptor::category,
        title = ActionDescriptor::displayName,
        description = ActionDescriptor::description,
        onSelected = onSelected
    )

@Composable
private fun ConditionPicker(conditions: List<ConditionDescriptor>, selectedKey: String?, onSelected: (ConditionDescriptor) -> Unit) =
    DescriptorPicker(
        selected = conditions.firstOrNull { it.key == selectedKey }?.displayName,
        emptyLabel = "No project conditions declared",
        placeholder = "Choose robot state condition",
        items = conditions,
        category = ConditionDescriptor::category,
        title = ConditionDescriptor::displayName,
        description = ConditionDescriptor::description,
        onSelected = onSelected
    )

@Composable
private fun <T> DescriptorPicker(
    selected: String?,
    emptyLabel: String,
    placeholder: String,
    items: List<T>,
    category: (T) -> String,
    title: (T) -> String,
    description: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(selected ?: if (items.isEmpty()) emptyLabel else placeholder)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            items.groupBy(category).forEach { (group, descriptors) ->
                DropdownMenuItem({ Text(group, color = AresCyan, fontWeight = FontWeight.Bold) }, {}, enabled = false)
                descriptors.forEach { item ->
                    DropdownMenuItem(
                        text = { Column { Text(title(item)); Text(description(item), style = MaterialTheme.typography.labelSmall) } },
                        onClick = { onSelected(item); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutinePicker(routines: List<RoutineDocument>, selectedId: String?, onSelected: (RoutineDocument) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = routines.firstOrNull { it.documentId == selectedId }
    Box {
        OutlinedButton({ expanded = true }, enabled = routines.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: if (routines.isEmpty()) "No other routines saved" else "Choose routine")
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            routines.forEach { routine -> DropdownMenuItem({ Text(routine.name) }, { onSelected(routine); expanded = false }) }
        }
    }
}

@Composable
private fun ParameterEditors(
    descriptors: List<CapabilityParameterDescriptor>,
    arguments: Map<String, String>,
    issues: List<RoutineValidationIssue> = emptyList(),
    onChanged: (Map<String, String>) -> Unit
) {
    descriptors.forEach { descriptor ->
        val value = arguments[descriptor.key] ?: defaultValue(descriptor)
        val fieldError = issues.firstOrNull {
            it.severity == RoutineValidationSeverity.ERROR && it.path.endsWith(".arguments.${descriptor.key}")
        }
        when (descriptor.type) {
            CapabilityParameterType.BOOLEAN -> Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(value == "true", { checked -> onChanged(arguments + (descriptor.key to checked.toString())) })
                    Spacer(Modifier.width(8.dp)); Column { Text(descriptor.displayName); Text(descriptor.description, style = MaterialTheme.typography.labelSmall, color = AresTextSecondary) }
                }
                fieldError?.let { Text(it.message, color = AresError, style = MaterialTheme.typography.labelSmall) }
            }
            CapabilityParameterType.ENUM -> EnumParameterPicker(descriptor, value) { onChanged(arguments + (descriptor.key to it)) }
            CapabilityParameterType.NUMBER,
            CapabilityParameterType.TEXT -> {
                val unit = descriptor.unit
                OutlinedTextField(
                    value = value,
                    onValueChange = { onChanged(arguments + (descriptor.key to it)) },
                    label = { Text(descriptor.displayName) },
                    supportingText = { Text(descriptor.description) },
                    isError = fieldError != null,
                    suffix = if (unit == null) null else ({ Text(unit) }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = routineTextFieldColors()
                )
            }
        }
    }
}

@Composable
private fun EnumParameterPicker(descriptor: CapabilityParameterDescriptor, value: String, onChanged: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("${descriptor.displayName}: $value"); Text(descriptor.description, style = MaterialTheme.typography.labelSmall) }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            descriptor.options.forEach { option -> DropdownMenuItem({ Text(option) }, { onChanged(option); expanded = false }) }
        }
    }
}

@Composable
private fun DriveActionList(label: String, keys: List<String>, actions: List<ActionDescriptor>, onChanged: (List<String>) -> Unit) {
    DescriptorPicker(
        selected = null,
        emptyLabel = "No project actions declared",
        placeholder = label,
        items = actions,
        category = ActionDescriptor::category,
        title = ActionDescriptor::displayName,
        description = ActionDescriptor::description
    ) { onChanged(keys + it.key) }
    keys.forEach { key ->
        Row(Modifier.fillMaxWidth().background(AresBackground.copy(alpha = .4f), RoundedCornerShape(6.dp)).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(actions.firstOrNull { it.key == key }?.displayName ?: key, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton({ onChanged(keys - key) }, Modifier.size(30.dp)) { Icon(Icons.Default.Delete, "Remove", tint = AresError, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun MotionPresetPicker(selected: String, onSelected: (String) -> Unit) {
    val presets = listOf("safe", "balanced", "fast", "adaptive")
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Motion: ${selected.replaceFirstChar(Char::uppercase)}"); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            presets.forEach { preset -> DropdownMenuItem({ Text(preset.replaceFirstChar(Char::uppercase)) }, { onSelected(preset); expanded = false }) }
        }
    }
}

@Composable
private fun AddRoutineStepBar(hasActions: Boolean, hasConditions: Boolean, hasOtherRoutines: Boolean, onAdd: (RoutineStepKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            { onAdd(RoutineStepKind.DRIVE_TO) },
            Modifier.weight(1.15f),
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = Color.Black)
        ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add drive goal", fontWeight = FontWeight.Bold) }
        CompactStepPicker(
            label = "Other step",
            modifier = Modifier.weight(.85f),
            unavailableReason = { kind ->
                when {
                    kind == RoutineStepKind.ACTION && !hasActions -> "Declare a project action first"
                    kind in setOf(RoutineStepKind.WAIT_UNTIL, RoutineStepKind.BRANCH) && !hasConditions -> "Declare a project condition first"
                    kind == RoutineStepKind.CALL && !hasOtherRoutines -> "Save another routine first"
                    else -> null
                }
            },
            onAdd = onAdd
        )
    }
}

@Composable
private fun CompactStepPicker(
    label: String,
    modifier: Modifier = Modifier,
    unavailableReason: (RoutineStepKind) -> String? = { null },
    onAdd: (RoutineStepKind) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            RoutineStepKind.entries.forEach { kind ->
                val reason = unavailableReason(kind)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(routineStepTitle(kind))
                            Text(
                                reason ?: routineStepDescription(kind),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reason == null) AresTextSecondary else AresGold
                            )
                        }
                    },
                    onClick = { onAdd(kind); expanded = false },
                    enabled = reason == null
                )
            }
        }
    }
}

@Composable
private fun RoutineDecimalEditor(value: Double, label: String, suffix: String, modifier: Modifier = Modifier, onChanged: (Double) -> Unit) {
    var text by remember { mutableStateOf(formatRoutineNumber(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = formatRoutineNumber(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { updated -> text = updated; updated.toDoubleOrNull()?.takeIf(Double::isFinite)?.let(onChanged) },
        label = { Text(label) }, suffix = { Text(suffix) }, singleLine = true,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        colors = routineTextFieldColors()
    )
}

private fun defaultsFor(parameters: List<CapabilityParameterDescriptor>): Map<String, String> = buildMap {
    parameters.forEach { parameter -> defaultValue(parameter).takeIf(String::isNotEmpty)?.let { put(parameter.key, it) } }
}

private fun defaultValue(parameter: CapabilityParameterDescriptor): String = when (parameter.type) {
    CapabilityParameterType.NUMBER -> parameter.defaultNumber?.toString().orEmpty()
    CapabilityParameterType.BOOLEAN -> parameter.defaultBoolean?.toString().orEmpty()
    CapabilityParameterType.TEXT,
    CapabilityParameterType.ENUM -> parameter.defaultText ?: parameter.options.firstOrNull().orEmpty()
}

private fun routineStepTitle(kind: RoutineStepKind): String = when (kind) {
    RoutineStepKind.ACTION -> "Run robot action"
    RoutineStepKind.DRIVE_TO -> "Drive to field goal"
    RoutineStepKind.WAIT -> "Wait"
    RoutineStepKind.WAIT_UNTIL -> "Wait for robot state"
    RoutineStepKind.TOGETHER -> "Run together"
    RoutineStepKind.FIRST_TO_FINISH -> "Race: first to finish"
    RoutineStepKind.DEADLINE -> "Run until deadline"
    RoutineStepKind.CALL -> "Run reusable routine"
    RoutineStepKind.REPEAT -> "Repeat steps"
    RoutineStepKind.BRANCH -> "Choose based on robot state"
}

private fun routineStepDescription(kind: RoutineStepKind): String = when (kind) {
    RoutineStepKind.ACTION -> "Run one action from this robot project"
    RoutineStepKind.DRIVE_TO -> "Move to a position and heading on the field"
    RoutineStepKind.WAIT -> "Pause for a fixed amount of time"
    RoutineStepKind.WAIT_UNTIL -> "Continue when a robot condition becomes true"
    RoutineStepKind.TOGETHER -> "Run every child step at the same time"
    RoutineStepKind.FIRST_TO_FINISH -> "Run children together and stop when one finishes"
    RoutineStepKind.DEADLINE -> "Run companions until the main step finishes"
    RoutineStepKind.CALL -> "Place another saved routine inside this routine"
    RoutineStepKind.REPEAT -> "Run a group of steps a fixed number of times"
    RoutineStepKind.BRANCH -> "Choose between two groups using robot state"
}

private fun routineStepSubtitle(step: RoutineStep): String = when (step.kind) {
    RoutineStepKind.ACTION -> step.actionKey ?: "Choose an action"
    RoutineStepKind.DRIVE_TO -> step.drive?.let { drive ->
        "${formatRoutineNumber(drive.target.xMeters)} m, ${formatRoutineNumber(drive.target.yMeters)} m · ${drive.motionPresetKey}"
    } ?: "Missing target"
    RoutineStepKind.WAIT -> "${formatRoutineNumber(step.durationSeconds ?: 0.0)} seconds"
    RoutineStepKind.WAIT_UNTIL -> "${step.conditionKey ?: "Choose condition"} · timeout ${formatRoutineNumber(step.timeoutSeconds ?: 0.0)} s"
    RoutineStepKind.CALL -> step.routineId ?: "Choose routine"
    RoutineStepKind.REPEAT -> "${step.repeatCount ?: 0} times · ${step.children.size} step(s)"
    RoutineStepKind.BRANCH -> "${step.conditionKey ?: "Choose condition"} · ${step.children.size}/${step.elseChildren.size} step(s)"
    RoutineStepKind.DEADLINE -> "${step.children.size} companion step(s)"
    RoutineStepKind.TOGETHER,
    RoutineStepKind.FIRST_TO_FINISH -> "${step.children.size} parallel step(s)"
}

private fun formatRoutineNumber(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun statusColor(status: String): Color = if (status.contains("fail", true) || status.contains("fix", true)) AresError else AresTextSecondary

@Composable
private fun routineTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AresCyan,
    unfocusedBorderColor = AresBorder,
    focusedTextColor = AresTextPrimary,
    unfocusedTextColor = AresTextPrimary
)
