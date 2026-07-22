package com.ares.analytics.service

import java.util.Random
import kotlin.math.*

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class SimPoint(val x: Double, val y: Double)

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class SimPolygon(val vertices: List<SimPoint>) {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun getEdges(): List<Pair<SimPoint, SimPoint>> {
        val edges = mutableListOf<Pair<SimPoint, SimPoint>>()
        for (i in vertices.indices) {
            edges.add(Pair(vertices[i], vertices[(i + 1) % vertices.size]))
        }
        return edges
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class SimulationService {

    private val random = Random()
    private val obstacles = mutableListOf<SimPolygon>()

    init {
        // Define default FTC center trusses/obstacles (around center of 3.6m x 3.6m field)
        // Center truss pole 1
        obstacles.add(SimPolygon(listOf(
            SimPoint(-0.1, -0.1), SimPoint(0.1, -0.1), SimPoint(0.1, 0.1), SimPoint(-0.1, 0.1)
        )))
        // Backdrop wall in FTC Centerstage
        obstacles.add(SimPolygon(listOf(
            SimPoint(1.6, -0.9), SimPoint(1.8, -0.9), SimPoint(1.8, -0.5), SimPoint(1.6, -0.5)
        )))
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun addObstacle(polygon: SimPolygon) {
        obstacles.add(polygon)
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun clearObstacles() {
        obstacles.clear()
    }

    /**
     * Checks if the line of sight between the camera and the AprilTag is clear.
     */
    fun hasLineOfSight(cam: SimPoint, tag: SimPoint): Boolean {
        for (obstacle in obstacles) {
            for (edge in obstacle.getEdges()) {
                if (segmentsIntersect(cam, tag, edge.first, edge.second)) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Generates a synthetic camera packet with Gaussian noise and simulated latency.
     */
    fun generateSyntheticVision(
        robotX: Double,
        robotY: Double,
        robotHeadingRad: Double,
        tagX: Double,
        tagY: Double,
        noiseStdDev: Double = 0.02 // 2 cm standard deviation
    ): SyntheticVisionResult? {
        val cam = SimPoint(robotX, robotY)
        val tag = SimPoint(tagX, tagY)

        if (!hasLineOfSight(cam, tag)) {
            return null // Tag is occluded
        }

        // Relative distance and angle
        val dx = tag.x - robotX
        val dy = tag.y - robotY
        val dist = sqrt(dx * dx + dy * dy)

        // Only visible if within 3.0 meters and within 60 degrees camera FOV
        if (dist > 3.0) return null
        val angleToTag = atan2(dy, dx)
        val relativeAngle = normalizeAngle(angleToTag - robotHeadingRad)

        if (abs(relativeAngle) > Math.toRadians(30.0)) {
            return null // Outside camera FOV
        }

        // Add Gaussian noise
        val noisyDist = dist + random.nextGaussian() * noiseStdDev
        val noisyAngle = relativeAngle + random.nextGaussian() * (noiseStdDev * 0.1)

        // Simulate 40ms capture latency
        val latencyMs = 40L

        return SyntheticVisionResult(
            distance = noisyDist,
            relativeAngleRad = noisyAngle,
            latencyMs = latencyMs
        )
    }

    private fun segmentsIntersect(a: SimPoint, b: SimPoint, c: SimPoint, d: SimPoint): Boolean {
        val d1 = direction(c, d, a)
        val d2 = direction(c, d, b)
        val d3 = direction(a, b, c)
        val d4 = direction(a, b, d)

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true
        }

        // Collinear cases
        if (d1 == 0.0 && onSegment(c, d, a)) return true
        if (d2 == 0.0 && onSegment(c, d, b)) return true
        if (d3 == 0.0 && onSegment(a, b, c)) return true
        if (d4 == 0.0 && onSegment(a, b, d)) return true

        return false
    }

    private fun direction(pi: SimPoint, pj: SimPoint, pk: SimPoint): Double {
        return (pk.x - pi.x) * (pj.y - pi.y) - (pj.x - pi.x) * (pk.y - pi.y)
    }

    private fun onSegment(pi: SimPoint, pj: SimPoint, pk: SimPoint): Boolean {
        return pk.x >= minOf(pi.x, pj.x) && pk.x <= maxOf(pi.x, pj.x) &&
               pk.y >= minOf(pi.y, pj.y) && pk.y <= maxOf(pi.y, pj.y)
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle
        while (a > Math.PI) a -= 2 * Math.PI
        while (a < -Math.PI) a += 2 * Math.PI
        return a
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class SyntheticVisionResult(
    val distance: Double,
    val relativeAngleRad: Double,
    val latencyMs: Long
)
