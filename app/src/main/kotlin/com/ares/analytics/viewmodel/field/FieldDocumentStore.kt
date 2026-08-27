package com.ares.analytics.viewmodel.field

import com.ares.analytics.shared.AprilTagPlacement
import com.ares.analytics.shared.FieldImageConfig
import com.ares.analytics.shared.FieldWaypoint
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.shared.GamePieceType
import com.ares.analytics.shared.League
import com.ares.analytics.shared.Obstacle
import com.ares.analytics.util.ProjectLayout
import com.ares.analytics.service.project.persistence.AtomicProjectFileWriter
import com.ares.analytics.service.project.persistence.ProjectDocumentWriteLocks
import com.ares.analytics.service.project.persistence.resolveProjectPath
import com.areslib.state.RobotFieldConfig
import com.areslib.state.RobotFieldDocument
import java.io.File
import java.security.MessageDigest

internal data class LoadedFieldDocument(
    val document: RobotFieldConfig,
    val imageConfig: FieldImageConfig,
    val obstacles: List<Obstacle>,
    val gamePieces: List<GamePiece>,
    val gamePieceTypes: List<GamePieceType>,
    val aprilTags: List<AprilTagPlacement>,
    val fieldWaypoints: List<FieldWaypoint>
)

/**
 * Owns canonical `field.json` persistence. Older split editor files are intentionally ignored.
 *
 * Student-authored field revisions are checkpointed under `.ares/history/fields`. A malformed
 * current document is never silently replaced: callers must repair or restore it first, preserving
 * the exact bytes that explain the failure.
 */
internal object FieldDocumentStore {
    fun load(projectPath: String, league: League): LoadedFieldDocument {
        val canonicalFile = ProjectLayout.fieldDefinitionFile(projectPath, league)
        if (canonicalFile.isFile) {
            return RobotFieldDocument.decode(canonicalFile.readText()).toLoaded()
        }

        return FieldDocumentMapper.newDocument(
            league,
            FieldDocumentMapper.defaultImageConfig(league)
        ).toLoaded()
    }

    fun save(projectPath: String, league: League, document: RobotFieldConfig) {
        val encoded = RobotFieldDocument.encode(document)
        val validated = RobotFieldDocument.decode(encoded)
        val canonicalFile = ProjectLayout.fieldDefinitionFile(projectPath, league)
        ProjectDocumentWriteLocks.withLock(canonicalFile) {
            val previous = canonicalFile.takeIf(File::isFile)?.let { file ->
                RobotFieldDocument.decode(file.readText())
            }
            if (previous != null) checkpoint(projectPath, previous)
            checkpoint(projectPath, validated)
            if (previous != validated || !canonicalFile.isFile) {
                AtomicProjectFileWriter.write(canonicalFile, encoded, replaceExisting = true)
            }
        }
    }

    private fun RobotFieldConfig.toLoaded(): LoadedFieldDocument = LoadedFieldDocument(
        document = this,
        imageConfig = FieldDocumentMapper.image(this),
        obstacles = FieldDocumentMapper.obstacles(this),
        gamePieces = FieldDocumentMapper.gamePieces(this),
        gamePieceTypes = FieldDocumentMapper.gamePieceTypes(this),
        aprilTags = FieldDocumentMapper.aprilTags(this),
        fieldWaypoints = FieldDocumentMapper.fieldWaypoints(this)
    )

    private fun checkpoint(projectPath: String, document: RobotFieldConfig) {
        val encoded = RobotFieldDocument.encode(document)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(encoded.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        val historyFile = File(
            resolveProjectPath(projectPath, ".ares/history/fields"),
            "${document.revision.toString().padStart(8, '0')}-${hash.take(12)}.json",
        )
        if (historyFile.isFile) {
            require(historyFile.readText() == encoded) {
                "Field history checkpoint '${historyFile.name}' already exists with different bytes"
            }
        } else {
            AtomicProjectFileWriter.write(historyFile, encoded, replaceExisting = false)
        }
    }
}
