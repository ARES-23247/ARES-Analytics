
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
import com.ares.analytics.shared.AprilTagPlacement
import com.ares.analytics.ui.components.forms.AresTextField
import com.ares.analytics.ui.theme.*

/**
 * UI row component for editing 3D AprilTag placement parameters in the field layout editor.
 *
 * Allows configuring numeric Tag ID, field coordinates $(x, y, z)$ in meters ($m$), and 3D Euler rotation angles $(\text{roll}, \text{pitch}, \text{yaw})$ in degrees ($^\circ$).
 *
 * ### Physical Units:
 * - Position $(x, y, z)$: Meters ($m$)
 * - Rotation $(\text{roll}, \text{pitch}, \text{yaw})$: Degrees ($^\circ$)
 *
 * ### Thread Safety & Performance Guarantees:
 * Zero-allocation Jetpack Compose UI component. State mutations are dispatched via non-blocking callbacks.
 *
 * @param index Zero-based list index of this AprilTag.
 * @param at Current [AprilTagPlacement] data record.
 * @param onUpdate Callback invoked when AprilTag parameters are modified.
 * @param onDelete Callback invoked when this AprilTag entry is deleted.
 *
 * @see com.ares.analytics.shared.AprilTagPlacement
 * @see FieldEditorScreen
 */
@Composable
fun AprilTagRow(
    index: Int,
    at: AprilTagPlacement,
    onUpdate: (Int, AprilTagPlacement) -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                var tagIdText by remember(at.id, at.tagId) { mutableStateOf(at.tagId.toString()) }
                AresTextField(
                    value = tagIdText,
                    onValueChange = { newVal ->
                        tagIdText = newVal
                        newVal.toIntOrNull()?.let { parsed -> onUpdate(index, at.copy(tagId = parsed)) }
                    },
                    label = "Tag ID",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
                var tagZText by remember(at.id, at.z) { mutableStateOf(at.z.toString()) }
                AresTextField(
                    value = tagZText,
                    onValueChange = { newVal ->
                        tagZText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, at.copy(z = parsed)) }
                    },
                    label = "Z (m)",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
                var tagYawText by remember(at.id, at.yawDegrees) { mutableStateOf(at.yawDegrees.toString()) }
                AresTextField(
                    value = tagYawText,
                    onValueChange = { newVal ->
                        tagYawText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, at.copy(yawDegrees = parsed)) }
                    },
                    label = "Yaw (°)",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                var tagXText by remember(at.id, at.x) { mutableStateOf(at.x.toString()) }
                AresTextField(
                    value = tagXText,
                    onValueChange = { newVal ->
                        tagXText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, at.copy(x = parsed)) }
                    },
                    label = "X (m)",
                    labelFontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary)
                )
                var tagYText by remember(at.id, at.y) { mutableStateOf(at.y.toString()) }
                AresTextField(
                    value = tagYText,
                    onValueChange = { newVal ->
                        tagYText = newVal
                        newVal.toDoubleOrNull()?.let { parsed -> onUpdate(index, at.copy(y = parsed)) }
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
                onClick = { onUpdate(index, at.copy(locked = !at.locked)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(if (at.locked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", tint = if (at.locked) AresCyan else AresTextTertiary, modifier = Modifier.size(16.dp))
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
