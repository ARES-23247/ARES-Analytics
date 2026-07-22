package com.ares.analytics.ui.components.pathplanner.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.PathPlannerCommand
import com.ares.analytics.shared.PathPlannerEventMarker
import com.ares.analytics.ui.theme.*

private data class MarkerParseResult(val action: String, val bVal: Boolean? = null, val dVal: Double? = null, val sVal: String? = null)

@Composable
fun EventMarkerCard(
    idx: Int,
    marker: PathPlannerEventMarker,
    maxPos: Double,
    onChanged: (PathPlannerEventMarker) -> Unit,
    onDelete: () -> Unit
) {
    var posSliderVal by remember { mutableStateOf(marker.waypointRelativePos.toFloat()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val parsed = remember(marker.name) {
        when {
            marker.name.startsWith("SetIntakeActive(") -> {
                val v = marker.name.removePrefix("SetIntakeActive(").removeSuffix(")").toBoolean()
                MarkerParseResult("Set Intake Active", bVal = v)
            }
            marker.name.startsWith("SetFlywheelActive(") -> {
                val v = marker.name.removePrefix("SetFlywheelActive(").removeSuffix(")").toBoolean()
                MarkerParseResult("Set Flywheel Active", bVal = v)
            }
            marker.name.startsWith("SetTransferActive(") -> {
                val v = marker.name.removePrefix("SetTransferActive(").removeSuffix(")").toBoolean()
                MarkerParseResult("Set Transfer Active", bVal = v)
            }
            marker.name.startsWith("SetFlywheelTargetRPM(") -> {
                val v = marker.name.removePrefix("SetFlywheelTargetRPM(").removeSuffix(")").toDoubleOrNull() ?: 0.0
                MarkerParseResult("Set Flywheel Target RPM", dVal = v)
            }
            marker.name.startsWith("SetIndicatorColor_") -> {
                val v = marker.name.removePrefix("SetIndicatorColor_")
                MarkerParseResult("Set Indicator Color", sVal = v)
            }
            else -> MarkerParseResult("Custom Command")
        }
    }
    var selectedAction by remember { mutableStateOf(parsed.action) }
    var boolValue by remember { mutableStateOf(parsed.bVal ?: true) }
    var doubleValue by remember { mutableStateOf(parsed.dVal ?: 2000.0) }
    var stringValue by remember { mutableStateOf(parsed.sVal ?: "OFF") }
    var customName by remember { mutableStateOf(if (parsed.action == "Custom Command") marker.name else "") }

    LaunchedEffect(marker.name) {
        val p = when {
            marker.name.startsWith("SetIntakeActive(") -> {
                val v = marker.name.removePrefix("SetIntakeActive(").removeSuffix(")").toBoolean()
                MarkerParseResult("Set Intake Active", bVal = v)
            }
            marker.name.startsWith("SetFlywheelActive(") -> {
                val v = marker.name.removePrefix("SetFlywheelActive(").removeSuffix(")").toBoolean()
                MarkerParseResult("Set Flywheel Active", bVal = v)
            }
            marker.name.startsWith("SetTransferActive(") -> {
                val v = marker.name.removePrefix("SetTransferActive(").removeSuffix(")").toBoolean()
                MarkerParseResult("Set Transfer Active", bVal = v)
            }
            marker.name.startsWith("SetFlywheelTargetRPM(") -> {
                val v = marker.name.removePrefix("SetFlywheelTargetRPM(").removeSuffix(")").toDoubleOrNull() ?: 0.0
                MarkerParseResult("Set Flywheel Target RPM", dVal = v)
            }
            marker.name.startsWith("SetIndicatorColor_") -> {
                val v = marker.name.removePrefix("SetIndicatorColor_")
                MarkerParseResult("Set Indicator Color", sVal = v)
            }
            else -> MarkerParseResult("Custom Command")
        }
        selectedAction = p.action
        if (p.bVal != null) boolValue = p.bVal
        if (p.dVal != null) doubleValue = p.dVal
        if (p.sVal != null) stringValue = p.sVal
        if (p.action == "Custom Command") customName = marker.name
    }

    LaunchedEffect(marker.waypointRelativePos) {
        if (kotlin.math.abs(posSliderVal - marker.waypointRelativePos.toFloat()) > 0.01f) {
            posSliderVal = marker.waypointRelativePos.toFloat()
        }
    }

    fun updateMarkerName(action: String, bVal: Boolean, dVal: Double, sVal: String, cName: String) {
        val newName = when (action) {
            "Set Intake Active" -> "SetIntakeActive($bVal)"
            "Set Flywheel Active" -> "SetFlywheelActive($bVal)"
            "Set Transfer Active" -> "SetTransferActive($bVal)"
            "Set Flywheel Target RPM" -> "SetFlywheelTargetRPM($dVal)"
            "Set Indicator Color" -> "SetIndicatorColor_$sVal"
            else -> cName
        }
        if (newName != marker.name) {
            onChanged(marker.copy(name = newName, command = PathPlannerCommand(name = newName)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AresSurfaceElevated)
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Marker #${idx + 1}: ${marker.name}", fontSize = 11.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AresError, modifier = Modifier.size(16.dp))
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AresTextPrimary),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedAction, fontSize = 13.sp)
                    Icon(imageVector = Icons.Default.ExpandMore, contentDescription = "Expand")
                }
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
            ) {
                listOf(
                    "Set Intake Active",
                    "Set Flywheel Active",
                    "Set Transfer Active",
                    "Set Flywheel Target RPM",
                    "Set Indicator Color",
                    "Custom Command"
                ).forEach { actionOption ->
                    DropdownMenuItem(
                        text = { Text(actionOption, color = AresTextPrimary) },
                        onClick = {
                            selectedAction = actionOption
                            dropdownExpanded = false
                            if (actionOption == "Custom Command" && customName.isEmpty()) {
                                customName = "custom_event"
                            }
                            updateMarkerName(actionOption, boolValue, doubleValue, stringValue, customName)
                        }
                    )
                }
            }
        }

        when (selectedAction) {
            "Set Intake Active", "Set Flywheel Active", "Set Transfer Active" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("State (Active):", fontSize = 12.sp, color = AresTextPrimary)
                    Switch(
                        checked = boolValue,
                        onCheckedChange = { checked ->
                            boolValue = checked
                            updateMarkerName(selectedAction, checked, doubleValue, stringValue, customName)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AresCyan,
                            checkedTrackColor = AresCyan.copy(alpha = 0.5f),
                            uncheckedThumbColor = AresTextSecondary,
                            uncheckedTrackColor = AresBorder
                        )
                    )
                }
            }
            "Set Flywheel Target RPM" -> {
                OutlinedTextField(
                    value = if (doubleValue == 0.0) "" else doubleValue.toString(),
                    onValueChange = { newValue ->
                        newValue.toDoubleOrNull()?.let { d ->
                            doubleValue = d
                            updateMarkerName(selectedAction, boolValue, d, stringValue, customName)
                        }
                    },
                    label = { Text("Target RPM", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                )
            }
            "Set Indicator Color" -> {
                var colorDropdownExpanded by remember { mutableStateOf(false) }
                val colors = listOf("OFF", "RED", "GREEN", "BLUE", "YELLOW", "VIOLET", "WHITE")
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stringValue,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Color", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().clickable { colorDropdownExpanded = true },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder),
                        trailingIcon = {
                            IconButton(onClick = { colorDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AresTextSecondary)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = colorDropdownExpanded,
                        onDismissRequest = { colorDropdownExpanded = false },
                        modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
                    ) {
                        colors.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, color = AresTextPrimary) },
                                onClick = {
                                    stringValue = c
                                    colorDropdownExpanded = false
                                    updateMarkerName(selectedAction, boolValue, doubleValue, stringValue, c)
                                }
                            )
                        }
                    }
                }
            }
            "Custom Command" -> {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { newValue ->
                        customName = newValue
                        updateMarkerName(selectedAction, boolValue, doubleValue, stringValue, newValue)
                    },
                    label = { Text("Event Name", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Position: ${String.format("%.2f", posSliderVal)}", fontSize = 11.sp, color = AresTextPrimary)
                Text("Range: 0.0 to ${String.format("%.1f", maxPos)}", fontSize = 11.sp, color = AresTextSecondary)
            }
            Slider(
                value = posSliderVal,
                onValueChange = { newValue ->
                    posSliderVal = newValue
                    onChanged(marker.copy(waypointRelativePos = newValue.toDouble()))
                },
                valueRange = 0f..maxPos.toFloat(),
                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan, inactiveTrackColor = AresBorder)
            )
        }
    }
}
