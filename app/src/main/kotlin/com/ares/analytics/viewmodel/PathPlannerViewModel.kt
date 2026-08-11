package com.ares.analytics.viewmodel

import com.ares.analytics.shared.AppJson

import com.ares.analytics.service.TrajectoryEstimator
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.service.AresGenerationPhase
import com.ares.analytics.service.AresProjectGenerator
import com.ares.analytics.shared.*
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.ui.components.pathplanner.resolveHeading
import com.ares.analytics.viewmodel.pathing.AresAutoRepository
import com.ares.analytics.viewmodel.pathing.AutoRevisionSummary
import com.ares.analytics.viewmodel.pathing.AutoCapabilityScanner
import com.ares.analytics.viewmodel.pathing.RobotDimensions
import com.ares.analytics.viewmodel.project.ProjectMetadataRepository
import com.areslib.project.AresLeague
import com.areslib.project.AresProjectMetadataDocument
import com.ares.analytics.viewmodel.pathing.clampAutoPose
import com.ares.analytics.viewmodel.pathing.validateAutoFieldBounds
import com.ares.analytics.viewmodel.pathing.withClampedDriveTarget
import com.ares.analytics.viewmodel.project.AutonomousCatalogProjectRepository
import com.ares.analytics.viewmodel.project.ProjectRevisionSummary
import com.ares.analytics.viewmodel.project.RoutineProjectRepository
import com.ares.analytics.viewmodel.routine.clampRoutinePose
import com.ares.analytics.viewmodel.routine.clampDriveTargets
import com.ares.analytics.viewmodel.routine.defaultRoutineStep
import com.ares.analytics.viewmodel.routine.lastRoutineDriveTarget
import com.ares.analytics.viewmodel.routine.routineDriveStepsInExecutionOrder
import com.ares.analytics.viewmodel.routine.routineEditorValidation
import com.ares.analytics.viewmodel.routine.withRoutineRouteWaypoints
import com.areslib.auto.AutoDriveStep
import com.areslib.auto.AutoPose
import com.areslib.auto.AutoRoutine
import com.areslib.auto.AutoStep
import com.areslib.auto.AutoStepKind
import com.areslib.auto.validateAutoRoutine
import com.areslib.math.geometry.Pose2d
import com.areslib.math.geometry.Rotation2d
import com.areslib.pathing.CommandKey
import com.areslib.pathing.DriveModel
import com.areslib.pathing.JerkLimitedTrajectoryProvider
import com.areslib.pathing.NamedCommandDescriptor
import com.areslib.pathing.TrajectoryLimits
import com.areslib.pathing.TrajectoryPlanner
import com.areslib.pathing.TrajectoryPreset
import com.areslib.pathing.TrajectoryRequest
import com.areslib.catalog.ActionDescriptor
import com.areslib.catalog.CapabilityCatalogDocument
import com.areslib.catalog.CapabilityContext
import com.areslib.catalog.ConditionDescriptor
import com.areslib.routine.AutonomousCatalogDocument
import com.areslib.routine.AutonomousCatalogEntry
import com.areslib.routine.RoutineAlliance
import com.areslib.routine.RoutineDocument
import com.areslib.routine.RoutineDriveStep
import com.areslib.routine.RoutinePose
import com.areslib.routine.RoutineStep
import com.areslib.routine.RoutineStepKind
import com.areslib.routine.RoutineValidationIssue
import com.areslib.routine.RoutineValidationSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

data class PathPreview(val name: String, val trajectory: Trajectory?)

private fun newAresAuto(name: String = "New Auto"): AutoRoutine = AutoRoutine(
    documentId = "${safeAutoDocumentId(name).take(55)}-${UUID.randomUUID().toString().take(8)}",
    name = name,
    startingPose = AutoPose(0.0, 0.0, 0.0),
    steps = emptyList()
)

private fun safeAutoDocumentId(name: String): String = name.trim().lowercase()
    .replace(Regex("[^a-z0-9._-]+"), "-")
    .trim('-', '.', '_')
    .take(64)
    .ifEmpty { "auto" }

private fun newRoutine(name: String = "New Routine"): RoutineDocument = RoutineDocument(
    documentId = "${safeAutoDocumentId(name).take(55)}-${UUID.randomUUID().toString().take(8)}",
    name = name,
    steps = emptyList()
)

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
    val activeEditorMode: String = "Auto", // Legacy import/export mode; the primary editor is unified.
    val availableAutos: List<String> = emptyList(),
    val autoStartingPose: AutoStartingPose? = null,
    val currentAutoCommands: List<AutoCommandNode> = emptyList(),
    val contextAutoName: String? = null,
    val contextTrajectory: Trajectory? = null,
    val contextWaypoints: List<Waypoint> = emptyList(),

    // Browser specific state
    val showBrowser: Boolean = false,
    val availablePathPreviews: List<PathPreview> = emptyList(),
    val availableAutoPreviews: List<PathPreview> = emptyList(),

    // Native ARES visual auto editor. PathPlanner fields above remain import/export adapters.
    val aresAuto: AutoRoutine = newAresAuto(),
    val aresAutoValidation: List<com.areslib.auto.AutoValidationIssue> = validateAutoRoutine(newAresAuto()),
    val availableAresAutos: List<AutoRoutine> = emptyList(),
    val aresAutoRevisions: List<AutoRevisionSummary> = emptyList(),
    val commandCatalog: List<NamedCommandDescriptor> = emptyList(),
    val capabilityStatus: String = "Select a project to discover robot actions",
    val activeLeague: League = League.FTC,
    val robotDimensions: RobotDimensions = RobotDimensions.defaultFor(League.FTC),
    val projectMetadata: AresProjectMetadataDocument? = null,
    val generationPhase: AresGenerationPhase = AresGenerationPhase.IDLE,
    val generationMessage: String? = null,
    val generatedContentHash: String? = null,

    // Canonical, trigger-neutral Routine Builder state. Legacy PathPlanner/AutoRoutine fields above
    // remain import adapters only and are intentionally not used by the primary editor.
    val routine: RoutineDocument = newRoutine(),
    val routineValidation: List<RoutineValidationIssue> = emptyList(),
    val availableRoutines: List<RoutineDocument> = emptyList(),
    val routineRevisions: List<ProjectRevisionSummary> = emptyList(),
    val capabilityCatalog: CapabilityCatalogDocument? = null,
    val routineActions: List<ActionDescriptor> = emptyList(),
    val routineConditions: List<ConditionDescriptor> = emptyList(),
    val autonomousCatalog: AutonomousCatalogDocument? = null,
    val autonomousEntry: AutonomousCatalogEntry? = null,
    val availableInAutonomousSelector: Boolean = false,
    val legacyRoutineFiles: List<File> = emptyList()
)

sealed class PathPlannerIntent {

    data class LoadPath(val projectPath: String?, val league: League, val name: String? = null) : PathPlannerIntent()

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

    data class LoadAuto(val projectPath: String?, val league: League, val name: String? = null) : PathPlannerIntent()

    data class SaveAuto(val projectPath: String?, val league: League) : PathPlannerIntent()

    data class UpdateAutoStartingPose(val pose: AutoStartingPose?) : PathPlannerIntent()

    data class AddAutoCommand(val node: AutoCommandNode, val projectPath: String?, val league: League) : PathPlannerIntent()

    data class RemoveAutoCommand(val index: Int, val projectPath: String?, val league: League) : PathPlannerIntent()

    data class MoveAutoCommand(val fromIndex: Int, val direction: Int, val projectPath: String?, val league: League) : PathPlannerIntent()

    data class UpdateAutoCommand(val index: Int, val node: AutoCommandNode, val projectPath: String?, val league: League) : PathPlannerIntent()

    data class UpdateContextAuto(val autoName: String?, val projectPath: String?, val league: League) : PathPlannerIntent()
    data class DeletePath(val name: String, val projectPath: String?, val league: League) : PathPlannerIntent()
    data class DeleteAuto(val name: String, val projectPath: String?, val league: League) : PathPlannerIntent()

    // Native ARES GUI/DSL auto document intents.
    data class LoadAresAuto(val projectPath: String?, val league: League, val documentId: String) : PathPlannerIntent()
    data class SaveAresAuto(val projectPath: String?, val league: League) : PathPlannerIntent()
    data class RestoreAresAuto(
        val projectPath: String?,
        val league: League,
        val contentHash: String
    ) : PathPlannerIntent()
    data class UpdateAresStartingPose(val pose: AutoPose, val league: League) : PathPlannerIntent()
    data class AddAresDriveGoal(val league: League) : PathPlannerIntent()
    data class AddAresCommand(val commandKey: String, val league: League) : PathPlannerIntent()
    data class AddAresWait(val league: League) : PathPlannerIntent()
    data class UpdateAresStep(val index: Int, val step: AutoStep, val league: League) : PathPlannerIntent()
    data class RemoveAresStep(val index: Int, val league: League) : PathPlannerIntent()
    data class MoveAresStep(val index: Int, val direction: Int, val league: League) : PathPlannerIntent()
    data class UpdateAresRouteWaypoints(val waypoints: List<Waypoint>, val league: League) : PathPlannerIntent()
    data class ConfigureAresField(val league: League, val robotDimensions: RobotDimensions) : PathPlannerIntent()
    data class UpdateCanonicalRobotDimensions(
        val projectPath: String?,
        val robotDimensions: RobotDimensions
    ) : PathPlannerIntent()

    // Canonical Routine Builder intents. These do not imply autonomous use.
    data class LoadRoutine(val projectPath: String?, val documentId: String) : PathPlannerIntent()
    data class SaveRoutine(val projectPath: String?) : PathPlannerIntent()
    data class SaveAndGenerateRoutine(val projectPath: String?, val league: League) : PathPlannerIntent()
    data class RestoreRoutine(val projectPath: String?, val contentHash: String) : PathPlannerIntent()
    data class CreateRoutine(val name: String = "New Routine") : PathPlannerIntent()
    data class UpdateRoutineName(val name: String) : PathPlannerIntent()
    data class UpdateRoutineDescription(val description: String) : PathPlannerIntent()
    data class AddRoutineStep(val kind: RoutineStepKind) : PathPlannerIntent()
    data class UpdateRoutineStep(val index: Int, val step: RoutineStep) : PathPlannerIntent()
    data class RemoveRoutineStep(val index: Int) : PathPlannerIntent()
    data class MoveRoutineStep(val index: Int, val direction: Int) : PathPlannerIntent()
    data class AddRoutineChild(val parentIndex: Int, val toElseBranch: Boolean, val kind: RoutineStepKind) : PathPlannerIntent()
    data class UpdateRoutineChild(
        val parentIndex: Int,
        val childIndex: Int,
        val toElseBranch: Boolean,
        val step: RoutineStep
    ) : PathPlannerIntent()
    data class RemoveRoutineChild(
        val parentIndex: Int,
        val childIndex: Int,
        val toElseBranch: Boolean
    ) : PathPlannerIntent()
    data class SetAutonomousAvailability(val enabled: Boolean, val league: League) : PathPlannerIntent()
    data class UpdateAutonomousEntry(val entry: AutonomousCatalogEntry, val league: League) : PathPlannerIntent()
    data class UpdateRoutineFieldWaypoints(val waypoints: List<Waypoint>, val league: League) : PathPlannerIntent()
    data class ImportLegacyRoutine(val projectPath: String?, val league: League, val file: File) : PathPlannerIntent()
}

/**
 * State owner for interactive path/auto editing.
 * Geometry uses meters and CCW-positive radians internally; serialized PathPlanner rotations are degrees.
 */
class PathPlannerViewModel(
    private val scope: CoroutineScope,
    nt4ClientService: Nt4ClientService? = null,
    private val projectGenerator: AresProjectGenerator? = null
) {
    private val _state = MutableStateFlow(PathPlannerState())
    val state: StateFlow<PathPlannerState> = _state.asStateFlow()

    private var playbackJob: kotlinx.coroutines.Job? = null

    private val undoRedoManager = com.ares.analytics.viewmodel.pathing.PathUndoRedoManager(_state)
    private val waypointController = com.ares.analytics.viewmodel.pathing.WaypointController(_state, this::recalculateDuration)
    private val serializationManager = com.ares.analytics.viewmodel.pathing.PathSerializationManager(scope, _state, this::recalculateDuration)
    private val aresAutoRepository = AresAutoRepository()
    private val autoCapabilityScanner = AutoCapabilityScanner()
    private val routineRepository = RoutineProjectRepository()
    private val autonomousRepository = AutonomousCatalogProjectRepository(routineRepository)
    private val capabilityRepository = com.ares.analytics.viewmodel.project.CapabilityCatalogProjectRepository()
    private val metadataRepository = ProjectMetadataRepository()
    private val trajectoryPlanner = TrajectoryPlanner(listOf(JerkLimitedTrajectoryProvider))
    private var projectCommandCatalog: List<NamedCommandDescriptor> = emptyList()
    private var liveCommandCatalog: List<NamedCommandDescriptor> = emptyList()

    init {
        projectGenerator?.let { generator ->
            scope.launch {
                generator.aresGenerationState.collect { generation ->
                    _state.update {
                        it.copy(
                            generationPhase = generation.phase,
                            generationMessage = generation.message.ifBlank { null },
                            generatedContentHash = generation.contentHash
                        )
                    }
                }
            }
        }
        if (nt4ClientService != null) {
            scope.launch {
                nt4ClientService.telemetryFlow
                    .filter { it.key == "ARES/Auto/CommandCatalog" }
                    .collect { frame ->
                        parseCommandCatalog(frame.stringValue)?.let { catalog ->
                            liveCommandCatalog = catalog
                            updateMergedCommandCatalog()
                        }
                    }
            }
        }
    }

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
                is PathPlannerIntent.LoadPath -> serializationManager.loadPath(intent.projectPath, intent.league, intent.name)
                is PathPlannerIntent.LoadAuto -> serializationManager.loadAuto(intent.projectPath, intent.league, intent.name)
                is PathPlannerIntent.FetchAvailablePaths -> {
                    refreshAresAutos(intent.projectPath, intent.league)
                    refreshRoutineProject(intent.projectPath, intent.league)
                }
                is PathPlannerIntent.SavePath -> serializationManager.savePath(intent.projectPath ?: return@launch, intent.league, ::updateContextAutoSync)
                is PathPlannerIntent.SaveAuto -> serializationManager.saveAuto(intent.projectPath ?: return@launch, intent.league)
                is PathPlannerIntent.CreateNewPath -> _state.update { it.copy(pathName = intent.name, saveStatus = "New path initialized") }
                is PathPlannerIntent.CreateNewAuto -> {
                    val draft = newAresAuto(intent.name.replace('_', ' '))
                    _state.update {
                        it.copy(
                            pathName = draft.name,
                            aresAuto = draft,
                            aresAutoValidation = validateAutoRoutine(draft) +
                                validateAutoFieldBounds(draft, it.activeLeague, it.robotDimensions),
                            aresAutoRevisions = emptyList(),
                            saveStatus = "New visual auto initialized"
                        )
                    }
                }
                is PathPlannerIntent.UpdateContextAuto -> serializationManager.recalculateAutoTrajectory(intent.projectPath, intent.league)
                is PathPlannerIntent.ToggleBrowser -> _state.update { it.copy(showBrowser = !it.showBrowser) }
                is PathPlannerIntent.UpdatePathName -> {
                    updateAresAuto { it.copy(name = intent.name) }
                }
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

                is PathPlannerIntent.LoadAresAuto -> loadAresAuto(
                    intent.projectPath,
                    intent.league,
                    intent.documentId
                )
                is PathPlannerIntent.SaveAresAuto -> saveAresAuto(intent.projectPath, intent.league)
                is PathPlannerIntent.RestoreAresAuto -> restoreAresAuto(
                    intent.projectPath,
                    intent.league,
                    intent.contentHash
                )
                is PathPlannerIntent.UpdateAresStartingPose -> {
                    updateAresAuto {
                        it.copy(startingPose = clampAutoPose(intent.pose, intent.league, _state.value.robotDimensions))
                    }
                    recalculateAresPreview(intent.league)
                }
                is PathPlannerIntent.AddAresDriveGoal -> {
                    val current = _state.value.aresAuto
                    val previous = current.steps.lastDriveTarget()
                        ?: current.startingPose
                    val target = clampAutoPose(
                        previous.copy(xMeters = previous.xMeters + 0.5),
                        intent.league,
                        _state.value.robotDimensions
                    )
                    updateAresAuto { routine ->
                        routine.copy(steps = routine.steps + AutoStep.drive(AutoDriveStep(target)))
                    }
                    recalculateAresPreview(intent.league)
                }
                is PathPlannerIntent.AddAresCommand -> {
                    val key = runCatching { CommandKey(intent.commandKey) }.getOrNull() ?: return@launch
                    updateAresAuto { routine -> routine.copy(steps = routine.steps + AutoStep.command(key)) }
                    recalculateAresPreview(intent.league)
                }
                is PathPlannerIntent.AddAresWait -> {
                    updateAresAuto { routine ->
                        routine.copy(
                            steps = routine.steps + AutoStep(
                                kind = AutoStepKind.WAIT,
                                durationSeconds = 1.0
                            )
                        )
                    }
                    recalculateAresPreview(intent.league)
                }
                is PathPlannerIntent.UpdateAresStep -> {
                    updateAresAuto { routine ->
                        if (intent.index !in routine.steps.indices) return@updateAresAuto routine
                        val steps = routine.steps.toMutableList()
                        steps[intent.index] = intent.step.withClampedDriveTarget(
                            intent.league,
                            _state.value.robotDimensions
                        )
                        routine.copy(steps = steps)
                    }
                    recalculateAresPreview(intent.league)
                }
                is PathPlannerIntent.RemoveAresStep -> {
                    updateAresAuto { routine ->
                        if (intent.index !in routine.steps.indices) return@updateAresAuto routine
                        routine.copy(steps = routine.steps.filterIndexed { index, _ -> index != intent.index })
                    }
                    recalculateAresPreview(intent.league)
                }
                is PathPlannerIntent.MoveAresStep -> {
                    updateAresAuto { routine ->
                        val destination = intent.index + intent.direction
                        if (intent.index !in routine.steps.indices || destination !in routine.steps.indices) {
                            return@updateAresAuto routine
                        }
                        val steps = routine.steps.toMutableList()
                        val moved = steps.removeAt(intent.index)
                        steps.add(destination, moved)
                        routine.copy(steps = steps)
                    }
                    recalculateAresPreview(intent.league)
                }
                is PathPlannerIntent.UpdateAresRouteWaypoints -> {
                    updateAresRouteWaypoints(intent.waypoints, intent.league)
                    recalculateAresPreview(intent.league)
                }
                is PathPlannerIntent.ConfigureAresField -> {
                    _state.update { current ->
                        val metadata = current.projectMetadata
                        val dimensions = metadata?.let { RobotDimensions(it.robotLengthMeters, it.robotWidthMeters) }
                            ?: intent.robotDimensions.normalized()
                        val league = metadata?.league?.toAnalyticsLeague() ?: intent.league
                        current.copy(
                            activeLeague = league,
                            robotDimensions = dimensions,
                            aresAutoValidation = validateAutoRoutine(current.aresAuto) +
                                validateAutoFieldBounds(current.aresAuto, league, dimensions),
                            routineValidation = routineEditorValidation(
                                current.routine,
                                current.capabilityCatalog,
                                current.availableRoutines,
                                league,
                                dimensions,
                                current.autonomousEntry
                            )
                        )
                    }
                }
                is PathPlannerIntent.UpdateCanonicalRobotDimensions -> {
                    val metadata = _state.value.projectMetadata
                    val projectPath = intent.projectPath
                    if (metadata != null && !projectPath.isNullOrBlank()) {
                        val dimensions = intent.robotDimensions.normalized()
                        withContext(Dispatchers.IO) {
                            metadataRepository.save(
                                projectPath,
                                metadata.copy(
                                    robotLengthMeters = dimensions.lengthMeters,
                                    robotWidthMeters = dimensions.widthMeters
                                )
                            )
                        }
                        _state.update { current ->
                            current.copy(
                                projectMetadata = metadata.copy(
                                    robotLengthMeters = dimensions.lengthMeters,
                                    robotWidthMeters = dimensions.widthMeters
                                ),
                                robotDimensions = dimensions,
                                saveStatus = "Saved canonical robot footprint to .ares/project.json"
                            )
                        }
                    }
                }

                is PathPlannerIntent.CreateRoutine -> {
                    val draft = newRoutine(intent.name)
                    _state.update { current ->
                        current.copy(
                            pathName = draft.name,
                            routine = draft,
                            routineValidation = routineEditorValidation(
                                draft,
                                current.capabilityCatalog,
                                current.availableRoutines,
                                current.activeLeague,
                                current.robotDimensions,
                                null
                            ),
                            routineRevisions = emptyList(),
                            autonomousEntry = null,
                            availableInAutonomousSelector = false,
                            saveStatus = "New reusable routine initialized"
                        )
                    }
                    recalculateRoutinePreview()
                }
                is PathPlannerIntent.LoadRoutine -> loadRoutine(intent.projectPath, intent.documentId)
                is PathPlannerIntent.SaveRoutine -> saveRoutine(intent.projectPath)
                is PathPlannerIntent.SaveAndGenerateRoutine -> {
                    if (saveRoutine(intent.projectPath)) {
                        val path = intent.projectPath
                        val generator = projectGenerator
                        if (!path.isNullOrBlank() && generator != null) {
                            generator.generateAresProject(path, intent.league)
                        } else {
                            _state.update { it.copy(saveStatus = "Saved, but project generation is unavailable") }
                        }
                    }
                }
                is PathPlannerIntent.RestoreRoutine -> restoreRoutine(intent.projectPath, intent.contentHash)
                is PathPlannerIntent.UpdateRoutineName -> updateRoutine { it.copy(name = intent.name) }
                is PathPlannerIntent.UpdateRoutineDescription -> updateRoutine {
                    it.copy(description = intent.description.trim().ifEmpty { null })
                }
                is PathPlannerIntent.AddRoutineStep -> {
                    val current = _state.value
                    val pose = current.routine.steps.lastRoutineDriveTarget()
                        ?: current.autonomousEntry?.startingPose
                        ?: RoutinePose(0.0, 0.0, 0.0)
                    val step = defaultRoutineStep(
                        intent.kind,
                        clampRoutinePose(pose, current.activeLeague, current.robotDimensions),
                        current.routineActions.firstOrNull()?.key,
                        current.routineConditions.firstOrNull()?.key,
                        current.availableRoutines.firstOrNull { it.documentId != current.routine.documentId }?.documentId
                    )
                    updateRoutine { it.copy(steps = it.steps + step) }
                    recalculateRoutinePreview()
                }
                is PathPlannerIntent.UpdateRoutineStep -> {
                    updateRoutine { routine ->
                        if (intent.index !in routine.steps.indices) return@updateRoutine routine
                        routine.copy(steps = routine.steps.toMutableList().apply {
                            this[intent.index] = intent.step.clampDriveTargets(
                                _state.value.activeLeague,
                                _state.value.robotDimensions
                            )
                        })
                    }
                    recalculateRoutinePreview()
                }
                is PathPlannerIntent.RemoveRoutineStep -> {
                    updateRoutine { routine ->
                        if (intent.index !in routine.steps.indices) routine else routine.copy(
                            steps = routine.steps.filterIndexed { index, _ -> index != intent.index }
                        )
                    }
                    recalculateRoutinePreview()
                }
                is PathPlannerIntent.MoveRoutineStep -> {
                    updateRoutine { routine ->
                        val destination = intent.index + intent.direction
                        if (intent.index !in routine.steps.indices || destination !in routine.steps.indices) {
                            return@updateRoutine routine
                        }
                        routine.copy(steps = routine.steps.toMutableList().apply {
                            add(destination, removeAt(intent.index))
                        })
                    }
                    recalculateRoutinePreview()
                }
                is PathPlannerIntent.AddRoutineChild -> updateRoutineChildList(
                    intent.parentIndex,
                    intent.toElseBranch
                ) { children ->
                    val current = _state.value
                    children + defaultRoutineStep(
                        intent.kind,
                        RoutinePose(0.0, 0.0, 0.0),
                        current.routineActions.firstOrNull()?.key,
                        current.routineConditions.firstOrNull()?.key,
                        current.availableRoutines.firstOrNull { it.documentId != current.routine.documentId }?.documentId
                    )
                }
                is PathPlannerIntent.UpdateRoutineChild -> updateRoutineChildList(
                    intent.parentIndex,
                    intent.toElseBranch
                ) { children ->
                    if (intent.childIndex !in children.indices) children else children.toMutableList().apply {
                        this[intent.childIndex] = intent.step.clampDriveTargets(
                            _state.value.activeLeague,
                            _state.value.robotDimensions
                        )
                    }
                }
                is PathPlannerIntent.RemoveRoutineChild -> updateRoutineChildList(
                    intent.parentIndex,
                    intent.toElseBranch
                ) { children -> children.filterIndexed { index, _ -> index != intent.childIndex } }
                is PathPlannerIntent.SetAutonomousAvailability -> setAutonomousAvailability(intent.enabled, intent.league)
                is PathPlannerIntent.UpdateAutonomousEntry -> {
                    val clamped = intent.entry.copy(
                        routineId = _state.value.routine.documentId,
                        startingPose = clampRoutinePose(
                            intent.entry.startingPose,
                            intent.league,
                            _state.value.robotDimensions
                        )
                    )
                    _state.update { current -> current.copy(autonomousEntry = clamped, availableInAutonomousSelector = true) }
                    updateRoutine { it }
                    recalculateRoutinePreview()
                }
                is PathPlannerIntent.UpdateRoutineFieldWaypoints -> {
                    updateRoutineFieldWaypoints(intent.waypoints, intent.league)
                    recalculateRoutinePreview()
                }
                is PathPlannerIntent.ImportLegacyRoutine -> importLegacyRoutine(
                    intent.projectPath,
                    intent.league,
                    intent.file
                )

                else -> { }
            }
        }
    }

    private fun updateRoutine(transform: (RoutineDocument) -> RoutineDocument) {
        _state.update { current ->
            val updated = transform(current.routine)
            val existingEntry = current.autonomousEntry
            val entry = existingEntry?.copy(
                routineId = updated.documentId,
                displayName = if (existingEntry.displayName == current.routine.name) {
                    updated.name
                } else {
                    existingEntry.displayName
                }
            )
            current.copy(
                pathName = updated.name,
                routine = updated,
                autonomousEntry = entry,
                routineValidation = routineEditorValidation(
                    updated,
                    current.capabilityCatalog,
                    current.availableRoutines,
                    current.activeLeague,
                    current.robotDimensions,
                    entry
                ),
                saveStatus = if (current.saveStatus.startsWith("Saved")) {
                    "Unsaved changes"
                } else {
                    current.saveStatus
                }
            )
        }
    }

    private fun updateRoutineChildList(
        parentIndex: Int,
        elseBranch: Boolean,
        transform: (List<RoutineStep>) -> List<RoutineStep>
    ) {
        updateRoutine { routine ->
            if (parentIndex !in routine.steps.indices) return@updateRoutine routine
            val parent = routine.steps[parentIndex]
            val updatedParent = if (elseBranch) {
                parent.copy(elseChildren = transform(parent.elseChildren))
            } else {
                parent.copy(children = transform(parent.children))
            }
            routine.copy(steps = routine.steps.toMutableList().apply { this[parentIndex] = updatedParent })
        }
        recalculateRoutinePreview()
    }

    private fun setAutonomousAvailability(enabled: Boolean, league: League) {
        _state.update { current ->
            val entry = if (enabled) {
                current.autonomousEntry ?: AutonomousCatalogEntry(
                    entryId = current.routine.documentId,
                    displayName = current.routine.name,
                    routineId = current.routine.documentId,
                    startingPose = clampRoutinePose(
                        current.routine.steps.firstOrNull()?.drive?.target ?: RoutinePose(0.0, 0.0, 0.0),
                        league,
                        current.robotDimensions
                    ),
                    authoredAlliance = RoutineAlliance.RED,
                    mirrorForOppositeAlliance = true
                )
            } else {
                null
            }
            current.copy(
                availableInAutonomousSelector = enabled,
                autonomousEntry = entry,
                routineValidation = routineEditorValidation(
                    current.routine,
                    current.capabilityCatalog,
                    current.availableRoutines,
                    league,
                    current.robotDimensions,
                    entry
                ),
                saveStatus = "Unsaved autonomous selector change"
            )
        }
        recalculateRoutinePreview()
    }

    private suspend fun refreshRoutineProject(projectPath: String?, league: League) {
        if (projectPath.isNullOrBlank()) {
            _state.update {
                it.copy(
                    capabilityStatus = "Select a project to load offline robot actions and routines",
                    availableRoutines = emptyList(),
                    routineActions = emptyList(),
                    routineConditions = emptyList(),
                    legacyRoutineFiles = emptyList()
                )
            }
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
                val routines = routineRepository.list(projectPath)
                val catalog = capabilityRepository.load(projectPath).getOrNull()
                val autonomous = autonomousRepository.load(projectPath).getOrNull()
                val legacy = routineRepository.listLegacyAutos(projectPath, league)
                val metadata = metadataRepository.load(projectPath).getOrNull()
                RoutineRefresh(routines.documents, routines.diagnostics.map { it.message }, catalog, autonomous, legacy.documents, metadata)
            }
        }.onSuccess { refresh ->
            val currentEntry = refresh.autonomous?.entries?.firstOrNull {
                it.routineId == _state.value.routine.documentId
            }
            val catalog = refresh.catalog
            val effectiveLeague = refresh.metadata?.league?.toAnalyticsLeague() ?: league
            val effectiveDimensions = refresh.metadata?.let {
                RobotDimensions(it.robotLengthMeters, it.robotWidthMeters)
            } ?: _state.value.robotDimensions
            _state.update { current ->
                current.copy(
                    availableRoutines = refresh.routines,
                    capabilityCatalog = catalog,
                    routineActions = catalog?.actions
                        ?.filter { CapabilityContext.AUTONOMOUS in it.allowedContexts || CapabilityContext.TELEOP in it.allowedContexts }
                        .orEmpty(),
                    routineConditions = catalog?.conditions.orEmpty(),
                    autonomousCatalog = refresh.autonomous,
                    autonomousEntry = currentEntry,
                    availableInAutonomousSelector = currentEntry != null,
                    legacyRoutineFiles = refresh.legacyFiles,
                    projectMetadata = refresh.metadata,
                    activeLeague = effectiveLeague,
                    robotDimensions = effectiveDimensions,
                    capabilityStatus = when {
                        refresh.diagnostics.isNotEmpty() -> refresh.diagnostics.first()
                        catalog == null -> "No generated action catalog yet; motion, waits, calls, and groups remain available offline"
                        catalog.actions.isEmpty() -> "This project declares no robot actions"
                        else -> "${catalog.actions.size} actions and ${catalog.conditions.size} conditions loaded from the project"
                    },
                    routineValidation = routineEditorValidation(
                        current.routine,
                        catalog,
                        refresh.routines,
                        effectiveLeague,
                        effectiveDimensions,
                        currentEntry
                    )
                )
            }
        }.onFailure { error ->
            _state.update { it.copy(capabilityStatus = "Could not read project documents: ${error.message}") }
        }
    }

    private suspend fun loadRoutine(projectPath: String?, documentId: String) {
        if (projectPath.isNullOrBlank()) return
        runCatching {
            withContext(Dispatchers.IO) {
                val routine = routineRepository.load(projectPath, documentId)
                val revisions = routineRepository.listRevisions(projectPath, documentId)
                val autonomous = autonomousRepository.load(projectPath).getOrNull()
                Triple(routine, revisions, autonomous)
            }
        }.onSuccess { (routine, revisions, autonomous) ->
            val entry = autonomous?.entries?.firstOrNull { it.routineId == routine.documentId }
            _state.update { current ->
                current.copy(
                    pathName = routine.name,
                    routine = routine,
                    routineRevisions = revisions,
                    autonomousCatalog = autonomous,
                    autonomousEntry = entry,
                    availableInAutonomousSelector = entry != null,
                    routineValidation = routineEditorValidation(
                        routine,
                        current.capabilityCatalog,
                        current.availableRoutines,
                        current.activeLeague,
                        current.robotDimensions,
                        entry
                    ),
                    saveStatus = "Loaded ${routine.name} revision ${routine.revision}"
                )
            }
            recalculateRoutinePreview()
        }.onFailure { error ->
            _state.update { it.copy(saveStatus = "Could not load routine: ${error.message}") }
        }
    }

    private suspend fun saveRoutine(projectPath: String?): Boolean {
        if (projectPath.isNullOrBlank()) {
            _state.update { it.copy(saveStatus = "Select a robot project before saving") }
            return false
        }
        val current = _state.value
        if (current.routineValidation.any { it.severity == RoutineValidationSeverity.ERROR }) {
            _state.update { it.copy(saveStatus = "Fix the highlighted routine issues before saving") }
            return false
        }
        var savedSuccessfully = false
        runCatching {
            withContext(Dispatchers.IO) {
                val saved = routineRepository.save(projectPath, current.routine)
                val oldCatalog = autonomousRepository.load(projectPath).getOrNull()
                val entry = current.autonomousEntry?.copy(routineId = saved.document.documentId)
                val entries = oldCatalog?.entries.orEmpty()
                    .filterNot { it.routineId == saved.document.documentId || it.entryId == entry?.entryId }
                    .let { remaining -> if (entry == null) remaining else remaining + entry }
                val projectId = oldCatalog?.projectId ?: safeAutoDocumentId(File(projectPath).name)
                val defaultEntryId = oldCatalog?.defaultEntryId?.takeIf { id -> entries.any { it.entryId == id && it.enabled } }
                    ?: entries.firstOrNull { it.enabled }?.entryId
                val catalogDraft = AutonomousCatalogDocument(
                    projectId = projectId,
                    revision = oldCatalog?.revision ?: 1,
                    defaultEntryId = defaultEntryId,
                    entries = entries
                )
                val savedCatalog = autonomousRepository.save(projectPath, catalogDraft)
                RoutineSave(saved.document, saved.createdRevision, savedCatalog.document)
            }
        }.onSuccess { saved ->
            savedSuccessfully = true
            val revisions = withContext(Dispatchers.IO) {
                routineRepository.listRevisions(projectPath, saved.routine.documentId)
            }
            val entry = saved.autonomous.entries.firstOrNull { it.routineId == saved.routine.documentId }
            _state.update { state ->
                state.copy(
                    pathName = saved.routine.name,
                    routine = saved.routine,
                    routineRevisions = revisions,
                    autonomousCatalog = saved.autonomous,
                    autonomousEntry = entry,
                    availableInAutonomousSelector = entry != null,
                    saveStatus = if (saved.createdRevision) {
                        "Saved routine revision ${saved.routine.revision}"
                    } else {
                        "Already up to date at revision ${saved.routine.revision}"
                    }
                )
            }
            refreshRoutineProject(projectPath, _state.value.activeLeague)
        }.onFailure { error ->
            _state.update { it.copy(saveStatus = "Routine save failed: ${error.message}") }
        }
        return savedSuccessfully
    }

    private suspend fun restoreRoutine(projectPath: String?, contentHash: String) {
        if (projectPath.isNullOrBlank()) return
        val documentId = _state.value.routine.documentId
        runCatching {
            withContext(Dispatchers.IO) {
                routineRepository.restore(projectPath, documentId, contentHash)
            }
        }.onSuccess { restored ->
            _state.update { current ->
                current.copy(
                    routine = restored.document,
                    routineRevisions = routineRepository.listRevisions(projectPath, documentId),
                    routineValidation = routineEditorValidation(
                        restored.document,
                        current.capabilityCatalog,
                        current.availableRoutines,
                        current.activeLeague,
                        current.robotDimensions,
                        current.autonomousEntry
                    ),
                    saveStatus = "Restored as revision ${restored.document.revision}"
                )
            }
            recalculateRoutinePreview()
        }.onFailure { error ->
            _state.update { it.copy(saveStatus = "Restore failed: ${error.message}") }
        }
    }

    private suspend fun importLegacyRoutine(projectPath: String?, league: League, file: File) {
        if (projectPath.isNullOrBlank()) return
        runCatching {
            withContext(Dispatchers.IO) { routineRepository.importLegacyAuto(projectPath, file) }
        }.onSuccess { imported ->
            val entryPoint = imported.autonomousEntryPoint
            _state.update { current ->
                val entry = entryPoint?.let {
                    AutonomousCatalogEntry(
                        entryId = imported.saved.document.documentId,
                        displayName = imported.saved.document.name,
                        routineId = imported.saved.document.documentId,
                        startingPose = clampRoutinePose(it.startingPose, league, current.robotDimensions)
                    )
                }
                current.copy(
                    routine = imported.saved.document,
                    autonomousEntry = entry,
                    availableInAutonomousSelector = entry != null,
                    saveStatus = "Imported ${file.name}; save once to add it to the autonomous selector"
                )
            }
            refreshRoutineProject(projectPath, league)
            loadRoutine(projectPath, imported.saved.document.documentId)
        }.onFailure { error ->
            _state.update { it.copy(saveStatus = "Legacy import failed: ${error.message}") }
        }
    }

    private fun updateRoutineFieldWaypoints(waypoints: List<Waypoint>, league: League) {
        if (waypoints.isEmpty()) return
        val hasStart = _state.value.autonomousEntry != null
        val driveWaypoints = if (hasStart) waypoints.drop(1) else waypoints
        if (hasStart) {
            val start = waypoints.first()
            val pose = clampRoutinePose(start.toRoutinePose(), league, _state.value.robotDimensions)
            _state.update { current -> current.copy(autonomousEntry = current.autonomousEntry?.copy(startingPose = pose)) }
        }
        updateRoutine { routine ->
            routine.copy(
                steps = routine.steps.withRoutineRouteWaypoints(
                    driveWaypoints.iterator(),
                    league,
                    _state.value.robotDimensions
                )
            )
        }
    }

    private fun recalculateRoutinePreview() {
        val snapshot = _state.value
        val draft = snapshot.routine
        val drives = draft.steps.routineDriveStepsInExecutionOrder()
        val previewStart = snapshot.autonomousEntry?.startingPose ?: drives.firstOrNull()?.target
        scope.launch(Dispatchers.Default) {
            if (previewStart == null || drives.isEmpty()) {
                if (_state.value.routine == draft) {
                    _state.update { it.copy(trajectory = null, estimatedDuration = 0.0) }
                }
                return@launch
            }
            val driveModel = if (snapshot.activeLeague == League.FRC) DriveModel.SWERVE else DriveModel.MECANUM
            val constraints = snapshot.globalConstraints
            val limits = TrajectoryLimits(
                maxVelocityMps = constraints.maxVelocity.coerceAtLeast(0.1),
                maxAccelerationMps2 = constraints.maxAcceleration.coerceAtLeast(0.1),
                maxJerkMps3 = (constraints.maxAcceleration * 4.0).coerceAtLeast(0.5),
                maxCentripetalAccelerationMps2 = constraints.maxAcceleration.coerceAtLeast(0.1),
                maxAngularVelocityRps = Math.toRadians(constraints.maxAngularVelocity).coerceAtLeast(0.1),
                maxAngularAccelerationRps2 = Math.toRadians(constraints.maxAngularAcceleration).coerceAtLeast(0.1)
            )
            var current = previewStart.toPose2d()
            var timeOffset = 0.0
            val previewStates = mutableListOf<TrajectoryState>()
            drives.forEachIndexed { driveIndex, drive ->
                // A neutral routine has no start pose. Treat its first drive target as the preview
                // anchor, rather than inventing match metadata that would change runtime behavior.
                if (snapshot.autonomousEntry == null && driveIndex == 0) return@forEachIndexed
                val target = drive.target.toPose2d()
                val preset = runCatching {
                    TrajectoryPreset.valueOf(drive.motionPresetKey.uppercase())
                }.getOrDefault(TrajectoryPreset.BALANCED)
                val generated = trajectoryPlanner.generate(
                    TrajectoryRequest(
                        waypoints = listOf(current, target),
                        driveModel = driveModel,
                        preset = preset,
                        limits = limits,
                        preferredEngine = null
                    )
                ).trajectory ?: return@forEachIndexed
                generated.states.forEachIndexed { index, sample ->
                    if (previewStates.isNotEmpty() && index == 0) return@forEachIndexed
                    previewStates += TrajectoryState(
                        timeSeconds = sample.timeSeconds + timeOffset,
                        x = sample.pose.x,
                        y = sample.pose.y,
                        headingRad = sample.pose.heading.radians,
                        velocity = kotlin.math.hypot(sample.velocityXMps, sample.velocityYMps)
                    )
                }
                timeOffset += generated.durationSeconds
                current = target
            }
            if (_state.value.routine == draft) {
                _state.update {
                    it.copy(
                        trajectory = previewStates.takeIf { states -> states.isNotEmpty() }
                            ?.let { states -> Trajectory(timeOffset, states) },
                        estimatedDuration = timeOffset
                    )
                }
            }
        }
    }

    private fun Waypoint.toRoutinePose(): RoutinePose = RoutinePose(
        xMeters = x,
        yMeters = y,
        headingRadians = rotationDeg?.let(Math::toRadians) ?: headingRad ?: 0.0
    )

    private fun RoutinePose.toPose2d(): Pose2d = Pose2d(xMeters, yMeters, Rotation2d(headingRadians))

    private data class RoutineRefresh(
        val routines: List<RoutineDocument>,
        val diagnostics: List<String>,
        val catalog: CapabilityCatalogDocument?,
        val autonomous: AutonomousCatalogDocument?,
        val legacyFiles: List<File>,
        val metadata: AresProjectMetadataDocument?
    )

    private fun AresLeague.toAnalyticsLeague(): League = when (this) {
        AresLeague.FTC -> League.FTC
        AresLeague.FRC -> League.FRC
    }

    private data class RoutineSave(
        val routine: RoutineDocument,
        val createdRevision: Boolean,
        val autonomous: AutonomousCatalogDocument
    )

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

    private fun updateAresAuto(transform: (AutoRoutine) -> AutoRoutine) {
        _state.update { state ->
            val updated = transform(state.aresAuto)
            state.copy(
                pathName = updated.name,
                aresAuto = updated,
                aresAutoValidation = validateAutoRoutine(updated) +
                    validateAutoFieldBounds(updated, state.activeLeague, state.robotDimensions),
                saveStatus = if (state.saveStatus.startsWith("Saved")) "Unsaved changes" else state.saveStatus
            )
        }
    }

    private fun updateAresRouteWaypoints(waypoints: List<Waypoint>, league: League) {
        if (waypoints.isEmpty()) return
        updateAresAuto { routine ->
            val routeIterator = waypoints.drop(1).iterator()
            val updatedSteps = routine.steps.withRouteWaypoints(
                routeIterator,
                league,
                _state.value.robotDimensions
            )
            val start = waypoints.first()
            routine.copy(
                startingPose = clampAutoPose(AutoPose(
                    start.x,
                    start.y,
                    start.rotationDeg?.let(Math::toRadians) ?: start.headingRad ?: 0.0
                ), league, _state.value.robotDimensions),
                steps = updatedSteps
            )
        }
    }

    private suspend fun refreshAresAutos(projectPath: String?, league: League) {
        if (projectPath.isNullOrBlank()) return
        val (autos, scan) = withContext(Dispatchers.IO) {
            aresAutoRepository.listAutos(projectPath, league) to
                autoCapabilityScanner.scan(projectPath, league)
        }
        projectCommandCatalog = scan.catalog
        _state.update {
            it.copy(
                availableAresAutos = autos,
                capabilityStatus = scan.warnings.firstOrNull() ?: it.capabilityStatus
            )
        }
        updateMergedCommandCatalog(scan.warnings)
    }

    private suspend fun loadAresAuto(projectPath: String?, league: League, documentId: String) {
        if (projectPath.isNullOrBlank()) return
        runCatching {
            withContext(Dispatchers.IO) {
                val routine = aresAutoRepository.load(projectPath, league, documentId)
                val revisions = aresAutoRepository.listRevisions(projectPath, documentId)
                routine to revisions
            }
        }.onSuccess { (routine, revisions) ->
            _state.update {
                it.copy(
                    pathName = routine.name,
                    aresAuto = routine,
                    aresAutoValidation = validateAutoRoutine(routine) +
                        validateAutoFieldBounds(routine, it.activeLeague, it.robotDimensions),
                    aresAutoRevisions = revisions,
                    saveStatus = "Loaded ${routine.name} revision ${routine.revision}"
                )
            }
            recalculateAresPreview(league)
        }.onFailure { error ->
            _state.update { it.copy(saveStatus = "Could not load auto: ${error.message}") }
        }
    }

    private suspend fun saveAresAuto(projectPath: String?, league: League) {
        if (projectPath.isNullOrBlank()) {
            _state.update { it.copy(saveStatus = "Select a robot project before saving") }
            return
        }
        val draft = _state.value.aresAuto
        if (_state.value.aresAutoValidation.any { it.severity == com.areslib.auto.AutoValidationSeverity.ERROR }) {
            _state.update { it.copy(saveStatus = "Fix the highlighted auto issues before saving") }
            return
        }
        runCatching {
            withContext(Dispatchers.IO) { aresAutoRepository.save(projectPath, league, draft) }
        }.onSuccess { saved ->
            if (league == League.FTC) {
                serializationManager.pushFileToRobot(
                    saved.currentFile,
                    "/sdcard/FIRST/ares/autos",
                    saved.currentFile.name
                )
            }
            val revisions = withContext(Dispatchers.IO) {
                aresAutoRepository.listRevisions(projectPath, saved.routine.documentId)
            }
            _state.update {
                it.copy(
                    pathName = saved.routine.name,
                    aresAuto = saved.routine,
                    aresAutoValidation = validateAutoRoutine(saved.routine) +
                        validateAutoFieldBounds(saved.routine, it.activeLeague, it.robotDimensions),
                    aresAutoRevisions = revisions,
                    saveStatus = if (saved.createdRevision) {
                        "Saved revision ${saved.routine.revision}"
                    } else {
                        "Already up to date at revision ${saved.routine.revision}"
                    }
                )
            }
            refreshAresAutos(projectPath, league)
        }.onFailure { error ->
            _state.update { it.copy(saveStatus = "Auto save failed: ${error.message}") }
        }
    }

    private suspend fun restoreAresAuto(projectPath: String?, league: League, contentHash: String) {
        if (projectPath.isNullOrBlank()) return
        val documentId = _state.value.aresAuto.documentId
        runCatching {
            withContext(Dispatchers.IO) {
                aresAutoRepository.restore(projectPath, league, documentId, contentHash)
            }
        }.onSuccess { restored ->
            _state.update {
                it.copy(
                    pathName = restored.routine.name,
                    aresAuto = restored.routine,
                    aresAutoValidation = validateAutoRoutine(restored.routine) +
                        validateAutoFieldBounds(restored.routine, it.activeLeague, it.robotDimensions),
                    aresAutoRevisions = aresAutoRepository.listRevisions(projectPath, documentId),
                    saveStatus = "Restored as revision ${restored.routine.revision}"
                )
            }
            recalculateAresPreview(league)
        }.onFailure { error ->
            _state.update { it.copy(saveStatus = "Restore failed: ${error.message}") }
        }
    }

    private fun recalculateAresPreview(league: League) {
        val draft = _state.value.aresAuto
        scope.launch(Dispatchers.Default) {
            val driveModel = if (league == League.FRC) DriveModel.SWERVE else DriveModel.MECANUM
            val constraints = _state.value.globalConstraints
            val limits = TrajectoryLimits(
                maxVelocityMps = constraints.maxVelocity.coerceAtLeast(0.1),
                maxAccelerationMps2 = constraints.maxAcceleration.coerceAtLeast(0.1),
                maxJerkMps3 = (constraints.maxAcceleration * 4.0).coerceAtLeast(0.5),
                maxCentripetalAccelerationMps2 = constraints.maxAcceleration.coerceAtLeast(0.1),
                maxAngularVelocityRps = Math.toRadians(constraints.maxAngularVelocity).coerceAtLeast(0.1),
                maxAngularAccelerationRps2 = Math.toRadians(constraints.maxAngularAcceleration).coerceAtLeast(0.1)
            )
            var current = Pose2d(
                draft.startingPose.xMeters,
                draft.startingPose.yMeters,
                Rotation2d(draft.startingPose.headingRadians)
            )
            var timeOffset = 0.0
            val previewStates = mutableListOf<TrajectoryState>()
            draft.steps.driveStepsInExecutionOrder().forEach { drive ->
                val target = Pose2d(
                    drive.target.xMeters,
                    drive.target.yMeters,
                    Rotation2d(drive.target.headingRadians)
                )
                val generated = trajectoryPlanner.generate(
                    TrajectoryRequest(
                        waypoints = listOf(current, target),
                        driveModel = driveModel,
                        preset = drive.preset,
                        limits = limits,
                        preferredEngine = null
                    )
                ).trajectory ?: return@forEach
                generated.states.forEachIndexed { index, sample ->
                    if (previewStates.isNotEmpty() && index == 0) return@forEachIndexed
                    previewStates += TrajectoryState(
                        timeSeconds = sample.timeSeconds + timeOffset,
                        x = sample.pose.x,
                        y = sample.pose.y,
                        headingRad = sample.pose.heading.radians,
                        velocity = kotlin.math.hypot(sample.velocityXMps, sample.velocityYMps)
                    )
                }
                timeOffset += generated.durationSeconds
                current = target
            }
            if (_state.value.aresAuto == draft) {
                _state.update {
                    it.copy(
                        trajectory = if (previewStates.isNotEmpty()) {
                            Trajectory(timeOffset, previewStates)
                        } else {
                            null
                        },
                        estimatedDuration = timeOffset
                    )
                }
            }
        }
    }

    private fun List<AutoStep>.lastDriveTarget(): AutoPose? =
        asReversed().firstNotNullOfOrNull { step ->
            step.children.lastDriveTarget() ?: step.drive?.target
        }

    private fun List<AutoStep>.driveStepsInExecutionOrder(): List<AutoDriveStep> = buildList {
        this@driveStepsInExecutionOrder.forEach { step ->
            step.drive?.let(::add)
            addAll(step.children.driveStepsInExecutionOrder())
        }
    }

    private fun List<AutoStep>.withRouteWaypoints(
        waypoints: Iterator<Waypoint>,
        league: League,
        dimensions: RobotDimensions
    ): List<AutoStep> = map { step ->
        val updatedDrive = step.drive?.let { drive ->
            if (!waypoints.hasNext()) return@let drive
            val waypoint = waypoints.next()
            drive.copy(
                target = clampAutoPose(
                    AutoPose(
                        waypoint.x,
                        waypoint.y,
                        waypoint.rotationDeg?.let(Math::toRadians) ?: waypoint.headingRad ?: 0.0
                    ),
                    league,
                    dimensions
                )
            )
        }
        step.copy(
            drive = updatedDrive,
            children = step.children.withRouteWaypoints(waypoints, league, dimensions)
        )
    }

    private fun parseCommandCatalog(json: String?): List<NamedCommandDescriptor>? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val entries = Json.parseToJsonElement(json).jsonArray
            entries.mapNotNull { element ->
                val item = element.jsonObject
                val key = item["key"]?.jsonPrimitive?.content ?: return@mapNotNull null
                NamedCommandDescriptor(
                    key = CommandKey(key),
                    displayName = item["displayName"]?.jsonPrimitive?.content ?: key,
                    description = item["description"]?.jsonPrimitive?.content ?: "Robot action",
                    category = item["category"]?.jsonPrimitive?.content ?: "General"
                )
            }.sortedWith(compareBy<NamedCommandDescriptor> { it.category }.thenBy { it.displayName })
        }.getOrNull()
    }

    private fun updateMergedCommandCatalog(scanWarnings: List<String> = emptyList()) {
        val merged = linkedMapOf<String, NamedCommandDescriptor>()
        projectCommandCatalog.forEach { merged[it.key.value] = it }
        liveCommandCatalog.forEach { live -> merged.putIfAbsent(live.key.value, live) }
        val missingOnLiveRobot = if (liveCommandCatalog.isEmpty()) {
            emptySet()
        } else {
            projectCommandCatalog.map { it.key.value }.toSet() - liveCommandCatalog.map { it.key.value }.toSet()
        }
        val status = when {
            scanWarnings.isNotEmpty() -> scanWarnings.first()
            projectCommandCatalog.isEmpty() && liveCommandCatalog.isEmpty() ->
                "No auto actions are declared yet; drive goals and waits remain available offline"
            projectCommandCatalog.isNotEmpty() && liveCommandCatalog.isEmpty() ->
                "${projectCommandCatalog.size} project actions available offline"
            missingOnLiveRobot.isNotEmpty() ->
                "Project catalog loaded; connected build is missing ${missingOnLiveRobot.size} action(s)"
            else -> "Project actions verified against the connected build"
        }
        _state.update {
            it.copy(
                commandCatalog = merged.values.sortedWith(
                    compareBy<NamedCommandDescriptor> { descriptor -> descriptor.category }
                        .thenBy { descriptor -> descriptor.displayName }
                ),
                capabilityStatus = status
            )
        }
    }

    private fun isModifyingIntent(intent: PathPlannerIntent): Boolean {
        return intent is PathPlannerIntent.UpdateWaypoints ||
               intent is PathPlannerIntent.UpdateWaypoint ||
               intent is PathPlannerIntent.AddWaypoint ||
               intent is PathPlannerIntent.DeleteWaypoint ||
               intent is PathPlannerIntent.OptimizePath ||
               intent is PathPlannerIntent.AddEventMarker ||
               intent is PathPlannerIntent.DeleteEventMarker ||
               intent is PathPlannerIntent.UpdateAresStartingPose ||
               intent is PathPlannerIntent.AddAresDriveGoal ||
               intent is PathPlannerIntent.AddAresCommand ||
               intent is PathPlannerIntent.AddAresWait ||
               intent is PathPlannerIntent.UpdateAresStep ||
               intent is PathPlannerIntent.RemoveAresStep ||
               intent is PathPlannerIntent.MoveAresStep ||
               intent is PathPlannerIntent.UpdateAresRouteWaypoints ||
               intent is PathPlannerIntent.UpdateRoutineName ||
               intent is PathPlannerIntent.UpdateRoutineDescription ||
               intent is PathPlannerIntent.AddRoutineStep ||
               intent is PathPlannerIntent.UpdateRoutineStep ||
               intent is PathPlannerIntent.RemoveRoutineStep ||
               intent is PathPlannerIntent.MoveRoutineStep ||
               intent is PathPlannerIntent.AddRoutineChild ||
               intent is PathPlannerIntent.UpdateRoutineChild ||
               intent is PathPlannerIntent.RemoveRoutineChild ||
               intent is PathPlannerIntent.SetAutonomousAvailability ||
               intent is PathPlannerIntent.UpdateAutonomousEntry ||
               intent is PathPlannerIntent.UpdateRoutineFieldWaypoints
    }
}
