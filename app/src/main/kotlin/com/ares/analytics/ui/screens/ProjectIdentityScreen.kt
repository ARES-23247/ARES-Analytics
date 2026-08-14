package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGold
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary
import com.ares.analytics.viewmodel.project.ProjectIdentityEditorState
import com.ares.analytics.viewmodel.project.ProjectIdentityField
import com.ares.analytics.viewmodel.project.ProjectIdentityViewModel

/** Reviewed editor for canonical `.ares/project.json`; workspace preferences remain separate. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProjectIdentityScreen(
    viewModel: ProjectIdentityViewModel,
    config: WorkspaceConfig,
    onBackToStudio: () -> Unit,
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
                OutlinedButton(onClick = onBackToStudio) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Robot Studio")
                }
                Column {
                    Text(
                        "Project identity & field frame",
                        modifier = Modifier.semantics { heading() },
                        color = AresTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    )
                    Text(
                        "Create or review the Git-tracked identity shared by builders, generation, simulation, and autonomous validation.",
                        color = AresTextSecondary,
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
            state.protectedError?.let { error -> item { ProtectedProjectIdentityCard(error) } }
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
                                "Only .ares/project.json will change. Existing valid content is checkpointed under .ares/history/project before replacement.",
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
                                ) { Text(if (state.currentDocument == null) "Create reviewed identity" else "Save reviewed changes") }
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
                    ) { Text("Review structured diff") }
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
                "Consumed by: Robot Studio, Drivebase Builder, Auto Builder, code generation, simulators, and field-boundary validation.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            Text(
                "This file describes identity and geometry. It does not contain tuning, device IDs, secrets, or proof of physical validation.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ProtectedProjectIdentityCard(error: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresError.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, AresError),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Error, contentDescription = null, tint = AresError)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Protected project file · no write allowed", color = AresTextPrimary, fontWeight = FontWeight.Bold)
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
                enabled = state.currentDocument == null && state.protectedError == null,
                help = "Starts with a letter; letters, numbers, dot, underscore, and dash only.",
            )
            ReadOnlyIdentityRow("League", state.workspaceLeague.name)
            ReadOnlyIdentityRow(
                "Coordinate convention",
                if (state.workspaceLeague == com.ares.analytics.shared.League.FTC) {
                    "CENTER_ORIGIN_CCW"
                } else {
                    "BLUE_CORNER_ORIGIN_CCW"
                },
            )

            HorizontalDivider(color = AresBorder)
            Text("Measured robot footprint", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Measure bumper-to-bumper length and width. Do not copy wheelbase or track width; those are different drivebase dimensions.",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            GeometryRow(
                firstLabel = "Robot length (m)",
                firstValue = state.draft.robotLengthMeters,
                firstError = state.fieldErrors[ProjectIdentityField.ROBOT_LENGTH],
                onFirst = { onUpdate(ProjectIdentityField.ROBOT_LENGTH, it) },
                secondLabel = "Robot width (m)",
                secondValue = state.draft.robotWidthMeters,
                secondError = state.fieldErrors[ProjectIdentityField.ROBOT_WIDTH],
                onSecond = { onUpdate(ProjectIdentityField.ROBOT_WIDTH, it) },
                enabled = state.protectedError == null,
            )

            HorizontalDivider(color = AresBorder)
            Text("Field frame", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "ARES pre-fills its current FTC or FRC field preset. Verify these dimensions against the selected season before saving; they define autonomous bounds.",
                color = AresGold,
                fontSize = 12.sp,
            )
            GeometryRow(
                firstLabel = "Field length (m)",
                firstValue = state.draft.fieldLengthMeters,
                firstError = state.fieldErrors[ProjectIdentityField.FIELD_LENGTH],
                onFirst = { onUpdate(ProjectIdentityField.FIELD_LENGTH, it) },
                secondLabel = "Field width (m)",
                secondValue = state.draft.fieldWidthMeters,
                secondError = state.fieldErrors[ProjectIdentityField.FIELD_WIDTH],
                onSecond = { onUpdate(ProjectIdentityField.FIELD_WIDTH, it) },
                enabled = state.protectedError == null,
            )
        }
    }
}

@Composable
private fun GeometryRow(
    firstLabel: String,
    firstValue: String,
    firstError: String?,
    onFirst: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    secondError: String?,
    onSecond: (String) -> Unit,
    enabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IdentityField(firstLabel, firstValue, onFirst, firstError, enabled, "Positive decimal meters.", Modifier.weight(1f))
        IdentityField(secondLabel, secondValue, onSecond, secondError, enabled, "Positive decimal meters.", Modifier.weight(1f))
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
