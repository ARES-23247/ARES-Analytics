package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.League
import com.areslib.auto.AutoPose
import com.areslib.auto.AutoRoutine
import com.areslib.auto.AutoStep
import com.areslib.auto.AutoValidationIssue
import com.areslib.auto.AutoValidationSeverity
import com.areslib.math.coordinate.CoordinateTransformers
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Physical bumper-to-bumper footprint used by the auto editor, in meters. */
data class RobotDimensions(
    val lengthMeters: Double,
    val widthMeters: Double
) {
    fun normalized(): RobotDimensions = RobotDimensions(
        lengthMeters = lengthMeters.takeIf(Double::isFinite)?.coerceIn(MIN_SIZE_METERS, MAX_SIZE_METERS)
            ?: DEFAULT_FTC_SIZE_METERS,
        widthMeters = widthMeters.takeIf(Double::isFinite)?.coerceIn(MIN_SIZE_METERS, MAX_SIZE_METERS)
            ?: DEFAULT_FTC_SIZE_METERS
    )

    companion object {
        const val MIN_SIZE_METERS = 0.10
        const val MAX_SIZE_METERS = 2.00
        const val DEFAULT_FTC_SIZE_METERS = 0.4572
        const val DEFAULT_FRC_SIZE_METERS = 0.80

        fun defaultFor(league: League): RobotDimensions {
            val size = if (league == League.FTC) DEFAULT_FTC_SIZE_METERS else DEFAULT_FRC_SIZE_METERS
            return RobotDimensions(size, size)
        }
    }
}

data class AutoCenterBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
)

/** Exact legal center bounds for a rectangular robot at [headingRadians]. */
fun legalCenterBounds(
    league: League,
    dimensions: RobotDimensions,
    headingRadians: Double
): AutoCenterBounds {
    val robot = dimensions.normalized()
    val heading = headingRadians.takeIf(Double::isFinite) ?: 0.0
    val halfLength = robot.lengthMeters / 2.0
    val halfWidth = robot.widthMeters / 2.0
    val projectedX = abs(cos(heading)) * halfLength + abs(sin(heading)) * halfWidth
    val projectedY = abs(sin(heading)) * halfLength + abs(cos(heading)) * halfWidth

    val (xBounds, yBounds) = if (league == League.FTC) {
        val halfField = CoordinateTransformers.FTC_FIELD_SIZE / 2.0
        boundedOrCentered(-halfField + projectedX, halfField - projectedX, 0.0) to
            boundedOrCentered(-halfField + projectedY, halfField - projectedY, 0.0)
    } else {
        boundedOrCentered(projectedX, CoordinateTransformers.FRC_FIELD_LENGTH - projectedX,
            CoordinateTransformers.FRC_FIELD_LENGTH / 2.0) to
            boundedOrCentered(projectedY, CoordinateTransformers.FRC_FIELD_WIDTH - projectedY,
                CoordinateTransformers.FRC_FIELD_WIDTH / 2.0)
    }
    return AutoCenterBounds(xBounds.first, xBounds.second, yBounds.first, yBounds.second)
}

fun clampAutoPose(pose: AutoPose, league: League, dimensions: RobotDimensions): AutoPose {
    if (!pose.xMeters.isFinite() || !pose.yMeters.isFinite() || !pose.headingRadians.isFinite()) return pose
    val bounds = legalCenterBounds(league, dimensions, pose.headingRadians)
    return pose.copy(
        xMeters = pose.xMeters.coerceIn(bounds.minX, bounds.maxX),
        yMeters = pose.yMeters.coerceIn(bounds.minY, bounds.maxY)
    )
}

fun isAutoPoseInsideField(pose: AutoPose, league: League, dimensions: RobotDimensions): Boolean =
    pose == clampAutoPose(pose, league, dimensions)

fun validateAutoFieldBounds(
    routine: AutoRoutine,
    league: League,
    dimensions: RobotDimensions
): List<AutoValidationIssue> = buildList {
    if (!isAutoPoseInsideField(routine.startingPose, league, dimensions)) {
        add(fieldBoundaryIssue("startingPose", "Starting robot footprint crosses the field boundary"))
    }
    validateStepBounds(routine.steps, "steps", league, dimensions, this)
}

private fun validateStepBounds(
    steps: List<AutoStep>,
    path: String,
    league: League,
    dimensions: RobotDimensions,
    issues: MutableList<AutoValidationIssue>
) {
    steps.forEachIndexed { index, step ->
        val stepPath = "$path[$index]"
        step.drive?.target?.takeUnless { isAutoPoseInsideField(it, league, dimensions) }?.let {
            issues += fieldBoundaryIssue(stepPath, "Drive goal robot footprint crosses the field boundary")
        }
        validateStepBounds(step.children, "$stepPath.children", league, dimensions, issues)
    }
}

private fun fieldBoundaryIssue(path: String, message: String) = AutoValidationIssue(
    severity = AutoValidationSeverity.ERROR,
    path = path,
    code = "robot_outside_field",
    message = message
)

private fun boundedOrCentered(min: Double, max: Double, center: Double): Pair<Double, Double> =
    if (min <= max) min to max else center to center

fun AutoStep.withClampedDriveTarget(league: League, dimensions: RobotDimensions): AutoStep =
    drive?.let { driveStep ->
        copy(drive = driveStep.copy(target = clampAutoPose(driveStep.target, league, dimensions)))
    } ?: this
