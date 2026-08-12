package com.ares.analytics.shared

import kotlinx.serialization.Serializable

// Keep these aliases while callers migrate to shared.models; removing them is a source-breaking change.
import com.ares.analytics.shared.models.*

const val DEFAULT_GEMINI_MODEL = "gemini-3.6-flash"

typealias League = com.ares.analytics.shared.models.League
typealias WorkspaceConfig = com.ares.analytics.shared.models.WorkspaceConfig
typealias AppWorkspaces = com.ares.analytics.shared.models.AppWorkspaces
typealias RobotProfile = com.ares.analytics.shared.models.RobotProfile

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
typealias ForensicsRequest = com.ares.analytics.shared.models.ForensicsRequest
typealias HardwareFaultLocus = com.ares.analytics.shared.models.HardwareFaultLocus
typealias ForensicsResponse = com.ares.analytics.shared.models.ForensicsResponse
typealias CalculatedSummary = com.ares.analytics.shared.models.CalculatedSummary
typealias TransientClassification = com.ares.analytics.shared.models.TransientClassification
typealias DriverProfile = com.ares.analytics.shared.models.DriverProfile

/** A point in field coordinates, in meters. */
@Serializable
data class PathPoint(val x: Double, val y: Double)

/** FTC field rendering convention selected by the user-authored field image. */
@Serializable
enum class FTCCoordinateSystem { DIAMOND, SQUARE }

/**
 * Describes how `field_image.png` maps to field coordinates.
 * Crop values are normalized fractions; dimensions are meters and rotation is degrees.
 */
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

/**
 * Editable field collision geometry. Positions and dimensions are meters;
 * [Rectangle.rotation] is degrees counter-clockwise in field coordinates.
 */
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

/** User-authored game-piece placement in field coordinates (meters). */
@Serializable
data class GamePiece(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val type: String = "Custom",
    val locked: Boolean = false
)

/** AprilTag placement in meters with a CCW-positive yaw in degrees. */
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

/** Named field pose in meters with a CCW-positive heading in degrees. */
@Serializable
data class FieldWaypoint(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val headingDegrees: Double,
    val locked: Boolean = false
)
