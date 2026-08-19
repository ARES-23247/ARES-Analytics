package com.ares.analytics.ui.components.subsystems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.SubsystemGeneratorState
import com.ares.analytics.viewmodel.SubsystemGeneratorViewModel
import com.areslib.subsystem.*

@Composable
fun SubsystemStateflowSection(
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
    modifier: Modifier = Modifier,
) {
    val document = state.draft?.document ?: return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // State Fields Card
        EditorCard("Immutable State Values (${document.stateFields.size})", Icons.Default.Memory) {
            Text(
                "Status values describe what sensors observed. Target values describe what driver or autonomous code wants. Select any value to edit in the slide-out inspector.",
                color = AresTextSecondary,
                fontSize = 11.sp,
            )
            document.stateFields.forEach { field ->
                SelectableRow(
                    title = field.displayName,
                    subtitle = "${field.role.name.lowercase()} · ${field.type.name.lowercase()}${field.unit?.let { " ($it)" }.orEmpty()}",
                    selected = state.selectedFieldUid == field.uid,
                    onClick = { viewModel.selectField(field.uid) },
                )
            }
            OutlinedButton(onClick = viewModel::addStateField, modifier = Modifier.fillMaxWidth()) {
                Text("+ Add State Value", fontSize = 11.sp)
            }
        }

        // Controller Rules Card
        EditorCard("Closed-Loop Controllers & Output Rules (${document.controlLoops.size})", Icons.Default.Build) {
            Text(
                "A controller converts immutable state into bounded motor/servo outputs. Select any controller rule to edit gains, feedforward, and sandboxes.",
                color = AresTextSecondary,
                fontSize = 11.sp,
            )
            document.controlLoops.forEach { loop ->
                SelectableRow(
                    title = loop.displayName,
                    subtitle = "${loop.strategy.name.replace('_', ' ').lowercase()} → ${loop.actuatorId}",
                    selected = state.selectedLoopUid == loop.uid,
                    onClick = { viewModel.selectLoop(loop.uid) },
                )
            }
            val canAddControl = document.hardware.any { it.kind.isActuator() } && document.stateFields.any {
                it.role == SubsystemFieldRole.TARGET &&
                    (it.type == SubsystemValueType.DOUBLE || it.type == SubsystemValueType.INT)
            }
            OutlinedButton(
                onClick = viewModel::addControlLoop,
                enabled = canAddControl,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ Add Controller Rule", fontSize = 11.sp)
            }
            if (!canAddControl) {
                Text("Add an actuator and a numeric target state value first.", color = AresTextSecondary, fontSize = 10.sp)
            }
        }

        // 2-DOF Linkage Geometry Editor Canvas
        com.ares.analytics.ui.components.linkage.LinkageEditorCanvas(
            linkage = document.linkage,
            actuatorIds = document.hardware
                .filter { it.kind == SubsystemHardwareKind.MOTOR && it.following == null }
                .map { it.hardwareId },
            angleMeasurementFieldIds = document.stateFields
                .filter { it.role == SubsystemFieldRole.MEASUREMENT && it.type == SubsystemValueType.DOUBLE }
                .map { it.fieldId },
            onLinkageChanged = { newLinkage -> viewModel.edit { it.copy(linkage = newLinkage) } },
        )

        // Safety Contract & Fault Recovery
        SafetyInspector(state, viewModel)
        FaultRecoveryCard(document, viewModel)
        InterlockMatrixCard(document, state, viewModel)
    }
}

@Composable
fun StateFieldInspectorBody(field: SubsystemStateFieldDocument, viewModel: SubsystemGeneratorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Configure state field role, value type, and default initial value.", color = AresTextSecondary, fontSize = 11.sp)
        TextInput("State value name", field.displayName) { value ->
            viewModel.updateStateField(field.fieldId) { it.copy(displayName = value) }
        }
        TextInput("Description", field.description) { value ->
            viewModel.updateStateField(field.fieldId) { it.copy(description = value) }
        }
        StableIdLabel("Code ID", field.fieldId, "Used by cached inputs, controller rules, and actions.")
        TextInput("Rename code ID (advanced)", field.fieldId) { value ->
            viewModel.renameStateFieldId(field.fieldId, value)
        }
        EnumSelector("Role", field.role, SubsystemFieldRole.entries) { role ->
            viewModel.updateStateField(field.fieldId) { it.copy(role = role) }
        }
        EnumSelector("Value Type", field.type, SubsystemValueType.entries) { type ->
            viewModel.updateStateField(field.fieldId) { it.copy(type = type) }
        }
        when (field.type) {
            SubsystemValueType.DOUBLE -> DoubleInput("Default number", field.defaultNumber ?: 0.0) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(defaultNumber = value) }
            }
            SubsystemValueType.BOOLEAN -> ToggleRow("Default boolean", field.defaultBoolean ?: false) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(defaultBoolean = value) }
            }
            SubsystemValueType.INT -> IntInput("Default integer", field.defaultInt ?: 0) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(defaultInt = value) }
            }
            SubsystemValueType.STRING -> TextInput("Default text", field.defaultText.orEmpty()) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(defaultText = value) }
            }
        }
        if (field.type == SubsystemValueType.DOUBLE || field.type == SubsystemValueType.INT) {
            TextInput("Physical unit (optional)", field.unit.orEmpty()) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(unit = value.ifBlank { null }) }
            }
            NullableDoubleInput("Minimum bound", field.minimum) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(minimum = value) }
            }
            NullableDoubleInput("Maximum bound", field.maximum) { value ->
                viewModel.updateStateField(field.fieldId) { it.copy(maximum = value) }
            }
        }
    }
}

@Composable
fun ControlInspectorBody(
    state: SubsystemGeneratorState,
    loop: SubsystemControlLoopDocument,
    viewModel: SubsystemGeneratorViewModel,
) {
    val document = state.draft?.document ?: return
    val actuators = document.hardware.filter { it.kind.isActuator() }.map { it.hardwareId }
    val numericFields = document.stateFields.filter { it.type == SubsystemValueType.DOUBLE || it.type == SubsystemValueType.INT }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Configure control strategy, PID feedback gains, and feedforward dynamics.", color = AresTextSecondary, fontSize = 11.sp)
        TextInput("Controller rule name", loop.displayName) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(displayName = value) }
        }
        StableIdLabel("Code ID", loop.loopId, "Used by generated controller code.")
        EnumSelector("Strategy", loop.strategy, SubsystemControlStrategy.entries) { strategy ->
            viewModel.updateControlLoop(loop.loopId) {
                it.copy(strategy = strategy, measurementFieldId = it.measurementFieldId.takeIf { strategy.requiresMeasurement() })
            }
        }
        if (actuators.isNotEmpty()) {
            DropdownSelector("Actuator", loop.actuatorId, actuators) { value ->
                viewModel.updateControlLoop(loop.loopId) { it.copy(actuatorId = value) }
            }
        }
        if (numericFields.isNotEmpty()) {
            DropdownSelector("Target state", loop.targetFieldId, numericFields.map { it.fieldId }) { value ->
                viewModel.updateControlLoop(loop.loopId) { it.copy(targetFieldId = value) }
            }
        }
        if (loop.strategy.requiresMeasurement()) {
            val measurements = numericFields.filter { it.role == SubsystemFieldRole.MEASUREMENT }.map { it.fieldId }
            if (measurements.isNotEmpty()) {
                DropdownSelector("Measurement feedback", loop.measurementFieldId ?: measurements.first(), measurements) { value ->
                    viewModel.updateControlLoop(loop.loopId) { it.copy(measurementFieldId = value) }
                }
            }
        }
        if (loop.strategy == SubsystemControlStrategy.POSITION_PID || loop.strategy == SubsystemControlStrategy.VELOCITY_PID) {
            Text("PID FEEDBACK GAINS", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            DoubleInput("kP (Proportional)", loop.kP) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kP = value) } }
            DoubleInput("kI (Integral)", loop.kI) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kI = value) } }
            DoubleInput("kD (Derivative)", loop.kD) { value -> viewModel.updateControlLoop(loop.loopId) { it.copy(kD = value) } }
            DoubleInput("Derivative filter (seconds)", loop.derivativeFilterTimeConstantSeconds) { value ->
                viewModel.updateControlLoop(loop.loopId) { it.copy(derivativeFilterTimeConstantSeconds = value) }
            }
        }
        DoubleInput("Minimum output", loop.minimumOutput) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(minimumOutput = value) }
        }
        DoubleInput("Maximum output", loop.maximumOutput) { value ->
            viewModel.updateControlLoop(loop.loopId) { it.copy(maximumOutput = value) }
        }
    }
}

@Composable
fun SafetyInspector(state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    val document = state.draft?.document ?: return
    val safety = document.safety
    var showAdvanced by remember { mutableStateOf(false) }

    EditorCard("Safety & Health Protections", Icons.Default.Warning) {
        Text("Outputs are held in safe neutral if feedback is stale, current is unsafe, or writes fail.", color = AresTextSecondary, fontSize = 11.sp)
        NullableDoubleInput("Feedback timeout (ms)", safety.feedbackTimeoutMs?.toDouble()) { value ->
            viewModel.edit { it.copy(safety = it.safety.copy(feedbackTimeoutMs = value?.toLong())) }
        }
        ToggleRow("Latch failed output writes", safety.latchOutputFaults) { value ->
            viewModel.edit { it.copy(safety = it.safety.copy(latchOutputFaults = value)) }
        }
        ToggleRow("Zero-allocation hot loop path", safety.zeroAllocationPeriodic) { value ->
            viewModel.edit { it.copy(safety = it.safety.copy(zeroAllocationPeriodic = value)) }
        }
        OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAdvanced) "Hide advanced safety flags" else "Show advanced safety flags", fontSize = 11.sp)
        }
        if (showAdvanced) {
            ToggleRow("Require calibration", safety.requiresCalibration) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(requiresCalibration = value)) }
            }
            ToggleRow("Require configuration health", safety.requiresConfigurationHealth) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(requiresConfigurationHealth = value)) }
            }
            ToggleRow("Require valid current monitoring", safety.requiresCurrentMonitoring) { value ->
                viewModel.edit { it.copy(safety = it.safety.copy(requiresCurrentMonitoring = value)) }
            }
        }
    }
}

@Composable
fun FaultRecoveryCard(document: SubsystemDocument, viewModel: SubsystemGeneratorViewModel) {
    val recovery = document.safety.faultRecovery
    val eligibleActuators = document.hardware.filter {
        it.following == null && it.kind in setOf(SubsystemHardwareKind.MOTOR, SubsystemHardwareKind.CONTINUOUS_SERVO)
    }

    EditorCard("Automatic Jam Recovery / Anti-Stall", Icons.Default.Build) {
        Text("Detects mechanical jams from motor current and triggers automatic recovery.", color = AresTextSecondary, fontSize = 11.sp)
        if (eligibleActuators.isNotEmpty()) {
            ToggleRow("Enable anti-jam pulse", recovery.enabled) { value ->
                viewModel.edit { doc ->
                    doc.copy(safety = doc.safety.copy(faultRecovery = doc.safety.faultRecovery.copy(enabled = value)))
                }
            }
        } else {
            Text("Add an independently controlled motor before enabling anti-jam protection.", color = AresTextTertiary, fontSize = 10.sp)
        }
    }
}

@Composable
fun InterlockMatrixCard(document: SubsystemDocument, state: SubsystemGeneratorState, viewModel: SubsystemGeneratorViewModel) {
    EditorCard("Positional Interlocks (${document.interlocks.size})", Icons.Default.Lock) {
        Text("Software interlocks prevent mechanical collisions between mechanism parts.", color = AresTextSecondary, fontSize = 11.sp)
        if (document.interlocks.isEmpty()) {
            Text("No positional interlocks configured.", color = AresTextTertiary, fontSize = 10.sp)
        }
    }
}
