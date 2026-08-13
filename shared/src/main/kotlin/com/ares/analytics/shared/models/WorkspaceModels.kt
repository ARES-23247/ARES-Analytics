package com.ares.analytics.shared.models

import com.ares.analytics.shared.DEFAULT_GEMINI_MODEL
import kotlinx.serialization.Serializable

/** Robotics league whose source layout and runtime conventions apply to a workspace. */
@Serializable
enum class League {
    FTC, FRC
}

/** Whether one workspace is private to a student or intentionally shared by a team. */
@Serializable
enum class WorkspaceCollaborationMode {
    PERSONAL,
    TEAM
}

/** Google Drive container used as the hard isolation boundary for one ARES workspace. */
@Serializable
enum class DriveDestinationType {
    /** A folder created by ARES in the signed-in user's My Drive. */
    PERSONAL_FOLDER,

    /** A folder created by ARES for the user to share with their team. */
    TEAM_FOLDER,

    /** An existing folder that the signed-in user has explicitly selected or joined. */
    SHARED_FOLDER,

    /** The root of a Google Shared Drive available to the signed-in account. */
    SHARED_DRIVE,
}

/**
 * Stable, workspace-scoped Google Drive destination.
 *
 * The OAuth client identifies ARES Analytics; it does not own this data. Google remains
 * authoritative for account identity, ownership, sharing, and write permissions. The root ID
 * is persisted so cloud code never searches a user's unrelated Drive files.
 */
@Serializable
data class DriveDestinationConfig(
    val type: DriveDestinationType,
    val rootFolderId: String,
    val displayName: String,
    val accountSubject: String,
    val accountEmail: String,
    val sharedDriveId: String? = null,
    val collaborationMode: WorkspaceCollaborationMode = when (type) {
        DriveDestinationType.PERSONAL_FOLDER -> WorkspaceCollaborationMode.PERSONAL
        else -> WorkspaceCollaborationMode.TEAM
    },
)

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
    /** Developer-only opt-in. Normal installations use the bundled ARES Desktop OAuth client. */
    val googleOAuthUseCustomClient: Boolean = false,
    /** HTTPS token broker owned by the administrator of a custom OAuth client. */
    val googleOAuthBrokerUrl: String? = null,
    /** Explicit Drive isolation boundary. Cloud synchronization is disabled until selected. */
    val driveDestination: DriveDestinationConfig? = null,
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
    /** Increases the Compose font scale for student-facing readability. */
    val largeTextMode: Boolean = false,
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
