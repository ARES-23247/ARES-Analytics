package com.ares.analytics.ui.components.linkage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGold
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.areslib.math.kinematics.ElbowConfiguration
import com.areslib.math.kinematics.TwoDofLinkageKinematics
import com.areslib.math.kinematics.TwoDofLinkageParameters
import com.areslib.subsystem.SubsystemLinkageDocument
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive 2D Mechanism & Linkage Kinematics Canvas.
 *
 * Renders 2-DOF planar linkages (double-jointed arms, virtual 4-bars), computes analytical
 * inverse kinematics, draws reachable workspace bounds, detects singularity hazards, and
 * previews multivariable gravity torque feedforward in real-time.
 */
@Composable
fun LinkageEditorCanvas(
    linkage: SubsystemLinkageDocument,
    onLinkageChanged: (SubsystemLinkageDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = remember(linkage.link1LengthMeters, linkage.link2LengthMeters, linkage.link1MassKg, linkage.link2MassKg) {
        TwoDofLinkageParameters(
            l1 = linkage.link1LengthMeters.coerceAtLeast(0.05),
            l2 = linkage.link2LengthMeters.coerceAtLeast(0.05),
            m1 = linkage.link1MassKg.coerceAtLeast(0.01),
            m2 = linkage.link2MassKg.coerceAtLeast(0.01),
        )
    }
    val kinematics = remember(params) { TwoDofLinkageKinematics(params) }

    var theta1Deg by remember { mutableStateOf(45.0) }
    var theta2Deg by remember { mutableStateOf(-60.0) }

    val theta1Rad = theta1Deg * PI / 180.0
    val theta2Rad = theta2Deg * PI / 180.0

    val fkPose = kinematics.forwardKinematics(theta1Rad, theta2Rad)
    val isSingular = kinematics.isNearSingularity(theta1Rad, theta2Rad)
    val torques = kinematics.gravityTorque(theta1Rad, theta2Rad)

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PrecisionManufacturing, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                    Text(
                        text = "2-DOF Linkage & Arm Kinematics",
                        color = AresTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSingular) AresError.copy(alpha = 0.2f) else AresGreen.copy(alpha = 0.2f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSingular) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AresError, modifier = Modifier.size(14.dp))
                            Text("Singularity Hazard", color = AresError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Kinematics OK", color = AresGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2D Visual Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(AresBackground, RoundedCornerShape(8.dp))
                    .border(1.dp, AresBorder, RoundedCornerShape(8.dp)),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val originX = size.width / 2.0f
                    val originY = size.height * 0.75f
                    val scale = (size.height * 0.6f) / (params.maxReach.toFloat().coerceAtLeast(0.1f))

                    // Draw workspace envelope (outer reach & inner reach)
                    val maxR = params.maxReach.toFloat() * scale
                    val minR = params.minReach.toFloat() * scale

                    drawCircle(
                        color = AresCyan.copy(alpha = 0.08f),
                        radius = maxR,
                        center = Offset(originX, originY),
                    )
                    drawCircle(
                        color = AresCyan.copy(alpha = 0.3f),
                        radius = maxR,
                        center = Offset(originX, originY),
                        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                    )
                    if (minR > 5f) {
                        drawCircle(
                            color = AresBackground,
                            radius = minR,
                            center = Offset(originX, originY),
                        )
                        drawCircle(
                            color = AresError.copy(alpha = 0.4f),
                            radius = minR,
                            center = Offset(originX, originY),
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
                        )
                    }

                    // Ground baseline
                    drawLine(
                        color = AresBorder,
                        start = Offset(0f, originY),
                        end = Offset(size.width, originY),
                        strokeWidth = 2f,
                    )

                    // Joint 1 -> Joint 2 (Proximal Link)
                    val elbowX = originX + (params.l1.toFloat() * cos(theta1Rad).toFloat() * scale)
                    val elbowY = originY - (params.l1.toFloat() * sin(theta1Rad).toFloat() * scale)

                    drawLine(
                        color = AresCyan,
                        start = Offset(originX, originY),
                        end = Offset(elbowX, elbowY),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round,
                    )

                    // Joint 2 -> End Effector (Distal Link)
                    val eeX = elbowX + (params.l2.toFloat() * cos(theta1Rad + theta2Rad).toFloat() * scale)
                    val eeY = elbowY - (params.l2.toFloat() * sin(theta1Rad + theta2Rad).toFloat() * scale)

                    drawLine(
                        color = AresGold,
                        start = Offset(elbowX, elbowY),
                        end = Offset(eeX, eeY),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )

                    // Joint Pivots
                    drawCircle(color = AresTextPrimary, radius = 6f, center = Offset(originX, originY))
                    drawCircle(color = AresCyan, radius = 5f, center = Offset(elbowX, elbowY))
                    drawCircle(color = AresGold, radius = 6f, center = Offset(eeX, eeY))
                }
            }

            // Real-time calculated values
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AresBackground,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("End-Effector (X, Y)", color = AresTextSecondary, fontSize = 11.sp)
                        Text(
                            "%.2f m, %.2f m".format(fkPose.x, fkPose.y),
                            color = AresTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Motor 1 Gravity Torque", color = AresTextSecondary, fontSize = 11.sp)
                        Text(
                            "%.2f N·m".format(torques[0]),
                            color = AresCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Motor 2 Gravity Torque", color = AresTextSecondary, fontSize = 11.sp)
                        Text(
                            "%.2f N·m".format(torques[1]),
                            color = AresGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // Joint Angle Sliders for live interactive simulation
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Base Joint Angle (\u03B81): %.1f°".format(theta1Deg), color = AresTextPrimary, fontSize = 12.sp)
                }
                Slider(
                    value = theta1Deg.toFloat(),
                    onValueChange = { theta1Deg = it.toDouble() },
                    valueRange = -180f..180f,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Elbow Joint Angle (\u03B82): %.1f°".format(theta2Deg), color = AresTextPrimary, fontSize = 12.sp)
                }
                Slider(
                    value = theta2Deg.toFloat(),
                    onValueChange = { theta2Deg = it.toDouble() },
                    valueRange = -180f..180f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
