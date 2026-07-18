package com.ares.analytics.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.TuningIntent
import com.ares.analytics.viewmodel.TuningViewModel
import com.ares.analytics.viewmodel.SysIdIntent
import com.ares.analytics.viewmodel.SysIdViewModel
import com.ares.analytics.service.AlignedDataRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.areslib.control.assist.SysIdMechanism
import com.areslib.control.assist.SysIdRoutine
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningScreen(
    viewModel: TuningViewModel,
    sysIdViewModel: SysIdViewModel,
    projectPath: String // Kept for compatibility but unused
) {
    val state by viewModel.state.collectAsState()
    val sysIdState by sysIdViewModel.state.collectAsState()
    var activeCalTab by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Constants Tuning Board
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(AresSurface)
                .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Constants Tuning Board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
            HorizontalDivider(color = AresBorder)

            if (state.saveStatus.isNotEmpty()) {
                Text(state.saveStatus, color = AresGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LaunchedEffect(state.saveStatus) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.onIntent(TuningIntent.ClearSaveStatus)
                }
            }
            val error = state.errorMessage
            if (error != null) {
                Text(error, color = AresError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (state.variables.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Waiting for live Tuning constants from Robot over NT4...", color = AresTextTertiary, fontSize = 12.sp)
                }
            } else {
                // Group by custom categories
                val grouped = state.variables.entries.groupBy { 
                    getCustomCategory(it.key)
                }
                
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 320.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    items(grouped.entries.toList().sortedBy { it.key }) { (category, constants) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AresSurfaceElevated, RoundedCornerShape(8.dp))
                                .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AresCyan
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                constants.sortedBy { it.key }.forEach { const ->
                                    val constKey = const.key
                                    val parts = constKey.removePrefix("Tuning/").split("/")
                                    val displayName = if (parts.size > 1) parts.drop(1).joinToString("/") else parts[0]
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val descAndRange = getConstantDescriptionAndRange(constKey)
                                        val tooltipState = rememberTooltipState(isPersistent = true)
                                        val scope = rememberCoroutineScope()

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            when (event.type) {
                                                                PointerEventType.Enter -> {
                                                                    scope.launch { tooltipState.show() }
                                                                }
                                                                PointerEventType.Exit -> {
                                                                    tooltipState.dismiss()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                        ) {
                                            @OptIn(ExperimentalMaterial3Api::class)
                                            TooltipBox(
                                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                                tooltip = {
                                                    PlainTooltip(
                                                        containerColor = AresSurfaceElevated,
                                                        contentColor = AresTextPrimary
                                                    ) {
                                                        Column(modifier = Modifier.padding(4.dp)) {
                                                            Text(displayName, fontSize = 12.sp, color = AresCyan, fontWeight = FontWeight.Bold)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(descAndRange.first, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text("Typical Range: ${descAndRange.second}", fontSize = 10.sp, color = AresCyan, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                },
                                                state = tooltipState
                                            ) {
                                                Text(
                                                    text = displayName,
                                                    fontSize = 12.sp,
                                                    color = AresTextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable(onClick = {})
                                                )
                                            }
                                        }

                                        var textValue by remember(const.value) { mutableStateOf(const.value.toString()) }
                                        BasicTextField(
                                            value = textValue,
                                            onValueChange = { textValue = it },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                                            cursorBrush = SolidColor(AresCyan),
                                            modifier = Modifier
                                                .width(90.dp)
                                                .height(32.dp)
                                                .background(AresSurface, RoundedCornerShape(6.dp))
                                                .border(1.dp, AresBorder, RoundedCornerShape(6.dp)),
                                            decorationBox = { innerTextField ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        if (textValue.isEmpty()) {
                                                            Text("null", fontSize = 11.sp, color = AresTextTertiary)
                                                        }
                                                        innerTextField()
                                                    }
                                                }
                                            }
                                        )
                                        Button(
                                            onClick = {
                                                val newVal = textValue.toDoubleOrNull()
                                                if (newVal != null) {
                                                    viewModel.onIntent(TuningIntent.SaveConstant(constKey, newVal))
                                                }
                                            },
                                            modifier = Modifier.width(60.dp).height(32.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Save", color = AresBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Auto-Calibration Board
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(AresSurface)
                .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Calibration Board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                
                // Connection Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (sysIdState.isRobotConnected) AresGreen else AresError,
                                RoundedCornerShape(4.dp)
                            )
                    )
                    Text(
                        text = if (sysIdState.isRobotConnected) "Robot Connected" else "Robot Disconnected",
                        fontSize = 11.sp,
                        color = AresTextSecondary
                    )
                }
            }
            HorizontalDivider(color = AresBorder)

            // Tabs Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AresSurfaceElevated, RoundedCornerShape(6.dp))
                    .border(1.dp, AresBorder, RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val calTabs = listOf("SysId", "Pinpoint", "Track Width", "Vision")
                calTabs.forEachIndexed { index, title ->
                    val selected = activeCalTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) AresCyan else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { activeCalTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (selected) AresBackground else AresTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tab Content Window
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (activeCalTab) {
                    0 -> { // SysId Drivetrain Tab
                        if (sysIdState.isRoutineRunning) {
                            AbortCard(sysIdViewModel)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SysId Routine Target:", fontSize = 12.sp, color = AresTextSecondary)
                                Row(
                                    modifier = Modifier
                                        .background(AresSurfaceElevated, RoundedCornerShape(6.dp))
                                        .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                                ) {
                                    SysIdMechanism.values().forEach { mech ->
                                        val selected = sysIdState.selectedMechanism == mech
                                        Box(
                                            modifier = Modifier
                                                .background(if (selected) AresCyan else Color.Transparent, RoundedCornerShape(6.dp))
                                                .clickable { sysIdViewModel.onIntent(SysIdIntent.SetMechanism(mech)) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = mech.name,
                                                color = if (selected) AresBackground else AresTextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Select SysId Test to Run:", fontSize = 11.sp, color = AresTextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RoutineButton(
                                        name = "Quasistatic (Combined)",
                                        desc = "Voltage ramps forward then reverse",
                                        onClick = { sysIdViewModel.onIntent(SysIdIntent.StartRoutine(SysIdRoutine.QUASISTATIC)) },
                                        enabled = sysIdState.isRobotConnected
                                    )
                                    RoutineButton(
                                        name = "Dynamic (Combined)",
                                        desc = "Voltage steps forward then reverse",
                                        onClick = { sysIdViewModel.onIntent(SysIdIntent.StartRoutine(SysIdRoutine.DYNAMIC)) },
                                        enabled = sysIdState.isRobotConnected
                                    )
                                }
                            }
                        }

                        val summary = sysIdState.summary
                        if (summary != null) {
                            HorizontalDivider(color = AresBorder)
                            Text("SysId Calculation Results:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                            ParamRow("Static Friction (kS)", String.format("%.4f V", summary.kS))
                            ParamRow("Velocity Constant (kV)", String.format("%.4f V/(m/s)", summary.kV))
                            ParamRow("Acceleration Constant (kA)", String.format("%.4f V/(m/s²)", summary.kA))
                            ParamRow("OLS Fit Quality (R²)", String.format("%.2f%%", summary.rSquared * 100))
                        }
                    }
                    1 -> { // Pinpoint Offset & Ticks/m Tab
                        if (sysIdState.isRoutineRunning) {
                            AbortCard(sysIdViewModel)
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Module Offset Calibration", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                                CalibrationTriggerCard(
                                    name = "Start Pinpoint Offset Calibration",
                                    desc = "Robot spins in place. Computes pinpoint module offsets from physical center of rotation.",
                                    onClick = { sysIdViewModel.onIntent(SysIdIntent.StartCalibration("PINPOINT_SPIN")) },
                                    enabled = sysIdState.isRobotConnected
                                )
                                val px = sysIdState.recommendedPinpointXOffsetMm
                                val py = sysIdState.recommendedPinpointYOffsetMm
                                if (px != null && py != null) {
                                    ParamRow("Recommended X Offset", String.format("%.2f mm", px))
                                    ParamRow("Recommended Y Offset", String.format("%.2f mm", py))
                                    Spacer(Modifier.height(4.dp))
                                    ApplyButton(onClick = { sysIdViewModel.onIntent(SysIdIntent.ApplyCalibration("PINPOINT_SPIN")) })
                                }

                                HorizontalDivider(color = AresBorder)

                                Text("Ticks/Meter Encoder Calibration", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                                var distText by remember(sysIdState.linearDriveActualDistanceMeters) { 
                                    mutableStateOf(sysIdState.linearDriveActualDistanceMeters.toString()) 
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Physical Distance Traveled (meters):", fontSize = 12.sp, color = AresTextSecondary)
                                    BasicTextField(
                                        value = distText,
                                        onValueChange = { 
                                            distText = it
                                            val parsed = it.toDoubleOrNull()
                                            if (parsed != null && parsed > 0.0) {
                                                sysIdViewModel.onIntent(SysIdIntent.SetLinearDriveDistance(parsed))
                                            }
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                                        cursorBrush = SolidColor(AresCyan),
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(30.dp)
                                            .background(AresSurfaceElevated, RoundedCornerShape(6.dp))
                                            .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                                CalibrationTriggerCard(
                                    name = "Start Linear Ticks Calibration",
                                    desc = "Robot will drive straight for 3 seconds. Mark start and end points and measure distance to input above.",
                                    onClick = { sysIdViewModel.onIntent(SysIdIntent.StartCalibration("LINEAR_DRIVE")) },
                                    enabled = sysIdState.isRobotConnected
                                )
                                val ticks = sysIdState.recommendedTicksPerMeter
                                if (ticks != null) {
                                    ParamRow("Recommended Ticks/Meter", String.format("%.2f", ticks))
                                    Spacer(Modifier.height(4.dp))
                                    ApplyButton(onClick = { sysIdViewModel.onIntent(SysIdIntent.ApplyCalibration("LINEAR_DRIVE")) })
                                }
                            }
                        }
                    }
                    2 -> { // Track Width Tab
                        if (sysIdState.isRoutineRunning) {
                            AbortCard(sysIdViewModel)
                        } else {
                            CalibrationTriggerCard(
                                name = "Start Track Width Calibration",
                                desc = "Robot will spin in place to calibrate effective track width / moment arm based on IMU vs wheel travel.",
                                onClick = { sysIdViewModel.onIntent(SysIdIntent.StartCalibration("TRACK_WIDTH_SPIN")) },
                                    enabled = sysIdState.isRobotConnected
                            )
                        }

                        val tw = sysIdState.recommendedTrackWidthMeters
                        if (tw != null) {
                            HorizontalDivider(color = AresBorder)
                            Text("Calibrated Drivetrain Kinematics:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                            ParamRow("Recommended Track Width", String.format("%.4f m", tw))
                            Spacer(Modifier.height(4.dp))
                            ApplyButton(onClick = { sysIdViewModel.onIntent(SysIdIntent.ApplyCalibration("TRACK_WIDTH_SPIN")) })
                        }
                    }
                    3 -> { // Vision Tab
                        if (sysIdState.isRoutineRunning) {
                            AbortCard(sysIdViewModel)
                        } else {
                            CalibrationTriggerCard(
                                name = "Start Vision Noise Calibration",
                                desc = "Place the robot stationary facing an AprilTag. Collects standard deviations of Limelight observations.",
                                onClick = { sysIdViewModel.onIntent(SysIdIntent.StartCalibration("VISION_CALIBRATION")) },
                                enabled = sysIdState.isRobotConnected
                            )
                        }

                        val vx = sysIdState.recommendedVisionStdDevsX
                        val vy = sysIdState.recommendedVisionStdDevsY
                        val vh = sysIdState.recommendedVisionStdDevsHeading
                        if (vx != null && vy != null && vh != null) {
                            HorizontalDivider(color = AresBorder)
                            Text("Calibrated Vision Std Devs:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                            ParamRow("Recommended Std Dev X", String.format("%.4f m", vx))
                            ParamRow("Recommended Std Dev Y", String.format("%.4f m", vy))
                            ParamRow("Recommended Std Dev Heading", String.format("%.4f rad", vh))
                            Spacer(Modifier.height(4.dp))
                            ApplyButton(onClick = { sysIdViewModel.onIntent(SysIdIntent.ApplyCalibration("VISION_CALIBRATION")) })
                        }
                    }
                }

                // Error Display
                sysIdState.errorMessage?.let { err ->
                    Text(err, color = AresError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Export/Apply Status
                if (sysIdState.exportStatus.isNotEmpty()) {
                    Text(
                        text = sysIdState.exportStatus,
                        color = if (sysIdState.exportStatus.contains("Failed")) AresError else AresGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LaunchedEffect(sysIdState.exportStatus) {
                        kotlinx.coroutines.delay(3000)
                        sysIdViewModel.onIntent(SysIdIntent.ClearExportStatus)
                    }
                }

                Spacer(Modifier.weight(1f))
                Text("Live Telemetry Plot (Velocity vs. Time)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AresTextSecondary)
                LiveTelemetryPlot(samples = sysIdState.liveSamples)
            }
        }
    }
}

@Composable
private fun AbortCard(viewModel: SysIdViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, AresError, RoundedCornerShape(8.dp)),
        color = AresError.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CALIBRATION IN PROGRESS", color = AresError, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Robot is executing routine.", color = AresTextSecondary, fontSize = 11.sp)
            }
            Button(
                onClick = { viewModel.onIntent(SysIdIntent.StopCalibration) },
                colors = ButtonDefaults.buttonColors(containerColor = AresError),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("ABORT TEST (STOP)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RoutineButton(
    name: String,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .height(80.dp)
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) AresSurfaceElevated else AresSurfaceElevated.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (enabled) AresCyan else AresTextTertiary)
            Text(desc, fontSize = 11.sp, color = AresTextTertiary, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun CalibrationTriggerCard(
    name: String,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) AresSurfaceElevated else AresSurfaceElevated.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (enabled) AresCyan else AresBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (enabled) AresBackground else AresTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (enabled) AresCyan else AresTextTertiary)
                Text(desc, fontSize = 11.sp, color = AresTextTertiary, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun ParamRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(AresSurfaceElevated).border(1.dp, AresBorder, RoundedCornerShape(6.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = AresTextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AresTextPrimary)
    }
}

@Composable
private fun ApplyButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
        modifier = Modifier.fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text("Apply to Robot Code", color = AresBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun LiveTelemetryPlot(samples: List<AlignedDataRow>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(AresSurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
    ) {
        if (samples.size < 2) return@Canvas

        val maxTime = samples.maxOf { it.timestampMs }
        val minTime = samples.minOf { it.timestampMs }
        val dt = (maxTime - minTime).toDouble()

        val maxVel = samples.maxOf { kotlin.math.abs(it.velocity) }.coerceAtLeast(1.0)
        val path = Path()

        samples.forEachIndexed { index, sample ->
            val x = if (dt > 0) ((sample.timestampMs - minTime) / dt * size.width).toFloat() else 0f
            val y = (size.height - (kotlin.math.abs(sample.velocity) / maxVel * size.height)).toFloat()

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = AresCyan,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun getCustomCategory(key: String): String {
    val cleanKey = key.removePrefix("Tuning/")
    val parts = cleanKey.split("/")
    if (parts.size > 1) {
        return when (parts[0]) {
            "pathTranslationGains" -> "Path Translation PID"
            "pathRotationGains" -> "Path Rotation PID"
            "headingGains" -> "Heading Lock PID"
            "driveFeedforward" -> "Drivetrain Feedforward"
            "motorGains" -> "Motor PIDF"
            else -> parts[0].replace(Regex("([a-z])([A-Z]+)"), "$1 $2").replaceFirstChar { it.uppercase() }
        }
    }
    
    // Top-level variables
    return when {
        cleanKey == "pinpointXOffsetMm" || 
        cleanKey == "pinpointYOffsetMm" || 
        cleanKey == "pinpointEncoderResolution" || 
        cleanKey == "ticksPerMeter" -> "Pinpoint Odometry"
        
        cleanKey.startsWith("vision") -> "Limelight Vision"
        
        cleanKey.startsWith("odomQ") -> "EKF Position Filter"
        
        else -> "General Drivetrain Constants"
    }
}

private fun getConstantDescriptionAndRange(key: String): Pair<String, String> {
    val cleanKey = key.removePrefix("Tuning/")
    return when (cleanKey) {
        "trackWidthMeters" -> Pair("Distance between center of left and right wheels.", "0.30 - 0.50 m")
        "wheelBaseMeters" -> Pair("Distance between center of front and rear wheels.", "0.30 - 0.50 m")
        "pathTranslationGains/kP" -> Pair("Proportional feedback gain for autonomous path translational errors.", "1.0 - 5.0")
        "pathTranslationGains/kI" -> Pair("Integral feedback gain for autonomous path translational errors.", "0.0 - 0.1")
        "pathTranslationGains/kD" -> Pair("Derivative feedback gain for autonomous path translational errors.", "0.01 - 0.1")
        "pathTranslationGains/kF" -> Pair("Feedforward velocity feedback gain coefficient for translation.", "0.0")
        "pathRotationGains/kP" -> Pair("Proportional feedback gain for autonomous path rotational heading errors.", "1.0 - 5.0")
        "pathRotationGains/kI" -> Pair("Integral feedback gain for autonomous path rotational heading errors.", "0.0 - 0.1")
        "pathRotationGains/kD" -> Pair("Derivative feedback gain for autonomous path rotational heading errors.", "0.01 - 0.1")
        "pathRotationGains/kF" -> Pair("Feedforward velocity feedback gain coefficient for rotation.", "0.0")
        "headingGains/kP" -> Pair("Proportional feedback gain to active hold current heading.", "2.0 - 6.0")
        "headingGains/kI" -> Pair("Integral feedback gain to active hold current heading.", "0.0")
        "headingGains/kD" -> Pair("Derivative feedback gain to active hold current heading.", "0.1 - 0.5")
        "headingGains/kF" -> Pair("Feedforward velocity feedback gain coefficient for heading.", "0.0")
        "headingDeadzoneDeg" -> Pair("Angular deadband before heading corrections are applied.", "0.1 - 1.0 deg")
        "driveFeedforward/kS" -> Pair("Static friction feedforward voltage offset to overcome friction.", "0.02 - 0.08")
        "driveFeedforward/kV" -> Pair("Velocity feedforward coefficient (1.0 / max physical speed).", "0.20 - 0.35")
        "driveFeedforward/kA" -> Pair("Acceleration feedforward coefficient.", "0.0 - 0.05")
        "driveSlewRateLimit" -> Pair("Maximum rate of velocity change (acceleration limit).", "2.0 - 4.0 m/s^2")
        "motorGains/kP" -> Pair("Proportional gain for wheel-level closed-loop velocity tracking.", "5.0 - 15.0")
        "motorGains/kI" -> Pair("Integral gain for wheel-level closed-loop velocity tracking.", "0.0 - 5.0")
        "motorGains/kD" -> Pair("Derivative gain for wheel-level closed-loop velocity tracking.", "0.0")
        "motorGains/kF" -> Pair("Feedforward gain for wheel-level closed-loop velocity tracking.", "0.0")
        "visionStdDevsX" -> Pair("Expected measurement noise standard deviation along X-axis.", "0.02 - 0.15 m")
        "visionStdDevsY" -> Pair("Expected measurement noise standard deviation along Y-axis.", "0.02 - 0.15 m")
        "visionStdDevsHeading" -> Pair("Expected measurement noise standard deviation for heading rotation.", "0.05 - 0.20 rad")
        "visionMaxDistanceMeters" -> Pair("Cutoff distance beyond which AprilTag decodes are discarded.", "4.0 - 7.0 m")
        "visionMaxAmbiguity" -> Pair("Maximum pose ambiguity limit for accepting vision tag decodes.", "0.1 - 0.3")
        "visionMahalanobisThreshold" -> Pair("Maximum standard deviations variance mismatch before EKF rejection.", "6.0 - 15.0")
        "odomQx" -> Pair("EKF process noise covariance diagonal parameter for X-axis.", "0.001 - 0.05")
        "odomQy" -> Pair("EKF process noise covariance diagonal parameter for Y-axis.", "0.001 - 0.05")
        "odomQtheta" -> Pair("EKF process noise covariance diagonal parameter for heading.", "0.001 - 0.05")
        "pinpointXOffsetMm" -> Pair("Mounting distance offset of the Pinpoint computer along robot X-axis.", "-200.0 - 200.0 mm")
        "pinpointYOffsetMm" -> Pair("Mounting distance offset of the Pinpoint computer along robot Y-axis.", "-200.0 - 200.0 mm")
        "pinpointEncoderResolution" -> Pair("Resolution calibration factor of the Pinpoint encoders.", "20.0 - 21.0 ticks/mm")
        "ticksPerMeter" -> Pair("Odometry encoder ticks per meter of linear travel.", "1000.0 - 4000.0")
        "driverDeadbandExponent" -> Pair("Input response curve scaling exponent for joysticks.", "1.0 - 2.0 (1.0 = linear)")
        "driverSlewRateLimit" -> Pair("Slew rate acceleration limit mapping on driver input command.", "2.0 - 10.0")
        "stolenRobotRejectionThreshold" -> Pair("Consecutive vision rejections before performing a reseed/snap.", "5 - 20 frames")
        "stolenRobotVelocityThreshold" -> Pair("Robot velocity threshold below which the robot is considered stationary.", "0.01 - 0.10 m/s")
        "pathVelocityScale" -> Pair("Scale factor applied to physical max speed limit during pathfinding.", "0.50 - 1.00 (0.85 = default)")
        "pathAccelerationLimit" -> Pair("Maximum acceleration limit allowed during pathfinding.", "1.5 - 4.0 m/s^2")
        "visionAlignTargetDistance" -> Pair("Target standoff distance to AprilTag center during auto alignment.", "1.5 - 3.5 m")
        "visionAlignMaxHeadingChangeRad" -> Pair("Maximum allowed heading change per frame to reject PnP flips.", "0.10 - 0.50 rad")
        "visionAlignAlphaTranslation" -> Pair("Low-pass filtering factor for vision-based translation tracking.", "0.1 - 0.8 (lower = smoother)")
        "visionAlignAlphaHeading" -> Pair("Low-pass filtering factor for vision-based heading tracking.", "0.1 - 0.8")
        "visionAlignKpTranslation" -> Pair("Proportional tracking gain for vision-assisted alignment translation.", "0.5 - 2.0")
        "visionAlignKpRotation" -> Pair("Proportional tracking gain for vision-assisted alignment rotation.", "0.5 - 2.5")
        "visionAlignKdRotation" -> Pair("Derivative tracking gain for vision-assisted alignment rotation damping.", "0.1 - 0.8")
        "visionAlignKsRotational" -> Pair("Rotational scrubbing static friction feedforward offset.", "0.02 - 0.12")
        "visionAlignTranslationDeadband" -> Pair("Translational deadband tolerance before alignment corrections end.", "0.01 - 0.08 m")
        "visionAlignHeadingErrorDeadband" -> Pair("Rotational error deadband tolerance before alignment corrections end.", "0.01 - 0.05 rad")
        "visionAlignClampTranslationX" -> Pair("Maximum translational X speed override command.", "0.2 - 0.8")
        "visionAlignClampTranslationY" -> Pair("Maximum translational Y speed override command.", "0.2 - 0.8")
        "visionAlignClampRotation" -> Pair("Maximum rotational speed override command.", "0.3 - 0.8")
        "visionAlignSearchFirstSweepMs" -> Pair("First sweep duration when searching for lost AprilTag.", "500 - 2000 ms")
        "visionAlignSearchSecondSweepMs" -> Pair("Second sweep duration (opposite direction) when searching.", "1000 - 4000 ms")
        "visionAlignSearchSpeed" -> Pair("Rotational sweep velocity when searching for lost AprilTag.", "0.3 - 1.0")
        "telemetryRateDivisor" -> Pair("Network tables telemetry streaming frame rate divisor.", "1 - 10 (1 = full speed, 3 = default)")
        "motorCurrentPollingIntervalMs" -> Pair("Background motor current polling sleep interval duration.", "20 - 150 ms")
        "intakeNominalVoltage" -> Pair("Nominal voltage applied to active intake rollers.", "6.0 - 12.0 V")
        "flywheelTargetRpmPreset" -> Pair("Target RPM preset for active flywheel shooter.", "1000.0 - 4000.0 RPM")
        "driverTriggerThreshold" -> Pair("Joystick trigger analog threshold to feed game piece.", "0.2 - 0.8")
        else -> Pair("Tunable parameter configuration constant.", "Varies")
    }
}
