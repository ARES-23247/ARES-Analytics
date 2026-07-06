package com.ares.analytics.ui.components.pathplanner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.League
import com.ares.analytics.shared.PathPlannerEventMarker
import com.ares.analytics.shared.RotationTarget
import com.ares.analytics.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

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
        val path = Path()
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
        drawPath(path = splinePath, color = AresPathPlanned, style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
    }
}

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

fun DrawScope.drawConstraintZones(
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
                val path = Path()
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
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                )
            }
        }
    }
}

fun DrawScope.drawPointTowardsZones(
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
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                )
            }
        }
    }
}

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
            val offset = getCanvasOffsetBase(targetWp, w, h, fieldWidthM, fieldHeightM, league)
            val rectSize = 24.dp.toPx()
            
            drawContext.canvas.save()
            drawContext.transform.rotate(degrees = -target.rotationDegrees.toFloat(), pivot = offset)
            
            drawRect(color = AresAmber.copy(alpha = 0.25f), topLeft = Offset(offset.x - rectSize/2, offset.y - rectSize/2), size = Size(rectSize, rectSize))
            drawRect(color = AresAmber, topLeft = Offset(offset.x - rectSize/2, offset.y - rectSize/2), size = Size(rectSize, rectSize), style = Stroke(width = 1.5.dp.toPx()))
            drawLine(color = AresCyan, start = Offset(offset.x + rectSize/2, offset.y - rectSize/2), end = Offset(offset.x + rectSize/2, offset.y + rectSize/2), strokeWidth = 2.dp.toPx())
            
            drawContext.canvas.restore()
            drawCircle(color = AresAmber, radius = 4.dp.toPx(), center = offset)
        }
    }
}

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
            actualPath.chunked(maxOf(1, actualPath.size / 20)).forEach { actualPoint ->
                val actualWp = actualPoint.firstOrNull() ?: return@forEach
                val actualOffset = getCanvasOffsetBase(actualWp, w, h, fieldWidthM, fieldHeightM, league)
                
                var closestWp = waypoints.first()
                var minDistance = Double.MAX_VALUE
                waypoints.forEach { plannedWp ->
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

fun DrawScope.drawRobotRepresentations(
    actualPath: List<Waypoint>,
    estimatedPose: Waypoint?,
    playbackPose: Waypoint?,
    visionPoses: List<Waypoint>,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League
) {
    val activeRobotWp = actualPath.lastOrNull()
    if (activeRobotWp != null) {
        val robotOffset = getCanvasOffsetBase(activeRobotWp, w, h, fieldWidthM, fieldHeightM, league)
        val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
        
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = -Math.toDegrees(activeRobotWp.headingRad).toFloat(), pivot = robotOffset)
        drawRect(color = AresCyan.copy(alpha = 0.2f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
        drawRect(color = AresCyan, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = AresAmber, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 3.dp.toPx())
        val arrowPath = Path().apply {
            moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
            lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
            lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
            close()
        }
        drawPath(path = arrowPath, color = AresAmber)
        drawContext.canvas.restore()
    }

    if (estimatedPose != null) {
        val robotOffset = getCanvasOffsetBase(estimatedPose, w, h, fieldWidthM, fieldHeightM, league)
        val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
        
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = -Math.toDegrees(estimatedPose.headingRad).toFloat(), pivot = robotOffset)
        drawRect(color = AresAmber.copy(alpha = 0.15f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
        drawRect(color = AresAmber, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
        drawLine(color = AresAmber, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 2.dp.toPx())
        val arrowPath = Path().apply {
            moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
            lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
            lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
            close()
        }
        drawPath(path = arrowPath, color = AresAmber)
        drawContext.canvas.restore()
    }

    if (playbackPose != null) {
        val robotOffset = getCanvasOffsetBase(playbackPose, w, h, fieldWidthM, fieldHeightM, league)
        val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
        
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = -Math.toDegrees(playbackPose.headingRad).toFloat(), pivot = robotOffset)
        drawRect(color = AresCyan.copy(alpha = 0.3f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
        drawRect(color = AresCyan, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = AresCyan, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 3.dp.toPx())
        val arrowPath = Path().apply {
            moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
            lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
            lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
            close()
        }
        drawPath(path = arrowPath, color = AresCyan)
        drawContext.canvas.restore()
    }

    visionPoses.forEach { pose ->
        val robotOffset = getCanvasOffsetBase(pose, w, h, fieldWidthM, fieldHeightM, league)
        val robotSizePx = ((0.45 / fieldWidthM) * w).toFloat()
        
        drawContext.canvas.save()
        drawContext.transform.rotate(degrees = -Math.toDegrees(pose.headingRad).toFloat(), pivot = robotOffset)
        drawRect(color = AresGreen.copy(alpha = 0.15f), topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx))
        drawRect(color = AresGreen, topLeft = Offset(robotOffset.x - robotSizePx / 2, robotOffset.y - robotSizePx / 2), size = Size(robotSizePx, robotSizePx), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)))
        drawLine(color = AresGreen, start = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 2), end = Offset(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 2), strokeWidth = 2.dp.toPx())
        val arrowPath = Path().apply {
            moveTo(robotOffset.x + robotSizePx / 2, robotOffset.y - robotSizePx / 4)
            lineTo(robotOffset.x + robotSizePx / 2 + robotSizePx / 4, robotOffset.y)
            lineTo(robotOffset.x + robotSizePx / 2, robotOffset.y + robotSizePx / 4)
            close()
        }
        drawPath(path = arrowPath, color = AresGreen)
        drawContext.canvas.restore()
    }
}

fun DrawScope.drawWaypoints(
    waypoints: List<Waypoint>,
    selectedWaypointIndex: Int,
    isDraggingHeading: Boolean,
    w: Float,
    h: Float,
    fieldWidthM: Double,
    fieldHeightM: Double,
    league: League
) {
    waypoints.forEachIndexed { idx, wp ->
        val offset = getCanvasOffsetBase(wp, w, h, fieldWidthM, fieldHeightM, league)
        val isSelected = idx == selectedWaypointIndex
        val color = if (isSelected) AresCyan else AresTextPrimary

        drawCircle(color = color, radius = 8.dp.toPx(), center = offset)
        drawCircle(color = AresBackground, radius = 4.dp.toPx(), center = offset)

        val handleMeters = Waypoint(wp.x + wp.tangentMagnitude * kotlin.math.cos(wp.headingRad), wp.y + wp.tangentMagnitude * kotlin.math.sin(wp.headingRad))
        val arrowEnd = getCanvasOffsetBase(handleMeters, w, h, fieldWidthM, fieldHeightM, league)
        drawLine(color = color.copy(alpha = 0.8f), start = offset, end = arrowEnd, strokeWidth = 2.dp.toPx())

        val handleColor = if (isSelected && isDraggingHeading) AresCyan else AresAmber
        drawCircle(color = handleColor, radius = 6.dp.toPx(), center = arrowEnd)
        drawCircle(color = AresBackground, radius = 3.dp.toPx(), center = arrowEnd)
    }
}
