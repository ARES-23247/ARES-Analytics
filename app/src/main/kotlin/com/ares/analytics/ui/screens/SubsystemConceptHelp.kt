package com.ares.analytics.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.areslib.subsystem.SubsystemControlLoopDocument
import com.areslib.subsystem.SubsystemControlStrategy
import com.areslib.subsystem.SubsystemFeedforwardKind
import com.areslib.subsystem.SubsystemHomingComparison
import com.areslib.subsystem.SubsystemHomingDocument
import java.awt.Desktop
import java.net.URI
import kotlin.math.*

private const val SUBSYSTEM_GUIDE =
    "https://github.com/ARES-23247/ARES-Analytics/blob/master/docs/SUBSYSTEM_BUILDER.md"

/** Keyboard-focusable, hoverable help for a concept used by the subsystem form. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ConceptHelp(
    title: String,
    explanation: String,
    anchor: String,
    compact: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Column(
                    modifier = Modifier.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(explanation)
                    Text("Press the help button for the full guide.", color = AresCyan)
                }
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = { openSubsystemGuide(anchor) },
            modifier = if (compact) Modifier.size(32.dp) else Modifier,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = "Learn about $title",
                tint = AresCyan,
                modifier = if (compact) Modifier.size(17.dp) else Modifier,
            )
        }
    }
}

@Composable
internal fun HomingConceptLab(homing: SubsystemHomingDocument) {
    if (homing.evidence.isEmpty()) return
    val evidence = homing.evidence.first()
    var numericSignal by remember(evidence.fieldId, evidence.threshold) {
        mutableFloatStateOf((evidence.threshold ?: 1.0).toFloat())
    }
    var booleanSignal by remember(evidence.fieldId) { mutableStateOf(false) }
    var heldMs by remember(homing.method, homing.dwellMs) { mutableFloatStateOf(0f) }
    val conditionMet = when (evidence.comparison) {
        SubsystemHomingComparison.TRUE -> booleanSignal
        SubsystemHomingComparison.FALSE -> !booleanSignal
        SubsystemHomingComparison.AT_OR_ABOVE -> numericSignal.toDouble() >= (evidence.threshold ?: 0.0)
        SubsystemHomingComparison.AT_OR_BELOW -> numericSignal.toDouble() <= (evidence.threshold ?: 0.0)
        SubsystemHomingComparison.ABS_AT_OR_ABOVE -> abs(numericSignal.toDouble()) >= (evidence.threshold ?: 0.0)
        SubsystemHomingComparison.ABS_AT_OR_BELOW -> abs(numericSignal.toDouble()) <= (evidence.threshold ?: 0.0)
    }
    val dwellProgress = if (homing.dwellMs <= 0L) 1f else (heldMs / homing.dwellMs).coerceIn(0f, 1f)
    val wouldHome = conditionMet && dwellProgress >= 1f

    LearningLabCard(
        title = "Try the homing evidence",
        explanation = "This preview does not command hardware. It shows why one sample is not enough: the evidence must remain true for the dwell time before zero is established.",
    ) {
        if (evidence.comparison == SubsystemHomingComparison.TRUE || evidence.comparison == SubsystemHomingComparison.FALSE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${evidence.fieldId} is active", color = AresTextPrimary, modifier = Modifier.weight(1f))
                Switch(checked = booleanSignal, onCheckedChange = { booleanSignal = it })
            }
        } else {
            Text("Observed ${evidence.fieldId}: ${"%.2f".format(numericSignal)}", color = AresTextPrimary)
            Slider(
                value = numericSignal,
                onValueChange = { numericSignal = it },
                valueRange = -20f..20f,
            )
        }
        Text("Evidence held for ${heldMs.toInt()} ms (required ${homing.dwellMs} ms)", color = AresTextSecondary)
        Slider(
            value = heldMs,
            onValueChange = { heldMs = it },
            valueRange = 0f..homing.timeoutMs.coerceAtLeast(1L).toFloat(),
        )
        LinearProgressIndicator(progress = { dwellProgress }, modifier = Modifier.fillMaxWidth())
        Text(
            when {
                !conditionMet -> "Result: keep searching; evidence is not yet true."
                wouldHome -> "Result: neutralize, establish zero, and mark the mechanism homed."
                else -> "Result: evidence is promising, but keep searching until it has dwelled long enough."
            },
            color = if (wouldHome) AresCyan else AresTextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun FeedforwardConceptLab(loop: SubsystemControlLoopDocument) {
    val feedforward = loop.feedforward
    if (feedforward.kind == SubsystemFeedforwardKind.NONE) return
    var velocity by remember(loop.uid, feedforward.kind) { mutableFloatStateOf(0f) }
    var acceleration by remember(loop.uid, feedforward.kind) { mutableFloatStateOf(0f) }
    var angleDegrees by remember(loop.uid, feedforward.kind) { mutableFloatStateOf(0f) }
    val velocityValue = velocity.toDouble()
    val accelerationValue = acceleration.toDouble()
    val static = if (velocity == 0f) 0.0 else feedforward.kS * sign(velocityValue)
    val gravity = when (feedforward.kind) {
        SubsystemFeedforwardKind.NONE, SubsystemFeedforwardKind.SIMPLE_MOTOR -> 0.0
        SubsystemFeedforwardKind.ELEVATOR -> feedforward.kG
        SubsystemFeedforwardKind.ARM -> feedforward.kG * cos(Math.toRadians(angleDegrees.toDouble()))
    }
    val output = static + feedforward.kV * velocityValue + feedforward.kA * accelerationValue + gravity

    LearningLabCard(
        title = "Try the feedforward model",
        explanation = "Feedforward predicts the voltage needed for the requested motion. Feedback then corrects what the prediction missed.",
    ) {
        LabSlider("Requested velocity", velocity, -10f..10f) { velocity = it }
        LabSlider("Requested acceleration", acceleration, -10f..10f) { acceleration = it }
        if (feedforward.kind == SubsystemFeedforwardKind.ARM) {
            LabSlider("Arm angle", angleDegrees, -180f..180f, "°") { angleDegrees = it }
        }
        Text(
            "Predicted output: ${"%.2f".format(output)} V = static ${"%.2f".format(static)} + velocity ${"%.2f".format(feedforward.kV * velocityValue)} + acceleration ${"%.2f".format(feedforward.kA * accelerationValue)} + gravity ${"%.2f".format(gravity)}",
            color = AresTextPrimary,
        )
        Text("The controller adds PID feedback correction after this prediction.", color = AresTextSecondary)
    }
}

// ── Control Theory Interactive Learning Sandbox ───────────────────────────────────────────

enum class MechanismPlantKind(val displayName: String) {
    FLYWHEEL("Velocity Flywheel (Inertia plant)"),
    ARM("Pivoting Arm (Gravity plant)"),
    ELEVATOR("Linear Elevator (Constant gravity plant)")
}

data class StepResponseMetrics(
    val riseTimeSec: Double?,
    val overshootPercent: Double,
    val settlingTimeSec: Double?,
    val steadyStateError: Double,
    val isStable: Boolean
)

enum class SandboxControllerStrategy(val displayName: String) {
    PID_FEEDFORWARD("PID + Feedforward"),
    LINEAR_ADRC("Linear ADRC (Observer)")
}

fun simulateStepResponse(
    plant: MechanismPlantKind,
    strategy: SandboxControllerStrategy,
    kp: Double,
    ki: Double,
    kd: Double,
    ks: Double,
    kv: Double,
    ka: Double,
    kg: Double,
    b0: Double = 1.0,
    omegaO: Double = 20.0,
    omegaC: Double = 10.0,
    simDurationSec: Double = 2.0,
    dt: Double = 0.01
): Pair<List<Pair<Double, Double>>, StepResponseMetrics> {
    val trajectory = mutableListOf<Pair<Double, Double>>()
    var position = 0.0
    var velocity = 0.0
    var integralError = 0.0
    var prevError = 1.0

    // ADRC State Observer (ESO)
    var z1 = 0.0
    var z2 = 0.0
    var z3 = 0.0 // Estimated total disturbance

    val setpoint = 1.0
    val totalSteps = (simDurationSec / dt).toInt()
    var isStable = true

    var firstRiseStep: Int? = null
    var maxPos = 0.0

    for (step in 0..totalSteps) {
        val t = step * dt
        val error = setpoint - (if (plant == MechanismPlantKind.FLYWHEEL) velocity else position)

        if (firstRiseStep == null && (if (plant == MechanismPlantKind.FLYWHEEL) velocity else position) >= 0.90 * setpoint) {
            firstRiseStep = step
        }
        val currentOutput = if (plant == MechanismPlantKind.FLYWHEEL) velocity else position
        if (currentOutput > maxPos) {
            maxPos = currentOutput
        }

        integralError = (integralError + error * dt).coerceIn(-12.0, 12.0)
        val dError = (error - prevError) / dt
        prevError = error

        // Controller output voltage u
        val voltage: Double = when (strategy) {
            SandboxControllerStrategy.LINEAR_ADRC -> {
                val l1 = 2.0 * omegaO
                val l2 = omegaO * omegaO
                val obsError = currentOutput - z1

                z1 += (z2 + b0 * z3 + l1 * obsError) * dt

                val u0 = omegaC * (setpoint - z1)
                val uUnsat = if (abs(b0) > 1e-9) (u0 - z2) / b0 else 0.0
                val u = uUnsat.coerceIn(-12.0, 12.0)

                val isSaturated = u != uUnsat
                val sameSign = sign(obsError) == sign(u - uUnsat)
                if (!(isSaturated && sameSign)) {
                    z2 += (l2 * obsError) * dt
                }
                z3 = u // store previous effort
                u
            }
            else -> {
                // PID Feedback
                val fb = kp * error + ki * integralError + kd * dError

                // Model Feedforward
                val gravityComp = when (plant) {
                    MechanismPlantKind.FLYWHEEL -> 0.0
                    MechanismPlantKind.ELEVATOR -> kg
                    MechanismPlantKind.ARM -> kg * cos(position)
                }
                val ff = ks * sign(setpoint) + kv * setpoint + gravityComp

                (fb + ff).coerceIn(-12.0, 12.0)
            }
        }

        // Physical Plant Dynamics
        when (plant) {
            MechanismPlantKind.FLYWHEEL -> {
                // tau * v_dot + v = K * u (Motor flywheel with back-EMF)
                val tau = 0.25
                val kGain = 0.10
                val vDot = (-velocity + kGain * voltage * 12.0) / tau
                velocity += vDot * dt
                position += velocity * dt
            }
            MechanismPlantKind.ARM -> {
                // J * theta_ddot + b * theta_dot + mgl * cos(theta) = Kt * u
                val inertia = 0.15
                val damping = 0.40
                val gravityTorque = 2.5 * cos(position)
                val motorTorque = 1.8 * voltage
                val accel = (motorTorque - damping * velocity - gravityTorque) / inertia
                velocity += accel * dt
                position += velocity * dt
            }
            MechanismPlantKind.ELEVATOR -> {
                // m * x_ddot + b * x_dot + m*g = Kt * u
                val mass = 2.0
                val damping = 1.0
                val gravityForce = mass * 9.81 * 0.20
                val motorForce = 3.5 * voltage
                val accel = (motorForce - damping * velocity - gravityForce) / mass
                velocity += accel * dt
                position += velocity * dt
            }
        }

        if (!position.isFinite() || !velocity.isFinite() || abs(position) > 50.0) {
            isStable = false
            break
        }

        trajectory.add(t to (if (plant == MechanismPlantKind.FLYWHEEL) velocity else position))
    }

    val riseTimeSec = firstRiseStep?.let { it * dt }
    val overshootPercent = if (isStable && maxPos > setpoint) ((maxPos - setpoint) / setpoint) * 100.0 else 0.0

    // Settling time: within +/- 5% of setpoint
    var lastOutIndex: Int? = null
    for (i in trajectory.indices) {
        val y = trajectory[i].second
        if (abs(y - setpoint) > 0.05 * setpoint) {
            lastOutIndex = i
        }
    }
    val settlingTimeSec = if (isStable && lastOutIndex != null && lastOutIndex < trajectory.size - 1) {
        lastOutIndex * dt
    } else if (isStable && lastOutIndex == null) {
        0.0
    } else {
        null
    }

    val finalY = trajectory.lastOrNull()?.second ?: 0.0
    val steadyStateError = if (isStable) abs(finalY - setpoint) else 999.0

    return trajectory to StepResponseMetrics(
        riseTimeSec = riseTimeSec,
        overshootPercent = overshootPercent,
        settlingTimeSec = settlingTimeSec,
        steadyStateError = steadyStateError,
        isStable = isStable
    )
}

@Composable
internal fun ControlTheorySandboxLab(loop: SubsystemControlLoopDocument) {
    var plant by remember {
        mutableStateOf(
            when (loop.feedforward.kind) {
                SubsystemFeedforwardKind.ARM -> MechanismPlantKind.ARM
                SubsystemFeedforwardKind.ELEVATOR -> MechanismPlantKind.ELEVATOR
                else -> MechanismPlantKind.FLYWHEEL
            }
        )
    }

    var controllerStrategy by remember { mutableStateOf(SandboxControllerStrategy.PID_FEEDFORWARD) }
    var kp by remember(loop.uid) { mutableFloatStateOf(loop.kP.toFloat().coerceAtLeast(1.0f)) }
    var ki by remember(loop.uid) { mutableFloatStateOf(loop.kI.toFloat()) }
    var kd by remember(loop.uid) { mutableFloatStateOf(loop.kD.toFloat()) }
    var ks by remember(loop.uid) { mutableFloatStateOf(loop.feedforward.kS.toFloat()) }
    var kv by remember(loop.uid) { mutableFloatStateOf(loop.feedforward.kV.toFloat().coerceAtLeast(0.5f)) }
    var kg by remember(loop.uid) { mutableFloatStateOf(loop.feedforward.kG.toFloat()) }
    var showTheory by remember { mutableStateOf(false) }

    val (trajectory, metrics) = remember(plant, controllerStrategy, kp, ki, kd, ks, kv, kg) {
        simulateStepResponse(
            plant = plant,
            strategy = controllerStrategy,
            kp = kp.toDouble(),
            ki = ki.toDouble(),
            kd = kd.toDouble(),
            ks = ks.toDouble(),
            kv = kv.toDouble(),
            ka = 0.0,
            kg = kg.toDouble()
        )
    }

    LearningLabCard(
        title = "Interactive Control Theory Sandbox: Live Step-Response Plant",
        explanation = "Simulates your control gains on a dynamic 1-DOF physics model to visualize rise time, overshoot, and settling stability."
    ) {
        // Plant Selector & Strategy Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Plant:", color = AresTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                MechanismPlantKind.entries.forEach { kind ->
                    val isSelected = plant == kind
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) AresCyan.copy(alpha = 0.2f) else AresSurfaceElevated)
                            .border(1.dp, if (isSelected) AresCyan else AresBorder, RoundedCornerShape(6.dp))
                            .clickable { plant = kind }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = kind.displayName.split(" ").first(),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AresCyan else AresTextSecondary
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SandboxControllerStrategy.entries.forEach { strat ->
                    val isSelected = controllerStrategy == strat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) AresGold.copy(alpha = 0.2f) else AresSurfaceElevated)
                            .border(1.dp, if (isSelected) AresGold else AresBorder, RoundedCornerShape(6.dp))
                            .clickable { controllerStrategy = strat }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = strat.name.split("_").first(),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AresGold else AresTextSecondary
                        )
                    }
                }
            }
        }

        // Live 2D Step Response Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AresBackground)
                .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw setpoint line at 1.0 (60% up from bottom)
                val setpointY = h * 0.35f
                drawLine(
                    color = AresTextTertiary.copy(alpha = 0.5f),
                    start = Offset(0f, setpointY),
                    end = Offset(w, setpointY),
                    strokeWidth = 1.5f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )

                // Draw tolerance envelope (+/- 5%)
                drawRect(
                    color = AresGreen.copy(alpha = 0.08f),
                    topLeft = Offset(0f, setpointY - h * 0.05f),
                    size = androidx.compose.ui.geometry.Size(w, h * 0.10f)
                )

                // Draw trajectory
                if (trajectory.isNotEmpty() && metrics.isStable) {
                    val path = Path()
                    val maxT = 2.0f
                    val scaleY = (h * 0.65f) / 1.5f // 1.5 setpoint ceiling

                    trajectory.forEachIndexed { index, (t, y) ->
                        val px = (t.toFloat() / maxT) * w
                        val py = (h * 0.90f) - (y.toFloat() * scaleY)
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }

                    drawPath(
                        path = path,
                        color = if (metrics.overshootPercent > 25.0) AresAmber else AresCyan,
                        style = Stroke(width = 2.5f)
                    )
                }
            }

            // Overlay Legend
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Target: 1.00", color = AresTextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text = if (metrics.isStable) "STABLE" else "UNSTABLE / DIVERGENT",
                    color = if (metrics.isStable) AresGreen else AresError,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Extracted Performance Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricPill("Rise Time (tr)", metrics.riseTimeSec?.let { "%.2fs".format(it) } ?: "N/A", AresCyan)
            MetricPill("Overshoot (%)", "%.1f%%".format(metrics.overshootPercent), if (metrics.overshootPercent > 20.0) AresAmber else AresGreen)
            MetricPill("Settling Time (ts)", metrics.settlingTimeSec?.let { "%.2fs".format(it) } ?: ">2.0s", AresGold)
            MetricPill("Steady Error (ess)", "%.3f".format(metrics.steadyStateError), if (metrics.steadyStateError > 0.05) AresError else AresGreen)
        }

        // Interactive Gain Sliders
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabSlider("Proportional Gain (kP)", kp, 0f..25f) { kp = it }
            LabSlider("Integral Gain (kI)", ki, 0f..10f) { ki = it }
            LabSlider("Derivative Gain (kD)", kd, 0f..5f) { kd = it }
            if (plant != MechanismPlantKind.FLYWHEEL) {
                LabSlider("Gravity Compensation (kG)", kg, 0f..5f, " V") { kg = it }
            }
            LabSlider("Velocity Feedforward (kV)", kv, 0f..3f) { kv = it }
        }

        // Educational Theory Deep-Dive Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showTheory = !showTheory }) {
                Icon(if (showTheory) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showTheory) "Hide Control Fundamentals" else "Learn How PID & Feedforward Work", fontSize = 11.sp, color = AresCyan)
            }
        }

        if (showTheory) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AresSurface)
                    .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TheoryConceptRow(
                    term = "kP (Proportional)",
                    summary = "Virtual Spring Constant",
                    detail = "Acts like a restorative spring: force is proportional to position error (F = -kP * e). Higher kP speeds up response but causes ringing/overshoot if damping is low."
                )
                TheoryConceptRow(
                    term = "kI (Integral)",
                    summary = "Steady-State Eliminator",
                    detail = "Accumulates past errors over time (∫ e dt) to push past static friction and gravity sag. Warning: excess kI causes windup and slow unstable hunting oscillations."
                )
                TheoryConceptRow(
                    term = "kD (Derivative)",
                    summary = "Virtual Damper / Shock Absorber",
                    detail = "Opposes the rate of change of error (-kD * de/dt). Dampens oscillations and suppresses overshoot, but amplifies high-frequency encoder noise."
                )
                TheoryConceptRow(
                    term = "Feedforward (kS, kV, kA, kG)",
                    summary = "Physics-Based Predictive Control",
                    detail = "Calculates the exact voltage required by physics before feedback reacts: V = kS*sign(v) + kV*v + kA*a + kG(θ). Handles 90% of motor effort so PID only fixes small residuals."
                )
                TheoryConceptRow(
                    term = "Linear ADRC",
                    summary = "Active Disturbance Rejection",
                    detail = "Uses an Extended State Observer (ESO) to estimate total disturbance (friction, load mass, battery drop) in real time without needing complex math models or integral windup."
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = AresTextTertiary, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TheoryConceptRow(term: String, summary: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(term, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AresCyan)
            Text("— $summary", fontSize = 10.sp, color = AresTextSecondary, fontWeight = FontWeight.Medium)
        }
        Text(detail, fontSize = 10.sp, color = AresTextTertiary, lineHeight = 14.sp)
    }
}

@Composable
private fun LearningLabCard(
    title: String,
    explanation: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    Text(explanation, color = AresTextSecondary, fontSize = 12.sp)
                }
                ConceptHelp(title, explanation, if (title.contains("homing", true)) "homing" else "feedforward")
            }
            content()
            OutlinedButton(onClick = { openSubsystemGuide(if (title.contains("homing", true)) "homing" else "feedforward") }) {
                Text("Read the full explanation")
            }
        }
    }
}

@Composable
private fun LabSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    onChange: (Float) -> Unit,
) {
    Text("$label: ${"%.2f".format(value)}$suffix", color = AresTextPrimary)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

private fun openSubsystemGuide(anchor: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI("$SUBSYSTEM_GUIDE#$anchor"))
        }
    }
}
