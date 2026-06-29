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

    // Match integration overrides
    var eventCode by remember(state.eventCode) { mutableStateOf(state.eventCode) }
    var toaApiKey by remember(state.toaApiKey) { mutableStateOf(state.toaApiKey) }
    var tbaApiKey by remember(state.tbaApiKey) { mutableStateOf(state.tbaApiKey) }

    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(AresSurface).border(1.dp, AresBorder, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Profile & Cloud Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
        HorizontalDivider(color = AresBorder)

        when (val auth = state.authState) {
            is AuthState.Unauthenticated -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Configure optional credentials for real cloud synchronization (leave blank for local dev/mock mode).", color = AresTextSecondary, fontSize = 12.sp)

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

                    Button(
                        onClick = {
                            val updatedConfig = config.copy(
                                googleClientId = googleClientId.takeIf { it.isNotBlank() },
                                firebaseApiKey = firebaseApiKey.takeIf { it.isNotBlank() }
                            )
                            onConfigChanged(updatedConfig)
                            viewModel.onIntent(ProfileIntent.GoogleSignIn(googleClientId))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Google Sign-In", color = AresBackground, fontWeight = FontWeight.Bold)
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
                            Text("Delta Sync", color = AresBackground, fontWeight = FontWeight.Bold)
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

        Spacer(Modifier.height(10.dp))
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

        Button(
            onClick = {
                viewModel.onIntent(
                    ProfileIntent.UpdateEventSettings(
                        googleClientId = googleClientId,
                        firebaseApiKey = firebaseApiKey,
                        eventCode = eventCode,
                        toaApiKey = toaApiKey,
                        tbaApiKey = tbaApiKey,
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
