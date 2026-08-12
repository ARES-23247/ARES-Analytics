package com.ares.analytics.viewmodel

import com.ares.analytics.service.EnvironmentService
import com.ares.analytics.service.SyncEngineService
import com.ares.analytics.shared.League
import com.ares.analytics.shared.RobotProfile
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class OnboardingStep(val number: Int) {
    PROJECT(1),
    ROBOT(2),
    OPTIONAL(3),
    REVIEW(4),
}

data class OnboardingFieldErrors(
    val projectPath: String? = null,
    val teamId: String? = null,
    val seasonId: String? = null,
    val robotId: String? = null,
    val java: String? = null,
) {
    val hasRequiredFieldErrors: Boolean
        get() = projectPath != null || teamId != null || seasonId != null || robotId != null
}

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.PROJECT,
    val projectPath: String = "",
    val projectDetectionMessage: String? = null,
    val teamId: String = "",
    val seasonId: String = "",
    val robotId: String = "",
    val robotName: String = "",
    val league: League = League.FTC,
    val nt4Host: String = "192.168.43.1",
    val googleClientId: String = DEFAULT_GOOGLE_CLIENT_ID,
    val googleClientSecret: String = "",
    val isVerifyingJava: Boolean = false,
    val javaEnvValid: Boolean? = null,
    val javaEnvMsg: String = "JDK 17 has not been checked yet.",
    val javaMajorVersion: Int? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val fieldErrors: OnboardingFieldErrors = OnboardingFieldErrors(),
    val simulatorCommand: String = "",
    val cloudRobots: List<RobotProfile> = emptyList(),
    val isCloudLoading: Boolean = false,
    val selectedOptionText: String = "Select a saved robot...",
    val cloudSetupExpanded: Boolean = false,
    val advancedSetupExpanded: Boolean = false,
) {
    val isProjectReady: Boolean
        get() = projectPath.isNotBlank() && fieldErrors.projectPath == null

    val isRobotReady: Boolean
        get() = teamId.isNotBlank() && seasonId.isNotBlank() && robotId.isNotBlank() &&
            fieldErrors.teamId == null && fieldErrors.seasonId == null && fieldErrors.robotId == null
}

sealed class OnboardingIntent {
    data class UpdateProjectPath(val projectPath: String) : OnboardingIntent()
    data class UpdateTeamId(val teamId: String) : OnboardingIntent()
    data class UpdateSeasonId(val seasonId: String) : OnboardingIntent()
    data class UpdateRobotId(val robotId: String) : OnboardingIntent()
    data class UpdateRobotName(val robotName: String) : OnboardingIntent()
    data class UpdateLeague(val league: League) : OnboardingIntent()
    data class UpdateNt4Host(val nt4Host: String) : OnboardingIntent()
    data class UpdateGoogleClientId(val googleClientId: String) : OnboardingIntent()
    data class UpdateGoogleClientSecret(val googleClientSecret: String) : OnboardingIntent()
    data class UpdateSimulatorCommand(val simulatorCommand: String) : OnboardingIntent()
    data class UpdateSelectedOptionText(val text: String) : OnboardingIntent()
    data class FetchCloudRobots(val token: String) : OnboardingIntent()
    data class SetCloudSetupExpanded(val expanded: Boolean) : OnboardingIntent()
    data class SetAdvancedSetupExpanded(val expanded: Boolean) : OnboardingIntent()
    object DetectLeague : OnboardingIntent()
    object NextStep : OnboardingIntent()
    object PreviousStep : OnboardingIntent()
    object VerifyJava : OnboardingIntent()
    object SubmitConfig : OnboardingIntent()
}

/** Builds and validates a workspace configuration before making it the active workspace. */
class OnboardingViewModel(
    private val environmentService: EnvironmentService,
    private val syncEngineService: SyncEngineService,
    private val scope: CoroutineScope,
    private val onConfigured: (WorkspaceConfig) -> Unit,
) {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        handleIntent(OnboardingIntent.VerifyJava)
    }

    fun handleIntent(intent: OnboardingIntent) {
        scope.launch {
            when (intent) {
                is OnboardingIntent.UpdateProjectPath -> {
                    _state.update {
                        it.copy(
                            projectPath = intent.projectPath,
                            projectDetectionMessage = null,
                            fieldErrors = it.fieldErrors.copy(projectPath = null),
                            errorMessage = null,
                        )
                    }
                    if (File(intent.projectPath).isDirectory) detectProject()
                }
                is OnboardingIntent.UpdateTeamId -> updateRequiredField {
                    it.copy(teamId = intent.teamId, selectedOptionText = "Select a saved robot...")
                }
                is OnboardingIntent.UpdateSeasonId -> updateRequiredField { it.copy(seasonId = intent.seasonId) }
                is OnboardingIntent.UpdateRobotId -> updateRequiredField { it.copy(robotId = intent.robotId) }
                is OnboardingIntent.UpdateRobotName -> _state.update { it.copy(robotName = intent.robotName) }
                is OnboardingIntent.UpdateLeague -> _state.update {
                    it.copy(
                        league = intent.league,
                        nt4Host = environmentService.getDefaultNt4Host(intent.league, it.teamId),
                    )
                }
                is OnboardingIntent.UpdateNt4Host -> _state.update { it.copy(nt4Host = intent.nt4Host) }
                is OnboardingIntent.UpdateGoogleClientId -> _state.update { it.copy(googleClientId = intent.googleClientId) }
                is OnboardingIntent.UpdateGoogleClientSecret -> _state.update { it.copy(googleClientSecret = intent.googleClientSecret) }
                is OnboardingIntent.UpdateSimulatorCommand -> _state.update { it.copy(simulatorCommand = intent.simulatorCommand) }
                is OnboardingIntent.UpdateSelectedOptionText -> _state.update { it.copy(selectedOptionText = intent.text) }
                is OnboardingIntent.SetCloudSetupExpanded -> _state.update { it.copy(cloudSetupExpanded = intent.expanded) }
                is OnboardingIntent.SetAdvancedSetupExpanded -> _state.update { it.copy(advancedSetupExpanded = intent.expanded) }
                is OnboardingIntent.FetchCloudRobots -> fetchCloudRobots()
                OnboardingIntent.DetectLeague -> detectProject()
                OnboardingIntent.NextStep -> moveNext()
                OnboardingIntent.PreviousStep -> _state.update {
                    it.copy(currentStep = OnboardingStep.entries[(it.currentStep.ordinal - 1).coerceAtLeast(0)], errorMessage = null)
                }
                OnboardingIntent.VerifyJava -> verifyJava17()
                OnboardingIntent.SubmitConfig -> submitConfig()
            }
        }
    }

    private fun updateRequiredField(transform: (OnboardingState) -> OnboardingState) {
        _state.update { transform(it).copy(fieldErrors = OnboardingFieldErrors(), errorMessage = null) }
    }

    private suspend fun fetchCloudRobots() {
        if (_state.value.teamId.isBlank()) {
            _state.update { it.copy(cloudRobots = emptyList()) }
            return
        }
        _state.update { it.copy(isCloudLoading = true) }
        try {
            val robots = syncEngineService.getRemoteRobotProfiles()
            _state.update { it.copy(cloudRobots = robots, isCloudLoading = false) }
        } catch (_: Exception) {
            _state.update { it.copy(cloudRobots = emptyList(), isCloudLoading = false) }
        }
    }

    private suspend fun detectProject() {
        val path = _state.value.projectPath.trim()
        val directory = File(path)
        if (path.isEmpty() || !directory.isDirectory) {
            _state.update {
                it.copy(
                    projectDetectionMessage = null,
                    fieldErrors = it.fieldErrors.copy(projectPath = "Choose a folder that contains your robot project."),
                )
            }
            return
        }

        val robotConfig = environmentService.readAresRobotJson(path)
        if (robotConfig != null) {
            val detectedLeague = if (robotConfig.league.equals("FRC", ignoreCase = true)) League.FRC else League.FTC
            _state.update {
                it.copy(
                    teamId = robotConfig.teamId,
                    seasonId = robotConfig.seasonId,
                    robotId = robotConfig.robotId,
                    robotName = robotConfig.name,
                    league = detectedLeague,
                    nt4Host = environmentService.getDefaultNt4Host(detectedLeague, robotConfig.teamId),
                    projectDetectionMessage = "Project found. We filled in the ${detectedLeague.name} robot details from .ares-robot.json.",
                    fieldErrors = OnboardingFieldErrors(),
                )
            }
        } else {
            val detectedLeague = environmentService.detectLeague(path)
            _state.update {
                it.copy(
                    league = detectedLeague,
                    nt4Host = environmentService.getDefaultNt4Host(detectedLeague, it.teamId),
                    projectDetectionMessage = "Project found. We detected ${detectedLeague.name}; add the robot details on the next step.",
                    fieldErrors = it.fieldErrors.copy(projectPath = null),
                )
            }
        }
    }

    private fun moveNext() {
        val current = _state.value
        val errors = validateOnboardingFields(current, current.currentStep)
        if (errors.hasRequiredFieldErrors) {
            _state.update { it.copy(fieldErrors = errors, errorMessage = "Check the highlighted field before continuing.") }
            return
        }
        val next = OnboardingStep.entries[(current.currentStep.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)]
        _state.update { it.copy(currentStep = next, fieldErrors = errors, errorMessage = null) }
    }

    private suspend fun verifyJava17() {
        _state.update { it.copy(isVerifyingJava = true, fieldErrors = it.fieldErrors.copy(java = null)) }
        val result = environmentService.verifyJavaEnvironment()
        val javaReadiness = evaluateJava17(result.isValid, result.message)
        _state.update {
            it.copy(
                isVerifyingJava = false,
                javaEnvValid = javaReadiness.isValid,
                javaEnvMsg = javaReadiness.message,
                javaMajorVersion = javaReadiness.majorVersion,
                fieldErrors = it.fieldErrors.copy(java = javaReadiness.message.takeUnless { javaReadiness.isValid }),
            )
        }
    }

    private suspend fun submitConfig() {
        val current = _state.value
        val errors = validateOnboardingFields(current, OnboardingStep.REVIEW).copy(
            java = current.javaEnvMsg.takeUnless { current.javaEnvValid == true },
        )
        if (errors.hasRequiredFieldErrors || errors.java != null) {
            _state.update {
                it.copy(
                    currentStep = if (errors.hasRequiredFieldErrors) {
                        if (errors.projectPath != null) OnboardingStep.PROJECT else OnboardingStep.ROBOT
                    } else {
                        OnboardingStep.REVIEW
                    },
                    fieldErrors = errors,
                    errorMessage = "Finish the required setup before creating this workspace.",
                )
            }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        try {
            val config = WorkspaceConfig(
                teamId = current.teamId.trim(),
                seasonId = current.seasonId.trim(),
                robotId = current.robotId.trim(),
                robotName = current.robotName.trim(),
                projectPath = current.projectPath.trim(),
                league = current.league,
                nt4Host = current.nt4Host.trim().takeIf(String::isNotEmpty),
                googleClientId = current.googleClientId.trim().takeIf(String::isNotEmpty),
                googleClientSecret = current.googleClientSecret.trim().takeIf(String::isNotEmpty),
                simulatorCommand = current.simulatorCommand.trim().takeIf(String::isNotEmpty),
            )
            environmentService.saveConfig(config)

            // Cloud registration is best effort. A local workspace is complete without sign-in.
            try {
                val profile = RobotProfile(
                    robotId = current.robotId.trim(),
                    league = current.league,
                    seasonId = current.seasonId.trim(),
                    name = current.robotName.trim().ifEmpty { "${current.robotId.trim()} Local Config" },
                )
                syncEngineService.mutateRemoteRobotProfiles { existing ->
                    if (existing.none { it.robotId == profile.robotId }) existing + profile else existing
                }
            } catch (_: Exception) {
                // Offline setup is intentionally sufficient.
            }

            _state.update { it.copy(isSaving = false, saveSuccess = true) }
            onConfigured(config)
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isSaving = false,
                    errorMessage = "We couldn't save this workspace. ${e.message ?: "Please try again."}",
                )
            }
        }
    }
}

internal data class Java17Readiness(
    val isValid: Boolean,
    val majorVersion: Int?,
    val message: String,
)

internal fun evaluateJava17(commandSucceeded: Boolean, rawMessage: String): Java17Readiness {
    if (!commandSucceeded) {
        return Java17Readiness(
            isValid = false,
            majorVersion = null,
            message = "JDK 17 is required. Java could not be started. Set JAVA_HOME to a JDK 17 installation, then check again.",
        )
    }
    val major = parseJavaMajorVersion(rawMessage)
    return when (major) {
        17 -> Java17Readiness(true, 17, "JDK 17 is ready.")
        null -> Java17Readiness(
            false,
            null,
            "JDK 17 is required, but the installed Java version could not be identified. Set JAVA_HOME to a JDK 17 installation, then check again.",
        )
        else -> Java17Readiness(
            false,
            major,
            "JDK 17 is required. We found Java $major. Set JAVA_HOME to a JDK 17 installation, then check again.",
        )
    }
}

internal fun parseJavaMajorVersion(message: String): Int? {
    val version = Regex("(?:java|openjdk) version \"([^\"]+)\"", RegexOption.IGNORE_CASE)
        .find(message)
        ?.groupValues
        ?.get(1)
        ?: Regex("version[=: ]+([0-9]+(?:\\.[0-9]+)*)", RegexOption.IGNORE_CASE)
            .find(message)
            ?.groupValues
            ?.get(1)
        ?: return null
    val parts = version.split('.')
    val first = parts.firstOrNull()?.takeWhile(Char::isDigit)?.toIntOrNull() ?: return null
    return if (first == 1) parts.getOrNull(1)?.takeWhile(Char::isDigit)?.toIntOrNull() else first
}

internal fun validateOnboardingFields(
    state: OnboardingState,
    throughStep: OnboardingStep,
): OnboardingFieldErrors {
    val validateProject = throughStep.ordinal >= OnboardingStep.PROJECT.ordinal
    val validateRobot = throughStep.ordinal >= OnboardingStep.ROBOT.ordinal
    return OnboardingFieldErrors(
        projectPath = when {
            !validateProject -> null
            state.projectPath.isBlank() -> "Choose your robot project folder."
            !File(state.projectPath.trim()).isDirectory -> "This folder does not exist or cannot be opened."
            else -> null
        },
        teamId = when {
            !validateRobot -> null
            state.teamId.isBlank() -> "Enter your FIRST team number."
            state.teamId.any { !it.isDigit() } -> "Use numbers only for the team number."
            else -> null
        },
        seasonId = when {
            !validateRobot -> null
            state.seasonId.isBlank() -> "Enter the season, for example 2026."
            else -> null
        },
        robotId = when {
            !validateRobot -> null
            state.robotId.isBlank() -> "Enter a short robot ID."
            else -> null
        },
    )
}

internal const val DEFAULT_GOOGLE_CLIENT_ID =
    "205869391101-nlcsea4539vjuo50i58bpo0t10d5s0ic.apps.googleusercontent.com"
