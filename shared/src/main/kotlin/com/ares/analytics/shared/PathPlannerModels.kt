package com.ares.analytics.shared

import kotlinx.serialization.Serializable

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class PathPlannerWaypoint(
    val anchor: PathPoint,
    val prevControl: PathPoint?,
    val nextControl: PathPoint?,
    val isLocked: Boolean = false,
    val linkedName: String? = null
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class PathPlannerCommand(
    val type: String = "named",
    val name: String
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class PathPlannerEventMarker(
    val name: String,
    val waypointRelativePos: Double,
    val endWaypointRelativePos: Double? = null,
    val command: PathPlannerCommand?
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class PathConstraints(
    val maxVelocity: Double = 3.0,
    val maxAcceleration: Double = 3.0,
    val maxAngularVelocity: Double = 540.0,
    val maxAngularAcceleration: Double = 720.0,
    val nominalVoltage: Double = 12.0,
    val unlimited: Boolean = false
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class GoalEndState(
    val velocity: Double = 0.0,
    val rotation: Double = 0.0
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class IdealStartingState(
    val velocity: Double = 0.0,
    val rotation: Double = 0.0
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class RotationTarget(
    val waypointRelativePos: Double,
    val rotationDegrees: Double
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class ConstraintsZone(
    val name: String = "Constraints Zone",
    val minWaypointRelativePos: Double,
    val maxWaypointRelativePos: Double,
    val constraints: PathConstraints
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class PointTowardsZone(
    val name: String = "Point Towards Zone",
    val fieldPosition: PathPoint,
    val rotationOffset: Double = 0.0,
    val minWaypointRelativePos: Double,
    val maxWaypointRelativePos: Double
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class PathPlannerFile(
    val version: String = "2025.0",
    val waypoints: List<PathPlannerWaypoint>,
    val rotationTargets: List<RotationTarget> = emptyList(),
    val constraintZones: List<ConstraintsZone> = emptyList(),
    val pointTowardsZones: List<PointTowardsZone> = emptyList(),
    val eventMarkers: List<PathPlannerEventMarker> = emptyList(),
    val globalConstraints: PathConstraints = PathConstraints(),
    val goalEndState: GoalEndState? = null,
    val idealStartingState: IdealStartingState? = null,
    val reversed: Boolean = false,
    val useDefaultConstraints: Boolean = true
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class AutoCommandNode(
    val type: String,
    val data: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap())
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class AutoFile(
    val version: String = "1.0",
    val startingPose: AutoStartingPose? = null,
    val command: AutoCommandNode
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class AutoStartingPose(
    val position: AutoPosition,
    val rotation: Double
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class AutoPosition(
    val x: Double,
    val y: Double
)
