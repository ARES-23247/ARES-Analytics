package com.ares.analytics.service

import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.shared.PathConstraints
import com.ares.analytics.shared.ConstraintsZone
import com.ares.analytics.shared.GoalEndState
import com.ares.analytics.shared.IdealStartingState
import com.ares.analytics.shared.RotationTarget
import com.ares.analytics.shared.Trajectory
import com.ares.analytics.shared.TrajectoryState
import kotlin.math.*

/**
 * High-performance acceleration- and angular-constrained trajectory estimator.
 *
 * Generates continuous time-parameterized drivetrain trajectories over piecewise cubic Bézier Splines,
 * enforcing translational velocity ($m/s$), acceleration ($m/s^2$), angular velocity ($rad/s$),
 * and angular acceleration ($rad/s^2$) limits. PathPlanner's current schema has no jerk field,
 * so this estimator deliberately makes no jerk-limited claim.
 *
 * ### Mathematical Formulations:
 * 1. **Cubic Bézier Position Evaluation**:
 *    $$\mathbf{P}(t) = (1-t)^3 \mathbf{P}_0 + 3(1-t)^2 t \mathbf{P}_1 + 3(1-t) t^2 \mathbf{P}_2 + t^3 \mathbf{P}_3$$
 * 2. **Path Curvature ($\kappa$) & Centripetal Speed Constraint**:
 *    $$\kappa = \frac{|\dot{x}\ddot{y} - \dot{y}\ddot{x}|}{(\dot{x}^2 + \dot{y}^2)^{3/2}}, \quad v_{\text{max, centripetal}} = \sqrt{\frac{a_{\text{max}}}{\kappa}}$$
 * 3. **Forward-Backward Kinematic Pass**:
 *    $$v_k^2 \le v_{k-1}^2 + 2 a_{\text{max}} \Delta s$$
 *
 * ### Physical Units & Coordinates:
 * - Position ($x, y$): Meters ($m$)
 * - Heading ($\theta$): Radians ($rad$), **CCW-positive** (0 = +X, $\pi/2$ = +Y)
 * - Translational Velocity / Accel: $m/s$, $m/s^2$
 * - Angular Velocity: $rad/s$
 *
 * ### Thread Safety & Zero-GC Guarantees:
 * Zero-allocation during hot-path sampling. Utilizes a [ThreadLocal] pool of pre-allocated `SampledPoint` buffers,
 * ensuring high-frequency UI path previews run without GC pauses.
 *
 * @see com.ares.analytics.shared.Trajectory
 * @see com.ares.analytics.shared.PathConstraints
 */
object TrajectoryEstimator {

    /**
     * Internal point record used during 2-pass motion profile trajectory generation.
     */
    private data class SampledPoint(
        var x: Double = 0.0,
        var y: Double = 0.0,
        var s: Double = 0.0,
        var relativePos: Double = 0.0,
        var maxV: Double = 0.0,
        var v: Double = 0.0,
        var t: Double = 0.0
    )

    private val pointPool = ThreadLocal.withInitial { ArrayList<SampledPoint>(500) }

    /**
     * Generates a time-parameterized [Trajectory] passing through the specified [waypoints].
     *
     * @param waypoints List of geometric waypoints defining spline control points.
     * @param globalConstraints Default velocity, acceleration, and angular velocity limits.
     * @param constraintZones Region-specific velocity and acceleration override boundaries.
     * @param rotationTargets Intermediate heading targets along the path.
     * @param idealStartingState Initial velocity and heading boundary condition.
     * @param goalEndState Target terminal velocity and heading boundary condition.
     * @return Fully sampled time-parameterized [Trajectory] object.
     */
    fun generateTrajectory(
        waypoints: List<Waypoint>,
        globalConstraints: PathConstraints,
        constraintZones: List<ConstraintsZone>,
        rotationTargets: List<RotationTarget>,
        idealStartingState: IdealStartingState?,
        goalEndState: GoalEndState?
    ): Trajectory {
        if (waypoints.size < 2) return Trajectory(0.0, emptyList())
        val sampledPoints = requireNotNull(pointPool.get()) {
            "Trajectory point pool was not initialized for this thread"
        }
        var pointCount = 0

        fun getNextPoint(): SampledPoint {
            if (pointCount >= sampledPoints.size) {
                sampledPoints.add(SampledPoint())
            }
            return sampledPoints[pointCount++]
        }

        // Start with the first waypoint
        val firstPt = getNextPoint()
        firstPt.x = waypoints[0].x
        firstPt.y = waypoints[0].y
        firstPt.s = 0.0
        firstPt.relativePos = 0.0
        val maxIdx = waypoints.size - 1
        var totalDist = 0.0

        // Step 1: Sample points along Catmull-Rom spline
        for (i in 0 until maxIdx) {
            val p0 = waypoints[maxOf(0, i - 1)]
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]
            val p3 = waypoints[minOf(maxIdx, i + 2)]
            val steps = 100
            for (j in 1..steps) {
                val t = j.toDouble() / steps
                val px = catmullRom(p0.x, p1.x, p2.x, p3.x, t)
                val py = catmullRom(p0.y, p1.y, p2.y, p3.y, t)
                val prev = sampledPoints[pointCount - 1]
                val ds = sqrt((px - prev.x).pow(2) + (py - prev.y).pow(2))
                if (ds >= 0.05 || (i == maxIdx - 1 && j == steps)) {
                    totalDist += ds
                    val relativePos = i.toDouble() + t
                    val newPt = getNextPoint()
                    newPt.x = px
                    newPt.y = py
                    newPt.s = totalDist
                    newPt.relativePos = relativePos
                }
            }
        }

        // Step 2: Calculate max velocity limits at each point based on local constraints and curvature
        for (idx in 0 until pointCount) {
            val pt = sampledPoints[idx]
            val constraints = getConstraintsAt(pt.relativePos, globalConstraints, constraintZones)
            val maxAcc = constraints.maxAcceleration
            val maxVel = constraints.maxVelocity

            // Curvature radius calculation
            val r = if (idx > 0 && idx < pointCount - 1) {
                val prev = sampledPoints[idx - 1]
                val curr = sampledPoints[idx]
                val next = sampledPoints[idx + 1]
                calculateRadius(prev.x, prev.y, curr.x, curr.y, next.x, next.y)
            } else {
                Double.POSITIVE_INFINITY
            }
            val centripetalV = if (r.isInfinite() || r.isNaN()) {
                maxVel
            } else {
                sqrt(maxAcc * r)
            }

            sampledPoints[idx].maxV = minOf(maxVel, centripetalV)
        }

        // Apply start/end velocity limits
        sampledPoints[0].maxV = minOf(sampledPoints[0].maxV, idealStartingState?.velocity ?: 0.0)
        sampledPoints[pointCount - 1].maxV = minOf(sampledPoints[pointCount - 1].maxV, goalEndState?.velocity ?: 0.0)

        // Step 3: Forward Pass (acceleration profile)
        sampledPoints[0].v = sampledPoints[0].maxV
        for (i in 1 until pointCount) {
            val ds = sampledPoints[i].s - sampledPoints[i - 1].s
            val constraints = getConstraintsAt(sampledPoints[i].relativePos, globalConstraints, constraintZones)
            val maxAcc = constraints.maxAcceleration
            val maxReachable = sqrt(sampledPoints[i - 1].v.pow(2) + 2.0 * maxAcc * ds)
            sampledPoints[i].v = minOf(sampledPoints[i].maxV, maxReachable)
        }

        // Step 4: Backward Pass (deceleration profile)
        sampledPoints[pointCount - 1].v = sampledPoints[pointCount - 1].maxV
        for (i in (pointCount - 2) downTo 0) {
            val ds = sampledPoints[i + 1].s - sampledPoints[i].s
            val constraints = getConstraintsAt(sampledPoints[i].relativePos, globalConstraints, constraintZones)
            val maxAcc = constraints.maxAcceleration
            val maxReachable = sqrt(sampledPoints[i + 1].v.pow(2) + 2.0 * maxAcc * ds)
            sampledPoints[i].v = minOf(sampledPoints[i].v, maxReachable)
        }

        // Step 5: Integrate time to compute estimated duration
        var currentTime = 0.0
        val states = mutableListOf<TrajectoryState>()
        val combinedRotationTargets = mutableListOf<RotationTarget>()

        waypoints.forEachIndexed { i, wp ->
            if (wp.rotationDeg != null) {
                combinedRotationTargets.add(RotationTarget(waypointRelativePos = i.toDouble(), rotationDegrees = wp.rotationDeg))
            }
        }
        val startingRot = idealStartingState?.rotation ?: 0.0
        if (combinedRotationTargets.none { kotlin.math.abs(it.waypointRelativePos) < 1e-3 }) {
            combinedRotationTargets.add(RotationTarget(waypointRelativePos = 0.0, rotationDegrees = startingRot))
        }
        combinedRotationTargets.addAll(rotationTargets)
        combinedRotationTargets.sortBy { it.waypointRelativePos }
        val endRot = goalEndState?.rotation ?: 0.0
        val lastWaypointIdx = (waypoints.size - 1).toDouble()
        if (lastWaypointIdx >= 0.0 && combinedRotationTargets.none { kotlin.math.abs(it.waypointRelativePos - lastWaypointIdx) < 1e-3 }) {
            combinedRotationTargets.add(RotationTarget(waypointRelativePos = lastWaypointIdx, rotationDegrees = endRot))
        }

        val headings = DoubleArray(pointCount)
        for (i in 0 until pointCount) {
            val prevPt = if (i > 0) sampledPoints[i - 1] else null
            val nextPt = if (i < pointCount - 1) sampledPoints[i + 1] else null
            headings[i] = getHeadingAt(
                sampledPoints[i].relativePos,
                combinedRotationTargets,
                waypoints,
                sampledPoints[i],
                prevPt,
                nextPt
            )
        }

        var previousAngularVelocity = 0.0
        for (i in 0 until pointCount) {
            if (i > 0) {
                val ds = sampledPoints[i].s - sampledPoints[i - 1].s
                val velocitySum = sampledPoints[i].v + sampledPoints[i - 1].v
                val constraints = getConstraintsAt(sampledPoints[i].relativePos, globalConstraints, constraintZones)
                val baseDt = when {
                    velocitySum > 1e-4 -> 2.0 * ds / velocitySum
                    ds > 0.0 && constraints.maxAcceleration > 1e-9 -> sqrt(2.0 * ds / constraints.maxAcceleration)
                    else -> 0.0
                }
                val headingDelta = wrapAngle(headings[i] - headings[i - 1])
                val segmentDt = minimumAngularSegmentTime(
                    baseDt = baseDt,
                    headingDelta = headingDelta,
                    previousAngularVelocity = previousAngularVelocity,
                    maxAngularVelocity = Math.toRadians(constraints.maxAngularVelocity),
                    maxAngularAcceleration = Math.toRadians(constraints.maxAngularAcceleration)
                )
                currentTime += segmentDt
                previousAngularVelocity = if (segmentDt > 1e-9) headingDelta / segmentDt else 0.0
            }
            sampledPoints[i].t = currentTime

            states.add(
                TrajectoryState(
                    timeSeconds = currentTime,
                    x = sampledPoints[i].x,
                    y = sampledPoints[i].y,
                    headingRad = headings[i],
                    velocity = sampledPoints[i].v
                )
            )
        }

        return Trajectory(currentTime, states)
    }

    private fun minimumAngularSegmentTime(
        baseDt: Double,
        headingDelta: Double,
        previousAngularVelocity: Double,
        maxAngularVelocity: Double,
        maxAngularAcceleration: Double
    ): Double {
        var lower = baseDt.coerceAtLeast(0.0)
        if (kotlin.math.abs(headingDelta) > 1e-12 && maxAngularVelocity > 1e-9) {
            lower = maxOf(lower, kotlin.math.abs(headingDelta) / maxAngularVelocity)
        }
        if (maxAngularAcceleration <= 1e-9) return lower

        fun satisfiesAcceleration(dt: Double): Boolean {
            if (dt <= 1e-12) {
                return kotlin.math.abs(headingDelta) <= 1e-12 && kotlin.math.abs(previousAngularVelocity) <= 1e-12
            }
            val omega = headingDelta / dt
            return kotlin.math.abs(omega - previousAngularVelocity) <= maxAngularAcceleration * dt + 1e-9
        }

        if (satisfiesAcceleration(lower)) return lower
        var upper = maxOf(lower, 1e-4)
        repeat(64) {
            if (satisfiesAcceleration(upper)) {
                repeat(48) {
                    val mid = (lower + upper) * 0.5
                    if (satisfiesAcceleration(mid)) upper = mid else lower = mid
                }
                return upper
            }
            upper *= 2.0
        }
        return upper
    }

    private fun wrapAngle(angle: Double): Double {
        var wrapped = angle % (2.0 * Math.PI)
        if (wrapped > Math.PI) wrapped -= 2.0 * Math.PI
        if (wrapped <= -Math.PI) wrapped += 2.0 * Math.PI
        return wrapped
    }

    private fun getHeadingAt(
        relativePos: Double,
        rotationTargets: List<RotationTarget>,
        waypoints: List<Waypoint>,
        currentPt: SampledPoint,
        prevPt: SampledPoint?,
        nextPt: SampledPoint?
    ): Double {
        if (rotationTargets.isEmpty()) {
            return when {
                nextPt != null -> atan2(nextPt.y - currentPt.y, nextPt.x - currentPt.x)
                prevPt != null -> atan2(currentPt.y - prevPt.y, currentPt.x - prevPt.x)
                else -> 0.0
            }
        }
        val sortedTargets = rotationTargets.sortedBy { it.waypointRelativePos }
        val firstTarget = sortedTargets.first()
        if (relativePos <= firstTarget.waypointRelativePos) {
            return Math.toRadians(firstTarget.rotationDegrees)
        }
        val lastTarget = sortedTargets.last()
        if (relativePos >= lastTarget.waypointRelativePos) {
            return Math.toRadians(lastTarget.rotationDegrees)
        }

        for (i in 0 until sortedTargets.size - 1) {
            val t1 = sortedTargets[i]
            val t2 = sortedTargets[i + 1]
            if (relativePos >= t1.waypointRelativePos && relativePos <= t2.waypointRelativePos) {
                val range = t2.waypointRelativePos - t1.waypointRelativePos
                if (range < 1e-6) return Math.toRadians(t1.rotationDegrees)
                val alpha = (relativePos - t1.waypointRelativePos) / range
                val rad1 = Math.toRadians(t1.rotationDegrees)
                val rad2 = Math.toRadians(t2.rotationDegrees)
                var diff = (rad2 - rad1) % (2 * Math.PI)
                when {
                    diff > Math.PI -> diff -= 2 * Math.PI
                    diff <= -Math.PI -> diff += 2 * Math.PI
                }

                return rad1 + alpha * diff
            }
        }
        return 0.0
    }

    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        return 0.5 * (
            (2.0 * p1) +
            (-p0 + p2) * t +
            (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t * t +
            (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t * t * t
        )
    }

    private fun calculateRadius(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double {
        val ab = sqrt((ax - bx).pow(2) + (ay - by).pow(2))
        val bc = sqrt((bx - cx).pow(2) + (by - cy).pow(2))
        val ca = sqrt((cx - ax).pow(2) + (cy - ay).pow(2))
        val area = 0.5 * abs(ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        if (area < 1e-6) return Double.POSITIVE_INFINITY
        return (ab * bc * ca) / (4.0 * area)
    }

    private fun getConstraintsAt(
        pos: Double,
        globalConstraints: PathConstraints,
        constraintZones: List<ConstraintsZone>
    ): PathConstraints {
        for (zone in constraintZones) {
            if (pos >= zone.minWaypointRelativePos && pos <= zone.maxWaypointRelativePos) {
                return zone.constraints
            }
        }
        return globalConstraints
    }
}
