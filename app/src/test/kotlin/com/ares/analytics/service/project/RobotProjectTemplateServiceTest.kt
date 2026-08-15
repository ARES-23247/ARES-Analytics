package com.ares.analytics.service.project

import com.ares.analytics.shared.League
import com.areslib.catalog.CapabilityCatalogCodec
import com.areslib.catalog.CapabilityCatalogDocument
import com.areslib.project.AresCoordinateConvention
import com.areslib.project.AresLeague
import com.areslib.project.AresProjectMetadataCodec
import com.areslib.project.AresProjectMetadataDocument
import com.areslib.routine.AutonomousCatalogCodec
import com.areslib.routine.AutonomousCatalogDocument
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class RobotProjectTemplateServiceTest {
    @Test
    fun `verified starter is staged personalized and published without merging`() = runBlocking {
        val root = Files.createTempDirectory("ares-project-template-test").toFile()
        try {
            val archive = validFtcArchive()
            val service = service(root, archive)
            val parent = File(root, "robots").apply { mkdirs() }

            val result = service.create(request(parent, "student-robot"))

            assertEquals(RobotProjectTemplateSource.VERIFIED_DOWNLOAD, result.source)
            assertTrue(result.destination.isDirectory)
            assertTrue(File(result.destination, "TeamCode/src/main/java/example/Robot.kt").isFile)
            val identity = File(result.destination, ".ares-robot.json").readText()
            assertTrue(identity.contains("\"teamId\": \"23247\""))
            assertTrue(identity.contains("\"robotId\": \"StudentBot\""))
            val metadata = AresProjectMetadataCodec.decode(File(result.destination, ".ares/project.json").readText())
            assertEquals("team23247-studentbot", metadata.projectId)
            assertEquals(
                metadata.projectId,
                CapabilityCatalogCodec.decode(File(result.destination, ".ares/action-catalog.json").readText()).projectId,
            )
            assertEquals(
                metadata.projectId,
                AutonomousCatalogCodec.decode(File(result.destination, ".ares/autonomous-catalog.json").readText()).projectId,
            )
            val provenance = File(result.destination, ".ares/template-provenance.json").readText()
            assertTrue(provenance.contains("fixture-revision"))
            assertTrue(provenance.contains("SIMULATION_ONLY_REFERENCE"))
            assertNotNull(templateDeploymentBlockReason(result.destination))
            assertFalse(parent.listFiles().orEmpty().any { it.name.contains("ares-partial") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `verified cache supports a second offline creation`() = runBlocking {
        val root = Files.createTempDirectory("ares-project-cache-test").toFile()
        try {
            val archive = validFtcArchive()
            var downloads = 0
            val service = service(root, archive) { _, destination ->
                downloads++
                destination.writeBytes(archive)
            }
            val parent = File(root, "robots").apply { mkdirs() }

            service.create(request(parent, "first-robot"))
            val second = service.create(request(parent, "second-robot"))

            assertEquals(1, downloads)
            assertEquals(RobotProjectTemplateSource.VERIFIED_CACHE, second.source)
            assertTrue(File(second.destination, ".ares/template-provenance.json").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `hash mismatch and zip slip both leave destination absent`() = runBlocking {
        val root = Files.createTempDirectory("ares-project-reject-test").toFile()
        try {
            val parent = File(root, "robots").apply { mkdirs() }
            val valid = validFtcArchive()
            val badHashTemplate = template(valid).copy(archiveSha256 = "0".repeat(64))
            val badHashService = RobotProjectTemplateService(
                cacheDirectory = File(root, "bad-hash-cache"),
                templates = listOf(badHashTemplate),
                archiveDownloader = { _, destination -> destination.writeBytes(valid) },
            )
            assertFailsWith<IllegalStateException> { badHashService.create(request(parent, "bad-hash")) }
            assertFalse(File(parent, "bad-hash").exists())

            val malicious = zipOf("fixture-root/../../outside.txt" to "escape")
            val maliciousService = service(File(root, "malicious-cache"), malicious)
            assertFailsWith<IllegalStateException> { maliciousService.create(request(parent, "bad-zip")) }
            assertFalse(File(parent, "bad-zip").exists())
            assertFalse(File(root, "outside.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `plan rejects traversal reserved names and existing destinations`() {
        val root = Files.createTempDirectory("ares-project-plan-test").toFile()
        try {
            val parent = File(root, "robots").apply { mkdirs() }
            val service = service(root, validFtcArchive())
            assertFalse(service.plan(request(parent, "../escape")).canCreate)
            assertFalse(service.plan(request(parent, "CON")).canCreate)
            File(parent, "already-here").mkdirs()
            val existing = service.plan(request(parent, "already-here"))
            assertFalse(existing.canCreate)
            assertTrue(existing.issues.any { it.contains("already exists") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `normal projects remain deployable while invalid provenance fails closed`() {
        val root = Files.createTempDirectory("ares-project-deploy-policy-test").toFile()
        try {
            assertNull(templateDeploymentBlockReason(root))
            val provenance = File(root, ".ares/template-provenance.json")
            provenance.parentFile.mkdirs()
            provenance.writeText("not-json")
            assertTrue(templateDeploymentBlockReason(root)!!.contains("provenance is invalid"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun service(
        root: File,
        archive: ByteArray,
        downloader: (RobotProjectTemplate, File) -> Unit = { _, destination -> destination.writeBytes(archive) },
    ): RobotProjectTemplateService = RobotProjectTemplateService(
        cacheDirectory = File(root, "cache"),
        templates = listOf(template(archive)),
        archiveDownloader = downloader,
    )

    private fun request(parent: File, folder: String) = RobotProjectCreationRequest(
        parentDirectory = parent,
        folderName = folder,
        league = League.FTC,
        teamId = "23247",
        seasonId = "2026",
        robotId = "StudentBot",
        robotName = "Student Robot",
    )

    private fun template(archive: ByteArray) = RobotProjectTemplate(
        id = "fixture-ftc",
        displayName = "Fixture FTC",
        league = League.FTC,
        aresVersion = "test",
        revision = "fixture-revision",
        archiveUrl = "https://invalid.example/fixture.zip",
        archiveSha256 = sha256(archive),
    )

    private fun validFtcArchive(): ByteArray {
        val metadata = AresProjectMetadataCodec.encode(
            AresProjectMetadataDocument(
                projectId = "template-project",
                league = AresLeague.FTC,
                coordinateConvention = AresCoordinateConvention.CENTER_ORIGIN_CCW,
                robotLengthMeters = 0.45,
                robotWidthMeters = 0.45,
                fieldLengthMeters = 3.65,
                fieldWidthMeters = 3.65,
            ),
        )
        return zipOf(
            "fixture-root/settings.gradle" to "include ':TeamCode'\n",
            "fixture-root/TeamCode/src/main/java/example/Robot.kt" to "package example\nclass Robot\n",
            "fixture-root/.ares/project.json" to metadata,
            "fixture-root/.ares/action-catalog.json" to CapabilityCatalogCodec.encode(
                CapabilityCatalogDocument(projectId = "template-project"),
            ),
            "fixture-root/.ares/autonomous-catalog.json" to AutonomousCatalogCodec.encode(
                AutonomousCatalogDocument(projectId = "template-project", entries = emptyList()),
            ),
            "fixture-root/.ares-robot.json" to "{}\n",
        )
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
