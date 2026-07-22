package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import com.ares.analytics.ui.components.core.*

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun IMUVisualizerCard(
    nt4ClientService: Nt4ClientService,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var roll by remember { mutableStateOf<Double?>(null) }
    var pitch by remember { mutableStateOf<Double?>(null) }
    var yaw by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        nt4ClientService.telemetryFlow.collect { frame ->
            val key = frame.key
            val value = frame.value as? Double ?: return@collect
            
            when {
                key.endsWith("IMU/Roll") || key.endsWith("IMU/roll") -> roll = value
                key.endsWith("IMU/Pitch") || key.endsWith("IMU/pitch") -> pitch = value
                key.endsWith("IMU/Yaw") || key.endsWith("IMU/yaw") || key.endsWith("Drive/Pose_Heading") -> yaw = value
                
                // Fallbacks if team puts IMU on different prefix
                key.endsWith("Robot/Roll") || key.endsWith("Robot/roll") -> roll = value
                key.endsWith("Robot/Pitch") || key.endsWith("Robot/pitch") -> pitch = value
                key.endsWith("Robot/Yaw") || key.endsWith("Robot/yaw") -> yaw = value
            }
        }
    }

    AnalyticsCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = AresSurfaceElevated
    ) {
        CardHeader(
            title = "IMU Orientation",
            icon = Icons.Default.CompassCalibration,
            iconTint = AresGold,
            trailingContent = {
                Button(
                    onClick = { scope.launch { nt4ClientService.publishInputBoolean(1100, true) } }, // Assume 1100 is a generic zero heading command
                    colors = ButtonDefaults.buttonColors(containerColor = AresSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("Zero Heading", color = AresTextSecondary, fontSize = 10.sp)
                }
            }
        )



            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Roll
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ROLL", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val rColor = if (roll != null && kotlin.math.abs(roll!!) > 15.0) AresError else AresTextPrimary
                    Text(
                        text = roll?.let { String.format("%.1f°", Math.toDegrees(it)) } ?: "--",
                        color = rColor,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Pitch
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PITCH", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val pColor = if (pitch != null && kotlin.math.abs(pitch!!) > 15.0) AresError else AresTextPrimary
                    Text(
                        text = pitch?.let { String.format("%.1f°", Math.toDegrees(it)) } ?: "--",
                        color = pColor,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Yaw
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("YAW (HEADING)", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = yaw?.let { String.format("%.1f°", Math.toDegrees(it)) } ?: "--",
                        color = AresCyan,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Simple visualizer for heading
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AresBackground)
                    .border(1.dp, AresBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = size.width / 2f - 4f
                    
                    // Draw compass circle
                    drawCircle(color = AresSurface, radius = radius, center = Offset(cx, cy))
                    drawCircle(color = AresBorder, radius = radius, center = Offset(cx, cy), style = Stroke(width = 2f))
                    
                    if (yaw != null) {
                        val yawDeg = Math.toDegrees(yaw!!).toFloat()
                        rotate(degrees = -yawDeg, pivot = Offset(cx, cy)) {
                            // Draw robot arrow pointing up
                            val arrowW = 20f
                            val arrowH = 40f
                            drawRect(
                                color = AresCyan,
                                topLeft = Offset(cx - arrowW/2, cy - arrowH/2),
                                size = Size(arrowW, arrowH)
                            )
                            // Front indicator
                            drawRect(
                                color = AresGold,
                                topLeft = Offset(cx - arrowW/2, cy - arrowH/2),
                                size = Size(arrowW, arrowH * 0.3f)
                            )
                        }
                    } else {
                        drawCircle(color = AresTextTertiary, radius = 4f, center = Offset(cx, cy))
                    }
                }
            }
        }
    }

