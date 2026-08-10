package com.ares.analytics.service

import com.ares.analytics.shared.AppWorkspaces
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentServiceWorkspaceMigrationTest {
    @Test
    fun `asset-only workspace migrates to the one matching robot repository`() = runBlocking {
        val root = createTempDir(prefix = "ares-workspace-migration-")
        try {
            val staleRoot = File(root, "ftc/ARES-FTC").apply { mkdirs() }
            File(staleRoot, "src/main/assets").mkdirs()

            val sourceRoot = File(root, "ares/ARES-FTC").apply { mkdirs() }
            File(sourceRoot, "TeamCode/src/main/java/Robot.kt").apply {
                parentFile.mkdirs()
                writeText("class Robot")
            }
            File(sourceRoot, ".ares-robot.json").writeText(
                """{"teamId":"23247","seasonId":"2026","robotId":"GoBilda","name":"Test","league":"FTC"}"""
            )

            val config = WorkspaceConfig(
                id = "robot",
                teamId = "23247",
                seasonId = "2026",
                robotId = "GoBilda",
                projectPath = staleRoot.path,
                league = League.FTC
            )
            val workspacesFile = File(root, "settings/workspaces.json").apply {
                parentFile.mkdirs()
                writeText(Json.encodeToString(AppWorkspaces("robot", listOf(config))))
            }

            val loaded = EnvironmentService(
                configPath = File(root, "settings/config.json").path,
                workspacesPath = workspacesFile.path
            ).loadWorkspaces()

            assertEquals(sourceRoot.canonicalPath, File(loaded.workspaces.single().projectPath).canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `ambiguous matching repositories do not rewrite workspace`() = runBlocking {
        val root = createTempDir(prefix = "ares-workspace-ambiguous-")
        try {
            val staleRoot = File(root, "ftc/ARES-FTC").apply { mkdirs() }
            File(staleRoot, "src/main/assets").mkdirs()
            listOf("copy-one", "copy-two").forEach { folder ->
                val candidate = File(root, "$folder/ARES-FTC").apply { mkdirs() }
                File(candidate, "TeamCode/src/main/java/Robot.kt").apply {
                    parentFile.mkdirs()
                    writeText("class Robot")
                }
                File(candidate, ".ares-robot.json").writeText(
                    """{"teamId":"23247","seasonId":"2026","robotId":"GoBilda","league":"FTC"}"""
                )
            }
            val config = WorkspaceConfig(
                id = "robot",
                teamId = "23247",
                seasonId = "2026",
                robotId = "GoBilda",
                projectPath = staleRoot.path,
                league = League.FTC
            )
            val workspacesFile = File(root, "settings/workspaces.json").apply {
                parentFile.mkdirs()
                writeText(Json.encodeToString(AppWorkspaces("robot", listOf(config))))
            }

            val loaded = EnvironmentService(
                configPath = File(root, "settings/config.json").path,
                workspacesPath = workspacesFile.path
            ).loadWorkspaces()

            assertEquals(staleRoot.path, loaded.workspaces.single().projectPath)
        } finally {
            root.deleteRecursively()
        }
    }
}
