package com.ares.analytics.shared.models

import kotlinx.serialization.Serializable

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
data class DriverProfile(
    val name: String,
    val deadbandExponent: Double = 1.0,
    val slewRateLimit: Double = Double.MAX_VALUE,
    val jitterPeakFrequencyHz: Double = 0.0,
    val jitterAmplitude: Double = 0.0
)
