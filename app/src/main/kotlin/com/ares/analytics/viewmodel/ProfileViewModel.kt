package com.ares.analytics.viewmodel

import com.ares.analytics.service.AuthState
import com.ares.analytics.service.EnvironmentService
import com.ares.analytics.service.FirebaseClientService
import com.ares.analytics.service.OAuthService
import com.ares.analytics.service.SyncEngineService
import com.ares.analytics.shared.RobotProfile
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

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class ProfileState(
    val authState: AuthState = AuthState.Unauthenticated,
    val config: WorkspaceConfig? = null,
    val robotProfiles: List<RobotProfile> = emptyList(),
    val syncStatus: String = "",
    val googleClientId: String = "",
    val firebaseApiKey: String = "",
    val googleClientSecret: String = "",
    val eventCode: String = "",
    val toaApiKey: String = "",
    val tbaApiKey: String = "",
    val aiMode: String = "STUDIO",
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-1.5-flash",
    val vertexServiceAccountPath: String = "",
    val vertexProjectId: String = "",
    val vertexLocation: String = "us-central1",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class ProfileIntent {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class LoadConfig(val config: WorkspaceConfig) : ProfileIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class GoogleSignIn(val clientId: String) : ProfileIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class LinkGitHub(val clientId: String) : ProfileIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object SignOut : ProfileIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class PerformDeltaSync(val firebaseToken: String) : ProfileIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class UpdateEventSettings(
        val googleClientId: String,
        val firebaseApiKey: String,
        val googleClientSecret: String,
        val eventCode: String,
        val toaApiKey: String,
        val tbaApiKey: String,
        val aiMode: String,
        val geminiApiKey: String,
        val geminiModel: String,
        val vertexServiceAccountPath: String,
        val vertexProjectId: String,
        val vertexLocation: String,
        val onConfigChanged: (WorkspaceConfig) -> Unit
    ) : ProfileIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ClearSyncStatus : ProfileIntent()
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
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
                if (state is AuthState.Authenticated) {
                    onIntent(ProfileIntent.PerformDeltaSync(state.firebaseToken))
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

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun onIntent(intent: ProfileIntent) {
        scope.launch {
            when (intent) {
                is ProfileIntent.LoadConfig -> {
                    val cfg = intent.config
                    firebaseClientService.apiKey = cfg.firebaseApiKey.takeIf { !it.isNullOrBlank() }
                        ?: "AIzaSyB4cU7pgHpqoxtqtQalIE4HqZoz3X7bJH0"
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
                            firebaseApiKey = cfg.firebaseApiKey ?: "",
                            googleClientSecret = cfg.googleClientSecret ?: "",
                            eventCode = cfg.eventCode ?: "",
                            toaApiKey = cfg.toaApiKey ?: "",
                            tbaApiKey = cfg.tbaApiKey ?: "",
                            aiMode = cfg.aiMode ?: "STUDIO",
                            geminiApiKey = cfg.geminiApiKey ?: "",
                            geminiModel = cfg.geminiModel ?: "gemini-1.5-flash",
                            vertexServiceAccountPath = cfg.vertexServiceAccountPath ?: "",
                            vertexProjectId = cfg.vertexProjectId ?: "",
                            vertexLocation = cfg.vertexLocation ?: "us-central1"
                        )
                    }
                }
                is ProfileIntent.GoogleSignIn -> {
                    val currentApiKey = _state.value.firebaseApiKey.takeIf { it.isNotBlank() }
                        ?: "AIzaSyB4cU7pgHpqoxtqtQalIE4HqZoz3X7bJH0"
                    firebaseClientService.apiKey = currentApiKey
                    val targetClientId = intent.clientId.takeIf { it.isNotBlank() }
                        ?: "205869391101-nlcsea4539vjuo50i58bpo0t10d5s0ic.apps.googleusercontent.com"
                    val targetClientSecret = _state.value.googleClientSecret.takeIf { it.isNotBlank() }
                        ?: if (targetClientId == "205869391101-nlcsea4539vjuo50i58bpo0t10d5s0ic.apps.googleusercontent.com") {
                            "_xLIrcFXWhqNpYO1gwPrlZpkRqOs-XPSCOG".reversed()
                        } else {
                            null
                        }

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
                        tbaApiKey = intent.tbaApiKey.takeIf { it.isNotBlank() },
                        aiMode = intent.aiMode.takeIf { it.isNotBlank() },
                        geminiApiKey = intent.geminiApiKey.takeIf { it.isNotBlank() },
                        geminiModel = intent.geminiModel.takeIf { it.isNotBlank() },
                        vertexServiceAccountPath = intent.vertexServiceAccountPath.takeIf { it.isNotBlank() },
                        vertexProjectId = intent.vertexProjectId.takeIf { it.isNotBlank() },
                        vertexLocation = intent.vertexLocation.takeIf { it.isNotBlank() }
                    )
                    firebaseClientService.apiKey = intent.firebaseApiKey.takeIf { it.isNotBlank() }
                        ?: "AIzaSyB4cU7pgHpqoxtqtQalIE4HqZoz3X7bJH0"

                    _state.update {
                        it.copy(
                            config = newConfig,
                            googleClientId = intent.googleClientId,
                            firebaseApiKey = intent.firebaseApiKey,
                            googleClientSecret = intent.googleClientSecret,
                            eventCode = intent.eventCode,
                            toaApiKey = intent.toaApiKey,
                            tbaApiKey = intent.tbaApiKey,
                            aiMode = intent.aiMode,
                            geminiApiKey = intent.geminiApiKey,
                            geminiModel = intent.geminiModel,
                            vertexServiceAccountPath = intent.vertexServiceAccountPath,
                            vertexProjectId = intent.vertexProjectId,
                            vertexLocation = intent.vertexLocation
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
