package com.ares.analytics.viewmodel.field

import com.ares.analytics.shared.League
import com.ares.analytics.shared.Obstacle
import com.ares.analytics.util.ProjectLayout
import com.areslib.state.RobotFieldDocument
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldDocumentStoreTest {
    @Test
    fun canonicalDocumentRoundTripsAtomically() {
        val project = Files.createTempDirectory("ares-field-canonical").toFile()
        try {
            File(project, "src/main/assets/paths").mkdirs()
            val image = FieldDocumentMapper.defaultImageConfig(League.FTC)
            val document = FieldDocumentMapper.withEditorData(
                base = FieldDocumentMapper.newDocument(League.FTC, image),
                league = League.FTC,
                image = image,
                obstacles = listOf(Obstacle.Rectangle("wall", "Wall", 0.5, -0.25, 0.6, 0.2, 30.0)),
                gamePieces = emptyList(),
                aprilTags = emptyList(),
                fieldWaypoints = emptyList()
            )

            FieldDocumentStore.save(project.absolutePath, League.FTC, document)
            val loaded = FieldDocumentStore.load(project.absolutePath, League.FTC)

            assertEquals("Wall", loaded.obstacles.single().name)
            assertEquals(30.0, loaded.document.obstacles.single().rotation, 1e-9)
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
