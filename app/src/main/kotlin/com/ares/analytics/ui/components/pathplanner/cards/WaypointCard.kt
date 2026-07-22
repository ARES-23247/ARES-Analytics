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
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.ui.components.pathplanner.*
import com.ares.analytics.ui.theme.*

@Composable
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
