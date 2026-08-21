package com.ares.analytics.service

import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.shared.AppWorkspaces
import com.ares.analytics.util.ProjectLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * Workspace and environment configuration management service.
 *
 * Manages active team workspaces, season identifiers, robot configurations, and league settings across FTC and FRC.
 * Handles schema migration from single legacy `config.json` files to multi-workspace `workspaces.json` persistence.
 *
 * ### Configuration Data:
 * - Active workspace ID mapping
 * - League enum configuration ([League.FTC] / [League.FRC])
 * - Team number, season ID, and robot hardware ID metadata
 *
 * ### Thread Safety & Performance Guarantees:
 * All file read/write operations execute asynchronously on `Dispatchers.IO`. Thread-safe.
 *
 * @param configPath Legacy single workspace config JSON path (`~/.ares-analytics/config.json`).
 * @param workspacesPath Multi-workspace configuration JSON path (`~/.ares-analytics/workspaces.json`).
 *
 * @see com.ares.analytics.shared.AppWorkspaces
 * @see com.ares.analytics.shared.WorkspaceConfig
 */
class EnvironmentService(
    private val configPath: String = System.getProperty("user.home") + "/.ares-analytics/config.json",
    private val workspacesPath: String = System.getProperty("user.home") + "/.ares-analytics/workspaces.json",
    private val secretsWriter: (File, ByteArray) -> Unit = ::writeSecrets,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun loadWorkspaces(): AppWorkspaces = withContext(Dispatchers.IO) {
        val file = File(workspacesPath)
        val legacyFile = File(configPath)

        if (file.exists()) {
            val saved = try {
                json.decodeFromString<AppWorkspaces>(file.readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            if (saved != null) {
                val resolved = saved.copy(
                    workspaces = saved.workspaces.map(::resolveMovedRobotProject)
                )
                if (resolved != saved) {
                    secretsWriter(file, json.encodeToString(resolved).toByteArray(Charsets.UTF_8))
                }
                return@withContext resolved
            }
        }

        if (legacyFile.exists()) {
            val legacyConfig = try {
                json.decodeFromString<WorkspaceConfig>(legacyFile.readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            if (legacyConfig != null) {
                val migratedId = legacyConfig.id.ifEmpty { "${legacyConfig.league}-${legacyConfig.teamId}-${legacyConfig.robotId}-${legacyConfig.seasonId}" }
                val migratedConfig = legacyConfig.copy(id = migratedId)
                val migratedWorkspaces = AppWorkspaces(
                    activeWorkspaceId = migratedId,
                    workspaces = listOf(migratedConfig)
                )
                secretsWriter(file, json.encodeToString(migratedWorkspaces).toByteArray(Charsets.UTF_8))
                return@withContext migratedWorkspaces
            }
        }

        AppWorkspaces(activeWorkspaceId = null, workspaces = emptyList())
    }

    /**
     * Repairs a workspace path after a repository was moved while leaving an old asset-only
     * directory behind. A migration is applied only when one nearby repository has matching
     * checked-in robot identity, so multiple robots can never be selected by filename guessing.
     */
    private fun resolveMovedRobotProject(config: WorkspaceConfig): WorkspaceConfig {
        val configuredRoot = runCatching { File(config.projectPath).canonicalFile }.getOrNull()
            ?: return config
        if (ProjectLayout.containsRobotSource(configuredRoot, config.league)) return config
        if (!hasRelocationEvidence(configuredRoot, config.league)) return config

        val searchRoot = configuredRoot.parentFile?.parentFile ?: return config
        val matches = runCatching {
            searchRoot.walkTopDown()
                .maxDepth(PROJECT_SEARCH_DEPTH)
                .onFail { _, _ -> }
                // Recovery is best-effort and runs while loading application state. Never let a
                // stale path under a broad directory (for example AppData) turn startup into an
                // unbounded filesystem crawl.
                .take(MAX_PROJECT_SEARCH_ENTRIES)
                .filter { it.isFile && it.name == ARES_ROBOT_FILE }
                .mapNotNull { identityFile ->
                    val candidate = identityFile.parentFile?.canonicalFile ?: return@mapNotNull null
                    val identity = runCatching {
                        json.decodeFromString<AresRobotConfig>(identityFile.readText())
                    }.getOrNull() ?: return@mapNotNull null
                    candidate.takeIf {
                        identity.matches(config) && ProjectLayout.containsRobotSource(candidate, config.league)
                    }
                }
                .distinctBy(File::getPath)
                .toList()
        }.getOrDefault(emptyList())

        return matches.singleOrNull()?.let { config.copy(projectPath = it.path) } ?: config
    }

    private fun hasRelocationEvidence(root: File, league: League): Boolean = when (league) {
        League.FTC -> File(root, "src/main/assets").isDirectory ||
            File(root, "TeamCode/src/main/assets").isDirectory
        League.FRC -> File(root, "src/main/deploy").isDirectory
    }

    private fun AresRobotConfig.matches(config: WorkspaceConfig): Boolean =
        teamId == config.teamId &&
            robotId.equals(config.robotId, ignoreCase = true) &&
            league.equals(config.league.name, ignoreCase = true)

    suspend fun saveWorkspaces(appWorkspaces: AppWorkspaces) = withContext(Dispatchers.IO) {
        val file = File(workspacesPath)
        // workspaces.json holds secrets (googleClientSecret, geminiApiKey, toaApiKey,
        // vertexServiceAccountPath) → restrict to owner-only via writeSecrets (AUDIT H2).
        secretsWriter(file, json.encodeToString(appWorkspaces).toByteArray(Charsets.UTF_8))
    }

    suspend fun loadConfig(): WorkspaceConfig? {
        val app = loadWorkspaces()
        val baseConfig = app.workspaces.find { it.id == app.activeWorkspaceId } ?: app.workspaces.firstOrNull()
        if (baseConfig != null) {
            val aresRobotConfig = readAresRobotJson(baseConfig.projectPath)
            if (aresRobotConfig != null) {
                return baseConfig.copy(
                    teamId = aresRobotConfig.teamId,
                    seasonId = aresRobotConfig.seasonId,
                    robotId = aresRobotConfig.robotId,
                    robotName = aresRobotConfig.name,
                    league = if (aresRobotConfig.league.equals("FRC", ignoreCase = true)) League.FRC else League.FTC
                )
            }
        }
        return baseConfig
    }

    suspend fun saveConfig(config: WorkspaceConfig) {
        val app = loadWorkspaces()
        val configWithId = if (config.id.isEmpty()) {
            config.copy(id = "${config.league}-${config.teamId}-${config.robotId}-${config.seasonId}")
        } else {
            config
        }
        val newList = app.workspaces.filter { it.id != configWithId.id } + configWithId
        saveWorkspaces(AppWorkspaces(activeWorkspaceId = configWithId.id, workspaces = newList))
    }

    suspend fun verifyJavaEnvironment(): JavaEnvResult = withContext(Dispatchers.IO) {
        val javaHome = System.getenv("JAVA_HOME")
        val executableName = if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
            "java.exe"
        } else {
            "java"
        }
        val javaExe = javaHome
            ?.takeIf(String::isNotBlank)
            ?.let { File(it, "bin/$executableName").path }
            ?: "java"

        if (javaExe != "java" && !File(javaExe).exists()) {
            return@withContext JavaEnvResult(false, "java executable not found at $javaExe")
        }

        try {
            val process = ProcessBuilder(javaExe, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext JavaEnvResult(false, "Java verification timed out.")
            }
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                JavaEnvResult(true, "Java executable valid. Output:\n$output")
            } else {
                JavaEnvResult(false, "Java version execution failed with exit code $exitCode. Output:\n$output")
            }
        } catch (e: Exception) {
            JavaEnvResult(false, "Failed to run Java verification: ${e.message}")
        }
    }

    suspend fun detectLeague(projectPath: String): League = withContext(Dispatchers.IO) {
        val root = File(projectPath)
        if (!root.exists() || !root.isDirectory) return@withContext League.FTC

        // Look for typical FRC indicators: build.gradle/settings.gradle mentioning 'frc', or 'wpilibj'
        // or a build.gradle with wpilib dependency.
        val searchFiles = root.walkTopDown().maxDepth(3)
        for (file in searchFiles) {
            if (file.name == "build.gradle" || file.name == "build.gradle.kts") {
                val content = file.readText()
                if (content.contains("edu.wpi.first") || content.contains("wpilibj")) {
                    return@withContext League.FRC
                }
            }
        }

        // Default to FTC (the workspace features TeamCode/ARESLib-Kotlin)
        League.FTC
    }

    fun getDefaultNt4Host(league: League, teamId: String): String {
        return when (league) {
            League.FTC -> "192.168.43.1"
            League.FRC -> {
                // FRC team host convention: 10.TE.AM.2
                val teamNumber = teamId.filter { it.isDigit() }
                if (teamNumber.length in 1..4) {
                    val padded = teamNumber.padStart(4, '0')
                    val te = padded.substring(0, 2).toInt()
                    val am = padded.substring(2, 4).toInt()
                    "10.$te.$am.2"
                } else {
                    "10.0.0.2"
                }
            }
        }
    }

    suspend fun readAresRobotJson(projectPath: String): AresRobotConfig? = withContext(Dispatchers.IO) {
        val file = File(projectPath, ".ares-robot.json")
        if (file.exists()) {
            try {
                return@withContext json.decodeFromString<AresRobotConfig>(file.readText())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        null
    }
}

data class JavaEnvResult(
    val isValid: Boolean,
    val message: String
)

@kotlinx.serialization.Serializable
data class AresRobotConfig(
    val teamId: String,
    val seasonId: String,
    val robotId: String,
    val name: String = "",
    val league: String = "FTC"
)

private const val ARES_ROBOT_FILE = ".ares-robot.json"
private const val PROJECT_SEARCH_DEPTH = 4
private const val MAX_PROJECT_SEARCH_ENTRIES = 5_000

/**
 * Atomically writes [bytes] to [file] through a force-flushed sibling temporary file, then
 * best-effort restricts it to owner-only `rw-------` (AUDIT H2). Used for every file that holds secrets — `workspaces.json` /
 * `config.json` (googleClientSecret, geminiApiKey, toaApiKey, vertexServiceAccountPath)
 * and `auth.json` (OAuth tokens). The permission step is POSIX-only; it is skipped on Windows.
 * Write and atomic-replace failures propagate to the caller and never truncate the prior file.
 */
fun writeSecrets(
    file: File,
    bytes: ByteArray,
    beforeReplace: BeforeAtomicReplace = NO_OP_BEFORE_ATOMIC_REPLACE,
) {
    writeFileAtomically(file, beforeReplace) { temporary ->
        try {
            Files.setPosixFilePermissions(
                temporary.toPath(),
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
            )
        } catch (e: UnsupportedOperationException) {
            // Windows / non-POSIX FS — no action possible.
        }
        Files.write(
            temporary.toPath(),
            bytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }
}
