package com.ares.analytics.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class League {
    FTC, FRC
}

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
    val firebaseApiKey: String? = null,
    val googleClientSecret: String? = null,
    val simulatorCommand: String? = null,
    val aiMode: String? = "STUDIO",
    val geminiApiKey: String? = null,
    val geminiModel: String? = "gemini-1.5-flash",
    val vertexServiceAccountPath: String? = null,
    val vertexProjectId: String? = null,
    val vertexLocation: String? = "us-central1",
    val colorblindMode: Boolean = false,
    val highContrastMode: Boolean = false,
    val touchOptimizedMode: Boolean = false
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

@Serializable
data class TeamRobotsResponse(
    val robots: List<RobotProfile>
)

@Serializable
data class AddRobotRequest(
    val teamId: String,
    val robot: RobotProfile
)

@Serializable
data class DeleteRobotRequest(
    val teamId: String,
    val robotId: String
)
