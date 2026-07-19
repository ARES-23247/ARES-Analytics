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

object TrajectoryEstimator {

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

    fun generateTrajectory(
        waypoints: List<Waypoint>,
        globalConstraints: PathConstraints,
        constraintZones: List<ConstraintsZone>,
        rotationTargets: List<RotationTarget>,
        idealStartingState: IdealStartingState?,
        goalEndState: GoalEndState?
    ): Trajectory {
        if (waypoints.size < 2) return Trajectory(0.0, emptyList())

        val sampledPoints = pointPool.get()!!
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

        for (i in 0 until pointCount) {
            if (i > 0) {
                val ds = sampledPoints[i].s - sampledPoints[i - 1].s
                val avgV = (sampledPoints[i].v + sampledPoints[i - 1].v) / 2.0
                currentTime += if (avgV > 1e-4) ds / avgV else 0.0
            }
            sampledPoints[i].t = currentTime

            val prevPt = if (i > 0) sampledPoints[i - 1] else null
            val nextPt = if (i < pointCount - 1) sampledPoints[i + 1] else null
            val headingRad = getHeadingAt(sampledPoints[i].relativePos, combinedRotationTargets, waypoints, sampledPoints[i], prevPt, nextPt)

            states.add(
                TrajectoryState(
                    timeSeconds = currentTime,
                    x = sampledPoints[i].x,
                    y = sampledPoints[i].y,
                    headingRad = headingRad,
                    velocity = sampledPoints[i].v
                )
            )
        }

        return Trajectory(currentTime, states)
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
