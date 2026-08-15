package com.ares.analytics.service.project

import com.ares.analytics.shared.League
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in release check for the real pinned archives.
 *
 * Run with `ARES_OFFICIAL_TEMPLATE_ARCHIVE_DIR=<download-dir>` and
 * `ARES_OFFICIAL_TEMPLATE_OUTPUT_DIR=<empty-build-dir>` (or matching `-Dares.*` properties), then
 * build the two emitted projects.
 * Normal unit runs skip this network/release-artifact boundary.
 */
class OfficialProjectTemplateIntegrationTest {
    @Test
    fun `official pinned archives create source-valid FTC and FRC projects`() = runBlocking {
        val archiveDirectory = (
            System.getProperty("ares.officialTemplateArchiveDir")
                ?: System.getenv("ARES_OFFICIAL_TEMPLATE_ARCHIVE_DIR")
            )?.let(::File)
        val outputDirectory = (
            System.getProperty("ares.officialTemplateOutputDir")
                ?: System.getenv("ARES_OFFICIAL_TEMPLATE_OUTPUT_DIR")
            )?.let(::File)
        assumeTrue(archiveDirectory?.isDirectory == true && outputDirectory != null)

        val archiveRoot = requireNotNull(archiveDirectory).canonicalFile
        val output = requireNotNull(outputDirectory).canonicalFile
        output.mkdirs()
        val archives = mapOf(
            League.FTC to File(archiveRoot, "ftc-schema8.zip"),
            League.FRC to File(archiveRoot, "frc.zip"),
        )
        assertTrue(archives.values.all(File::isFile), "Download both pinned archives before this release check.")
        val service = RobotProjectTemplateService(
            cacheDirectory = File(output, "cache"),
            archiveDownloader = { template, destination ->
                requireNotNull(archives[template.league]).copyTo(destination, overwrite = true)
            },
        )

        League.entries.forEach { league ->
            val destination = File(output, league.name.lowercase())
            if (destination.exists()) destination.deleteRecursively()
            val result = service.create(
                RobotProjectCreationRequest(
                    parentDirectory = output,
                    folderName = destination.name,
                    league = league,
                    teamId = "23247",
                    seasonId = "2026",
                    robotId = "TemplateCheck${league.name}",
                    robotName = "Template Check ${league.name}",
                ),
            )
            assertTrue(result.destination.isDirectory)
            assertTrue(File(result.destination, ".ares/template-provenance.json").isFile)
        }
    }
}
