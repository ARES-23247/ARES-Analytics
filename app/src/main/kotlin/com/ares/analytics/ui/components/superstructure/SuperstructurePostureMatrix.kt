package com.ares.analytics.ui.components.superstructure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areslib.superstructure.SuperstructureDocument
import com.areslib.superstructure.SuperstructureStatePreset
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.superstructure.*

@Composable
fun SuperstructurePostureMatrix(
    state: SuperstructureStudioState,
    draft: SuperstructureDocument,
    viewModel: SuperstructureStudioViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Coordinator Overview Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AresSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("COORDINATOR IDENTITY & DEFAULT POSTURES", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.displayName,
                        onValueChange = { viewModel.updateMetadata(it, draft.description) },
                        label = { Text("Coordinator Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    StudioDropdown(
                        label = "Initial: ${draft.states.firstOrNull { it.stateId == draft.initialStateId }?.displayName ?: draft.initialStateId}",
                        options = draft.states.map { it.stateId to it.displayName },
                        onSelect = viewModel::setInitialState,
                        modifier = Modifier.weight(1f),
                    )
                    StudioDropdown(
                        label = "Fault: ${draft.states.firstOrNull { it.stateId == draft.faultStateId }?.displayName ?: draft.faultStateId}",
                        options = draft.states.map { it.stateId to it.displayName },
                        onSelect = viewModel::setFaultState,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Posture Presets Matrix
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AresSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("SYNCHRONIZED POSTURES & TARGET SETPOINTS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Each posture defines immutable targets across all mechanism subsystems.", color = AresTextTertiary, fontSize = 10.sp)
                    }
                    var addOpen by remember { mutableStateOf(false) }
                    var newId by remember { mutableStateOf("") }
                    var newName by remember { mutableStateOf("") }
                    if (!addOpen) {
                        Button(
                            onClick = { addOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Posture", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newId,
                                onValueChange = { newId = it },
                                label = { Text("ID (e.g. score_high)", fontSize = 10.sp) },
                                modifier = Modifier.width(140.dp).height(48.dp),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Name (e.g. Score High)", fontSize = 10.sp) },
                                modifier = Modifier.width(160.dp).height(48.dp),
                                singleLine = true,
                            )
                            Button(
                                onClick = {
                                    if (newId.isNotBlank()) {
                                        viewModel.addState(newId.trim(), newName.trim())
                                        newId = ""
                                        newName = ""
                                        addOpen = false
                                    }
                                },
                                enabled = newId.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = AresGreen, contentColor = AresOnAccent),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Text("Create", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { addOpen = false }, modifier = Modifier.height(36.dp)) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Posture Preset Cards List
                draft.states.forEach { preset ->
                    val isSelected = preset.stateId == state.selectedStateId
                    val isInitial = preset.stateId == draft.initialStateId
                    val isFault = preset.stateId == draft.faultStateId

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) AresCyan else AresBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.selectState(preset.stateId) },
                        color = if (isSelected) AresCyan.copy(alpha = 0.08f) else AresSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        preset.displayName.ifBlank { preset.stateId },
                                        color = AresTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        "id: ${preset.stateId}",
                                        color = AresCyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                    )
                                    if (isInitial) {
                                        Surface(color = AresGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                            Text("INITIAL", color = AresGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    if (isFault) {
                                        Surface(color = AresError.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                            Text("FAULT", color = AresError, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${preset.subsystemTargets.size} targets",
                                        color = AresTextSecondary,
                                        fontSize = 10.sp,
                                    )
                                    if (!isInitial && !isFault && draft.states.size > 2) {
                                        IconButton(
                                            onClick = {
                                                viewModel.selectState(preset.stateId)
                                                viewModel.removeSelectedState()
                                            },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AresError, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            // Subsystem Setpoints inline badges
                            if (preset.subsystemTargets.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    preset.subsystemTargets.take(6).forEach { target ->
                                        val label = state.targetFields.firstOrNull { it.reference == target.target }?.label ?: target.target.fieldUid
                                        val valStr = target.constantDoubleValue?.toString()
                                            ?: target.constantBooleanValue?.toString()
                                            ?: target.constantStringValue
                                            ?: target.lutId
                                            ?: "-"
                                        Surface(
                                            color = AresSurface,
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, AresBorder),
                                        ) {
                                            Text(
                                                "$label: $valStr",
                                                color = AresTextSecondary,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudioDropdown(
    label: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(label, color = AresTextPrimary, fontSize = 11.sp, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, display) ->
                DropdownMenuItem(
                    text = { Text(display, fontSize = 11.sp) },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    }
                )
            }
        }
    }
}
