package com.ares.analytics.ui.components.pathplanner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.League
import com.ares.analytics.shared.PathPlannerEventMarker
import com.ares.analytics.shared.RotationTarget
import com.ares.analytics.ui.theme.*
import com.ares.analytics.util.IndicatorLightColorMapper
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawPlannedSpline(
    pathCache: PathCacheHolder,
    splinePoints: List<Waypoint>,
    waypoints: List<Waypoint>,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League
) {
    val splinePath = if (pathCache.splinePath != null && pathCache.splinePoints === splinePoints && pathCache.w == w && pathCache.h == h) {
        pathCache.splinePath!!
    } else {
        val path = pathCache.splinePath?.apply { reset() } ?: Path()
        if (splinePoints.isNotEmpty()) {
            val firstOffset = getCanvasOffsetBase(splinePoints.first(), w, h, fieldWidthM, fieldHeightM, league)
            path.moveTo(firstOffset.x, firstOffset.y)
            for (i in 1 until splinePoints.size) {
                val offset = getCanvasOffsetBase(splinePoints[i], w, h, fieldWidthM, fieldHeightM, league)
                path.lineTo(offset.x, offset.y)
            }
        }
        pathCache.splinePoints = splinePoints
        pathCache.w = w
        pathCache.h = h
        pathCache.splinePath = path
        path
    }
    if (waypoints.size >= 2) {
        drawPath(path = splinePath, color = AresPathPlanned, style = Stroke(width = 4f, pathEffect = pathCache.dashEffect10))
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawEventMarkers(
    waypoints: List<Waypoint>,
    eventMarkerPoints: List<Waypoint>,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League,
    selectedEventMarkerIndex: Int
) {
    if (waypoints.size >= 2) {
        eventMarkerPoints.forEachIndexed { idx, markerOffsetWp ->
            val markerOffset = getCanvasOffsetBase(markerOffsetWp, w, h, fieldWidthM, fieldHeightM, league)
            drawCircle(
                color = Color(0xFF9C27B0).copy(alpha = 0.4f),
                radius = 8.dp.toPx(),
                center = markerOffset
            )
            drawCircle(
                color = Color(0xFF9C27B0),
                radius = 5.dp.toPx(),
                center = markerOffset
            )
            if (idx == selectedEventMarkerIndex) {
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = markerOffset,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawConstraintZones(
    pathCache: PathCacheHolder,
    waypoints: List<Waypoint>,
    constraintZoneSplines: List<List<Waypoint>>,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League
) {
    if (waypoints.size >= 2) {
        constraintZoneSplines.forEach { zoneSpline ->
            if (zoneSpline.size >= 2) {
                val path = pathCache.reusablePath.apply { reset() }
                val firstOffset = getCanvasOffsetBase(zoneSpline.first(), w, h, fieldWidthM, fieldHeightM, league)
                path.moveTo(firstOffset.x, firstOffset.y)
                for (i in 1 until zoneSpline.size) {
                    val offset = getCanvasOffsetBase(zoneSpline[i], w, h, fieldWidthM, fieldHeightM, league)
                    path.lineTo(offset.x, offset.y)
                }
                drawPath(
                    path = path,
                    color = AresRed,
                    style = Stroke(
                        width = 8.dp.toPx(),
                        pathEffect = pathCache.dashEffect8
                    )
                )
            }
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawPointTowardsZones(
    pathCache: PathCacheHolder,
    waypoints: List<Waypoint>,
    pointTowardsZoneRenderData: List<PointTowardsZoneRenderData>,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League
) {
    if (waypoints.size >= 2) {
        pointTowardsZoneRenderData.forEach { data ->
            val targetOffset = getCanvasOffsetBase(data.target, w, h, fieldWidthM, fieldHeightM, league)
            
            drawCircle(color = AresCyan.copy(alpha = 0.3f), radius = 12.dp.toPx(), center = targetOffset)
            drawCircle(color = AresCyan, radius = 6.dp.toPx(), center = targetOffset, style = Stroke(width = 2.dp.toPx()))
            drawLine(color = AresCyan, start = Offset(targetOffset.x - 10.dp.toPx(), targetOffset.y), end = Offset(targetOffset.x + 10.dp.toPx(), targetOffset.y), strokeWidth = 1.5.dp.toPx())
            drawLine(color = AresCyan, start = Offset(targetOffset.x, targetOffset.y - 10.dp.toPx()), end = Offset(targetOffset.x, targetOffset.y + 10.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            
            data.splinePoints.forEach { wp ->
                val splineOffset = getCanvasOffsetBase(wp, w, h, fieldWidthM, fieldHeightM, league)
                drawLine(
                    color = AresCyan.copy(alpha = 0.3f),
                    start = splineOffset,
                    end = targetOffset,
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = pathCache.dashEffect5
                )
            }
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawHolonomicRotationTargets(
    waypoints: List<Waypoint>,
    rotationTargets: List<RotationTarget>,
    rotationTargetPoints: List<Waypoint>,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League
) {
    if (waypoints.size >= 2) {
        rotationTargetPoints.forEachIndexed { idx, targetWp ->
            val target = rotationTargets[idx]
            // Only draw the ghost outline for the initial waypoint
            if (kotlin.math.abs(target.waypointRelativePos) < 1e-3) {
                val offset = getCanvasOffsetBase(targetWp, w, h, fieldWidthM, fieldHeightM, league)
                val rectSize = 24.dp.toPx()
                
                drawContext.canvas.save()
                drawContext.transform.rotate(degrees = -target.rotationDegrees.toFloat() - 90f, pivot = offset)
                
                drawRect(color = AresAmber.copy(alpha = 0.25f), topLeft = Offset(offset.x - rectSize/2, offset.y - rectSize/2), size = Size(rectSize, rectSize))
                drawRect(color = AresAmber, topLeft = Offset(offset.x - rectSize/2, offset.y - rectSize/2), size = Size(rectSize, rectSize), style = Stroke(width = 1.5.dp.toPx()))
                drawLine(color = AresCyan, start = Offset(offset.x + rectSize/2, offset.y - rectSize/2), end = Offset(offset.x + rectSize/2, offset.y + rectSize/2), strokeWidth = 2.dp.toPx())
                
                drawContext.canvas.restore()
                drawCircle(color = AresAmber, radius = 4.dp.toPx(), center = offset)
            }
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawActualPathAndDeviations(
    pathCache: PathCacheHolder,
    actualPath: List<Waypoint>,
    waypoints: List<Waypoint>,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League
) {
    val actualPathObj = if (pathCache.actualPath != null &&
        pathCache.actualPoints.size == actualPath.size &&
        pathCache.actualPoints.firstOrNull() == actualPath.firstOrNull() &&
        pathCache.actualPoints.lastOrNull() == actualPath.lastOrNull() &&
        pathCache.w == w && pathCache.h == h) {
        pathCache.actualPath!!
    } else {
        val path = Path()
        if (actualPath.isNotEmpty()) {
            val firstOffset = getCanvasOffsetBase(actualPath.first(), w, h, fieldWidthM, fieldHeightM, league)
            path.moveTo(firstOffset.x, firstOffset.y)
            for (i in 1 until actualPath.size) {
                val offset = getCanvasOffsetBase(actualPath[i], w, h, fieldWidthM, fieldHeightM, league)
                path.lineTo(offset.x, offset.y)
            }
        }
        pathCache.actualPoints = actualPath
        pathCache.w = w
        pathCache.h = h
        pathCache.actualPath = path
        path
    }
    
    if (actualPath.size >= 2) {
        drawPath(path = actualPathObj, color = AresPathActual, style = Stroke(width = 3f))

        if (waypoints.size >= 2) {
            val step = maxOf(1, actualPath.size / 20)
            for (i in 0 until actualPath.size step step) {
                val actualWp = actualPath[i]
                val actualOffset = getCanvasOffsetBase(actualWp, w, h, fieldWidthM, fieldHeightM, league)
                var closestWp = waypoints.first()
                var minDistance = Double.MAX_VALUE
                val wpSize = waypoints.size
                for (j in 0 until wpSize) {
                    val plannedWp = waypoints[j]
                    val dist = sqrt((actualWp.x - plannedWp.x).pow(2) + (actualWp.y - plannedWp.y).pow(2))
                    if (dist < minDistance) {
                        minDistance = dist
                        closestWp = plannedWp
                    }
                }
                val plannedOffset = getCanvasOffsetBase(closestWp, w, h, fieldWidthM, fieldHeightM, league)
                val deviationM = minDistance
                val deviationColor = when {
                    deviationM < 0.02 -> AresGreen 
                    deviationM < 0.05 -> AresAmber 
                    else -> AresRed               
                }
                drawLine(color = deviationColor, start = actualOffset, end = plannedOffset, strokeWidth = 1.5f)
            }
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawContextPath(
    pathCache: PathCacheHolder,
    contextPath: List<Waypoint>,
    contextWaypoints: List<Waypoint>?,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League
) {
    if (contextPath.size >= 2) {
        val path = pathCache.reusablePath.apply { reset() }
        val firstOffset = getCanvasOffsetBase(contextPath.first(), w, h, fieldWidthM, fieldHeightM, league)
        path.moveTo(firstOffset.x, firstOffset.y)
        for (i in 1 until contextPath.size) {
            val offset = getCanvasOffsetBase(contextPath[i], w, h, fieldWidthM, fieldHeightM, league)
            path.lineTo(offset.x, offset.y)
        }
        drawPath(
            path = path,
            color = AresPathPlanned.copy(alpha = 0.35f),
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = pathCache.dashEffect5
            )
        )
    }

    if (contextWaypoints != null) {
        for (wp in contextWaypoints) {
            val offset = getCanvasOffsetBase(wp, w, h, fieldWidthM, fieldHeightM, league)
            drawCircle(
                color = AresPathPlanned.copy(alpha = 0.5f),
                radius = 5.dp.toPx(),
                center = offset
            )
            drawCircle(
                color = AresBackground.copy(alpha = 0.5f),
                radius = 2.dp.toPx(),
                center = offset
            )
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawRobotRepresentations(
    pathCache: PathCacheHolder,
    actualPath: List<Waypoint>,
    estimatedPose: Waypoint?,
    playbackPose: Waypoint?,
    visionPoses: List<Waypoint>,
    odomPose: Waypoint? = null,
    showTruePose: Boolean = true,
    showEkfPose: Boolean = true,
    showOdomPose: Boolean = true,
    showVisionPoses: Boolean = true,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League,
    indicatorLightPosition: Double = -1.0
) {
    val activeRobotWp = actualPath.lastOrNull()
    if (activeRobotWp != null && showTruePose) {
        val robotOffset = getCanvasOffsetBase(activeRobotWp, w, h, fieldWidthM, fieldHeightM, league)
        val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
        
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = -Math.toDegrees(activeRobotWp.headingRad ?: 0.0).toFloat() - 90f, pivot = robotOffset)
        drawRect(color = AresCyan.copy(alpha = 0.2f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
        drawRect(color = AresCyan, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = AresAmber, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 3.dp.toPx())
        val arrowPath = pathCache.reusableArrowPath.apply {
            reset()
            moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
            lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
            lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
            close()
        }
        drawPath(path = arrowPath, color = AresAmber)

        // ── Indicator Light Box ──
        // Draw a colored square in the rear-left corner of the robot body.
        // Inside the save/restore block so it rotates with the robot.
        if (indicatorLightPosition >= 0.0) {
            val lightColor = IndicatorLightColorMapper.positionToColor(indicatorLightPosition)
            val lightSize = robotSizePx * 0.22f
            val lightMargin = robotSizePx * 0.06f
            val lightTopLeft = Offset(
                robotOffset.x - robotSizePx / 2 + lightMargin,
                robotOffset.y - robotSizePx / 2 + lightMargin
            )
            // Glowing fill
            drawRect(
                color = lightColor.copy(alpha = 0.85f),
                topLeft = lightTopLeft,
                size = Size(lightSize, lightSize),
                style = Fill
            )
            // Border
            drawRect(
                color = lightColor,
                topLeft = lightTopLeft,
                size = Size(lightSize, lightSize),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        drawContext.canvas.restore()
    }

    if (estimatedPose != null && showEkfPose) {
        val robotOffset = getCanvasOffsetBase(estimatedPose, w, h, fieldWidthM, fieldHeightM, league)
        val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
        
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = -Math.toDegrees(estimatedPose.headingRad ?: 0.0).toFloat() - 90f, pivot = robotOffset)

        // Draw Limelight FOV Cone projecting forward (+X robot-relative points RIGHT in this rotated context)
        val cameraOffsetPx = ((0.18 / fieldWidthM) * w).toFloat()
        val rangePx = ((4.0 / fieldWidthM) * w).toFloat()
        val fovCenter = Offset(robotOffset.x + cameraOffsetPx, robotOffset.y)
        
        drawArc(
            color = AresGold.copy(alpha = 0.08f),
            startAngle = -30f,
            sweepAngle = 60f,
            useCenter = true,
            topLeft = Offset(fovCenter.x - rangePx, fovCenter.y - rangePx),
            size = Size(rangePx * 2f, rangePx * 2f)
        )
        drawArc(
            color = AresGold.copy(alpha = 0.25f),
            startAngle = -30f,
            sweepAngle = 60f,
            useCenter = true,
            topLeft = Offset(fovCenter.x - rangePx, fovCenter.y - rangePx),
            size = Size(rangePx * 2f, rangePx * 2f),
            style = Stroke(width = 1.dp.toPx(), pathEffect = pathCache.dashEffect4)
        )

        drawRect(color = AresAmber.copy(alpha = 0.15f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
        drawRect(color = AresAmber, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 1.5.dp.toPx(), pathEffect = pathCache.dashEffect10))
        drawLine(color = AresAmber, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 2.dp.toPx())
        val arrowPath = pathCache.reusableArrowPath.apply {
            reset()
            moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
            lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
            lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
            close()
        }
        drawPath(path = arrowPath, color = AresAmber)
        drawContext.canvas.restore()
    }

    if (odomPose != null && showOdomPose) {
        val robotOffset = getCanvasOffsetBase(odomPose, w, h, fieldWidthM, fieldHeightM, league)
        val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
        
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = -Math.toDegrees(odomPose.headingRad ?: 0.0).toFloat() - 90f, pivot = robotOffset)
        drawRect(color = AresGreen.copy(alpha = 0.15f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
        drawRect(color = AresGreen, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 1.5.dp.toPx(), pathEffect = pathCache.dashEffect10))
        drawLine(color = AresGreen, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 2.dp.toPx())
        val arrowPath = pathCache.reusableArrowPath.apply {
            reset()
            moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
            lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
            lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
            close()
        }
        drawPath(path = arrowPath, color = AresGreen)
        drawContext.canvas.restore()
    }

    if (playbackPose != null) {
        val robotOffset = getCanvasOffsetBase(playbackPose, w, h, fieldWidthM, fieldHeightM, league)
        val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
        
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = -Math.toDegrees(playbackPose.headingRad ?: 0.0).toFloat() - 90f, pivot = robotOffset)
        drawRect(color = AresCyan.copy(alpha = 0.3f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
        drawRect(color = AresCyan, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = AresCyan, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 3.dp.toPx())
        val arrowPath = pathCache.reusableArrowPath.apply {
            reset()
            moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
            lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
            lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
            close()
        }
        drawPath(path = arrowPath, color = AresCyan)
        drawContext.canvas.restore()
    }

    if (showVisionPoses) {
        visionPoses.forEach { pose ->
            val robotOffset = getCanvasOffsetBase(pose, w, h, fieldWidthM, fieldHeightM, league)
            val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
            
            drawContext.canvas.save()
            drawContext.transform.rotate(degrees = -Math.toDegrees(pose.headingRad ?: 0.0).toFloat() - 90f, pivot = robotOffset)
            drawRect(color = AresGold.copy(alpha = 0.15f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
            drawRect(color = AresGold, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 1.5.dp.toPx(), pathEffect = pathCache.dashEffect4))
            drawLine(color = AresGold, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 2.dp.toPx())
            val arrowPath = pathCache.reusableArrowPath.apply {
                reset()
                moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
                lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
                lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
                close()
            }
            drawPath(path = arrowPath, color = AresGold)
            drawContext.canvas.restore()
        }
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawWaypoints(
    pathCache: PathCacheHolder,
    waypoints: List<Waypoint>,
    selectedWaypointIndex: Int,
    isDraggingHeading: Boolean,
    isDraggingPrevHeading: Boolean,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League,
    rotationTargets: List<RotationTarget> = emptyList(),
    isDraggingRotation: Boolean = false
) {
    waypoints.forEachIndexed { idx, wp ->
        val offset = getCanvasOffsetBase(wp, w, h, fieldWidthM, fieldHeightM, league)
        val isSelected = idx == selectedWaypointIndex
        val color = if (isSelected) AresCyan else AresTextPrimary

        // --- Tangent heading handle (amber arrowhead) ---
        val resolvedHeading = resolveHeading(waypoints, idx)
        val hasExplicitHeading = wp.headingRad != null
        val handleMeters = Waypoint(wp.x + wp.nextControlLength * cos(resolvedHeading), wp.y + wp.nextControlLength * sin(resolvedHeading))
        val arrowEnd = getCanvasOffsetBase(handleMeters, w, h, fieldWidthM, fieldHeightM, league)

        // Tangent line
        val tangentAlpha = when {
            !hasExplicitHeading && !isSelected -> 0.15f  // auto heading, not selected: very dim
            !hasExplicitHeading -> 0.35f                  // auto heading, selected: dim
            isSelected -> 0.9f                            // explicit, selected: bright
            else -> 0.4f                                  // explicit, not selected
        }
        drawLine(color = color.copy(alpha = tangentAlpha), start = offset, end = arrowEnd, strokeWidth = if (hasExplicitHeading) 2.dp.toPx() else 1.dp.toPx())

        // Arrowhead at end of tangent line
        val handleColor = when {
            isSelected && isDraggingHeading -> AresCyan
            !hasExplicitHeading -> Color.Gray    // ghost color for auto heading
            else -> AresAmber
        }
        val handleAlpha = when {
            !hasExplicitHeading && !isSelected -> 0.2f
            !hasExplicitHeading -> 0.4f
            isSelected -> 1f
            else -> 0.5f
        }
        val arrowSize = if (isSelected) 8.dp.toPx() else 6.dp.toPx()
        val dx = arrowEnd.x - offset.x
        val dy = arrowEnd.y - offset.y
        val len = sqrt(dx * dx + dy * dy)
        if (len > 1f) {
            val ux = dx / len; val uy = dy / len
            val perpX = -uy; val perpY = ux
            val arrowPath = pathCache.reusableArrowPath.apply {
                reset()
                moveTo(arrowEnd.x, arrowEnd.y)
                lineTo(arrowEnd.x - ux * arrowSize * 1.8f + perpX * arrowSize, arrowEnd.y - uy * arrowSize * 1.8f + perpY * arrowSize)
                lineTo(arrowEnd.x - ux * arrowSize * 0.8f, arrowEnd.y - uy * arrowSize * 0.8f)
                lineTo(arrowEnd.x - ux * arrowSize * 1.8f - perpX * arrowSize, arrowEnd.y - uy * arrowSize * 1.8f - perpY * arrowSize)
                close()
            }
            drawPath(path = arrowPath, color = handleColor.copy(alpha = handleAlpha))
            drawPath(path = arrowPath, color = handleColor, style = Stroke(width = 1.5f))
        } else {
            drawCircle(color = handleColor.copy(alpha = handleAlpha), radius = arrowSize, center = arrowEnd)
        }

        // --- Prev tangent heading handle ---
        val prevHandleMeters = Waypoint(wp.x + wp.prevControlLength * cos(resolvedHeading + Math.PI), wp.y + wp.prevControlLength * sin(resolvedHeading + Math.PI))
        val prevArrowEnd = getCanvasOffsetBase(prevHandleMeters, w, h, fieldWidthM, fieldHeightM, league)
        
        drawLine(color = color.copy(alpha = tangentAlpha), start = offset, end = prevArrowEnd, strokeWidth = if (hasExplicitHeading) 2.dp.toPx() else 1.dp.toPx())
        val prevHandleColor = when {
            isSelected && isDraggingPrevHeading -> AresCyan
            !hasExplicitHeading -> Color.Gray
            else -> AresAmber
        }
        val prevDx = prevArrowEnd.x - offset.x
        val prevDy = prevArrowEnd.y - offset.y
        val prevLen = sqrt(prevDx * prevDx + prevDy * prevDy)
        if (prevLen > 1f) {
            val ux = prevDx / prevLen; val uy = prevDy / prevLen
            val perpX = -uy; val perpY = ux
            val arrowPath = pathCache.reusableArrowPath.apply {
                reset()
                moveTo(prevArrowEnd.x, prevArrowEnd.y)
                lineTo(prevArrowEnd.x - ux * arrowSize * 1.8f + perpX * arrowSize, prevArrowEnd.y - uy * arrowSize * 1.8f + perpY * arrowSize)
                lineTo(prevArrowEnd.x - ux * arrowSize * 0.8f, prevArrowEnd.y - uy * arrowSize * 0.8f)
                lineTo(prevArrowEnd.x - ux * arrowSize * 1.8f - perpX * arrowSize, prevArrowEnd.y - uy * arrowSize * 1.8f - perpY * arrowSize)
                close()
            }
            drawPath(path = arrowPath, color = prevHandleColor.copy(alpha = handleAlpha))
            drawPath(path = arrowPath, color = prevHandleColor, style = Stroke(width = 1.5f))
        } else {
            drawCircle(color = prevHandleColor.copy(alpha = handleAlpha), radius = arrowSize, center = prevArrowEnd)
        }

        // --- Waypoint center dot (drawn on top) ---
        drawCircle(color = color, radius = 8.dp.toPx(), center = offset)
        drawCircle(color = AresBackground, radius = 4.dp.toPx(), center = offset)

        // --- Rotation handle (green diamond) — reads rotation directly from waypoint ---
        val hasExplicitRotation = wp.rotationDeg != null
        val rotDeg = wp.rotationDeg ?: 0.0
        val rotAngleRad = Math.toRadians(-rotDeg - 90.0)
        val rotHandleLenPx = 30.dp.toPx()
        val rotHandleX = offset.x + rotHandleLenPx * cos(rotAngleRad).toFloat()
        val rotHandleY = offset.y + rotHandleLenPx * sin(rotAngleRad).toFloat()
        val rotHandleCenter = Offset(rotHandleX, rotHandleY)

        when {
            hasExplicitRotation -> {
                // Dashed line from center to rotation handle
                val rotColor = if (isSelected && isDraggingRotation) AresCyan else AresGreen
                val rotAlpha = if (isSelected) 1f else 0.5f
                drawLine(
                    color = rotColor.copy(alpha = 0.7f * rotAlpha),
                    start = offset,
                    end = rotHandleCenter,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = pathCache.dashEffect6_4
                )

                // Diamond shape at rotation handle position
                val diamondSize = if (isSelected) 7.dp.toPx() else 5.dp.toPx()
                val diamondPath = pathCache.reusableDiamondPath.apply {
                    reset()
                    moveTo(rotHandleX, rotHandleY - diamondSize)
                    lineTo(rotHandleX + diamondSize, rotHandleY)
                    lineTo(rotHandleX, rotHandleY + diamondSize)
                    lineTo(rotHandleX - diamondSize, rotHandleY)
                    close()
                }
                drawPath(path = diamondPath, color = rotColor.copy(alpha = 0.6f * rotAlpha))
                drawPath(path = diamondPath, color = rotColor.copy(alpha = rotAlpha), style = Stroke(width = 2f))

                // Arc indicator (only on selected waypoint)
                if (isSelected) {
                    val arcRadius = 18.dp.toPx()
                    drawArc(
                        color = rotColor.copy(alpha = 0.3f),
                        startAngle = Math.toDegrees(rotAngleRad).toFloat() - 30f,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(offset.x - arcRadius, offset.y - arcRadius),
                        size = Size(arcRadius * 2, arcRadius * 2),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = pathCache.dashEffect4_3)
                    )
                }
            }
            isSelected -> {
                // Unspecified rotation: draw a small dimmed circle placeholder at 0° position
                val ghostColor = Color.Gray
                drawLine(
                    color = ghostColor.copy(alpha = 0.25f),
                    start = offset,
                    end = rotHandleCenter,
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = pathCache.dashEffect6_4
                )
                drawCircle(color = ghostColor.copy(alpha = 0.3f), radius = 4.dp.toPx(), center = rotHandleCenter)
            }
        }
    }
}
