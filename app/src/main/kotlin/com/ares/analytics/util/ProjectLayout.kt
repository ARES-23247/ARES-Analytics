package com.ares.analytics.util

import com.ares.analytics.shared.League
import java.io.File

/**
 * Resolves files owned by an FTC or FRC robot project.
 *
 * FTC workspaces may point either at the Android project root (which contains
 * `TeamCode`) or directly at a desktop/simulator module. FRC deploy assets have
 * one canonical root. Keeping that distinction here prevents screens and view
 * models from silently choosing different locations for the same asset.
 */
internal object ProjectLayout {
    fun assetsDirectory(projectPath: String, league: League): File = when (league) {
        League.FTC -> {
            val teamCodeAssets = File(projectPath, "TeamCode/src/main/assets")
            teamCodeAssets.takeIf(File::isDirectory)
                ?: File(projectPath, "src/main/assets")
        }

        League.FRC -> File(projectPath, "src/main/deploy")
    }

    /** Directory containing obstacles, game pieces, AprilTags, and field waypoints. */
    fun fieldDataDirectory(projectPath: String, league: League): File =
        File(assetsDirectory(projectPath, league), "paths")

    /** Canonical versioned field document consumed by the editor and simulators. */
    fun fieldDefinitionFile(projectPath: String, league: League): File =
        File(fieldDataDirectory(projectPath, league), "field.json")

    /** Returns null only when [projectPath] is a usable robot source repository. */
    fun validationError(projectPath: String, league: League): String? {
        if (projectPath.isBlank()) return "Choose the robot repository folder."
        val root = File(projectPath)
        if (!root.isDirectory) return "That folder does not exist."
        if (!containsRobotSource(root, league)) {
            return "No ${league.name} robot source was found. Choose the repository root, not its assets folder."
        }
        return null
    }

    fun containsRobotSource(root: File, league: League): Boolean {
        val sourceRoots = when (league) {
            League.FTC -> listOf(
                File(root, "TeamCode/src/main/java"),
                File(root, "TeamCode/src/main/kotlin"),
                File(root, "src/main/java"),
                File(root, "src/main/kotlin")
            )
            League.FRC -> listOf(File(root, "src/main/kotlin"), File(root, "src/main/java"))
        }
        return sourceRoots.any { sourceRoot ->
            sourceRoot.isDirectory && sourceRoot.walkTopDown().any { file ->
                file.isFile && (file.extension == "kt" || file.extension == "java")
            }
        }
    }
}
