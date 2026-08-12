package com.ares.analytics.shared.models

import com.ares.analytics.shared.DEFAULT_GEMINI_MODEL
import kotlinx.serialization.Serializable

/** Robotics league whose source layout and runtime conventions apply to a workspace. */
@Serializable
enum class League {
    FTC, FRC
}

/**
 * User configuration for one robot project.
 *
 * API keys and OAuth client values are local configuration, not safe-to-share
 * metadata. Callers must redact them from diagnostics and logs.
 */
@Serializable
data class WorkspaceConfig(
    val id: String = "",
    val teamId: String,
    val seasonId: String,
    val robotId: String,
    val robotName: String = "",
    val projectPath: String,
    val league: League,
    val nt4Host: String? = null,
    val eventCode: String? = null,
    val toaApiKey: String? = null,
    val tbaApiKey: String? = null,
    val googleClientId: String? = null,
    val googleClientSecret: String? = null,
    val simulatorCommand: String? = null,
    val aiMode: String? = "STUDIO",
    val geminiApiKey: String? = null,
    val geminiModel: String? = DEFAULT_GEMINI_MODEL,
    val vertexServiceAccountPath: String? = null,
    val vertexProjectId: String? = null,
    val vertexLocation: String? = "us-central1",
    val colorblindMode: Boolean = false,
    val highContrastMode: Boolean = false,
    val touchOptimizedMode: Boolean = false,
    /** Exposes advanced code, database, and scaffolding tools in navigation search. */
    val developerMode: Boolean = false,
    /** Bumper-to-bumper robot length used for field-boundary validation. */
    val robotLengthMeters: Double? = null,
    /** Bumper-to-bumper robot width used for field-boundary validation. */
    val robotWidthMeters: Double? = null
)

@Serializable
data class AppWorkspaces(
    val activeWorkspaceId: String?,
    val workspaces: List<WorkspaceConfig>
)

@Serializable
data class RobotProfile(
    val robotId: String,
    val league: League,
    val seasonId: String,
    val name: String
)
