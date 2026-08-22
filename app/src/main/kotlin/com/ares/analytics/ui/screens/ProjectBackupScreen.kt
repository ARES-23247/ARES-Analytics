package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.service.versioncontrol.GitHubConnectionState
import com.ares.analytics.service.versioncontrol.ProjectBackupPlan
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.viewmodel.ProjectBackupIntent
import com.ares.analytics.viewmodel.ProjectBackupViewModel
import java.io.File

/** Plain-language, review-first local history and private GitHub backup workflow. */
@Composable
fun ProjectBackupScreen(
    viewModel: ProjectBackupViewModel,
    projectPath: String,
    robotName: String,
) {
    val state by viewModel.state.collectAsState()
    var authorName by remember { mutableStateOf("") }
    var authorEmail by remember { mutableStateOf("") }
    var versionMessage by remember { mutableStateOf("Save robot design") }
    var repositoryName by remember(robotName) { mutableStateOf(safeRepositoryName(robotName)) }

    LaunchedEffect(projectPath) { viewModel.onIntent(ProjectBackupIntent.Load(projectPath)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Project Backup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Save named versions of this robot on the computer, then optionally back them up to a private GitHub repository.",
                    color = AresTextSecondary,
                )
            }
            OutlinedButton(
                onClick = { viewModel.onIntent(ProjectBackupIntent.Refresh) },
                enabled = !state.isBusy,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }
        Text("PROJECT  •  $projectPath", color = AresTextSecondary, style = MaterialTheme.typography.labelSmall)

        state.error?.let { StatusCard(it, AresError) }
        state.notice?.let { StatusCard(it, AresGreen) }
        if (state.isBusy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                Text("Working…", color = AresTextSecondary)
            }
        }

        val plan = state.plan
        if (plan != null) {
            IdentityFields(authorName, { authorName = it }, authorEmail, { authorEmail = it })
        }
        if (plan != null && !plan.initialized) {
            StepCard("1", "Start local version history", Icons.Default.History) {
                Text(
                    "ARES will track this project inside its current folder. Nothing is uploaded, and you do not need to install Git.",
                    color = AresTextSecondary,
                )
                Button(
                    onClick = {
                        viewModel.onIntent(ProjectBackupIntent.StartLocalHistory(authorName, authorEmail))
                    },
                    enabled = !state.isBusy,
                ) { Text("Start local history") }
            }
        } else if (plan != null) {
            LocalVersionStep(plan, versionMessage, { versionMessage = it }, authorName, authorEmail, state.isBusy, viewModel)
            GitHubBackupStep(
                plan = plan,
                connection = state.githubState,
                repositoryName = repositoryName,
                onRepositoryNameChanged = { repositoryName = it },
                busy = state.isBusy,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun IdentityFields(
    name: String,
    onNameChanged: (String) -> Unit,
    email: String,
    onEmailChanged: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, AresBorder)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Who is saving this version?", fontWeight = FontWeight.Bold)
            Text("This name appears in project history so teammates know who made a change.", color = AresTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, onNameChanged, label = { Text("Your name") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(email, onEmailChanged, label = { Text("Email") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LocalVersionStep(
    plan: ProjectBackupPlan,
    message: String,
    onMessageChanged: (String) -> Unit,
    authorName: String,
    authorEmail: String,
    busy: Boolean,
    viewModel: ProjectBackupViewModel,
) {
    StepCard("2", "Review and save a local version", Icons.Default.CheckCircle) {
        Text(
            if (plan.changes.isEmpty()) "Everything is saved. Make a change in Robot Studio, then refresh this page."
            else "Review every changed file before saving. ARES checks the contents again when you press Save.",
            color = AresTextSecondary,
        )
        if (plan.blockedSensitivePaths.isNotEmpty()) {
            Text(
                "Blocked private files: ${plan.blockedSensitivePaths.joinToString()}. Remove or ignore these before saving.",
                color = AresError,
            )
        }
        plan.changes.take(30).forEach { change ->
            Text("${change.kind.name.lowercase().replaceFirstChar(Char::uppercase)}  •  ${change.path}")
        }
        if (plan.changes.size > 30) Text("…and ${plan.changes.size - 30} more files", color = AresTextSecondary)
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChanged,
            label = { Text("What changed?") },
            supportingText = { Text("Example: Add intake motor and safe current limit") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                viewModel.onIntent(
                    ProjectBackupIntent.SaveVersion(
                        confirmationToken = requireNotNull(plan.confirmationToken),
                        message = message,
                        authorName = authorName,
                        authorEmail = authorEmail,
                    ),
                )
            },
            enabled = !busy && plan.canCommit,
        ) { Text("Save this version") }
    }
}

@Composable
private fun GitHubBackupStep(
    plan: ProjectBackupPlan,
    connection: GitHubConnectionState,
    repositoryName: String,
    onRepositoryNameChanged: (String) -> Unit,
    busy: Boolean,
    viewModel: ProjectBackupViewModel,
) {
    StepCard("3", "Optional private GitHub backup", Icons.Default.CloudUpload) {
        Text(
            "GitHub stores another copy outside this computer. ARES creates a private repository and never puts your access token in the robot project.",
            color = AresTextSecondary,
        )
        when (connection) {
            GitHubConnectionState.Disconnected -> Button(
                onClick = { viewModel.onIntent(ProjectBackupIntent.SignInToGitHub) },
                enabled = !busy,
            ) { Text("Sign in with GitHub") }
            is GitHubConnectionState.Unavailable -> Text(connection.message, color = AresTextSecondary)
            is GitHubConnectionState.AwaitingUser -> {
                Text("Enter code ${connection.userCode} in the GitHub page that ARES opened.", fontWeight = FontWeight.Bold)
                Text("ARES is waiting for approval. The code expires automatically.", color = AresTextSecondary)
            }
            is GitHubConnectionState.Error -> {
                Text(connection.message, color = AresError)
                OutlinedButton(onClick = { viewModel.onIntent(ProjectBackupIntent.SignInToGitHub) }, enabled = !busy) {
                    Text("Try GitHub sign-in again")
                }
            }
            is GitHubConnectionState.Connected -> {
                Text("Signed in as ${connection.login}", color = AresGreen, fontWeight = FontWeight.Bold)
                if (plan.remoteUrl == null) {
                    OutlinedTextField(
                        repositoryName,
                        onRepositoryNameChanged,
                        label = { Text("Private repository name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.onIntent(ProjectBackupIntent.CreatePrivateGitHubBackup(repositoryName)) },
                        enabled = !busy && plan.lastCommit != null && plan.changes.isEmpty(),
                    ) { Text("Create private backup") }
                    if (plan.lastCommit == null || plan.changes.isNotEmpty()) {
                        Text("Save a clean local version before creating the online backup.", color = AresTextSecondary)
                    }
                } else {
                    Text("Backup: ${plan.remoteUrl}", color = AresTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.onIntent(ProjectBackupIntent.SyncGitHubBackup) },
                            enabled = !busy && plan.changes.isEmpty(),
                        ) { Text("Sync backup now") }
                        OutlinedButton(onClick = { viewModel.onIntent(ProjectBackupIntent.DisconnectGitHub) }, enabled = !busy) {
                            Text("Disconnect GitHub")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(number: String, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, AresBorder)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = AresCyan)
                Text("$number. $title", fontWeight = FontWeight.Bold, color = AresTextPrimary)
            }
            HorizontalDivider(color = AresBorder)
            content()
        }
    }
}

@Composable
private fun StatusCard(message: String, color: androidx.compose.ui.graphics.Color) {
    Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), border = BorderStroke(1.dp, color)) {
        Text(message, color = color, modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
}

private fun safeRepositoryName(robotName: String): String = robotName.trim()
    .lowercase()
    .replace(Regex("[^a-z0-9._-]+"), "-")
    .trim('-', '.')
    .ifBlank { "ares-robot" }
    .take(100)
