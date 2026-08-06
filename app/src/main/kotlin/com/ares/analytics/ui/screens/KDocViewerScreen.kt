package com.ares.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*

data class KDocSymbol(
    val id: String,
    val name: String,
    val category: String,
    val signature: String,
    val summary: String,
    val description: String,
    val formula: String?,
    val physicalUnits: String,
    val codeSnippet: String,
    val aiExplanation: String
)

@Composable
fun KDocViewerScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedSymbolId by remember { mutableStateOf("holonomic_drive_facade") }
    var userAiPrompt by remember { mutableStateOf("") }
    var aiChatHistory by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var isAiThinking by remember { mutableStateOf(false) }

    val categories = remember { listOf("All", "Drivetrain", "Control & Math", "Localization & EKF", "Pathfinding", "Hardware & IO", "State & Redux") }

    val kdocSymbols = remember {
        listOf(
            KDocSymbol(
                id = "holonomic_drive_facade",
                name = "HolonomicDriveFacade",
                category = "Drivetrain",
                signature = "abstract class HolonomicDriveFacade(store: Store, headingGains: PIDFCoefficients)",
                summary = "Master student-facing facade for Mecanum and Swerve holonomic drivetrains.",
                description = "Standardizes field-relative joystick control, heading locking, static friction feedforward (kS), and closed-loop position hold. Inverts joystick inputs automatically for Blue Alliance.",
                formula = "v_{field} = R(-\\theta) v_{robot} + \\hat{e}_{dir} \\cdot kS",
                physicalUnits = "Positions: m, Velocities: m/s, Angles: rad (CCW+)",
                codeSnippet = """
                    // FTC Holonomic Drive Setup
                    val robot = ftcMecanumRobot(hardwareMap) {
                        setHeadingDeadzone(1.0)
                        enablePositionHold(true)
                    }
                    robot.driveFieldCentric(gamepad1.left_stick_x, gamepad1.left_stick_y, gamepad1.right_stick_x)
                """.trimIndent(),
                aiExplanation = "HolonomicDriveFacade manages field-centric driving by taking driver joystick inputs and rotating them by the gyro heading angle θ before computing wheel velocities. Position hold applies static friction feedforward kS (0.06) to overcome foam tile scrubbing friction."
            ),
            KDocSymbol(
                id = "lqr_controller",
                name = "LQRController",
                category = "Control & Math",
                signature = "class LQRController(numStates: Int, numInputs: Int, Q: Matrix, R: Matrix)",
                summary = "Linear Quadratic Regulator for optimal multi-variable state-space feedback control.",
                description = "Solves Discrete Algebraic Riccati Equations (DARE) offline or online to compute optimal feedback gain matrix K. Minimizes state error cost x^T Q x while penalizing control effort u^T R u.",
                formula = "u = -K x; \\quad J = \\int_0^\\infty (x^T Q x + u^T R u) dt",
                physicalUnits = "State Vector x: [pos, vel]^T, Output: Volts or Motor Power",
                codeSnippet = """
                    val lqr = LQRController(
                        numStates = 2, numInputs = 1,
                        qDiag = doubleArrayOf(1.0, 0.1),
                        rDiag = doubleArrayOf(0.01)
                    )
                    val u = lqr.calculate(yMeasured = doubleArrayOf(pos, vel), xRef = doubleArrayOf(targetPos, 0.0), dt = 0.02)
                """.trimIndent(),
                aiExplanation = "LQR optimal control calculates the best possible feedback gains K for multi-variable systems (like arm elevators or swerve modules) by balancing setpoint tracking error against motor voltage usage."
            ),
            KDocSymbol(
                id = "ekf_pose_estimator",
                name = "EKFPoseEstimator",
                category = "Localization & EKF",
                signature = "class EKFPoseEstimator(qPos: Double, qRot: Double, rVisionPos: Double)",
                summary = "Extended Kalman Filter for 100 Hz odometry and 20 Hz AprilTag vision fusion.",
                description = "Executes state prediction at 100 Hz using wheel odometry or GoBilda Pinpoint. Corrects pose estimates at 20 Hz using Limelight AprilTag vision measurements with Mahalanobis outlier filtering.",
                formula = "\\hat{x}_k = \\hat{x}_k^- + K_k (z_k - H \\hat{x}_k^-)",
                physicalUnits = "Pose: (x, y, θ) in meters and radians",
                codeSnippet = """
                    val ekf = EKFPoseEstimator()
                    ekf.predict(deltaPoseMeters, dtSeconds)
                    if (mahalanobisDistance < 18.0) {
                        ekf.correctWithVision(visionMeasurement)
                    }
                """.trimIndent(),
                aiExplanation = "EKFPoseEstimator fuses fast 100 Hz odometry with 20 Hz AprilTag vision. If an AprilTag is corrupted by motion blur, Mahalanobis outlier filtering rejects the update so the robot pose doesn't jump."
            ),
            KDocSymbol(
                id = "theta_star_planner",
                name = "ThetaStarPlanner",
                category = "Pathfinding",
                signature = "object ThetaStarPlanner",
                summary = "Any-angle global pathfinder eliminating grid-line zigzag artifacts.",
                description = "Executes Bresenham line-of-sight checks during A* node expansion to connect parent nodes directly to reachable neighbors, yielding mathematically optimal straight paths around obstacle inflation boundaries.",
                formula = "J_{cost} = d_{target} + w_{obs} \\cdot \\text{costmap}(x, y)",
                physicalUnits = "Waypoints: meters (m), Costmap: meters/cell",
                codeSnippet = """
                    val waypoints = ThetaStarPlanner.plan(
                        costmap = robotCostmap,
                        start = Translation2d(0.5, 0.5),
                        end = Translation2d(2.5, 1.8)
                    )
                """.trimIndent(),
                aiExplanation = "ThetaStarPlanner plans paths around obstacles without being trapped on a grid. It performs line-of-sight checks to shortcut corners, producing smooth, direct paths."
            )
        )
    }

    val filteredSymbols = remember(searchQuery, selectedCategory) {
        kdocSymbols.filter { symbol ->
            val matchesCat = selectedCategory == "All" || symbol.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isEmpty() ||
                    symbol.name.contains(searchQuery, ignoreCase = true) ||
                    symbol.summary.contains(searchQuery, ignoreCase = true) ||
                    symbol.description.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    val activeSymbol = kdocSymbols.firstOrNull { it.id == selectedSymbolId } ?: kdocSymbols.first()

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
                        Icon(Icons.Default.Book, contentDescription = null, tint = AresCyan, modifier = Modifier.size(28.dp))
                        Text(
                            text = "ARESLib KDoc API Explorer & AI Co-Pilot",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = AresTextPrimary
                        )
                    }
                    Text(
                        text = "Interactive API Reference, Mathematical Formulations & AI Code Assistant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AresTextSecondary
                    )
                }

                // Search Input Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search API symbol...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AresCyan) },
                    modifier = Modifier.width(300.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Category Pills Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = cat == selectedCategory
                Surface(
                    color = if (isSelected) AresCyan.copy(alpha = 0.2f) else AresSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AresCyan else AresBorder),
                    modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) AresCyan else AresTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Main Split Screen Content
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Sidebar: Symbol Catalog
            Card(
                modifier = Modifier.width(300.dp).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = AresSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("API SYMBOLS (${filteredSymbols.size})", style = MaterialTheme.typography.labelMedium, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredSymbols) { symbol ->
                            val isSelected = symbol.id == activeSymbol.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) AresCyan.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) AresCyan else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { selectedSymbolId = symbol.id }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(symbol.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) AresCyan else AresTextPrimary, fontSize = 13.sp)
                                    Text(symbol.summary, fontSize = 10.sp, color = AresTextSecondary, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // Center Panel: KDoc Class Viewer & Code Snippet
            Card(
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = AresSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(activeSymbol.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                            Surface(color = AresCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AresCyan)) {
                                Text(activeSymbol.category, color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }

                    item { HorizontalDivider(color = AresBorder) }

                    item {
                        Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(6.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)) {
                            Text(activeSymbol.signature, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AresGold, modifier = Modifier.padding(10.dp))
                        }
                    }

                    item {
                        Text("DESCRIPTION & KDOC", style = MaterialTheme.typography.labelMedium, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                        Text(activeSymbol.description, style = MaterialTheme.typography.bodyMedium, color = AresTextPrimary)
                    }

                    if (activeSymbol.formula != null) {
                        item {
                            Surface(color = Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text("MATHEMATICAL FORMULATION", fontSize = 10.sp, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(activeSymbol.formula, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AresCyan)
                                }
                            }
                        }
                    }

                    item {
                        Text("PHYSICAL UNITS & CONVENTIONS", style = MaterialTheme.typography.labelMedium, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                        Text(activeSymbol.physicalUnits, style = MaterialTheme.typography.bodySmall, color = AresCyan)
                    }

                    item {
                        Text("EXAMPLE KOTLIN USAGE", style = MaterialTheme.typography.labelMedium, color = AresTextSecondary, fontWeight = FontWeight.Bold)
                        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)) {
                            Text(activeSymbol.codeSnippet, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AresGreen, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            // Right Panel: AI Co-Pilot Assistant ("Ask ARES AI")
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AresPurple, modifier = Modifier.size(20.dp))
                            Text("Ask ARES AI Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                        }

                        HorizontalDivider(color = AresBorder)

                        // AI Pre-loaded Explanation for Active Symbol
                        Card(colors = CardDefaults.cardColors(containerColor = AresSurface), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AresPurple.copy(alpha = 0.4f))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = AresPurple, modifier = Modifier.size(16.dp))
                                    Text("AI Insight: ${activeSymbol.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AresPurple)
                                }
                                Text(activeSymbol.aiExplanation, style = MaterialTheme.typography.bodySmall, color = AresTextPrimary, fontSize = 11.sp)
                            }
                        }

                        // Chat History
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(aiChatHistory) { (q, a) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("You: $q", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = AresCyan)
                                    Text("ARES AI: $a", style = MaterialTheme.typography.bodySmall, color = AresTextPrimary)
                                }
                            }
                        }
                    }

                    // Prompt Input Box
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = userAiPrompt,
                            onValueChange = { userAiPrompt = it },
                            placeholder = { Text("Ask AI about ${activeSymbol.name}...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                if (userAiPrompt.isNotEmpty()) {
                                    val prompt = userAiPrompt
                                    userAiPrompt = ""
                                    val answer = when {
                                        prompt.contains("hold", ignoreCase = true) -> "Position hold in ${activeSymbol.name} calculates proportional target position error and adds static friction feedforward kS (0.06) to overcome foam tile scrubbing friction."
                                        prompt.contains("unit", ignoreCase = true) -> "ARESLib standardizes distances in meters (m), angles in radians (rad, CCW+), velocities in m/s, and time in seconds."
                                        else -> "In ${activeSymbol.name}, the algorithm operates deterministically without GC allocations on 50Hz update loops."
                                    }
                                    aiChatHistory = aiChatHistory + (prompt to answer)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AresPurple)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
