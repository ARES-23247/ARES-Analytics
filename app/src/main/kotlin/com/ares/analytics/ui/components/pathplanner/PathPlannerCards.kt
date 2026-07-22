package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.*
import com.ares.analytics.ui.theme.*

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun WaypointCard(
    idx: Int,
    wp: Waypoint,
    onChanged: (Waypoint) -> Unit,
    onDelete: () -> Unit
) {
    var xText by remember { mutableStateOf(String.format("%.3f", wp.x)) }
    var yText by remember { mutableStateOf(String.format("%.3f", wp.y)) }
    val headingDeg = wp.headingRad?.let { Math.toDegrees(it) }
    var headingText by remember { mutableStateOf(headingDeg?.let { String.format("%.1f", it) } ?: "") }
    val rotationDeg = wp.rotationDeg
    var rotationText by remember { mutableStateOf(rotationDeg?.let { String.format("%.1f", it) } ?: "") }
    var prevLengthText by remember { mutableStateOf(String.format("%.3f", wp.prevControlLength)) }
    var nextLengthText by remember { mutableStateOf(String.format("%.3f", wp.nextControlLength)) }

    LaunchedEffect(wp.x) {
        if (xText.toDoubleOrNull() != wp.x) xText = String.format("%.3f", wp.x)
    }
    LaunchedEffect(wp.y) {
        if (yText.toDoubleOrNull() != wp.y) yText = String.format("%.3f", wp.y)
    }
    LaunchedEffect(headingDeg) {
        when {
            headingDeg == null -> { if (headingText.isNotEmpty()) headingText = "" }
            else -> {
                val parsed = headingText.toDoubleOrNull()
                if (parsed == null || kotlin.math.abs(parsed - headingDeg) > 0.1) {
                    headingText = String.format("%.1f", headingDeg)
                }
            }
        }
    }
    LaunchedEffect(rotationDeg) {
        when {
            rotationDeg == null -> { if (rotationText.isNotEmpty()) rotationText = "" }
            else -> {
                val parsed = rotationText.toDoubleOrNull()
                if (parsed == null || kotlin.math.abs(parsed - rotationDeg) > 0.1) {
                    rotationText = String.format("%.1f", rotationDeg)
                }
            }
        }
    }
    LaunchedEffect(wp.prevControlLength) {
        val parsed = prevLengthText.toDoubleOrNull()
        if (parsed == null || kotlin.math.abs(parsed - wp.prevControlLength) > 1e-3) {
            prevLengthText = String.format("%.3f", wp.prevControlLength)
        }
    }
    LaunchedEffect(wp.nextControlLength) {
        val parsed = nextLengthText.toDoubleOrNull()
        if (parsed == null || kotlin.math.abs(parsed - wp.nextControlLength) > 1e-3) {
            nextLengthText = String.format("%.3f", wp.nextControlLength)
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
            Text("Waypoint #${idx + 1}", fontSize = 12.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AresError, modifier = Modifier.size(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = xText,
                onValueChange = { newValue ->
                    xText = newValue
                    newValue.toDoubleOrNull()?.let {
                        onChanged(wp.copy(x = it))
                    }
                },
                label = { Text("X (m)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )

            OutlinedTextField(
                value = yText,
                onValueChange = { newValue ->
                    yText = newValue
                    newValue.toDoubleOrNull()?.let {
                        onChanged(wp.copy(y = it))
                    }
                },
                label = { Text("Y (m)", fontSize = 10.sp) },
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
                value = headingText,
                onValueChange = { newValue ->
                    headingText = newValue
                    if (newValue.isBlank()) {
                        onChanged(wp.copy(headingRad = null))
                    } else {
                        newValue.toDoubleOrNull()?.let {
                            onChanged(wp.copy(headingRad = Math.toRadians(it)))
                        }
                    }
                },
                label = { Text("Heading (°)", fontSize = 10.sp) },
                placeholder = { Text("Auto", fontSize = 12.sp, color = AresTextSecondary.copy(alpha = 0.5f)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
            
            OutlinedTextField(
                value = rotationText,
                onValueChange = { newValue ->
                    rotationText = newValue
                    if (newValue.isBlank()) {
                        onChanged(wp.copy(rotationDeg = null))
                    } else {
                        newValue.toDoubleOrNull()?.let {
                            onChanged(wp.copy(rotationDeg = it))
                        }
                    }
                },
                label = { Text("Rotation (°)", fontSize = 10.sp) },
                placeholder = { Text("Auto", fontSize = 12.sp, color = AresTextSecondary.copy(alpha = 0.5f)) },
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
                value = prevLengthText,
                onValueChange = { newValue ->
                    prevLengthText = newValue
                    newValue.toDoubleOrNull()?.let {
                        onChanged(wp.copy(prevControlLength = it))
                    }
                },
                label = { Text("Previous Control Length (M)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
            
            OutlinedTextField(
                value = nextLengthText,
                onValueChange = { newValue ->
                    nextLengthText = newValue
                    newValue.toDoubleOrNull()?.let {
                        onChanged(wp.copy(nextControlLength = it))
                    }
                },
                label = { Text("Next Control Length (M)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
        }
    }
}

private data class MarkerParseResult(val action: String, val bVal: Boolean? = null, val dVal: Double? = null, val sVal: String? = null)

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun EventMarkerCard(
    idx: Int,
    marker: PathPlannerEventMarker,
    maxPos: Double,
    onChanged: (PathPlannerEventMarker) -> Unit,
    onDelete: () -> Unit
) {
    var posSliderVal by remember { mutableStateOf(marker.waypointRelativePos.toFloat()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Parse current marker.name into structured state
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

    // Helper to format name and trigger change
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
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

        // Action Type Dropdown
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
                            // Default values for new actions
                            if (actionOption == "Custom Command" && customName.isEmpty()) {
                                customName = "custom_event"
                            }
                            updateMarkerName(actionOption, boolValue, doubleValue, stringValue, customName)
                        }
                    )
                }
            }
        }

        // Dynamic Parameter Inputs
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
                                    updateMarkerName(selectedAction, boolValue, doubleValue, stringValue, customName)
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

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun RotationTargetCard(
    idx: Int,
    target: RotationTarget,
    maxPos: Double,
    onChanged: (RotationTarget) -> Unit,
    onDelete: () -> Unit
) {
    var degText by remember { mutableStateOf(String.format("%.1f", target.rotationDegrees)) }
    var posSliderVal by remember { mutableStateOf(target.waypointRelativePos.toFloat()) }

    LaunchedEffect(target.rotationDegrees) {
        val parsed = degText.toDoubleOrNull()
        if (parsed == null || kotlin.math.abs(parsed - target.rotationDegrees) > 0.1) {
            degText = String.format("%.1f", target.rotationDegrees)
        }
    }
    LaunchedEffect(target.waypointRelativePos) {
        if (kotlin.math.abs(posSliderVal - target.waypointRelativePos.toFloat()) > 0.01f) {
            posSliderVal = target.waypointRelativePos.toFloat()
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
            Text("Target #${idx + 1}", fontSize = 12.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AresError, modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = degText,
            onValueChange = { newValue ->
                degText = newValue
                newValue.toDoubleOrNull()?.let {
                    onChanged(target.copy(rotationDegrees = it))
                }
            },
            label = { Text("Rotation (Deg)", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
        )

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
                    onChanged(target.copy(waypointRelativePos = newValue.toDouble()))
                },
                valueRange = 0f..maxPos.toFloat(),
                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan, inactiveTrackColor = AresBorder)
            )
        }
    }
}

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun ConstraintsZoneCard(
    idx: Int,
    zone: ConstraintsZone,
    maxPos: Double,
    onChanged: (ConstraintsZone) -> Unit,
    onDelete: () -> Unit
) {
    var nameText by remember { mutableStateOf(zone.name) }
    var minPosSliderVal by remember { mutableStateOf(zone.minWaypointRelativePos.toFloat()) }
    var maxPosSliderVal by remember { mutableStateOf(zone.maxWaypointRelativePos.toFloat()) }
    var velText by remember { mutableStateOf(String.format("%.2f", zone.constraints.maxVelocity)) }
    var accText by remember { mutableStateOf(String.format("%.2f", zone.constraints.maxAcceleration)) }

    LaunchedEffect(zone.name) { if (nameText != zone.name) nameText = zone.name }
    LaunchedEffect(zone.minWaypointRelativePos) { if (kotlin.math.abs(minPosSliderVal - zone.minWaypointRelativePos.toFloat()) > 0.01f) minPosSliderVal = zone.minWaypointRelativePos.toFloat() }
    LaunchedEffect(zone.maxWaypointRelativePos) { if (kotlin.math.abs(maxPosSliderVal - zone.maxWaypointRelativePos.toFloat()) > 0.01f) maxPosSliderVal = zone.maxWaypointRelativePos.toFloat() }
    LaunchedEffect(zone.constraints.maxVelocity) {
        val parsed = velText.toDoubleOrNull()
        if (parsed == null || kotlin.math.abs(parsed - zone.constraints.maxVelocity) > 0.01) {
            velText = String.format("%.2f", zone.constraints.maxVelocity)
        }
    }
    LaunchedEffect(zone.constraints.maxAcceleration) {
        val parsed = accText.toDoubleOrNull()
        if (parsed == null || kotlin.math.abs(parsed - zone.constraints.maxAcceleration) > 0.01) {
            accText = String.format("%.2f", zone.constraints.maxAcceleration)
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
            Text("Zone #${idx + 1}: ${zone.name}", fontSize = 12.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AresError, modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = nameText,
            onValueChange = { newValue ->
                nameText = newValue
                onChanged(zone.copy(name = newValue))
            },
            label = { Text("Zone Name", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = velText,
                onValueChange = { newValue ->
                    velText = newValue
                    newValue.toDoubleOrNull()?.let {
                        onChanged(zone.copy(constraints = zone.constraints.copy(maxVelocity = it)))
                    }
                },
                label = { Text("Max Vel (m/s)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )

            OutlinedTextField(
                value = accText,
                onValueChange = { newValue ->
                    accText = newValue
                    newValue.toDoubleOrNull()?.let {
                        onChanged(zone.copy(constraints = zone.constraints.copy(maxAcceleration = it)))
                    }
                },
                label = { Text("Max Accel (m/s²)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Start Position: ${String.format("%.2f", minPosSliderVal)}", fontSize = 11.sp, color = AresTextPrimary)
            Slider(
                value = minPosSliderVal,
                onValueChange = { newValue ->
                    minPosSliderVal = newValue
                    if (newValue > maxPosSliderVal) {
                        maxPosSliderVal = newValue
                    }
                    onChanged(zone.copy(minWaypointRelativePos = newValue.toDouble(), maxWaypointRelativePos = maxPosSliderVal.toDouble()))
                },
                valueRange = 0f..maxPos.toFloat(),
                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan, inactiveTrackColor = AresBorder)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("End Position: ${String.format("%.2f", maxPosSliderVal)}", fontSize = 11.sp, color = AresTextPrimary)
            Slider(
                value = maxPosSliderVal,
                onValueChange = { newValue ->
                    maxPosSliderVal = newValue
                    if (newValue < minPosSliderVal) {
                        minPosSliderVal = newValue
                    }
                    onChanged(zone.copy(maxWaypointRelativePos = newValue.toDouble(), minWaypointRelativePos = minPosSliderVal.toDouble()))
                },
                valueRange = 0f..maxPos.toFloat(),
                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan, inactiveTrackColor = AresBorder)
            )
        }
    }
}

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun PointTowardsZoneCard(
    idx: Int,
    zone: PointTowardsZone,
    maxPos: Double,
    onChanged: (PointTowardsZone) -> Unit,
    onDelete: () -> Unit
) {
    var nameText by remember { mutableStateOf(zone.name) }
    var minPosSliderVal by remember { mutableStateOf(zone.minWaypointRelativePos.toFloat()) }
    var maxPosSliderVal by remember { mutableStateOf(zone.maxWaypointRelativePos.toFloat()) }
    var targetXText by remember { mutableStateOf(String.format("%.3f", zone.fieldPosition.x)) }
    var targetYText by remember { mutableStateOf(String.format("%.3f", zone.fieldPosition.y)) }
    var offsetText by remember { mutableStateOf(String.format("%.1f", zone.rotationOffset)) }

    LaunchedEffect(zone.name) { if (nameText != zone.name) nameText = zone.name }
    LaunchedEffect(zone.minWaypointRelativePos) { if (kotlin.math.abs(minPosSliderVal - zone.minWaypointRelativePos.toFloat()) > 0.01f) minPosSliderVal = zone.minWaypointRelativePos.toFloat() }
    LaunchedEffect(zone.maxWaypointRelativePos) { if (kotlin.math.abs(maxPosSliderVal - zone.maxWaypointRelativePos.toFloat()) > 0.01f) maxPosSliderVal = zone.maxWaypointRelativePos.toFloat() }
    LaunchedEffect(zone.fieldPosition.x) {
        if (targetXText.toDoubleOrNull() != zone.fieldPosition.x) targetXText = String.format("%.3f", zone.fieldPosition.x)
    }
    LaunchedEffect(zone.fieldPosition.y) {
        if (targetYText.toDoubleOrNull() != zone.fieldPosition.y) targetYText = String.format("%.3f", zone.fieldPosition.y)
    }
    LaunchedEffect(zone.rotationOffset) {
        val parsed = offsetText.toDoubleOrNull()
        if (parsed == null || kotlin.math.abs(parsed - zone.rotationOffset) > 0.1) offsetText = String.format("%.1f", zone.rotationOffset)
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
            Text("Aim Zone #${idx + 1}: ${zone.name}", fontSize = 12.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AresError, modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = nameText,
            onValueChange = { newValue ->
                nameText = newValue
                onChanged(zone.copy(name = newValue))
            },
            label = { Text("Zone Name", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = targetXText,
                onValueChange = { newValue ->
                    targetXText = newValue
                    newValue.toDoubleOrNull()?.let {
                        onChanged(zone.copy(fieldPosition = PathPoint(it, zone.fieldPosition.y)))
                    }
                },
                label = { Text("Target X (m)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )

            OutlinedTextField(
                value = targetYText,
                onValueChange = { newValue ->
                    targetYText = newValue
                    newValue.toDoubleOrNull()?.let {
                        onChanged(zone.copy(fieldPosition = PathPoint(zone.fieldPosition.x, it)))
                    }
                },
                label = { Text("Target Y (m)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
            )
        }

        OutlinedTextField(
            value = offsetText,
            onValueChange = { newValue ->
                offsetText = newValue
                newValue.toDoubleOrNull()?.let {
                    onChanged(zone.copy(rotationOffset = it))
                }
            },
            label = { Text("Rotation Offset (Deg)", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Start Position: ${String.format("%.2f", minPosSliderVal)}", fontSize = 11.sp, color = AresTextPrimary)
            Slider(
                value = minPosSliderVal,
                onValueChange = { newValue ->
                    minPosSliderVal = newValue
                    if (newValue > maxPosSliderVal) {
                        maxPosSliderVal = newValue
                    }
                    onChanged(zone.copy(minWaypointRelativePos = newValue.toDouble(), maxWaypointRelativePos = maxPosSliderVal.toDouble()))
                },
                valueRange = 0f..maxPos.toFloat(),
                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan, inactiveTrackColor = AresBorder)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("End Position: ${String.format("%.2f", maxPosSliderVal)}", fontSize = 11.sp, color = AresTextPrimary)
            Slider(
                value = maxPosSliderVal,
                onValueChange = { newValue ->
                    maxPosSliderVal = newValue
                    if (newValue < minPosSliderVal) {
                        minPosSliderVal = newValue
                    }
                    onChanged(zone.copy(maxWaypointRelativePos = newValue.toDouble(), minWaypointRelativePos = minPosSliderVal.toDouble()))
                },
                valueRange = 0f..maxPos.toFloat(),
                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan, inactiveTrackColor = AresBorder)
            )
        }
    }
}

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun CollapsibleSection(
    title: String,
    badgeCount: Int? = null,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, AresBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                    if (badgeCount != null) {
                        Surface(
                            shape = CircleShape,
                            color = AresCyan.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(badgeCount.toString(), fontSize = 10.sp, color = AresCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AresCyan.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(badgeText, fontSize = 10.sp, color = AresCyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = AresTextSecondary
                )
            }
            if (expanded) {
                HorizontalDivider(color = AresBorder)
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}
