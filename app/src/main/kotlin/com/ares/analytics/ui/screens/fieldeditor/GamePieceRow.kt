package com.ares.analytics.ui.screens.fieldeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.shared.League
import com.ares.analytics.ui.components.forms.AresTextField
import com.ares.analytics.ui.theme.*

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun GamePieceRow(
    index: Int,
    gp: GamePiece,
    league: League,
    onUpdate: (Int, GamePiece) -> Unit,
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
            AresTextField(
                value = gp.name,
                onValueChange = { newName -> onUpdate(index, gp.copy(name = newName)) },
                label = "Label",
                labelFontSize = 9.sp,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            val types = if (league == League.FTC) {
                listOf("Sample (Yellow)", "Sample (Red)", "Sample (Blue)", "Specimen")
            } else {
                listOf("Note", "High Note")
            }
            var expanded by remember { mutableStateOf(false) }
            Box {
                Text(
                    text = "Type: ${gp.type} ▾",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AresCyan,
                    modifier = Modifier.clickable { expanded = true }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    types.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t, fontSize = 12.sp) },
                            onClick = {
                                expanded = false
                                onUpdate(index, gp.copy(type = t))
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                var gpXText by remember(gp.id, gp.x) { mutableStateOf(gp.x.toString()) }
                AresTextField(
                    value = gpXText,
                    onValueChange = { newVal ->
                        gpXText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, gp.copy(x = parsed)) }
                    },
                    label = "X (m)",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
                var gpYText by remember(gp.id, gp.y) { mutableStateOf(gp.y.toString()) }
                AresTextField(
                    value = gpYText,
                    onValueChange = { newVal ->
                        gpYText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, gp.copy(y = parsed)) }
                    },
                    label = "Y (m)",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = { onUpdate(index, gp.copy(locked = !gp.locked)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(if (gp.locked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", tint = if (gp.locked) AresCyan else AresTextTertiary, modifier = Modifier.size(16.dp))
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
