package com.ares.analytics.shared

import kotlinx.serialization.Serializable
// Re-export all domain model definitions from subpackage for 100% backward compatibility
import com.ares.analytics.shared.models.*

typealias League = com.ares.analytics.shared.models.League
typealias WorkspaceConfig = com.ares.analytics.shared.models.WorkspaceConfig
typealias AppWorkspaces = com.ares.analytics.shared.models.AppWorkspaces
typealias RobotProfile = com.ares.analytics.shared.models.RobotProfile
typealias TeamRobotsResponse = com.ares.analytics.shared.models.TeamRobotsResponse
typealias AddRobotRequest = com.ares.analytics.shared.models.AddRobotRequest
typealias DeleteRobotRequest = com.ares.analytics.shared.models.DeleteRobotRequest

typealias SessionMode = com.ares.analytics.shared.models.SessionMode
typealias Session = com.ares.analytics.shared.models.Session
typealias SessionSummary = com.ares.analytics.shared.models.SessionSummary
typealias SessionAnnotation = com.ares.analytics.shared.models.SessionAnnotation
typealias TelemetryFrame = com.ares.analytics.shared.models.TelemetryFrame
typealias RobotActionRecord = com.ares.analytics.shared.models.RobotActionRecord
typealias AlertRecord = com.ares.analytics.shared.models.AlertRecord
typealias ThresholdRule = com.ares.analytics.shared.models.ThresholdRule
typealias ConsoleMessage = com.ares.analytics.shared.models.ConsoleMessage
typealias ControllerBinding = com.ares.analytics.shared.models.ControllerBinding
typealias TrajectoryState = com.ares.analytics.shared.models.TrajectoryState
typealias Trajectory = com.ares.analytics.shared.models.Trajectory

typealias TopologyNodeType = com.ares.analytics.shared.models.TopologyNodeType
typealias TopologyNode = com.ares.analytics.shared.models.TopologyNode
typealias HardwareTopology = com.ares.analytics.shared.models.HardwareTopology
typealias UploadUrlRequest = com.ares.analytics.shared.models.UploadUrlRequest
typealias UploadUrlResponse = com.ares.analytics.shared.models.UploadUrlResponse
typealias DownloadUrlResponse = com.ares.analytics.shared.models.DownloadUrlResponse
typealias SyncRequest = com.ares.analytics.shared.models.SyncRequest
typealias SyncResponse = com.ares.analytics.shared.models.SyncResponse
typealias DeleteSessionRequest = com.ares.analytics.shared.models.DeleteSessionRequest
typealias RawUploadUrlsRequest = com.ares.analytics.shared.models.RawUploadUrlsRequest
typealias RawUploadUrlsResponse = com.ares.analytics.shared.models.RawUploadUrlsResponse
typealias ForensicsRequest = com.ares.analytics.shared.models.ForensicsRequest
typealias HardwareFaultLocus = com.ares.analytics.shared.models.HardwareFaultLocus
typealias ForensicsResponse = com.ares.analytics.shared.models.ForensicsResponse
typealias CalculatedSummary = com.ares.analytics.shared.models.CalculatedSummary
typealias TransientClassification = com.ares.analytics.shared.models.TransientClassification
typealias DriverProfile = com.ares.analytics.shared.models.DriverProfile

// Path Planner Obstacles & Field Models
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
    abstract val colorHex: String

    @Serializable
    data class Polygon(
        override val id: String,
        override val name: String,
        val vertices: List<PathPoint>,
        override val locked: Boolean = false,
        override val colorHex: String = "#E53935"
    ) : Obstacle()

    @Serializable
    data class Circle(
        override val id: String,
        override val name: String,
        val centerX: Double,
        val centerY: Double,
        val radius: Double,
        override val locked: Boolean = false,
        override val colorHex: String = "#E53935"
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
        override val locked: Boolean = false,
        override val colorHex: String = "#E53935"
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
data class FieldWaypoint(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val headingDegrees: Double,
    val locked: Boolean = false
)