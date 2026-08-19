package com.ares.analytics.ui.components.subsystems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.SubsystemGeneratorViewModel
import com.areslib.subsystem.SubsystemControlStrategy
import com.areslib.subsystem.SubsystemHardwareDocument
import com.areslib.subsystem.SubsystemHardwareKind
import com.areslib.subsystem.SubsystemPlatform

fun SubsystemHardwareKind.isActuator(): Boolean =
    this == SubsystemHardwareKind.MOTOR ||
    this == SubsystemHardwareKind.POSITIONAL_SERVO ||
    this == SubsystemHardwareKind.CONTINUOUS_SERVO

fun SubsystemHardwareDocument.connectionLabel(platform: SubsystemPlatform): String = when (platform) {
    SubsystemPlatform.FTC -> connection.hardwareMapName?.let { "hwMap: $it" } ?: "unconfigured"
    SubsystemPlatform.FRC -> if (kind == SubsystemHardwareKind.MOTOR) "CAN ${connection.canId ?: 0} (${connection.canBus})" else "channel ${connection.channel ?: 0}"
}

fun SubsystemControlStrategy.requiresMeasurement(): Boolean =
    this in setOf(SubsystemControlStrategy.POSITION_PID, SubsystemControlStrategy.VELOCITY_PID, SubsystemControlStrategy.BANG_BANG)

@Composable
fun EditorCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = AresCyan, modifier = Modifier.size(16.dp))
                Text(title, color = AresTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
fun SelectableRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AresCyan else AresBorder,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
        color = if (selected) AresCyan.copy(alpha = 0.10f) else AresSurface,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = AresTextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AresTextPrimary, fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun TextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun DoubleInput(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun NullableDoubleInput(
    label: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (it.isBlank()) onValueChange(null) else it.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun IntInput(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun <T : Enum<T>> EnumSelector(
    label: String,
    selected: T,
    entries: List<T>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$label: ${selected.name.replace('_', ' ').lowercase()}", fontSize = 11.sp, color = AresTextPrimary)
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp), tint = AresTextSecondary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name.replace('_', ' ').lowercase(), fontSize = 11.sp) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    }
                )
            }
        }
    }
}

@Composable
fun DropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$label: $selected", fontSize = 11.sp, color = AresTextPrimary)
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp), tint = AresTextSecondary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 11.sp) },
                    onClick = {
                        expanded = false
                        onSelect(opt)
                    }
                )
            }
        }
    }
}

@Composable
fun StableIdLabel(
    title: String,
    id: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AresTextSecondary, fontSize = 11.sp)
            Text(id, color = AresCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(description, color = AresTextTertiary, fontSize = 10.sp)
    }
}

@Composable
fun AddHardwareButton(viewModel: SubsystemGeneratorViewModel, label: String = "+ Add hardware") {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SubsystemHardwareKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.name.replace('_', ' ').lowercase(), fontSize = 11.sp) },
                    onClick = {
                        expanded = false
                        viewModel.addHardware(kind)
                    }
                )
            }
        }
    }
}

@Composable
fun ConceptCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AresSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AresBorder),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(body, color = AresTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
