package com.ares.analytics.viewmodel

import com.ares.analytics.service.versioncontrol.GitHubConnectionState
import com.ares.analytics.service.versioncontrol.GitHubBackupCatalog
import com.ares.analytics.service.versioncontrol.ProjectBackupPlan
import com.ares.analytics.service.versioncontrol.ProjectVersionControlService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectBackupState(
    val projectPath: String = "",
    val plan: ProjectBackupPlan? = null,
    val githubState: GitHubConnectionState = GitHubConnectionState.Disconnected,
    val githubCatalog: GitHubBackupCatalog = GitHubBackupCatalog(),
    val selectedInstallationId: Long? = null,
    val isBusy: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
)

sealed class ProjectBackupIntent {
    data class Load(val projectPath: String) : ProjectBackupIntent()
    object Refresh : ProjectBackupIntent()
    data class StartLocalHistory(val authorName: String, val authorEmail: String) : ProjectBackupIntent()
    data class SaveVersion(
        val confirmationToken: String,
        val message: String,
        val authorName: String,
        val authorEmail: String,
    ) : ProjectBackupIntent()
    object SignInToGitHub : ProjectBackupIntent()
    object OpenGitHubAppInstallation : ProjectBackupIntent()
    object RefreshGitHubDestinations : ProjectBackupIntent()
    data class SelectGitHubInstallation(val installationId: Long) : ProjectBackupIntent()
    data class ConnectGitHubRepository(val installationId: Long, val repositoryId: Long) : ProjectBackupIntent()
    object SyncGitHubBackup : ProjectBackupIntent()
    object DisconnectGitHubDestination : ProjectBackupIntent()
    object DisconnectGitHub : ProjectBackupIntent()
    object ClearMessage : ProjectBackupIntent()
}

/** Coordinates review-first project history and optional private GitHub backup. */
class ProjectBackupViewModel(
    private val service: ProjectVersionControlService,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ProjectBackupState(githubState = service.githubState.value))
    val state: StateFlow<ProjectBackupState> = _state.asStateFlow()

    init {
        scope.launch {
            service.githubState.collectLatest { github ->
                _state.update { it.copy(githubState = github) }
            }
        }
    }

    fun onIntent(intent: ProjectBackupIntent) {
        when (intent) {
            is ProjectBackupIntent.Load -> load(intent.projectPath)
            ProjectBackupIntent.Refresh -> load(_state.value.projectPath)
            ProjectBackupIntent.ClearMessage -> _state.update { it.copy(notice = null, error = null) }
            ProjectBackupIntent.DisconnectGitHub -> runAction(
                "GitHub was disconnected. Local versions remain on this computer.",
                clearCatalog = true,
            ) {
                service.disconnectGitHub()
                service.inspect(requireProjectPath())
            }
            ProjectBackupIntent.SignInToGitHub -> runAction(
                "GitHub is connected. Choose an approved personal or team repository.",
                refreshCatalog = true,
            ) {
                service.signInToGitHub()
                service.inspect(requireProjectPath())
            }
            ProjectBackupIntent.OpenGitHubAppInstallation -> runAction(
                "GitHub opened the ARES App installation page. Return here and refresh destinations after approval.",
            ) {
                service.openGitHubAppInstallation()
                service.inspect(requireProjectPath())
            }
            ProjectBackupIntent.RefreshGitHubDestinations -> runAction(
                "GitHub destinations and permissions were refreshed.",
                refreshCatalog = true,
            ) { service.inspect(requireProjectPath()) }
            is ProjectBackupIntent.SelectGitHubInstallation -> _state.update {
                it.copy(selectedInstallationId = intent.installationId, notice = null, error = null)
            }
            is ProjectBackupIntent.StartLocalHistory -> runAction("Local version history is ready. Review the files below, then save your first version.") {
                service.initialize(requireProjectPath(), intent.authorName, intent.authorEmail)
            }
            is ProjectBackupIntent.SaveVersion -> runAction("Version saved locally. Your working files were not moved or replaced.") {
                service.commit(
                    requireProjectPath(),
                    intent.confirmationToken,
                    intent.message,
                    intent.authorName,
                    intent.authorEmail,
                )
            }
            is ProjectBackupIntent.ConnectGitHubRepository -> runAction(
                "Approved GitHub repository connected and synchronized.",
                refreshCatalog = true,
            ) {
                service.connectApprovedRepository(
                    requireProjectPath(),
                    intent.installationId,
                    intent.repositoryId,
                )
            }
            ProjectBackupIntent.SyncGitHubBackup -> runAction(
                "GitHub backup is up to date.",
                refreshCatalog = true,
            ) {
                service.pushBackup(requireProjectPath())
            }
            ProjectBackupIntent.DisconnectGitHubDestination -> runAction(
                "The online destination was disconnected. No local or GitHub files were deleted.",
            ) { service.disconnectBackupDestination(requireProjectPath()) }
        }
    }

    private fun load(projectPath: String) {
        _state.update { it.copy(projectPath = projectPath, notice = null, error = null) }
        if (projectPath.isBlank()) {
            _state.update { it.copy(plan = null, error = "Choose a robot project before opening Project Backup.") }
            return
        }
        runAction(
            notice = null,
            refreshCatalog = _state.value.githubState is GitHubConnectionState.Connected,
        ) { service.inspect(projectPath) }
    }

    private fun runAction(
        notice: String?,
        refreshCatalog: Boolean = false,
        clearCatalog: Boolean = false,
        block: suspend () -> ProjectBackupPlan,
    ) {
        if (_state.value.isBusy) return
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null, notice = null) }
            try {
                val plan = block()
                val catalog = when {
                    clearCatalog -> GitHubBackupCatalog()
                    refreshCatalog -> service.discoverGitHubDestinations()
                    else -> _state.value.githubCatalog
                }
                _state.update { current ->
                    val selected = current.selectedInstallationId
                        ?.takeIf { id -> catalog.accounts.any { it.installationId == id } }
                        ?: catalog.accounts.firstOrNull()?.installationId
                    current.copy(
                        plan = plan,
                        githubCatalog = catalog,
                        selectedInstallationId = selected,
                        isBusy = false,
                        notice = notice,
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(isBusy = false) }
                throw cancelled
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isBusy = false,
                        error = failure.message ?: "Project Backup could not complete that action.",
                    )
                }
            }
        }
    }

    private fun requireProjectPath(): String = _state.value.projectPath.takeIf(String::isNotBlank)
        ?: error("Choose a robot project before opening Project Backup.")
}
