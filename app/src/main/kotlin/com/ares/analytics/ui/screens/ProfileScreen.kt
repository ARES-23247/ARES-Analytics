package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun ProfileScreen(
    viewModel: ProfileViewModel,
    config: WorkspaceConfig,
    onConfigChanged: (WorkspaceConfig) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(config) {
        viewModel.onIntent(ProfileIntent.LoadConfig(config))
    }

    // Workspace Active Settings overrides
    var teamId by remember(config.teamId) { mutableStateOf(config.teamId) }
    var robotId by remember(config.robotId) { mutableStateOf(config.robotId) }
    var robotName by remember(config.robotName) { mutableStateOf(config.robotName) }
    var league by remember(config.league) { mutableStateOf(config.league) }
    var seasonId by remember(config.seasonId) { mutableStateOf(config.seasonId) }
    var robotMenuExpanded by remember { mutableStateOf(false) }
    var colorblindMode by remember(config.colorblindMode) { mutableStateOf(config.colorblindMode) }
    var highContrastMode by remember(config.highContrastMode) { mutableStateOf(config.highContrastMode) }
    var touchOptimizedMode by remember(config.touchOptimizedMode) { mutableStateOf(config.touchOptimizedMode) }

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
    var geminiModel by remember(state.geminiModel) { mutableStateOf(state.geminiModel) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var vertexServiceAccountPath by remember(state.vertexServiceAccountPath) { mutableStateOf(state.vertexServiceAccountPath) }
    var vertexProjectId by remember(state.vertexProjectId) { mutableStateOf(state.vertexProjectId) }
    var vertexLocation by remember(state.vertexLocation) { mutableStateOf(state.vertexLocation) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Profile & Settings Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AresTextPrimary)
        Text("Configure active workspace profiles, third-party integrations, and direct client-side diagnostics.", color = AresTextSecondary, fontSize = 12.sp)
        HorizontalDivider(color = AresBorder)

        // 1. Workspace Identity & Active Robot Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AresBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                    Text("Workspace Active Robot Profile", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 15.sp)
                }
                Text("Specify your active team identity or select from Google Drive synchronized robot profiles.", color = AresTextSecondary, fontSize = 11.sp)

                // Shared Roster Dropdown (if loaded)
                if (state.robotProfiles.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (robotName.isNotBlank()) "$robotName ($robotId)" else robotId,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Registered Team Robot Profile") },
                            modifier = Modifier.fillMaxWidth().clickable { robotMenuExpanded = !robotMenuExpanded },
                            trailingIcon = {
                                IconButton(onClick = { robotMenuExpanded = !robotMenuExpanded }) {
                                    Icon(imageVector = if (robotMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = AresTextSecondary)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )
                        DropdownMenu(
                            expanded = robotMenuExpanded,
                            onDismissRequest = { robotMenuExpanded = false }
                        ) {
                            state.robotProfiles.forEach { robot ->
                                DropdownMenuItem(
                                    text = { Text("${robot.name} (${robot.robotId})", color = AresTextPrimary) },
                                    onClick = {
                                        robotId = robot.robotId
                                        robotName = robot.name
                                        league = robot.league
                                        seasonId = robot.seasonId
                                        robotMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = teamId,
                        onValueChange = { teamId = it.filter { c -> c.isDigit() } },
                        label = { Text("Team ID Number") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                    OutlinedTextField(
                        value = robotId,
                        onValueChange = { robotId = it.filter { c -> c.isLetterOrDigit() || c == '-' } },
                        label = { Text("Robot ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = robotName,
                        onValueChange = { robotName = it },
                        label = { Text("Robot Friendly Name") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                    OutlinedTextField(
                        value = seasonId,
                        onValueChange = { seasonId = it },
                        label = { Text("Season ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                }

                // League selection toggle group
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    League.entries.forEach { l ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { league = l }
                                .border(1.dp, if (league == l) AresCyan else AresBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            RadioButton(selected = league == l, onClick = { league = l }, colors = RadioButtonDefaults.colors(selectedColor = AresCyan))
                            Text(l.name, color = if (league == l) AresCyan else AresTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Google Drive Cloud Sync
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AresBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                    Text("Google Drive Roster & Cloud Sync", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 15.sp)
                }

                when (val auth = state.authState) {
                    is AuthState.Unauthenticated -> {
                        Text("Connect your Google Account to automatically synchronize registered robots, configurations, and Parquet data blocks with your team.", color = AresTextSecondary, fontSize = 11.sp)
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
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                        ) {
                            Text("Google Sign-In", color = AresBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                    is AuthState.Authenticating -> {
                        CircularProgressIndicator(color = AresCyan, modifier = Modifier.size(24.dp))
                        Text("Verifying authorization flow via system browser...", color = AresTextSecondary, fontSize = 12.sp)
                    }
                    is AuthState.Authenticated -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Signed in as: ${auth.displayName}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                            Text("Email: ${auth.email}", fontSize = 11.sp, color = AresTextSecondary)
                            Text("Storage Folder: Google Drive/ARES-Analytics/", fontSize = 11.sp, color = AresCyan)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.onIntent(ProfileIntent.PerformDeltaSync(auth.firebaseToken)) },
                                colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                            ) {
                                Text("Sync Google Drive Now", color = AresBackground, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.onIntent(ProfileIntent.SignOut) },
                                colors = ButtonDefaults.buttonColors(containerColor = AresBorder)
                            ) {
                                Text("Sign Out", color = AresTextPrimary)
                            }
                        }
                    }
                    is AuthState.Error -> {
                        Text("Authorization Error: ${auth.message}", color = AresError, fontSize = 12.sp)
                        Button(onClick = { viewModel.onIntent(ProfileIntent.SignOut) }, colors = ButtonDefaults.buttonColors(containerColor = AresCyan)) {
                            Text("Retry Login Flow", color = AresBackground)
                        }
                    }
                }

                // Collapsible Advanced Google Developer Credentials
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AresTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Developer OAuth Credentials (Advanced)", color = AresTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = googleClientId,
                        onValueChange = { googleClientId = it },
                        label = { Text("Custom Google Client ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                    OutlinedTextField(
                        value = googleClientSecret,
                        onValueChange = { googleClientSecret = it },
                        label = { Text("Custom Google Client Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                    OutlinedTextField(
                        value = firebaseApiKey,
                        onValueChange = { firebaseApiKey = it },
                        label = { Text("Custom Firebase API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                }
            }
        }

        // 3. Third-Party Integrations
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AresBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.IntegrationInstructions, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                    Text("Third-Party API Integrations", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 15.sp)
                }
                Text("Match metadata and schedules can be synced from FRC or FTC event aggregators.", color = AresTextSecondary, fontSize = 11.sp)

                OutlinedTextField(
                    value = eventCode,
                    onValueChange = { eventCode = it },
                    label = { Text("Event Code / ID (e.g. USNYTUT)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                )

                if (league == League.FTC) {
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
            }
        }

        // 4. AI Diagnostics
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AresBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                    Text("AI Diagnostics Settings", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 15.sp)
                }
                Text("Choose your cloud model and credentials to query Gemini for real-time telemetry diagnostics.", color = AresTextSecondary, fontSize = 11.sp)

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

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = geminiModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("AI Model Selection") },
                        modifier = Modifier.fillMaxWidth().clickable { modelMenuExpanded = !modelMenuExpanded },
                        trailingIcon = {
                            IconButton(onClick = { modelMenuExpanded = !modelMenuExpanded }) {
                                Icon(imageVector = if (modelMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = AresTextSecondary)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        listOf("gemini-1.5-flash", "gemini-2.5-flash", "gemini-3.1-flash", "gemini-3.5-flash").forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model, color = AresTextPrimary) },
                                onClick = {
                                    geminiModel = model
                                    modelMenuExpanded = false
                                }
                            )
                        }
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
                        label = { Text("GCP Service Account JSON Key File Path") },
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
                        label = { Text("GCP Location Region") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                }
            }
        }

        // 4b. Accessibility & Usability Options
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AresBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                    Text("Accessibility & Usability Options", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 15.sp)
                }
                Text("Optimize the mission control interface for different environments and readability requirements.", color = AresTextSecondary, fontSize = 11.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Colorblind-Friendly Palette", color = AresTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Remaps success and failure states (red/green) to deuteranopia-safe cobalt blue and vermilion orange.", color = AresTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = colorblindMode,
                        onCheckedChange = { colorblindMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AresCyan, checkedTrackColor = AresCyanGlow)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enhanced High Contrast", color = AresTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Boosts contrast of secondary text, tertiary text, and borders to pass strict WCAG AAA guidelines.", color = AresTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = highContrastMode,
                        onCheckedChange = { highContrastMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AresCyan, checkedTrackColor = AresCyanGlow)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Touch Target Optimization", color = AresTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Increases minimum touch target sizes of interactive elements for field operations under high-pressure scenarios.", color = AresTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = touchOptimizedMode,
                        onCheckedChange = { touchOptimizedMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AresCyan, checkedTrackColor = AresCyanGlow)
                    )
                }
            }
        }

        // Save Button Footer
        Button(
            onClick = {
                val newConfig = config.copy(
                    teamId = teamId,
                    robotId = robotId,
                    robotName = robotName,
                    league = league,
                    seasonId = seasonId,
                    googleClientId = googleClientId.takeIf { it.isNotBlank() },
                    firebaseApiKey = firebaseApiKey.takeIf { it.isNotBlank() },
                    googleClientSecret = googleClientSecret.takeIf { it.isNotBlank() },
                    eventCode = eventCode.takeIf { it.isNotBlank() },
                    toaApiKey = toaApiKey.takeIf { it.isNotBlank() },
                    tbaApiKey = tbaApiKey.takeIf { it.isNotBlank() },
                    aiMode = aiMode.takeIf { it.isNotBlank() },
                    geminiApiKey = geminiApiKey.takeIf { it.isNotBlank() },
                    geminiModel = geminiModel.takeIf { it.isNotBlank() },
                    vertexServiceAccountPath = vertexServiceAccountPath.takeIf { it.isNotBlank() },
                    vertexProjectId = vertexProjectId.takeIf { it.isNotBlank() },
                    vertexLocation = vertexLocation.takeIf { it.isNotBlank() },
                    colorblindMode = colorblindMode,
                    highContrastMode = highContrastMode,
                    touchOptimizedMode = touchOptimizedMode
                )
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
                        geminiModel = geminiModel,
                        vertexServiceAccountPath = vertexServiceAccountPath,
                        vertexProjectId = vertexProjectId,
                        vertexLocation = vertexLocation,
                        onConfigChanged = onConfigChanged
                    )
                )
                // Trigger workspace config sync on parent main view model
                onConfigChanged(newConfig)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
            modifier = Modifier.fillMaxWidth().height(if (touchOptimizedMode) 56.dp else 48.dp)
        ) {
            Text("Save Profile & Settings", color = AresBackground, fontWeight = FontWeight.Bold, fontSize = if (touchOptimizedMode) 18.sp else 16.sp)
        }
    }
}
