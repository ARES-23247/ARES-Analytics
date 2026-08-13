package com.ares.analytics.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.areslib.subsystem.SubsystemControlLoopDocument
import com.areslib.subsystem.SubsystemFeedforwardKind
import com.areslib.subsystem.SubsystemHomingComparison
import com.areslib.subsystem.SubsystemHomingDocument
import java.awt.Desktop
import java.net.URI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign

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
