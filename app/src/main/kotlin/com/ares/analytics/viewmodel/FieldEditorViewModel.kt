package com.ares.analytics.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.ares.analytics.shared.*
import com.ares.analytics.util.ProjectLayout
import com.ares.analytics.viewmodel.field.FieldDocumentMapper
import com.ares.analytics.viewmodel.field.FieldDocumentStore
import com.areslib.state.RobotFieldConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Immutable editor state backed by one canonical, revisioned field document. */
data class FieldEditorState(
    val document: RobotFieldConfig? = null,
    val fieldImage: ImageBitmap? = null,
    val fieldImageConfig: FieldImageConfig = FieldImageConfig(),
    val obstacles: List<Obstacle> = emptyList(),
    val gamePieces: List<GamePiece> = emptyList(),
    val aprilTags: List<AprilTagPlacement> = emptyList(),
    val fieldWaypoints: List<FieldWaypoint> = emptyList(),
    val saveStatus: String = "",
    val selectedElement: String? = null,
    val isLoading: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null
)

sealed class FieldEditorIntent {
    data class LoadConfig(val projectPath: String?, val league: League) : FieldEditorIntent()
    object SaveDocument : FieldEditorIntent()
    data class ImportFieldImage(val imageFile: File, val projectPath: String?, val league: League) : FieldEditorIntent()
    data class UpdateFieldImageConfig(val config: FieldImageConfig, val projectPath: String?, val league: League) : FieldEditorIntent()
    data class AddObstacle(val obstacle: Obstacle) : FieldEditorIntent()
    data class UpdateObstacle(val index: Int, val obstacle: Obstacle) : FieldEditorIntent()
    data class DeleteObstacle(val index: Int) : FieldEditorIntent()
    data class AddGamePiece(val piece: GamePiece) : FieldEditorIntent()
    data class UpdateGamePiece(val index: Int, val piece: GamePiece) : FieldEditorIntent()
    data class DeleteGamePiece(val index: Int) : FieldEditorIntent()
    data class AddAprilTag(val tag: AprilTagPlacement) : FieldEditorIntent()
    data class UpdateAprilTag(val index: Int, val tag: AprilTagPlacement) : FieldEditorIntent()
    data class DeleteAprilTag(val index: Int) : FieldEditorIntent()
    data class AddFieldWaypoint(val waypoint: FieldWaypoint) : FieldEditorIntent()
    data class UpdateFieldWaypoint(val index: Int, val waypoint: FieldWaypoint) : FieldEditorIntent()
    data class DeleteFieldWaypoint(val index: Int) : FieldEditorIntent()
    data class SelectElement(val elementId: String?) : FieldEditorIntent()
    object ClearSaveStatus : FieldEditorIntent()
    data class SetObstacles(val obstacles: List<Obstacle>) : FieldEditorIntent()
    data class SetGamePieces(val gamePieces: List<GamePiece>) : FieldEditorIntent()
    data class SetAprilTags(val tags: List<AprilTagPlacement>) : FieldEditorIntent()
    data class SetFieldWaypoints(val waypoints: List<FieldWaypoint>) : FieldEditorIntent()
    data class ImportFmap(val fmapContent: String, val projectPath: String?, val league: League) : FieldEditorIntent()
}

/**
 * Single owner for field editor state and persistence.
 *
 * Edits are applied synchronously, folded into a new canonical revision, and atomically persisted
 * after a short debounce. Legacy JSON files are compatibility outputs produced by the same save.
 */
class FieldEditorViewModel(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(FieldEditorState())
    val state: StateFlow<FieldEditorState> = _state.asStateFlow()

    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var loadGeneration = 0L
    private var activeProjectPath: String? = null
    private var activeLeague: League = League.FTC

    fun onIntent(intent: FieldEditorIntent) {
        when (intent) {
            is FieldEditorIntent.LoadConfig -> load(intent.projectPath, intent.league)
            is FieldEditorIntent.ImportFieldImage -> importFieldImage(intent)
            is FieldEditorIntent.ImportFmap -> importFmap(intent)
            is FieldEditorIntent.UpdateFieldImageConfig -> {
                activeProjectPath = intent.projectPath ?: activeProjectPath
                activeLeague = intent.league
                applyEdit { it.copy(fieldImageConfig = intent.config) }
            }
            is FieldEditorIntent.AddObstacle -> applyEdit { it.copy(obstacles = it.obstacles + intent.obstacle) }
            is FieldEditorIntent.UpdateObstacle -> applyEdit { state ->
                state.copy(obstacles = state.obstacles.mapIndexed { index, value -> if (index == intent.index) intent.obstacle else value })
            }
            is FieldEditorIntent.DeleteObstacle -> applyEdit { state ->
                state.copy(obstacles = state.obstacles.filterIndexed { index, _ -> index != intent.index })
            }
            is FieldEditorIntent.AddGamePiece -> applyEdit { it.copy(gamePieces = it.gamePieces + intent.piece) }
            is FieldEditorIntent.UpdateGamePiece -> applyEdit { state ->
                state.copy(gamePieces = state.gamePieces.mapIndexed { index, value -> if (index == intent.index) intent.piece else value })
            }
            is FieldEditorIntent.DeleteGamePiece -> applyEdit { state ->
                state.copy(gamePieces = state.gamePieces.filterIndexed { index, _ -> index != intent.index })
            }
            is FieldEditorIntent.AddAprilTag -> applyEdit { it.copy(aprilTags = it.aprilTags + intent.tag) }
            is FieldEditorIntent.UpdateAprilTag -> applyEdit { state ->
                state.copy(aprilTags = state.aprilTags.mapIndexed { index, value -> if (index == intent.index) intent.tag else value })
            }
            is FieldEditorIntent.DeleteAprilTag -> applyEdit { state ->
                state.copy(aprilTags = state.aprilTags.filterIndexed { index, _ -> index != intent.index })
            }
            is FieldEditorIntent.AddFieldWaypoint -> applyEdit { it.copy(fieldWaypoints = it.fieldWaypoints + intent.waypoint) }
            is FieldEditorIntent.UpdateFieldWaypoint -> applyEdit { state ->
                state.copy(fieldWaypoints = state.fieldWaypoints.mapIndexed { index, value -> if (index == intent.index) intent.waypoint else value })
            }
            is FieldEditorIntent.DeleteFieldWaypoint -> applyEdit { state ->
                state.copy(fieldWaypoints = state.fieldWaypoints.filterIndexed { index, _ -> index != intent.index })
            }
            is FieldEditorIntent.SetObstacles -> applyEdit { it.copy(obstacles = intent.obstacles) }
            is FieldEditorIntent.SetGamePieces -> applyEdit { it.copy(gamePieces = intent.gamePieces) }
            is FieldEditorIntent.SetAprilTags -> applyEdit { it.copy(aprilTags = intent.tags) }
            is FieldEditorIntent.SetFieldWaypoints -> applyEdit { it.copy(fieldWaypoints = intent.waypoints) }
            is FieldEditorIntent.SelectElement -> _state.update { it.copy(selectedElement = intent.elementId) }
            FieldEditorIntent.ClearSaveStatus -> _state.update { it.copy(saveStatus = "") }
            FieldEditorIntent.SaveDocument -> saveImmediately()
        }
    }

    private fun load(projectPath: String?, league: League) {
        activeProjectPath = projectPath
        activeLeague = league
        saveJob?.cancel()
        val generation = ++loadGeneration
        _state.update { it.copy(isLoading = true, errorMessage = null, saveStatus = "") }
        scope.launch {
            try {
                if (projectPath.isNullOrBlank()) {
                    val imageConfig = FieldDocumentMapper.defaultImageConfig(league)
                    val document = FieldDocumentMapper.newDocument(league, imageConfig)
                    if (generation == loadGeneration) {
                        _state.value = FieldEditorState(document = document, fieldImageConfig = imageConfig)
                    }
                    return@launch
                }

                val loaded = withContext(Dispatchers.IO) { FieldDocumentStore.load(projectPath, league) }
                val bitmap = withContext(Dispatchers.IO) {
                    val imageFile = File(ProjectLayout.assetsDirectory(projectPath, league), "field_image.png")
                    if (imageFile.isFile) {
                        org.jetbrains.skia.Image.makeFromEncoded(imageFile.readBytes()).toComposeImageBitmap()
                    } else null
                }
                if (generation == loadGeneration) {
                    _state.value = FieldEditorState(
                        document = loaded.document,
                        fieldImage = bitmap,
                        fieldImageConfig = loaded.imageConfig,
                        obstacles = loaded.obstacles,
                        gamePieces = loaded.gamePieces,
                        aprilTags = loaded.aprilTags,
                        fieldWaypoints = loaded.fieldWaypoints,
                        isLoading = false,
                        isDirty = false
                    )
                }
            } catch (error: Exception) {
                if (generation == loadGeneration) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load field layout")
                    }
                }
            }
        }
    }

    private fun applyEdit(transform: (FieldEditorState) -> FieldEditorState) {
        val transformed = transform(_state.value)
        val base = transformed.document ?: FieldDocumentMapper.newDocument(activeLeague, transformed.fieldImageConfig)
        val document = FieldDocumentMapper.withEditorData(
            base = base,
            league = activeLeague,
            image = transformed.fieldImageConfig,
            obstacles = transformed.obstacles,
            gamePieces = transformed.gamePieces,
            aprilTags = transformed.aprilTags,
            fieldWaypoints = transformed.fieldWaypoints
        )
        _state.value = transformed.copy(document = document, isDirty = true, saveStatus = "Unsaved changes")
        scheduleSave(document)
    }

    private fun scheduleSave(document: RobotFieldConfig) {
        val projectPath = activeProjectPath?.takeIf(String::isNotBlank) ?: return
        val league = activeLeague
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persistSnapshot(projectPath, league, document)
        }
    }

    private fun saveImmediately() {
        val document = _state.value.document ?: return
        val projectPath = activeProjectPath?.takeIf(String::isNotBlank) ?: return
        val league = activeLeague
        saveJob?.cancel()
        saveJob = scope.launch { persistSnapshot(projectPath, league, document) }
    }

    private suspend fun persistSnapshot(projectPath: String, league: League, document: RobotFieldConfig) {
        saveMutex.withLock {
            if (_state.value.document?.revision != document.revision) return@withLock
            _state.update { it.copy(saveStatus = "Saving…") }
            try {
                withContext(Dispatchers.IO) { FieldDocumentStore.save(projectPath, league, document) }
                if (_state.value.document?.revision == document.revision) {
                    _state.update { it.copy(isDirty = false, saveStatus = "Saved field revision ${document.revision}") }
                }
            } catch (error: Exception) {
                if (_state.value.document?.revision == document.revision) {
                    _state.update { it.copy(saveStatus = "Failed to save field: ${error.message}") }
                }
            }
        }
    }

    private fun importFieldImage(intent: FieldEditorIntent.ImportFieldImage) {
        val projectPath = intent.projectPath?.takeIf(String::isNotBlank) ?: return
        activeProjectPath = projectPath
        activeLeague = intent.league
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val target = File(ProjectLayout.assetsDirectory(projectPath, intent.league), "field_image.png")
                    target.parentFile?.mkdirs()
                    val temporary = File(target.parentFile, ".field_image.png.tmp")
                    try {
                        Files.copy(intent.imageFile.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } finally {
                        if (temporary.exists()) temporary.delete()
                    }
                    org.jetbrains.skia.Image.makeFromEncoded(target.readBytes()).toComposeImageBitmap()
                }
                applyEdit {
                    it.copy(
                        fieldImage = bitmap,
                        fieldImageConfig = it.fieldImageConfig.copy(imagePath = "field_image.png")
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(saveStatus = "Failed to import field image: ${error.message}") }
            }
        }
    }

    private fun importFmap(intent: FieldEditorIntent.ImportFmap) {
        activeProjectPath = intent.projectPath ?: activeProjectPath
        activeLeague = intent.league
        try {
            val fmap = AppJson.decodeFromString<LimelightFmap>(intent.fmapContent)
            val placements = fmap.fiducials.mapNotNull { fiducial ->
                if (fiducial.transform.size < 16) return@mapNotNull null
                val transform = fiducial.transform
                AprilTagPlacement(
                    id = "apriltag_${fiducial.id}",
                    tagId = fiducial.id,
                    x = transform[3],
                    y = transform[7],
                    z = transform[11],
                    yawDegrees = Math.toDegrees(kotlin.math.atan2(transform[4], transform[0]))
                )
            }
            applyEdit { it.copy(aprilTags = placements) }
        } catch (error: Exception) {
            _state.update { it.copy(saveStatus = "Failed to parse fmap: ${error.message}") }
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 350L
    }
}

@Serializable
private data class LimelightFiducial(
    val id: Int = 0,
    val family: String? = null,
    val size: Double = 0.0,
    val transform: List<Double> = emptyList()
)

@Serializable
private data class LimelightFmap(val fiducials: List<LimelightFiducial> = emptyList())
