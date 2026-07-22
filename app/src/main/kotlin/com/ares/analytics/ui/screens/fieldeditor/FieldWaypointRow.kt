package com.ares.analytics.ui.screens.fieldeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.FieldWaypoint
import com.ares.analytics.ui.components.forms.AresTextField
import com.ares.analytics.ui.theme.*

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun FieldWaypointRow(
    index: Int,
    wp: FieldWaypoint,
    onUpdate: (Int, FieldWaypoint) -> Unit,
    onDelete: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AresSurfaceElevated)
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            var nameText by remember(wp.id, wp.name) { mutableStateOf(wp.name) }
            AresTextField(
                value = nameText,
                onValueChange = { newVal ->
                    nameText = newVal
                    onUpdate(index, wp.copy(name = newVal))
                },
                label = "Waypoint Name",
                labelFontSize = 9.sp,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                var wpXText by remember(wp.id, wp.x) { mutableStateOf(wp.x.toString()) }
                AresTextField(
                    value = wpXText,
                    onValueChange = { newVal ->
                        wpXText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, wp.copy(x = parsed)) }
                    },
                    label = "X (m)",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
                var wpYText by remember(wp.id, wp.y) { mutableStateOf(wp.y.toString()) }
                AresTextField(
                    value = wpYText,
                    onValueChange = { newVal ->
                        wpYText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, wp.copy(y = parsed)) }
                    },
                    label = "Y (m)",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
                var headingText by remember(wp.id, wp.headingDegrees) { mutableStateOf(wp.headingDegrees.toString()) }
                AresTextField(
                    value = headingText,
                    onValueChange = { newVal ->
                        headingText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, wp.copy(headingDegrees = parsed)) }
                    },
                    label = "Heading (°)",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = { onUpdate(index, wp.copy(locked = !wp.locked)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(if (wp.locked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", tint = if (wp.locked) AresCyan else AresTextTertiary, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onDelete(index) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AresError, modifier = Modifier.size(16.dp))
            }
        }
    }
}
