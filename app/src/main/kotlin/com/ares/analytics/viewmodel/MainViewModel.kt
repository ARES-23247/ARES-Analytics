package com.ares.analytics.viewmodel

import com.ares.analytics.service.EnvironmentService
import com.ares.analytics.service.EventApiService
import com.ares.analytics.service.MatchInfo
import com.ares.analytics.shared.ForensicsResponse
import com.ares.analytics.ui.components.NavigationTarget
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.shared.League
import com.ares.analytics.shared.AppWorkspaces
import com.ares.analytics.shared.ControllerBinding
import com.ares.analytics.service.KeybindingParserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class MainState(
    val config: WorkspaceConfig? = null,
    val workspaces: List<WorkspaceConfig> = emptyList(),
    val activeNav: NavigationTarget = NavigationTarget.DASHBOARD,
    val matches: List<MatchInfo> = emptyList(),
    val runsIndexReloadTrigger: Int = 0,
    val diagnosticsResponse: ForensicsResponse? = null,
    val isTerminalOpen: Boolean = false,
    val isKeybindingsOpen: Boolean = false,
    val parsedBindings: List<ControllerBinding> = emptyList(),
    val showUpdateBanner: Boolean = true
)

sealed class MainIntent {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object LoadConfig : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetActiveNav(val nav: NavigationTarget) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetMatches(val matches: List<MatchInfo>) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object TriggerRunsIndexReload : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetDiagnosticsResponse(val response: ForensicsResponse?) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetTerminalOpen(val isOpen: Boolean) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetKeybindingsOpen(val isOpen: Boolean) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object RefreshKeybindings : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetShowUpdateBanner(val show: Boolean) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SaveConfig(val config: WorkspaceConfig) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SelectWorkspace(val id: String) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class DeleteWorkspace(val id: String) : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object AddNewWorkspace : MainIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object CancelAddNewWorkspace : MainIntent()
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class MainViewModel(
    private val environmentService: EnvironmentService,
    private val eventApiService: EventApiService,
    private val keybindingParserService: KeybindingParserService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    init {
        onIntent(MainIntent.LoadConfig)
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun onIntent(intent: MainIntent) {
        scope.launch {
            when (intent) {
                is MainIntent.LoadConfig -> {
                    val app = environmentService.loadWorkspaces()
                    val active = app.workspaces.find { it.id == app.activeWorkspaceId } ?: app.workspaces.firstOrNull()
                    _state.update { it.copy(config = active, workspaces = app.workspaces) }
                    if (active != null) {
                        loadMatchSchedule(active)
                    }
                }
                is MainIntent.SetActiveNav -> {
                    _state.update { it.copy(activeNav = intent.nav) }
                }
                is MainIntent.SetMatches -> {
                    _state.update { it.copy(matches = intent.matches) }
                }
                is MainIntent.TriggerRunsIndexReload -> {
                    _state.update { it.copy(runsIndexReloadTrigger = it.runsIndexReloadTrigger + 1) }
                }
                is MainIntent.SetDiagnosticsResponse -> {
                    _state.update { it.copy(diagnosticsResponse = intent.response) }
                }
                is MainIntent.SetTerminalOpen -> {
                    _state.update { it.copy(isTerminalOpen = intent.isOpen) }
                }
                is MainIntent.SetKeybindingsOpen -> {
                    _state.update { it.copy(isKeybindingsOpen = intent.isOpen) }
                    if (intent.isOpen && _state.value.parsedBindings.isEmpty()) {
                        onIntent(MainIntent.RefreshKeybindings)
                    }
                }
                is MainIntent.RefreshKeybindings -> {
                    val path = _state.value.config?.projectPath
                    if (path != null && path.isNotEmpty()) {
                        val bindings = keybindingParserService.parseBindings(path)
                        _state.update { it.copy(parsedBindings = bindings) }
                    }
                }
                is MainIntent.SetShowUpdateBanner -> {
                    _state.update { it.copy(showUpdateBanner = intent.show) }
                }
                is MainIntent.SaveConfig -> {
                    val configWithId = if (intent.config.id.isEmpty()) {
                        intent.config.copy(id = "${intent.config.league}-${intent.config.teamId}-${intent.config.robotId}-${intent.config.seasonId}")
                    } else {
                        intent.config
                    }
                    val app = environmentService.loadWorkspaces()
                    val newList = app.workspaces.filter { it.id != configWithId.id } + configWithId
                    val newApp = com.ares.analytics.shared.AppWorkspaces(activeWorkspaceId = configWithId.id, workspaces = newList)
                    environmentService.saveWorkspaces(newApp)

                    _state.update { it.copy(config = configWithId, workspaces = newList) }
                    loadMatchSchedule(configWithId)
                }
                is MainIntent.SelectWorkspace -> {
                    val app = environmentService.loadWorkspaces()
                    val target = app.workspaces.find { it.id == intent.id }
                    if (target != null) {
                        val newApp = app.copy(activeWorkspaceId = target.id)
                        environmentService.saveWorkspaces(newApp)
                        _state.update { it.copy(config = target, workspaces = app.workspaces) }
                        loadMatchSchedule(target)
                    }
                }
                is MainIntent.DeleteWorkspace -> {
                    val app = environmentService.loadWorkspaces()
                    val newList = app.workspaces.filter { it.id != intent.id }
                    val newActiveId = if (app.activeWorkspaceId == intent.id) {
                        newList.firstOrNull()?.id
                    } else {
                        app.activeWorkspaceId
                    }
                    val newApp = com.ares.analytics.shared.AppWorkspaces(activeWorkspaceId = newActiveId, workspaces = newList)
                    environmentService.saveWorkspaces(newApp)
                    val newActiveConfig = newList.find { it.id == newActiveId }
                    _state.update { it.copy(config = newActiveConfig, workspaces = newList) }
                    if (newActiveConfig != null) {
                        loadMatchSchedule(newActiveConfig)
                    } else {
                        _state.update { it.copy(matches = emptyList()) }
                    }
                }
                is MainIntent.AddNewWorkspace -> {
                    _state.update { it.copy(config = null) }
                }
                is MainIntent.CancelAddNewWorkspace -> {
                    val app = environmentService.loadWorkspaces()
                    val active = app.workspaces.find { it.id == app.activeWorkspaceId } ?: app.workspaces.firstOrNull()
                    if (active != null) {
                        _state.update { it.copy(config = active) }
                    }
                }
            }
        }
    }

    private suspend fun loadMatchSchedule(currentConf: WorkspaceConfig) {
        val eventCode = currentConf.eventCode
        if (eventCode != null && eventCode.isNotEmpty()) {
            val list = if (currentConf.league == League.FTC) {
                val apiKey = currentConf.toaApiKey ?: ""
                eventApiService.fetchFtcEventSchedule(eventCode, apiKey)
            } else {
                val apiKey = currentConf.tbaApiKey ?: ""
                eventApiService.fetchFrcEventSchedule(eventCode, apiKey)
            }
            _state.update { it.copy(matches = list) }
        } else {
            _state.update { it.copy(matches = emptyList()) }
        }
    }
}
