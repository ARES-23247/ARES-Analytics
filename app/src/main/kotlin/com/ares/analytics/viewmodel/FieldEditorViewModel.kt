package com.ares.analytics.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.ares.analytics.shared.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class FieldEditorState(
    val fieldImage: ImageBitmap? = null,
    val fieldImageConfig: FieldImageConfig = FieldImageConfig(),
    val obstacles: List<Obstacle> = emptyList(),
    val gamePieces: List<GamePiece> = emptyList(),
    val aprilTags: List<AprilTagPlacement> = emptyList(),
    val fieldWaypoints: List<FieldWaypoint> = emptyList(),
    val saveStatus: String = "",
    val selectedElement: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class FieldEditorIntent {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class LoadConfig(val projectPath: String?, val league: League) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SaveObstacles(val projectPath: String?, val league: League) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SaveGamePieces(val projectPath: String?, val league: League) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class ImportFieldImage(val imageFile: File, val projectPath: String?, val league: League) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class UpdateFieldImageConfig(val config: FieldImageConfig, val projectPath: String?, val league: League) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class AddObstacle(val obstacle: Obstacle) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class UpdateObstacle(val index: Int, val obstacle: Obstacle) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class DeleteObstacle(val index: Int) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class AddGamePiece(val piece: GamePiece) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class UpdateGamePiece(val index: Int, val piece: GamePiece) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class DeleteGamePiece(val index: Int) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SaveAprilTags(val projectPath: String?, val league: League) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class AddAprilTag(val tag: AprilTagPlacement) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class UpdateAprilTag(val index: Int, val tag: AprilTagPlacement) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class DeleteAprilTag(val index: Int) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SaveFieldWaypoints(val projectPath: String?, val league: League) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class AddFieldWaypoint(val waypoint: FieldWaypoint) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class UpdateFieldWaypoint(val index: Int, val waypoint: FieldWaypoint) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class DeleteFieldWaypoint(val index: Int) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SelectElement(val elementId: String?) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ClearSaveStatus : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetObstacles(val obstacles: List<Obstacle>) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetGamePieces(val gamePieces: List<GamePiece>) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetAprilTags(val tags: List<AprilTagPlacement>) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetFieldWaypoints(val waypoints: List<FieldWaypoint>) : FieldEditorIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class ImportFmap(val fmapContent: String, val projectPath: String?, val league: League) : FieldEditorIntent()
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class FieldEditorViewModel(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(FieldEditorState())
    val state: StateFlow<FieldEditorState> = _state.asStateFlow()

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun onIntent(intent: FieldEditorIntent) {
        scope.launch {
            when (intent) {
                is FieldEditorIntent.LoadConfig -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    _state.update { it.copy(isLoading = true, errorMessage = null) }
                    if (!projectPath.isNullOrEmpty()) {
                        try {
                            withContext(Dispatchers.IO) {
                                val relativePathsDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths"
                                    else "src/main/assets/paths"
                                } else {
                                    "src/main/deploy/paths"
                                }
                                val obsFile = File(File(projectPath, relativePathsDir), "obstacles.json")
                                val loadedObstacles = if (obsFile.exists()) {
                                    Json.decodeFromString<List<Obstacle>>(obsFile.readText())
                                } else {
                                    emptyList()
                                }
                                val gpFile = File(File(projectPath, relativePathsDir), "game_pieces.json")
                                val loadedGamePieces = if (gpFile.exists()) {
                                    Json.decodeFromString<List<GamePiece>>(gpFile.readText())
                                } else {
                                    emptyList()
                                }
                                val atFile = File(File(projectPath, relativePathsDir), "apriltags.json")
                                val loadedAprilTags = if (atFile.exists()) {
                                    Json.decodeFromString<List<AprilTagPlacement>>(atFile.readText())
                                } else {
                                    emptyList()
                                }
                                val wpFile = File(File(projectPath, relativePathsDir), "field_waypoints.json")
                                val loadedFieldWaypoints = if (wpFile.exists()) {
                                    Json.decodeFromString<List<FieldWaypoint>>(wpFile.readText())
                                } else {
                                    emptyList()
                                }
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets"
                                    else "src/main/assets"
                                } else {
                                    "src/main/deploy"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                val imgFile = File(targetDir, "field_image.png")
                                val loadedBitmap = if (imgFile.exists()) {
                                    org.jetbrains.skia.Image.makeFromEncoded(imgFile.readBytes()).toComposeImageBitmap()
                                } else {
                                    null
                                }
                                val configFile = File(targetDir, "field_image_config.json")
                                val loadedConfig = if (configFile.exists()) {
                                    Json.decodeFromString<FieldImageConfig>(configFile.readText())
                                } else {
                                    val defaultW = if (league == League.FTC) 3.65 else 16.5
                                    val defaultH = if (league == League.FTC) 3.65 else 8.2
                                    FieldImageConfig(widthMeters = defaultW, heightMeters = defaultH)
                                }

                                _state.update {
                                    it.copy(
                                        obstacles = loadedObstacles,
                                        gamePieces = loadedGamePieces,
                                        aprilTags = loadedAprilTags,
                                        fieldWaypoints = loadedFieldWaypoints,
                                        fieldImage = loadedBitmap,
                                        fieldImageConfig = loadedConfig,
                                        isLoading = false
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load field layout config") }
                        }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
                is FieldEditorIntent.SaveObstacles -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    val s = _state.value
                    if (!projectPath.isNullOrEmpty()) {
                        try {
                            withContext(Dispatchers.IO) {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths"
                                    else "src/main/assets/paths"
                                } else {
                                    "src/main/deploy/paths"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                targetDir.mkdirs()
                                val targetFile = File(targetDir, "obstacles.json")
                                val jsonFormat = Json { prettyPrint = true }
                                targetFile.writeText(jsonFormat.encodeToString(s.obstacles))
                            }
                            _state.update { it.copy(saveStatus = "Saved obstacles successfully!") }
                        } catch (e: Exception) {
                            _state.update { it.copy(saveStatus = "Failed to save obstacles: ${e.message}") }
                        }
                    }
                }
                is FieldEditorIntent.SaveGamePieces -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    val s = _state.value
                    if (!projectPath.isNullOrEmpty()) {
                        try {
                            withContext(Dispatchers.IO) {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths"
                                    else "src/main/assets/paths"
                                } else {
                                    "src/main/deploy/paths"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                targetDir.mkdirs()
                                val targetFile = File(targetDir, "game_pieces.json")
                                val jsonFormat = Json { prettyPrint = true }
                                targetFile.writeText(jsonFormat.encodeToString(s.gamePieces))
                            }
                            _state.update { it.copy(saveStatus = "Saved game pieces successfully!") }
                        } catch (e: Exception) {
                            _state.update { it.copy(saveStatus = "Failed to save game pieces: ${e.message}") }
                        }
                    }
                }
                is FieldEditorIntent.ImportFieldImage -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    if (!projectPath.isNullOrEmpty()) {
                        try {
                            withContext(Dispatchers.IO) {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets"
                                    else "src/main/assets"
                                } else {
                                    "src/main/deploy"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                targetDir.mkdirs()
                                val targetFile = File(targetDir, "field_image.png")
                                intent.imageFile.copyTo(targetFile, overwrite = true)
                                val bitmap = org.jetbrains.skia.Image.makeFromEncoded(targetFile.readBytes()).toComposeImageBitmap()
                                _state.update { it.copy(fieldImage = bitmap, saveStatus = "Field image imported successfully!") }
                            }
                        } catch (e: Exception) {
                            _state.update { it.copy(saveStatus = "Failed to import field image: ${e.message}") }
                        }
                    }
                }
                is FieldEditorIntent.UpdateFieldImageConfig -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    val newConfig = intent.config
                    _state.update { it.copy(fieldImageConfig = newConfig) }
                    if (!projectPath.isNullOrEmpty()) {
                        try {
                            withContext(Dispatchers.IO) {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets"
                                    else "src/main/assets"
                                } else {
                                    "src/main/deploy"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                targetDir.mkdirs()
                                val configFile = File(targetDir, "field_image_config.json")
                                val jsonFormat = Json { prettyPrint = true }
                                configFile.writeText(jsonFormat.encodeToString(newConfig))
                            }
                            _state.update { it.copy(saveStatus = "Field config updated successfully!") }
                        } catch (e: Exception) {
                            _state.update { it.copy(saveStatus = "Failed to save field config: ${e.message}") }
                        }
                    }
                }
                is FieldEditorIntent.AddObstacle -> {
                    val updated = _state.value.obstacles + intent.obstacle
                    _state.update { it.copy(obstacles = updated) }
                }
                is FieldEditorIntent.UpdateObstacle -> {
                    val updated = _state.value.obstacles.toMutableList().apply {
                        set(intent.index, intent.obstacle)
                    }
                    _state.update { it.copy(obstacles = updated) }
                }
                is FieldEditorIntent.DeleteObstacle -> {
                    val updated = _state.value.obstacles.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(obstacles = updated) }
                }
                is FieldEditorIntent.AddGamePiece -> {
                    val updated = _state.value.gamePieces + intent.piece
                    _state.update { it.copy(gamePieces = updated) }
                }
                is FieldEditorIntent.UpdateGamePiece -> {
                    val updated = _state.value.gamePieces.toMutableList().apply {
                        set(intent.index, intent.piece)
                    }
                    _state.update { it.copy(gamePieces = updated) }
                }
                is FieldEditorIntent.DeleteGamePiece -> {
                    val updated = _state.value.gamePieces.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(gamePieces = updated) }
                }
                is FieldEditorIntent.SaveAprilTags -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    val s = _state.value
                    if (!projectPath.isNullOrEmpty()) {
                        try {
                            withContext(Dispatchers.IO) {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths"
                                    else "src/main/assets/paths"
                                } else {
                                    "src/main/deploy/paths"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                targetDir.mkdirs()
                                val targetFile = File(targetDir, "apriltags.json")
                                val jsonFormat = Json { prettyPrint = true }
                                targetFile.writeText(jsonFormat.encodeToString(s.aprilTags))
                            }
                            _state.update { it.copy(saveStatus = "Saved AprilTags successfully!") }
                        } catch (e: Exception) {
                            _state.update { it.copy(saveStatus = "Failed to save AprilTags: ${e.message}") }
                        }
                    }
                }
                is FieldEditorIntent.AddAprilTag -> {
                    val updated = _state.value.aprilTags + intent.tag
                    _state.update { it.copy(aprilTags = updated) }
                }
                is FieldEditorIntent.UpdateAprilTag -> {
                    val updated = _state.value.aprilTags.toMutableList().apply {
                        set(intent.index, intent.tag)
                    }
                    _state.update { it.copy(aprilTags = updated) }
                }
                is FieldEditorIntent.DeleteAprilTag -> {
                    val updated = _state.value.aprilTags.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(aprilTags = updated) }
                }
                is FieldEditorIntent.SelectElement -> {
                    _state.update { it.copy(selectedElement = intent.elementId) }
                }
                is FieldEditorIntent.ClearSaveStatus -> {
                    _state.update { it.copy(saveStatus = "") }
                }
                is FieldEditorIntent.SetObstacles -> {
                    _state.update { it.copy(obstacles = intent.obstacles) }
                }
                is FieldEditorIntent.SetGamePieces -> {
                    _state.update { it.copy(gamePieces = intent.gamePieces) }
                }
                is FieldEditorIntent.SetAprilTags -> {
                    _state.update { it.copy(aprilTags = intent.tags) }
                }
                is FieldEditorIntent.SaveFieldWaypoints -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    val s = _state.value
                    if (!projectPath.isNullOrEmpty()) {
                        try {
                            withContext(Dispatchers.IO) {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths"
                                    else "src/main/assets/paths"
                                } else {
                                    "src/main/deploy/paths"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                targetDir.mkdirs()
                                val targetFile = File(targetDir, "field_waypoints.json")
                                val jsonFormat = Json { prettyPrint = true }
                                targetFile.writeText(jsonFormat.encodeToString(s.fieldWaypoints))
                            }
                            _state.update { it.copy(saveStatus = "Saved field waypoints successfully!") }
                        } catch (e: Exception) {
                            _state.update { it.copy(saveStatus = "Failed to save field waypoints: ${e.message}") }
                        }
                    }
                }
                is FieldEditorIntent.AddFieldWaypoint -> {
                    val updated = _state.value.fieldWaypoints + intent.waypoint
                    _state.update { it.copy(fieldWaypoints = updated) }
                }
                is FieldEditorIntent.UpdateFieldWaypoint -> {
                    val updated = _state.value.fieldWaypoints.toMutableList().apply {
                        set(intent.index, intent.waypoint)
                    }
                    _state.update { it.copy(fieldWaypoints = updated) }
                }
                is FieldEditorIntent.DeleteFieldWaypoint -> {
                    val updated = _state.value.fieldWaypoints.toMutableList().apply {
                        removeAt(intent.index)
                    }
                    _state.update { it.copy(fieldWaypoints = updated) }
                }
                is FieldEditorIntent.SetFieldWaypoints -> {
                    _state.update { it.copy(fieldWaypoints = intent.waypoints) }
                }
                is FieldEditorIntent.ImportFmap -> {
                    val projectPath = intent.projectPath
                    val league = intent.league
                    val fmapContent = intent.fmapContent
                    if (!projectPath.isNullOrEmpty()) {
                        try {
                            val fmap = Json.decodeFromString<LimelightFmap>(fmapContent)
                            val placements = fmap.fiducials.mapNotNull { fiducial ->
                                val transform = fiducial.transform
                                if (transform.size >= 16) {
                                    val tx = transform[3]
                                    val ty = transform[7]
                                    val tz = transform[11]
                                    val yawRad = kotlin.math.atan2(transform[4], transform[0])
                                    val yawDeg = Math.toDegrees(yawRad)
                                    AprilTagPlacement(
                                        id = "apriltag_${fiducial.id}",
                                        tagId = fiducial.id,
                                        x = tx,
                                        y = ty,
                                        z = tz,
                                        yawDegrees = yawDeg
                                    )
                                } else null
                            }
                            _state.update { it.copy(aprilTags = placements) }

                            withContext(Dispatchers.IO) {
                                val relativeDir = if (league == League.FTC) {
                                    if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/paths"
                                    else "src/main/assets/paths"
                                } else {
                                    "src/main/deploy/paths"
                                }
                                val targetDir = File(projectPath, relativeDir)
                                targetDir.mkdirs()
                                val targetFile = File(targetDir, "apriltags.json")
                                val jsonFormat = Json { prettyPrint = true }
                                targetFile.writeText(jsonFormat.encodeToString(placements))
                            }
                            _state.update { it.copy(saveStatus = "Imported and saved AprilTags successfully!") }
                        } catch (e: Exception) {
                            _state.update { it.copy(saveStatus = "Failed to parse fmap: ${e.message}") }
                        }
                    }
                }
            }
        }
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
private data class LimelightFmap(
    val fiducials: List<LimelightFiducial> = emptyList()
)
