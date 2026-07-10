package com.ares.analytics.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.ares.analytics.service.AuthState
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.ProfileIntent
import com.ares.analytics.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    config: WorkspaceConfig,
    onConfigChanged: (WorkspaceConfig) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(config) {
        viewModel.onIntent(ProfileIntent.LoadConfig(config))
    }

    // Optional credential overrides
    var googleClientId by remember(state.googleClientId) { mutableStateOf(state.googleClientId) }
    var firebaseApiKey by remember(state.firebaseApiKey) { mutableStateOf(state.firebaseApiKey) }
    var googleClientSecret by remember(state.googleClientSecret) { mutableStateOf(state.googleClientSecret) }
    var showAdvanced by remember { mutableStateOf(false) }

    // Match integration overrides
    var eventCode by remember(state.eventCode) { mutableStateOf(state.eventCode) }
    var toaApiKey by remember(state.toaApiKey) { mutableStateOf(state.toaApiKey) }
    var tbaApiKey by remember(state.tbaApiKey) { mutableStateOf(state.tbaApiKey) }

    // AI Diagnostics overrides
    var aiMode by remember(state.aiMode) { mutableStateOf(state.aiMode) }
    var geminiApiKey by remember(state.geminiApiKey) { mutableStateOf(state.geminiApiKey) }
    var vertexServiceAccountPath by remember(state.vertexServiceAccountPath) { mutableStateOf(state.vertexServiceAccountPath) }
    var vertexProjectId by remember(state.vertexProjectId) { mutableStateOf(state.vertexProjectId) }
    var vertexLocation by remember(state.vertexLocation) { mutableStateOf(state.vertexLocation) }

    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(AresSurface).border(1.dp, AresBorder, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Profile & Cloud Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
        Text("Note: GCS raw file uploads are disabled. Telemetry Parquet database blobs sync directly via Google Drive.", color = AresTextSecondary, fontSize = 11.sp)
        HorizontalDivider(color = AresBorder)

        when (val auth = state.authState) {
            is AuthState.Unauthenticated -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sign in with your Google account to enable real-time Google Drive synchronization.", color = AresTextSecondary, fontSize = 12.sp)

                    Button(
                        onClick = {
                            val updatedConfig = config.copy(
                                googleClientId = googleClientId.takeIf { it.isNotBlank() },
                                firebaseApiKey = firebaseApiKey.takeIf { it.isNotBlank() },
                                googleClientSecret = googleClientSecret.takeIf { it.isNotBlank() }
                            )
                            onConfigChanged(updatedConfig)
                            viewModel.onIntent(ProfileIntent.GoogleSignIn(googleClientId))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Google Sign-In", color = AresBackground, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AresTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Developer Settings (Advanced)", color = AresTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (showAdvanced) {
                        OutlinedTextField(
                            value = googleClientId,
                            onValueChange = { googleClientId = it },
                            label = { Text("Google Client ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )

                        OutlinedTextField(
                            value = firebaseApiKey,
                            onValueChange = { firebaseApiKey = it },
                            label = { Text("Firebase API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )

                        OutlinedTextField(
                            value = googleClientSecret,
                            onValueChange = { googleClientSecret = it },
                            label = { Text("Google Client Secret") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )
                    }
                }
            }
            is AuthState.Authenticating -> {
                CircularProgressIndicator(color = AresCyan)
                Text("Verifying system browser OAuth...", color = AresTextSecondary, fontSize = 12.sp)
            }
            is AuthState.Authenticated -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Signed in as ${auth.displayName}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                    Text("Email: ${auth.email}", fontSize = 12.sp, color = AresTextSecondary)
                    Text("UID: ${auth.uid}", fontSize = 12.sp, color = AresTextSecondary)

                    if (auth.githubToken != null) {
                        Text("GitHub Account: Linked ✅", fontSize = 12.sp, color = AresGreen)
                    } else {
                        Button(
                            onClick = { viewModel.onIntent(ProfileIntent.LinkGitHub("mock-github-client-id")) },
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                        ) {
                            Text("Link GitHub Account", color = AresBackground, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Button(
                            onClick = {
                                viewModel.onIntent(ProfileIntent.PerformDeltaSync(auth.firebaseToken))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                        ) {
                            Text("Sync with Google Drive", color = AresBackground, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.onIntent(ProfileIntent.SignOut) },
                            colors = ButtonDefaults.buttonColors(containerColor = AresBorder)
                        ) {
                            Text("Sign Out", color = AresTextPrimary)
                        }
                    }

                    if (state.syncStatus.isNotEmpty()) {
                        Text(state.syncStatus, color = if (state.syncStatus.contains("failed")) AresError else AresGreen, fontSize = 12.sp)
                        LaunchedEffect(state.syncStatus) {
                            if (state.syncStatus.contains("successful") || state.syncStatus.contains("failed")) {
                                kotlinx.coroutines.delay(3000)
                                viewModel.onIntent(ProfileIntent.ClearSyncStatus)
                            }
                        }
                    }
                }
            }
            is AuthState.Error -> {
                Text("OAuth error: ${auth.message}", color = AresError)
                Button(
                    onClick = { viewModel.onIntent(ProfileIntent.SignOut) },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                ) {
                    Text("Reset Sign-In State", color = AresBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = AresBorder)
        Text("Event Integration Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary)

        OutlinedTextField(
            value = eventCode,
            onValueChange = { eventCode = it },
            label = { Text("Event Code / Key (e.g. 2026cmp or USNYTUT)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
        )

        if (config.league == League.FTC) {
            OutlinedTextField(
                value = toaApiKey,
                onValueChange = { toaApiKey = it },
                label = { Text("The Orange Alliance (TOA) API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
        } else {
            OutlinedTextField(
                value = tbaApiKey,
                onValueChange = { tbaApiKey = it },
                label = { Text("The Blue Alliance (TBA) API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = AresBorder)
        Text("AI Diagnostics Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { aiMode = "STUDIO" }) {
                RadioButton(selected = aiMode == "STUDIO", onClick = { aiMode = "STUDIO" }, colors = RadioButtonDefaults.colors(selectedColor = AresCyan))
                Spacer(Modifier.width(4.dp))
                Text("Google AI Studio (API Key)", color = AresTextPrimary, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { aiMode = "VERTEX" }) {
                RadioButton(selected = aiMode == "VERTEX", onClick = { aiMode = "VERTEX" }, colors = RadioButtonDefaults.colors(selectedColor = AresCyan))
                Spacer(Modifier.width(4.dp))
                Text("GCP Vertex AI (Service Account)", color = AresTextPrimary, fontSize = 13.sp)
            }
        }

        if (aiMode == "STUDIO") {
            OutlinedTextField(
                value = geminiApiKey,
                onValueChange = { geminiApiKey = it },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
        } else {
            OutlinedTextField(
                value = vertexServiceAccountPath,
                onValueChange = { vertexServiceAccountPath = it },
                label = { Text("GCP Service Account JSON File Path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
            OutlinedTextField(
                value = vertexProjectId,
                onValueChange = { vertexProjectId = it },
                label = { Text("GCP Project ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
            OutlinedTextField(
                value = vertexLocation,
                onValueChange = { vertexLocation = it },
                label = { Text("GCP Region (e.g. us-central1)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
        }

        Button(
            onClick = {
                viewModel.onIntent(
                    ProfileIntent.UpdateEventSettings(
                        googleClientId = googleClientId,
                        firebaseApiKey = firebaseApiKey,
                        googleClientSecret = googleClientSecret,
                        eventCode = eventCode,
                        toaApiKey = toaApiKey,
                        tbaApiKey = tbaApiKey,
                        aiMode = aiMode,
                        geminiApiKey = geminiApiKey,
                        vertexServiceAccountPath = vertexServiceAccountPath,
                        vertexProjectId = vertexProjectId,
                        vertexLocation = vertexLocation,
                        onConfigChanged = onConfigChanged
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("Save Settings", color = AresBackground, fontWeight = FontWeight.Bold)
        }
    }
}
