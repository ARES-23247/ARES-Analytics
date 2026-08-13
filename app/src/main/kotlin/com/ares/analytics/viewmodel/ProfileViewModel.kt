package com.ares.analytics.viewmodel

import com.ares.analytics.service.AuthState
import com.ares.analytics.service.OAuthService
import com.ares.analytics.service.SyncEngineService
import com.ares.analytics.shared.RobotProfile
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.shared.DEFAULT_GEMINI_MODEL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileState(
    val authState: AuthState = AuthState.Unauthenticated,
    val config: WorkspaceConfig? = null,
    val robotProfiles: List<RobotProfile> = emptyList(),
    val syncStatus: String = "",
    val googleClientId: String = "",
    val googleClientSecret: String = "",
    val eventCode: String = "",
    val toaApiKey: String = "",
    val tbaApiKey: String = "",
    val aiMode: String = "STUDIO",
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val vertexServiceAccountPath: String = "",
    val vertexProjectId: String = "",
    val vertexLocation: String = "us-central1",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class ProfileIntent {

    data class LoadConfig(val config: WorkspaceConfig) : ProfileIntent()

    data class GoogleSignIn(val clientId: String) : ProfileIntent()

    data class LinkGitHub(val clientId: String) : ProfileIntent()

    object SignOut : ProfileIntent()

    data class PerformDeltaSync(val firebaseToken: String) : ProfileIntent()

    object ClearSyncStatus : ProfileIntent()
}

/** Manages account linking, event settings, and explicit cloud synchronization actions. */
class ProfileViewModel(
    private val oauthService: OAuthService,
    private val syncEngineService: SyncEngineService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        scope.launch {
            oauthService.authState.collectLatest { state ->
                _state.update { it.copy(authState = state) }
                if (state is AuthState.Authenticated) {
                    onIntent(ProfileIntent.PerformDeltaSync(state.idToken))
                    try {
                        val remoteProfiles = syncEngineService.getRemoteRobotProfiles()
                        _state.update { it.copy(robotProfiles = remoteProfiles) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun onIntent(intent: ProfileIntent) {
        scope.launch {
            when (intent) {
                is ProfileIntent.LoadConfig -> {
                    val cfg = intent.config
                    val remoteProfiles = try {
                        syncEngineService.getRemoteRobotProfiles()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    _state.update {
                        it.copy(
                            config = cfg,
                            robotProfiles = remoteProfiles,
                            googleClientId = cfg.googleClientId ?: "",
                            googleClientSecret = cfg.googleClientSecret ?: "",
                            eventCode = cfg.eventCode ?: "",
                            toaApiKey = cfg.toaApiKey ?: "",
                            tbaApiKey = cfg.tbaApiKey ?: "",
                            aiMode = cfg.aiMode ?: "STUDIO",
                            geminiApiKey = cfg.geminiApiKey ?: "",
                            geminiModel = cfg.geminiModel
                                ?.takeUnless { it == "gemini-1.5-flash" }
                                ?: DEFAULT_GEMINI_MODEL,
                            vertexServiceAccountPath = cfg.vertexServiceAccountPath ?: "",
                            vertexProjectId = cfg.vertexProjectId ?: "",
                            vertexLocation = cfg.vertexLocation ?: "us-central1"
                        )
                    }
                }
                is ProfileIntent.GoogleSignIn -> {
                    val targetClientId = intent.clientId.trim().takeIf { it.isNotBlank() }
                    if (targetClientId == null) {
                        _state.update {
                            it.copy(syncStatus = "Add a Google Desktop OAuth client ID under Developer OAuth Credentials before signing in.")
                        }
                        return@launch
                    }
                    val targetClientSecret = _state.value.googleClientSecret.takeIf { it.isNotBlank() }

                    oauthService.startGoogleLogin(targetClientId, targetClientSecret)
                }
                is ProfileIntent.LinkGitHub -> {
                    oauthService.startGithubLogin(intent.clientId.takeIf { it.isNotBlank() } ?: "mock-github-client-id")
                }
                is ProfileIntent.SignOut -> {
                    oauthService.logout()
                }
                is ProfileIntent.PerformDeltaSync -> {
                    val cfg = _state.value.config ?: return@launch
                    _state.update { it.copy(syncStatus = "Running delta sync...") }
                    try {
                        withContext(Dispatchers.IO) {
                            syncEngineService.performDeltaSync(cfg.teamId, cfg.seasonId, intent.firebaseToken)
                        }
                        _state.update { it.copy(syncStatus = "Sync successful!") }
                    } catch (e: Exception) {
                        _state.update { it.copy(syncStatus = "Sync failed: ${e.message}") }
                    }
                }
                is ProfileIntent.ClearSyncStatus -> {
                    _state.update { it.copy(syncStatus = "") }
                }
            }
        }
    }
}
