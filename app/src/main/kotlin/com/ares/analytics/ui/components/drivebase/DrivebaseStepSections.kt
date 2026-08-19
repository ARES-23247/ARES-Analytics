package com.ares.analytics.ui.components.drivebase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.drivebase.*
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.drivebase.*

@Composable
fun DriveTypeStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("1 · Choose how the robot moves", "Select your drive kinematics archetype. ARES rebuilds the editable configuration draft accordingly.")
    val cards = listOf(
        Triple(DrivebaseKind.FTC_MECANUM, "FTC Mecanum", "Four angled rollers allow omnidirectional forward, strafe, and turning motion."),
        Triple(DrivebaseKind.FRC_CTRE_SWERVE, "FRC CTRE Swerve", "Four independently steering and driving modules; supports read-only TunerConstants import."),
        Triple(DrivebaseKind.DIFFERENTIAL, "Differential / Tank", "Left and right wheel groups drive like a tank; no sideways strafing motion."),
        Triple(DrivebaseKind.CUSTOM, "Advanced / Custom", "Start with an example motor and gyro, then declare, configure, and classify custom hardware.")
    ).filter { (kind, _, _) -> kind in drivebaseKindsForLeague(state.league) }

    cards.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (kind, title, explanation) ->
                val isSelected = state.draft.kind == kind
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AresCyan else AresBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { viewModel.onIntent(DrivebaseBuilderIntent.SelectKind(kind)) },
                    color = if (isSelected) AresCyan.copy(alpha = 0.10f) else AresSurface,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (kind.runtimeSupport(state.league) == DrivebaseRuntimeSupport.NO_CODE_RUNNABLE) AresGreen.copy(alpha = 0.15f) else AresGold.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    kind.runtimeSupportLabel(state.league),
                                    color = if (kind.runtimeSupport(state.league) == DrivebaseRuntimeSupport.NO_CODE_RUNNABLE) AresGreen else AresGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(explanation, color = AresTextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                        Text(
                            if (isSelected) "● SELECTED" else "Choose this drive",
                            color = if (isSelected) AresCyan else AresTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (state.draft.kind == DrivebaseKind.FRC_CTRE_SWERVE) CtreImportCard(state, viewModel)
}

@Composable
fun HardwareStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("2 · Identify hardware", "Configure the 4 drive corner motors and any auxiliary sensors or odometry pods.")

    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 440.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Left Column (38%): Interactive 2D Chassis Visualizer
        Surface(
            modifier = Modifier.weight(0.38f),
            color = AresSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("TOP-DOWN CHASSIS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AresCyan.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            "FRONT ▲",
                            color = AresCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                InteractiveChassisCanvas(
                    state = state,
                    onSelectHardware = { id -> viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(id)) },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(AresGreen, androidx.compose.foundation.shape.CircleShape))
                        Text("Normal Direction", color = AresTextSecondary, fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(AresError, androidx.compose.foundation.shape.CircleShape))
                        Text("Inverted Direction", color = AresTextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }

        // Right Column (62%): 2x2 Motor Layout Grid + Auxiliary Hardware & Sensors
        Surface(
            modifier = Modifier.weight(0.62f),
            color = AresSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("DRIVE MOTORS (2×2 PHYSICAL LAYOUT)", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("FTC hardware-map names: fl, fr, rl, rr", color = AresTextTertiary, fontSize = 10.sp)
                    }
                }

                val cornerHardware = state.draft.cornerDriveHardware()
                val fl = cornerHardware.getOrNull(0)
                val fr = cornerHardware.getOrNull(1)
                val rl = cornerHardware.getOrNull(2)
                val rr = cornerHardware.getOrNull(3)

                // Front Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MotorGridCard(
                        cornerCode = "FL",
                        defaultCornerName = "Front-Left",
                        device = fl,
                        isSelected = fl?.id == state.selectedHardwareId,
                        onSelect = { fl?.id?.let { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(it)) } },
                        onToggleInvert = { fl?.let { dev -> viewModel.onIntent(DrivebaseBuilderIntent.UpdateHardware(dev.copy(inverted = !dev.inverted))) } },
                        modifier = Modifier.weight(1f),
                    )
                    MotorGridCard(
                        cornerCode = "FR",
                        defaultCornerName = "Front-Right",
                        device = fr,
                        isSelected = fr?.id == state.selectedHardwareId,
                        onSelect = { fr?.id?.let { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(it)) } },
                        onToggleInvert = { fr?.let { dev -> viewModel.onIntent(DrivebaseBuilderIntent.UpdateHardware(dev.copy(inverted = !dev.inverted))) } },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Rear Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MotorGridCard(
                        cornerCode = "RL",
                        defaultCornerName = "Rear-Left",
                        device = rl,
                        isSelected = rl?.id == state.selectedHardwareId,
                        onSelect = { rl?.id?.let { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(it)) } },
                        onToggleInvert = { rl?.let { dev -> viewModel.onIntent(DrivebaseBuilderIntent.UpdateHardware(dev.copy(inverted = !dev.inverted))) } },
                        modifier = Modifier.weight(1f),
                    )
                    MotorGridCard(
                        cornerCode = "RR",
                        defaultCornerName = "Rear-Right",
                        device = rr,
                        isSelected = rr?.id == state.selectedHardwareId,
                        onSelect = { rr?.id?.let { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(it)) } },
                        onToggleInvert = { rr?.let { dev -> viewModel.onIntent(DrivebaseBuilderIntent.UpdateHardware(dev.copy(inverted = !dev.inverted))) } },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Auxiliary Hardware List & Sensor Creator
                val cornerIds = cornerHardware.mapNotNull { it?.id }.toSet()
                val auxHardware = state.draft.hardware.filterNot { it.id in cornerIds }
                
                HorizontalDivider(color = AresBorder)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("AUXILIARY SENSORS & CAMERAS (${auxHardware.size})", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Pinpoint odometry, Limelight cameras, IMUs, and distance sensors", color = AresTextTertiary, fontSize = 10.sp)
                    }
                    var addMenu by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { addMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                            modifier = Modifier.height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Sensor / Camera", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(addMenu, { addMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("📷 Limelight / Vision Camera", fontSize = 11.sp) },
                                onClick = {
                                    addMenu = false
                                    viewModel.onIntent(DrivebaseBuilderIntent.AddHardware(DriveHardwareRole.LIMELIGHT))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📍 goBILDA Pinpoint / Odometry Pod", fontSize = 11.sp) },
                                onClick = {
                                    addMenu = false
                                    viewModel.onIntent(DrivebaseBuilderIntent.AddHardware(DriveHardwareRole.ODOMETRY))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🧭 Control Hub IMU / Gyroscope", fontSize = 11.sp) },
                                onClick = {
                                    addMenu = false
                                    viewModel.onIntent(DrivebaseBuilderIntent.AddHardware(DriveHardwareRole.GYRO))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📏 Laser Distance Sensor (ToF / REV 2m)", fontSize = 11.sp) },
                                onClick = {
                                    addMenu = false
                                    viewModel.onIntent(DrivebaseBuilderIntent.AddHardware(DriveHardwareRole.DISTANCE_SENSOR))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⚙️ Custom Sensor / Expansion Device", fontSize = 11.sp) },
                                onClick = {
                                    addMenu = false
                                    viewModel.onIntent(DrivebaseBuilderIntent.AddHardware(DriveHardwareRole.OTHER))
                                }
                            )
                        }
                    }
                }

                if (auxHardware.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = AresSurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AresBorder)
                    ) {
                        Text(
                            "No auxiliary sensors configured. Click '+ Add Sensor / Camera' above to add Pinpoint odometry, Limelights, or distance sensors.",
                            color = AresTextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        auxHardware.forEach { device ->
                            AuxHardwareRow(
                                device = device,
                                isSelected = device.id == state.selectedHardwareId,
                                onSelect = { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(device.id)) },
                                onToggleInvert = { viewModel.onIntent(DrivebaseBuilderIntent.UpdateHardware(device.copy(inverted = !device.inverted))) },
                                onRemove = { viewModel.onIntent(DrivebaseBuilderIntent.RemoveHardware(device.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeometryStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("3 · Measure geometry", "Use meters internally. Wheelbase and track width are center-to-center distances; robot dimensions include frame perimeter.")
    val geometry = state.draft.geometry
    val labResult = evaluateGeometryLab(
        geometry = geometry,
        linearCommand = 1.0,
        angularCommand = 0.0,
        configuredMaxLinearSpeedMps = state.draft.safety.maxLinearSpeedMetersPerSecond,
        useCornerModuleRadius = state.draft.kind == DrivebaseKind.FRC_CTRE_SWERVE
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Left Column (50%): Physical Measurements
        Surface(
            modifier = Modifier.weight(1f),
            color = AresSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("PHYSICAL MEASUREMENTS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                GeometryField("Wheel radius", geometry.wheelRadiusMeters, "m", "Measure from axle center to floor under normal robot weight.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateGeometry(geometry.copy(wheelRadiusMeters = it))) }
                GeometryField("Track width", geometry.trackWidthMeters, "m", "Center-to-center distance between left and right wheel contact lines.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateGeometry(geometry.copy(trackWidthMeters = it))) }
                GeometryField("Wheelbase", geometry.wheelBaseMeters, "m", "Center-to-center distance between front and rear wheel contact lines.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateGeometry(geometry.copy(wheelBaseMeters = it))) }
            }
        }

        // Right Column (50%): Derived Kinematic Limits & Aspect Ratio
        Surface(
            modifier = Modifier.weight(1f),
            color = AresSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("CONFIGURED KINEMATIC LIMITS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(Modifier.weight(1f), color = AresSurfaceElevated, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, AresBorder)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Linear Limit", color = AresTextSecondary, fontSize = 10.sp)
                            Text(labResult.maxLinearSpeedMps?.let { "%.2f m/s".format(it) } ?: "Not configured", color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("≈ ${"%.1f".format((labResult.maxLinearSpeedMps ?: 0.0) * 3.28084)} ft/s", color = AresTextTertiary, fontSize = 10.sp)
                        }
                    }
                    Surface(Modifier.weight(1f), color = AresSurfaceElevated, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, AresBorder)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Max Angular Rate", color = AresTextSecondary, fontSize = 10.sp)
                            Text("${"%.1f".format(labResult.maxAngularSpeedRadPerSec ?: 0.0)} rad/s", color = AresGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("≈ ${"%.0f".format((labResult.maxAngularSpeedRadPerSec ?: 0.0) * 180.0 / Math.PI)}°/s", color = AresTextTertiary, fontSize = 10.sp)
                        }
                    }
                }
                val ratio = if (geometry.trackWidthMeters > 0.01) geometry.wheelBaseMeters / geometry.trackWidthMeters else 1.0
                Surface(Modifier.fillMaxWidth(), color = AresSurfaceElevated, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, AresBorder)) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Aspect Ratio (L/W)", color = AresTextSecondary, fontSize = 10.sp)
                            Text("%.2f".format(ratio), color = if (ratio in 0.7..1.4) AresTextPrimary else AresGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (ratio in 0.7..1.4) AresGreen.copy(alpha = 0.15f) else AresGold.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (ratio in 0.7..1.4) AresGreen else AresGold),
                        ) {
                            Text(
                                if (ratio in 0.7..1.4) "Balanced Turning" else "High Scrub Risk",
                                color = if (ratio in 0.7..1.4) AresGreen else AresGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text("Overall frame perimeter & bumper geometry belong to the robot identity contract.", color = AresTextTertiary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun LocalizationStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("4 · Choose localization", "Localization estimates robot pose on the field. Multiple sources can be fused with CCW-positive standard.")
    val kinds = LocalizationKind.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { kind ->
                    val isChecked = kind in state.draft.localization
                    val description = when (kind) {
                        LocalizationKind.FTC_PINPOINT -> "goBILDA Pinpoint odometry computer; CCW-positive normalized."
                        LocalizationKind.WHEEL_ODOMETRY_GYRO -> "Wheel deadwheel encoders plus internal IMU gyro."
                        LocalizationKind.CTRE_POSE_ESTIMATOR -> "CTRE swerve module and Pigeon observations."
                        LocalizationKind.VISION_FUSION -> "AprilTag vision corrections with Mahalanobis gating."
                        LocalizationKind.CUSTOM -> "Team-maintained custom estimator adapter."
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isChecked) 1.5.dp else 1.dp,
                                color = if (isChecked) AresCyan else AresBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.onIntent(DrivebaseBuilderIntent.SetLocalization(kind, !isChecked)) },
                        color = if (isChecked) AresCyan.copy(alpha = 0.08f) else AresSurface,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    kind.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase),
                                    color = AresTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                                Text(description, color = AresTextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { viewModel.onIntent(DrivebaseBuilderIntent.SetLocalization(kind, it)) },
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SafetyAndReviewStep(
    state: DrivebaseBuilderState,
    viewModel: DrivebaseBuilderViewModel,
    onContinueToSubsystems: (() -> Unit)? = null,
    onBackToStudio: (() -> Unit)? = null,
) {
    SectionHeading("5 · Safety rules & save review", "Review fail-closed safety contracts, validate the draft diff, and save the canonical drivebase.")
    val safety = state.draft.safety
    val review = state.saveReview
    val noCodeRunnable = state.draft.kind.runtimeSupport(state.league) == DrivebaseRuntimeSupport.NO_CODE_RUNNABLE

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Safety Interlocks & Limits (2 columns)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = AresSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AresBorder),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("FAIL-CLOSED SAFETY INTERLOCKS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    SafetySwitch("Safe neutral required", safety.safeNeutralRequired, "Outputs become neutral at startup, disable, stop, and fault.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(safeNeutralRequired = it))) }
                    SafetySwitch("Configuration health required", safety.configurationHealthRequired, "Nonzero motion is blocked until all required devices report healthy configuration.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(configurationHealthRequired = it))) }
                    SafetySwitch("Explicit neutral recovery", safety.explicitNeutralRecoveryRequired, "Motion resumes only after a successful neutral write is confirmed.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(explicitNeutralRecoveryRequired = it))) }
                    SafetySwitch("Current monitoring required", safety.currentMonitoringRequired, "Monitoring must report validity for continuous current protection.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(currentMonitoringRequired = it))) }
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                color = AresSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AresBorder),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("COMMAND ENVELOPE & LIMITS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    GeometryField("Feedback freshness timeout", safety.feedbackFreshnessTimeoutMs.toDouble(), "ms", "Feedback older than this is stale and blocks closed-loop output.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(feedbackFreshnessTimeoutMs = it.toInt()))) }
                    GeometryField("Maximum linear speed", safety.maxLinearSpeedMetersPerSecond, "m/s", "Hard command envelope used by control and simulation.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(maxLinearSpeedMetersPerSecond = it))) }
                    GeometryField("Maximum angular speed", safety.maxAngularSpeedRadiansPerSecond, "rad/s", "Positive rotation is counter-clockwise.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(maxAngularSpeedRadiansPerSecond = it))) }
                }
            }
        }

        // Structured Diff / Save Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AresSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DOCUMENT REVIEW & DIFF", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)

                if (review == null) {
                    if (state.dirty) {
                        Text("Select Review Changes to validate the draft against ARES rules and generate a content-hash-bound structured diff.", color = AresTextSecondary, fontSize = 11.sp)
                        Button(
                            onClick = { viewModel.onIntent(DrivebaseBuilderIntent.ReviewSave) },
                            enabled = noCodeRunnable,
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                        ) {
                            Text(if (noCodeRunnable) "Create Reviewed Diff" else "Code Required Before Save", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AresGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AresGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "✓ Saved · The canonical drivebase document already matches this form.",
                                color = AresGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (onContinueToSubsystems != null) {
                                Button(
                                    onClick = onContinueToSubsystems,
                                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                                ) {
                                    Text("Next: Mechanism Subsystems →", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (onBackToStudio != null) {
                                OutlinedButton(onClick = onBackToStudio) {
                                    Text("Return to Robot Studio")
                                }
                            }
                        }
                    }
                } else {
                    Text("The following changes will be written to .ares/drivetrains:", color = AresTextPrimary, fontSize = 11.sp)
                    review.changes.forEach { change ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AresSurfaceElevated,
                            border = BorderStroke(1.dp, AresBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(change.path, color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Before: ${change.before}", color = AresTextSecondary, fontSize = 10.sp)
                                Text("After:  ${change.after}", color = AresTextPrimary, fontSize = 10.sp)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.onIntent(DrivebaseBuilderIntent.ConfirmSave(review.confirmationToken)) },
                            colors = ButtonDefaults.buttonColors(containerColor = AresGreen, contentColor = AresOnAccent),
                        ) {
                            Text("Confirm & Save", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { viewModel.onIntent(DrivebaseBuilderIntent.Reload) }) {
                            Text("Discard Changes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareEditor(
    device: DriveHardwareDeclaration,
    hardware: List<DriveHardwareDeclaration>,
    advanced: Boolean,
    onUpdate: (DriveHardwareDeclaration) -> Unit,
    onRemove: () -> Unit,
) {
    Text(device.displayName, color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Text(device.role.name.lowercase().replace('_', ' '), color = AresTextSecondary, fontSize = 10.sp)
    HelpedTextField("Display name", device.displayName, "A student-facing label. It does not change the stable device ID.") { onUpdate(device.copy(displayName = it)) }
    HelpedTextField("Hardware-map name", device.hardwareName, "The exact configured name used by FTC or a named FRC device.") { onUpdate(device.copy(hardwareName = it)) }
    if (advanced) {
        var roleMenu by remember(device.id) { mutableStateOf(false) }
        Box {
            OutlinedButton({ roleMenu = true }, Modifier.fillMaxWidth()) { Text("Role · ${device.role.name.lowercase().replace('_', ' ')}") }
            DropdownMenu(roleMenu, { roleMenu = false }) {
                DriveHardwareRole.entries.forEach { role ->
                    DropdownMenuItem({ Text(role.name.lowercase().replace('_', ' ')) }, {
                        roleMenu = false
                        onUpdate(device.copy(role = role, leaderId = if (role in setOf(DriveHardwareRole.LEFT_FOLLOWER, DriveHardwareRole.RIGHT_FOLLOWER)) device.leaderId else null))
                    })
                }
            }
        }
    }
    if (device.role in setOf(DriveHardwareRole.LEFT_FOLLOWER, DriveHardwareRole.RIGHT_FOLLOWER)) {
        var leaderMenu by remember(device.id) { mutableStateOf(false) }
        val leaders = hardware.filter { it.id != device.id && it.role in setOf(DriveHardwareRole.LEFT_LEADER, DriveHardwareRole.RIGHT_LEADER, DriveHardwareRole.DRIVE_MOTOR) }
        Box {
            OutlinedButton({ leaderMenu = true }, Modifier.fillMaxWidth()) { Text("Leader · ${device.leaderId ?: "Choose a leader"}") }
            DropdownMenu(leaderMenu, { leaderMenu = false }) {
                leaders.forEach { leader -> DropdownMenuItem({ Text(leader.displayName) }, { leaderMenu = false; onUpdate(device.copy(leaderId = leader.id)) }) }
            }
        }
        Text("Follower inversion below is relative to the leader and remains independent from the leader's own mounting inversion.", color = AresTextSecondary, fontSize = 9.sp)
    }
    if (advanced || device.canId != null) {
        HelpedTextField("CAN ID", device.canId?.toString().orEmpty(), "The unique numeric CAN address. Valid ARES range: 0–62.") { onUpdate(device.copy(canId = it.toIntOrNull())) }
        HelpedTextField("CAN bus", device.canBus.orEmpty(), "The named CAN network, for example rio or CANivore name.") { onUpdate(device.copy(canBus = it.ifBlank { null })) }
    }
    val isAuxiliaryOrSensors = device.role in setOf(
        DriveHardwareRole.ODOMETRY,
        DriveHardwareRole.LIMELIGHT,
        DriveHardwareRole.DISTANCE_SENSOR,
        DriveHardwareRole.GYRO,
        DriveHardwareRole.OTHER,
        DriveHardwareRole.CUSTOM
    )
    val isVisionCamera = device.role == DriveHardwareRole.LIMELIGHT

    if (isAuxiliaryOrSensors || advanced) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PHYSICAL MOUNTING POSITION (3D TRANSLATION)", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            GeometryField(
                label = "X Mounting Offset",
                value = device.xMeters ?: 0.0,
                unit = "m",
                explanation = "Longitudinal offset forward (+) or backward (-) from robot center of rotation. Required for Pinpoint turning arc compensation and 3D camera pose estimation."
            ) { onUpdate(device.copy(xMeters = it)) }
            GeometryField(
                label = "Y Mounting Offset",
                value = device.yMeters ?: 0.0,
                unit = "m",
                explanation = "Lateral offset left (+) or right (-) from robot center of rotation. Required for Pinpoint turning arc compensation and 3D camera pose estimation."
            ) { onUpdate(device.copy(yMeters = it)) }
            GeometryField(
                label = "Z Mounting Height",
                value = device.zMeters ?: 0.0,
                unit = "m",
                explanation = "Vertical height from the floor / ground plane up to the sensor or camera optical center."
            ) { onUpdate(device.copy(zMeters = it)) }
        }

        if (isVisionCamera || advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CAMERA ORIENTATION & ANGLES (3D ROTATION)", color = AresCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                GeometryField(
                    label = "Camera Pitch",
                    value = device.pitchDegrees ?: 0.0,
                    unit = "°",
                    explanation = "Tilt angle above (+) or below (-) the horizontal horizon. Crucial for MegaTag2 / AprilTag vertical angle solving."
                ) { onUpdate(device.copy(pitchDegrees = it)) }
                GeometryField(
                    label = "Camera Yaw",
                    value = device.yawDegrees ?: 0.0,
                    unit = "°",
                    explanation = "Horizontal angle: facing straight ahead (0°), facing left (+90°), facing right (-90°), facing rear (180°)."
                ) { onUpdate(device.copy(yawDegrees = it)) }
                GeometryField(
                    label = "Camera Roll",
                    value = device.rollDegrees ?: 0.0,
                    unit = "°",
                    explanation = "Rotation / tilt of the camera around its own optical lens axis (clockwise/counter-clockwise)."
                ) { onUpdate(device.copy(rollDegrees = it)) }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(device.inverted, { onUpdate(device.copy(inverted = it)) })
        Spacer(Modifier.width(8.dp))
        Text(if (device.inverted) "INVERTED direction" else "NORMAL direction", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        HelpButton("Mounting inversion changes the sign at the hardware boundary.")
    }

    if (isAuxiliaryOrSensors || advanced) {
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AresError),
            border = BorderStroke(1.dp, AresError.copy(alpha = 0.4f)),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = AresError)
            Spacer(Modifier.width(6.dp))
            Text("Remove this device", color = AresError, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CtreImportCard(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AresSurface, RoundedCornerShape(8.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FieldHeading("Optional CTRE TunerConstants import", "ARES reads a snapshot of vendor-generated constants for review. It never edits, formats, or overwrites TunerConstants.java.")
        OutlinedTextField(state.importPath, { viewModel.onIntent(DrivebaseBuilderIntent.SetImportPath(it)) }, Modifier.fillMaxWidth(), label = { Text("TunerConstants.java path") }, singleLine = true)
        Button(onClick = { viewModel.onIntent(DrivebaseBuilderIntent.ImportCtre) }, enabled = state.importPath.isNotBlank()) { Text("Import read-only snapshot") }
        Text("Import support fails closed when typed units, module positions, CAN bus, IDs, or inversion cannot be recognized. Review every imported field.", color = AresGold, fontSize = 10.sp)
        state.importWarnings.forEach { Text("• $it", color = AresGold, fontSize = 10.sp) }
    }
}

@Composable
fun SectionHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(description, color = AresTextSecondary, fontSize = 11.sp)
    }
}

@Composable
fun FieldHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(description, color = AresTextSecondary, fontSize = 10.sp)
    }
}

@Composable
fun GeometryField(label: String, value: Double, unit: String, explanation: String, onValueChange: (Double) -> Unit) {
    var raw by remember(value) { mutableStateOf(value.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            HelpButton(explanation)
        }
        OutlinedTextField(
            value = raw,
            onValueChange = {
                raw = it
                it.toDoubleOrNull()?.let(onValueChange)
            },
            trailingIcon = { Text(unit, color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
fun SafetySwitch(label: String, checked: Boolean, explanation: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(explanation, color = AresTextSecondary, fontSize = 10.sp)
        }
        Switch(checked, onCheckedChange)
    }
}

@Composable
fun HelpedTextField(label: String, value: String, explanation: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            HelpButton(explanation)
        }
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@Composable
fun HelpButton(text: String) {
    var showDialog by remember { mutableStateOf(false) }
    IconButton(onClick = { showDialog = true }, modifier = Modifier.size(20.dp)) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help", tint = AresTextSecondary, modifier = Modifier.size(13.dp))
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("OK") } },
            text = { Text(text, color = AresTextPrimary, fontSize = 12.sp) },
        )
    }
}
