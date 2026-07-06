package com.ares.analytics.viewmodel

import com.ares.analytics.service.TrajectoryEstimator
import com.ares.analytics.shared.*
import com.ares.analytics.ui.components.pathplanner.Waypoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
    val idealStartingState: IdealStartingState = IdealStartingState(),
    val goalEndState: GoalEndState = GoalEndState(),
    val reversed: Boolean = false,
    val useDefaultConstraints: Boolean = true,
    val estimatedDuration: Double = 0.0,
    val selectedWaypointIndex: Int? = null,
    val toolMode: String = "Select",
    val viewRotation: Float = 0f,
    val trajectory: Trajectory? = null,
    val isPlaying: Boolean = false,
    val playbackTime: Double = 0.0
)

sealed class PathPlannerIntent {
    data class LoadPath(val projectPath: String?, val league: League) : PathPlannerIntent()
    data class FetchAvailablePaths(val projectPath: String?, val league: League) : PathPlannerIntent()
    data class SavePath(val projectPath: String?, val league: League) : PathPlannerIntent()
    data class CreateNewPath(val name: String = "new_path") : PathPlannerIntent()
    data class UpdatePathName(val name: String) : PathPlannerIntent()
    data class UpdateWaypoints(val newWaypoints: List<Waypoint>) : PathPlannerIntent()
    data class UpdateWaypoint(val index: Int, val waypoint: Waypoint) : PathPlannerIntent()
    data class AddWaypoint(val waypoint: Waypoint) : PathPlannerIntent()
    data class DeleteWaypoint(val index: Int) : PathPlannerIntent()
    data class SelectWaypoint(val index: Int?) : PathPlannerIntent()
    data class UpdateToolMode(val mode: String) : PathPlannerIntent()
    data class UpdateGlobalConstraints(val constraints: PathConstraints) : PathPlannerIntent()
    data class UpdateStartingState(val state: IdealStartingState) : PathPlannerIntent()
    data class UpdateEndState(val state: GoalEndState) : PathPlannerIntent()
    data class UpdateReversed(val reversed: Boolean) : PathPlannerIntent()
    data class UpdateUseDefaultConstraints(val useDefault: Boolean) : PathPlannerIntent()
    data class UpdateViewRotation(val viewRotation: Float) : PathPlannerIntent()
    
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
}

class PathPlannerViewModel(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PathPlannerState())
    val state: StateFlow<PathPlannerState> = _state.asStateFlow()

    private var playbackJob: kotlinx.coroutines.Job? = null

    private fun recalculateDuration() {
        val s = _state.value
        val trajectory = TrajectoryEstimator.generateTrajectory(
            waypoints = s.waypoints,
            globalConstraints = s.globalConstraints,
            constraintZones = s.constraintZones,
            rotationTargets = s.rotationTargets,
            idealStartingState = s.idealStartingState,
            goalEndState = s.goalEndState
        )
        _state.update { it.copy(trajectory = trajectory, estimatedDuration = trajectory.durationSeconds) }
    }

    fun onIntent(intent: PathPlannerIntent) {
        scope.launch {
            when (intent) {
                is PathPlannerIntent.LoadPath -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    val pathName = _state.value.pathName
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
                                        val heading = if (next != null) {
                                            kotlin.math.atan2(next.y - pwp.anchor.y, next.x - pwp.anchor.x)
                                        } else if (prev != null) {
                                            kotlin.math.atan2(pwp.anchor.y - prev.y, pwp.anchor.x - prev.x)
                                        } else {
                                            0.0
                                        }
                                        Waypoint(pwp.anchor.x, pwp.anchor.y, heading)
                                    }
                                    _state.update {
                                        it.copy(
                                            waypoints = loadedWps,
                                            eventMarkers = pathFile.eventMarkers,
                                            rotationTargets = pathFile.rotationTargets,
                                            constraintZones = pathFile.constraintZones,
                                            pointTowardsZones = pathFile.pointTowardsZones,
                                            globalConstraints = pathFile.globalConstraints,
                                            idealStartingState = pathFile.idealStartingState,
                                            goalEndState = pathFile.goalEndState,
                                            reversed = pathFile.reversed,
                                            useDefaultConstraints = pathFile.useDefaultConstraints,
                                            saveStatus = "Loaded $pathName.path successfully!"
                                        )
                                    }
                                    recalculateDuration()
                                } else {
                                    _state.update { it.copy(saveStatus = "New path (file not found on disk)") }
                                }
                            } catch (e: Exception) {
                                _state.update { it.copy(saveStatus = "Load failed: ${e.message}") }
                            }
                        }
                    }
                }
                is PathPlannerIntent.CreateNewPath -> {
                    _state.update { 
                        PathPlannerState(
                            pathName = intent.name,
                            availablePaths = it.availablePaths,
                            saveStatus = "New path initialized"
                        ) 
                    }
                    recalculateDuration()
                }
                is PathPlannerIntent.FetchAvailablePaths -> {
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
                is PathPlannerIntent.SavePath -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    val s = _state.value
                    if (!projectPath.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            try {
                                val pWaypoints = s.waypoints.mapIndexed { idx, wp ->
                                    val theta = wp.headingRad
                                    val anchor = PathPoint(wp.x, wp.y)
                                    val nextControl = if (idx == s.waypoints.size - 1) null else PathPoint(
                                        wp.x + kotlin.math.cos(theta) * 0.5,
                                        wp.y + kotlin.math.sin(theta) * 0.5
                                    )
                                    val prevControl = if (idx == 0) null else PathPoint(
                                        wp.x - kotlin.math.cos(theta) * 0.5,
                                        wp.y - kotlin.math.sin(theta) * 0.5
                                    )
                                    PathPlannerWaypoint(
                                        anchor = anchor,
                                        prevControl = prevControl,
                                        nextControl = nextControl
                                    )
                                }

                                val pathFile = PathPlannerFile(
                                    version = "2025.0",
                                    waypoints = pWaypoints,
                                    rotationTargets = s.rotationTargets,
                                    constraintZones = s.constraintZones,
                                    pointTowardsZones = s.pointTowardsZones,
                                    eventMarkers = s.eventMarkers,
                                    globalConstraints = s.globalConstraints,
                                    goalEndState = s.goalEndState,
                                    idealStartingState = s.idealStartingState,
                                    reversed = s.reversed,
                                    useDefaultConstraints = s.useDefaultConstraints
                                )

                                val json = Json { 
                                    prettyPrint = true
                                    encodeDefaults = true
                                }
                                val serialized = json.encodeToString(pathFile)

                                val relativeDir = if (league == League.FTC) {
                                    "TeamCode/src/main/assets/pathplanner/paths"
                                } else {
                                    "src/main/deploy/pathplanner/paths"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                targetDir.mkdirs()
                                val targetFile = File(targetDir, "${s.pathName}.path")
                                targetFile.writeText(serialized)

                                // Generate companion auto helper code file
                                try {
                                    val packageDir = if (league == League.FTC) {
                                        val candidates = listOf(
                                            "TeamCode/src/main/java/org/firstinspires/ftc/teamcode",
                                            "TeamCode/src/main/kotlin/org/firstinspires/ftc/teamcode",
                                            "src/main/java/org/firstinspires/ftc/teamcode",
                                            "src/main/kotlin/org/firstinspires/ftc/teamcode"
                                        )
                                        candidates.map { File(projectPath, it) }.firstOrNull { it.exists() && it.isDirectory }
                                    } else {
                                        val srcKotlin = File(projectPath, "src/main/kotlin")
                                        if (srcKotlin.exists() && srcKotlin.isDirectory) srcKotlin else File(projectPath, "src/main/java")
                                    }

                                    if (packageDir != null && packageDir.exists()) {
                                        val className = s.pathName.split("_", "-").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } } + "Auto"
                                        val companionFile = File(packageDir, "$className.kt")
                                        val packageName = if (league == League.FTC) {
                                            "org.firstinspires.ftc.teamcode"
                                        } else {
                                            "com.areslib.pathing"
                                        }

                                        val code = """
                                            package $packageName

                                            import com.areslib.pathing.DynamicPathLoader
                                            import com.areslib.pathing.HolonomicPathFollower

                                            /**
                                             * Auto-generated companion code for the path: ${s.pathName}.path
                                             * Map event callbacks to trigger subsystem commands.
                                             */
                                            object $className {
                                                val pathName = "${s.pathName}"

                                                fun buildPathFollower(
                                                    follower: HolonomicPathFollower,
                                                    eventMap: Map<String, () -> Unit>
                                                ) {
                                                    val path = DynamicPathLoader.loadPath(pathName)
                                                    follower.startPath(path)
                                                    follower.onEventTriggered = { eventName ->
                                                        println("[Auto] Path event triggered: ${'$'}eventName")
                                                        eventMap[eventName]?.invoke()
                                                    }
                                                }
                                            }
                                        """.trimIndent()
                                        companionFile.writeText(code)
                                    }
                                } catch (e: Exception) {
                                    System.err.println("WARN: Failed to generate companion auto file: ${e.message}")
                                }

                                _state.update { it.copy(saveStatus = "Saved to ${targetFile.name}!") }
                            } catch (e: Exception) {
                                _state.update { it.copy(saveStatus = "Save failed: ${e.message}") }
                            }
                        }
                    } else {
                        _state.update { it.copy(saveStatus = "No project path configured") }
                    }
                }
                is PathPlannerIntent.UpdatePathName -> {
                    _state.update { it.copy(pathName = intent.name) }
                }
                is PathPlannerIntent.UpdateWaypoints -> {
                    _state.update { it.copy(waypoints = intent.newWaypoints) }
                    recalculateDuration()
                }
                is PathPlannerIntent.UpdateWaypoint -> {
                    val updated = _state.value.waypoints.toMutableList().apply {
                        set(intent.index, intent.waypoint)
                    }
                    _state.update { it.copy(waypoints = updated) }
                    recalculateDuration()
                }
                is PathPlannerIntent.AddWaypoint -> {
                    val updated = _state.value.waypoints.toMutableList().apply {
                        add(intent.waypoint)
                    }
                    _state.update { it.copy(waypoints = updated) }
                    recalculateDuration()
                }
                is PathPlannerIntent.DeleteWaypoint -> {
                    val updated = _state.value.waypoints.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(waypoints = updated) }
                    recalculateDuration()
                }
                is PathPlannerIntent.SelectWaypoint -> {
                    _state.update { it.copy(selectedWaypointIndex = intent.index) }
                }
                is PathPlannerIntent.UpdateToolMode -> {
                    _state.update { it.copy(toolMode = intent.mode) }
                }
                is PathPlannerIntent.UpdateGlobalConstraints -> {
                    _state.update { it.copy(globalConstraints = intent.constraints) }
                    recalculateDuration()
                }
                is PathPlannerIntent.UpdateStartingState -> {
                    _state.update { it.copy(idealStartingState = intent.state) }
                    recalculateDuration()
                }
                is PathPlannerIntent.UpdateEndState -> {
                    _state.update { it.copy(goalEndState = intent.state) }
                    recalculateDuration()
                }
                is PathPlannerIntent.UpdateReversed -> {
                    _state.update { it.copy(reversed = intent.reversed) }
                }
                is PathPlannerIntent.UpdateUseDefaultConstraints -> {
                    _state.update { it.copy(useDefaultConstraints = intent.useDefault) }
                }
                is PathPlannerIntent.UpdateViewRotation -> {
                    _state.update { it.copy(viewRotation = intent.viewRotation) }
                }
                
                // Event Markers
                is PathPlannerIntent.AddEventMarker -> {
                    val updated = _state.value.eventMarkers + intent.marker
                    _state.update { it.copy(eventMarkers = updated) }
                }
                is PathPlannerIntent.UpdateEventMarker -> {
                    val updated = _state.value.eventMarkers.toMutableList().apply {
                        set(intent.index, intent.marker)
                    }
                    _state.update { it.copy(eventMarkers = updated) }
                }
                is PathPlannerIntent.UpdateEventMarkers -> {
                    _state.update { it.copy(eventMarkers = intent.markers) }
                }
                is PathPlannerIntent.DeleteEventMarker -> {
                    val updated = _state.value.eventMarkers.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(eventMarkers = updated) }
                }
                
                // Rotation Targets
                is PathPlannerIntent.AddRotationTarget -> {
                    val updated = _state.value.rotationTargets + intent.target
                    _state.update { it.copy(rotationTargets = updated) }
                }
                is PathPlannerIntent.UpdateRotationTarget -> {
                    val updated = _state.value.rotationTargets.toMutableList().apply {
                        set(intent.index, intent.target)
                    }
                    _state.update { it.copy(rotationTargets = updated) }
                }
                is PathPlannerIntent.UpdateRotationTargets -> {
                    _state.update { it.copy(rotationTargets = intent.targets) }
                    recalculateDuration()
                }
            is PathPlannerIntent.DeleteRotationTarget -> {
                    val updated = _state.value.rotationTargets.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(rotationTargets = updated) }
                }
                
                // Point Towards Zones
                is PathPlannerIntent.AddPointTowardsZone -> {
                    val updated = _state.value.pointTowardsZones + intent.zone
                    _state.update { it.copy(pointTowardsZones = updated) }
                }
                is PathPlannerIntent.UpdatePointTowardsZone -> {
                    val updated = _state.value.pointTowardsZones.toMutableList().apply {
                        set(intent.index, intent.zone)
                    }
                    _state.update { it.copy(pointTowardsZones = updated) }
                }
                is PathPlannerIntent.DeletePointTowardsZone -> {
                    val updated = _state.value.pointTowardsZones.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(pointTowardsZones = updated) }
                }

                // Constraint Zones
                is PathPlannerIntent.AddConstraintZone -> {
                    val updated = _state.value.constraintZones + intent.zone
                    _state.update { it.copy(constraintZones = updated) }
                    recalculateDuration()
                }
                is PathPlannerIntent.UpdateConstraintZone -> {
                    val updated = _state.value.constraintZones.toMutableList().apply {
                        set(intent.index, intent.zone)
                    }
                    _state.update { it.copy(constraintZones = updated) }
                    recalculateDuration()
                }
                is PathPlannerIntent.DeleteConstraintZone -> {
                    val updated = _state.value.constraintZones.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(constraintZones = updated) }
                    recalculateDuration()
                }

                // Playback
                is PathPlannerIntent.TogglePlayback -> {
                    val currentlyPlaying = _state.value.isPlaying
                    if (currentlyPlaying) {
                        _state.update { it.copy(isPlaying = false) }
                        playbackJob?.cancel()
                    } else {
                        // If we are at the end, reset to 0
                        if (_state.value.playbackTime >= _state.value.estimatedDuration) {
                            _state.update { it.copy(playbackTime = 0.0) }
                        }
                        _state.update { it.copy(isPlaying = true) }
                        playbackJob = scope.launch {
                            var lastTime = System.currentTimeMillis()
                            while (_state.value.isPlaying) {
                                kotlinx.coroutines.delay(16) // ~60fps
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
                is PathPlannerIntent.SeekPlayback -> {
                    _state.update { it.copy(playbackTime = intent.timeSeconds.coerceIn(0.0, _state.value.estimatedDuration)) }
                }
                is PathPlannerIntent.StopPlayback -> {
                    _state.update { it.copy(isPlaying = false, playbackTime = 0.0) }
                    playbackJob?.cancel()
                }
            }
        }
    }
}
