package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.shared.TelemetryMetricCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiagnosticSeverity { INFORMATION, REVIEW, URGENT }

data class DiagnosticFinding(
    val id: String,
    val title: String,
    val severity: DiagnosticSeverity,
    val timestampSeconds: Double,
    val observation: String,
    val thresholdContext: String,
    val possibleCauses: List<String>,
    val verificationSteps: List<String>,
    val topic: String
)

data class PitDiagnosticSummary(
    val findings: List<DiagnosticFinding>,
    val missingSignals: List<String>,
    val evidenceNotice: String = "These are telemetry screening observations, not root-cause diagnoses or proof that the robot is safe."
) {
    val urgentCount: Int get() = findings.count { it.severity == DiagnosticSeverity.URGENT }
    val reviewCount: Int get() = findings.count { it.severity == DiagnosticSeverity.REVIEW }
}

/**
 * Produces an evidence-limited pit checklist from imported telemetry.
 *
 * It reports only thresholds directly supported by the selected signal. Possible causes are
 * hypotheses to verify; they are never presented as diagnoses.
 */
class DiagnosticCoachService(private val databaseService: DatabaseService) {
    suspend fun analyze(sessionId: String): PitDiagnosticSummary = withContext(Dispatchers.Default) {
        require(sessionId.isNotBlank()) { "Select a recorded session before running the checklist" }
        val batteryFrames = TelemetryMetricCatalog.BATTERY_VOLTAGE.keys
            .asSequence()
            .map { key -> databaseService.getTelemetryForKey(sessionId, key) }
            .firstOrNull(List<TelemetryFrame>::isNotEmpty)
            .orEmpty()
            .filter { it.value.isFinite() }
            .sortedBy { it.timestampUs }
        val currentFrames = databaseService.getTelemetryForKeyPatterns(
            sessionId,
            listOf("Hardware/Motors/%/CurrentAmps", "Hardware/Motors/%/Current")
        ).filter { it.value.isFinite() }.sortedBy { it.timestampUs }

        val findings = buildList {
            batteryFrames.minByOrNull(TelemetryFrame::value)?.takeIf { it.value < BATTERY_REVIEW_VOLTS }?.let { frame ->
                add(
                    DiagnosticFinding(
                        id = "battery-low",
                        title = "Battery voltage crossed the review threshold",
                        severity = if (frame.value < BATTERY_URGENT_VOLTS) DiagnosticSeverity.URGENT else DiagnosticSeverity.REVIEW,
                        timestampSeconds = frame.timestampUs / 1_000_000.0,
                        observation = "Minimum observed battery voltage was ${"%.2f".format(frame.value)} V.",
                        thresholdContext = "ARES screens imported voltage below $BATTERY_REVIEW_VOLTS V for review; this is not a battery diagnosis.",
                        possibleCauses = listOf("A discharged or high-resistance battery", "High simultaneous mechanism load", "Loose or resistive power wiring", "A telemetry or calibration problem"),
                        verificationSteps = listOf("Check the timestamp against total current and driver actions", "Measure the battery with approved pit equipment", "Inspect and torque power connections using the team's electrical checklist"),
                        topic = frame.key
                    )
                )
            }
            sustainedCurrentFinding(currentFrames)?.let(::add)
        }
        val missing = buildList {
            if (batteryFrames.isEmpty()) add("Battery voltage")
            if (currentFrames.isEmpty()) add("Per-motor current")
        }
        PitDiagnosticSummary(findings, missing)
    }

    private fun sustainedCurrentFinding(frames: List<TelemetryFrame>): DiagnosticFinding? {
        for (group in frames.groupBy(TelemetryFrame::key).values) {
            var start: TelemetryFrame? = null
            var previous: TelemetryFrame? = null
            for (frame in group) {
                val gapUs = previous?.let { frame.timestampUs - it.timestampUs } ?: 0L
                if (frame.value >= CURRENT_REVIEW_AMPS && (previous == null || gapUs in 0..MAX_SAMPLE_GAP_US)) {
                    if (start == null) start = frame
                    if (frame.timestampUs - start.timestampUs >= CURRENT_REVIEW_DURATION_US) {
                        return DiagnosticFinding(
                            id = "sustained-current-${frame.key}",
                            title = "Sustained motor current crossed the review threshold",
                            severity = DiagnosticSeverity.REVIEW,
                            timestampSeconds = start.timestampUs / 1_000_000.0,
                            observation = "${frame.key} stayed at or above $CURRENT_REVIEW_AMPS A for at least 0.5 s; peak ${"%.1f".format(group.maxOf { it.value })} A.",
                            thresholdContext = "This generic screening threshold is not the mechanism's configured current limit and does not establish a stall.",
                            possibleCauses = listOf("Expected heavy mechanism load", "Binding or obstruction", "Aggressive control demand", "Incorrect current telemetry"),
                            verificationSteps = listOf("Compare the interval with target, velocity, position, and operator intent", "Check the mechanism-specific current limit and duty cycle", "Inspect the mechanism while disabled before repeating a restrained test"),
                            topic = frame.key
                        )
                    }
                } else {
                    start = null
                }
                previous = frame
            }
        }
        return null
    }

    companion object {
        const val BATTERY_REVIEW_VOLTS = 10.5
        const val BATTERY_URGENT_VOLTS = 9.5
        const val CURRENT_REVIEW_AMPS = 40.0
        const val CURRENT_REVIEW_DURATION_US = 500_000L
        const val MAX_SAMPLE_GAP_US = 200_000L
    }
}
