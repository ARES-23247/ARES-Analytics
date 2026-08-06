package com.ares.analytics.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*

enum class AcademyTrack(val title: String, val subtitle: String, val icon: ImageVector, val color: Color) {
    CONTROL_THEORY("Control Theory & Motion Profiling", "PIDF, Feedforwards (kS/kV/kA), S-Curve Motion, LQR", Icons.Default.Tune, AresCyan),
    LOCALIZATION("Localization & Sensor Fusion", "GoBilda Pinpoint, EKF Pose Estimation, Outlier Filtering", Icons.Default.GpsFixed, AresGreen),
    KINEMATICS("Drivetrain Kinematics", "Mecanum Vector Math, Swerve Azimuth & Zero Calibration", Icons.Default.DirectionsCar, AresGold),
    VISION("Vision & AprilTags", "Limelight 3D Pose, Target-Space Alignment, Outlier Rejection", Icons.Default.Videocam, AresPurple),
    PATHFINDING("Pathfinding & Avoidance", "Bezier Splines, PathPlanner Markers, Theta* & VFH+", Icons.Default.Route, AresRed),
    POWER_DIAGNOSTICS("Power & System Diagnostics", "Brownout Protection, .wpilog Analysis, SysId Characterization", Icons.Default.BatteryChargingFull, AresCyan)
}

data class AcademyLesson(
    val id: String,
    val title: String,
    val description: String,
    val formula: String?,
    val physicalUnits: String,
    val keyTakeaway: String,
    val defaultVal: Double = 1.0,
    val minVal: Double = 0.0,
    val maxVal: Double = 10.0
)

@Composable
fun AcademyScreen(
    onLaunchSimChallenge: (String) -> Unit = {}
) {
    var selectedTrack by remember { mutableStateOf(AcademyTrack.CONTROL_THEORY) }
    var selectedLessonId by remember { mutableStateOf("pidf_tuning") }
    var completedLessons by remember { mutableStateOf(setOf<String>()) }
    var simParamValue by remember { mutableStateOf(1.8) }

    val lessonsByTrack = remember {
        mapOf(
            AcademyTrack.CONTROL_THEORY to listOf(
                AcademyLesson("pidf_tuning", "PIDF Closed-Loop Control", "Proportional, Integral, Derivative, and Feedforward closed-loop position & velocity control.", "u(t) = kP e(t) + kI \\int e(t) dt + kD \\frac{de}{dt} + kF v_{target}", "Distances: m, Angles: rad, Time: s", "Proportional gain kP reduces rise time, kD damps oscillation, kI fixes steady-state offset.", defaultVal = 1.8, minVal = 0.0, maxVal = 5.0),
                AcademyLesson("feedforward_ks_kv_ka", "Motor Physics (kS, kV, kA)", "Overcoming static friction (kS), back-EMF velocity resistance (kV), and inertia (kA).", "V_{ff} = kS \\text{sgn}(v) + kV v + kA a", "Voltage: V, Velocity: m/s, Acceleration: m/s²", "Static friction kS is critical for Mecanum position hold on foam tile friction.", defaultVal = 0.06, minVal = 0.0, maxVal = 0.25),
                AcademyLesson("scurve_profiling", "Jerk-Limited Motion Profiling", "Smooth S-curve velocity profiles to prevent wheel slippage and chassis tip-over.", "jerk = \\frac{d^3 x}{dt^3} \\le j_{max}", "Velocity: m/s, Jerk: m/s³", "S-curves bound maximum jerk for smooth, zero-slip acceleration.", defaultVal = 3.0, minVal = 0.5, maxVal = 8.0)
            ),
            AcademyTrack.LOCALIZATION to listOf(
                AcademyLesson("ekf_fusion", "Extended Kalman Filter (EKF)", "State-space sensor fusion combining 100 Hz odometry with 20 Hz AprilTag vision.", "\\hat{x}_{k} = \\hat{x}_k^- + K_k (z_k - H \\hat{x}_k^-)", "Pose: (x, y, θ), Covariance: Q & R", "EKF dynamically weights sensors based on variance covariances Q and R.", defaultVal = 0.01, minVal = 0.001, maxVal = 0.1),
                AcademyLesson("mahalanobis_outliers", "Mahalanobis Outlier Rejection", "Automatic rejection of corrupted or blurred AprilTag vision updates.", "d_M^2 = (z - Hx)^T S^{-1} (z - Hx) < \\chi^2_{thresh}", "Threshold: Dimensionless χ²", "Rejects vision spikes caused by motion blur, edge glare, or PnP ambiguity.", defaultVal = 18.0, minVal = 5.0, maxVal = 50.0)
            ),
            AcademyTrack.KINEMATICS to listOf(
                AcademyLesson("mecanum_kinematics", "Mecanum Kinematics & Roller Vectors", "Decomposing chassis speeds (vx, vy, omega) into 4 wheel angular velocities.", "v_i = \\frac{1}{R} (v_x \\pm v_y \\pm (a+b)\\omega)", "Linear: m/s, Angular: rad/s", "Field-centric drive rotates velocity vectors using gyro heading θ before inverse kinematics.", defaultVal = 0.45, minVal = 0.2, maxVal = 0.8),
                AcademyLesson("swerve_zeroing", "Swerve Azimuth & CANcoder Zeroing", "Calibrating absolute magnet zero offsets and preserving 4-tier flash backups.", "\\theta_{module} = \\theta_{raw} - \\theta_{zero}", "Angles: rad, CAN IDs: 1..12", "Swerve zeroing writes runtime JSON with auto-pruned timestamped backups.", defaultVal = 0.0, minVal = -3.14, maxVal = 3.14)
            ),
            AcademyTrack.VISION to listOf(
                AcademyLesson("target_space_pose", "AprilTag 3D Target-Space Pose", "3D pose relative to AprilTag face (X right, Y up, Z depth outward).", "P_{robot} = R_{tag}^T (P_{camera} - P_{tag})", "X, Y, Z: meters, Heading: rad (rotation.y)", "Heading rotation relative to tag is in rotation.y (NOT rotation.z).", defaultVal = 2.438, minVal = 0.5, maxVal = 6.0)
            ),
            AcademyTrack.PATHFINDING to listOf(
                AcademyLesson("theta_star_vfh", "Theta* Pathfinder & VFH+ Avoidance", "Any-angle grid pathfinding and real-time obstacle vector field histograms.", "J_{cost} = d_{target} + w_{obs} \\cdot \\text{costmap}(x, y)", "Grid Resolution: m, Inflation Radius: m", "VFH+ computes safe steering vectors around alliance robots dynamically.", defaultVal = 0.40, minVal = 0.1, maxVal = 1.0)
            ),
            AcademyTrack.POWER_DIAGNOSTICS to listOf(
                AcademyLesson("brownout_protection", "Battery Voltage & Brownout Protection", "Dynamic current scaling to keep battery voltage above 10.5V.", "I_{max} = f(V_{battery}, T_{cell})", "Voltage: V, Current: Amps", "Brownout guard prevents Control Hub / roboRIO reboots during high acceleration.", defaultVal = 10.5, minVal = 9.0, maxVal = 12.0)
            )
        )
    }

    val currentLessons = lessonsByTrack[selectedTrack] ?: emptyList()
    val activeLesson = currentLessons.firstOrNull { it.id == selectedLessonId } ?: currentLessons.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AresBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AresSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.School, contentDescription = null, tint = AresCyan, modifier = Modifier.size(28.dp))
                        Text(
                            text = "ARES Academy",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = AresTextPrimary
                        )
                    }
                    Text(
                        text = "Interactive Student Onboarding & Control Theory Suite (FTC & FRC)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AresTextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = AresGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AresGreen)
                    ) {
                        Text(
                            text = "Completed: ${completedLessons.size} / ${lessonsByTrack.values.sumOf { it.size }}",
                            color = AresGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Track Selector Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AcademyTrack.values().forEach { track ->
                val isSelected = track == selectedTrack
                val trackBg = if (isSelected) track.color.copy(alpha = 0.2f) else AresSurface
                val borderCol = if (isSelected) track.color else AresBorder

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedTrack = track
                            selectedLessonId = lessonsByTrack[track]?.firstOrNull()?.id ?: ""
                        },
                    colors = CardDefaults.cardColors(containerColor = trackBg),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(track.icon, contentDescription = null, tint = track.color, modifier = Modifier.size(20.dp))
                        Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 12.sp, maxLines = 1)
                        Text(track.subtitle, style = MaterialTheme.typography.bodySmall, color = AresTextSecondary, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }

        // Main Lesson Content Area
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Sidebar: Lessons List
            Card(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = AresSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("MODULE LESSONS", style = MaterialTheme.typography.labelMedium, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(currentLessons) { lesson ->
                            val isSelected = lesson.id == (activeLesson?.id ?: "")
                            val isDone = completedLessons.contains(lesson.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) AresCyan.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) AresCyan else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { selectedLessonId = lesson.id }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lesson.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) AresCyan else AresTextPrimary, fontSize = 13.sp)
                                    Text(lesson.physicalUnits, fontSize = 10.sp, color = AresTextSecondary)
                                }
                                if (isDone) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AresGreen, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Right Panel: Interactive Lesson Viewer & Sim Sandbox
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = AresSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
            ) {
                if (activeLesson != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Title & Verification Status
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(activeLesson.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                                    Text(activeLesson.physicalUnits, style = MaterialTheme.typography.bodySmall, color = AresCyan, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        completedLessons = completedLessons + activeLesson.id
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (completedLessons.contains(activeLesson.id)) AresGreen else AresCyan
                                    )
                                ) {
                                    Icon(if (completedLessons.contains(activeLesson.id)) Icons.Default.Check else Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (completedLessons.contains(activeLesson.id)) "Verified Complete" else "Mark Complete")
                                }
                            }

                            HorizontalDivider(color = AresBorder)

                            // Description & Key Takeaway
                            Text(activeLesson.description, style = MaterialTheme.typography.bodyMedium, color = AresTextPrimary)

                            Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), shape = RoundedCornerShape(8.dp)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("KEY TAKEAWAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AresGold)
                                    Text(activeLesson.keyTakeaway, style = MaterialTheme.typography.bodySmall, color = AresTextPrimary)
                                }
                            }

                            // Math Formula Display
                            if (activeLesson.formula != null) {
                                Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text("MATHEMATICAL FORMULATION", fontSize = 10.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(activeLesson.formula, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                                    }
                                }
                            }

                            // Interactive Parameter Slider Simulation Sandbox
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Interactive Parameter Tuning Sandbox", style = MaterialTheme.typography.labelMedium, color = AresTextPrimary)
                                    Text("Value: %.3f".format(simParamValue), style = MaterialTheme.typography.labelMedium, color = AresCyan, fontWeight = FontWeight.Bold)
                                }

                                Slider(
                                    value = simParamValue.toFloat(),
                                    onValueChange = { simParamValue = it.toDouble() },
                                    valueRange = activeLesson.minVal.toFloat()..activeLesson.maxVal.toFloat(),
                                    colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan)
                                )
                            }
                        }

                        // Bottom Actions
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onLaunchSimChallenge(activeLesson.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = AresPurple)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Launch Challenge in Physics Sim")
                            }
                        }
                    }
                }
            }
        }
    }
}
