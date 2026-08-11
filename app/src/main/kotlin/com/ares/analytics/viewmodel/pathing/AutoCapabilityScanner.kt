package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.League
import com.ares.analytics.util.ProjectLayout
import com.ares.analytics.viewmodel.project.CapabilityCatalogProjectRepository
import com.areslib.catalog.CapabilityCatalogDocument
import com.areslib.pathing.CommandKey
import com.areslib.pathing.NamedCommandDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

enum class CapabilityCatalogSource {
    CANONICAL_PROJECT_CATALOG,
    LEGACY_MANIFEST_MIGRATION,
    NONE
}

data class CapabilityScanResult(
    /** Compatibility view consumed by the current Auto Builder action dropdown. */
    val catalog: List<NamedCommandDescriptor>,
    val warnings: List<String>,
    val projectRoot: String,
    /** Retained for source compatibility. Kotlin source is intentionally no longer regex-scanned. */
    val kotlinFileCount: Int,
    val manifestsRead: List<String>,
    val capabilityCatalog: CapabilityCatalogDocument? = null,
    val source: CapabilityCatalogSource = CapabilityCatalogSource.NONE
)

/**
 * Reads the generated `.ares/action-catalog.json` from the selected project directory.
 *
 * The checked-in, validated catalog is authoritative and makes authoring deterministic and fully
 * offline. Runtime NT data may verify availability elsewhere, but it never defines the editor's
 * actions. Old `auto-capabilities.json` manifests remain a read-only migration fallback. Kotlin
 * source regex discovery was removed because comments, formatting, and dynamic declarations made
 * it both incomplete and non-hermetic.
 */
class AutoCapabilityScanner(
    private val repository: CapabilityCatalogProjectRepository = CapabilityCatalogProjectRepository()
) {
    fun scan(projectPath: String, league: League): CapabilityScanResult {
        val project = File(projectPath).canonicalFile
        require(project.isDirectory) { "Project directory does not exist" }
        val canonicalFile = repository.file(project.path)

        if (canonicalFile.isFile) {
            return repository.load(project.path).fold(
                onSuccess = { document -> canonicalResult(project, canonicalFile, document) },
                onFailure = { error ->
                    CapabilityScanResult(
                        catalog = emptyList(),
                        warnings = listOf(
                            "Action catalog is invalid; fix ${canonicalFile.path}: ${error.message}"
                        ),
                        projectRoot = project.path,
                        kotlinFileCount = 0,
                        manifestsRead = listOf(canonicalFile.path),
                        capabilityCatalog = null,
                        source = CapabilityCatalogSource.CANONICAL_PROJECT_CATALOG
                    )
                }
            )
        }

        val warnings = mutableListOf<String>()
        val legacyFiles = legacyManifestCandidates(project, league).filter(File::isFile)
        val descriptors = linkedMapOf<CommandKey, NamedCommandDescriptor>()
        legacyFiles.forEach { manifest ->
            parseLegacyManifest(manifest, warnings).forEach { descriptor ->
                descriptors[descriptor.key] = descriptor
            }
        }
        if (legacyFiles.isEmpty()) {
            warnings += "No .ares/action-catalog.json was found in ${project.path}. Generate the project catalog before adding robot actions."
        }
        return CapabilityScanResult(
            catalog = descriptors.values.sortedWith(descriptorOrdering),
            warnings = warnings,
            projectRoot = project.path,
            kotlinFileCount = 0,
            manifestsRead = legacyFiles.map { it.path },
            capabilityCatalog = null,
            source = if (legacyFiles.isEmpty()) {
                CapabilityCatalogSource.NONE
            } else {
                CapabilityCatalogSource.LEGACY_MANIFEST_MIGRATION
            }
        )
    }

    private fun canonicalResult(
        project: File,
        file: File,
        document: CapabilityCatalogDocument
    ): CapabilityScanResult = CapabilityScanResult(
        catalog = document.actions.map { action ->
            NamedCommandDescriptor(
                key = CommandKey(action.key),
                displayName = action.displayName,
                description = action.description,
                category = action.category
            )
        }.sortedWith(descriptorOrdering),
        warnings = emptyList(),
        projectRoot = project.path,
        kotlinFileCount = 0,
        manifestsRead = listOf(file.path),
        capabilityCatalog = document,
        source = CapabilityCatalogSource.CANONICAL_PROJECT_CATALOG
    )

    private fun parseLegacyManifest(
        file: File,
        warnings: MutableList<String>
    ): List<NamedCommandDescriptor> = runCatching {
        val root = Json.parseToJsonElement(file.readText()) as? JsonObject
            ?: error("manifest root must be an object")
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        require(schemaVersion == LEGACY_MANIFEST_SCHEMA_VERSION) { "unsupported schema $schemaVersion" }
        val actions = root["actions"] as? JsonArray ?: JsonArray(emptyList())
        actions.map { element ->
            val action = element as? JsonObject ?: error("action must be an object")
            val rawKey = action["key"]?.jsonPrimitive?.content ?: error("action key is required")
            NamedCommandDescriptor(
                key = CommandKey(rawKey),
                displayName = action["displayName"]?.jsonPrimitive?.content ?: titleFromKey(rawKey),
                description = action["description"]?.jsonPrimitive?.content
                    ?: "Robot action declared by the project",
                category = action["category"]?.jsonPrimitive?.content ?: "General"
            )
        }
    }.onFailure { error ->
        warnings += "Legacy action manifest ${file.name} could not be migrated: ${error.message}"
    }.getOrDefault(emptyList())

    private fun legacyManifestCandidates(project: File, league: League): List<File> = listOf(
        File(project, ".ares/auto-capabilities.json"),
        File(ProjectLayout.assetsDirectory(project.path, league), "ares/auto-capabilities.json")
    ).distinctBy { it.absolutePath }

    private fun titleFromKey(key: String): String = key
        .replace('.', ' ')
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar(Char::titlecase) }

    private companion object {
        const val LEGACY_MANIFEST_SCHEMA_VERSION = 1
        val descriptorOrdering = compareBy<NamedCommandDescriptor> { it.category.lowercase() }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.key.value }
    }
}
