package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.areslib.math.wrapAngle
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.*
import com.ares.analytics.util.ProjectLayout
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.pathing.RobotDimensions
import com.areslib.math.coordinate.CoordinateTransformers
import com.ares.analytics.viewmodel.field.FieldDocumentMapper
import com.areslib.state.RobotFieldDocument
import java.io.File
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FieldCanvas(
    league: League,
    waypoints: List<Waypoint>,
    actualPath: List<Waypoint>,
    contextPath: List<Waypoint>? = null,
    contextWaypoints: List<Waypoint>? = null,
    onWaypointsChanged: (List<Waypoint>) -> Unit,
    projectPath: String? = null,
    showPathControls: Boolean = true,
    showObstacleControls: Boolean = true,
    fieldImage: ImageBitmap? = null,
    fieldImageConfig: FieldImageConfig? = null,
    obstacles: List<Obstacle>? = null,
    onObstaclesChanged: ((List<Obstacle>) -> Unit)? = null,
    gamePieces: List<GamePiece>? = null,
    onGamePiecesChanged: ((List<GamePiece>) -> Unit)? = null,
    aprilTags: List<AprilTagPlacement>? = null,
    onAprilTagsChanged: ((List<AprilTagPlacement>) -> Unit)? = null,
    fieldWaypoints: List<FieldWaypoint>? = null,
    onFieldWaypointsChanged: ((List<FieldWaypoint>) -> Unit)? = null,
    eventMarkers: List<PathPlannerEventMarker> = emptyList(),
    onEventMarkersChanged: ((List<PathPlannerEventMarker>) -> Unit)? = null,
    rotationTargets: List<RotationTarget> = emptyList(),
    onRotationTargetsChanged: ((List<RotationTarget>) -> Unit)? = null,
    idealStartingState: IdealStartingState? = null,
    onStartingStateChanged: ((IdealStartingState) -> Unit)? = null,
    goalEndState: GoalEndState? = null,
    onGoalEndStateChanged: ((GoalEndState) -> Unit)? = null,
    constraintZones: List<ConstraintsZone> = emptyList(),
    pointTowardsZones: List<PointTowardsZone> = emptyList(),
    globalConstraints: PathConstraints = PathConstraints(),
    estimatedPose: Waypoint? = null,
    playbackPose: Waypoint? = null,
    visionPoses: List<Waypoint> = emptyList(),
    odomPose: Waypoint? = null,
    showTruePose: Boolean = true,
    showEkfPose: Boolean = true,
    showOdomPose: Boolean = true,
    showVisionPoses: Boolean = true,
    onItemSelected: ((String?, String?) -> Unit)? = null,
    onItemDoubleTapped: ((String, String) -> Unit)? = null,
    initialViewRotation: Float = 0f,
    onViewRotationChanged: ((Float) -> Unit)? = null,
    showToolbar: Boolean = true,
    indicatorLightPosition: Double = -1.0,
    autoGoalMode: Boolean = false,
    robotDimensions: RobotDimensions = RobotDimensions.defaultFor(league),
    modifier: Modifier = Modifier
) {
    var localFieldImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var localFieldImageConfig by remember { mutableStateOf(FieldImageConfig()) }
    var localFieldConfigLoaded by remember { mutableStateOf(false) }
    var isDraggingHeading by remember { mutableStateOf(false) }
    var isDraggingPrevHeading by remember { mutableStateOf(false) }
    var isDraggingRotation by remember { mutableStateOf(false) }
    var isDraggingFieldWaypoint by remember { mutableStateOf(false) }
    var isDraggingFieldWaypointHeading by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val pathCache = remember { PathCacheHolder() }
    var contextMenuExpanded by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var contextTargetType by remember { mutableStateOf<String?>(null) }
    var contextTargetId by remember { mutableStateOf<String?>(null) }
    var contextTargetIndex by remember { mutableStateOf<Int>(-1) }
    val density = LocalDensity.current
    val splinePoints by remember(waypoints, autoGoalMode) {
        derivedStateOf {
            if (autoGoalMode || waypoints.size < 2) emptyList<Waypoint>() else {
                val list = ArrayList<Waypoint>((waypoints.size - 1) * 30 + 1)
                val density = 30
                for (i in 0 until waypoints.size - 1) {
                    val p0 = waypoints[i]
                    val p1 = waypoints[i + 1]
                    val h0 = resolveHeading(waypoints, i)
                    val h1 = resolveHeading(waypoints, i + 1)
                    val v0x = cos(h0) * p0.nextControlLength
                    val v0y = sin(h0) * p0.nextControlLength
                    val v1x = cos(h1) * p1.prevControlLength
                    val v1y = sin(h1) * p1.prevControlLength

                    for (j in 1..density) {
                        val t = j.toDouble() / density
                        list.add(Waypoint(cubicHermite(p0.x, v0x, p1.x, v1x, t), cubicHermite(p0.y, v0y, p1.y, v1y, t)))
                    }
                }
                list
            }
        }
    }
    val eventMarkerPoints = remember(eventMarkers, waypoints) {
        if (waypoints.size < 2) emptyList()
        else eventMarkers.map { getPositionOnSpline(it.waypointRelativePos, waypoints) }
    }
    val combinedRotationTargets = remember(rotationTargets, idealStartingState, goalEndState, waypoints) {
        val list = mutableListOf<RotationTarget>()
        val startingRot = idealStartingState?.rotation ?: 0.0
        if (rotationTargets.none { kotlin.math.abs(it.waypointRelativePos) < 1e-3 }) {
            list.add(RotationTarget(waypointRelativePos = 0.0, rotationDegrees = startingRot))
        }
        list.addAll(rotationTargets)
        val endRot = goalEndState?.rotation ?: 0.0
        val lastWaypointIdx = (waypoints.size - 1).toDouble()
        if (lastWaypointIdx >= 0.0 && rotationTargets.none { kotlin.math.abs(it.waypointRelativePos - lastWaypointIdx) < 1e-3 }) {
            list.add(RotationTarget(waypointRelativePos = lastWaypointIdx, rotationDegrees = endRot))
        }
        list
    }
    val combinedRotationTargetPoints = remember(combinedRotationTargets, waypoints) {
        if (waypoints.size < 2) emptyList()
        else combinedRotationTargets.map { getPositionOnSpline(it.waypointRelativePos, waypoints) }
    }
    val constraintZoneSplines = remember(constraintZones, waypoints) {
        if (waypoints.size < 2) emptyList()
        else constraintZones.map { zone ->
            val minPos = zone.minWaypointRelativePos
            val maxPos = zone.maxWaypointRelativePos
            val steps = ((maxPos - minPos) * 20).toInt().coerceIn(2, 100)
            val pathPoints = ArrayList<Waypoint>(steps + 1)
            for (step in 0..steps) {
                pathPoints.add(getPositionOnSpline(minPos + (maxPos - minPos) * (step.toDouble() / steps), waypoints))
            }
            pathPoints
        }
    }
    val pointTowardsZoneRenderData = remember(pointTowardsZones, waypoints) {
        if (waypoints.size < 2) emptyList()
        else pointTowardsZones.map { zone ->
            val numLines = 5
            val spPoints = ArrayList<Waypoint>(numLines)
            for (k in 0 until numLines) {
                spPoints.add(getPositionOnSpline(zone.minWaypointRelativePos + (zone.maxWaypointRelativePos - zone.minWaypointRelativePos) * (if (numLines > 1) k.toDouble() / (numLines - 1) else 0.5), waypoints))
            }
            PointTowardsZoneRenderData(Waypoint(zone.fieldPosition.x, zone.fieldPosition.y), spPoints)
        }
    }
    val activeImage = fieldImage ?: localFieldImage
    val activeConfig = fieldImageConfig ?: localFieldImageConfig
    val useConfiguredFieldSize = fieldImageConfig != null || localFieldConfigLoaded
    val fieldWidthM = if (useConfiguredFieldSize && activeConfig.widthMeters > 0.0) {
        activeConfig.widthMeters
    } else if (league == League.FTC) {
        CoordinateTransformers.FTC_FIELD_SIZE
    } else {
        CoordinateTransformers.FRC_FIELD_LENGTH
    }
    val fieldHeightM = if (useConfiguredFieldSize && activeConfig.heightMeters > 0.0) {
        activeConfig.heightMeters
    } else if (league == League.FTC) {
        CoordinateTransformers.FTC_FIELD_SIZE
    } else {
        CoordinateTransformers.FRC_FIELD_WIDTH
    }
    var editorMode by remember { mutableStateOf(EditorMode.SELECT) }
    var selectedWaypointIndex by remember { mutableStateOf(-1) }
    var selectedEventMarkerIndex by remember { mutableStateOf(-1) }
    var selectedObstacleId by remember { mutableStateOf<String?>(null) }
    var selectedAprilTagId by remember { mutableStateOf<String?>(null) }
    var selectedGamePieceId by remember { mutableStateOf<String?>(null) }
    var selectedFieldWaypointId by remember { mutableStateOf<String?>(null) }
    var activeGamePieceType by remember { mutableStateOf(if (league == League.FTC) "Sample (Yellow)" else "Note") }
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showHeatmap by remember { mutableStateOf(false) }
    var showCostmap by remember { mutableStateOf(false) }
    val windowInfo = LocalWindowInfo.current
    val isShiftPressed = windowInfo.keyboardModifiers.isShiftPressed
    var viewRotation by remember(initialViewRotation) { mutableStateOf(initialViewRotation) }
    val localObstacles = remember { mutableStateListOf<Obstacle>() }
    val localGamePieces = remember { mutableStateListOf<GamePiece>() }
    val localAprilTags = remember { mutableStateListOf<AprilTagPlacement>() }
    val localFieldWaypoints = remember { mutableStateListOf<FieldWaypoint>() }
    val activeObstacles = obstacles ?: localObstacles
    val activeGamePieces = gamePieces ?: localGamePieces
    val activeAprilTags = aprilTags ?: localAprilTags
    val activeFieldWaypoints = fieldWaypoints ?: localFieldWaypoints
    val updateObstacles: (List<Obstacle>) -> Unit = { newObstacles ->
        onObstaclesChanged?.invoke(newObstacles) ?: run {
            localObstacles.clear()
            localObstacles.addAll(newObstacles)
        }
    }
    val updateGamePieces: (List<GamePiece>) -> Unit = { newPieces ->
        onGamePiecesChanged?.invoke(newPieces) ?: run {
            localGamePieces.clear()
            localGamePieces.addAll(newPieces)
        }
    }
    val updateAprilTags: (List<AprilTagPlacement>) -> Unit = { newTags ->
        onAprilTagsChanged?.invoke(newTags) ?: run {
            localAprilTags.clear()
            localAprilTags.addAll(newTags)
        }
    }
    val updateFieldWaypoints: (List<FieldWaypoint>) -> Unit = { newWps ->
        onFieldWaypointsChanged?.invoke(newWps) ?: run {
            localFieldWaypoints.clear()
            localFieldWaypoints.addAll(newWps)
        }
    }
    val currentPolygonPoints = remember { mutableStateListOf<PathPoint>() }
    val currentWaypoints by rememberUpdatedState(waypoints)
    val currentActiveObstacles by rememberUpdatedState(activeObstacles)
    val currentActiveGamePieces by rememberUpdatedState(activeGamePieces)
    val currentActiveAprilTags by rememberUpdatedState(activeAprilTags)
    val currentActiveFieldWaypoints by rememberUpdatedState(activeFieldWaypoints)
    val currentEventMarkers by rememberUpdatedState(eventMarkers)
    val currentRotationTargets by rememberUpdatedState(rotationTargets)
    val currentCombinedRotationTargets by rememberUpdatedState(combinedRotationTargets)
    val currentIdealStartingState by rememberUpdatedState(idealStartingState)
    val currentGoalEndState by rememberUpdatedState(goalEndState)

    LaunchedEffect(projectPath) {
        try {
            if (!projectPath.isNullOrEmpty()) {
                val documentFile = ProjectLayout.fieldDefinitionFile(projectPath, league)
                if (documentFile.isFile) {
                    val document = RobotFieldDocument.decode(documentFile.readText())
                    if (obstacles == null) updateObstacles(FieldDocumentMapper.obstacles(document))
                    if (gamePieces == null) updateGamePieces(FieldDocumentMapper.gamePieces(document))
                    if (aprilTags == null) updateAprilTags(FieldDocumentMapper.aprilTags(document))
                    if (fieldWaypoints == null) updateFieldWaypoints(FieldDocumentMapper.fieldWaypoints(document))
                    if (fieldImageConfig == null) {
                        localFieldImageConfig = FieldDocumentMapper.image(document)
                        localFieldConfigLoaded = true
                    }
                }

                val assetsDirectory = ProjectLayout.assetsDirectory(projectPath, league)
                val imgFile = File(assetsDirectory, "field_image.png")
                if (fieldImage == null) {
                    localFieldImage = if (imgFile.exists()) org.jetbrains.skia.Image.makeFromEncoded(imgFile.readBytes()).toComposeImageBitmap() else null
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showToolbar) {
            FieldCanvasToolbar(
                showPathControls = showPathControls,
                showObstacleControls = showObstacleControls,
                league = league,
                editorMode = editorMode,
                onEditorModeChanged = { editorMode = it },
                activeGamePieceType = activeGamePieceType,
                onActiveGamePieceTypeChanged = { activeGamePieceType = it },
                currentPolygonPoints = currentPolygonPoints,
                onCurrentPolygonPointsCleared = { currentPolygonPoints.clear() },
                activeObstacles = activeObstacles,
                updateObstacles = updateObstacles,
                zoomScale = zoomScale,
                onZoomScaleChanged = { zoomScale = it },
                onResetZoomPan = { zoomScale = 1f; panOffset = Offset.Zero },
                showHeatmap = showHeatmap,
                onShowHeatmapChanged = { showHeatmap = it },
                showCostmap = showCostmap,
                onShowCostmapChanged = { showCostmap = it },
                viewRotation = viewRotation,
                onViewRotationChanged = {
                    viewRotation = it
                    onViewRotationChanged?.invoke(it)
                }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.aspectRatio(fieldWidthM.toFloat() / fieldHeightM.toFloat())) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AresSurface)
                    .pointerInput(autoGoalMode) {
                        // Accumulated pixel-space drag for the entire gesture
                        var accumulatedDragPx = Offset.Zero
                        // Initial positions captured at press for absolute positioning
                        var dragInitialPos = Waypoint(0.0, 0.0)
                        var dragInitialVertices: List<PathPoint> = emptyList()
                        awaitEachGesture {
                            // 1. Capture the TRUE press position (before touch slop)
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pressOffset = down.position
                            var hasDragged = false
                            accumulatedDragPx = Offset.Zero

                            // 2. Perform hit-testing at the ACTUAL press position
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            when (editorMode) {
                                 EditorMode.SELECT -> {
                                     var hitIdx = -1; var hitHeading = false; var hitPrevHeading = false; var hitRotation = false

                                     // Convert press position to base canvas space (before zoom/pan/rotate transform).
                                     // This matches the coordinate system used by PathRenderer.drawWaypoints.
                                     val basePress = getBaseCanvasFromScreen(pressOffset, w, h, zoomScale, panOffset, viewRotation)

                                     // Helper: compute rotation handle position in base canvas space (matches PathRenderer draw code)
                                     fun rotHandleBase(wpBaseOffset: Offset, wp: Waypoint): Offset {
                                         val rotAngleRad = Math.toRadians(-(wp.rotationDeg ?: 0.0) - 90.0)
                                         val rotHandleLenPx = 30.dp.toPx()
                                         return Offset(
                                             wpBaseOffset.x + rotHandleLenPx * cos(rotAngleRad).toFloat(),
                                             wpBaseOffset.y + rotHandleLenPx * sin(rotAngleRad).toFloat()
                                         )
                                     }

                                     // Scale-aware hit threshold: fixed screen-size radius divided by zoom
                                     // so clickability doesn't shrink when zoomed out
                                     val hitRadiusPx = 15.dp.toPx() / zoomScale
                                     val rotHitRadiusPx = 18.dp.toPx() / zoomScale

                                     // 1. Prioritize handles of the ALREADY selected waypoint (if any)
                                     if (!autoGoalMode && selectedWaypointIndex in currentWaypoints.indices) {
                                         val wp = currentWaypoints[selectedWaypointIndex]
                                         val wpBase = getCanvasOffsetBase(wp, w, h, fieldWidthM, fieldHeightM, league)

                                         // Heading handle (tangent arrowhead)
                                         val selHeading = resolveHeading(currentWaypoints, selectedWaypointIndex)
                                         val headingWp = Waypoint(wp.x + wp.nextControlLength * cos(selHeading), wp.y + wp.nextControlLength * sin(selHeading))
                                         val headingBase = getCanvasOffsetBase(headingWp, w, h, fieldWidthM, fieldHeightM, league)
                                         if (sqrt((basePress.x - headingBase.x).pow(2) + (basePress.y - headingBase.y).pow(2)) < hitRadiusPx) {
                                             hitIdx = selectedWaypointIndex; hitHeading = true
                                         }

                                         // Prev Heading handle
                                         val prevHeadingWp = Waypoint(wp.x + wp.prevControlLength * cos(selHeading + Math.PI), wp.y + wp.prevControlLength * sin(selHeading + Math.PI))
                                         val prevHeadingBase = getCanvasOffsetBase(prevHeadingWp, w, h, fieldWidthM, fieldHeightM, league)
                                         if (hitIdx == -1 && sqrt((basePress.x - prevHeadingBase.x).pow(2) + (basePress.y - prevHeadingBase.y).pow(2)) < hitRadiusPx) {
                                             hitIdx = selectedWaypointIndex; hitPrevHeading = true
                                         }

                                         // Rotation handle (green diamond)
                                         if (hitIdx == -1) {
                                             val rotBase = rotHandleBase(wpBase, wp)
                                             if (sqrt((basePress.x - rotBase.x).pow(2) + (basePress.y - rotBase.y).pow(2)) < rotHitRadiusPx) {
                                                 hitIdx = selectedWaypointIndex; hitRotation = true
                                             }
                                         }
                                     }

                                     // 2. Check all waypoint center dots
                                     if (hitIdx == -1) {
                                         for (i in currentWaypoints.indices) {
                                             val wpBase = getCanvasOffsetBase(currentWaypoints[i], w, h, fieldWidthM, fieldHeightM, league)
                                             if (sqrt((basePress.x - wpBase.x).pow(2) + (basePress.y - wpBase.y).pow(2)) < hitRadiusPx) {
                                                 hitIdx = i; break
                                             }
                                         }
                                     }

                                     // 3. Check other waypoints' handles (heading + rotation)
                                     if (hitIdx == -1 && !autoGoalMode) {
                                         for (i in currentWaypoints.indices) {
                                             if (i == selectedWaypointIndex) continue
                                             val wp = currentWaypoints[i]
                                             val wpBase = getCanvasOffsetBase(wp, w, h, fieldWidthM, fieldHeightM, league)
                                             val hd = resolveHeading(currentWaypoints, i)
                                             val headingWp = Waypoint(wp.x + wp.nextControlLength * cos(hd), wp.y + wp.nextControlLength * sin(hd))
                                             val headingBase = getCanvasOffsetBase(headingWp, w, h, fieldWidthM, fieldHeightM, league)
                                             if (sqrt((basePress.x - headingBase.x).pow(2) + (basePress.y - headingBase.y).pow(2)) < hitRadiusPx) {
                                                 hitIdx = i; hitHeading = true; break
                                             }
                                             val prevHeadingWp = Waypoint(wp.x + wp.prevControlLength * cos(hd + Math.PI), wp.y + wp.prevControlLength * sin(hd + Math.PI))
                                             val prevHeadingBase = getCanvasOffsetBase(prevHeadingWp, w, h, fieldWidthM, fieldHeightM, league)
                                             if (sqrt((basePress.x - prevHeadingBase.x).pow(2) + (basePress.y - prevHeadingBase.y).pow(2)) < hitRadiusPx) {
                                                 hitIdx = i; hitPrevHeading = true; break
                                             }
                                             val rotBase = rotHandleBase(wpBase, wp)
                                             if (sqrt((basePress.x - rotBase.x).pow(2) + (basePress.y - rotBase.y).pow(2)) < rotHitRadiusPx) {
                                                 hitIdx = i; hitRotation = true; break
                                             }
                                         }
                                     }
                                     var hitEventIdx = -1
                                     if (hitIdx == -1) {
                                         hitEventIdx = currentEventMarkers.indexOfFirst {
                                             val markerBase = getCanvasOffsetBase(getPositionOnSpline(it.waypointRelativePos, currentWaypoints), w, h, fieldWidthM, fieldHeightM, league)
                                             sqrt((basePress.x - markerBase.x).pow(2) + (basePress.y - markerBase.y).pow(2)) < hitRadiusPx
                                         }
                                     }
                                    var hitFieldWpId: String? = null
                                    var hitFieldWpHeading = false
                                    var hitFieldWpCenter = false
                                    if (hitIdx == -1 && hitEventIdx == -1) {
                                        for (wp in currentActiveFieldWaypoints) {
                                            val wpOffset = getTransformedCanvasOffset(Waypoint(wp.x, wp.y), w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset, viewRotation)
                                            val angleRad = Math.toRadians(-wp.headingDegrees - 90.0)
                                            val pointerLen = 22.dp.toPx()
                                            val handleOffset = Offset(
                                                (wpOffset.x + pointerLen * cos(angleRad)).toFloat(),
                                                (wpOffset.y + pointerLen * sin(angleRad)).toFloat()
                                            )
                                            if (sqrt((pressOffset.x - handleOffset.x).pow(2) + (pressOffset.y - handleOffset.y).pow(2)) < 15.dp.toPx()) {
                                                hitFieldWpId = wp.id; hitFieldWpHeading = true; break
                                            }
                                            if (sqrt((pressOffset.x - wpOffset.x).pow(2) + (pressOffset.y - wpOffset.y).pow(2)) < 15.dp.toPx()) {
                                                hitFieldWpId = wp.id; hitFieldWpCenter = true; break
                                            }
                                        }
                                    }

                                    selectedWaypointIndex = hitIdx; selectedEventMarkerIndex = hitEventIdx
                                    isDraggingHeading = hitIdx != -1 && hitHeading
                                    isDraggingPrevHeading = hitIdx != -1 && hitPrevHeading
                                    isDraggingRotation = hitIdx != -1 && hitRotation

                                    selectedFieldWaypointId = hitFieldWpId
                                    isDraggingFieldWaypoint = hitFieldWpId != null && hitFieldWpCenter
                                    isDraggingFieldWaypointHeading = hitFieldWpId != null && hitFieldWpHeading

                                     when {
                                         hitFieldWpId != null -> {
                                             selectedObstacleId = null; selectedAprilTagId = null; selectedGamePieceId = null
                                             onItemSelected?.invoke(hitFieldWpId, "FieldWaypoint")
                                             if (hitFieldWpCenter) {
                                                 currentActiveFieldWaypoints.find { it.id == hitFieldWpId }
                                                     ?.let { dragInitialPos = Waypoint(it.x, it.y) }
                                             }
                                         }
                                         hitIdx != -1 || hitEventIdx != -1 -> {
                                             selectedObstacleId = null; selectedAprilTagId = null; selectedGamePieceId = null; selectedFieldWaypointId = null
                                         }
                                         else -> {
                                             val clickCoord = getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                             val hitObs = currentActiveObstacles.minByOrNull { obs ->
                                                 when (obs) {
                                                     is Obstacle.Circle -> sqrt((clickCoord.x - obs.centerX).pow(2) + (clickCoord.y - obs.centerY).pow(2)) - obs.radius
                                                     is Obstacle.Rectangle -> {
                                                         val dx = clickCoord.x - obs.centerX; val dy = clickCoord.y - obs.centerY
                                                         sqrt(dx * dx + dy * dy)
                                                     }
                                                     is Obstacle.Polygon -> obs.vertices.minOf { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) }
                                                 }
                                             }?.takeIf { obs ->
                                                 when (obs) {
                                                     is Obstacle.Circle -> sqrt((clickCoord.x - obs.centerX).pow(2) + (clickCoord.y - obs.centerY).pow(2)) <= obs.radius
                                                     is Obstacle.Rectangle -> {
                                                         val dx = clickCoord.x - obs.centerX; val dy = clickCoord.y - obs.centerY
                                                         val rad = Math.toRadians(-obs.rotation)
                                                         kotlin.math.abs(dx * cos(rad) - dy * sin(rad)) <= obs.width / 2.0 && kotlin.math.abs(dx * sin(rad) + dy * cos(rad)) <= obs.height / 2.0
                                                     }
                                                     is Obstacle.Polygon -> obs.vertices.any { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.3 }
                                                 }
                                             }
                                             selectedObstacleId = hitObs?.id
                                             if (selectedObstacleId != null) {
                                                 onItemSelected?.invoke(selectedObstacleId, "Obstacle")
                                                 when (hitObs) {
                                                     is Obstacle.Circle -> dragInitialPos = Waypoint(hitObs.centerX, hitObs.centerY)
                                                     is Obstacle.Rectangle -> dragInitialPos = Waypoint(hitObs.centerX, hitObs.centerY)
                                                     is Obstacle.Polygon -> dragInitialVertices = hitObs.vertices.toList()
                                                     else -> {}
                                                 }
                                             } else {
                                                 val hitAt = currentActiveAprilTags.minByOrNull { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) }?.takeIf { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.3 }
                                                 selectedAprilTagId = hitAt?.id
                                                 hitAt?.let { aprilTag ->
                                                     onItemSelected?.invoke(aprilTag.id, "AprilTag")
                                                     dragInitialPos = Waypoint(aprilTag.x, aprilTag.y)
                                                 } ?: run {
                                                     val hitGp = currentActiveGamePieces.minByOrNull { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) }?.takeIf { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.2 }
                                                     selectedGamePieceId = hitGp?.id
                                                     hitGp?.let { gamePiece ->
                                                         onItemSelected?.invoke(gamePiece.id, "GamePiece")
                                                         dragInitialPos = Waypoint(gamePiece.x, gamePiece.y)
                                                     } ?: onItemSelected?.invoke(null, null)
                                                 }
                                             }
                                         }
                                     }
                                }
                                else -> { /* Placement handled on release when !hasDragged */ }
                            }

                            // 3. Wait for touch slop then track drag
                            val slopChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                            }

                            if (slopChange != null) {
                                // Include the slop displacement so drag starts without a gap
                                accumulatedDragPx = slopChange.position - down.position
                                // Drag detected — track movement
                                drag(slopChange.id) { change ->
                                    hasDragged = true
                                    val dragAmount = change.positionChange()
                                    accumulatedDragPx += dragAmount
                                    change.consume()
                                    fun snap(v: Double) = if (isShiftPressed) kotlin.math.round(v * 10.0) / 10.0 else v
                                    val totalDelta = getDragDeltaInFieldCoords(accumulatedDragPx, w, h, fieldWidthM, fieldHeightM, league, zoomScale)
                                     when {
                                         selectedEventMarkerIndex != -1 && onEventMarkersChanged != null -> {
                                             val bestPos = getClosestSplinePosition(change.position, currentWaypoints, w, h, fieldWidthM, fieldHeightM, league)
                                             onEventMarkersChanged(currentEventMarkers.toMutableList().apply { set(selectedEventMarkerIndex, this[selectedEventMarkerIndex].copy(waypointRelativePos = bestPos)) })
                                         }
                                         selectedWaypointIndex != -1 -> {
                                             when {
                                                 isDraggingHeading -> {
                                                     val wp = currentWaypoints[selectedWaypointIndex]
                                                     val posMeters = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                                     val dx = posMeters.x - wp.x
                                                     val dy = posMeters.y - wp.y
                                                     val angle = kotlin.math.atan2(dy, dx)
                                                     val mag = kotlin.math.sqrt(dx * dx + dy * dy)
                                                     onWaypointsChanged(currentWaypoints.toMutableList().apply { set(selectedWaypointIndex, wp.copy(headingRad = angle, nextControlLength = if (isShiftPressed) snap(mag) else mag, prevControlLength = if (isShiftPressed) snap(mag) else mag)) })
                                                 }
                                                 isDraggingPrevHeading -> {
                                                     val wp = currentWaypoints[selectedWaypointIndex]
                                                     val posMeters = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                                     val dx = posMeters.x - wp.x
                                                     val dy = posMeters.y - wp.y
                                                     val angle = kotlin.math.atan2(dy, dx) - Math.PI
                                                     val normalizedAngle = wrapAngle(angle)
                                                     val mag = kotlin.math.sqrt(dx * dx + dy * dy)
                                                     onWaypointsChanged(currentWaypoints.toMutableList().apply { set(selectedWaypointIndex, wp.copy(headingRad = normalizedAngle, nextControlLength = if (isShiftPressed) snap(mag) else mag, prevControlLength = if (isShiftPressed) snap(mag) else mag)) })
                                                 }
                                                 isDraggingRotation -> {
                                                     val wp = currentWaypoints[selectedWaypointIndex]
                                                     val posMeters = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                                     val angle = kotlin.math.atan2(posMeters.y - wp.y, posMeters.x - wp.x)
                                                     val degrees = Math.toDegrees(angle)
                                                     val snappedDeg = if (isShiftPressed) snap(degrees).toDouble() else degrees
                                                     // Update waypoint's own rotation directly
                                                     onWaypointsChanged(currentWaypoints.toMutableList().apply {
                                                         set(selectedWaypointIndex, wp.copy(rotationDeg = snappedDeg))
                                                     })
                                                 }
                                                 else -> {
                                                     val newPos = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                                     val existingWp = currentWaypoints[selectedWaypointIndex]
                                                     onWaypointsChanged(currentWaypoints.toMutableList().apply { set(selectedWaypointIndex, existingWp.copy(x = snap(newPos.x), y = snap(newPos.y))) })
                                                 }
                                             }
                                         }
                                         selectedObstacleId != null && showObstacleControls -> {
                                             val targetObs = currentActiveObstacles.find { it.id == selectedObstacleId }
                                             if (targetObs != null && !targetObs.locked) {
                                                 updateObstacles(currentActiveObstacles.map { obs ->
                                                     if (obs.id == selectedObstacleId) {
                                                         when (obs) {
                                                             is Obstacle.Circle -> obs.copy(centerX = snap(dragInitialPos.x + totalDelta.x), centerY = snap(dragInitialPos.y + totalDelta.y))
                                                             is Obstacle.Rectangle -> obs.copy(centerX = snap(dragInitialPos.x + totalDelta.x), centerY = snap(dragInitialPos.y + totalDelta.y))
                                                             is Obstacle.Polygon -> obs.copy(vertices = dragInitialVertices.mapIndexed { idx, v -> PathPoint(snap(v.x + totalDelta.x), snap(v.y + totalDelta.y)) })
                                                         }
                                                     } else obs
                                                 })
                                             }
                                         }
                                         selectedAprilTagId != null && showObstacleControls -> {
                                             val targetAt = currentActiveAprilTags.find { it.id == selectedAprilTagId }
                                             if (targetAt != null && !targetAt.locked) {
                                                 updateAprilTags(currentActiveAprilTags.map { at -> if (at.id == selectedAprilTagId) at.copy(x = snap(dragInitialPos.x + totalDelta.x), y = snap(dragInitialPos.y + totalDelta.y)) else at })
                                             }
                                         }
                                         selectedGamePieceId != null && showObstacleControls -> {
                                             val targetGp = currentActiveGamePieces.find { it.id == selectedGamePieceId }
                                             if (targetGp != null && !targetGp.locked) {
                                                 updateGamePieces(currentActiveGamePieces.map { gp -> if (gp.id == selectedGamePieceId) gp.copy(x = snap(dragInitialPos.x + totalDelta.x), y = snap(dragInitialPos.y + totalDelta.y)) else gp })
                                             }
                                         }
                                         selectedFieldWaypointId != null && showObstacleControls -> {
                                             val targetWp = currentActiveFieldWaypoints.find { it.id == selectedFieldWaypointId }
                                             if (targetWp != null && !targetWp.locked) {
                                                 when {
                                                     isDraggingFieldWaypointHeading -> {
                                                         val posMeters = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                                         val angle = kotlin.math.atan2(posMeters.y - targetWp.y, posMeters.x - targetWp.x)
                                                         val degrees = Math.toDegrees(angle)
                                                         val targetHeading = -degrees - 90.0
                                                         val normalizedHeading = ((targetHeading + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                                                         updateFieldWaypoints(currentActiveFieldWaypoints.map { wp ->
                                                             if (wp.id == selectedFieldWaypointId) wp.copy(headingDegrees = if (isShiftPressed) snap(normalizedHeading) else normalizedHeading) else wp
                                                         })
                                                     }
                                                     isDraggingFieldWaypoint -> {
                                                         updateFieldWaypoints(currentActiveFieldWaypoints.map { wp ->
                                                             if (wp.id == selectedFieldWaypointId) wp.copy(x = snap(dragInitialPos.x + totalDelta.x), y = snap(dragInitialPos.y + totalDelta.y)) else wp
                                                         })
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                }
                            }

                            // 4. Gesture ended (pointer up or drag ended) — handle click-to-place
                            if (!hasDragged) {
                                when (editorMode) {
                                    EditorMode.ADD_WAYPOINT -> {
                                        onWaypointsChanged(currentWaypoints + getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset))
                                    }
                                    EditorMode.DRAW_POLYGON -> {
                                        val newWp = getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        currentPolygonPoints.add(PathPoint(newWp.x, newWp.y))
                                    }
                                    EditorMode.DRAW_CIRCLE -> {
                                        val newWp = getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateObstacles(currentActiveObstacles + Obstacle.Circle("circle_${System.currentTimeMillis()}", "Circle Obstacle ${currentActiveObstacles.size + 1}", newWp.x, newWp.y, 0.25))
                                    }
                                    EditorMode.DRAW_RECTANGLE -> {
                                        val newWp = getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateObstacles(currentActiveObstacles + Obstacle.Rectangle("rect_${System.currentTimeMillis()}", "Rectangle Obstacle ${currentActiveObstacles.size + 1}", newWp.x, newWp.y, 0.5, 0.5, 0.0))
                                    }
                                    EditorMode.PLACE_GAME_PIECE -> {
                                        val newWp = getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        val typeLabel = if (league == League.FTC) when (activeGamePieceType) { "Sample (Yellow)" -> "Yellow Sample"; "Sample (Red)" -> "Red Sample"; "Sample (Blue)" -> "Blue Sample"; "Decode (Ball)" -> "Decode Ball"; else -> "Specimen" } else "Note"
                                        updateGamePieces(currentActiveGamePieces + GamePiece("piece_${System.currentTimeMillis()}", "$typeLabel ${currentActiveGamePieces.size + 1}", newWp.x, newWp.y, activeGamePieceType))
                                    }
                                    EditorMode.PLACE_APRILTAG -> {
                                        val newWp = getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateAprilTags(currentActiveAprilTags + AprilTagPlacement("apriltag_${System.currentTimeMillis()}", 11 + currentActiveAprilTags.size, newWp.x, newWp.y, 0.5, 0.0))
                                    }
                                    EditorMode.PLACE_FIELD_WAYPOINT -> {
                                        val newWp = getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateFieldWaypoints(currentActiveFieldWaypoints + FieldWaypoint("fieldwp_${System.currentTimeMillis()}", "Waypoint ${currentActiveFieldWaypoints.size + 1}", newWp.x, newWp.y, 0.0))
                                    }
                                    EditorMode.ERASER -> {
                                        val hitIdx = currentWaypoints.indexOfFirst { sqrt((pressOffset.x - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset, viewRotation).x).pow(2) + (pressOffset.y - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset, viewRotation).y).pow(2)) < 25f }
                                        if (hitIdx != -1) onWaypointsChanged(currentWaypoints.toMutableList().apply { removeAt(hitIdx) })
                                        else {
                                            val robotWp = getRobotCoordFromScreen(pressOffset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                            val hitGp = currentActiveGamePieces.find { sqrt((robotWp.x - it.x).pow(2) + (robotWp.y - it.y).pow(2)) < 0.3 }
                                            if (hitGp != null) updateGamePieces(currentActiveGamePieces - hitGp)
                                            else {
                                                val hitObs = currentActiveObstacles.find { obs ->
                                                    when (obs) {
                                                        is Obstacle.Circle -> sqrt((robotWp.x - obs.centerX).pow(2) + (robotWp.y - obs.centerY).pow(2)) - obs.radius < 0.5
                                                        is Obstacle.Rectangle -> sqrt((robotWp.x - obs.centerX).pow(2) + (robotWp.y - obs.centerY).pow(2)) - maxOf(obs.width, obs.height)/2.0 < 0.5
                                                        is Obstacle.Polygon -> obs.vertices.any { sqrt((robotWp.x - it.x).pow(2) + (robotWp.y - it.y).pow(2)) < 0.5 }
                                                    }
                                                }
                                                if (hitObs != null) updateObstacles(currentActiveObstacles - hitObs)
                                                else {
                                                    val hitAt = currentActiveAprilTags.find { sqrt((robotWp.x - it.x).pow(2) + (robotWp.y - it.y).pow(2)) < 0.3 }
                                                    if (hitAt != null) updateAprilTags(currentActiveAprilTags - hitAt)
                                                    else {
                                                        val hitFwp = currentActiveFieldWaypoints.find { sqrt((robotWp.x - it.x).pow(2) + (robotWp.y - it.y).pow(2)) < 0.3 }
                                                        if (hitFwp != null) updateFieldWaypoints(currentActiveFieldWaypoints - hitFwp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                            // Reset selection state
                            selectedWaypointIndex = -1; selectedEventMarkerIndex = -1
                            selectedObstacleId = null; selectedAprilTagId = null; selectedGamePieceId = null
                            hasDragged = false
                            accumulatedDragPx = Offset.Zero
                            isDraggingHeading = false
                            isDraggingPrevHeading = false
                            isDraggingRotation = false
                            isDraggingFieldWaypoint = false
                            isDraggingFieldWaypointHeading = false
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val w = size.width.toFloat(); val h = size.height.toFloat()
                                if (editorMode == EditorMode.SELECT) {
                                    val clickCoord = getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                    val hitObs = currentActiveObstacles.find { obs ->
                                        when (obs) {
                                            is Obstacle.Circle -> sqrt((clickCoord.x - obs.centerX).pow(2) + (clickCoord.y - obs.centerY).pow(2)) <= obs.radius
                                            is Obstacle.Rectangle -> {
                                                val dx = clickCoord.x - obs.centerX; val dy = clickCoord.y - obs.centerY
                                                val rad = Math.toRadians(-obs.rotation)
                                                kotlin.math.abs(dx * cos(rad) - dy * sin(rad)) <= obs.width / 2.0 && kotlin.math.abs(dx * sin(rad) + dy * cos(rad)) <= obs.height / 2.0
                                            }
                                            is Obstacle.Polygon -> obs.vertices.any { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.3 }
                                        }
                                    }
                                    if (hitObs != null) onItemDoubleTapped?.invoke(hitObs.id, "Obstacle")
                                    else {
                                        val hitAt = currentActiveAprilTags.find { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.3 }
                                        if (hitAt != null) onItemDoubleTapped?.invoke(hitAt.id, "AprilTag")
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press && event.button == PointerButton.Secondary) {
                                    val offset = event.changes.first().position
                                    contextMenuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                                    val w = size.width.toFloat(); val h = size.height.toFloat()
                                    val hitWpIdx = currentWaypoints.indexOfFirst { sqrt((offset.x - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset, viewRotation).x).pow(2) + (offset.y - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset, viewRotation).y).pow(2)) < 20.dp.toPx() }
                                    if (hitWpIdx != -1) { contextTargetType = "Waypoint"; contextTargetIndex = hitWpIdx; contextMenuExpanded = true; continue }
                                    val hitEventIdx = currentEventMarkers.indexOfFirst { sqrt((offset.x - getTransformedCanvasOffset(getPositionOnSpline(it.waypointRelativePos, currentWaypoints), w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset, viewRotation).x).pow(2) + (offset.y - getTransformedCanvasOffset(getPositionOnSpline(it.waypointRelativePos, currentWaypoints), w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset, viewRotation).y).pow(2)) < 15.dp.toPx() }
                                    if (hitEventIdx != -1) { contextTargetType = "EventMarker"; contextTargetIndex = hitEventIdx; contextMenuExpanded = true; continue }
                                    val clickCoord = getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                    val hitObs = currentActiveObstacles.find { obs ->
                                        when (obs) {
                                            is Obstacle.Circle -> sqrt((clickCoord.x - obs.centerX).pow(2) + (clickCoord.y - obs.centerY).pow(2)) <= obs.radius
                                            is Obstacle.Rectangle -> {
                                                val dx = clickCoord.x - obs.centerX; val dy = clickCoord.y - obs.centerY; val rad = Math.toRadians(-obs.rotation)
                                                kotlin.math.abs(dx * cos(rad) - dy * sin(rad)) <= obs.width / 2.0 && kotlin.math.abs(dx * sin(rad) + dy * cos(rad)) <= obs.height / 2.0
                                            }
                                            is Obstacle.Polygon -> obs.vertices.any { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.3 }
                                        }
                                    }
                                    if (hitObs != null) { contextTargetType = "Obstacle"; contextTargetId = hitObs.id; contextMenuExpanded = true; continue }
                                    val hitAt = currentActiveAprilTags.find { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.3 }
                                    if (hitAt != null) { contextTargetType = "AprilTag"; contextTargetId = hitAt.id; contextMenuExpanded = true; continue }
                                    val hitGp = currentActiveGamePieces.find { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.2 }
                                    if (hitGp != null) { contextTargetType = "GamePiece"; contextTargetId = hitGp.id; contextMenuExpanded = true; continue }
                                    val hitFwp = currentActiveFieldWaypoints.find { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.3 }
                                    if (hitFwp != null) { contextTargetType = "FieldWaypoint"; contextTargetId = hitFwp.id; contextMenuExpanded = true; continue }
                                }
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                drawContext.canvas.save()
                // Apply view rotation around the canvas center (replaces Modifier.rotate)
                if (viewRotation != 0f) {
                    drawContext.transform.rotate(viewRotation, pivot = Offset(w / 2f, h / 2f))
                }
                drawContext.transform.translate(panOffset.x, panOffset.y)
                drawContext.transform.scale(zoomScale, zoomScale, pivot = Offset.Zero)

                drawFieldBackground(activeImage, activeConfig, w, h)
                if (showHeatmap) HeatmapOverlay.drawHeatmap(this, actualPath, fieldWidthM, fieldHeightM, league)

                drawFieldGrid(w, h, fieldWidthM, fieldHeightM, league, showCostmap = showCostmap)
                drawFtcAllianceStations(w, h, fieldWidthM, fieldHeightM, league, activeConfig)
                if (league == League.FTC) drawCoordinateAxes(w, h, fieldWidthM, fieldHeightM, league, textMeasurer)

                drawCustomObstacles(currentActiveObstacles, w, h, fieldWidthM, fieldHeightM, league, showCostmap = showCostmap)
                drawGamePieces(currentActiveGamePieces, w, h, fieldWidthM, fieldHeightM, league)
                drawAprilTags(currentActiveAprilTags, w, h, fieldWidthM, fieldHeightM, league, textMeasurer)
                drawFieldWaypoints(currentActiveFieldWaypoints, selectedFieldWaypointId, w, h, fieldWidthM, fieldHeightM, league, textMeasurer)
                drawActivePolygonPoints(currentPolygonPoints, w, h, fieldWidthM, fieldHeightM, league)

                if (contextPath != null) {
                    drawContextPath(pathCache, contextPath, contextWaypoints, w, h, fieldWidthM, fieldHeightM, league)
                }

                if (!autoGoalMode) {
                    drawPlannedSpline(pathCache, splinePoints, waypoints, w, h, fieldWidthM, fieldHeightM, league)
                    drawEventMarkers(waypoints, eventMarkerPoints, w, h, fieldWidthM, fieldHeightM, league, selectedEventMarkerIndex)
                    drawConstraintZones(pathCache, waypoints, constraintZoneSplines, w, h, fieldWidthM, fieldHeightM, league)
                    drawPointTowardsZones(pathCache, waypoints, pointTowardsZoneRenderData, w, h, fieldWidthM, fieldHeightM, league)
                    drawHolonomicRotationTargets(waypoints, combinedRotationTargets, combinedRotationTargetPoints, w, h, fieldWidthM, fieldHeightM, league)
                }

                drawActualPathAndDeviations(pathCache, actualPath, waypoints, w, h, fieldWidthM, fieldHeightM, league)
                if (autoGoalMode) {
                    drawAutoGoals(
                        pathCache = pathCache,
                        waypoints = waypoints,
                        selectedWaypointIndex = selectedWaypointIndex,
                        playbackPose = playbackPose,
                        robotDimensions = robotDimensions,
                        w = w,
                        h = h,
                        fieldWidthM = fieldWidthM,
                        fieldHeightM = fieldHeightM,
                        league = league
                    )
                } else {
                    drawRobotRepresentations(
                        pathCache = pathCache,
                        actualPath = actualPath,
                        estimatedPose = estimatedPose,
                        playbackPose = playbackPose,
                        visionPoses = visionPoses,
                        odomPose = odomPose,
                        showTruePose = showTruePose,
                        showEkfPose = showEkfPose,
                        showOdomPose = showOdomPose,
                        showVisionPoses = showVisionPoses,
                        w = w,
                        h = h,
                        fieldWidthM = fieldWidthM,
                        fieldHeightM = fieldHeightM,
                        league = league,
                        indicatorLightPosition = indicatorLightPosition
                    )
                    drawWaypoints(pathCache, waypoints, selectedWaypointIndex, isDraggingHeading, isDraggingPrevHeading, w, h, fieldWidthM, fieldHeightM, league, combinedRotationTargets, isDraggingRotation)
                }

                drawContext.canvas.restore()
            }
            }

            FieldCanvasContextMenu(
                expanded = contextMenuExpanded && !autoGoalMode,
                onDismissRequest = { contextMenuExpanded = false },
                offset = contextMenuOffset,
                targetType = contextTargetType,
                targetIndex = contextTargetIndex,
                targetId = contextTargetId,
                waypoints = waypoints,
                eventMarkers = currentEventMarkers,
                obstacles = currentActiveObstacles,
                aprilTags = currentActiveAprilTags,
                gamePieces = currentActiveGamePieces,
                fieldWaypoints = currentActiveFieldWaypoints,
                onWaypointsChanged = onWaypointsChanged,
                onEventMarkersChanged = onEventMarkersChanged,
                updateObstacles = updateObstacles,
                updateAprilTags = updateAprilTags,
                updateGamePieces = updateGamePieces,
                updateFieldWaypoints = updateFieldWaypoints,
                onClearSelected = {
                    selectedWaypointIndex = -1
                    selectedObstacleId = null
                    selectedAprilTagId = null
                    selectedGamePieceId = null
                    selectedFieldWaypointId = null
                    selectedEventMarkerIndex = -1
                }
            )

            PlannerExportPanel(
                showPathControls = showPathControls,
                projectPath = projectPath,
                league = league,
                waypoints = waypoints,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}
