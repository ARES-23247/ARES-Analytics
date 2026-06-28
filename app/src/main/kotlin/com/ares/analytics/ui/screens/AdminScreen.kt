package com.ares.analytics.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.AuthState
import com.ares.analytics.service.OAuthService
import com.ares.analytics.service.TeamApiService
import com.ares.analytics.shared.League
import com.ares.analytics.shared.RobotProfile
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(
    teamApiService: TeamApiService,
    oauthService: OAuthService,
    config: WorkspaceConfig,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val authState by oauthService.authState.collectAsState()
    
    var robotProfiles by remember { mutableStateOf<List<RobotProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val token = (authState as? AuthState.Authenticated)?.firebaseToken

    fun refreshRobots() {
        if (token == null) return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                robotProfiles = teamApiService.fetchTeamRobots(config.teamId, token)
            } catch (e: Exception) {
                errorMessage = "Failed to load robots: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(token) {
        refreshRobots()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = AresCyan)
                Text(
                    "Team Administration Panel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AresTextPrimary
                )
            }

            if (token != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { refreshRobots() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = AresTextSecondary)
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = AresBackground)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Robot", color = AresBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        HorizontalDivider(color = AresBorder)

        if (token == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Authentication Required",
                        fontWeight = FontWeight.Bold,
                        color = AresTextSecondary,
                        fontSize = 16.sp
                    )
                    Text(
                        "Please sign in with Google on the Profile screen to manage your team's robot registry in the cloud.",
                        color = AresTextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.width(360.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            return
        }

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AresCyan)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(errorMessage!!, color = AresError)
            }
        } else if (robotProfiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No robots registered for Team ${config.teamId} yet. Click 'Add Robot' to get started.",
                    color = AresTextTertiary,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(robotProfiles) { robot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AresSurfaceElevated)
                            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val badgeBg = if (robot.league == League.FTC) AresGold else AresCyan
                                Text(
                                    text = robot.league.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AresBackground,
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = robot.name,
                                    fontWeight = FontWeight.Bold,
                                    color = AresTextPrimary,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "ID: ${robot.robotId} • Season ID: ${robot.seasonId}",
                                fontSize = 11.sp,
                                color = AresTextSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    val success = teamApiService.deleteRobotProfile(config.teamId, robot.robotId, token)
                                    if (success) refreshRobots()
                                    else errorMessage = "Failed to delete robot from cloud roster."
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AresError)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var robotId by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var league by remember { mutableStateOf(League.FTC) }
        var seasonId by remember { mutableStateOf("2026") }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Register Team Robot Profile", color = AresTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This robot will be shared with all team members when they onboard the app.", color = AresTextSecondary, fontSize = 11.sp)
                    
                    OutlinedTextField(
                        value = robotId,
                        onValueChange = { robotId = it.filter { c -> c.isLetterOrDigit() || c == '-' } },
                        label = { Text("Robot Unique ID (e.g. ares-2026)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Friendly Name (e.g. ARES FTC Drivetrain)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )

                    OutlinedTextField(
                        value = seasonId,
                        onValueChange = { seasonId = it },
                        label = { Text("Season ID (e.g. 2026)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )

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

                    dialogError?.let {
                        Text(it, color = AresError, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (robotId.isEmpty() || name.isEmpty() || seasonId.isEmpty()) {
                            dialogError = "All fields are required."
                            return@Button
                        }
                        scope.launch {
                            val newRobot = RobotProfile(robotId, league, seasonId, name)
                            val success = teamApiService.addRobotProfile(config.teamId, newRobot, token)
                            if (success) {
                                showAddDialog = false
                                refreshRobots()
                            } else {
                                dialogError = "Cloud operation failed. Check your network or credentials."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                ) {
                    Text("Register Profile", color = AresBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = AresTextPrimary)
                }
            }
        )
    }
}
