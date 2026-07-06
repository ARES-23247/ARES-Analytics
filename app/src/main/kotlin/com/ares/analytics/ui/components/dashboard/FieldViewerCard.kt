package com.ares.analytics.ui.components.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.League
import com.ares.analytics.ui.components.pathplanner.FieldCanvas
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.FieldViewerViewModel
import com.ares.analytics.viewmodel.FieldViewerIntent

@Composable
fun FieldViewerCard(
    nt4ClientService: Nt4ClientService,
    league: League,
    projectPath: String? = null,
    properties: Map<String, String> = emptyMap(),
    onPropertiesChanged: (Map<String, String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(nt4ClientService) { FieldViewerViewModel(nt4ClientService, scope) }
    val state by viewModel.state.collectAsState()

    val estimatedPose = if (state.ekfX != null && state.ekfY != null && state.ekfHeading != null) {
        Waypoint(state.ekfX!!, state.ekfY!!, state.ekfHeading!!)
    } else null

    val activeVisionPoses = remember(state.visionPoses.size, state.visionX, state.visionY, state.visionHeading) {
        val list = mutableListOf<Waypoint>()
        val maxIndex = state.visionPoses.keys.maxOrNull() ?: -1
        for (i in 0..maxIndex step 3) {
            val vx = state.visionPoses[i]
            val vy = state.visionPoses[i + 1]
            val vh = state.visionPoses[i + 2]
            if (vx != null && vy != null && vh != null) {
                list.add(Waypoint(vx, vy, vh))
            }
        }
        if (list.isEmpty() && state.visionX != null && state.visionY != null && state.visionHeading != null) {
            list.add(Waypoint(state.visionX!!, state.visionY!!, state.visionHeading!!))
        }
        list
    }

    LaunchedEffect(projectPath) {
        viewModel.onIntent(FieldViewerIntent.FetchAvailablePaths(projectPath, league))
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = AresSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "Field 2D Live Tracker",
                    color = AresTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    if (state.isConnected) "Connected" else "Offline",
                    color = if (state.isConnected) AresGreen else AresTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true }
                        ) {
                            Text(
                                state.selectedPathName ?: "No Path",
                                color = AresTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(AresBackground)
                        ) {
                            DropdownMenuItem(onClick = {
                                viewModel.onIntent(FieldViewerIntent.SelectPath(null, projectPath, league))
                                menuExpanded = false
                            }) {
                                Text("None", color = AresTextPrimary)
                            }
                            state.availablePaths.forEach { pathName ->
                                DropdownMenuItem(onClick = {
                                    viewModel.onIntent(FieldViewerIntent.SelectPath(pathName, projectPath, league))
                                    menuExpanded = false
                                }) {
                                    Text(pathName, color = AresTextPrimary)
                                }
                            }
                        }
                    }
                
                    val currentRotation = properties["rotation"]?.toFloatOrNull() ?: 0f
                    IconButton(
                        onClick = {
                            val nextRot = (currentRotation + 90f) % 360f
                            onPropertiesChanged(properties + ("rotation" to nextRot.toString()))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate",
                            tint = AresTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    val showTracer = properties["show_tracer"]?.toBoolean() ?: false
                    IconButton(
                        onClick = { onPropertiesChanged(properties + ("show_tracer" to (!showTracer).toString())) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Toggle Tracer",
                            tint = if (showTracer) AresCyan else AresTextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.onIntent(FieldViewerIntent.ClearTrace) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Trace",
                            tint = AresTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = AresBorder)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val tracerEnabled = properties["show_tracer"]?.toBoolean() == true
                FieldCanvas(
                    league = league,
                    waypoints = state.selectedPathWaypoints,
                    actualPath = if (tracerEnabled) state.poseHistory else listOfNotNull(state.poseHistory.lastOrNull() ?: if (state.robotX != 0.0 || state.robotY != 0.0) Waypoint(state.robotX, state.robotY, state.robotHeading) else null),
                    onWaypointsChanged = {},
                    projectPath = projectPath,
                    estimatedPose = estimatedPose,
                    visionPoses = activeVisionPoses,
                    gamePieces = state.liveGamePieces.values.toList(),
                    showPathControls = false,
                    showObstacleControls = false,
                    showToolbar = false,
                    initialViewRotation = properties["rotation"]?.toFloatOrNull() ?: 0f,
                    onViewRotationChanged = { newRot -> onPropertiesChanged(properties + ("rotation" to newRot.toString())) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
