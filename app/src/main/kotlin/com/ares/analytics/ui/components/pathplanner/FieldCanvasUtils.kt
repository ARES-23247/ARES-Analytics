package com.ares.analytics.ui.components.pathplanner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.*

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
enum class EditorMode {
    SELECT, ADD_WAYPOINT, DRAW_POLYGON, DRAW_CIRCLE, DRAW_RECTANGLE, PLACE_GAME_PIECE, PLACE_APRILTAG, PLACE_FIELD_WAYPOINT, ERASER
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class PointTowardsZoneRenderData(
    val target: Waypoint,
    val splinePoints: List<Waypoint>
)

// Precomputed Cache Data Structures for zero-allocation rendering performance
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class PathCacheHolder {
    var splinePoints: List<Waypoint> = emptyList()
    var actualPoints: List<Waypoint> = emptyList()
    var constraintSplines: List<List<Waypoint>> = emptyList()
    var w: Float = 0f
    var h: Float = 0f
    var splinePath: Path? = null
    var actualPath: Path? = null
    var constraintPaths: List<Path> = emptyList()
    val reusableArrowPath = Path()
    val reusableDiamondPath = Path()
    val reusableXAxisPath = Path()
    val reusableYAxisPath = Path()
    val reusablePath = Path()
    val dashEffect10 = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    val dashEffect8 = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
    val dashEffect5 = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
    val dashEffect4 = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
    val dashEffect6_4 = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
    val dashEffect4_3 = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
}

// Convert coordinates base models
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun getCanvasOffsetBase(
    wp: Waypoint,
    canvasW: Float,
    canvasH: Float,
    fieldW: Double,
    fieldH: Double,
    league: League
): Offset {
    return if (league == League.FTC) {
        val x = (-wp.y / fieldW + 0.5) * canvasW
        val y = (-wp.x / fieldH + 0.5) * canvasH
        Offset(x.toFloat(), y.toFloat())
    } else {
        val x = (wp.x / fieldW) * canvasW
        val y = ((fieldH - wp.y) / fieldH) * canvasH
        Offset(x.toFloat(), y.toFloat())
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun getRobotCoordBase(
    offset: Offset,
    canvasW: Float,
    canvasH: Float,
    fieldW: Double,
    fieldH: Double,
    league: League
): Waypoint {
    return if (league == League.FTC) {
        val rx = -(offset.y / canvasH - 0.5) * fieldH
        val ry = -(offset.x / canvasW - 0.5) * fieldW
        Waypoint(rx, ry)
    } else {
        val rx = (offset.x / canvasW) * fieldW
        val ry = fieldH - (offset.y / canvasH) * fieldH
        Waypoint(rx, ry)
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun getTransformedCanvasOffset(
    wp: Waypoint, 
    w: Float, 
    h: Float, 
    fieldWidthM: Double, 
    fieldHeightM: Double, 
    league: League, 
    zoomScale: Float, 
    panOffset: Offset,
    viewRotationDeg: Float = 0f
): Offset {
    val base = getCanvasOffsetBase(wp, w, h, fieldWidthM, fieldHeightM, league)
    // Apply zoom scale and pan offset
    var x = base.x * zoomScale + panOffset.x
    var y = base.y * zoomScale + panOffset.y
    // Apply view rotation around canvas center (matching the draw transform)
    if (viewRotationDeg != 0f) {
        val cx = w / 2f; val cy = h / 2f
        val rad = Math.toRadians(viewRotationDeg.toDouble())
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()
        val dx = x - cx; val dy = y - cy
        x = cx + dx * cosR - dy * sinR
        y = cy + dx * sinR + dy * cosR
    }
    return Offset(x, y)
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun getRobotCoordFromScreen(
    screenOffset: Offset, 
    w: Float, 
    h: Float, 
    fieldWidthM: Double, 
    fieldHeightM: Double, 
    league: League, 
    zoomScale: Float, 
    panOffset: Offset,
    viewRotationDeg: Float = 0f
): Waypoint {
    var sx = screenOffset.x; var sy = screenOffset.y
    // Reverse view rotation around canvas center
    if (viewRotationDeg != 0f) {
        val cx = w / 2f; val cy = h / 2f
        val rad = Math.toRadians(-viewRotationDeg.toDouble()) // negative to reverse
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()
        val dx = sx - cx; val dy = sy - cy
        sx = cx + dx * cosR - dy * sinR
        sy = cy + dx * sinR + dy * cosR
    }
    // Reverse pan & zoom
    val baseOffset = Offset(
        x = (sx - panOffset.x) / zoomScale,
        y = (sy - panOffset.y) / zoomScale
    )
    return getRobotCoordBase(baseOffset, w, h, fieldWidthM, fieldHeightM, league)
}

/**
 * Reverse-transforms screen pointer coordinates to base (untransformed) canvas coordinates.
 * This allows hit-testing using getCanvasOffsetBase positions directly.
 */
fun getBaseCanvasFromScreen(
    screenOffset: Offset,
    w: Float,
    h: Float,
    zoomScale: Float,
    panOffset: Offset,
    viewRotationDeg: Float = 0f
): Offset {
    var sx = screenOffset.x; var sy = screenOffset.y
    if (viewRotationDeg != 0f) {
        val cx = w / 2f; val cy = h / 2f
        val rad = Math.toRadians(-viewRotationDeg.toDouble())
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()
        val dx = sx - cx; val dy = sy - cy
        sx = cx + dx * cosR - dy * sinR
        sy = cy + dx * sinR + dy * cosR
    }
    return Offset(
        x = (sx - panOffset.x) / zoomScale,
        y = (sy - panOffset.y) / zoomScale
    )
}

/**
 * Converts a pixel-space drag delta directly to a field-coordinate delta.
 * Uses the `dragAmount` vector from Compose's `detectDragGestures` to avoid
 * absolute coordinate conversion issues (DPI scaling, rotate modifier, etc.).
 *
 * For FTC:
 *   canvasX = (-fieldY / fieldW + 0.5) * canvasW  ⇒  deltaFieldY = -deltaCanvasX / canvasW * fieldW
 *   canvasY = (-fieldX / fieldH + 0.5) * canvasH  ⇒  deltaFieldX = -deltaCanvasY / canvasH * fieldH
 */
fun getDragDeltaInFieldCoords(
    dragAmount: Offset,
    canvasW: Float,
    canvasH: Float,
    fieldW: Double,
    fieldH: Double,
    league: League,
    zoomScale: Float,
    viewRotationDeg: Float = 0f
): Waypoint {
    var dxPx = dragAmount.x
    var dyPx = dragAmount.y
    if (viewRotationDeg != 0f) {
        val rad = Math.toRadians(-viewRotationDeg.toDouble())
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()
        val rotatedX = dxPx * cosR - dyPx * sinR
        val rotatedY = dxPx * sinR + dyPx * cosR
        dxPx = rotatedX
        dyPx = rotatedY
    }
    
    return if (league == League.FTC) {
        val dx = -(dyPx / canvasH) * fieldH / zoomScale
        val dy = -(dxPx / canvasW) * fieldW / zoomScale
        Waypoint(dx, dy)
    } else {
        val dx = (dxPx / canvasW) * fieldW / zoomScale
        val dy = -(dyPx / canvasH) * fieldH / zoomScale
        Waypoint(dx, dy)
    }
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun cubicHermite(p0: Double, v0: Double, p1: Double, v1: Double, t: Double): Double {
    val t2 = t * t
    val t3 = t2 * t
    val h00 = 2 * t3 - 3 * t2 + 1
    val h10 = t3 - 2 * t2 + t
    val h01 = -2 * t3 + 3 * t2
    val h11 = t3 - t2
    return h00 * p0 + h10 * v0 + h01 * p1 + h11 * v1
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun getPositionOnSpline(pos: Double, waypoints: List<Waypoint>): Waypoint {
    if (waypoints.isEmpty()) return Waypoint(0.0, 0.0)
    if (waypoints.size == 1) return waypoints.first()
    val maxIdx = waypoints.size - 1
    val i = pos.toInt().coerceIn(0, maxIdx - 1)
    val t = (pos - i).coerceIn(0.0, 1.0)
    val p0 = waypoints[i]
    val p1 = waypoints[i + 1]
    val h0 = resolveHeading(waypoints, i)
    val h1 = resolveHeading(waypoints, i + 1)
    val v0x = kotlin.math.cos(h0) * p0.nextControlLength
    val v0y = kotlin.math.sin(h0) * p0.nextControlLength
    val v1x = kotlin.math.cos(h1) * p1.prevControlLength
    val v1y = kotlin.math.sin(h1) * p1.prevControlLength
    val px = cubicHermite(p0.x, v0x, p1.x, v1x, t)
    val py = cubicHermite(p0.y, v0y, p1.y, v1y, t)
    return Waypoint(px, py)
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun getClosestSplinePosition(
    mouseOffset: Offset,
    waypoints: List<Waypoint>,
    canvasW: Float,
    canvasH: Float,
    fieldW: Double,
    fieldH: Double,
    league: League
): Double {
    if (waypoints.size < 2) return 0.0
    var bestPos = 0.0
    var minDist = Float.MAX_VALUE
    val maxPos = (waypoints.size - 1).toDouble()
    
    // Sample the spline densely to find closest projection
    val steps = (waypoints.size - 1) * 40
    for (k in 0..steps) {
        val pos = maxPos * k / steps
        val wp = getPositionOnSpline(pos, waypoints)
        val offset = getCanvasOffsetBase(wp, canvasW, canvasH, fieldW, fieldH, league)
        val dx = mouseOffset.x - offset.x
        val dy = mouseOffset.y - offset.y
        val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (dist < minDist) {
            minDist = dist
            bestPos = pos
        }
    }
    return bestPos
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun DrawScope.drawCoordinateAxes(
    canvasW: Float,
    canvasH: Float,
    fieldW: Double,
    fieldH: Double,
    league: League,
    textMeasurer: TextMeasurer
) {
    val originOffset = getCanvasOffsetBase(Waypoint(0.0, 0.0), canvasW, canvasH, fieldW, fieldH, league)
    val xAxisOffset = getCanvasOffsetBase(Waypoint(0.8, 0.0), canvasW, canvasH, fieldW, fieldH, league)
    val yAxisOffset = getCanvasOffsetBase(Waypoint(0.0, 0.8), canvasW, canvasH, fieldW, fieldH, league)
    val strokeW = 3.dp.toPx()
    val arrowSize = 10.dp.toPx()

    // X Axis (Red)
    drawLine(color = AresRed, start = originOffset, end = xAxisOffset, strokeWidth = strokeW)
    // X Axis Arrow Head
    val xAngle = kotlin.math.atan2((xAxisOffset.y - originOffset.y).toDouble(), (xAxisOffset.x - originOffset.x).toDouble())
    val xArrowPath = Path().apply {
        moveTo(xAxisOffset.x, xAxisOffset.y)
        lineTo(
            xAxisOffset.x - arrowSize * kotlin.math.cos(xAngle - Math.PI / 6).toFloat(),
            xAxisOffset.y - arrowSize * kotlin.math.sin(xAngle - Math.PI / 6).toFloat()
        )
        lineTo(
            xAxisOffset.x - arrowSize * kotlin.math.cos(xAngle + Math.PI / 6).toFloat(),
            xAxisOffset.y - arrowSize * kotlin.math.sin(xAngle + Math.PI / 6).toFloat()
        )
        close()
    }
    drawPath(path = xArrowPath, color = AresRed)

    // Y Axis (Green)
    drawLine(color = AresGreen, start = originOffset, end = yAxisOffset, strokeWidth = strokeW)
    // Y Axis Arrow Head
    val yAngle = kotlin.math.atan2((yAxisOffset.y - originOffset.y).toDouble(), (yAxisOffset.x - originOffset.x).toDouble())
    val yArrowPath = Path().apply {
        moveTo(yAxisOffset.x, yAxisOffset.y)
        lineTo(
            yAxisOffset.x - arrowSize * kotlin.math.cos(yAngle - Math.PI / 6).toFloat(),
            yAxisOffset.y - arrowSize * kotlin.math.sin(yAngle - Math.PI / 6).toFloat()
        )
        lineTo(
            yAxisOffset.x - arrowSize * kotlin.math.cos(yAngle + Math.PI / 6).toFloat(),
            yAxisOffset.y - arrowSize * kotlin.math.sin(yAngle + Math.PI / 6).toFloat()
        )
        close()
    }
    drawPath(path = yArrowPath, color = AresGreen)

    // Axis Labels
    val textStyle = TextStyle(
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
    val textLayoutX = textMeasurer.measure("X", textStyle)
    val textLayoutY = textMeasurer.measure("Y", textStyle)

    drawText(
        textMeasurer = textMeasurer,
        text = "X",
        style = textStyle,
        topLeft = Offset(
            xAxisOffset.x + arrowSize * kotlin.math.cos(xAngle).toFloat(),
            xAxisOffset.y + arrowSize * kotlin.math.sin(xAngle).toFloat() - textLayoutX.size.height / 2f
        )
    )

    drawText(
        textMeasurer = textMeasurer,
        text = "Y",
        style = textStyle,
        topLeft = Offset(
            yAxisOffset.x + arrowSize * kotlin.math.cos(yAngle).toFloat() - textLayoutY.size.width / 2f,
            yAxisOffset.y + arrowSize * kotlin.math.sin(yAngle).toFloat()
        )
    )
}
