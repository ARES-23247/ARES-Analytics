package com.ares.analytics.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areslib.logging.SensoryReplayRunner
import com.areslib.logging.ReplaySummary
import com.areslib.telemetry.ReplayPublisher
import com.areslib.math.Vector3
import com.areslib.math.Pose3d
import com.areslib.math.Translation3d
import com.areslib.math.Rotation3d
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.concurrent.thread

@Composable
fun ReplayScreen() {
    var logLines by remember { mutableStateOf(generateSyntheticLog()) }
    var fileName by remember { mutableStateOf("Synthetic Preloaded EKF Log") }

    // Sliders for dynamic EKF tuning
    var visionStdDevX by remember { mutableStateOf(0.05f) }
    var visionStdDevY by remember { mutableStateOf(0.05f) }
    var visionStdDevHeading by remember { mutableStateOf(0.10f) }

    var showFov by remember { mutableStateOf(true) }

    // Recalculated state
    val replaySummary by remember(logLines, visionStdDevX, visionStdDevY, visionStdDevHeading) {
        derivedStateOf {
            val customWeights = Vector3(
                visionStdDevX.toDouble(),
                visionStdDevY.toDouble(),
                visionStdDevHeading.toDouble()
            )
            try {
                SensoryReplayRunner.replaySensoryLines(logLines, customWeights)
            } catch (e: Exception) {
                // Return empty summary on parsing error
                ReplaySummary(emptyList(), com.areslib.math.Pose2d(), com.areslib.math.Pose2d())
            }
        }
    }

    var isStreaming by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Controls (Width 320.dp)
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ARES REPLAY ENGINE",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Students EKF Covariance Tuning Dashboard",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Active Log: $fileName",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // File Loading & Action Button Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val chooser = JFileChooser().apply {
                                fileFilter = FileNameExtensionFilter("JSONL Sensory Logs (*.jsonl)", "jsonl")
                                dialogTitle = "Open Raw ARES Sensory Log"
                            }
                            val result = chooser.showOpenDialog(null)
                            if (result == JFileChooser.APPROVE_OPTION) {
                                val selectedFile = chooser.selectedFile
                                logLines = selectedFile.readLines()
                                fileName = selectedFile.name
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("LOAD LOG FILE (.JSONL)", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            isStreaming = true
                            thread {
                                try {
                                    val publisher = ReplayPublisher()
                                    publisher.publishReplay(replaySummary, speedMultiplier = 1.0)
                                } finally {
                                    isStreaming = false
                                }
                            }
                        },
                        enabled = !isStreaming && replaySummary.steps.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isStreaming) "STREAMING TO ADVANTAGESCOPE..." else "STREAM TO ADVANTAGESCOPE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Camera FOV Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CAMERA FOV VISUALIZER",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = showFov,
                            onCheckedChange = { showFov = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show FOV Outlines", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }

            // Tuning Sliders Card
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Extended Kalman Filter Sweep",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    // Slider X
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vision Std Dev X", color = Color.LightGray, fontSize = 12.sp)
                            Text(String.format("%.3f m", visionStdDevX), color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = visionStdDevX,
                            onValueChange = { visionStdDevX = it },
                            valueRange = 0.001f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )
                    }

                    // Slider Y
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vision Std Dev Y", color = Color.LightGray, fontSize = 12.sp)
                            Text(String.format("%.3f m", visionStdDevY), color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = visionStdDevY,
                            onValueChange = { visionStdDevY = it },
                            valueRange = 0.001f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )
                    }

                    // Slider Heading
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vision Std Dev Heading", color = Color.LightGray, fontSize = 12.sp)
                            Text(String.format("%.3f rad", visionStdDevHeading), color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = visionStdDevHeading,
                            onValueChange = { visionStdDevHeading = it },
                            valueRange = 0.001f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Dynamic Metrics box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0E14), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("REPLAY COMPLETED SUCCESSFUL", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Data Frames: ${replaySummary.steps.size}", color = Color.LightGray, fontSize = 11.sp)
                            if (replaySummary.steps.isNotEmpty()) {
                                val finalReal = replaySummary.finalRealPose
                                val finalGhost = replaySummary.finalGhostPose
                                Text(String.format("Logged Pose: (%.2f, %.2f)", finalReal.x, finalReal.y), color = Color.LightGray, fontSize = 11.sp)
                                Text(String.format("Ghost Pose: (%.2f, %.2f)", finalGhost.x, finalGhost.y), color = Color(0xFFFF1744), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Right Main Canvas Area: 2D Field Path Visualizer
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF10131E),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Interactive 2D Field Coordinate View",
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Canvas Drawing Area
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(16.541f / 8.069f)
                        .background(Color(0xFF0A0C12), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                ) {
                    val width = size.width
                    val height = size.height

                    // 1. Draw Field Grid Lines (every 2 meters)
                    for (xMeter in 2..16 step 2) {
                        val px = ((xMeter / 16.541) * (width * 0.9) + (width * 0.05)).toFloat()
                        drawLine(Color(0x0FFFFFFF), Offset(px, 0f), Offset(px, height), strokeWidth = 1f)
                    }
                    for (yMeter in 2..8 step 2) {
                        val py = (height - ((yMeter / 8.069) * (height * 0.9) + (height * 0.05))).toFloat()
                        drawLine(Color(0x0FFFFFFF), Offset(0f, py), Offset(width, py), strokeWidth = 1f)
                    }

                    // FRC Midfield Line (8.27m) in a slightly brighter color
                    val midPx = ((8.27 / 16.541) * (width * 0.9) + (width * 0.05)).toFloat()
                    drawLine(Color(0x22FFFFFF), Offset(midPx, 0f), Offset(midPx, height), strokeWidth = 2f)

                    // Mapping coordinates from [0..16.541] for X and [0..8.069] for Y meters to pixels
                    fun mapToPixel(meterX: Double, meterY: Double): Offset {
                        val px = ((meterX / 16.541) * (width * 0.9) + (width * 0.05)).toFloat()
                        // Invert Y for screen coordinates
                        val py = (height - ((meterY / 8.069) * (height * 0.9) + (height * 0.05))).toFloat()
                        return Offset(px, py)
                    }

                    // 2. Draw Vision Targets Snapping Location (FRC Tag at x=8.0, y=4.0)
                    val targetPixel = mapToPixel(8.0, 4.0)
                    drawCircle(Color(0xFFFFEA00), radius = 8f, center = targetPixel)
                    drawCircle(Color(0xFFFFEA00), radius = 14f, center = targetPixel, style = Stroke(width = 2f))

                    // 3. Draw Real Pose Path (Logged Odometry / Baseline EKF)
                    val realPath = Path()
                    var isFirstReal = true
                    for (step in replaySummary.steps) {
                        val pixelOffset = mapToPixel(step.realPose.x, step.realPose.y)
                        if (isFirstReal) {
                            realPath.moveTo(pixelOffset.x, pixelOffset.y)
                            isFirstReal = false
                        } else {
                            realPath.lineTo(pixelOffset.x, pixelOffset.y)
                        }
                    }
                    drawPath(realPath, color = Color(0xFF00E5FF), style = Stroke(width = 4f))

                    // 4. Draw Ghost Pose Path ("What-If" Custom Covariance EKF)
                    val ghostPath = Path()
                    var isFirstGhost = true
                    for (step in replaySummary.steps) {
                        val pixelOffset = mapToPixel(step.ghostPose.x, step.ghostPose.y)
                        if (isFirstGhost) {
                            ghostPath.moveTo(pixelOffset.x, pixelOffset.y)
                            isFirstGhost = false
                        } else {
                            ghostPath.lineTo(pixelOffset.x, pixelOffset.y)
                        }
                    }
                    drawPath(ghostPath, color = Color(0xFFFF1744), style = Stroke(width = 4f))

                    // 5. Draw active robot indicator at final coordinate
                    if (replaySummary.steps.isNotEmpty()) {
                        val robotPose = replaySummary.finalRealPose
                        val finalRealPixel = mapToPixel(robotPose.x, robotPose.y)
                        val robotHeading = robotPose.heading.radians
                        
                        val ghostPose = replaySummary.finalGhostPose
                        val finalGhostPixel = mapToPixel(ghostPose.x, ghostPose.y)
                        val ghostHeading = ghostPose.heading.radians

                        val pixelsPerMeter = ((width * 0.9) / 16.541).toFloat()
                        val robotSizePx = 0.45f * pixelsPerMeter
                        val halfSize = robotSizePx / 2.0f

                        // Draw camera FOV outlines first (so they are under the robot body)
                        if (showFov) {
                            val activeStep = replaySummary.steps.lastOrNull()
                            val cameras = activeStep?.cameraPoses?.takeIf { it.isNotEmpty() }
                                ?: listOf(Pose3d(Translation3d(0.18, 0.0, 0.0), Rotation3d(0.0, 0.0, 0.0)))

                            cameras.forEach { cam ->
                                val cx = robotPose.x + cam.translation.x * kotlin.math.cos(robotHeading) - cam.translation.y * kotlin.math.sin(robotHeading)
                                val cy = robotPose.y + cam.translation.x * kotlin.math.sin(robotHeading) + cam.translation.y * kotlin.math.cos(robotHeading)
                                val camHeading = robotHeading + cam.rotation.z
                                val cameraPixel = mapToPixel(cx, cy)
                                
                                val range = 4.0f * pixelsPerMeter
                                val halfFov = (63.0f * Math.PI / 180.0f) / 2.0f
                                
                                val angleStart = camHeading - halfFov
                                val angleEnd = camHeading + halfFov
                                
                                val path = Path().apply {
                                    moveTo(cameraPixel.x, cameraPixel.y)
                                    lineTo(
                                        (cameraPixel.x + range * kotlin.math.cos(angleStart)).toFloat(),
                                        (cameraPixel.y - range * kotlin.math.sin(angleStart)).toFloat()
                                    )
                                    lineTo(
                                        (cameraPixel.x + range * kotlin.math.cos(angleEnd)).toFloat(),
                                        (cameraPixel.y - range * kotlin.math.sin(angleEnd)).toFloat()
                                    )
                                    close()
                                }
                                
                                drawPath(path, color = Color(0x0A00E5FF))
                                drawPath(path, color = Color(0x3300E5FF), style = Stroke(width = 1.5f))
                            }
                        }

                        // Draw Physical Robot Box (Rotated)
                        drawContext.canvas.save()
                        drawContext.transform.rotate(
                            degrees = -Math.toDegrees(robotHeading).toFloat(),
                            pivot = finalRealPixel
                        )
                        drawRect(
                            color = Color(0x1100E5FF),
                            topLeft = Offset(finalRealPixel.x - halfSize, finalRealPixel.y - halfSize),
                            size = Size(robotSizePx, robotSizePx)
                        )
                        drawRect(
                            color = Color(0x5500E5FF),
                            topLeft = Offset(finalRealPixel.x - halfSize, finalRealPixel.y - halfSize),
                            size = Size(robotSizePx, robotSizePx),
                            style = Stroke(width = 2f)
                        )
                        // Arrow pointing forward (+X direction)
                        drawLine(
                            color = Color(0xFF00E5FF),
                            start = finalRealPixel,
                            end = Offset(finalRealPixel.x + halfSize + 10f, finalRealPixel.y),
                            strokeWidth = 3f
                        )
                        drawContext.canvas.restore()

                        // Draw Ghost Robot Box (Rotated)
                        drawContext.canvas.save()
                        drawContext.transform.rotate(
                            degrees = -Math.toDegrees(ghostHeading).toFloat(),
                            pivot = finalGhostPixel
                        )
                        drawRect(
                            color = Color(0x11FF1744),
                            topLeft = Offset(finalGhostPixel.x - halfSize, finalGhostPixel.y - halfSize),
                            size = Size(robotSizePx, robotSizePx)
                        )
                        drawRect(
                            color = Color(0x55FF1744),
                            topLeft = Offset(finalGhostPixel.x - halfSize, finalGhostPixel.y - halfSize),
                            size = Size(robotSizePx, robotSizePx),
                            style = Stroke(width = 1.5f)
                        )
                        // Arrow pointing forward
                        drawLine(
                            color = Color(0xFFFF1744),
                            start = finalGhostPixel,
                            end = Offset(finalGhostPixel.x + halfSize + 8f, finalGhostPixel.y),
                            strokeWidth = 2f
                        )
                        drawContext.canvas.restore()

                        // Draw central indicator dots
                        drawCircle(Color(0xFF00E5FF), radius = 5f, center = finalRealPixel)
                        drawCircle(Color(0xFFFF1744), radius = 5f, center = finalGhostPixel)
                    }
                }

                // Legend layout
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp, 4.dp).background(Color(0xFF00E5FF)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Real (Logged) Path", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp, 4.dp).background(Color(0xFFFF1744)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ghost (\"What-If\") Path", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFEA00), RoundedCornerShape(4.dp)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vision Target Tag", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Generates highly realistic multi-tick mock raw sensor inputs.
 * Emulates a standard linear robot path with a sudden vision camera tag reading.
 */
private fun generateSyntheticLog(): List<String> {
    val lines = mutableListOf<String>()
    val gson = com.google.gson.Gson()

    for (i in 0..50) {
        val timeMs = 1000L + i * 20L
        val frame = com.areslib.logging.RobotInputsFrame().apply {
            timestampMs = timeMs
            
            // Standard forward odometer driving with minor drift noise
            odometryInputs.apply {
                posX = i * 0.25
                posY = i * 0.10 + (if (i > 25) 0.5 else 0.0)
                heading = 0.0
                velX = 2.0
                velY = 2.0
                headingVelocity = 0.0
                timestampMs = timeMs
            }
            
            imuInputs.apply {
                headingRadians = 0.0
                timestampMs = timeMs
            }

            visionInputs.apply {
                cameraPoses = listOf(
                    com.areslib.math.Pose3d(
                        com.areslib.math.Translation3d(0.18, 0.0, 0.0),
                        com.areslib.math.Rotation3d(0.0, 0.0, 0.0)
                    )
                )
            }

            // Simulate one camera update frame at step 25 (tag target is at x=8.0, y=4.0 in FRC scale)
            if (i == 25) {
                visionInputs.apply {
                    isConnected = true
                    measurements = listOf(
                        com.areslib.state.VisionMeasurement(
                            timestampMs = timeMs,
                            targetPose = com.areslib.math.Pose3d(
                                com.areslib.math.Translation3d(8.0, 4.0, 0.0),
                                com.areslib.math.Rotation3d()
                            ),
                            tagId = 9,
                            ambiguity = 0.05
                        )
                    )
                }
            }
        }
        lines.add(gson.toJson(frame))
    }
    return lines
}
