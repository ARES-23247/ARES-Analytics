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
    val league: League = League.FTC,
    val nt4Host: String = "192.168.43.1",
    val googleClientId: String = "205869391101-7bhkcpseglmtv0n3ig8i17e1ntl47tdr.apps.googleusercontent.com",
    val isVerifyingJava: Boolean = false,
    val javaEnvValid: Boolean? = null,
    val javaEnvMsg: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

sealed class OnboardingIntent {
    data class UpdateFields(
        val projectPath: String,
        val teamId: String,
        val seasonId: String,
        val robotId: String,
        val league: League,
        val nt4Host: String,
        val googleClientId: String
    ) : OnboardingIntent()
    object DetectLeague : OnboardingIntent()
    object VerifyJava : OnboardingIntent()
    object SubmitConfig : OnboardingIntent()
}

class OnboardingViewModel(
    private val environmentService: EnvironmentService,
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
                            league = intent.league,
                            nt4Host = intent.nt4Host,
                            googleClientId = intent.googleClientId
                        )
                    }
                }
                is OnboardingIntent.DetectLeague -> {
                    val path = _state.value.projectPath
                    if (path.isNotEmpty() && File(path).isDirectory) {
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
                            projectPath = currentState.projectPath,
                            league = currentState.league,
                            nt4Host = currentState.nt4Host.takeIf { it.isNotEmpty() },
                            googleClientId = currentState.googleClientId.takeIf { it.isNotEmpty() }
                        )
                        environmentService.saveConfig(config)
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
