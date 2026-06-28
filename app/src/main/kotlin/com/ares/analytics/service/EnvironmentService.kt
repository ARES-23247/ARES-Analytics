package com.ares.analytics.service

import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.shared.AppWorkspaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class EnvironmentService(
    private val configPath: String = System.getProperty("user.home") + "/.ares-analytics/config.json",
    private val workspacesPath: String = System.getProperty("user.home") + "/.ares-analytics/workspaces.json"
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun loadWorkspaces(): AppWorkspaces = withContext(Dispatchers.IO) {
        val file = File(workspacesPath)
        val legacyFile = File(configPath)

        if (file.exists()) {
            try {
                return@withContext json.decodeFromString<AppWorkspaces>(file.readText())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (legacyFile.exists()) {
            try {
                val legacyConfig = json.decodeFromString<WorkspaceConfig>(legacyFile.readText())
                val migratedId = legacyConfig.id.ifEmpty { "${legacyConfig.league}-${legacyConfig.teamId}-${legacyConfig.robotId}-${legacyConfig.seasonId}" }
                val migratedConfig = legacyConfig.copy(id = migratedId)
                val migratedWorkspaces = AppWorkspaces(
                    activeWorkspaceId = migratedId,
                    workspaces = listOf(migratedConfig)
                )
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(migratedWorkspaces))
                return@withContext migratedWorkspaces
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        AppWorkspaces(activeWorkspaceId = null, workspaces = emptyList())
    }

    suspend fun saveWorkspaces(appWorkspaces: AppWorkspaces) = withContext(Dispatchers.IO) {
        val file = File(workspacesPath)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(appWorkspaces))
    }

    suspend fun loadConfig(): WorkspaceConfig? {
        val app = loadWorkspaces()
        return app.workspaces.find { it.id == app.activeWorkspaceId } ?: app.workspaces.firstOrNull()
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
        if (javaHome.isNullOrEmpty()) {
            return@withContext JavaEnvResult(false, "JAVA_HOME environment variable is not set.")
        }

        val javaExe = if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
            "$javaHome\\bin\\java.exe"
        } else {
            "$javaHome/bin/java"
        }

        if (!File(javaExe).exists()) {
            return@withContext JavaEnvResult(false, "java executable not found at $javaExe")
        }

        try {
            val process = ProcessBuilder(javaExe, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
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
}

data class JavaEnvResult(
    val isValid: Boolean,
    val message: String
)
