package com.ares.analytics.viewmodel

import com.ares.analytics.shared.AppJson
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.viewmodel.field.FieldPoseBufferManager
import com.ares.analytics.viewmodel.field.FieldTopicSubscriber
import com.ares.analytics.viewmodel.field.FieldCameraGestureController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class FieldViewerState(
    val trueX: Double = 0.0,
    val trueY: Double = 0.0,
    val trueHeading: Double = 0.0,
    val simHeading: Double? = null,
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
    val indicatorLights: Map<String, Double> = emptyMap()
)

sealed class FieldViewerIntent {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class FetchAvailablePaths(val projectPath: String?, val league: League) : FieldViewerIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SelectPath(val pathName: String?, val projectPath: String?, val league: League) : FieldViewerIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ClearTrace : FieldViewerIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ToggleAlliance : FieldViewerIntent()
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class FieldViewerViewModel(
    private val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(FieldViewerState())
    val state: StateFlow<FieldViewerState> = _state.asStateFlow()

    private val topicSubscriber = FieldTopicSubscriber(nt4ClientService, scope, _state)
    private val poseBufferManager = FieldPoseBufferManager(scope, _state)
    val cameraGestureController = FieldCameraGestureController()

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun onIntent(intent: FieldViewerIntent) {
        scope.launch {
            when (intent) {
                is FieldViewerIntent.ToggleAlliance -> {
                    _state.update { it.copy(isRedAlliance = !it.isRedAlliance) }
                }
                is FieldViewerIntent.ClearTrace -> {
                    poseBufferManager.clearTrace()
                }
                is FieldViewerIntent.FetchAvailablePaths -> {
                    fetchAvailablePaths(intent.projectPath, intent.league)
                }
                is FieldViewerIntent.SelectPath -> {
                    selectPath(intent.pathName, intent.projectPath, intent.league)
                }
            }
        }
    }

    private suspend fun fetchAvailablePaths(projectPath: String?, league: League) {
        if (projectPath.isNullOrEmpty()) return
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

    private suspend fun selectPath(pathName: String?, projectPath: String?, league: League) {
        if (pathName == null) {
            _state.update { it.copy(selectedPathName = null, selectedPathWaypoints = emptyList()) }
            return
        }
        if (projectPath.isNullOrEmpty()) return
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
                val json = AppJson
                val loadedWps = mutableListOf<Waypoint>()

                if (pathName.startsWith("[Auto] ")) {
                    val autoName = pathName.substringAfter("[Auto] ")
                    val autoFile = File(autosTargetDir, "$autoName.auto")
                    if (autoFile.exists()) {
                        val content = autoFile.readText()
                        val auto = json.decodeFromString<AutoFile>(content)
                        val extractedPaths = mutableListOf<String>()
                        /**

                         * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

                         *

                         */
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
                // Ignore
            }
        }
    }
}
