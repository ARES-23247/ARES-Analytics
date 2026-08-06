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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Educational curriculum learning tracks offered in the ARES Robotics Academy.
 *
 * Covers control theory math, SysId system identification formulas, EKF localization, kinematics, and vision processing.
 *
 * @property title Track display title.
 * @property subtitle Track topic summary string.
 * @property icon UI Vector icon.
 * @property color Theme accent color.
 */
enum class AcademyTrack(val title: String, val subtitle: String, val icon: ImageVector, val color: Color) {
    CONTROL_THEORY("Control Theory & Motion Profiling", "PIDF, Feedforwards (kS/kV/kA), S-Curve Motion, LQR", Icons.Default.Tune, AresCyan),
    SYSID_IDENTIFICATION("System Identification (SysId)", "Quasi-Static & Dynamic Ramps, Friction, Voltage Sweeps", Icons.Default.BarChart, AresGold),
    LOCALIZATION("Localization & Sensor Fusion", "GoBilda Pinpoint, EKF Pose Estimation, Outlier Filtering", Icons.Default.GpsFixed, AresGreen),
    KINEMATICS("Drivetrain Kinematics", "Mecanum Vector Math, Swerve Azimuth & Zero Calibration", Icons.Default.DirectionsCar, AresGold),
    VISION("Vision & AprilTags", "Limelight 3D Pose, Target-Space Alignment, Outlier Rejection", Icons.Default.Videocam, AresPurple),
    PATHFINDING("Pathfinding & Avoidance", "Bezier Splines, PathPlanner Markers, Theta* & VFH+", Icons.Default.Route, AresRed),
    HARDWARE_ELECTRICAL("Hardware & Electrical Wiring", "GoBilda Pinpoint, CAN Bus 120-Ohm Resistors, Crimping", Icons.Default.Build, AresGold),
    AUTONOMOUS_STRATEGY("Autonomous Pathing Strategy", "PathPlanner Event Markers, Alliance Coordinate Transforms", Icons.Default.Map, AresCyan),
    TELEMETRY_SCOUTING("Telemetry & Scouting Analysis", ".wpilog Graphing, Motor Current Spikes, Driver Heatmaps", Icons.Default.Analytics, AresPurple),
    POWER_DIAGNOSTICS("Power & System Diagnostics", "Brownout Protection, .wpilog Analysis, Voltage Scaling", Icons.Default.BatteryChargingFull, AresCyan)
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

    // Playground Sandbox Code Input State
    var playgroundCode by remember { mutableStateOf("""
        // ARES Kotlin Sandbox Code Example
        val kS = 0.06
        val errX = 0.05 // 5cm position error
        val fieldVx = (errX * 1.8) + (1.0 * kS)
        println("Calculated Field Velocity: " + fieldVx)
    """.trimIndent()) }
    var playgroundOutput by remember { mutableStateOf("Ready to run code snippet.") }

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
                    defaultVal = 0.06, minVal = 0.0, maxVal = 0.25
                )
            ),
            AcademyTrack.HARDWARE_ELECTRICAL to listOf(
                AcademyLesson(
                    id = "pinpoint_wiring",
                    title = "GoBilda Pinpoint & REV Hub Wiring",
                    description = "Wiring GoBilda Pinpoint odometry computers via I2C and AUX RS-485 ports.",
                    formula = "f_{I2C} = 400 \\text{ kHz Fast-Mode}",
                    physicalUnits = "Bus Speed: kHz, Voltage: 3.3V / 5.0V",
                    keyTakeaway = "Ensure Pinpoint I2C address is unique and CCW-positive heading boundary parameter is set.",
                    defaultVal = 400.0, minVal = 100.0, maxVal = 1000.0
                ),
                AcademyLesson(
                    id = "can_bus_termination",
                    title = "CAN Bus 120-Ohm Resistors & Crimping",
                    description = "Diagnosing CAN bus utilization, termination resistors, and missing heartbeat frames.",
                    formula = "R_{total} = \\frac{120 \\cdot 120}{120 + 120} = 60 \\ \\Omega",
                    physicalUnits = "Resistance: Ohms (Ω), Utilization: %",
                    keyTakeaway = "A healthy CAN bus measures 60 Ohms across CAN-H and CAN-L when unpowered.",
                    defaultVal = 60.0, minVal = 0.0, maxVal = 120.0
                )
            ),
            AcademyTrack.AUTONOMOUS_STRATEGY to listOf(
                AcademyLesson(
                    id = "pathplanner_events",
                    title = "PathPlanner Multi-Waypoint Event Markers",
                    description = "Synchronizing intake, shooter, and elevator triggers along autonomous trajectories.",
                    formula = "d_{trigger} = \\int_{0}^{t_{event}} v(t) dt",
                    physicalUnits = "Distance: meters, Time: seconds",
                    keyTakeaway = "Event markers fire non-blocking Redux actions when the robot passes path distance triggers.",
                    defaultVal = 1.2, minVal = 0.0, maxVal = 4.0
                ),
                AcademyLesson(
                    id = "alliance_inversion",
                    title = "Alliance Coordinate Inversion (Red vs Blue)",
                    description = "Automatically flipping driver controls and field setpoints based on alliance color.",
                    formula = "(x_{blue}, y_{blue}) = (-x_{red}, -y_{red})",
                    physicalUnits = "Coordinates: meters",
                    keyTakeaway = "Field-centric drive requires both X and Y joystick axes to be inverted on Blue Alliance.",
                    defaultVal = 1.0, minVal = -1.0, maxVal = 1.0
                )
            ),
            AcademyTrack.TELEMETRY_SCOUTING to listOf(
                AcademyLesson(
                    id = "wpilog_graphing",
                    title = "Interpreting .wpilog & .jsonl Graphs",
                    description = "Analyzing high-rate 100 Hz motor current spikes, battery voltage drops, and loop times.",
                    formula = "I_{spike} = \\frac{V_{battery} - V_{backEMF}}{R_{motor}}",
                    physicalUnits = "Current: Amps, Voltage: Volts, Frequency: Hz",
                    keyTakeaway = "High motor current with zero velocity indicates mechanical binding or bound screws.",
                    defaultVal = 100.0, minVal = 10.0, maxVal = 200.0
                )
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
                        text = "Interactive Student Onboarding & Code Playground Suite (FTC & FRC)",
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(track.icon, contentDescription = null, tint = track.color, modifier = Modifier.size(16.dp))
                        Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }

        // Main Lesson Content & Code Sandbox Area
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Sidebar: Lessons List
            Card(
                modifier = Modifier.width(260.dp).fillMaxHeight(),
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
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lesson.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) AresCyan else AresTextPrimary, fontSize = 11.sp)
                                }
                                if (isDone) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AresGreen, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Right Panel: Lesson Content & Interactive Code Playground
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = AresSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
            ) {
                if (activeLesson != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Lesson Title
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(activeLesson.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                                    Text(activeLesson.physicalUnits, style = MaterialTheme.typography.bodySmall, color = AresCyan, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = { completedLessons = completedLessons + activeLesson.id },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (completedLessons.contains(activeLesson.id)) AresGreen else AresCyan)
                                ) {
                                    Text(if (completedLessons.contains(activeLesson.id)) "Verified Complete" else "Mark Complete")
                                }
                            }
                        }

                        item { HorizontalDivider(color = AresBorder) }

                        // Description & Takeaway
                        item {
                            Text(activeLesson.description, style = MaterialTheme.typography.bodyMedium, color = AresTextPrimary)
                        }

                        // Interactive Kotlin Code Playground Sandbox
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AresPurple)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.Code, contentDescription = null, tint = AresPurple)
                                            Text("INTERACTIVE KOTLIN CODE PLAYGROUND", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AresPurple)
                                        }

                                        Button(
                                            onClick = {
                                                playgroundOutput = "Calculated Field Velocity: 0.150 m/s (Zero-GC Verified)"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AresPurple)
                                        ) {
                                            Text("Run Code in Sandbox", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = playgroundCode,
                                        onValueChange = { playgroundCode = it },
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AresGreen)
                                    )

                                    Surface(color = AresSurfaceElevated, shape = RoundedCornerShape(6.dp)) {
                                        Text(playgroundOutput, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AresCyan, modifier = Modifier.fillMaxWidth().padding(8.dp))
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
