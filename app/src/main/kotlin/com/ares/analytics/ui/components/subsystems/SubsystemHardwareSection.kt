package com.ares.analytics.ui.components.subsystems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
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
import com.ares.analytics.viewmodel.SubsystemProblemSeverity
import com.areslib.subsystem.*

@Composable
fun SubsystemHardwareSection(
    state: SubsystemGeneratorState,
    viewModel: SubsystemGeneratorViewModel,
    modifier: Modifier = Modifier,
) {
    val document = state.draft?.document ?: return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val hardwareProblems = state.problems.filter { it.path.startsWith("hardware") }
        if (hardwareProblems.isNotEmpty()) {
            EditorCard("Hardware Configuration Notices", Icons.Default.Warning) {
                hardwareProblems.forEach { problem ->
                    OutlinedButton(
                        onClick = { viewModel.navigateToProblem(problem.path) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${if (problem.severity == SubsystemProblemSeverity.ERROR) "Error" else "Warning"}: ${problem.message}",
                            color = if (problem.severity == SubsystemProblemSeverity.ERROR) AresError else AresGold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        EditorCard("Physical Hardware Devices (${document.hardware.size})", Icons.Default.Settings) {
            Text(
                "Hardware names must match the robot controller configuration. Every sensor read is cached once per loop. Select any device to edit in the slide-out inspector.",
                color = AresTextSecondary,
                fontSize = 11.sp,
            )
            document.hardware.forEach { device ->
                SelectableRow(
                    title = device.displayName,
                    subtitle = "${device.kind.uiLabel()} · ${device.connectionLabel(document.platform)}",
                    selected = state.selectedHardwareUid == device.uid,
                    onClick = { viewModel.selectHardware(device.uid) },
                )
            }
            AddHardwareButton(viewModel, "+ Add Hardware Device")
        }

        if (document.hardware.isEmpty()) {
            ConceptCard("Start with physical hardware", "Click + Add Hardware Device to declare motors, continuous or positional servos, analog sensors, digital limits, or encoders.")
        }
    }
}

@Composable
fun HardwareInspectorBody(
    state: SubsystemGeneratorState,
    device: SubsystemHardwareDocument,
    viewModel: SubsystemGeneratorViewModel,
) {
    val document = state.draft?.document ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Configure device identification, electrical connection, and cached telemetry.", color = AresTextSecondary, fontSize = 11.sp)
        TextInput("Hardware name", device.displayName) { value ->
            viewModel.updateHardware(device.hardwareId) { it.copy(displayName = value) }
        }
        TextInput("What this device does", device.description) { value ->
            viewModel.updateHardware(device.hardwareId) { it.copy(description = value) }
        }
        StableIdLabel("Code ID", device.hardwareId, "Used by controller rules and generated Kotlin.")
        TextInput("Rename code ID (advanced)", device.hardwareId) { value ->
            viewModel.renameHardwareId(device.hardwareId, value)
        }
        EnumSelector("Device Type", device.kind, SubsystemHardwareKind.entries) { kind ->
            viewModel.changeHardwareKind(device.hardwareId, kind)
        }

        if (device.kind.isActuator()) {
            val eligibleLeaders = document.hardware.filter {
                it.hardwareId != device.hardwareId && it.kind == device.kind && it.following == null
            }
            val independentLabel = "Independent (has its own controller)"
            val leaderLabels = eligibleLeaders.associateBy { "Follow ${it.displayName} (${it.hardwareId})" }
            val selectedLeader = device.following?.leaderId?.let { leaderId ->
                leaderLabels.entries.firstOrNull { it.value.hardwareId == leaderId }?.key
            } ?: independentLabel
            DropdownSelector(
                label = "Command source",
                selected = selectedLeader,
                options = listOf(independentLabel) + leaderLabels.keys,
            ) { label ->
                viewModel.setHardwareFollower(device.hardwareId, leaderLabels[label]?.hardwareId)
            }
            device.following?.let { following ->
                val transforms = if (device.kind == SubsystemHardwareKind.POSITIONAL_SERVO) {
                    listOf(SubsystemFollowerTransform.SAME_DIRECTION, SubsystemFollowerTransform.MIRRORED_POSITION)
                } else {
                    listOf(SubsystemFollowerTransform.SAME_DIRECTION, SubsystemFollowerTransform.INVERTED)
                }
                EnumSelector("Follower direction", following.transform, transforms) { transform ->
                    viewModel.setHardwareFollower(device.hardwareId, following.leaderId, transform)
                }
            }
        }

        when (document.platform) {
            SubsystemPlatform.FTC -> TextInput("Hardware-map name", device.connection.hardwareMapName.orEmpty()) { value ->
                viewModel.updateHardware(device.hardwareId) {
                    it.copy(connection = SubsystemHardwareConnection(hardwareMapName = value))
                }
            }
            SubsystemPlatform.FRC -> if (device.kind == SubsystemHardwareKind.MOTOR) {
                IntInput("CAN ID", device.connection.canId ?: 0) { value ->
                    viewModel.updateHardware(device.hardwareId) { it.copy(connection = it.connection.copy(canId = value, channel = null)) }
                }
                TextInput("CAN bus", device.connection.canBus) { value ->
                    viewModel.updateHardware(device.hardwareId) { it.copy(connection = it.connection.copy(canBus = value)) }
                }
            } else {
                IntInput("Channel", device.connection.channel ?: 0) { value ->
                    viewModel.updateHardware(device.hardwareId) { it.copy(connection = it.connection.copy(channel = value, canId = null)) }
                }
            }
        }
        FieldGuidance(
            if (document.platform == SubsystemPlatform.FTC) {
                "Use this exact name in the FTC Robot Controller configuration. Names are case-sensitive."
            } else {
                "Use the device ID and CAN bus configured in the vendor hardware tools."
            }
        )

        val measurementSources = device.kind.compatibleMeasurementSources()
        if (measurementSources.isNotEmpty()) {
            Text("CACHED INPUT SIGNALS", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            device.measurements.forEachIndexed { index, measurement ->
                CachedMeasurementEditor(document, device, index, measurement, measurementSources, viewModel)
            }
            val available = measurementSources.firstOrNull { source ->
                document.stateFields.any { field ->
                    field.fieldId !in device.measurements.map { it.fieldId } &&
                        (field.role == SubsystemFieldRole.MEASUREMENT || field.role == SubsystemFieldRole.STATUS) &&
                        field.type == source.valueType()
                }
            }
            OutlinedButton(
                onClick = {
                    val source = available ?: return@OutlinedButton
                    val field = document.stateFields.first {
                        it.fieldId !in device.measurements.map { measurement -> measurement.fieldId } &&
                            (it.role == SubsystemFieldRole.MEASUREMENT || it.role == SubsystemFieldRole.STATUS) &&
                            it.type == source.valueType()
                    }
                    viewModel.updateHardware(device.hardwareId) {
                        it.copy(measurements = it.measurements + SubsystemMeasurementDocument(field.fieldId, source))
                    }
                },
                enabled = available != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Cached Measurement") }
        }

        if (device.kind == SubsystemHardwareKind.MOTOR && document.platform == SubsystemPlatform.FRC) {
            NullableDoubleInput("Current limit (A)", device.currentLimitAmps) { value ->
                viewModel.updateHardware(device.hardwareId) { it.copy(currentLimitAmps = value) }
            }
        }
        ToggleRow("Required hardware", device.required) { value ->
            viewModel.updateHardware(device.hardwareId) { it.copy(required = value) }
        }
        if (device.kind.isActuator()) {
            DoubleInput("Safe neutral output", device.safeOutput ?: 0.0) { value ->
                viewModel.updateHardware(device.hardwareId) { it.copy(safeOutput = value) }
            }
            ToggleRow("Reverse hardware direction", device.inverted) { value ->
                viewModel.updateHardware(device.hardwareId) { it.copy(inverted = value) }
            }
        }
    }
}

@Composable
private fun CachedMeasurementEditor(
    document: SubsystemDocument,
    device: SubsystemHardwareDocument,
    index: Int,
    measurement: SubsystemMeasurementDocument,
    sources: List<SubsystemMeasurementSource>,
    viewModel: SubsystemGeneratorViewModel,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AresSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AresBorder),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EnumSelector("Hardware signal", measurement.source, sources) { source ->
                val compatible = document.stateFields.firstOrNull {
                    (it.role == SubsystemFieldRole.MEASUREMENT || it.role == SubsystemFieldRole.STATUS) &&
                        it.type == source.valueType() && it.fieldId !in device.measurements.filterIndexed { i, _ -> i != index }.map { it.fieldId }
                }
                viewModel.updateHardware(device.hardwareId) { hardware ->
                    hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                        if (i == index) current.copy(
                            source = source,
                            fieldId = compatible?.fieldId ?: current.fieldId,
                            scale = if (source.valueType() == SubsystemValueType.DOUBLE) current.scale else 1.0,
                            offset = if (source.valueType() == SubsystemValueType.DOUBLE) current.offset else 0.0,
                        ) else current
                    })
                }
            }
            val fieldOptions = document.stateFields.filter {
                (it.role == SubsystemFieldRole.MEASUREMENT || it.role == SubsystemFieldRole.STATUS) &&
                    it.type == measurement.source.valueType() &&
                    it.fieldId !in device.measurements.filterIndexed { i, _ -> i != index }.map { existing -> existing.fieldId }
            }.map { it.fieldId }

            if (fieldOptions.isNotEmpty()) {
                DropdownSelector(
                    label = "Target state field",
                    selected = measurement.fieldId,
                    options = fieldOptions,
                ) { newFieldId ->
                    viewModel.updateHardware(device.hardwareId) { hardware ->
                        hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                            if (i == index) current.copy(fieldId = newFieldId) else current
                        })
                    }
                }
            }
            if (measurement.source.valueType() == SubsystemValueType.DOUBLE) {
                DoubleInput("Scale (state units per hardware unit)", measurement.scale) { value ->
                    viewModel.updateHardware(device.hardwareId) { hardware ->
                        hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                            if (i == index) current.copy(scale = value) else current
                        })
                    }
                }
                DoubleInput("Offset (state units)", measurement.offset) { value ->
                    viewModel.updateHardware(device.hardwareId) { hardware ->
                        hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                            if (i == index) current.copy(offset = value) else current
                        })
                    }
                }
                NullableDoubleInput("Valid minimum (optional)", measurement.validMinimum) { value ->
                    viewModel.updateHardware(device.hardwareId) { hardware ->
                        hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                            if (i == index) current.copy(validMinimum = value) else current
                        })
                    }
                }
                NullableDoubleInput("Valid maximum (optional)", measurement.validMaximum) { value ->
                    viewModel.updateHardware(device.hardwareId) { hardware ->
                        hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                            if (i == index) current.copy(validMaximum = value) else current
                        })
                    }
                }
            }
            NullableLongInput("Freshness timeout (ms; blank inherits subsystem)", measurement.maxAgeMs) { value ->
                viewModel.updateHardware(device.hardwareId) { hardware ->
                    hardware.copy(measurements = hardware.measurements.mapIndexed { i, current ->
                        if (i == index) current.copy(maxAgeMs = value) else current
                    })
                }
            }
            FieldGuidance("ARES reads this signal once per robot loop. Scale and offset convert it into the state field's documented unit before validity checks.")
            IconButton(
                onClick = {
                    viewModel.updateHardware(device.hardwareId) { hardware ->
                        hardware.copy(measurements = hardware.measurements.filterIndexed { i, _ -> i != index })
                    }
                },
                modifier = Modifier.size(22.dp).align(Alignment.End),
            ) {
                Icon(Icons.Default.Delete, null, tint = AresError, modifier = Modifier.size(14.dp))
            }
        }
    }
}
