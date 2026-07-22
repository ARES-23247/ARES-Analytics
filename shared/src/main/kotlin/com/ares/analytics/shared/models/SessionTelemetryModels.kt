package com.ares.analytics.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class SessionMode {
    LIVE_STREAMING,
    LIVE_REWIND,
    HISTORICAL_REPLAY
}

@Serializable
data class Session(
    val sessionId: String,
    val teamId: String,
    val seasonId: String,
    val robotId: String,
    val createdAt: Long,
    val durationMs: Long = 0L,
    val tags: List<String> = emptyList(),
    val matchNumber: Int? = null,
    val allianceColor: String? = null
)

@Serializable
data class SessionSummary(
    val sessionId: String,
    val teamId: String,
    val seasonId: String,
    val robotId: String,
    val createdAt: Long,
    val durationMs: Long = 0L,
    val minBatteryVoltage: Double = 0.0,
    val maxEkfDrift: Double = 0.0,
    val avgLoopTimeMs: Double = 0.0,
    val p95LoopTimeMs: Double = 0.0,
    val motorCurrentAverages: Map<String, Double> = emptyMap(),
    val visionAcceptanceRate: Double = 0.0,
    val avgCrossTrackError: Double = 0.0,
    val avgBatteryResistance: Double = 0.0,
    val maxMotorTemps: Map<String, Double> = emptyMap(),
    val avgVisionLatencyMs: Double = 0.0,
    val tags: List<String> = emptyList(),
    val matchNumber: Int? = null,
    val allianceColor: String? = null,
    val rawGcsPath: String? = null,
    val fileSizeBytes: Long = 0L
)

@Serializable
data class SessionAnnotation(
    val annotationId: String,
    val sessionId: String,
    val text: String,
    val createdAt: Long,
    val authorId: String? = null
)

@Serializable
data class TelemetryFrame(
    val timestampMs: Long,
    val sessionId: String,
    val key: String,
    val value: Double,
    val stringValue: String? = null
)

@Serializable
data class RobotActionRecord(
    val timestampMs: Long,
    val sessionId: String,
    val runId: String,
    val robotId: String,
    val matchNumber: Int = 0,
    val alliance: String = "UNKNOWN",
    val actionType: String,
    val payloadJson: String
)

@Serializable
data class AlertRecord(
    val alertId: String,
    val sessionId: String,
    val ruleKey: String,
    val triggerTimestampMs: Long,
    val resolveTimestampMs: Long? = null,
    val durationMs: Long = 0L,
    val peakValue: Double = 0.0,
    val triaged: Boolean = false
)

@Serializable
data class ThresholdRule(
    val key: String,
    val displayName: String,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val audibleAlert: Boolean = true
)

@Serializable
data class ConsoleMessage(
    val timestampMs: Long,
    val text: String,
    val severity: String
)

data class ControllerBinding(
    val gamepadId: String,
    val button: String,
    val action: String,
    val sourceFile: String,
    val lineNumber: Int
)

@Serializable
data class TrajectoryState(
    val timeSeconds: Double,
    val x: Double,
    val y: Double,
    val headingRad: Double,
    val velocity: Double
)

@Serializable
data class Trajectory(
    val durationSeconds: Double,
    val states: List<TrajectoryState>
)
