package com.ares.analytics.shared

import kotlinx.serialization.Serializable

// ────────────────────────────────────────────────────────────────────────────
// Workspace & Configuration
// ────────────────────────────────────────────────────────────────────────────

@Serializable
data class WorkspaceConfig(
    val id: String = "",
    val teamId: String,
    val seasonId: String,
    val robotId: String,
    val robotName: String = "",
    val projectPath: String,
    val league: League,
    val nt4Host: String? = null,
    val eventCode: String? = null,
    val toaApiKey: String? = null,
    val tbaApiKey: String? = null,
    val googleClientId: String? = null,
    val firebaseApiKey: String? = null,
    val googleClientSecret: String? = null,
    val simulatorCommand: String? = null
)

@Serializable
enum class League {
    FTC, FRC
}

@Serializable
data class AppWorkspaces(
    val activeWorkspaceId: String?,
    val workspaces: List<WorkspaceConfig>
)

@Serializable
data class RobotProfile(
    val robotId: String,
    val league: League,
    val seasonId: String,
    val name: String
)

@Serializable
data class TeamRobotsResponse(
    val robots: List<RobotProfile>
)

@Serializable
data class AddRobotRequest(
    val teamId: String,
    val robot: RobotProfile
)

@Serializable
data class DeleteRobotRequest(
    val teamId: String,
    val robotId: String
)

// ────────────────────────────────────────────────────────────────────────────
// Session & Telemetry
// ────────────────────────────────────────────────────────────────────────────

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

// ────────────────────────────────────────────────────────────────────────────
// Alert System
// ────────────────────────────────────────────────────────────────────────────

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

// ────────────────────────────────────────────────────────────────────────────
// Hardware Topology
// ────────────────────────────────────────────────────────────────────────────

@Serializable
enum class TopologyNodeType {
    CONTROL_HUB, EXPANSION_HUB, SRS_HUB,
    ROBORIO, CANIVORE,
    MOTOR, CAN_MOTOR_CONTROLLER, SERVO,
    CAMERA, ODOMETRY_COMPUTER, IMU,
    COLOR_SENSOR, DISTANCE_SENSOR, BEAM_BREAK, ANALOG_SENSOR,
    CAN_CODER, PIGEON_IMU, POWER_DISTRIBUTION
}

@Serializable
data class TopologyNode(
    val id: String,
    val type: TopologyNodeType,
    val displayName: String,
    val parentId: String? = null,
    val port: Int? = null,
    val canId: Int? = null,
    val canBus: String? = null,
    val busPosition: Int? = null,
    val connectionType: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class HardwareTopology(
    val robotId: String,
    val nodes: List<TopologyNode> = emptyList()
)

// ────────────────────────────────────────────────────────────────────────────
// Cloud Sync
// ────────────────────────────────────────────────────────────────────────────

@Serializable
data class UploadUrlRequest(
    val teamId: String,
    val seasonId: String,
    val robotId: String,
    val sessionId: String,
    val createdAt: Long,
    val summary: SessionSummary
)

@Serializable
data class UploadUrlResponse(
    val uploadUrl: String,
    val expiresAt: Long
)

@Serializable
data class DownloadUrlResponse(
    val downloadUrl: String,
    val expiresAt: Long
)

@Serializable
data class SyncRequest(
    val teamId: String,
    val seasonId: String,
    val knownSessionIds: List<String>
)

@Serializable
data class SyncResponse(
    val missingSummaries: List<SessionSummary>
)

@Serializable
data class DeleteSessionRequest(
    val sessionId: String,
    val teamId: String
)

@Serializable
data class RawUploadUrlsRequest(
    val teamId: String,
    val runTimestamp: String,
    val fileNames: List<String>
)

@Serializable
data class RawUploadUrlsResponse(
    val uploadUrls: Map<String, String>,
    val expiresAt: Long
)

// ────────────────────────────────────────────────────────────────────────────
// AI Diagnostics
// ────────────────────────────────────────────────────────────────────────────

@Serializable
data class ForensicsRequest(
    val teamId: String,
    val sessionId: String,
    val alerts: List<AlertRecord>,
    val summary: SessionSummary,
    val topology: HardwareTopology? = null,
    val sysIdDrift: Map<String, Double> = emptyMap()
)

@Serializable
data class HardwareFaultLocus(
    val failedNodeId: String,
    val interruptedLinkId: String? = null
)

@Serializable
data class ForensicsResponse(
    val probableRootCause: String,
    val confidenceScore: Double,
    val cascadingNodesAffected: List<String> = emptyList(),
    val hardwareFaultLocus: HardwareFaultLocus? = null,
    val recommendedActions: List<String> = emptyList()
)

// ────────────────────────────────────────────────────────────────────────────
// SysId Results
// ────────────────────────────────────────────────────────────────────────────

@Serializable
data class CalculatedSummary(
    val kS: Double = 0.0,
    val kV: Double = 0.0,
    val kA: Double = 0.0,
    val rSquared: Double = 0.0,
    val transientClassification: TransientClassification = TransientClassification.UNKNOWN
)

@Serializable
enum class TransientClassification {
    UNDERDAMPED, OVERDAMPED, CRITICALLY_DAMPED, UNKNOWN
}

@Serializable
data class MotorSummary(
    val name: String,
    val avgCurrentAmps: Double = 0.0,
    val maxCurrentAmps: Double = 0.0,
    val stallCount: Int = 0
)

// ────────────────────────────────────────────────────────────────────────────
// Driver Profiles
// ────────────────────────────────────────────────────────────────────────────

@Serializable
data class DriverProfile(
    val name: String,
    val deadbandExponent: Double = 1.0,
    val slewRateLimit: Double = Double.MAX_VALUE,
    val jitterPeakFrequencyHz: Double = 0.0,
    val jitterAmplitude: Double = 0.0
)

// ────────────────────────────────────────────────────────────────────────────
// Path Planner Obstacles
// ────────────────────────────────────────────────────────────────────────────

@Serializable
data class PathPoint(val x: Double, val y: Double)

@Serializable
enum class FTCCoordinateSystem { DIAMOND, SQUARE }

@Serializable
data class FieldImageConfig(
    val imagePath: String = "",
    val rotationDegrees: Double = 0.0,
    val cropLeft: Double = 0.0,
    val cropRight: Double = 1.0,
    val cropTop: Double = 0.0,
    val cropBottom: Double = 1.0,
    val widthMeters: Double = 3.65,
    val heightMeters: Double = 3.65,
    val ftcCoordinateSystem: FTCCoordinateSystem = FTCCoordinateSystem.DIAMOND
)

@Serializable
sealed class Obstacle {
    abstract val id: String
    abstract val name: String
    abstract val locked: Boolean

    @Serializable
    data class Polygon(
        override val id: String,
        override val name: String,
        val vertices: List<PathPoint>,
        override val locked: Boolean = false
    ) : Obstacle()

    @Serializable
    data class Circle(
        override val id: String,
        override val name: String,
        val centerX: Double,
        val centerY: Double,
        val radius: Double,
        override val locked: Boolean = false
    ) : Obstacle()

    @Serializable
    data class Rectangle(
        override val id: String,
        override val name: String,
        val centerX: Double,
        val centerY: Double,
        val width: Double,
        val height: Double,
        val rotation: Double = 0.0,
        override val locked: Boolean = false
    ) : Obstacle()
}

@Serializable
data class GamePiece(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val type: String = "Custom",
    val locked: Boolean = false
)

@Serializable
data class AprilTagPlacement(
    val id: String,
    val tagId: Int,
    val x: Double,
    val y: Double,
    val z: Double = 0.5,
    val yawDegrees: Double = 0.0,
    val locked: Boolean = false
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