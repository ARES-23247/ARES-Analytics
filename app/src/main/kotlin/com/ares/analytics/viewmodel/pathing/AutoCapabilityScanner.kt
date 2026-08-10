package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.League
import com.ares.analytics.util.ProjectLayout
import com.areslib.pathing.CommandKey
import com.areslib.pathing.NamedCommandDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files

data class CapabilityScanResult(
    val catalog: List<NamedCommandDescriptor>,
    val warnings: List<String>,
    val projectRoot: String,
    val kotlinFileCount: Int,
    val manifestsRead: List<String>
)

/**
 * Discovers auto actions from the selected workspace without requiring a running robot.
 *
 * A checked-in `ares/auto-capabilities.json` manifest is authoritative for labels and help text.
 * Literal `CommandKey("...")` declarations in Kotlin are also discovered so a new action appears
 * immediately while its richer manifest entry is being added. Runtime telemetry is verification,
 * not the source required for authoring.
 */
class AutoCapabilityScanner {
    fun scan(projectPath: String, league: League): CapabilityScanResult {
        val project = File(projectPath).canonicalFile
        require(project.isDirectory) { "Project directory does not exist" }
        val warnings = mutableListOf<String>()
        val discovered = linkedMapOf<CommandKey, NamedCommandDescriptor>()
        val manifestsRead = mutableListOf<String>()

        val kotlinFileCount = scanKotlin(project, discovered, warnings)
        manifestCandidates(project, league).filter(File::isFile).forEach { manifest ->
            manifestsRead += manifest.path
            parseManifest(manifest, warnings).forEach { descriptor ->
                discovered[descriptor.key] = descriptor
            }
        }
        if (kotlinFileCount == 0 && manifestsRead.isEmpty()) {
            warnings += "No robot source or action manifest found in ${project.path}. Select the robot repository root."
        }
        return CapabilityScanResult(
            catalog = discovered.values.sortedWith(
                compareBy<NamedCommandDescriptor> { it.category.lowercase() }
                    .thenBy { it.displayName.lowercase() }
            ),
            warnings = warnings,
            projectRoot = project.path,
            kotlinFileCount = kotlinFileCount,
            manifestsRead = manifestsRead
        )
    }

    private fun scanKotlin(
        project: File,
        discovered: MutableMap<CommandKey, NamedCommandDescriptor>,
        warnings: MutableList<String>
    ): Int {
        var kotlinFileCount = 0
        runCatching {
            Files.walk(project.toPath()).use { paths ->
                paths.filter { path ->
                    val normalized = path.toString().replace('\\', '/')
                    Files.isRegularFile(path) && normalized.endsWith(".kt") &&
                        EXCLUDED_PATH_SEGMENTS.none { segment -> "/$segment/" in normalized }
                }.forEach { path ->
                    kotlinFileCount++
                    if (Files.size(path) > MAX_SOURCE_BYTES) return@forEach
                    val source = Files.readString(path)
                    DESCRIPTOR_REGEX.findAll(source).forEach { match ->
                        runCatching {
                            val key = CommandKey(match.groupValues[1])
                            discovered[key] = NamedCommandDescriptor(
                                key = key,
                                displayName = unescapeKotlinString(match.groupValues[2]),
                                description = unescapeKotlinString(match.groupValues[3]),
                                category = unescapeKotlinString(match.groupValues[4])
                            )
                        }.onFailure { error ->
                            warnings += "Could not read auto action metadata in ${path.fileName}: ${error.message}"
                        }
                    }
                    COMMAND_KEY_REGEX.findAll(source).forEach { match ->
                        val key = CommandKey(match.groupValues[1])
                        discovered.putIfAbsent(
                            key,
                            NamedCommandDescriptor(
                                key = key,
                                displayName = titleFromKey(key.value),
                                description = "Robot action declared in project code",
                                category = "Project actions"
                            )
                        )
                    }
                }
            }
        }.onFailure { error ->
            warnings += "Could not scan Kotlin auto actions: ${error.message}"
        }
        return kotlinFileCount
    }

    private fun parseManifest(file: File, warnings: MutableList<String>): List<NamedCommandDescriptor> =
        runCatching {
            val root = Json.parseToJsonElement(file.readText()) as? JsonObject
                ?: error("manifest root must be an object")
            val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            require(schemaVersion == MANIFEST_SCHEMA_VERSION) {
                "unsupported schema $schemaVersion"
            }
            val actions = root["actions"] as? JsonArray ?: JsonArray(emptyList())
            actions.map { element ->
                val action = element as? JsonObject ?: error("action must be an object")
                val rawKey = action["key"]?.jsonPrimitive?.content ?: error("action key is required")
                val key = CommandKey(rawKey)
                NamedCommandDescriptor(
                    key = key,
                    displayName = action["displayName"]?.jsonPrimitive?.content ?: titleFromKey(rawKey),
                    description = action["description"]?.jsonPrimitive?.content
                        ?: "Robot action declared by the project",
                    category = action["category"]?.jsonPrimitive?.content ?: "General"
                )
            }
        }.onFailure { error ->
            warnings += "Could not read ${file.name}: ${error.message}"
        }.getOrDefault(emptyList())

    private fun manifestCandidates(project: File, league: League): List<File> = listOf(
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

    private fun unescapeKotlinString(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")

    private companion object {
        const val MANIFEST_SCHEMA_VERSION = 1
        const val MAX_SOURCE_BYTES = 2L * 1024L * 1024L
        val COMMAND_KEY_REGEX = Regex("CommandKey\\(\\s*\"([A-Za-z][A-Za-z0-9._-]{0,63})\"\\s*\\)")
        val DESCRIPTOR_REGEX = Regex(
            """NamedCommandDescriptor\s*\(\s*key\s*=\s*CommandKey\(\s*\"([A-Za-z][A-Za-z0-9._-]{0,63})\"\s*\)\s*,\s*displayName\s*=\s*\"((?:\\.|[^\"\\])*)\"\s*,\s*description\s*=\s*\"((?:\\.|[^\"\\])*)\"\s*,\s*category\s*=\s*\"((?:\\.|[^\"\\])*)\"\s*\)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val EXCLUDED_PATH_SEGMENTS = setOf(
            "build", ".gradle", ".git", "generated", "FtcRobotController",
            "src/test", "src/androidTest", "src/testFixtures"
        )
    }
}
