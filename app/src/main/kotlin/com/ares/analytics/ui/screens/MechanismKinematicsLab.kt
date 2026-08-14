package com.ares.analytics.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import kotlin.math.*

enum class RoboticsMotor(
    val displayName: String,
    val freeRpm: Double,
    val stallTorqueNm: Double,
    val stallCurrentAmps: Double,
    val freeCurrentAmps: Double
) {
    KRAKEN_X60("Kraken X60 (FRC)", 6000.0, 7.09, 366.0, 1.5),
    FALCON_500("Falcon 500 (FRC)", 6380.0, 4.69, 257.0, 1.5),
    NEO("REV NEO (FRC)", 5676.0, 2.60, 105.0, 1.8),
    NEO_VORTEX("NEO Vortex (FRC)", 6784.0, 3.60, 211.0, 3.6),
    REV_HD_HEX_20("REV HD Hex 20:1 (FTC)", 300.0, 2.10, 10.5, 0.4),
    REV_HD_HEX_40("REV HD Hex 40:1 (FTC)", 150.0, 4.20, 10.5, 0.4),
    REV_CORE_HEX("REV Core Hex (FTC)", 125.0, 3.20, 4.4, 0.2)
}

data class MotorSizingAnalysis(
    val outputSpeedRpm: Double,
    val outputStallTorqueNm: Double,
    val loadTorqueNm: Double,
    val stallMargin: Double,
    val estimatedCurrentDrawAmps: Double,
    val travelTimeSec: Double,
    val isSafe: Boolean,
    val warningMessage: String?
)

object KinematicsMath {

    fun calculateMotorSizing(
        motor: RoboticsMotor,
        motorCount: Int = 1,
        gearRatio: Double = 25.0,
        efficiency: Double = 0.85,
        mechanismKind: String = "ARM", // "ARM", "ELEVATOR", "FLYWHEEL"
        massKg: Double = 3.0,
        armLengthM: Double = 0.50,
        spoolRadiusM: Double = 0.025,
        travelDistance: Double = 1.0 // radians or meters
    ): MotorSizingAnalysis {
        val totalRatio = gearRatio.coerceAtLeast(0.1)
        val outRpm = motor.freeRpm / totalRatio
        val outStallTorque = motor.stallTorqueNm * totalRatio * motorCount * efficiency

        val g = 9.81
        val loadTorque = when (mechanismKind) {
            "ARM" -> massKg * g * (armLengthM * 0.5) // CG at midpoint
            "ELEVATOR" -> (massKg * g * spoolRadiusM) / efficiency.coerceAtLeast(0.1)
            else -> 0.10 // small rotational friction for flywheel
        }

        val stallMargin = if (loadTorque > 1e-4) outStallTorque / loadTorque else 99.0
        val torqueFraction = (loadTorque / outStallTorque.coerceAtLeast(1e-4)).coerceIn(0.0, 1.0)
        val currentDraw = motorCount * (motor.freeCurrentAmps + torqueFraction * (motor.stallCurrentAmps - motor.freeCurrentAmps))

        val loadedRpm = outRpm * (1.0 - torqueFraction * 0.5).coerceAtLeast(0.1)
        val loadedRadPerSec = (loadedRpm * 2.0 * PI) / 60.0
        val linearSpeed = if (mechanismKind == "ELEVATOR") loadedRadPerSec * spoolRadiusM else loadedRadPerSec
        val travelTime = if (linearSpeed > 1e-4) travelDistance / linearSpeed else 99.0

        val isSafe = stallMargin >= 1.8 && currentDraw <= (40.0 * motorCount)
        val warning = when {
            stallMargin < 1.0 -> "STALL DETECTED: Motor will stall and overheat under load!"
            stallMargin < 1.8 -> "LOW TORQUE MARGIN (<1.8x): Risk of tripping breakers during fast maneuvers."
            currentDraw > (40.0 * motorCount) -> "HIGH CONTINUOUS CURRENT (>40A/motor): Risk of battery brownout."
            else -> null
        }

        return MotorSizingAnalysis(
            outputSpeedRpm = outRpm,
            outputStallTorqueNm = outStallTorque,
            loadTorqueNm = loadTorque,
            stallMargin = stallMargin,
            estimatedCurrentDrawAmps = currentDraw,
            travelTimeSec = travelTime,
            isSafe = isSafe,
            warningMessage = warning
        )
    }
}

@Composable
fun MechanismKinematicsLabCard(
    modifier: Modifier = Modifier
) {
    var selectedMotor by remember { mutableStateOf(RoboticsMotor.NEO) }
    var motorCount by remember { mutableIntStateOf(1) }
    var mechanismType by remember { mutableStateOf("ARM") }
    var gearRatio by remember { mutableFloatStateOf(40.0f) }
    var massKg by remember { mutableFloatStateOf(3.0f) }
    var armLengthM by remember { mutableFloatStateOf(0.45f) }
    var showTheory by remember { mutableStateOf(false) }

    val analysis = remember(selectedMotor, motorCount, mechanismType, gearRatio, massKg, armLengthM) {
        KinematicsMath.calculateMotorSizing(
            motor = selectedMotor,
            motorCount = motorCount,
            gearRatio = gearRatio.toDouble(),
            mechanismKind = mechanismType,
            massKg = massKg.toDouble(),
            armLengthM = armLengthM.toDouble(),
            travelDistance = if (mechanismType == "ARM") Math.toRadians(90.0) else 0.80
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mechanism Kinematics & Motor Sizing Advisor", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    Text("Calculate gear ratios, stall torque margins, and battery current draws before cutting metal.", color = AresTextSecondary, fontSize = 12.sp)
                }
            }

            // Motor Selector Chips
            Text("Select Motor:", color = AresTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoboticsMotor.entries.take(4).forEach { motor ->
                    val isSelected = selectedMotor == motor
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) AresCyan.copy(alpha = 0.2f) else AresSurfaceElevated)
                            .border(1.dp, if (isSelected) AresCyan else AresBorder, RoundedCornerShape(6.dp))
                            .clickable { selectedMotor = motor }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = motor.name.split("_").first(),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AresCyan else AresTextSecondary
                        )
                    }
                }
            }

            // Analysis Stat Readouts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Stall Margin", fontSize = 9.sp, color = AresTextTertiary)
                    Text("${"%.1f".format(analysis.stallMargin)}x", fontSize = 13.sp, color = if (analysis.stallMargin >= 2.0) AresGreen else AresAmber, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Load Current", fontSize = 9.sp, color = AresTextTertiary)
                    Text("${"%.1f".format(analysis.estimatedCurrentDrawAmps)}A", fontSize = 13.sp, color = if (analysis.estimatedCurrentDrawAmps <= 30.0) AresGreen else AresError, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Max Speed", fontSize = 9.sp, color = AresTextTertiary)
                    Text("${analysis.outputSpeedRpm.toInt()} RPM", fontSize = 13.sp, color = AresCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Travel Time (90°)", fontSize = 9.sp, color = AresTextTertiary)
                    Text("${"%.2f".format(analysis.travelTimeSec)}s", fontSize = 13.sp, color = AresGold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            // Warning Notice
            analysis.warningMessage?.let { warn ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AresError.copy(alpha = 0.15f))
                        .border(1.dp, AresError, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(warn, color = AresError, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Interactive Sliders
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Total Gear Reduction: ${gearRatio.toInt()}:1", color = AresTextPrimary, fontSize = 11.sp)
                Slider(value = gearRatio, onValueChange = { gearRatio = it }, valueRange = 5f..120f)

                Text("Mechanism Mass: ${"%.1f".format(massKg)} kg", color = AresTextPrimary, fontSize = 11.sp)
                Slider(value = massKg, onValueChange = { massKg = it }, valueRange = 0.5f..12f)

                Text("Arm Length: ${"%.2f".format(armLengthM)} m", color = AresTextPrimary, fontSize = 11.sp)
                Slider(value = armLengthM, onValueChange = { armLengthM = it }, valueRange = 0.2f..1.2f)
            }

            // Educational Concept Toggle
            TextButton(onClick = { showTheory = !showTheory }) {
                Icon(if (showTheory) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showTheory) "Hide Sizing Rules" else "Learn Motor Selection & Stall Margins", fontSize = 11.sp, color = AresCyan)
            }

            if (showTheory) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AresSurface)
                        .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("1. The 2x Stall Margin Golden Rule", fontWeight = FontWeight.Bold, color = AresCyan, fontSize = 11.sp)
                    Text("Always design mechanisms so output stall torque is at least 2.0x to 3.0x larger than the peak static load torque. This keeps steady-state motor current well below 20A, preventing thermal shutdown and battery sag.", color = AresTextTertiary, fontSize = 10.sp)

                    Text("2. Linear Current vs. Torque Relationship", fontWeight = FontWeight.Bold, color = AresCyan, fontSize = 11.sp)
                    Text("DC motor current scales linearly with torque: I = I_free + (τ / τ_stall) * (I_stall - I_free). Higher gear ratios reduce the torque load on the motor rotor, drastically lowering current draw at the cost of free speed.", color = AresTextTertiary, fontSize = 10.sp)

                    Text("3. Gravity Compensation in Controls", fontWeight = FontWeight.Bold, color = AresCyan, fontSize = 11.sp)
                    Text("Feedforward voltage kG cancels load torque before PID acts: for an elevator V_hold = kG (constant), and for an arm V_hold = kG * cos(θ) (maximum at horizontal, zero at vertical).", color = AresTextTertiary, fontSize = 10.sp)
                }
            }
        }
    }
}
