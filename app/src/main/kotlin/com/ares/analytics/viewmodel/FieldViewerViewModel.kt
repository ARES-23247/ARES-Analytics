package com.ares.analytics.viewmodel

import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.ui.components.pathplanner.Waypoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File
import com.ares.analytics.shared.League
import com.ares.analytics.shared.PathPlannerFile
import com.ares.analytics.shared.GamePiece
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class FieldViewerState(
    val trueX: Double = 0.0,
    val trueY: Double = 0.0,
    val trueHeading: Double = 0.0,
    val ekfX: Double? = null,
    val ekfY: Double? = null,
    val ekfHeading: Double? = null,
    val odomX: Double? = null,
    val odomY: Double? = null,
    val odomHeading: Double? = null,
    val visionX: Double? = null,
    val visionY: Double? = null,
    val visionHeading: Double? = null,
    val visionPoses: Map<Int, Double> = emptyMap(),
    val visionHasTarget: Boolean = false,
    val poseHistory: List<Waypoint> = emptyList(),
    val liveGamePieces: Map<Int, GamePiece> = emptyMap(),
    val isConnected: Boolean = false,
    val availablePaths: List<String> = emptyList(),
    val selectedPathName: String? = null,
    val selectedPathWaypoints: List<Waypoint> = emptyList()
)

sealed class FieldViewerIntent {
    data class FetchAvailablePaths(val projectPath: String?, val league: League) : FieldViewerIntent()
    data class SelectPath(val pathName: String?, val projectPath: String?, val league: League) : FieldViewerIntent()
    object ClearTrace : FieldViewerIntent()
}

class FieldViewerViewModel(
    private val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(FieldViewerState())
    val state: StateFlow<FieldViewerState> = _state.asStateFlow()

    init {
        scope.launch {
            nt4ClientService.isConnected.collect { connected ->
                _state.update { currentState ->
                    currentState.copy(
                        isConnected = connected,
                        visionHasTarget = if (connected) currentState.visionHasTarget else false,
                        visionX = if (connected && currentState.visionHasTarget) currentState.visionX else null,
                        visionY = if (connected && currentState.visionHasTarget) currentState.visionY else null,
                        visionHeading = if (connected && currentState.visionHasTarget) currentState.visionHeading else null,
                        visionPoses = if (connected && currentState.visionHasTarget) currentState.visionPoses else emptyMap()
                    )
                }
            }
        }
        scope.launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                val key = frame.key
                val value = frame.value
                
                _state.update { currentState ->
                    var newState = currentState
                    when (key) {
                        "ARES/EstimatedPose/0", "Drive/Pose_X" -> newState = newState.copy(trueX = value, ekfX = if (key == "Drive/Pose_X") value else newState.ekfX)
                        "ARES/EstimatedPose/1", "Drive/Pose_Y" -> newState = newState.copy(trueY = value, ekfY = if (key == "Drive/Pose_Y") value else newState.ekfY)
                        "ARES/EstimatedPose/2", "Drive/Pose_Heading", "Drive/Drive_Heading" -> newState = newState.copy(trueHeading = value, ekfHeading = if (key != "ARES/EstimatedPose/2") value else newState.ekfHeading)
                        "Drive/Odom_X", "pinpoint_x", "pinpoint/x" -> newState = newState.copy(odomX = value)
                        "Drive/Odom_Y", "pinpoint_y", "pinpoint/y" -> newState = newState.copy(odomY = value)
                        "Drive/Odom_Heading", "pinpoint_heading", "pinpoint/heading" -> newState = newState.copy(odomHeading = value)
                        "Vision/HasTarget" -> {
                            val hasTarget = value > 0.5
                            newState = newState.copy(
                                visionHasTarget = hasTarget,
                                visionX = if (hasTarget) newState.visionX else null,
                                visionY = if (hasTarget) newState.visionY else null,
                                visionHeading = if (hasTarget) newState.visionHeading else null,
                                visionPoses = if (hasTarget) newState.visionPoses else emptyMap()
                            )
                        }
                        "Vision/Pose_X", "Vision/Pose/X" -> newState = newState.copy(visionX = if (newState.visionHasTarget) value else null)
                        "Vision/Pose_Y", "Vision/Pose/Y" -> newState = newState.copy(visionY = if (newState.visionHasTarget) value else null)
                        "Vision/Pose_Heading", "Vision/Pose/Heading" -> newState = newState.copy(visionHeading = if (newState.visionHasTarget) value else null)
                    }

                    if (key.startsWith("Vision/PoseArray/") || key.startsWith("AdvantageScope/VisionPose/")) {
                        if (newState.visionHasTarget) {
                            val idx = key.substringAfterLast("/").toIntOrNull()
                            if (idx != null) {
                                val newMap = newState.visionPoses.toMutableMap()
                                newMap[idx] = value
                                newState = newState.copy(visionPoses = newMap)
                            }
                        } else {
                            newState = newState.copy(visionPoses = emptyMap())
                        }
                    }

                    if (key.startsWith("ARES/GamePieces/")) {
                        val arrayIdx = key.substringAfterLast("/").toIntOrNull()
                        if (arrayIdx != null) {
                            val pieceIdx = arrayIdx / 7
                            val attributeIdx = arrayIdx % 7
                            val currentPiece = newState.liveGamePieces[pieceIdx] ?: GamePiece(
                                id = pieceIdx.toString(),
                                name = "Piece $pieceIdx",
                                x = 0.0,
                                y = 0.0,
                                type = "Decode (Ball)" // Assuming Decode (Ball) for sim
                            )
                            
                            val updatedPiece = when (attributeIdx) {
                                0 -> currentPiece.copy(x = value)
                                1 -> currentPiece.copy(y = value)
                                else -> currentPiece
                            }
                            
                            val newMap = newState.liveGamePieces.toMutableMap()
                            newMap[pieceIdx] = updatedPiece
                            newState = newState.copy(liveGamePieces = newMap)
                        }
                    }
                    
                    newState
                }
            }
        }

        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(50)
                val currentState = _state.value
                if (currentState.trueX != 0.0 || currentState.trueY != 0.0) {
                    val newWp = Waypoint(currentState.trueX, currentState.trueY, currentState.trueHeading)
                    val lastWp = currentState.poseHistory.lastOrNull()
                    if (lastWp == null || kotlin.math.abs(lastWp.x - newWp.x) > 0.01 || kotlin.math.abs(lastWp.y - newWp.y) > 0.01) {
                        val newHistory = currentState.poseHistory.toMutableList()
                        newHistory.add(newWp)
                        if (newHistory.size > 2000) {
                            newHistory.subList(0, 500).clear()
                        }
                        _state.update { it.copy(poseHistory = newHistory) }
                    } else if (lastWp.headingRad != newWp.headingRad) {
                        val newHistory = currentState.poseHistory.toMutableList()
                        newHistory[newHistory.size - 1] = newWp
                        _state.update { it.copy(poseHistory = newHistory) }
                    }
                }
            }
        }
    }

    fun onIntent(intent: FieldViewerIntent) {
        scope.launch {
            when (intent) {
                is FieldViewerIntent.FetchAvailablePaths -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    if (!projectPath.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            try {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/paths"
                                    else "src/main/assets/pathplanner/paths"
                                } else {
                                    "src/main/deploy/pathplanner/paths"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                if (targetDir.exists() && targetDir.isDirectory) {
                                    val files = targetDir.listFiles { _, name -> name.endsWith(".path") }
                                    val paths = files?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
                                    _state.update { it.copy(availablePaths = paths) }
                                }
                            } catch (e: Exception) {
                                // Ignore failure to fetch paths
                            }
                        }
                    }
                }
                is FieldViewerIntent.SelectPath -> {
                    val pathName = intent.pathName
                    val projectPath = intent.projectPath
                    val league = intent.league

                    if (pathName == null) {
                        _state.update { it.copy(selectedPathName = null, selectedPathWaypoints = emptyList()) }
                        return@launch
                    }

                    if (!projectPath.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            try {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/paths"
                                    else "src/main/assets/pathplanner/paths"
                                } else {
                                    "src/main/deploy/pathplanner/paths"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                val file = File(targetDir, "$pathName.path")
                                if (file.exists()) {
                                    val json = Json { ignoreUnknownKeys = true }
                                    val content = file.readText()
                                    val pathFile = json.decodeFromString<PathPlannerFile>(content)

                                    val loadedWps = pathFile.waypoints.map { pwp ->
                                        val next = pwp.nextControl
                                        val prev = pwp.prevControl
                                        val heading = when {
                                            next != null -> {
                                                kotlin.math.atan2(next.y - pwp.anchor.y, next.x - pwp.anchor.x)
                                            }
                                            prev != null -> {
                                                kotlin.math.atan2(pwp.anchor.y - prev.y, pwp.anchor.x - prev.x)
                                            }
                                            else -> {
                                                0.0
                                            }
                                        }
                                        Waypoint(pwp.anchor.x, pwp.anchor.y, heading)
                                    }
                                    _state.update { it.copy(selectedPathName = pathName, selectedPathWaypoints = loadedWps) }
                                }
                            } catch (e: Exception) {
                                // Ignore failure to parse
                            }
                        }
                    }
                }
                is FieldViewerIntent.ClearTrace -> {
                    _state.update { it.copy(poseHistory = emptyList()) }
                }
            }
        }
    }
}

