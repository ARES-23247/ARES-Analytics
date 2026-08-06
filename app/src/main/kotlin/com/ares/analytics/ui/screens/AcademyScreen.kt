package com.ares.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AcademyTrack(val title: String, val subtitle: String, val icon: ImageVector, val color: Color) {
    CONTROL_THEORY("Control Theory & Motion Profiling", "PIDF, Feedforwards (kS/kV/kA), S-Curve Motion, LQR", Icons.Default.Tune, AresCyan),
    LOCALIZATION("Localization & Sensor Fusion", "GoBilda Pinpoint, EKF Pose Estimation, Outlier Filtering", Icons.Default.GpsFixed, AresGreen),
    KINEMATICS("Drivetrain Kinematics", "Mecanum Vector Math, Swerve Azimuth & Zero Calibration", Icons.Default.DirectionsCar, AresGold),
    VISION("Vision & AprilTags", "Limelight 3D Pose, Target-Space Alignment, Outlier Rejection", Icons.Default.Videocam, AresPurple),
    PATHFINDING("Pathfinding & Avoidance", "Bezier Splines, PathPlanner Markers, Theta* & VFH+", Icons.Default.Route, AresRed),
    POWER_DIAGNOSTICS("Power & System Diagnostics", "Brownout Protection, .wpilog Analysis, SysId Characterization", Icons.Default.BatteryChargingFull, AresCyan)
}

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class AcademyLesson(
    val id: String,
    val title: String,
    val description: String,
    val formula: String?,
    val physicalUnits: String,
    val keyTakeaway: String,
    val defaultVal: Double = 1.0,
    val minVal: Double = 0.0,
    val maxVal: Double = 10.0,
    val quiz: List<QuizQuestion> = emptyList()
)

@Composable
fun AcademyScreen(
    onLaunchSimChallenge: (String) -> Unit = {}
) {
    var selectedTrack by remember { mutableStateOf(AcademyTrack.CONTROL_THEORY) }
    var selectedLessonId by remember { mutableStateOf("pidf_tuning") }
    var completedLessons by remember { mutableStateOf(setOf<String>()) }
    var simParamValue by remember { mutableStateOf(1.8) }
    var showCertDialog by remember { mutableStateOf(false) }
    var studentNameInput by remember { mutableStateOf("Student Programmer") }
    var certExportPath by remember { mutableStateOf("") }
    var quizAnswers by remember { mutableStateOf(mapOf<Int, Int>()) }
    var quizSubmitted by remember { mutableStateOf(false) }

    val lessonsByTrack = remember {
        mapOf(
            AcademyTrack.CONTROL_THEORY to listOf(
                AcademyLesson(
                    id = "pidf_tuning",
                    title = "PIDF Closed-Loop Control",
                    description = "Proportional, Integral, Derivative, and Feedforward closed-loop position & velocity control.",
                    formula = "u(t) = kP e(t) + kI \\int e(t) dt + kD \\frac{de}{dt} + kF v_{target}",
                    physicalUnits = "Distances: m, Angles: rad, Time: s",
                    keyTakeaway = "Proportional gain kP reduces rise time, kD damps oscillation, kI fixes steady-state offset.",
                    defaultVal = 1.8, minVal = 0.0, maxVal = 5.0,
                    quiz = listOf(
                        QuizQuestion(
                            question = "What is the primary effect of increasing Proportional gain (kP)?",
                            options = listOf("Increases rise speed towards target", "Damps high-frequency oscillation", "Removes motor battery noise", "Limits maximum velocity"),
                            correctIndex = 0,
                            explanation = "kP produces control effort proportional to error, reducing initial rise time towards the setpoint."
                        ),
                        QuizQuestion(
                            question = "Which term prevents overshooting and dampens system oscillation?",
                            options = listOf("Integral (kI)", "Derivative (kD)", "Feedforward (kF)", "Deadband"),
                            correctIndex = 1,
                            explanation = "Derivative gain kD resists the rate of change of error, dampening overshoot."
                        ),
                        QuizQuestion(
                            question = "Why is Feedforward (kF / kS) essential in motor velocity control?",
                            options = listOf("It anticipates expected motor output before error occurs", "It resets the gyro heading", "It increases battery voltage", "It filters vision noise"),
                            correctIndex = 0,
                            explanation = "Feedforward provides baseline voltage based on expected target velocity or static friction."
                        )
                    )
                ),
                AcademyLesson(
                    id = "feedforward_ks_kv_ka",
                    title = "Motor Physics (kS, kV, kA)",
                    description = "Overcoming static friction (kS), back-EMF velocity resistance (kV), and inertia (kA).",
                    formula = "V_{ff} = kS \\text{sgn}(v) + kV v + kA a",
                    physicalUnits = "Voltage: V, Velocity: m/s, Acceleration: m/s²",
                    keyTakeaway = "Static friction kS is critical for Mecanum position hold on foam tile friction.",
                    defaultVal = 0.06, minVal = 0.0, maxVal = 0.25,
                    quiz = listOf(
                        QuizQuestion(
                            question = "Why is static friction feedforward kS added to Mecanum position hold?",
                            options = listOf("To break static wheel friction on foam tiles", "To measure optical odometry", "To invert motor directions", "To lock EKF covariance"),
                            correctIndex = 0,
                            explanation = "Small position errors produce tiny PID outputs below motor breakout voltage. kS adds minimum breakout power."
                        )
                    )
                ),
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

    val totalLessonsCount = lessonsByTrack.values.sumOf { it.size }
    val currentLessons = lessonsByTrack[selectedTrack] ?: emptyList()
    val activeLesson = currentLessons.firstOrNull { it.id == selectedLessonId } ?: currentLessons.firstOrNull()

    // Reset quiz when lesson changes
    LaunchedEffect(selectedLessonId) {
        quizAnswers = emptyMap()
        quizSubmitted = false
    }

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

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = AresGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AresGreen)
                    ) {
                        Text(
                            text = "Completed: ${completedLessons.size} / $totalLessonsCount",
                            color = AresGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    if (completedLessons.size == totalLessonsCount) {
                        Button(
                            onClick = { showCertDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AresGold)
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AresBackground)
                            Spacer(Modifier.width(6.dp))
                            Text("Export Certificate", color = AresBackground, fontWeight = FontWeight.Bold)
                        }
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

            // Right Panel: Interactive Lesson Viewer, Step Response Canvas & Quizzes
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = AresSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
            ) {
                if (activeLesson != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title & Verification Status
                        item {
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
                        }

                        item { HorizontalDivider(color = AresBorder) }

                        // Description & Key Takeaway
                        item {
                            Text(activeLesson.description, style = MaterialTheme.typography.bodyMedium, color = AresTextPrimary)
                        }

                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated), shape = RoundedCornerShape(8.dp)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("KEY TAKEAWAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AresGold)
                                    Text(activeLesson.keyTakeaway, style = MaterialTheme.typography.bodySmall, color = AresTextPrimary)
                                }
                            }
                        }

                        // Math Formula Display
                        if (activeLesson.formula != null) {
                            item {
                                Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text("MATHEMATICAL FORMULATION", fontSize = 10.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(activeLesson.formula, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                                    }
                                }
                            }
                        }

                        // Interactive Step-Response Simulation Canvas
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("LIVE STEP RESPONSE CANVAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                                        Text("Gain: %.2f".format(simParamValue), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AresGold)
                                    }

                                    // Render Animated Curve
                                    StepResponseCanvas(gain = simParamValue)

                                    Slider(
                                        value = simParamValue.toFloat(),
                                        onValueChange = { simParamValue = it.toDouble() },
                                        valueRange = activeLesson.minVal.toFloat()..activeLesson.maxVal.toFloat(),
                                        colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan)
                                    )
                                }
                            }
                        }

                        // Interactive Knowledge Check Mini-Quiz
                        if (activeLesson.quiz.isNotEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = AresGold, modifier = Modifier.size(18.dp))
                                            Text("KNOWLEDGE CHECK QUIZ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AresGold)
                                        }

                                        activeLesson.quiz.forEachIndexed { qIdx, q ->
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("${qIdx + 1}. ${q.question}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary)

                                                q.options.forEachIndexed { optIdx, optText ->
                                                    val isSelected = quizAnswers[qIdx] == optIdx
                                                    val isCorrect = optIdx == q.correctIndex
                                                    val bgCol = when {
                                                        quizSubmitted && isCorrect -> AresGreen.copy(alpha = 0.2f)
                                                        quizSubmitted && isSelected && !isCorrect -> AresRed.copy(alpha = 0.2f)
                                                        isSelected -> AresCyan.copy(alpha = 0.15f)
                                                        else -> AresSurface
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(bgCol)
                                                            .border(1.dp, if (isSelected) AresCyan else AresBorder, RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                if (!quizSubmitted) {
                                                                    quizAnswers = quizAnswers + (qIdx to optIdx)
                                                                }
                                                            }
                                                            .padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("${('A' + optIdx)}. $optText", style = MaterialTheme.typography.bodySmall, color = AresTextPrimary)
                                                    }
                                                }

                                                if (quizSubmitted) {
                                                    Text(q.explanation, style = MaterialTheme.typography.bodySmall, color = AresCyan, fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        Button(
                                            onClick = { quizSubmitted = true },
                                            modifier = Modifier.align(Alignment.End),
                                            colors = ButtonDefaults.buttonColors(containerColor = AresGold)
                                        ) {
                                            Text("Check Answers", color = AresBackground, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Actions
                        item {
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

    // Student Certificate Dialog
    if (showCertDialog) {
        AlertDialog(
            onDismissRequest = { showCertDialog = false },
            title = { Text("ARES Certified Programmer Certificate", fontWeight = FontWeight.Bold, color = AresGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Congratulations! You have completed all 6 ARES Academy tracks.")
                    OutlinedTextField(
                        value = studentNameInput,
                        onValueChange = { studentNameInput = it },
                        label = { Text("Student Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (certExportPath.isNotEmpty()) {
                        Text("Saved certificate to: $certExportPath", color = AresGreen, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val certText = """
                            ==============================================================
                              ARES ROBOTICS CERTIFIED PROGRAMMER CERTIFICATE
                            ==============================================================
                            Student Name: $studentNameInput
                            Date: $date
                            Status: VERIFIED COMPLETE (All 6 Tracks Mastered)
                            
                            Verified Competencies:
                            - Control Theory & Motion Profiling (PIDF, kS/kV/kA, Jerk-Limited S-Curves)
                            - Localization & Sensor Fusion (GoBilda Pinpoint, EKF, Mahalanobis Outliers)
                            - Drivetrain Kinematics (Mecanum Vector Math, Swerve CANcoder Zeroing)
                            - Computer Vision & AprilTags (3D Target-Space Pose, Alignment)
                            - Pathfinding & Avoidance (Bezier Splines, Theta* & VFH+)
                            - Power & System Diagnostics (Brownout Scaling, .wpilog Analysis)
                            ==============================================================
                        """.trimIndent()
                        val file = File("ARES_Certificate_$studentNameInput.txt")
                        file.writeText(certText)
                        certExportPath = file.absolutePath
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AresGold)
                ) {
                    Text("Export Printable Certificate", color = AresBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCertDialog = false }) {
                    Text("Close", color = AresTextSecondary)
                }
            }
        )
    }
}

@Composable
fun StepResponseCanvas(gain: Double) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
    ) {
        val w = size.width
        val h = size.height
        val targetY = h * 0.3f

        // Target Line (Dashed Gold)
        drawLine(
            color = AresGold,
            start = Offset(0f, targetY),
            end = Offset(w, targetY),
            strokeWidth = 2f
        )

        val path = Path()
        val points = 100
        val omega = (gain * 1.5).coerceIn(0.5, 10.0)
        val damping = (2.0 / (gain + 0.1)).coerceIn(0.2, 2.5)

        for (i in 0..points) {
            val t = (i.toFloat() / points) * 5f
            val response = 1.0 - kotlin.math.exp(-damping * t) * kotlin.math.cos(omega * t)
            val px = (i.toFloat() / points) * w
            val py = h - (response.toFloat() * (h - targetY))

            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }

        drawPath(
            path = path,
            color = AresCyan,
            style = Stroke(width = 3f)
        )
    }
}
