package com.ares.analytics.ui.components.pathplanner.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.ConstraintsZone
import com.ares.analytics.shared.PathPoint
import com.ares.analytics.shared.PointTowardsZone
import com.ares.analytics.shared.RotationTarget
import com.ares.analytics.ui.theme.*

@Composable
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
