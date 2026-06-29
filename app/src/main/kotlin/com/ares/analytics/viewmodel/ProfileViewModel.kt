package com.ares.analytics.viewmodel

import com.ares.analytics.service.AuthState
import com.ares.analytics.service.EnvironmentService
import com.ares.analytics.service.FirebaseClientService
import com.ares.analytics.service.OAuthService
import com.ares.analytics.service.SyncEngineService
import com.ares.analytics.shared.WorkspaceConfig
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
    val syncStatus: String = "",
    val googleClientId: String = "",
    val firebaseApiKey: String = "",
    val googleClientSecret: String = "",
    val eventCode: String = "",
    val toaApiKey: String = "",
    val tbaApiKey: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class ProfileIntent {
    data class LoadConfig(val config: WorkspaceConfig) : ProfileIntent()
    data class GoogleSignIn(val clientId: String) : ProfileIntent()
    data class LinkGitHub(val clientId: String) : ProfileIntent()
    object SignOut : ProfileIntent()
    data class PerformDeltaSync(val firebaseToken: String) : ProfileIntent()
    data class UpdateEventSettings(
        val googleClientId: String,
        val firebaseApiKey: String,
        val googleClientSecret: String,
        val eventCode: String,
        val toaApiKey: String,
        val tbaApiKey: String,
        val onConfigChanged: (WorkspaceConfig) -> Unit
    ) : ProfileIntent()
    object ClearSyncStatus : ProfileIntent()
}

class ProfileViewModel(
    private val oauthService: OAuthService,
    private val syncEngineService: SyncEngineService,
    private val environmentService: EnvironmentService,
    private val firebaseClientService: FirebaseClientService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        scope.launch {
            oauthService.authState.collectLatest { state ->
                _state.update { it.copy(authState = state) }
            }
        }
    }

    fun onIntent(intent: ProfileIntent) {
        scope.launch {
            when (intent) {
                is ProfileIntent.LoadConfig -> {
                    val cfg = intent.config
                    // Dynamically bind current Firebase API key override
                    firebaseClientService.apiKey = cfg.firebaseApiKey.takeIf { !it.isNullOrBlank() }
                        ?: "AIzaSyCkTBJqV7CAFRsxm4047oGXwbm_QP-BT7I"

                    _state.update {
                        it.copy(
                            config = cfg,
                            googleClientId = cfg.googleClientId ?: "",
                            firebaseApiKey = cfg.firebaseApiKey ?: "",
                            googleClientSecret = cfg.googleClientSecret ?: "",
                            eventCode = cfg.eventCode ?: "",
                            toaApiKey = cfg.toaApiKey ?: "",
                            tbaApiKey = cfg.tbaApiKey ?: ""
                        )
                    }
                }
                is ProfileIntent.GoogleSignIn -> {
                    // Update FirebaseClientService configuration context
                    val currentApiKey = _state.value.firebaseApiKey.takeIf { it.isNotBlank() }
                        ?: "AIzaSyCkTBJqV7CAFRsxm4047oGXwbm_QP-BT7I"
                    firebaseClientService.apiKey = currentApiKey

                    val targetClientId = intent.clientId.takeIf { it.isNotBlank() }
                        ?: "205869391101-nlcsea4539vjuo50i58bpo0t10d5s0ic.apps.googleusercontent.com"

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
                is ProfileIntent.UpdateEventSettings -> {
                    val currentCfg = _state.value.config ?: return@launch
                    val newConfig = currentCfg.copy(
                        googleClientId = intent.googleClientId.takeIf { it.isNotBlank() },
                        firebaseApiKey = intent.firebaseApiKey.takeIf { it.isNotBlank() },
                        googleClientSecret = intent.googleClientSecret.takeIf { it.isNotBlank() },
                        eventCode = intent.eventCode.takeIf { it.isNotBlank() },
                        toaApiKey = intent.toaApiKey.takeIf { it.isNotBlank() },
                        tbaApiKey = intent.tbaApiKey.takeIf { it.isNotBlank() }
                    )
                    firebaseClientService.apiKey = intent.firebaseApiKey.takeIf { it.isNotBlank() }
                        ?: "AIzaSyCkTBJqV7CAFRsxm4047oGXwbm_QP-BT7I"

                    _state.update {
                        it.copy(
                            config = newConfig,
                            googleClientId = intent.googleClientId,
                            firebaseApiKey = intent.firebaseApiKey,
                            googleClientSecret = intent.googleClientSecret,
                            eventCode = intent.eventCode,
                            toaApiKey = intent.toaApiKey,
                            tbaApiKey = intent.tbaApiKey
                        )
                    }
                    withContext(Dispatchers.IO) {
                        environmentService.saveConfig(newConfig)
                    }
                    intent.onConfigChanged(newConfig)
                }
                is ProfileIntent.ClearSyncStatus -> {
                    _state.update { it.copy(syncStatus = "") }
                }
            }
        }
    }
}
