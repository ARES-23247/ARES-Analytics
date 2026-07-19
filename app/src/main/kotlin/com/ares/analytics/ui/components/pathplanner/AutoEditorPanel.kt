package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.AutoCommandNode
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.PathPlannerIntent
import com.ares.analytics.viewmodel.PathPlannerState
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEditorPanel(
    state: PathPlannerState,
    projectPath: String?,
    league: League,
    onIntent: (PathPlannerIntent) -> Unit
) {
    var expandedAddCommand by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Header
        Column(modifier = Modifier.padding(16.dp)) {
            PlannerActionBar(
                mode = "Auto",
                pathName = state.pathName,
                availablePaths = state.availableAutos,
                saveStatus = state.saveStatus,
                isPlaying = state.isPlaying,
                playbackTime = state.playbackTime,
                estimatedDuration = state.estimatedDuration,
                onPathNameChange = { onIntent(PathPlannerIntent.UpdatePathName(it)) },
                onPathSelected = {
                    onIntent(PathPlannerIntent.UpdatePathName(it))
                    onIntent(PathPlannerIntent.LoadAuto(projectPath, league))
                },
                onCreateNewPath = { onIntent(PathPlannerIntent.CreateNewAuto()) },
                onSavePath = { onIntent(PathPlannerIntent.SaveAuto(projectPath, league)) },
                onTogglePlayback = { onIntent(PathPlannerIntent.TogglePlayback) },
                onSeekPlayback = { onIntent(PathPlannerIntent.SeekPlayback(it)) },
                onStopPlayback = { onIntent(PathPlannerIntent.StopPlayback) }
            )
        }
        
        HorizontalDivider(color = AresBorder)
        
        // Command List
        val rootNode = state.currentAutoCommands.firstOrNull()
        val commandsArray = rootNode?.data?.get("commands") as? JsonArray ?: JsonArray(emptyList())

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(commandsArray) { index, element ->
                val commandObj = element as? JsonObject
                if (commandObj != null) {
                    val type = commandObj["type"]?.jsonPrimitive?.content ?: "unknown"
                    val dataObj = commandObj["data"] as? JsonObject
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                        border = BorderStroke(1.dp, AresBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(type.uppercase(), style = MaterialTheme.typography.labelSmall, color = AresCyan)
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                when (type) {
                                    "path" -> {
                                        val pathName = dataObj?.get("pathName")?.jsonPrimitive?.content ?: ""
                                        var expanded by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedTextField(
                                                value = pathName,
                                                onValueChange = { 
                                                    val newNode = AutoCommandNode("path", buildJsonObject { put("pathName", it) })
                                                    onIntent(PathPlannerIntent.UpdateAutoCommand(index, newNode, projectPath, league))
                                                },
                                                label = { Text("Run Path") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = AresCyan,
                                                    unfocusedBorderColor = AresBorder
                                                ),
                                                trailingIcon = {
                                                    IconButton(onClick = { expanded = true }) {
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AresTextSecondary)
                                                    }
                                                }
                                            )
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false },
                                                modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
                                            ) {
                                                state.availablePaths.forEach { p ->
                                                    DropdownMenuItem(
                                                        text = { Text(p, color = AresTextPrimary) },
                                                        onClick = {
                                                            val newNode = AutoCommandNode("path", buildJsonObject { put("pathName", p) })
                                                            onIntent(PathPlannerIntent.UpdateAutoCommand(index, newNode, projectPath, league))
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    "wait" -> {
                                        val waitTimeStr = dataObj?.get("waitTime")?.jsonPrimitive?.content ?: "0.0"
                                        OutlinedTextField(
                                            value = waitTimeStr,
                                            onValueChange = { 
                                                val num = it.toDoubleOrNull() ?: 0.0
                                                val newNode = AutoCommandNode("wait", buildJsonObject { put("waitTime", num) })
                                                onIntent(PathPlannerIntent.UpdateAutoCommand(index, newNode, projectPath, league))
                                            },
                                            label = { Text("Wait Time (s)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AresCyan,
                                                unfocusedBorderColor = AresBorder
                                            )
                                        )
                                    }
                                    "named" -> {
                                        val fullName = dataObj?.get("name")?.jsonPrimitive?.content ?: ""
                                        val isIndicator = fullName.startsWith("SetIndicatorColor")
                                        val baseName = if (isIndicator) "SetIndicatorColor" else fullName
                                        
                                        var expandedAction by remember { mutableStateOf(false) }
                                        val defaultActions = listOf("Intake", "Outtake", "Shoot", "Score", "Climb", "Stop", "SetIndicatorColor")
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = baseName,
                                                    onValueChange = { 
                                                        val finalName = if (it == "SetIndicatorColor") "SetIndicatorColor_OFF" else it
                                                        val newNode = AutoCommandNode("named", buildJsonObject { put("name", finalName) })
                                                        onIntent(PathPlannerIntent.UpdateAutoCommand(index, newNode, projectPath, league))
                                                    },
                                                    label = { Text("Action Name") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth().clickable { expandedAction = true },
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = AresCyan,
                                                        unfocusedBorderColor = AresBorder
                                                    ),
                                                    trailingIcon = {
                                                        IconButton(onClick = { expandedAction = true }) {
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AresTextSecondary)
                                                        }
                                                    }
                                                )
                                                DropdownMenu(
                                                    expanded = expandedAction,
                                                    onDismissRequest = { expandedAction = false },
                                                    modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
                                                ) {
                                                    defaultActions.forEach { a ->
                                                        DropdownMenuItem(
                                                            text = { Text(a, color = AresTextPrimary) },
                                                            onClick = {
                                                                val finalName = if (a == "SetIndicatorColor") "SetIndicatorColor_OFF" else a
                                                                val newNode = AutoCommandNode("named", buildJsonObject { put("name", finalName) })
                                                                onIntent(PathPlannerIntent.UpdateAutoCommand(index, newNode, projectPath, league))
                                                                expandedAction = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            if (isIndicator) {
                                                val currentColor = fullName.substringAfter("_", "OFF")
                                                val colors = listOf("OFF", "RED", "GREEN", "BLUE", "YELLOW", "VIOLET", "WHITE")
                                                var expandedColor by remember { mutableStateOf(false) }

                                                Box(modifier = Modifier.weight(1f)) {
                                                    OutlinedTextField(
                                                        value = currentColor,
                                                        onValueChange = { },
                                                        readOnly = true,
                                                        label = { Text("Color") },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth().clickable { expandedColor = true },
                                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = AresCyan,
                                                            unfocusedBorderColor = AresBorder
                                                        ),
                                                        trailingIcon = {
                                                            IconButton(onClick = { expandedColor = true }) {
                                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AresTextSecondary)
                                                            }
                                                        }
                                                    )
                                                    DropdownMenu(
                                                        expanded = expandedColor,
                                                        onDismissRequest = { expandedColor = false },
                                                        modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
                                                    ) {
                                                        colors.forEach { c ->
                                                            DropdownMenuItem(
                                                                text = { Text(c, color = AresTextPrimary) },
                                                                onClick = {
                                                                    val newNode = AutoCommandNode("named", buildJsonObject { put("name", "SetIndicatorColor_$c") })
                                                                    onIntent(PathPlannerIntent.UpdateAutoCommand(index, newNode, projectPath, league))
                                                                    expandedColor = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        Text("Data: $dataObj", style = MaterialTheme.typography.bodySmall, color = AresTextSecondary)
                                    }
                                }
                            }
                            
                            IconButton(onClick = { onIntent(PathPlannerIntent.RemoveAutoCommand(index, projectPath, league)) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Command", tint = AresError)
                            }
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(color = AresBorder)
        
        // Add Button
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { expandedAddCommand = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresBackground)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Command")
            }
            
            DropdownMenu(
                expanded = expandedAddCommand,
                onDismissRequest = { expandedAddCommand = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Run Path") },
                    onClick = {
                        val node = AutoCommandNode("path", buildJsonObject { put("pathName", "NewPath") })
                        onIntent(PathPlannerIntent.AddAutoCommand(node, projectPath, league))
                        expandedAddCommand = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Wait") },
                    onClick = {
                        val node = AutoCommandNode("wait", buildJsonObject { put("waitTime", 1.0) })
                        onIntent(PathPlannerIntent.AddAutoCommand(node, projectPath, league))
                        expandedAddCommand = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Named Action") },
                    onClick = {
                        val node = AutoCommandNode("named", buildJsonObject { put("name", "Intake") })
                        onIntent(PathPlannerIntent.AddAutoCommand(node, projectPath, league))
                        expandedAddCommand = false
                    }
                )
            }
        }
    }
}
