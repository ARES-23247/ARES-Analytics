package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.project.ProjectIdentityEditorState
import com.ares.analytics.viewmodel.project.ProjectIdentityField
import com.ares.analytics.viewmodel.project.ProjectIdentityViewModel

/** Reviewed editor for canonical `.ares/project.json`; workspace preferences remain separate. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProjectIdentityScreen(
    viewModel: ProjectIdentityViewModel,
    config: WorkspaceConfig,
    onBackToStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(config) { viewModel.load(config) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onBackToStudio != null) {
                    OutlinedButton(onClick = onBackToStudio) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Robot Studio")
                    }
                }
                Column {
                    Text(
                        "Project Identity & Robot Footprint",
                        modifier = Modifier.semantics { heading() },
                        color = AresTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        "Configure the robot identity and outer physical boundary shared by simulators, collision checkers, and autonomous paths.",
                        color = AresTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item { ProjectIdentityDestinationCard(state) }

        if (state.loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                    Text("Reading canonical project identity…", color = AresTextSecondary)
                }
            }
        } else {
            state.protectedError?.let { error ->
                item { ProtectedProjectIdentityCard(error, repairAvailable = state.protectedContentHash != null) }
            }
            item {
                ProjectIdentityForm(
                    state = state,
                    onUpdate = viewModel::update,
                )
            }
            if (state.generalErrors.isNotEmpty()) {
                item { ProjectIdentityErrors(state.generalErrors) }
            }
            state.message?.let { message -> item { ProjectIdentityMessage(message, state.messageIsError) } }
            state.proposal?.let { proposal ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                        border = BorderStroke(1.dp, AresGold),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Reviewed diff · confirmation required", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                if (proposal.expectedInvalidRawContentHash != null) {
                                    "The invalid file is hash-bound to this review and will be copied byte-for-byte to .ares/recovery/project before .ares/project.json is replaced."
                                } else {
                                    "Only .ares/project.json will change. Existing valid content is checkpointed under .ares/history/project before replacement."
                                },
                                color = AresTextSecondary,
                                fontSize = 12.sp,
                            )
                            proposal.changes.forEach { change ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(change.label, color = AresTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Before: ${change.before}", color = AresTextSecondary, fontFamily = FontFamily.Monospace)
                                    Text("After:  ${change.after}", color = AresTextPrimary, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(
                                "Proposed SHA-256: ${proposal.proposedContentHash}",
                                color = AresTextTertiary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = viewModel::applyReviewed,
                                    enabled = !state.saving,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AresCyan,
                                        contentColor = AresOnAccent,
                                    ),
                                ) {
                                    Text(
                                        when {
                                            proposal.expectedInvalidRawContentHash != null -> "Preserve original and repair identity"
                                            state.currentDocument == null -> "Create reviewed identity"
                                            else -> "Save reviewed changes"
                                        },
                                    )
                                }
                                OutlinedButton(onClick = viewModel::cancelReview, enabled = !state.saving) {
                                    Text("Keep editing")
                                }
                            }
                        }
                    }
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = viewModel::review,
                        enabled = state.canReview && state.proposal == null,
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                    ) {
                        Text(if (state.protectedContentHash != null) "Review protected-file repair" else "Review structured diff")
                    }
                    OutlinedButton(onClick = viewModel::resetDraft, enabled = !state.saving) {
                        Text("Discard draft changes")
                    }
                    OutlinedButton(onClick = { viewModel.load(config) }, enabled = !state.saving) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Reload project file")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectIdentityDestinationCard(state: ProjectIdentityEditorState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = BorderStroke(1.dp, AresBorder),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Selected project", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            Text(state.projectPath, color = AresTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            HorizontalDivider(color = AresBorder)
            Text("Stored in: .ares/project.json", color = AresTextPrimary)
            Text(
                "Consumed by: Robot Studio, Drivebase Builder, Superstructure Studio, Autonomous Planner, simulators, and collision bounds.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ProtectedProjectIdentityCard(error: String, repairAvailable: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresError.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, AresError),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Error, contentDescription = null, tint = AresError)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (repairAvailable) "Invalid project file preserved · reviewed repair available"
                    else "Protected project file · no write allowed",
                    color = AresTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(error, color = AresTextSecondary)
            }
        }
    }
}

@Composable
private fun ProjectIdentityForm(
    state: ProjectIdentityEditorState,
    onUpdate: (ProjectIdentityField, String) -> Unit,
) {
    val isFtc = state.workspaceLeague == League.FTC
    val lengthM = state.draft.robotLengthMeters.toDoubleOrNull() ?: 0.0
    val widthM = state.draft.robotWidthMeters.toDoubleOrNull() ?: 0.0
    val lengthIn = lengthM * 39.3701
    val widthIn = widthM * 39.3701
    val fitsSizingBox = !isFtc || (lengthM <= 0.4572 && widthM <= 0.4572 && lengthM > 0.0 && widthM > 0.0)

    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = BorderStroke(1.dp, AresBorder),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Canonical identity", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "The project ID is a stable reference, not a display name. After creation it is locked here so renaming cannot silently break other documents.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            IdentityField(
                label = "Stable project ID",
                value = state.draft.projectId,
                onValueChange = { onUpdate(ProjectIdentityField.PROJECT_ID, it) },
                error = state.fieldErrors[ProjectIdentityField.PROJECT_ID],
                enabled = state.currentDocument == null &&
                    (state.protectedError == null || state.protectedContentHash != null),
                help = "Starts with a letter; letters, numbers, dot, underscore, and dash only.",
            )
            ReadOnlyIdentityRow("League", state.workspaceLeague.name)
            ReadOnlyIdentityRow(
                "Coordinate convention",
                if (isFtc) "CENTER_ORIGIN_CCW (0,0 center)" else "BLUE_CORNER_ORIGIN_CCW (0,0 blue corner)",
            )

            HorizontalDivider(color = AresBorder)

            // Robot Outer Footprint Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Robot Bumper Footprint (Outer Dimensions)", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        "Measure bumper-to-bumper outer length and width for collision detection and autonomous path clearance.",
                        color = AresTextSecondary,
                        fontSize = 11.sp,
                    )
                }

                if (isFtc && lengthM > 0.0 && widthM > 0.0) {
                    Surface(
                        color = if (fitsSizingBox) AresGreen.copy(alpha = 0.15f) else AresError.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (fitsSizingBox) AresGreen else AresError),
                    ) {
                        Text(
                            if (fitsSizingBox) "✓ 18\" Sizing Box Compliant" else "⚠ Exceeds 18\" FTC Sizing Box",
                            color = if (fitsSizingBox) AresGreen else AresError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            GeometryRow(
                firstLabel = "Robot length (meters)",
                firstValue = state.draft.robotLengthMeters,
                firstError = state.fieldErrors[ProjectIdentityField.ROBOT_LENGTH],
                onFirst = { onUpdate(ProjectIdentityField.ROBOT_LENGTH, it) },
                firstHelp = if (lengthIn > 0.0) "≈ ${"%.1f".format(lengthIn)} inches" else "Positive decimal meters.",
                secondLabel = "Robot width (meters)",
                secondValue = state.draft.robotWidthMeters,
                secondError = state.fieldErrors[ProjectIdentityField.ROBOT_WIDTH],
                onSecond = { onUpdate(ProjectIdentityField.ROBOT_WIDTH, it) },
                secondHelp = if (widthIn > 0.0) "≈ ${"%.1f".format(widthIn)} inches" else "Positive decimal meters.",
                enabled = state.protectedError == null || state.protectedContentHash != null,
            )

            HorizontalDivider(color = AresBorder)

            // Standard Field Environment Preset Card (Read-only automatic preset)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AresSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AresBorder),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Field Environment: Official ${state.workspaceLeague.name} Standard",
                            color = AresTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        Text(
                            if (isFtc) "Standard 12ft × 12ft (3.66m × 3.66m) competition perimeter. Game elements & AprilTags are configured in the Field Editor / Autonomous Planner."
                            else "Standard 54ft × 27ft (16.54m × 8.21m) competition perimeter. Game elements & AprilTags are configured in the Field Editor / Autonomous Planner.",
                            color = AresTextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeometryRow(
    firstLabel: String,
    firstValue: String,
    firstError: String?,
    onFirst: (String) -> Unit,
    firstHelp: String = "Positive decimal meters.",
    secondLabel: String,
    secondValue: String,
    secondError: String?,
    onSecond: (String) -> Unit,
    secondHelp: String = "Positive decimal meters.",
    enabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IdentityField(firstLabel, firstValue, onFirst, firstError, enabled, firstHelp, Modifier.weight(1f))
        IdentityField(secondLabel, secondValue, onSecond, secondError, enabled, secondHelp, Modifier.weight(1f))
    }
}

@Composable
private fun IdentityField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    help: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(error ?: help) },
        isError = error != null,
        enabled = enabled,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun ReadOnlyIdentityRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(0.35f), color = AresTextSecondary)
        Text(value, modifier = Modifier.weight(0.65f), color = AresTextPrimary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ProjectIdentityErrors(errors: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresError.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, AresError),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cannot review yet", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            errors.forEach { Text("• $it", color = AresTextSecondary) }
        }
    }
}

@Composable
private fun ProjectIdentityMessage(message: String, isError: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isError) AresError else AresGreen,
        )
        Text(
            text = if (isError) "Error: $message" else "Status: $message",
            color = AresTextPrimary,
        )
    }
}
