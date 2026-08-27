package com.ares.analytics.viewmodel.project

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ProjectModelArchitectureTest {
    @Test
    fun `studio features consume the assembled project instead of decoding the action catalog`() {
        val sourceRoot = sequenceOf(
            File("app/src/main/kotlin/com/ares/analytics"),
            File("src/main/kotlin/com/ares/analytics"),
        ).firstOrNull(File::isDirectory)
        checkNotNull(sourceRoot) { "Could not locate Analytics application sources" }

        val projectBoundary = File(sourceRoot, "viewmodel/project").canonicalFile
        val templateBoundary = File(sourceRoot, "service/project").canonicalFile
        val violations = sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filterNot { file -> file.canonicalFile.toPath().startsWith(projectBoundary.toPath()) }
            .filterNot { file -> file.canonicalFile.toPath().startsWith(templateBoundary.toPath()) }
            .mapNotNull { file ->
                val source = file.readText()
                val forbidden = listOf(
                    "CapabilityCatalogCodec.decode(",
                    "CapabilityCatalogProjectRepository()",
                ).filter(source::contains)
                forbidden.takeIf(List<String>::isNotEmpty)?.let { file.relativeTo(sourceRoot).path to it }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "Studio features must load action capabilities through AresProjectDocuments: " +
                violations.joinToString { (file, patterns) -> "$file uses ${patterns.joinToString()}" },
        )
    }
}
