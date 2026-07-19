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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.decodeFromString
import java.io.File
import com.ares.analytics.shared.League
import com.ares.analytics.shared.PathPlannerFile
import com.ares.analytics.shared.AutoFile
import com.ares.analytics.shared.AutoCommandNode
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
    val selectedPathWaypoints: List<Waypoint> = emptyList(),
    val isRedAlliance: Boolean = true,
    /** Maps indicator light names to servo positions (0.0 to 1.0). -1.0 = no light. */
    val indicatorLights: Map<String, Double> = emptyMap()
)

private class FieldViewerStateBuilder(state: FieldViewerState) {
    var trueX: Double = state.trueX
    var trueY: Double = state.trueY
    var trueHeading: Double = state.trueHeading
    var ekfX: Double? = state.ekfX
    var ekfY: Double? = state.ekfY
    var ekfHeading: Double? = state.ekfHeading
    var odomX: Double? = state.odomX
    var odomY: Double? = state.odomY
    var odomHeading: Double? = state.odomHeading
    var visionX: Double? = state.visionX
    var visionY: Double? = state.visionY
    var visionHeading: Double? = state.visionHeading
    var visionPoses: MutableMap<Int, Double> = state.visionPoses.toMutableMap()
    var visionHasTarget: Boolean = state.visionHasTarget
    var liveGamePieces: MutableMap<Int, GamePiece> = state.liveGamePieces.toMutableMap()
    var isRedAlliance: Boolean = state.isRedAlliance
    var indicatorLights: MutableMap<String, Double> = state.indicatorLights.toMutableMap()

    fun build(original: FieldViewerState): FieldViewerState {
        return original.copy(
            trueX = trueX,
            trueY = trueY,
            trueHeading = trueHeading,
            ekfX = ekfX,
            ekfY = ekfY,
            ekfHeading = ekfHeading,
            odomX = odomX,
            odomY = odomY,
            odomHeading = odomHeading,
            visionX = visionX,
            visionY = visionY,
            visionHeading = visionHeading,
            visionPoses = visionPoses.toMap(),
            visionHasTarget = visionHasTarget,
            liveGamePieces = liveGamePieces.toMap(),
            isRedAlliance = isRedAlliance,
            indicatorLights = indicatorLights.toMap()
        )
    }
}

sealed class FieldViewerIntent {
    data class FetchAvailablePaths(val projectPath: String?, val league: League) : FieldViewerIntent()
    data class SelectPath(val pathName: String?, val projectPath: String?, val league: League) : FieldViewerIntent()
    object ClearTrace : FieldViewerIntent()
    object ToggleAlliance : FieldViewerIntent()
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
            var lastEmit = System.currentTimeMillis()
            var currentBuilder = FieldViewerStateBuilder(_state.value)
            
            nt4ClientService.telemetryFlow.collect { frame ->
                val key = frame.key
                val value = frame.value
                
                when (key) {
                    "ARES/EstimatedPose/0", "Drive/Pose_X" -> { currentBuilder.trueX = value; if (key == "Drive/Pose_X") currentBuilder.ekfX = value }
                    "ARES/EstimatedPose/1", "Drive/Pose_Y" -> { currentBuilder.trueY = value; if (key == "Drive/Pose_Y") currentBuilder.ekfY = value }
                    "ARES/EstimatedPose/2", "Drive/Pose_Heading", "Drive/Drive_Heading" -> { currentBuilder.trueHeading = value; if (key != "ARES/EstimatedPose/2") currentBuilder.ekfHeading = value }
                    "Drive/Odom_X", "pinpoint_x", "pinpoint/x" -> currentBuilder.odomX = value
                    "Drive/Odom_Y", "pinpoint_y", "pinpoint/y" -> currentBuilder.odomY = value
                    "Drive/Odom_Heading", "pinpoint_heading", "pinpoint/heading" -> currentBuilder.odomHeading = value
                    "Vision/HasTarget" -> {
                        val hasTarget = value > 0.5
                        currentBuilder.visionHasTarget = hasTarget
                        if (!hasTarget) {
                            currentBuilder.visionX = null
                            currentBuilder.visionY = null
                            currentBuilder.visionHeading = null
                            currentBuilder.visionPoses.clear()
                        }
                    }
                    "Vision/Pose_X", "Vision/Pose/X" -> if (currentBuilder.visionHasTarget) currentBuilder.visionX = value
                    "Vision/Pose_Y", "Vision/Pose/Y" -> if (currentBuilder.visionHasTarget) currentBuilder.visionY = value
                    "Vision/Pose_Heading", "Vision/Pose/Heading" -> if (currentBuilder.visionHasTarget) currentBuilder.visionHeading = value
                    "ARES/Input/isRedAlliance" -> currentBuilder.isRedAlliance = value > 0.5
                }

                // Indicator light topics: Superstructure/IndicatorLight/{name}
                if (key.startsWith("Superstructure/IndicatorLight/")) {
                    val lightName = key.substringAfterLast("/")
                    currentBuilder.indicatorLights[lightName] = value
                }

                if (key.startsWith("Vision/PoseArray/") || key.startsWith("AdvantageScope/VisionPose/")) {
                    if (currentBuilder.visionHasTarget) {
                        val idx = key.substringAfterLast("/").toIntOrNull()
                        if (idx != null) {
                            currentBuilder.visionPoses[idx] = value
                        }
                    } else {
                        currentBuilder.visionPoses.clear()
                    }
                }

                if (key.startsWith("ARES/GamePieces/")) {
                    val arrayIdx = key.substringAfterLast("/").toIntOrNull()
                    if (arrayIdx != null) {
                        val pieceIdx = arrayIdx / 7
                        val attributeIdx = arrayIdx % 7
                        val currentPiece = currentBuilder.liveGamePieces[pieceIdx] ?: GamePiece(
                            id = pieceIdx.toString(),
                            name = "Piece $pieceIdx",
                            x = 0.0,
                            y = 0.0,
                            type = "Decode (Ball)"
                        )
                        
                        val updatedPiece = when (attributeIdx) {
                            0 -> currentPiece.copy(x = value)
                            1 -> currentPiece.copy(y = value)
                            else -> currentPiece
                        }
                        
                        currentBuilder.liveGamePieces[pieceIdx] = updatedPiece
                    }
                }
                
                val now = System.currentTimeMillis()
                if (now - lastEmit > 16) {
                    _state.update { currentBuilder.build(it) }
                    lastEmit = now
                    currentBuilder = FieldViewerStateBuilder(_state.value)
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
                is FieldViewerIntent.ToggleAlliance -> {
                    _state.update { it.copy(isRedAlliance = !it.isRedAlliance) }
                }
                is FieldViewerIntent.FetchAvailablePaths -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    if (!projectPath.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            try {
                                val relativePathsDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/paths"
                                    else "src/main/assets/pathplanner/paths"
                                } else {
                                    "src/main/deploy/pathplanner/paths"
                                }
                                val relativeAutosDir = relativePathsDir.replace("/paths", "/autos").replace("\\paths", "\\autos")

                                val pathsTargetDir = File(projectPath, relativePathsDir)
                                val autosTargetDir = File(projectPath, relativeAutosDir)

                                val pathFiles = if (pathsTargetDir.exists() && pathsTargetDir.isDirectory) {
                                    pathsTargetDir.listFiles { _, name -> name.endsWith(".path") }?.map { "[Path] ${it.nameWithoutExtension}" } ?: emptyList()
                                } else emptyList()

                                val autoFiles = if (autosTargetDir.exists() && autosTargetDir.isDirectory) {
                                    autosTargetDir.listFiles { _, name -> name.endsWith(".auto") }?.map { "[Auto] ${it.nameWithoutExtension}" } ?: emptyList()
                                } else emptyList()

                                val allAvailable = (autoFiles.sorted() + pathFiles.sorted())
                                _state.update { it.copy(availablePaths = allAvailable) }
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
                                val relativePathsDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/paths"
                                    else "src/main/assets/pathplanner/paths"
                                } else {
                                    "src/main/deploy/pathplanner/paths"
                                }
                                val relativeAutosDir = relativePathsDir.replace("/paths", "/autos").replace("\\paths", "\\autos")

                                val pathsTargetDir = File(projectPath, relativePathsDir)
                                val autosTargetDir = File(projectPath, relativeAutosDir)

                                val json = Json { ignoreUnknownKeys = true }
                                val loadedWps = mutableListOf<Waypoint>()

                                if (pathName.startsWith("[Auto] ")) {
                                    val autoName = pathName.substringAfter("[Auto] ")
                                    val autoFile = File(autosTargetDir, "$autoName.auto")
                                    if (autoFile.exists()) {
                                        val content = autoFile.readText()
                                        val auto = json.decodeFromString<AutoFile>(content)

                                        val extractedPaths = mutableListOf<String>()
                                        fun collectPaths(node: AutoCommandNode) {
                                            if (node.type == "path") {
                                                node.data["pathName"]?.let {
                                                    extractedPaths.add(it.toString().removeSurrounding("\""))
                                                }
                                            }
                                            node.data["commands"]?.let { commandsElement ->
                                                if (commandsElement is kotlinx.serialization.json.JsonArray) {
                                                    for (element in commandsElement) {
                                                        if (element is kotlinx.serialization.json.JsonObject) {
                                                            try {
                                                                val subNode = json.decodeFromJsonElement<AutoCommandNode>(element)
                                                                collectPaths(subNode)
                                                            } catch (e: Exception) {}
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        collectPaths(auto.command)

                                        for (pName in extractedPaths) {
                                            val pFile = File(pathsTargetDir, "$pName.path")
                                            if (pFile.exists()) {
                                                val pContent = pFile.readText()
                                                val pathFile = json.decodeFromString<PathPlannerFile>(pContent)
                                                val pathWps = pathFile.waypoints.map { pwp ->
                                                    val next = pwp.nextControl
                                                    val prev = pwp.prevControl
                                                    val heading = when {
                                                        next != null -> kotlin.math.atan2(next.y - pwp.anchor.y, next.x - pwp.anchor.x)
                                                        prev != null -> kotlin.math.atan2(pwp.anchor.y - prev.y, pwp.anchor.x - prev.x)
                                                        else -> 0.0
                                                    }
                                                    Waypoint(pwp.anchor.x, pwp.anchor.y, heading)
                                                }
                                                loadedWps.addAll(pathWps)
                                            }
                                        }
                                    }
                                } else {
                                    val cleanPathName = if (pathName.startsWith("[Path] ")) pathName.substringAfter("[Path] ") else pathName
                                    val file = File(pathsTargetDir, "$cleanPathName.path")
                                    if (file.exists()) {
                                        val content = file.readText()
                                        val pathFile = json.decodeFromString<PathPlannerFile>(content)
                                        val pathWps = pathFile.waypoints.map { pwp ->
                                            val next = pwp.nextControl
                                            val prev = pwp.prevControl
                                            val heading = when {
                                                next != null -> kotlin.math.atan2(next.y - pwp.anchor.y, next.x - pwp.anchor.x)
                                                prev != null -> kotlin.math.atan2(pwp.anchor.y - prev.y, pwp.anchor.x - prev.x)
                                                else -> 0.0
                                            }
                                            Waypoint(pwp.anchor.x, pwp.anchor.y, heading)
                                        }
                                        loadedWps.addAll(pathWps)
                                    }
                                }

                                _state.update { it.copy(selectedPathName = pathName, selectedPathWaypoints = loadedWps) }
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

