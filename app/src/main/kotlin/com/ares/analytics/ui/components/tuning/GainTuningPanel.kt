package com.ares.analytics.ui.components.tuning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.TuningIntent
import com.ares.analytics.viewmodel.TuningViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GainTuningPanel(
    viewModel: TuningViewModel,
    state: com.ares.analytics.viewmodel.TuningState,
    modifier: Modifier = Modifier
) {
    val allKeys = (state.variables.keys + state.appVariables.keys).toSet()
    val syncedCount = allKeys.count { k ->
        val r = state.variables[k]
        val a = state.appVariables[k]
        r != null && a != null && kotlin.math.abs(r - a) < 1e-5
    }
    val diffCount = allKeys.count { k ->
        val r = state.variables[k]
        val a = state.appVariables[k]
        r != null && a != null && kotlin.math.abs(r - a) >= 1e-5
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row with Global Batch Sync Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Constants Tuning Board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                if (allKeys.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (diffCount == 0 && syncedCount > 0) AresGreen else AresAmber, RoundedCornerShape(3.dp))
                        )
                        Text(
                            text = if (diffCount == 0 && syncedCount > 0) "All $syncedCount Constants Synced" else "$diffCount Out of Sync ($syncedCount Synced)",
                            fontSize = 11.sp,
                            color = if (diffCount == 0 && syncedCount > 0) AresGreen else AresAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            var showBackupMenu by remember { mutableStateOf(false) }

            // Global Push / Pull / Backup Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onIntent(TuningIntent.PushAllToRobot) },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Push All -> Robot", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.onIntent(TuningIntent.PullAllFromRobot) },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AresBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = AresTextPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("<- Pull All to App", color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                FilterChip(
                    selected = state.isAutoSaveEnabled,
                    onClick = { viewModel.onIntent(TuningIntent.ToggleAutoSave) },
                    label = { Text(if (state.isAutoSaveEnabled) "⚡ Auto-Sync ON" else "Manual Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AresCyan.copy(alpha = 0.2f),
                        selectedLabelColor = AresCyan,
                        containerColor = AresSurfaceElevated,
                        labelColor = AresTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = AresBorder,
                        selectedBorderColor = AresCyan,
                        enabled = true,
                        selected = state.isAutoSaveEnabled
                    ),
                    modifier = Modifier.height(32.dp)
                )

                OutlinedButton(
                    onClick = { viewModel.onIntent(TuningIntent.CreateBackup) },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AresBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Save Backup", color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Box {
                    OutlinedButton(
                        onClick = {
                            viewModel.onIntent(TuningIntent.RefreshBackups)
                            showBackupMenu = true
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AresBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Load Backup", color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    DropdownMenu(
                        expanded = showBackupMenu,
                        onDismissRequest = { showBackupMenu = false },
                        modifier = Modifier.background(AresSurfaceElevated).border(1.dp, AresBorder)
                    ) {
                        if (state.availableBackups.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No backups found", color = AresTextTertiary, fontSize = 11.sp) },
                                onClick = { showBackupMenu = false }
                            )
                        } else {
                            state.availableBackups.forEach { backup ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(backup.formattedDate, color = AresCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("${backup.count} constants • ${backup.filename}", color = AresTextSecondary, fontSize = 10.sp)
                                        }
                                    },
                                    onClick = {
                                        viewModel.onIntent(TuningIntent.LoadBackup(backup.filename))
                                        showBackupMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
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

        if (allKeys.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Waiting for live Tuning constants from Robot over NT4 or local project JSON...", color = AresTextTertiary, fontSize = 12.sp)
            }
        } else {
            // Group by custom categories
            val grouped = allKeys.groupBy { getCustomCategory(it) }

            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(minSize = 360.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
            ) {
                items(grouped.entries.toList().sortedBy { it.key }) { (category, keys) ->
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

                        // Column Headers: Name | App JSON | Sync | Robot NT4
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("VARIABLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AresTextTertiary, modifier = Modifier.weight(1f))
                            Text("APP (JSON)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AresCyan, modifier = Modifier.width(75.dp))
                            Text("SYNC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AresTextTertiary, modifier = Modifier.width(50.dp))
                            Text("ROBOT (NT4)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AresTextSecondary, modifier = Modifier.width(75.dp))
                        }
                        HorizontalDivider(color = AresBorder.copy(alpha = 0.5f))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            keys.sorted().forEach { constKey ->
                                val parts = constKey.removePrefix("Tuning/").split("/")
                                val displayName = if (parts.size > 1) parts.drop(1).joinToString("/") else parts[0]

                                val robotVal = state.variables[constKey]
                                val appVal = state.appVariables[constKey]

                                val isSynced = robotVal != null && appVal != null && kotlin.math.abs(robotVal - appVal) < 1e-5
                                val isDiff = robotVal != null && appVal != null && kotlin.math.abs(robotVal - appVal) >= 1e-5

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                                modifier = Modifier.fillMaxWidth().clickable(onClick = {})
                                            )
                                        }
                                    }

                                    // App Value Input Field (Local JSON)
                                    var appText by remember(appVal) { mutableStateOf(appVal?.toString() ?: "") }
                                    BasicTextField(
                                        value = appText,
                                        onValueChange = { newText ->
                                            appText = newText
                                            newText.toDoubleOrNull()?.let { v ->
                                                viewModel.onIntent(TuningIntent.UpdateAppConstant(constKey, v))
                                            }
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = AresTextPrimary, fontWeight = FontWeight.Bold),
                                        cursorBrush = SolidColor(AresCyan),
                                        modifier = Modifier
                                            .width(75.dp)
                                            .height(30.dp)
                                            .background(AresSurface, RoundedCornerShape(6.dp))
                                            .border(1.dp, if (isDiff) AresAmber else AresBorder, RoundedCornerShape(6.dp)),
                                        decorationBox = { innerTextField ->
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    if (appText.isEmpty()) {
                                                        Text("--", fontSize = 11.sp, color = AresTextTertiary)
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        }
                                    )

                                    // Sync Transfer Controls: [Push ->] [Dot] [<- Pull]
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.width(50.dp)
                                    ) {
                                        // Push App -> Robot
                                        IconButton(
                                            onClick = { viewModel.onIntent(TuningIntent.PushToRobot(constKey)) },
                                            modifier = Modifier.size(20.dp).background(AresCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Push to Robot", tint = AresCyan, modifier = Modifier.size(12.dp))
                                        }

                                        // Status dot
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    when {
                                                        isSynced -> AresGreen
                                                        isDiff -> AresAmber
                                                        else -> AresTextTertiary
                                                    },
                                                    RoundedCornerShape(3.dp)
                                                )
                                        )

                                        // Pull Robot -> App
                                        IconButton(
                                            onClick = { viewModel.onIntent(TuningIntent.PullFromRobot(constKey)) },
                                            modifier = Modifier.size(20.dp).background(AresSurface, RoundedCornerShape(4.dp)).border(1.dp, AresBorder, RoundedCornerShape(4.dp))
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Pull from Robot", tint = AresTextSecondary, modifier = Modifier.size(12.dp))
                                        }
                                    }

                                    // Robot Live Value Display (NT4)
                                    Box(
                                        modifier = Modifier
                                            .width(75.dp)
                                            .height(30.dp)
                                            .background(AresSurface, RoundedCornerShape(6.dp))
                                            .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = robotVal?.let { String.format("%.4f", it).trimEnd('0').trimEnd('.') } ?: "--",
                                            fontSize = 11.sp,
                                            color = if (robotVal != null) AresCyan else AresTextTertiary,
                                            fontWeight = FontWeight.SemiBold
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

private fun getCustomCategory(key: String): String {
    val cleanKey = key.removePrefix("Tuning/")
    val parts = cleanKey.split("/")
    if (parts.size > 1) {
        return when (parts[0]) {
            "drive" -> when (parts.getOrNull(1)) {
                "pathTranslationGains" -> "Path Translation PID"
                "pathRotationGains" -> "Path Rotation PID"
                "headingGains" -> "Heading Lock PID"
                "driveFeedforward" -> "Linear Feedforward"
                "angularFeedforward" -> "Angular Feedforward"
                "ftc" -> "FTC Drivetrain"
                else -> "Drivetrain"
            }
            "localization" -> "Odometry & Localization"
            "vision" -> "Limelight Vision"
            "driver" -> "Driver Profile"
            "subsystem" -> "Mechanism Tuning"
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
        "intakeNominalVoltage" -> Pair("Nominal voltage applied to intake motors.", "8.0 - 12.0 V")
        else -> Pair("No description available.", "Unknown")
    }
}
