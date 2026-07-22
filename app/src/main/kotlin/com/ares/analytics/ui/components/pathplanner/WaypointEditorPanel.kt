package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.*
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.PathPlannerIntent
import com.ares.analytics.viewmodel.PathPlannerState

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun WaypointEditorPanel(
    state: PathPlannerState,
    projectPath: String?,
    league: League,
    onIntent: (PathPlannerIntent) -> Unit
) {
    Surface(
        modifier = Modifier.width(360.dp).fillMaxHeight().border(1.dp, AresBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = AresSurface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PlannerToolbar(estimatedDuration = state.estimatedDuration)
            }
            item {
                PlannerActionBar(
                    pathName = state.pathName,
                    availablePaths = state.availablePaths,
                    saveStatus = state.saveStatus,
                    isPlaying = state.isPlaying,
                    playbackTime = state.playbackTime,
                    estimatedDuration = state.estimatedDuration,
                    onPathNameChange = { onIntent(PathPlannerIntent.UpdatePathName(it)) },
                    onPathSelected = { 
                        onIntent(PathPlannerIntent.UpdatePathName(it))
                        onIntent(PathPlannerIntent.LoadPath(projectPath, league))
                    },
                    onCreateNewPath = { onIntent(PathPlannerIntent.CreateNewPath()) },
                    onSavePath = { onIntent(PathPlannerIntent.SavePath(projectPath, league)) },
                    onTogglePlayback = { onIntent(PathPlannerIntent.TogglePlayback) },
                    onSeekPlayback = { onIntent(PathPlannerIntent.SeekPlayback(it)) },
                    onStopPlayback = { onIntent(PathPlannerIntent.StopPlayback) },
                    onBrowseClicked = { onIntent(PathPlannerIntent.ToggleBrowser) }
                )
            }
            
            item {
                var contextDropdownExpanded by remember { mutableStateOf(false) }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text("Background Auto Overlay", fontSize = 11.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = contextDropdownExpanded,
                        onExpandedChange = { contextDropdownExpanded = !contextDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = state.contextAutoName ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = contextDropdownExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = contextDropdownExpanded,
                            onDismissRequest = { contextDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None", color = AresTextPrimary) },
                                onClick = {
                                    contextDropdownExpanded = false
                                    onIntent(PathPlannerIntent.UpdateContextAuto(null, projectPath, league))
                                }
                            )
                            state.availableAutos.forEach { auto ->
                                DropdownMenuItem(
                                    text = { Text(auto, color = AresTextPrimary) },
                                    onClick = {
                                        contextDropdownExpanded = false
                                        onIntent(PathPlannerIntent.UpdateContextAuto(auto, projectPath, league))
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // WAYPOINTS
            item {
                CollapsibleSection(title = "Waypoints", badgeCount = state.waypoints.size) {
                    state.waypoints.forEachIndexed { idx, wp ->
                        WaypointCard(
                            idx = idx,
                            wp = wp,
                            onChanged = { updatedWp ->
                                onIntent(PathPlannerIntent.UpdateWaypoint(idx, updatedWp))
                            },
                            onDelete = {
                                onIntent(PathPlannerIntent.DeleteWaypoint(idx))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onIntent(PathPlannerIntent.OptimizePath) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AresCyan)
                    ) {
                        Text("Optimize Path", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val last = state.waypoints.lastOrNull() ?: Waypoint(0.0, 0.0)
                            onIntent(PathPlannerIntent.AddWaypoint(Waypoint(last.x + 0.3, last.y + 0.3)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                    ) {
                        Text("Add Waypoint", color = AresBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // EVENT MARKERS
            item {
                CollapsibleSection(title = "Event Markers", badgeCount = state.eventMarkers.size) {
                    val maxPos = (state.waypoints.size - 1).coerceAtLeast(0).toDouble()
                    state.eventMarkers.forEachIndexed { idx, marker ->
                        EventMarkerCard(
                            idx = idx,
                            marker = marker,
                            maxPos = maxPos,
                            onChanged = { updatedMarker ->
                                onIntent(PathPlannerIntent.UpdateEventMarker(idx, updatedMarker))
                            },
                            onDelete = {
                                onIntent(PathPlannerIntent.DeleteEventMarker(idx))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onIntent(
                                PathPlannerIntent.AddEventMarker(
                                    PathPlannerEventMarker(
                                        name = "event_${state.eventMarkers.size + 1}",
                                        waypointRelativePos = 0.0,
                                        command = PathPlannerCommand(name = "event_${state.eventMarkers.size + 1}")
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                    ) {
                        Text("Add Event Marker", color = AresBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ROTATION TARGETS REMOVED — rotation is now per-waypoint via Waypoint.rotationDeg

            // POINT TOWARDS ZONES
            item {
                CollapsibleSection(title = "Point Towards Zones", badgeCount = state.pointTowardsZones.size) {
                    val maxPos = (state.waypoints.size - 1).coerceAtLeast(0).toDouble()
                    state.pointTowardsZones.forEachIndexed { idx, zone ->
                        PointTowardsZoneCard(
                            idx = idx,
                            zone = zone,
                            maxPos = maxPos,
                            onChanged = { updatedZone ->
                                onIntent(PathPlannerIntent.UpdatePointTowardsZone(idx, updatedZone))
                            },
                            onDelete = {
                                onIntent(PathPlannerIntent.DeletePointTowardsZone(idx))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onIntent(
                                PathPlannerIntent.AddPointTowardsZone(
                                    PointTowardsZone(
                                        name = "Aim Zone ${state.pointTowardsZones.size + 1}",
                                        fieldPosition = PathPoint(2.0, 2.0),
                                        rotationOffset = 0.0,
                                        minWaypointRelativePos = 0.0,
                                        maxWaypointRelativePos = maxPos
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                    ) {
                        Text("Add Aim Zone", color = AresBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // STARTING STATE (velocity only — rotation is per-waypoint)
            item {
                val startVelStr = state.idealStartingState?.velocity?.toString() ?: "0.0"
                CollapsibleSection(title = "Start Velocity", badgeText = "${startVelStr} M/S") {
                    OutlinedTextField(
                        value = startVelStr,
                        onValueChange = { newValue ->
                            newValue.toDoubleOrNull()?.let { v ->
                                val currentState = state.idealStartingState ?: IdealStartingState()
                                onIntent(PathPlannerIntent.UpdateStartingState(currentState.copy(velocity = v)))
                            }
                        },
                        label = { Text("Starting Velocity (M/S)", fontSize = 10.sp) },
                        placeholder = { Text("0.0 = stopped", fontSize = 12.sp, color = AresTextSecondary.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                    Text(
                        "Set > 0 if chaining from a previous path that ends in motion.",
                        fontSize = 10.sp, color = AresTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // END STATE (velocity only — rotation is per-waypoint)
            item {
                val endVelStr = state.goalEndState?.velocity?.toString() ?: "0.0"
                CollapsibleSection(title = "End Velocity", badgeText = "${endVelStr} M/S") {
                    OutlinedTextField(
                        value = endVelStr,
                        onValueChange = { newValue ->
                            newValue.toDoubleOrNull()?.let { v ->
                                val currentState = state.goalEndState ?: GoalEndState()
                                onIntent(PathPlannerIntent.UpdateEndState(currentState.copy(velocity = v)))
                            }
                        },
                        label = { Text("End Velocity (M/S)", fontSize = 10.sp) },
                        placeholder = { Text("0.0 = full stop", fontSize = 12.sp, color = AresTextSecondary.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                    Text(
                        "Set > 0 to keep moving into the next path (chaining).",
                        fontSize = 10.sp, color = AresTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // GLOBAL CONSTRAINTS
            item {
                val globalVelText by remember(state.globalConstraints.maxVelocity) { mutableStateOf(String.format("%.2f", state.globalConstraints.maxVelocity)) }
                val globalAccText by remember(state.globalConstraints.maxAcceleration) { mutableStateOf(String.format("%.2f", state.globalConstraints.maxAcceleration)) }
                val globalAngVelText by remember(state.globalConstraints.maxAngularVelocity) { mutableStateOf(String.format("%.1f", state.globalConstraints.maxAngularVelocity)) }
                val globalAngAccText by remember(state.globalConstraints.maxAngularAcceleration) { mutableStateOf(String.format("%.1f", state.globalConstraints.maxAngularAcceleration)) }
                val globalVoltsText by remember(state.globalConstraints.nominalVoltage) { mutableStateOf(String.format("%.1f", state.globalConstraints.nominalVoltage)) }

                CollapsibleSection(title = "Global Constraints") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = globalVelText,
                            onValueChange = { newValue ->
                                newValue.toDoubleOrNull()?.let {
                                    onIntent(PathPlannerIntent.UpdateGlobalConstraints(state.globalConstraints.copy(maxVelocity = it)))
                                }
                            },
                            label = { Text("Max Vel (M/S)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )
                        OutlinedTextField(
                            value = globalAccText,
                            onValueChange = { newValue ->
                                newValue.toDoubleOrNull()?.let {
                                    onIntent(PathPlannerIntent.UpdateGlobalConstraints(state.globalConstraints.copy(maxAcceleration = it)))
                                }
                            },
                            label = { Text("Max Accel (M/S²)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = globalAngVelText,
                            onValueChange = { newValue ->
                                newValue.toDoubleOrNull()?.let {
                                    onIntent(PathPlannerIntent.UpdateGlobalConstraints(state.globalConstraints.copy(maxAngularVelocity = it)))
                                }
                            },
                            label = { Text("Max Ang Vel (°/s)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )
                        OutlinedTextField(
                            value = globalAngAccText,
                            onValueChange = { newValue ->
                                newValue.toDoubleOrNull()?.let {
                                    onIntent(PathPlannerIntent.UpdateGlobalConstraints(state.globalConstraints.copy(maxAngularAcceleration = it)))
                                }
                            },
                            label = { Text("Max Ang Accel (°/s²)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )
                    }

                    OutlinedTextField(
                        value = globalVoltsText,
                        onValueChange = { newValue ->
                            newValue.toDoubleOrNull()?.let {
                                onIntent(PathPlannerIntent.UpdateGlobalConstraints(state.globalConstraints.copy(nominalVoltage = it)))
                            }
                        },
                        label = { Text("Nominal Voltage (V)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                    )
                }
            }

            // CONSTRAINT ZONES
            item {
                CollapsibleSection(title = "Constraint Zones", badgeCount = state.constraintZones.size) {
                    val maxPos = (state.waypoints.size - 1).coerceAtLeast(0).toDouble()
                    state.constraintZones.forEachIndexed { idx, zone ->
                        ConstraintsZoneCard(
                            idx = idx,
                            zone = zone,
                            maxPos = maxPos,
                            onChanged = { updatedZone ->
                                onIntent(PathPlannerIntent.UpdateConstraintZone(idx, updatedZone))
                            },
                            onDelete = {
                                onIntent(PathPlannerIntent.DeleteConstraintZone(idx))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onIntent(
                                PathPlannerIntent.AddConstraintZone(
                                    ConstraintsZone(
                                        name = "Speed Limit ${state.constraintZones.size + 1}",
                                        minWaypointRelativePos = 0.0,
                                        maxWaypointRelativePos = maxPos,
                                        constraints = PathConstraints(maxVelocity = 1.5, maxAcceleration = 1.5)
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan)
                    ) {
                        Text("Add Constraint Zone", color = AresBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
