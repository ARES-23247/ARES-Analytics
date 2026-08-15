package com.ares.analytics.viewmodel.field

import com.ares.analytics.shared.AprilTagPlacement
import com.ares.analytics.shared.FieldImageConfig
import com.ares.analytics.shared.FieldWaypoint
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.shared.GamePieceType
import com.ares.analytics.shared.League
import com.ares.analytics.shared.Obstacle
import com.ares.analytics.util.ProjectLayout
import com.areslib.state.RobotFieldConfig
import com.areslib.state.RobotFieldDocument
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal data class LoadedFieldDocument(
    val document: RobotFieldConfig,
    val imageConfig: FieldImageConfig,
    val obstacles: List<Obstacle>,
    val gamePieces: List<GamePiece>,
    val gamePieceTypes: List<GamePieceType>,
    val aprilTags: List<AprilTagPlacement>,
    val fieldWaypoints: List<FieldWaypoint>
)

/** Owns canonical `field.json` persistence. Older split editor files are intentionally ignored. */
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
        writeAtomically(ProjectLayout.fieldDefinitionFile(projectPath, league), RobotFieldDocument.encode(document))
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

    private fun writeAtomically(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(content)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
