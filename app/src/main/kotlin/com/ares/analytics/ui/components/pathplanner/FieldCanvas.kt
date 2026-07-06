package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.*
import com.ares.analytics.ui.theme.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    eventMarkers: List<PathPlannerEventMarker> = emptyList(),
    onEventMarkersChanged: ((List<PathPlannerEventMarker>) -> Unit)? = null,
    rotationTargets: List<RotationTarget> = emptyList(),
    onRotationTargetsChanged: ((List<RotationTarget>) -> Unit)? = null,
    constraintZones: List<ConstraintsZone> = emptyList(),
    pointTowardsZones: List<PointTowardsZone> = emptyList(),
    globalConstraints: PathConstraints = PathConstraints(),
    estimatedPose: Waypoint? = null,
    playbackPose: Waypoint? = null,
    visionPoses: List<Waypoint> = emptyList(),
    onItemSelected: ((String?, String?) -> Unit)? = null,
    onItemDoubleTapped: ((String, String) -> Unit)? = null,
    initialViewRotation: Float = 0f,
    onViewRotationChanged: ((Float) -> Unit)? = null,
    showToolbar: Boolean = true,
    modifier: Modifier = Modifier
) {
    var localFieldImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var localFieldImageConfig by remember { mutableStateOf(FieldImageConfig()) }
    var isDraggingHeading by remember { mutableStateOf(false) }
    var isDraggingRotation by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val pathCache = remember { PathCacheHolder() }

    var contextMenuExpanded by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var contextTargetType by remember { mutableStateOf<String?>(null) }
    var contextTargetId by remember { mutableStateOf<String?>(null) }
    var contextTargetIndex by remember { mutableStateOf<Int>(-1) }

    val density = LocalDensity.current
    val splinePoints = remember(waypoints) {
        if (waypoints.size < 2) emptyList<Waypoint>() else {
            val list = ArrayList<Waypoint>((waypoints.size - 1) * 30 + 1)
            val density = 30
            for (i in 0 until waypoints.size - 1) {
                val p0 = waypoints[i]
                val p1 = waypoints[i + 1]
                val v0x = cos(p0.headingRad) * p0.tangentMagnitude
                val v0y = sin(p0.headingRad) * p0.tangentMagnitude
                val v1x = cos(p1.headingRad) * p1.tangentMagnitude
                val v1y = sin(p1.headingRad) * p1.tangentMagnitude

                for (j in 1..density) {
                    val t = j.toDouble() / density
                    list.add(Waypoint(cubicHermite(p0.x, v0x, p1.x, v1x, t), cubicHermite(p0.y, v0y, p1.y, v1y, t)))
                }
            }
            list
        }
    }

    val eventMarkerPoints = remember(eventMarkers, waypoints) {
        if (waypoints.size < 2) emptyList()
        else eventMarkers.map { getPositionOnSpline(it.waypointRelativePos, waypoints) }
    }

    val rotationTargetPoints = remember(rotationTargets, waypoints) {
        if (waypoints.size < 2) emptyList()
        else rotationTargets.map { getPositionOnSpline(it.waypointRelativePos, waypoints) }
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
    val fieldWidthM = if (activeConfig.widthMeters > 0.0) activeConfig.widthMeters else (if (league == League.FTC) 3.65 else 16.5)
    val fieldHeightM = if (activeConfig.heightMeters > 0.0) activeConfig.heightMeters else (if (league == League.FTC) 3.65 else 8.2)

    var editorMode by remember { mutableStateOf(EditorMode.SELECT) }
    var selectedWaypointIndex by remember { mutableStateOf(-1) }
    var selectedEventMarkerIndex by remember { mutableStateOf(-1) }
    var selectedObstacleId by remember { mutableStateOf<String?>(null) }
    var selectedAprilTagId by remember { mutableStateOf<String?>(null) }
    var selectedGamePieceId by remember { mutableStateOf<String?>(null) }
    var activeGamePieceType by remember { mutableStateOf(if (league == League.FTC) "Sample (Yellow)" else "Note") }
    
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showHeatmap by remember { mutableStateOf(false) }
    val windowInfo = LocalWindowInfo.current
    val isShiftPressed = windowInfo.keyboardModifiers.isShiftPressed
    var viewRotation by remember(initialViewRotation) { mutableStateOf(initialViewRotation) }
    
    val localObstacles = remember { mutableStateListOf<Obstacle>() }
    val localGamePieces = remember { mutableStateListOf<GamePiece>() }
    val localAprilTags = remember { mutableStateListOf<AprilTagPlacement>() }

    val activeObstacles = obstacles ?: localObstacles
    val activeGamePieces = gamePieces ?: localGamePieces
    val activeAprilTags = aprilTags ?: localAprilTags

    val updateObstacles: (List<Obstacle>) -> Unit = { newObstacles ->
        if (onObstaclesChanged != null) onObstaclesChanged(newObstacles)
        else { localObstacles.clear(); localObstacles.addAll(newObstacles) }
        if (!projectPath.isNullOrEmpty()) {
            try {
                val targetDir = File(projectPath, if (league == League.FTC) {
                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths" else "src/main/assets/paths"
                } else "src/main/deploy/paths")
                targetDir.mkdirs()
                File(targetDir, "obstacles.json").writeText(Json { prettyPrint = true }.encodeToString(newObstacles))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val updateGamePieces: (List<GamePiece>) -> Unit = { newPieces ->
        if (onGamePiecesChanged != null) onGamePiecesChanged(newPieces)
        else { localGamePieces.clear(); localGamePieces.addAll(newPieces) }
        if (!projectPath.isNullOrEmpty()) {
            try {
                val targetDir = File(projectPath, if (league == League.FTC) {
                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths" else "src/main/assets/paths"
                } else "src/main/deploy/paths")
                targetDir.mkdirs()
                File(targetDir, "game_pieces.json").writeText(Json { prettyPrint = true }.encodeToString(newPieces))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    val updateAprilTags: (List<AprilTagPlacement>) -> Unit = { newTags ->
        if (onAprilTagsChanged != null) onAprilTagsChanged(newTags)
        else { localAprilTags.clear(); localAprilTags.addAll(newTags) }
        if (!projectPath.isNullOrEmpty()) {
            try {
                val targetDir = File(projectPath, if (league == League.FTC) {
                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths" else "src/main/assets/paths"
                } else "src/main/deploy/paths")
                targetDir.mkdirs()
                File(targetDir, "apriltags.json").writeText(Json { prettyPrint = true }.encodeToString(newTags))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val currentPolygonPoints = remember { mutableStateListOf<PathPoint>() }
    val currentWaypoints by rememberUpdatedState(waypoints)
    val currentActiveObstacles by rememberUpdatedState(activeObstacles)
    val currentActiveGamePieces by rememberUpdatedState(activeGamePieces)
    val currentActiveAprilTags by rememberUpdatedState(activeAprilTags)
    val currentEventMarkers by rememberUpdatedState(eventMarkers)
    val currentRotationTargets by rememberUpdatedState(rotationTargets)

    LaunchedEffect(projectPath) {
        if (!projectPath.isNullOrEmpty()) {
            try {
                val relDir = if (league == League.FTC) {
                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths" else "src/main/assets/paths"
                } else "src/main/deploy/paths"
                val fObs = File(File(projectPath, relDir), "obstacles.json")
                if (fObs.exists()) updateObstacles(Json.decodeFromString(fObs.readText()))
                val fGp = File(File(projectPath, relDir), "game_pieces.json")
                if (fGp.exists()) updateGamePieces(Json.decodeFromString(fGp.readText()))
                val fAt = File(File(projectPath, relDir), "apriltags.json")
                if (fAt.exists()) updateAprilTags(Json.decodeFromString(fAt.readText()))
                
                val imgDir = if (league == League.FTC) {
                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets" else "src/main/assets"
                } else "src/main/deploy"
                val imgFile = File(File(projectPath, imgDir), "field_image.png")
                localFieldImage = if (imgFile.exists()) imgFile.inputStream().use { loadImageBitmap(it) } else null
                
                val confFile = File(File(projectPath, imgDir), "field_image_config.json")
                localFieldImageConfig = if (confFile.exists()) Json.decodeFromString(confFile.readText()) else FieldImageConfig()
            } catch (e: Exception) { e.printStackTrace() }
        }
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
                viewRotation = viewRotation,
                onViewRotationChanged = { 
                    viewRotation = it
                    onViewRotationChanged?.invoke(it)
                }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .aspectRatio(fieldWidthM.toFloat() / fieldHeightM.toFloat())
                    .align(Alignment.Center)
                    .rotate(viewRotation)
                    .background(AresSurface)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                selectedWaypointIndex = -1; selectedEventMarkerIndex = -1
                                selectedObstacleId = null; selectedAprilTagId = null; selectedGamePieceId = null
                                isDraggingHeading = false
                            },
                            onDragCancel = {
                                selectedWaypointIndex = -1; selectedEventMarkerIndex = -1
                                selectedObstacleId = null; selectedAprilTagId = null; selectedGamePieceId = null
                                isDraggingHeading = false
                            },
                            onDrag = { change, dragAmount ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                fun snap(v: Double) = if (isShiftPressed) kotlin.math.round(v * 10.0) / 10.0 else v
                                
                                if (selectedEventMarkerIndex != -1 && onEventMarkersChanged != null) {
                                    val bestPos = getClosestSplinePosition(change.position, currentWaypoints, w, h, fieldWidthM, fieldHeightM, league)
                                    onEventMarkersChanged(currentEventMarkers.toMutableList().apply { set(selectedEventMarkerIndex, this[selectedEventMarkerIndex].copy(waypointRelativePos = bestPos)) })
                                } else if (selectedWaypointIndex != -1) {
                                    val pos = change.position
                                    if (isDraggingHeading) {
                                        val wp = currentWaypoints[selectedWaypointIndex]
                                        val posMeters = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        val dx = posMeters.x - wp.x
                                        val dy = posMeters.y - wp.y
                                        val angle = kotlin.math.atan2(dy, dx)
                                        val mag = kotlin.math.sqrt(dx * dx + dy * dy)
                                        onWaypointsChanged(currentWaypoints.toMutableList().apply { set(selectedWaypointIndex, wp.copy(headingRad = angle, tangentMagnitude = if (isShiftPressed) snap(mag) else mag)) })
                                    } else if (isDraggingRotation && onRotationTargetsChanged != null) {
                                        val wp = currentWaypoints[selectedWaypointIndex]
                                        val posMeters = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        val angle = kotlin.math.atan2(posMeters.y - wp.y, posMeters.x - wp.x)
                                        val degrees = Math.toDegrees(angle)
                                        
                                        val existingIdx = rotationTargets.indexOfFirst { kotlin.math.abs(it.waypointRelativePos - selectedWaypointIndex) < 1e-3 }
                                        if (existingIdx != -1) {
                                            val newList = rotationTargets.toMutableList()
                                            newList[existingIdx] = newList[existingIdx].copy(rotationDegrees = if (isShiftPressed) snap(degrees).toDouble() else degrees)
                                            onRotationTargetsChanged(newList)
                                        } else {
                                            val newList = rotationTargets.toMutableList()
                                            newList.add(RotationTarget(waypointRelativePos = selectedWaypointIndex.toDouble(), rotationDegrees = if (isShiftPressed) snap(degrees).toDouble() else degrees))
                                            onRotationTargetsChanged(newList)
                                        }
                                    } else {
                                        val wp = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        onWaypointsChanged(currentWaypoints.toMutableList().apply { set(selectedWaypointIndex, Waypoint(snap(wp.x), snap(wp.y), this[selectedWaypointIndex].headingRad)) })
                                    }
                                } else if (selectedObstacleId != null && showObstacleControls) {
                                    val targetObs = currentActiveObstacles.find { it.id == selectedObstacleId }
                                    if (targetObs != null && !targetObs.locked) {
                                        val prevWp = getRobotCoordFromScreen(change.previousPosition, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        val currWp = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        val dx = currWp.x - prevWp.x; val dy = currWp.y - prevWp.y
                                        updateObstacles(currentActiveObstacles.map { obs ->
                                            if (obs.id == selectedObstacleId) {
                                                when (obs) {
                                                    is Obstacle.Circle -> obs.copy(centerX = snap(obs.centerX + dx), centerY = snap(obs.centerY + dy))
                                                    is Obstacle.Rectangle -> obs.copy(centerX = snap(obs.centerX + dx), centerY = snap(obs.centerY + dy))
                                                    is Obstacle.Polygon -> obs.copy(vertices = obs.vertices.map { PathPoint(snap(it.x + dx), snap(it.y + dy)) })
                                                }
                                            } else obs
                                        })
                                    }
                                } else if (selectedAprilTagId != null && showObstacleControls) {
                                    val targetAt = currentActiveAprilTags.find { it.id == selectedAprilTagId }
                                    if (targetAt != null && !targetAt.locked) {
                                        val prevWp = getRobotCoordFromScreen(change.previousPosition, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        val currWp = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateAprilTags(currentActiveAprilTags.map { at -> if (at.id == selectedAprilTagId) at.copy(x = snap(at.x + currWp.x - prevWp.x), y = snap(at.y + currWp.y - prevWp.y)) else at })
                                    }
                                } else if (selectedGamePieceId != null && showObstacleControls) {
                                    val targetGp = currentActiveGamePieces.find { it.id == selectedGamePieceId }
                                    if (targetGp != null && !targetGp.locked) {
                                        val prevWp = getRobotCoordFromScreen(change.previousPosition, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        val currWp = getRobotCoordFromScreen(change.position, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateGamePieces(currentActiveGamePieces.map { gp -> if (gp.id == selectedGamePieceId) gp.copy(x = snap(gp.x + currWp.x - prevWp.x), y = snap(gp.y + currWp.y - prevWp.y)) else gp })
                                    }
                                }
                            }
                        )
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
                            },
                            onPress = { offset ->
                                val w = size.width.toFloat(); val h = size.height.toFloat()
                                when (editorMode) {
                                    EditorMode.SELECT -> {
                                        var hitIdx = -1; var hitHeading = false; var hitRotation = false
                                        for (i in currentWaypoints.indices) {
                                            val wp = currentWaypoints[i]
                                            val wpOffset = getTransformedCanvasOffset(wp, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                            val handleOffset = getTransformedCanvasOffset(Waypoint(wp.x + wp.tangentMagnitude * cos(wp.headingRad), wp.y + wp.tangentMagnitude * sin(wp.headingRad)), w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                            if (sqrt((offset.x - handleOffset.x).pow(2) + (offset.y - handleOffset.y).pow(2)) < 15.dp.toPx()) {
                                                hitIdx = i; hitHeading = true; break
                                            }
                                            
                                            // Check rotation handle hit
                                            val targetAngleRad = rotationTargets.find { kotlin.math.abs(it.waypointRelativePos - i) < 1e-3 }?.rotationDegrees?.let { Math.toRadians(-it) }
                                                ?: wp.headingRad
                                            
                                            val rotationHandleLenPx = 30.dp.toPx()
                                            val rotHandleX = wpOffset.x + rotationHandleLenPx * cos(targetAngleRad).toFloat()
                                            val rotHandleY = wpOffset.y + rotationHandleLenPx * sin(targetAngleRad).toFloat()
                                            
                                            if (sqrt((offset.x - rotHandleX).pow(2) + (offset.y - rotHandleY).pow(2)) < 20.dp.toPx()) {
                                                hitIdx = i; hitRotation = true; break
                                            }
                                        }
                                        if (hitIdx == -1) {
                                            hitIdx = currentWaypoints.indexOfFirst { sqrt((offset.x - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).x).pow(2) + (offset.y - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).y).pow(2)) < 20.dp.toPx() }
                                        }
                                        var hitEventIdx = -1
                                        if (hitIdx == -1) {
                                            hitEventIdx = currentEventMarkers.indexOfFirst { sqrt((offset.x - getTransformedCanvasOffset(getPositionOnSpline(it.waypointRelativePos, currentWaypoints), w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).x).pow(2) + (offset.y - getTransformedCanvasOffset(getPositionOnSpline(it.waypointRelativePos, currentWaypoints), w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).y).pow(2)) < 15.dp.toPx() }
                                        }
                                        
                                        selectedWaypointIndex = hitIdx; selectedEventMarkerIndex = hitEventIdx
                                        isDraggingHeading = hitIdx != -1 && hitHeading
                                        isDraggingRotation = hitIdx != -1 && hitRotation
                                        
                                        if (hitIdx == -1 && hitEventIdx == -1) {
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
                                            selectedObstacleId = hitObs?.id
                                            if (selectedObstacleId != null) onItemSelected?.invoke(selectedObstacleId, "Obstacle")
                                            else {
                                                val hitAt = currentActiveAprilTags.find { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.3 }
                                                selectedAprilTagId = hitAt?.id
                                                if (selectedAprilTagId != null) onItemSelected?.invoke(selectedAprilTagId, "AprilTag")
                                                else {
                                                    val hitGp = currentActiveGamePieces.find { sqrt((clickCoord.x - it.x).pow(2) + (clickCoord.y - it.y).pow(2)) < 0.2 }
                                                    selectedGamePieceId = hitGp?.id
                                                    if (selectedGamePieceId != null) onItemSelected?.invoke(selectedGamePieceId, "GamePiece")
                                                    else onItemSelected?.invoke(null, null)
                                                }
                                            }
                                        } else {
                                            selectedObstacleId = null; selectedAprilTagId = null; selectedGamePieceId = null
                                        }
                                    }
                                    EditorMode.ADD_WAYPOINT -> {
                                        onWaypointsChanged(currentWaypoints + getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset))
                                    }
                                    EditorMode.DRAW_POLYGON -> {
                                        val newWp = getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        currentPolygonPoints.add(PathPoint(newWp.x, newWp.y))
                                    }
                                    EditorMode.DRAW_CIRCLE -> {
                                        val newWp = getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateObstacles(currentActiveObstacles + Obstacle.Circle("circle_${System.currentTimeMillis()}", "Circle Obstacle ${currentActiveObstacles.size + 1}", newWp.x, newWp.y, 0.25))
                                    }
                                    EditorMode.DRAW_RECTANGLE -> {
                                        val newWp = getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateObstacles(currentActiveObstacles + Obstacle.Rectangle("rect_${System.currentTimeMillis()}", "Rectangle Obstacle ${currentActiveObstacles.size + 1}", newWp.x, newWp.y, 0.5, 0.5, 0.0))
                                    }
                                    EditorMode.PLACE_GAME_PIECE -> {
                                        val newWp = getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        val typeLabel = if (league == League.FTC) when (activeGamePieceType) { "Sample (Yellow)" -> "Yellow Sample"; "Sample (Red)" -> "Red Sample"; "Sample (Blue)" -> "Blue Sample"; "Decode (Ball)" -> "Decode Ball"; else -> "Specimen" } else "Note"
                                        updateGamePieces(currentActiveGamePieces + GamePiece("piece_${System.currentTimeMillis()}", "$typeLabel ${currentActiveGamePieces.size + 1}", newWp.x, newWp.y, activeGamePieceType))
                                    }
                                    EditorMode.PLACE_APRILTAG -> {
                                        val newWp = getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
                                        updateAprilTags(currentActiveAprilTags + AprilTagPlacement("apriltag_${System.currentTimeMillis()}", 11 + currentActiveAprilTags.size, newWp.x, newWp.y, 0.5, 0.0))
                                    }
                                    EditorMode.ERASER -> {
                                        val hitIdx = currentWaypoints.indexOfFirst { sqrt((offset.x - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).x).pow(2) + (offset.y - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).y).pow(2)) < 25f }
                                        if (hitIdx != -1) onWaypointsChanged(currentWaypoints.toMutableList().apply { removeAt(hitIdx) })
                                        else {
                                            val robotWp = getRobotCoordFromScreen(offset, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset)
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
                                                }
                                            }
                                        }
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
                                    
                                    val hitWpIdx = currentWaypoints.indexOfFirst { sqrt((offset.x - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).x).pow(2) + (offset.y - getTransformedCanvasOffset(it, w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).y).pow(2)) < 20.dp.toPx() }
                                    if (hitWpIdx != -1) { contextTargetType = "Waypoint"; contextTargetIndex = hitWpIdx; contextMenuExpanded = true; continue }
                                    
                                    val hitEventIdx = currentEventMarkers.indexOfFirst { sqrt((offset.x - getTransformedCanvasOffset(getPositionOnSpline(it.waypointRelativePos, currentWaypoints), w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).x).pow(2) + (offset.y - getTransformedCanvasOffset(getPositionOnSpline(it.waypointRelativePos, currentWaypoints), w, h, fieldWidthM, fieldHeightM, league, zoomScale, panOffset).y).pow(2)) < 15.dp.toPx() }
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
                                }
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                drawContext.canvas.save()
                drawContext.transform.translate(panOffset.x, panOffset.y)
                drawContext.transform.scale(zoomScale, zoomScale, pivot = Offset.Zero)

                drawFieldBackground(activeImage, activeConfig, w, h)
                if (showHeatmap) HeatmapOverlay.drawHeatmap(this, actualPath, fieldWidthM, fieldHeightM, league)
                
                drawFieldGrid(w, h, fieldWidthM, fieldHeightM, league)
                drawFtcAllianceStations(w, h, fieldWidthM, fieldHeightM, league, activeConfig)
                if (league == League.FTC) drawCoordinateAxes(w, h, fieldWidthM, fieldHeightM, league, textMeasurer)

                drawCustomObstacles(currentActiveObstacles, w, h, fieldWidthM, fieldHeightM, league)
                drawGamePieces(currentActiveGamePieces, w, h, fieldWidthM, fieldHeightM, league)
                drawAprilTags(currentActiveAprilTags, w, h, fieldWidthM, fieldHeightM, league)
                drawActivePolygonPoints(currentPolygonPoints, w, h, fieldWidthM, fieldHeightM, league)

                drawPlannedSpline(pathCache, splinePoints, waypoints, w, h, fieldWidthM, fieldHeightM, league)
                drawEventMarkers(waypoints, eventMarkerPoints, w, h, fieldWidthM, fieldHeightM, league, selectedEventMarkerIndex)
                drawConstraintZones(waypoints, constraintZoneSplines, w, h, fieldWidthM, fieldHeightM, league)
                drawPointTowardsZones(waypoints, pointTowardsZoneRenderData, w, h, fieldWidthM, fieldHeightM, league)
                drawHolonomicRotationTargets(waypoints, currentRotationTargets, rotationTargetPoints, w, h, fieldWidthM, fieldHeightM, league)

                drawActualPathAndDeviations(pathCache, actualPath, waypoints, w, h, fieldWidthM, fieldHeightM, league)
                drawRobotRepresentations(actualPath, estimatedPose, playbackPose, visionPoses, w, h, fieldWidthM, fieldHeightM, league)
                drawWaypoints(waypoints, selectedWaypointIndex, isDraggingHeading, w, h, fieldWidthM, fieldHeightM, league)

                drawContext.canvas.restore()
            }

            DropdownMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = { contextMenuExpanded = false },
                offset = contextMenuOffset
            ) {
                if (contextTargetType == "Waypoint" && contextTargetIndex in waypoints.indices) {
                    DropdownMenuItem(onClick = { contextMenuExpanded = false }) { Text("Edit Waypoint...") }
                    DropdownMenuItem(onClick = {
                        onWaypointsChanged(waypoints.toMutableList().apply { removeAt(contextTargetIndex) })
                        contextMenuExpanded = false; selectedWaypointIndex = -1
                    }) { Text("Delete Waypoint") }
                } else if (contextTargetType == "Obstacle" && contextTargetId != null) {
                    DropdownMenuItem(onClick = {
                        updateObstacles(currentActiveObstacles.filter { it.id != contextTargetId })
                        contextMenuExpanded = false; selectedObstacleId = null
                    }) { Text("Delete Obstacle") }
                } else if (contextTargetType == "AprilTag" && contextTargetId != null) {
                    DropdownMenuItem(onClick = {
                        updateAprilTags(currentActiveAprilTags.filter { it.id != contextTargetId })
                        contextMenuExpanded = false; selectedAprilTagId = null
                    }) { Text("Delete AprilTag") }
                } else if (contextTargetType == "GamePiece" && contextTargetId != null) {
                    val gp = currentActiveGamePieces.find { it.id == contextTargetId }
                    if (gp != null) {
                        DropdownMenuItem(onClick = {
                            val nextType = if (gp.type == "Sample") "Specimen" else "Sample"
                            updateGamePieces(currentActiveGamePieces.map { if (it.id == contextTargetId) it.copy(type = nextType) else it })
                            contextMenuExpanded = false
                        }) { Text("Toggle Type: ${if (gp.type == "Sample") "Specimen" else "Sample"}") }
                    }
                    DropdownMenuItem(onClick = {
                        updateGamePieces(currentActiveGamePieces.filter { it.id != contextTargetId })
                        contextMenuExpanded = false; selectedGamePieceId = null
                    }) { Text("Delete Game Piece") }
                } else if (contextTargetType == "EventMarker" && contextTargetIndex in currentEventMarkers.indices) {
                    DropdownMenuItem(onClick = {
                        onEventMarkersChanged?.invoke(currentEventMarkers.toMutableList().apply { removeAt(contextTargetIndex) })
                        contextMenuExpanded = false; selectedEventMarkerIndex = -1
                    }) { Text("Delete Event Marker") }
                } else {
                    DropdownMenuItem(onClick = { contextMenuExpanded = false }) { Text("Cancel") }
                }
            }

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
