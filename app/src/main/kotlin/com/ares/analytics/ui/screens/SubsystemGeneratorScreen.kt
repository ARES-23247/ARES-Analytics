
package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.*

/**
 * Interactive wizard screen for generating Redux-compliant robot subsystem code files.
 *
 * Configures state variables, hardware interfaces (motors, encoders, analog sensors), controller models (PID, LQR, Feedforward),
 * and generates complete Kotlin source file suites (`State.kt`, `IO.kt`, `FtcIO.kt`, `MockIO.kt`, `Controller.kt`).
 *
 * @param viewModel [SubsystemGeneratorViewModel] managing generation parameters and file preview maps.
 * @param projectPath Target root path of the robot codebase directory.
 *
 * @see com.ares.analytics.service.SubsystemTemplateEngine
 * @see SubsystemGeneratorViewModel
 */
@Composable
fun SubsystemGeneratorScreen(
    viewModel: SubsystemGeneratorViewModel,
    projectPath: String
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val previewScrollState = rememberScrollState()

    var selectedPreviewFile by remember(state.previewFiles) {
        mutableStateOf(state.previewFiles.keys.firstOrNull() ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Construction, contentDescription = null, tint = AresCyan, modifier = Modifier.size(24.dp))
                    Text(
                        "Redux Subsystem Generator Wizard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AresTextPrimary
                    )
                }
                Text(
                    "Visually configure and generate 100% Zero-GC Redux subsystem suites for your robot codebase.",
                    color = AresTextSecondary,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { viewModel.onIntent(SubsystemGeneratorIntent.GenerateFiles(projectPath)) },
                enabled = state.validationErrors.isEmpty() && state.subsystemName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, disabledContainerColor = AresBorder)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate Subsystem Files", color = if (state.validationErrors.isEmpty()) AresBackground else AresTextTertiary, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = AresBorder)

        // Status Result Banner
        state.generationResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (result.success) AresGreen.copy(alpha = 0.15f) else AresError.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, if (result.success) AresGreen else AresError),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (result.success) AresGreen else AresError
                        )
                        Text(result.message, color = AresTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    IconButton(onClick = { viewModel.onIntent(SubsystemGeneratorIntent.ClearResult) }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AresTextSecondary)
                    }
                }
            }
        }

        // Main 2-Column Layout
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT COLUMN: Form Controls
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Identity Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                    border = BorderStroke(1.dp, AresBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = AresCyan, modifier = Modifier.size(18.dp))
                            Text("1. Subsystem Identity", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 14.sp)
                        }

                        OutlinedTextField(
                            value = state.subsystemName,
                            onValueChange = { viewModel.onIntent(SubsystemGeneratorIntent.SetName(it)) },
                            label = { Text("Subsystem Name (e.g. Intake, Elevator, Shooter)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )

                        OutlinedTextField(
                            value = state.packageName,
                            onValueChange = { viewModel.onIntent(SubsystemGeneratorIntent.SetPackageName(it)) },
                            label = { Text("Package Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                        )
                    }
                }

                // 2. Hardware Components Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                    border = BorderStroke(1.dp, AresBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = AresCyan, modifier = Modifier.size(18.dp))
                                Text("2. Hardware Components", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 14.sp)
                            }
                            IconButton(onClick = { viewModel.onIntent(SubsystemGeneratorIntent.AddHardware()) }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add Hardware", tint = AresCyan)
                            }
                        }

                        state.hardwareEntries.forEachIndexed { index, hw ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AresSurface, RoundedCornerShape(6.dp))
                                    .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = hw.name,
                                    onValueChange = { viewModel.onIntent(SubsystemGeneratorIntent.UpdateHardware(index, hw.copy(name = it))) },
                                    label = { Text("Name") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                                )

                                var typeExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1.3f)) {
                                    OutlinedTextField(
                                        value = hw.type.displayName.split(" ").first(),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Type") },
                                        modifier = Modifier.fillMaxWidth().clickable { typeExpanded = !typeExpanded },
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { typeExpanded = !typeExpanded })
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                                    )
                                    DropdownMenu(
                                        expanded = typeExpanded,
                                        onDismissRequest = { typeExpanded = false }
                                    ) {
                                        HardwareType.entries.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type.displayName, fontSize = 12.sp) },
                                                onClick = {
                                                    viewModel.onIntent(SubsystemGeneratorIntent.UpdateHardware(index, hw.copy(type = type)))
                                                    typeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                if (state.hardwareEntries.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.onIntent(SubsystemGeneratorIntent.RemoveHardware(index)) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = AresError)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. State Fields Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                    border = BorderStroke(1.dp, AresBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.DataObject, contentDescription = null, tint = AresCyan, modifier = Modifier.size(18.dp))
                                Text("3. Immutable State Fields", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 14.sp)
                            }
                            IconButton(onClick = { viewModel.onIntent(SubsystemGeneratorIntent.AddStateField()) }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add Field", tint = AresCyan)
                            }
                        }

                        state.stateFields.forEachIndexed { index, field ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AresSurface, RoundedCornerShape(6.dp))
                                    .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = field.name,
                                    onValueChange = { viewModel.onIntent(SubsystemGeneratorIntent.UpdateStateField(index, field.copy(name = it))) },
                                    label = { Text("Field Name") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                                )

                                var fieldTypeExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(0.9f)) {
                                    OutlinedTextField(
                                        value = field.type.kotlinType,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Type") },
                                        modifier = Modifier.fillMaxWidth().clickable { fieldTypeExpanded = !fieldTypeExpanded },
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { fieldTypeExpanded = !fieldTypeExpanded })
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                                    )
                                    DropdownMenu(
                                        expanded = fieldTypeExpanded,
                                        onDismissRequest = { fieldTypeExpanded = false }
                                    ) {
                                        FieldType.entries.forEach { fType ->
                                            DropdownMenuItem(
                                                text = { Text(fType.kotlinType, fontSize = 12.sp) },
                                                onClick = {
                                                    viewModel.onIntent(SubsystemGeneratorIntent.UpdateStateField(index, field.copy(type = fType, defaultValue = fType.defaultLiteral)))
                                                    fieldTypeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = field.defaultValue,
                                    onValueChange = { viewModel.onIntent(SubsystemGeneratorIntent.UpdateStateField(index, field.copy(defaultValue = it))) },
                                    label = { Text("Default") },
                                    modifier = Modifier.weight(0.8f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                                )

                                if (state.stateFields.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.onIntent(SubsystemGeneratorIntent.RemoveStateField(index)) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = AresError)
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Options Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                    border = BorderStroke(1.dp, AresBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = AresCyan, modifier = Modifier.size(18.dp))
                            Text("4. Suite Generation Options", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 14.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.onIntent(SubsystemGeneratorIntent.SetOption("mockIO", !state.generateMockIO)) }) {
                            Checkbox(checked = state.generateMockIO, onCheckedChange = { viewModel.onIntent(SubsystemGeneratorIntent.SetOption("mockIO", it)) }, colors = CheckboxDefaults.colors(checkedColor = AresCyan))
                            Text("Generate MockIO stub (required for desktop physics simulation)", color = AresTextPrimary, fontSize = 12.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.onIntent(SubsystemGeneratorIntent.SetOption("testSkeleton", !state.generateTestSkeleton)) }) {
                            Checkbox(checked = state.generateTestSkeleton, onCheckedChange = { viewModel.onIntent(SubsystemGeneratorIntent.SetOption("testSkeleton", it)) }, colors = CheckboxDefaults.colors(checkedColor = AresCyan))
                            Text("Generate JUnit 5 unit test skeleton class", color = AresTextPrimary, fontSize = 12.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.onIntent(SubsystemGeneratorIntent.SetOption("dedicatedActions", !state.generateDedicatedActions)) }) {
                            Checkbox(checked = state.generateDedicatedActions, onCheckedChange = { viewModel.onIntent(SubsystemGeneratorIntent.SetOption("dedicatedActions", it)) }, colors = CheckboxDefaults.colors(checkedColor = AresCyan))
                            Text("Generate dedicated sealed Action & Reducer classes (vs. flat SuperstructureState)", color = AresTextPrimary, fontSize = 12.sp)
                        }
                    }
                }

                // Validation Error display
                if (state.validationErrors.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(AresError.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.validationErrors.forEach { err ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AresError, modifier = Modifier.size(14.dp))
                                Text(err, color = AresError, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // RIGHT COLUMN: Live Code Preview
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                border = BorderStroke(1.dp, AresBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = AresCyan, modifier = Modifier.size(18.dp))
                            Text("Live Code Preview", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 14.sp)
                        }
                        Text("${state.previewFiles.size} Files", color = AresTextSecondary, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    // File Selection Tabs
                    if (state.previewFiles.isNotEmpty()) {
                        val keys = state.previewFiles.keys.toList()
                        val currentTab = if (selectedPreviewFile in keys) selectedPreviewFile else keys.first()

                        ScrollableTabRow(
                            selectedTabIndex = keys.indexOf(currentTab).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            containerColor = AresSurface,
                            contentColor = AresCyan,
                            divider = {}
                        ) {
                            keys.forEach { path ->
                                val fileName = path.substringAfterLast("/")
                                Tab(
                                    selected = currentTab == path,
                                    onClick = { selectedPreviewFile = path },
                                    text = { Text(fileName, fontSize = 11.sp, fontWeight = if (currentTab == path) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Preview Content Box
                        val content = state.previewFiles[currentTab] ?: "// No preview"
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AresBackground, RoundedCornerShape(6.dp))
                                .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                                .verticalScroll(previewScrollState)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = content,
                                color = AresTextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Enter a valid subsystem name to generate live code preview.", color = AresTextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
