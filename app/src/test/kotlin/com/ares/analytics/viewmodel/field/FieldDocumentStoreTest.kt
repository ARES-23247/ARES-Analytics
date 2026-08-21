package com.ares.analytics.viewmodel.field

import com.ares.analytics.shared.League
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.shared.Obstacle
import com.ares.analytics.util.ProjectLayout
import com.areslib.state.AxisDirection
import com.areslib.state.ObstacleType
import com.areslib.state.RobotFieldDocument
import com.areslib.state.RobotFieldElementType
import com.areslib.state.RobotFieldObstacle
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldDocumentStoreTest {
    @Test
    fun editorSavePreservesCanonicalPropertiesThatAreNotAuthorable() {
        val image = FieldDocumentMapper.defaultImageConfig(League.FTC)
        val canonicalType = RobotFieldElementType(
            id = "custom-piece",
            name = "Custom Piece",
            shape = "box",
            width = 0.20,
            height = 0.10,
            depth = 0.37,
            massKg = 0.42,
            movable = false,
            friction = 0.71,
            restitution = 0.12
        )
        val canonicalObstacle = RobotFieldObstacle(
            id = "ramp",
            name = "Ramp",
            x = 0.5,
            y = -0.25,
            width = 0.6,
            height = 0.2,
            isBlocking = false,
            obstacleType = ObstacleType.RAMP,
            rampDirection = AxisDirection.UP,
            friction = 0.83,
            restitution = 0.07
        )
        val base = FieldDocumentMapper.newDocument(League.FTC, image).copy(
            elementTypes = listOf(canonicalType),
            obstacles = listOf(canonicalObstacle)
        )

        val edited = FieldDocumentMapper.withEditorData(
            base = base,
            league = League.FTC,
            image = image,
            obstacles = listOf(Obstacle.Rectangle("ramp", "Renamed Ramp", 0.5, -0.25, 0.6, 0.2)),
            gamePieces = emptyList(),
            gamePieceTypes = FieldDocumentMapper.gamePieceTypes(base),
            aprilTags = emptyList(),
            fieldWaypoints = emptyList()
        )

        val obstacle = edited.obstacles.single()
        assertEquals("Renamed Ramp", obstacle.name)
        assertEquals(false, obstacle.isBlocking)
        assertEquals(ObstacleType.RAMP, obstacle.obstacleType)
        assertEquals(AxisDirection.UP, obstacle.rampDirection)
        assertEquals(0.83, obstacle.friction, 1e-9)
        assertEquals(0.07, obstacle.restitution, 1e-9)

        val type = edited.elementTypes.single()
        assertEquals(0.37, type.depth, 1e-9)
        assertEquals(false, type.movable)
    }

    @Test
    fun canonicalDocumentRoundTripsAtomically() {
        val project = Files.createTempDirectory("ares-field-canonical").toFile()
        try {
            File(project, "src/main/assets/paths").mkdirs()
            val image = FieldDocumentMapper.defaultImageConfig(League.FTC)
            val gamePieceTypes = FieldDocumentMapper.defaultGamePieceTypes(League.FTC)
            val gamePieceType = gamePieceTypes.first()
            val document = FieldDocumentMapper.withEditorData(
                base = FieldDocumentMapper.newDocument(League.FTC, image),
                league = League.FTC,
                image = image,
                obstacles = listOf(Obstacle.Rectangle("wall", "Wall", 0.5, -0.25, 0.6, 0.2, 30.0)),
                gamePieces = listOf(
                    GamePiece("piece-1", "Opening piece", 0.1, 0.2, gamePieceType.name, gamePieceType.id),
                ),
                gamePieceTypes = gamePieceTypes,
                aprilTags = emptyList(),
                fieldWaypoints = emptyList()
            )

            FieldDocumentStore.save(project.absolutePath, League.FTC, document)
            val loaded = FieldDocumentStore.load(project.absolutePath, League.FTC)

            assertEquals("Wall", loaded.obstacles.single().name)
            assertEquals(30.0, loaded.document.obstacles.single().rotation, 1e-9)
            assertEquals(gamePieceType.id, loaded.gamePieces.single().typeId)
            assertEquals(gamePieceType.massKg, loaded.gamePieceTypes.single { it.id == gamePieceType.id }.massKg, 1e-9)
            val canonicalFile = ProjectLayout.fieldDefinitionFile(project.absolutePath, League.FTC)
            assertTrue(canonicalFile.isFile)
            assertEquals(document.revision, RobotFieldDocument.decode(canonicalFile.readText()).revision)
            assertTrue(canonicalFile.parentFile.listFiles().none { it.name.endsWith(".tmp") })
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun splitLegacyFilesAreIgnored() {
        val project = Files.createTempDirectory("ares-field-clean-cutover").toFile()
        try {
            val paths = File(project, "src/main/assets/paths").apply { mkdirs() }
            File(paths, "obstacles.json").writeText("not canonical field data")

            val loaded = FieldDocumentStore.load(project.absolutePath, League.FTC)

            assertTrue(loaded.obstacles.isEmpty())
            assertTrue(!ProjectLayout.fieldDefinitionFile(project.absolutePath, League.FTC).exists())
        } finally {
            project.deleteRecursively()
        }
    }
}
