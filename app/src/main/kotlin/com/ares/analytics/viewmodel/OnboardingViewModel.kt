package com.ares.analytics.viewmodel

import com.ares.analytics.service.EnvironmentService
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class OnboardingState(
    val projectPath: String = "",
    val teamId: String = "",
    val seasonId: String = "",
    val robotId: String = "",
    val robotName: String = "",
    val league: League = League.FTC,
    val nt4Host: String = "192.168.43.1",
    val googleClientId: String = "205869391101-nlcsea4539vjuo50i58bpo0t10d5s0ic.apps.googleusercontent.com",
    val googleClientSecret: String = "",
    val isVerifyingJava: Boolean = false,
    val javaEnvValid: Boolean? = null,
    val javaEnvMsg: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val simulatorCommand: String = "",
    val cloudRobots: List<com.ares.analytics.shared.RobotProfile> = emptyList(),
    val isCloudLoading: Boolean = false,
    val selectedOptionText: String = "Select Robot Profile..."
)

sealed class OnboardingIntent {
    data class UpdateFields(
        val projectPath: String,
        val teamId: String,
        val seasonId: String,
        val robotId: String,
        val robotName: String,
        val league: League,
        val nt4Host: String,
        val googleClientId: String,
        val googleClientSecret: String,
        val simulatorCommand: String
    ) : OnboardingIntent()
    data class UpdateSelectedOptionText(val text: String) : OnboardingIntent()
    data class FetchCloudRobots(val token: String) : OnboardingIntent()
    object DetectLeague : OnboardingIntent()
    object VerifyJava : OnboardingIntent()
    object SubmitConfig : OnboardingIntent()
}

class OnboardingViewModel(
    private val environmentService: EnvironmentService,
    private val teamApiService: com.ares.analytics.service.TeamApiService,
    private val scope: CoroutineScope,
    private val onConfigured: (WorkspaceConfig) -> Unit
) {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        handleIntent(OnboardingIntent.VerifyJava)
    }

    fun handleIntent(intent: OnboardingIntent) {
        scope.launch {
            when (intent) {
                is OnboardingIntent.UpdateFields -> {
                    _state.update {
                        it.copy(
                            projectPath = intent.projectPath,
                            teamId = intent.teamId,
                            seasonId = intent.seasonId,
                            robotId = intent.robotId,
                            robotName = intent.robotName,
                            league = intent.league,
                            nt4Host = intent.nt4Host,
                            googleClientId = intent.googleClientId,
                            googleClientSecret = intent.googleClientSecret,
                            simulatorCommand = intent.simulatorCommand
                        )
                    }
                }
                is OnboardingIntent.UpdateSelectedOptionText -> {
                    _state.update { it.copy(selectedOptionText = intent.text) }
                }
                is OnboardingIntent.FetchCloudRobots -> {
                    val currentTeamId = _state.value.teamId
                    if (currentTeamId.isNotEmpty() && intent.token.isNotEmpty()) {
                        _state.update { it.copy(isCloudLoading = true) }
                        scope.launch {
                            try {
                                val robots = teamApiService.fetchTeamRobots(currentTeamId, intent.token)
                                _state.update { it.copy(cloudRobots = robots, isCloudLoading = false) }
                            } catch (e: Exception) {
                                _state.update { it.copy(cloudRobots = emptyList(), isCloudLoading = false) }
                            }
                        }
                    } else {
                        _state.update { it.copy(cloudRobots = emptyList()) }
                    }
                }
                is OnboardingIntent.DetectLeague -> {
                    val path = _state.value.projectPath
                    if (path.isNotEmpty() && File(path).isDirectory) {
                        val aresRobotConfig = environmentService.readAresRobotJson(path)
                        if (aresRobotConfig != null) {
                            val detectedLeague = if (aresRobotConfig.league.equals("FRC", ignoreCase = true)) League.FRC else League.FTC
                            val defaultHost = environmentService.getDefaultNt4Host(detectedLeague, aresRobotConfig.teamId)
                            _state.update {
                                it.copy(
                                    teamId = aresRobotConfig.teamId,
                                    seasonId = aresRobotConfig.seasonId,
                                    robotId = aresRobotConfig.robotId,
                                    robotName = aresRobotConfig.name,
                                    league = detectedLeague,
                                    nt4Host = defaultHost
                                )
                            }
                        } else {
                            val detectedLeague = environmentService.detectLeague(path)
                            val defaultHost = environmentService.getDefaultNt4Host(detectedLeague, _state.value.teamId)
                            _state.update {
                                it.copy(
                                    league = detectedLeague,
                                    nt4Host = defaultHost
                                )
                            }
                        }
                    }
                }
                is OnboardingIntent.VerifyJava -> {
                    _state.update { it.copy(isVerifyingJava = true) }
                    val result = environmentService.verifyJavaEnvironment()
                    _state.update {
                        it.copy(
                            isVerifyingJava = false,
                            javaEnvValid = result.isValid,
                            javaEnvMsg = result.message
                        )
                    }
                }
                is OnboardingIntent.SubmitConfig -> {
                    val currentState = _state.value
                    if (currentState.projectPath.isEmpty() || currentState.teamId.isEmpty() ||
                        currentState.seasonId.isEmpty() || currentState.robotId.isEmpty()) {
                        _state.update { it.copy(errorMessage = "All fields are required.") }
                        return@launch
                    }

                    val projectDir = File(currentState.projectPath)
                    if (!projectDir.exists() || !projectDir.isDirectory) {
                        _state.update { it.copy(errorMessage = "Project path must be a valid directory.") }
                        return@launch
                    }

                    _state.update { it.copy(isSaving = true, errorMessage = null) }
                    try {
                        val config = WorkspaceConfig(
                            teamId = currentState.teamId,
                            seasonId = currentState.seasonId,
                            robotId = currentState.robotId,
                            robotName = currentState.robotName,
                            projectPath = currentState.projectPath,
                            league = currentState.league,
                            nt4Host = currentState.nt4Host.takeIf { it.isNotEmpty() },
                            googleClientId = currentState.googleClientId.takeIf { it.isNotEmpty() },
                            googleClientSecret = currentState.googleClientSecret.takeIf { it.isNotEmpty() },
                            simulatorCommand = currentState.simulatorCommand.takeIf { it.isNotEmpty() }
                        )
                        environmentService.saveConfig(config)
                        
                        // Upload local robot to Firebase
                        try {
                            val profile = com.ares.analytics.shared.RobotProfile(
                                robotId = currentState.robotId,
                                league = currentState.league,
                                seasonId = currentState.seasonId,
                                name = currentState.robotName.ifEmpty { "${currentState.robotId} Local Config" }
                            )
                            teamApiService.addRobotProfile(currentState.teamId, profile)
                        } catch (e: Exception) {
                            // Silently fail if they aren't signed in, but maybe log it
                            e.printStackTrace()
                        }
                        
                        _state.update { it.copy(isSaving = false, saveSuccess = true) }
                        onConfigured(config)
                    } catch (e: Exception) {
                        _state.update { it.copy(isSaving = false, errorMessage = "Failed to save configuration: ${e.message}") }
                    }
                }
            }
        }
    }
}
