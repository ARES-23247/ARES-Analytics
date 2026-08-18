package com.ares.analytics.ui.components.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
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
import com.ares.analytics.viewmodel.project.ProjectIdentityField
import com.ares.analytics.viewmodel.project.ProjectIdentityViewModel

/**
 * Modal dialog for reviewing and editing .ares/project.json without navigating away from Robot Studio.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProjectIdentityModal(
    isOpen: Boolean,
    viewModel: ProjectIdentityViewModel,
    config: WorkspaceConfig,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    val state by viewModel.state.collectAsState()
    LaunchedEffect(config) { viewModel.load(config) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .heightIn(max = 780.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(14.dp),
            color = AresSurface,
            border = BorderStroke(1.dp, AresBorder),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AresCyan.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Project & Robot Identity", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Stored in .ares/project.json · Git-tracked robot metadata", color = AresTextSecondary, fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AresTextSecondary)
                    }
                }

                HorizontalDivider(color = AresBorder)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AresCyan, strokeWidth = 2.dp)
                            Text("Reading canonical project identity…", color = AresTextSecondary)
                        }
                    } else {
                        state.protectedError?.let { error ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AresError.copy(alpha = 0.10f)),
                                border = BorderStroke(1.dp, AresError),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = AresError)
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("Protected project file · no write allowed", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                                        Text(error, color = AresTextSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Identity form
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                            border = BorderStroke(1.dp, AresBorder),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Robot Identity & Platform", color = AresTextPrimary, fontWeight = FontWeight.Bold)

                                OutlinedTextField(
                                    value = state.draft.projectId,
                                    onValueChange = { viewModel.update(ProjectIdentityField.PROJECT_ID, it) },
                                    label = { Text("Stable Project ID") },
                                    isError = state.fieldErrors.containsKey(ProjectIdentityField.PROJECT_ID),
                                    supportingText = {
                                        Text(state.fieldErrors[ProjectIdentityField.PROJECT_ID] ?: "Unique reference ID locked after creation.")
                                    },
                                    enabled = state.currentDocument == null && state.protectedError == null,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("League", modifier = Modifier.weight(0.35f), color = AresTextSecondary)
                                    Text(state.workspaceLeague.name, modifier = Modifier.weight(0.65f), color = AresTextPrimary, fontFamily = FontFamily.Monospace)
                                }

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Coordinate frame", modifier = Modifier.weight(0.35f), color = AresTextSecondary)
                                    Text(
                                        if (state.workspaceLeague == League.FTC) "CENTER_ORIGIN_CCW" else "BLUE_CORNER_ORIGIN_CCW",
                                        modifier = Modifier.weight(0.65f),
                                        color = AresTextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }

                                HorizontalDivider(color = AresBorder)

                                Text("Measured Robot Footprint", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                                Text(
                                    "Measure bumper-to-bumper length and width in meters.",
                                    color = AresTextSecondary,
                                    fontSize = 12.sp,
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = state.draft.robotLengthMeters,
                                        onValueChange = { viewModel.update(ProjectIdentityField.ROBOT_LENGTH, it) },
                                        label = { Text("Robot Length (m)") },
                                        isError = state.fieldErrors.containsKey(ProjectIdentityField.ROBOT_LENGTH),
                                        supportingText = { Text(state.fieldErrors[ProjectIdentityField.ROBOT_LENGTH] ?: "Positive decimal meters") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    OutlinedTextField(
                                        value = state.draft.robotWidthMeters,
                                        onValueChange = { viewModel.update(ProjectIdentityField.ROBOT_WIDTH, it) },
                                        label = { Text("Robot Width (m)") },
                                        isError = state.fieldErrors.containsKey(ProjectIdentityField.ROBOT_WIDTH),
                                        supportingText = { Text(state.fieldErrors[ProjectIdentityField.ROBOT_WIDTH] ?: "Positive decimal meters") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                }

                                HorizontalDivider(color = AresBorder)

                                Text("Field Bounds", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = state.draft.fieldLengthMeters,
                                        onValueChange = { viewModel.update(ProjectIdentityField.FIELD_LENGTH, it) },
                                        label = { Text("Field Length (m)") },
                                        isError = state.fieldErrors.containsKey(ProjectIdentityField.FIELD_LENGTH),
                                        supportingText = { Text(state.fieldErrors[ProjectIdentityField.FIELD_LENGTH] ?: "e.g. 3.658 for FTC, 16.54 for FRC") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    OutlinedTextField(
                                        value = state.draft.fieldWidthMeters,
                                        onValueChange = { viewModel.update(ProjectIdentityField.FIELD_WIDTH, it) },
                                        label = { Text("Field Width (m)") },
                                        isError = state.fieldErrors.containsKey(ProjectIdentityField.FIELD_WIDTH),
                                        supportingText = { Text(state.fieldErrors[ProjectIdentityField.FIELD_WIDTH] ?: "e.g. 3.658 for FTC, 8.21 for FRC") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                }
                            }
                        }

                        // Proposal diff review
                        state.proposal?.let { proposal ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                                border = BorderStroke(1.dp, AresGold),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Structured Diff Review", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                                    proposal.changes.forEach { change ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(change.label, color = AresTextSecondary, fontSize = 12.sp)
                                            Text("${change.before} → ${change.after}", color = AresCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.applyReviewed()
                                                onDismiss()
                                            },
                                            enabled = !state.saving,
                                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                                        ) {
                                            Text(if (state.currentDocument == null) "Create reviewed identity" else "Save reviewed changes")
                                        }
                                        OutlinedButton(onClick = viewModel::cancelReview, enabled = !state.saving) {
                                            Text("Keep editing")
                                        }
                                    }
                                }
                            }
                        }

                        // Messages
                        state.message?.let { message ->
                            Surface(
                                color = (if (state.messageIsError) AresError else AresGreen).copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, if (state.messageIsError) AresError else AresGreen),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    message,
                                    color = AresTextPrimary,
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                // Footer
                HorizontalDivider(color = AresBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }

                    if (state.proposal == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::resetDraft,
                                enabled = !state.saving && !state.loading,
                            ) {
                                Text("Discard")
                            }
                            Button(
                                onClick = viewModel::review,
                                enabled = state.canReview && !state.saving && !state.loading,
                                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                            ) {
                                Text("Review diff & save")
                            }
                        }
                    }
                }
            }
        }
    }
}
