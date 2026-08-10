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

    /** Directory containing PathPlanner `.path` files. */
    fun pathPlannerPathsDirectory(projectPath: String, league: League): File =
        File(assetsDirectory(projectPath, league), "pathplanner/paths")

    /** Directory containing PathPlanner `.auto` files. */
    fun pathPlannerAutosDirectory(projectPath: String, league: League): File =
        File(assetsDirectory(projectPath, league), "pathplanner/autos")
}
