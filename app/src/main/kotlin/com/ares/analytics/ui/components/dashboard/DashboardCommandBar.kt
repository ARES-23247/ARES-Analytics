package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.service.layoutProfileNameError
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresThemeSettings

private val builtInDashboardProfiles = setOf(
    "Standard", "Driver Coach", "Programmer", "Pit Crew", "Match Review",
    "Pit Diagnostics", "Driver Practice", "Replay"
)

fun isBuiltInDashboardProfile(profile: String): Boolean = builtInDashboardProfiles.any { it.equals(profile, ignoreCase = true) }

@Composable
fun DashboardCommandBar(
    profileName: String,
    availableProfiles: List<String>,
    isEditing: Boolean,
    onSelectProfile: (String) -> Unit,
    onSaveLayoutAs: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onToggleEditing: () -> Unit,
    onAddWidget: () -> Unit,
    onResetLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controlHeight = if (AresThemeSettings.touchOptimizedMode) 48.dp else 38.dp
    var profileExpanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var layoutName by remember { mutableStateOf("") }
    val layoutNameValidationError = if (layoutName.isEmpty()) null else layoutProfileNameError(layoutName)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AresSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isEditing) AresCyan.copy(alpha = 0.65f) else AresBorder.copy(alpha = 0.55f))
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 820.dp
            val title: @Composable () -> Unit = {
                Box {
                    TextButton(onClick = { profileExpanded = true }, modifier = Modifier.height(controlHeight)) {
                        Text(profileName, color = AresCyan, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, "Change dashboard profile", tint = AresCyan)
                    }
                    DropdownMenu(
                        expanded = profileExpanded,
                        onDismissRequest = { profileExpanded = false },
                        modifier = Modifier.width(230.dp).background(AresSurfaceElevated).border(1.dp, AresBorder)
                    ) {
                        availableProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(profile, color = if (profile.equals(profileName, true)) AresCyan else AresTextPrimary, modifier = Modifier.weight(1f))
                                        if (!isBuiltInDashboardProfile(profile)) {
                                            IconButton(onClick = { onDeleteProfile(profile) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Delete, "Delete $profile", tint = AresError, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectProfile(profile)
                                    profileExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(if (isEditing) "Layout editing" else "Operational view", color = if (isEditing) AresCyan else AresTextSecondary)
            }
            val controls: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isEditing) {
                        OutlinedButton(onClick = onResetLayout, modifier = Modifier.height(controlHeight)) {
                            Icon(Icons.Default.RestartAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reset")
                        }
                        OutlinedButton(onClick = { showSaveDialog = true }, modifier = Modifier.height(controlHeight)) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save as")
                        }
                        Button(
                            onClick = onAddWidget,
                            modifier = Modifier.height(controlHeight),
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add widget")
                        }
                    }
                    OutlinedButton(onClick = onToggleEditing, modifier = Modifier.height(controlHeight)) {
                        Icon(if (isEditing) Icons.Default.Check else Icons.Default.Edit, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEditing) "Done" else "Edit layout")
                    }
                }
            }
            if (compact) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { title() }
                    controls()
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { title() }
                    controls()
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save dashboard layout", color = AresTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = layoutName,
                    onValueChange = { layoutName = it },
                    label = { Text("Layout name") },
                    isError = layoutNameValidationError != null,
                    supportingText = layoutNameValidationError?.let { message ->
                        { Text(message, color = AresError) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveLayoutAs(layoutName.trim())
                        layoutName = ""
                        showSaveDialog = false
                    },
                    enabled = layoutProfileNameError(layoutName) == null,
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
            containerColor = AresSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
