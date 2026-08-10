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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
                onStopPlayback = { onIntent(PathPlannerIntent.StopPlayback) },
                onBrowseClicked = { onIntent(PathPlannerIntent.ToggleBrowser) }
            )
        }

        HorizontalDivider(color = AresBorder)

        // Command List
        val rootNode = state.currentAutoCommands.firstOrNull()
        val commandsArray = rootNode?.data?.get("commands") as? JsonArray ?: JsonArray(emptyList())

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Column(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header Row: Type badge + Reorder/Delete Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = type.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AresCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { onIntent(PathPlannerIntent.MoveAutoCommand(index, -1, projectPath, league)) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = AresTextSecondary)
                                        }
                                    }
                                    if (index < commandsArray.size - 1) {
                                        IconButton(
                                            onClick = { onIntent(PathPlannerIntent.MoveAutoCommand(index, 1, projectPath, league)) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = AresTextSecondary)
                                        }
                                    }
                                    IconButton(
                                        onClick = { onIntent(PathPlannerIntent.RemoveAutoCommand(index, projectPath, league)) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Command", tint = AresError)
                                    }
                                }
                            }

                            // Body Inputs
                            when (type) {
                                "path" -> {
                                    val pathName = dataObj?.get("pathName")?.jsonPrimitive?.content ?: ""
                                    var expanded by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.fillMaxWidth()) {
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
                                    val isIndicator1 = fullName.startsWith("SetIndicatorColor_") && fullName.split("_").size == 2 || fullName == "SetIndicatorColor"
                                    val isIndicator2 = fullName.startsWith("SetSecondIndicatorColor_") || fullName == "SetSecondIndicatorColor"
                                    val isIndicator3 = fullName.startsWith("SetThirdIndicatorColor_") || fullName == "SetThirdIndicatorColor"
                                    val isIndicator4 = fullName.startsWith("SetFourthIndicatorColor_") || fullName == "SetFourthIndicatorColor"
                                    val isCustomIndicator = fullName.startsWith("SetIndicatorColor_") && fullName.split("_").size == 3
                                    val isIndicator = isIndicator1 || isIndicator2 || isIndicator3 || isIndicator4 || isCustomIndicator

                                    val prefix = when {
                                        isIndicator2 -> "SetSecondIndicatorColor"
                                        isIndicator3 -> "SetThirdIndicatorColor"
                                        isIndicator4 -> "SetFourthIndicatorColor"
                                        isCustomIndicator -> "SetIndicatorColor_${fullName.split("_")[1]}"
                                        else -> "SetIndicatorColor"
                                    }

                                    val actionDisplayLabel = when {
                                        isIndicator1 -> "Indicator Light 1"
                                        isIndicator2 -> "Indicator Light 2"
                                        isIndicator3 -> "Indicator Light 3"
                                        isIndicator4 -> "Indicator Light 4"
                                        isCustomIndicator -> "Indicator Light (${fullName.split("_")[1]})"
                                        else -> fullName
                                    }

                                    var expandedAction by remember { mutableStateOf(false) }
                                    val defaultActions = listOf(
                                        "Indicator Light 1",
                                        "Indicator Light 2",
                                        "Indicator Light 3",
                                        "Indicator Light 4",
                                        "Intake",
                                        "Outtake",
                                        "Shoot",
                                        "Score",
                                        "Climb",
                                        "Stop"
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = actionDisplayLabel,
                                                onValueChange = { newText ->
                                                    val newNode = AutoCommandNode("named", buildJsonObject { put("name", newText) })
                                                    onIntent(PathPlannerIntent.UpdateAutoCommand(index, newNode, projectPath, league))
                                                },
                                                label = { Text("Action / Target") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
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
                                                            val finalName = when (a) {
                                                                "Indicator Light 1" -> "SetIndicatorColor_OFF"
                                                                "Indicator Light 2" -> "SetSecondIndicatorColor_OFF"
                                                                "Indicator Light 3" -> "SetThirdIndicatorColor_OFF"
                                                                "Indicator Light 4" -> "SetFourthIndicatorColor_OFF"
                                                                else -> a
                                                            }
                                                            val newNode = AutoCommandNode("named", buildJsonObject { put("name", finalName) })
                                                            onIntent(PathPlannerIntent.UpdateAutoCommand(index, newNode, projectPath, league))
                                                            expandedAction = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        if (isIndicator) {
                                            val currentColor = fullName.substringAfterLast("_", "OFF")
                                            val colors = listOf("OFF", "RAINBOW", "RED", "ORANGE", "YELLOW", "GREEN", "CYAN", "BLUE", "PURPLE", "VIOLET", "WHITE")
                                            var expandedColor by remember { mutableStateOf(false) }

                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                OutlinedTextField(
                                                    value = currentColor,
                                                    onValueChange = { },
                                                    readOnly = true,
                                                    label = { Text("Light Color") },
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
                                                                val newNode = AutoCommandNode("named", buildJsonObject { put("name", "${prefix}_$c") })
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
                    }
                }
            }
        }

        HorizontalDivider(color = AresBorder)

        // High-Contrast Legible Add Command Button
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { expandedAddCommand = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Command", color = Color.Black, fontWeight = FontWeight.Bold)
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
                DropdownMenuItem(
                    text = { Text("Indicator Light 1") },
                    onClick = {
                        val node = AutoCommandNode("named", buildJsonObject { put("name", "SetIndicatorColor_GREEN") })
                        onIntent(PathPlannerIntent.AddAutoCommand(node, projectPath, league))
                        expandedAddCommand = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Indicator Light 2") },
                    onClick = {
                        val node = AutoCommandNode("named", buildJsonObject { put("name", "SetSecondIndicatorColor_BLUE") })
                        onIntent(PathPlannerIntent.AddAutoCommand(node, projectPath, league))
                        expandedAddCommand = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Indicator Light 3") },
                    onClick = {
                        val node = AutoCommandNode("named", buildJsonObject { put("name", "SetThirdIndicatorColor_RED") })
                        onIntent(PathPlannerIntent.AddAutoCommand(node, projectPath, league))
                        expandedAddCommand = false
                    }
                )
            }
        }
    }
}
