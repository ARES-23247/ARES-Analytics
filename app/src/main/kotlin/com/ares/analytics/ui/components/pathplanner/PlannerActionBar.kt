package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerActionBar(
    mode: String = "Path",
    pathName: String,
    availablePaths: List<String>,
    saveStatus: String,
    isPlaying: Boolean,
    playbackTime: Double,
    estimatedDuration: Double,
    onPathNameChange: (String) -> Unit,
    onPathSelected: (String) -> Unit,
    onCreateNewPath: () -> Unit,
    onSavePath: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Double) -> Unit,
    onStopPlayback: () -> Unit,
    onBrowseClicked: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$mode Name", fontSize = 11.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = pathName,
                onValueChange = onPathNameChange,
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availablePaths.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p) },
                        onClick = {
                            onPathSelected(p)
                            expanded = false
                        }
                    )
                }
            }
        }
        Button(
            onClick = onBrowseClicked,
            colors = ButtonDefaults.buttonColors(containerColor = AresSurfaceElevated, contentColor = AresTextPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Browse visually...")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCreateNewPath,
                colors = ButtonDefaults.buttonColors(containerColor = AresSurface, contentColor = AresTextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder),
                modifier = Modifier.weight(1f)
            ) {
                Text("New $mode", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onSavePath,
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                modifier = Modifier.weight(1f)
            ) {
                Text("Save $mode", color = AresBackground, fontWeight = FontWeight.Bold)
            }
        }
        if (saveStatus.isNotEmpty()) {
            Text(
                text = saveStatus,
                color = if (saveStatus.contains("failed") || saveStatus.contains("No")) AresError else AresGreen,
                fontSize = 11.sp
            )
        }
        HorizontalDivider(color = AresBorder, modifier = Modifier.padding(vertical = 8.dp))

        // Playback Controls
        Text("Playback", fontSize = 11.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTogglePlayback,
                colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) AresAmber else AresGreen),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isPlaying) "Pause" else "Play", color = AresBackground, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onStopPlayback,
                colors = ButtonDefaults.buttonColors(containerColor = AresSurface, contentColor = AresTextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder),
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop", fontWeight = FontWeight.Bold)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(playbackTime * 100.0).roundToInt() / 100.0}s", color = AresTextPrimary, fontSize = 12.sp)
            Text("${(estimatedDuration * 100.0).roundToInt() / 100.0}s", color = AresTextSecondary, fontSize = 12.sp)
        }
        Slider(
            value = playbackTime.toFloat(),
            onValueChange = { onSeekPlayback(it.toDouble()) },
            valueRange = 0f..maxOf(estimatedDuration.toFloat(), 0.01f),
            colors = SliderDefaults.colors(
                thumbColor = AresCyan,
                activeTrackColor = AresCyan,
                inactiveTrackColor = AresSurfaceElevated
            ),
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider(color = AresBorder, modifier = Modifier.padding(vertical = 8.dp))
    }
}
