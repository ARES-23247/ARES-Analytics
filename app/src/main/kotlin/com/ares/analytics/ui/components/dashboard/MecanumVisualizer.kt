package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.service.ReplayFrame
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MecanumVisualizer(
    currentFrame: ReplayFrame? = null,
    nt4ClientService: Nt4ClientService? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // FL = 0, FR = 1, BL = 2, BR = 3
    val velocities = remember { mutableStateListOf(0.0, 0.0, 0.0, 0.0) }
    val currents = remember { mutableStateListOf(0.0, 0.0, 0.0, 0.0) }

    when {
        currentFrame != null -> {
            velocities[0] = currentFrame.values["Drive/MotorVelocity_fl"] ?: currentFrame.values["Drive/MotorPower_fl"] ?: currentFrame.values["Hardware/Motors/fl/Velocity"] ?: currentFrame.values["Hardware/Motors/fl/Power"] ?: 0.0
            velocities[1] = currentFrame.values["Drive/MotorVelocity_fr"] ?: currentFrame.values["Drive/MotorPower_fr"] ?: currentFrame.values["Hardware/Motors/fr/Velocity"] ?: currentFrame.values["Hardware/Motors/fr/Power"] ?: 0.0
            velocities[2] = currentFrame.values["Drive/MotorVelocity_bl"] ?: currentFrame.values["Drive/MotorPower_bl"] ?: currentFrame.values["Hardware/Motors/bl/Velocity"] ?: currentFrame.values["Hardware/Motors/bl/Power"] ?: currentFrame.values["Hardware/Motors/rl/Velocity"] ?: currentFrame.values["Hardware/Motors/rl/Power"] ?: 0.0
            velocities[3] = currentFrame.values["Drive/MotorVelocity_br"] ?: currentFrame.values["Drive/MotorPower_br"] ?: currentFrame.values["Hardware/Motors/br/Velocity"] ?: currentFrame.values["Hardware/Motors/br/Power"] ?: currentFrame.values["Hardware/Motors/rr/Velocity"] ?: currentFrame.values["Hardware/Motors/rr/Power"] ?: 0.0

            currents[0] = currentFrame.values["Drive/MotorCurrent_fl"] ?: currentFrame.values["Hardware/Motors/fl/CurrentAmps"] ?: 0.0
            currents[1] = currentFrame.values["Drive/MotorCurrent_fr"] ?: currentFrame.values["Hardware/Motors/fr/CurrentAmps"] ?: 0.0
            currents[2] = currentFrame.values["Drive/MotorCurrent_bl"] ?: currentFrame.values["Hardware/Motors/bl/CurrentAmps"] ?: currentFrame.values["Hardware/Motors/rl/CurrentAmps"] ?: 0.0
            currents[3] = currentFrame.values["Drive/MotorCurrent_br"] ?: currentFrame.values["Hardware/Motors/br/CurrentAmps"] ?: currentFrame.values["Hardware/Motors/rr/CurrentAmps"] ?: 0.0
        }
        nt4ClientService != null -> {
            LaunchedEffect(Unit) {
                nt4ClientService.telemetryFlow.collect { frame ->
                    val key = frame.key
                    val value = frame.value
                    when (key) {
                        "Drive/MotorVelocity_fl", "Drive/MotorPower_fl", "Hardware/Motors/fl/Velocity", "Hardware/Motors/fl/Power" -> velocities[0] = value
                        "Drive/MotorVelocity_fr", "Drive/MotorPower_fr", "Hardware/Motors/fr/Velocity", "Hardware/Motors/fr/Power" -> velocities[1] = value
                        "Drive/MotorVelocity_bl", "Drive/MotorPower_bl", "Drive/MotorVelocity_rl", "Drive/MotorPower_rl", "Hardware/Motors/bl/Velocity", "Hardware/Motors/bl/Power", "Hardware/Motors/rl/Velocity", "Hardware/Motors/rl/Power" -> velocities[2] = value
                        "Drive/MotorVelocity_br", "Drive/MotorPower_br", "Drive/MotorVelocity_rr", "Drive/MotorPower_rr", "Hardware/Motors/br/Velocity", "Hardware/Motors/br/Power", "Hardware/Motors/rr/Velocity", "Hardware/Motors/rr/Power" -> velocities[3] = value
                        
                        "Drive/MotorCurrent_fl", "Hardware/Motors/fl/CurrentAmps" -> currents[0] = value
                        "Drive/MotorCurrent_fr", "Hardware/Motors/fr/CurrentAmps" -> currents[1] = value
                        "Drive/MotorCurrent_bl", "Drive/MotorCurrent_rl", "Hardware/Motors/bl/CurrentAmps", "Hardware/Motors/rl/CurrentAmps" -> currents[2] = value
                        "Drive/MotorCurrent_br", "Drive/MotorCurrent_rr", "Hardware/Motors/br/CurrentAmps", "Hardware/Motors/rr/CurrentAmps" -> currents[3] = value
                    }
                }
            }
        }
    }

    val isConnected by nt4ClientService?.isConnected?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AresSurface, RoundedCornerShape(12.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mecanum Drivetrain Force Vectors",
                color = AresTextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (isConnected) "Live Connected" else "Live Disconnected",
                color = if (isConnected) AresGreen else AresTextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AresBackground, RoundedCornerShape(8.dp))
                .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Draw robot outline (dashed)
                val robotW = 160f
                val robotH = 220f
                drawRect(
                    color = AresBorder,
                    topLeft = Offset(cx - robotW / 2f, cy - robotH / 2f),
                    size = Size(robotW, robotH),
                    style = Stroke(width = 2f, pathEffect = dashEffect)
                )

                // Wheel definitions: Name, CenterOffset, RollerAngleRad, Speed
                val wheels = listOf(
                    // FL: rollers at 45 deg (points top-left to bottom-right)
                    WheelData("FL", Offset(cx - robotW / 2f, cy - robotH / 2f), Math.toRadians(45.0), velocities[0], currents[0]),
                    // FR: rollers at -45 deg (points bottom-left to top-right)
                    WheelData("FR", Offset(cx + robotW / 2f, cy - robotH / 2f), Math.toRadians(-45.0), velocities[1], currents[1]),
                    // BL: rollers at -45 deg
                    WheelData("BL", Offset(cx - robotW / 2f, cy + robotH / 2f), Math.toRadians(-45.0), velocities[2], currents[2]),
                    // BR: rollers at 45 deg
                    WheelData("BR", Offset(cx + robotW / 2f, cy + robotH / 2f), Math.toRadians(45.0), velocities[3], currents[3])
                )

                    val maxAbsSpeed = wheels.maxOfOrNull { Math.abs(it.speed) }?.toFloat() ?: 0f
                    val speedScale = if (maxAbsSpeed > 2.0f) Math.max(maxAbsSpeed, 100f) else 1.0f

                    for (w in wheels) {
                        val center = w.center
                        val wWidth = 32f
                        val wHeight = 64f

                        // Draw wheel body
                        drawRoundRect(
                            color = AresSurfaceElevated,
                            topLeft = Offset(center.x - wWidth / 2f, center.y - wHeight / 2f),
                            size = Size(wWidth, wHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawRoundRect(
                            color = AresBorder,
                            topLeft = Offset(center.x - wWidth / 2f, center.y - wHeight / 2f),
                            size = Size(wWidth, wHeight),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = 1.5f)
                        )

                        // Draw wheel rollers (diagonal lines)
                        val spacing = 12f
                        var offset = -wHeight / 2f + 4f
                        while (offset < wHeight / 2f) {
                            val rx1 = center.x - wWidth / 2f + 2f
                            val ry1 = center.y + offset
                            val rx2 = center.x + wWidth / 2f - 2f
                            val ry2 = ry1 + wWidth * tan(w.rollerAngle).toFloat()

                            if (ry2 >= center.y - wHeight / 2f && ry2 <= center.y + wHeight / 2f) {
                                drawLine(
                                    color = AresBorder.copy(alpha = 0.6f),
                                    start = Offset(rx1, ry1),
                                    end = Offset(rx2, ry2),
                                    strokeWidth = 2.5f,
                                    cap = StrokeCap.Round
                                )
                            }
                            offset += spacing
                        }

                        // Draw spin vector arrow (along wheel axis, vertically)
                        val normalizedSpeed = (w.speed / speedScale).toFloat()
                        if (Math.abs(normalizedSpeed) > 0.05f) {
                            val maxArrowLen = 40f
                            val arrowLen = normalizedSpeed * maxArrowLen
                            val spinEnd = Offset(center.x, center.y - arrowLen)

                            drawLine(
                                color = AresGreen,
                                start = center,
                                end = spinEnd,
                                strokeWidth = 3.5f,
                                cap = StrokeCap.Round
                            )

                            // Draw traction force vector arrow (at 45 degrees, matching roller slide reaction)
                            // The traction force vector points along the roller's perpendicular axis (direction of push)
                            val forceAngle = getForceAngle(w.name, w.speed)

                            val forceLen = Math.abs(normalizedSpeed * maxArrowLen)
                            val forceEnd = Offset(
                                center.x + forceLen * cos(forceAngle).toFloat(),
                                center.y + forceLen * sin(forceAngle).toFloat()
                            )

                            drawLine(
                                color = AresCyan,
                                start = center,
                                end = forceEnd,
                                strokeWidth = 3.5f,
                                cap = StrokeCap.Round
                            )

                            // Draw force arrowhead
                            val headSize = 8f
                            val leftWing = Offset(
                                forceEnd.x - headSize * cos(forceAngle - Math.PI / 6).toFloat(),
                                forceEnd.y - headSize * sin(forceAngle - Math.PI / 6).toFloat()
                            )
                            val rightWing = Offset(
                                forceEnd.x - headSize * cos(forceAngle + Math.PI / 6).toFloat(),
                                forceEnd.y - headSize * sin(forceAngle + Math.PI / 6).toFloat()
                            )
                            drawLine(color = AresCyan, start = forceEnd, end = leftWing, strokeWidth = 2.5f)
                            drawLine(color = AresCyan, start = forceEnd, end = rightWing, strokeWidth = 2.5f)
                        }
                    }

                    // Calculate and draw net force vector in the center
                    var netForceX = 0f
                    var netForceY = 0f
                    for (w in wheels) {
                        val normalizedSpeed = (w.speed / speedScale).toFloat()
                        if (Math.abs(normalizedSpeed) > 0.05f) {
                            val forceAngle = getForceAngle(w.name, w.speed)
                            val forceLen = Math.abs(normalizedSpeed)
                            netForceX += forceLen * cos(forceAngle).toFloat()
                            netForceY += forceLen * sin(forceAngle).toFloat()
                        }
                    }

                    val netMagnitude = Math.sqrt((netForceX * netForceX + netForceY * netForceY).toDouble()).toFloat()
                    if (netMagnitude > 0.05f) {
                        val maxNetArrowLen = 100f
                        val arrowLen = (netMagnitude * maxNetArrowLen).coerceAtMost(maxNetArrowLen)
                        val netAngle = Math.atan2(netForceY.toDouble(), netForceX.toDouble())
                        
                        val netStart = Offset(cx, cy)
                        val netEnd = Offset(
                            cx + arrowLen * cos(netAngle).toFloat(),
                            cy + arrowLen * sin(netAngle).toFloat()
                        )

                        drawLine(
                            color = AresAmber, // use Amber to stand out from Cyan wheel vectors
                            start = netStart,
                            end = netEnd,
                            strokeWidth = 6f, // thicker line for net vector
                            cap = StrokeCap.Round
                        )

                        // Draw net force arrowhead
                        val headSize = 14f
                        val leftWing = Offset(
                            netEnd.x - headSize * cos(netAngle - Math.PI / 6).toFloat(),
                            netEnd.y - headSize * sin(netAngle - Math.PI / 6).toFloat()
                        )
                        val rightWing = Offset(
                            netEnd.x - headSize * cos(netAngle + Math.PI / 6).toFloat(),
                            netEnd.y - headSize * sin(netAngle + Math.PI / 6).toFloat()
                        )
                        drawLine(color = AresAmber, start = netEnd, end = leftWing, strokeWidth = 4f, cap = StrokeCap.Round)
                        drawLine(color = AresAmber, start = netEnd, end = rightWing, strokeWidth = 4f, cap = StrokeCap.Round)
                    }
                }
            }

        // Details Panel
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text("FL: ${"%.2f".format(velocities[0])} rad/s | ${"%.1f".format(currents[0])}A", color = AresTextSecondary, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("BL: ${"%.2f".format(velocities[2])} rad/s | ${"%.1f".format(currents[2])}A", color = AresTextSecondary, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("FR: ${"%.2f".format(velocities[1])} rad/s | ${"%.1f".format(currents[1])}A", color = AresTextSecondary, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("BR: ${"%.2f".format(velocities[3])} rad/s | ${"%.1f".format(currents[3])}A", color = AresTextSecondary, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

private data class WheelData(
    val name: String,
    val center: Offset,
    val rollerAngle: Double,
    val speed: Double,
    val current: Double
)

private fun getForceAngle(name: String, speed: Double): Double = when (name) {
    "FL" -> if (speed >= 0) Math.toRadians(-45.0) else Math.toRadians(135.0)
    "FR" -> if (speed >= 0) Math.toRadians(-135.0) else Math.toRadians(45.0)
    "BL" -> if (speed >= 0) Math.toRadians(-135.0) else Math.toRadians(45.0)
    "BR" -> if (speed >= 0) Math.toRadians(-45.0) else Math.toRadians(135.0)
    else -> 0.0
}

private fun tan(radians: Double): Double = kotlin.math.tan(radians)
