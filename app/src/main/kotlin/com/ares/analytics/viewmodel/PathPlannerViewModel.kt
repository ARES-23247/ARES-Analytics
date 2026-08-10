package com.ares.analytics.viewmodel

import com.ares.analytics.shared.AppJson

import com.ares.analytics.service.TrajectoryEstimator
import com.ares.analytics.shared.*
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.ui.components.pathplanner.resolveHeading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

data class PathPreview(val name: String, val trajectory: Trajectory?)

data class PathPlannerState(
    val pathName: String = "autonomous_route",
    val availablePaths: List<String> = emptyList(),
    val saveStatus: String = "",
    val waypoints: List<Waypoint> = listOf(
        Waypoint(-1.2, -1.2, 0.0),
        Waypoint(0.0, 0.0, Math.toRadians(45.0)),
        Waypoint(1.2, 1.2, Math.toRadians(90.0))
    ),
    val eventMarkers: List<PathPlannerEventMarker> = emptyList(),
    val rotationTargets: List<RotationTarget> = emptyList(),
    val constraintZones: List<ConstraintsZone> = emptyList(),
    val pointTowardsZones: List<PointTowardsZone> = emptyList(),
    val globalConstraints: PathConstraints = PathConstraints(),
    val idealStartingState: IdealStartingState? = null,
    val goalEndState: GoalEndState? = null,
    val reversed: Boolean = false,
    val useDefaultConstraints: Boolean = true,
    val estimatedDuration: Double = 0.0,
    val selectedWaypointIndex: Int? = null,
    val toolMode: String = "Select",
    val viewRotation: Float = 0f,
    val trajectory: Trajectory? = null,
    val isPlaying: Boolean = false,
    val playbackTime: Double = 0.0,

    // Auto Editor specific state
    val activeEditorMode: String = "Path", // "Path" or "Auto"
    val availableAutos: List<String> = emptyList(),
    val autoStartingPose: AutoStartingPose? = null,
    val currentAutoCommands: List<AutoCommandNode> = emptyList(),
    val contextAutoName: String? = null,
    val contextTrajectory: Trajectory? = null,
    val contextWaypoints: List<Waypoint> = emptyList(),

    // Browser specific state
    val showBrowser: Boolean = false,
    val availablePathPreviews: List<PathPreview> = emptyList(),
    val availableAutoPreviews: List<PathPreview> = emptyList()
)

sealed class PathPlannerIntent {

    data class LoadPath(val projectPath: String?, val league: League) : PathPlannerIntent()

    data class FetchAvailablePaths(val projectPath: String?, val league: League) : PathPlannerIntent()

    data class SavePath(val projectPath: String?, val league: League) : PathPlannerIntent()

    data class CreateNewPath(val name: String = "new_path") : PathPlannerIntent()

    data class CreateNewAuto(val name: String = "new_auto") : PathPlannerIntent()

    object OptimizePath : PathPlannerIntent()

    data class UpdatePathName(val name: String) : PathPlannerIntent()

    data class UpdateEditorMode(val mode: String) : PathPlannerIntent()

    data class UpdateWaypoints(val newWaypoints: List<Waypoint>) : PathPlannerIntent()

    data class UpdateWaypoint(val index: Int, val waypoint: Waypoint) : PathPlannerIntent()

    data class AddWaypoint(val waypoint: Waypoint) : PathPlannerIntent()

    data class DeleteWaypoint(val index: Int) : PathPlannerIntent()

    data class SelectWaypoint(val index: Int?) : PathPlannerIntent()

    data class UpdateToolMode(val mode: String) : PathPlannerIntent()

    data class UpdateGlobalConstraints(val constraints: PathConstraints) : PathPlannerIntent()

    data class UpdateStartingState(val state: IdealStartingState?) : PathPlannerIntent()

    data class UpdateEndState(val state: GoalEndState?) : PathPlannerIntent()

    data class UpdateReversed(val reversed: Boolean) : PathPlannerIntent()

    data class UpdateUseDefaultConstraints(val useDefault: Boolean) : PathPlannerIntent()

    data class UpdateViewRotation(val viewRotation: Float) : PathPlannerIntent()

    // Browser

    object ToggleBrowser : PathPlannerIntent()

    // Playback

    object TogglePlayback : PathPlannerIntent()

    data class SeekPlayback(val timeSeconds: Double) : PathPlannerIntent()

    object StopPlayback : PathPlannerIntent()

    // Event Markers

    data class AddEventMarker(val marker: PathPlannerEventMarker) : PathPlannerIntent()

    data class UpdateEventMarker(val index: Int, val marker: PathPlannerEventMarker) : PathPlannerIntent()

    data class UpdateEventMarkers(val markers: List<PathPlannerEventMarker>) : PathPlannerIntent()

    data class DeleteEventMarker(val index: Int) : PathPlannerIntent()

    // Rotation Targets

    data class AddRotationTarget(val target: RotationTarget) : PathPlannerIntent()

    data class UpdateRotationTarget(val index: Int, val target: RotationTarget) : PathPlannerIntent()

    data class UpdateRotationTargets(val targets: List<RotationTarget>) : PathPlannerIntent()

    data class DeleteRotationTarget(val index: Int) : PathPlannerIntent()

    // Point Towards Zones

    data class AddPointTowardsZone(val zone: PointTowardsZone) : PathPlannerIntent()

    data class UpdatePointTowardsZone(val index: Int, val zone: PointTowardsZone) : PathPlannerIntent()

    data class DeletePointTowardsZone(val index: Int) : PathPlannerIntent()

    // Constraint Zones

    data class AddConstraintZone(val zone: ConstraintsZone) : PathPlannerIntent()

    data class UpdateConstraintZone(val index: Int, val zone: ConstraintsZone) : PathPlannerIntent()

    data class DeleteConstraintZone(val index: Int) : PathPlannerIntent()

    // Auto Editor

    data class LoadAuto(val projectPath: String?, val league: League) : PathPlannerIntent()

    data class SaveAuto(val projectPath: String?, val league: League) : PathPlannerIntent()

    data class UpdateAutoStartingPose(val pose: AutoStartingPose?) : PathPlannerIntent()

    data class AddAutoCommand(val node: AutoCommandNode, val projectPath: String?, val league: League) : PathPlannerIntent()

    data class RemoveAutoCommand(val index: Int, val projectPath: String?, val league: League) : PathPlannerIntent()

    data class MoveAutoCommand(val fromIndex: Int, val direction: Int, val projectPath: String?, val league: League) : PathPlannerIntent()

    data class UpdateAutoCommand(val index: Int, val node: AutoCommandNode, val projectPath: String?, val league: League) : PathPlannerIntent()

    data class UpdateContextAuto(val autoName: String?, val projectPath: String?, val league: League) : PathPlannerIntent()
    data class DeletePath(val name: String, val projectPath: String?, val league: League) : PathPlannerIntent()
    data class DeleteAuto(val name: String, val projectPath: String?, val league: League) : PathPlannerIntent()
}

/**
 * State owner for interactive path/auto editing.
 * Geometry uses meters and CCW-positive radians internally; serialized PathPlanner rotations are degrees.
 */
class PathPlannerViewModel(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PathPlannerState())
    val state: StateFlow<PathPlannerState> = _state.asStateFlow()

    private var playbackJob: kotlinx.coroutines.Job? = null

    private val undoRedoManager = com.ares.analytics.viewmodel.pathing.PathUndoRedoManager(_state)
    private val waypointController = com.ares.analytics.viewmodel.pathing.WaypointController(_state, this::recalculateDuration)
    private val serializationManager = com.ares.analytics.viewmodel.pathing.PathSerializationManager(scope, _state, this::recalculateDuration)

    private fun recalculateDuration() {
        val s = _state.value
        val trajectory = com.ares.analytics.service.TrajectoryEstimator.generateTrajectory(
            waypoints = s.waypoints,
            globalConstraints = s.globalConstraints,
            constraintZones = s.constraintZones,
            rotationTargets = s.rotationTargets,
            idealStartingState = s.idealStartingState,
            goalEndState = s.goalEndState
        )
        _state.update { it.copy(trajectory = trajectory, estimatedDuration = trajectory.durationSeconds) }
    }

    private fun updateContextAutoSync(autoName: String?, projectPath: String?, league: com.ares.analytics.shared.League) {
        onIntent(PathPlannerIntent.UpdateContextAuto(autoName, projectPath, league))
    }

    fun onIntent(intent: PathPlannerIntent) {
        if (isModifyingIntent(intent)) {
            undoRedoManager.saveSnapshot()
        }

        if (waypointController.handleIntent(intent)) return

        scope.launch {
            when (intent) {
                is PathPlannerIntent.LoadPath -> serializationManager.loadPath(intent.projectPath, intent.league)
                is PathPlannerIntent.LoadAuto -> serializationManager.loadAuto(intent.projectPath, intent.league)
                is PathPlannerIntent.FetchAvailablePaths -> serializationManager.fetchAvailablePaths(intent.projectPath, intent.league)
                is PathPlannerIntent.SavePath -> serializationManager.savePath(intent.projectPath ?: return@launch, intent.league, ::updateContextAutoSync)
                is PathPlannerIntent.SaveAuto -> serializationManager.saveAuto(intent.projectPath ?: return@launch, intent.league)
                is PathPlannerIntent.CreateNewPath -> _state.update { it.copy(pathName = intent.name, saveStatus = "New path initialized") }
                is PathPlannerIntent.CreateNewAuto -> _state.update { it.copy(pathName = intent.name, autoStartingPose = null, currentAutoCommands = listOf(com.ares.analytics.shared.AutoCommandNode("sequential")), saveStatus = "New auto initialized") }
                is PathPlannerIntent.UpdateContextAuto -> serializationManager.recalculateAutoTrajectory(intent.projectPath, intent.league)
                is PathPlannerIntent.ToggleBrowser -> _state.update { it.copy(showBrowser = !it.showBrowser) }
                is PathPlannerIntent.UpdatePathName -> _state.update { it.copy(pathName = intent.name) }
                is PathPlannerIntent.UpdateToolMode -> _state.update { it.copy(toolMode = intent.mode) }
                is PathPlannerIntent.UpdateGlobalConstraints -> { _state.update { it.copy(globalConstraints = intent.constraints) }; recalculateDuration() }
                is PathPlannerIntent.UpdateStartingState -> { _state.update { it.copy(idealStartingState = intent.state) }; recalculateDuration() }
                is PathPlannerIntent.UpdateEndState -> { _state.update { it.copy(goalEndState = intent.state) }; recalculateDuration() }
                is PathPlannerIntent.UpdateReversed -> _state.update { it.copy(reversed = intent.reversed) }
                is PathPlannerIntent.UpdateUseDefaultConstraints -> _state.update { it.copy(useDefaultConstraints = intent.useDefault) }
                is PathPlannerIntent.UpdateViewRotation -> _state.update { it.copy(viewRotation = intent.viewRotation) }

                is PathPlannerIntent.TogglePlayback -> {
                    val currentlyPlaying = _state.value.isPlaying
                    if (currentlyPlaying) {
                        _state.update { it.copy(isPlaying = false) }
                        playbackJob?.cancel()
                    } else {
                        if (_state.value.playbackTime >= _state.value.estimatedDuration) {
                            _state.update { it.copy(playbackTime = 0.0) }
                        }
                        _state.update { it.copy(isPlaying = true) }
                        playbackJob = scope.launch {
                            var lastTime = System.currentTimeMillis()
                            while (_state.value.isPlaying) {
                                kotlinx.coroutines.delay(16)
                                val now = System.currentTimeMillis()
                                val dt = (now - lastTime) / 1000.0
                                lastTime = now
                                val nextTime = _state.value.playbackTime + dt
                                if (nextTime >= _state.value.estimatedDuration) {
                                    _state.update { it.copy(playbackTime = _state.value.estimatedDuration, isPlaying = false) }
                                    break
                                } else {
                                    _state.update { it.copy(playbackTime = nextTime) }
                                }
                            }
                        }
                    }
                }
                is PathPlannerIntent.SeekPlayback -> _state.update { it.copy(playbackTime = intent.timeSeconds.coerceIn(0.0, _state.value.estimatedDuration)) }
                is PathPlannerIntent.StopPlayback -> _state.update { it.copy(isPlaying = false, playbackTime = 0.0) }

                is PathPlannerIntent.AddEventMarker -> _state.update { it.copy(eventMarkers = it.eventMarkers + intent.marker) }
                is PathPlannerIntent.UpdateEventMarker -> _state.update { val l = it.eventMarkers.toMutableList(); l[intent.index] = intent.marker; it.copy(eventMarkers = l) }
                is PathPlannerIntent.UpdateEventMarkers -> _state.update { it.copy(eventMarkers = intent.markers) }
                is PathPlannerIntent.DeleteEventMarker -> _state.update { val l = it.eventMarkers.toMutableList(); l.removeAt(intent.index); it.copy(eventMarkers = l) }

                is PathPlannerIntent.AddRotationTarget -> _state.update { it.copy(rotationTargets = it.rotationTargets + intent.target) }
                is PathPlannerIntent.UpdateRotationTarget -> _state.update { val l = it.rotationTargets.toMutableList(); l[intent.index] = intent.target; it.copy(rotationTargets = l) }
                is PathPlannerIntent.UpdateRotationTargets -> { _state.update { it.copy(rotationTargets = intent.targets) }; recalculateDuration() }
                is PathPlannerIntent.DeleteRotationTarget -> _state.update { val l = it.rotationTargets.toMutableList(); l.removeAt(intent.index); it.copy(rotationTargets = l) }

                is PathPlannerIntent.AddPointTowardsZone -> _state.update { it.copy(pointTowardsZones = it.pointTowardsZones + intent.zone) }
                is PathPlannerIntent.UpdatePointTowardsZone -> _state.update { val l = it.pointTowardsZones.toMutableList(); l[intent.index] = intent.zone; it.copy(pointTowardsZones = l) }
                is PathPlannerIntent.DeletePointTowardsZone -> _state.update { val l = it.pointTowardsZones.toMutableList(); l.removeAt(intent.index); it.copy(pointTowardsZones = l) }

                is PathPlannerIntent.AddConstraintZone -> { _state.update { it.copy(constraintZones = it.constraintZones + intent.zone) }; recalculateDuration() }
                is PathPlannerIntent.UpdateConstraintZone -> { _state.update { val l = it.constraintZones.toMutableList(); l[intent.index] = intent.zone; it.copy(constraintZones = l) }; recalculateDuration() }
                is PathPlannerIntent.DeleteConstraintZone -> { _state.update { val l = it.constraintZones.toMutableList(); l.removeAt(intent.index); it.copy(constraintZones = l) }; recalculateDuration() }

                is PathPlannerIntent.UpdateEditorMode -> _state.update { it.copy(activeEditorMode = intent.mode) }

                is PathPlannerIntent.AddAutoCommand -> {
                    updateAutoCommands { oldArray ->
                        val jsonNode = AppJson.encodeToJsonElement(AutoCommandNode.serializer(), intent.node)
                        buildJsonArray {
                            oldArray.forEach { add(it) }
                            add(jsonNode)
                        }
                    }
                    serializationManager.recalculateAutoTrajectory(intent.projectPath, intent.league)
                }

                is PathPlannerIntent.RemoveAutoCommand -> {
                    updateAutoCommands { oldArray ->
                        buildJsonArray {
                            oldArray.forEachIndexed { i, element ->
                                if (i != intent.index) add(element)
                            }
                        }
                    }
                    serializationManager.recalculateAutoTrajectory(intent.projectPath, intent.league)
                }

                is PathPlannerIntent.MoveAutoCommand -> {
                    updateAutoCommands { oldArray ->
                        val toIndex = intent.fromIndex + intent.direction
                        if (intent.fromIndex in 0 until oldArray.size && toIndex in 0 until oldArray.size) {
                            val list = oldArray.toMutableList()
                            val item = list.removeAt(intent.fromIndex)
                            list.add(toIndex, item)
                            buildJsonArray { list.forEach { add(it) } }
                        } else {
                            oldArray
                        }
                    }
                    serializationManager.recalculateAutoTrajectory(intent.projectPath, intent.league)
                }

                is PathPlannerIntent.UpdateAutoCommand -> {
                    updateAutoCommands { oldArray ->
                        val jsonNode = AppJson.encodeToJsonElement(AutoCommandNode.serializer(), intent.node)
                        buildJsonArray {
                            oldArray.forEachIndexed { i, element ->
                                if (i == intent.index) add(jsonNode) else add(element)
                            }
                        }
                    }
                    serializationManager.recalculateAutoTrajectory(intent.projectPath, intent.league)
                }

                is PathPlannerIntent.DeletePath -> serializationManager.deletePath(intent.name, intent.projectPath, intent.league)
                is PathPlannerIntent.DeleteAuto -> serializationManager.deleteAuto(intent.name, intent.projectPath, intent.league)

                else -> { }
            }
        }
    }

    private fun updateAutoCommands(transform: (JsonArray) -> JsonArray) {
        val rootNode = _state.value.currentAutoCommands.firstOrNull() ?: AutoCommandNode("sequential", buildJsonObject { put("commands", buildJsonArray {}) })
        val oldCommands = (rootNode.data["commands"] as? JsonArray) ?: buildJsonArray {}
        val newCommands = transform(oldCommands)
        val newRoot = AutoCommandNode(rootNode.type, buildJsonObject {
            rootNode.data.forEach { (k, v) ->
                if (k != "commands") put(k, v)
            }
            put("commands", newCommands)
        })
        _state.update { it.copy(currentAutoCommands = listOf(newRoot)) }
    }

    private fun isModifyingIntent(intent: PathPlannerIntent): Boolean {
        return intent is PathPlannerIntent.UpdateWaypoints ||
               intent is PathPlannerIntent.UpdateWaypoint ||
               intent is PathPlannerIntent.AddWaypoint ||
               intent is PathPlannerIntent.DeleteWaypoint ||
               intent is PathPlannerIntent.OptimizePath ||
               intent is PathPlannerIntent.AddEventMarker ||
               intent is PathPlannerIntent.DeleteEventMarker
    }
}
