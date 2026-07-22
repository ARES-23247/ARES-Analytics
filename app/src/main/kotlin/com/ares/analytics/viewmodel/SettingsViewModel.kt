package com.ares.analytics.viewmodel

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.EnvironmentService
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class SettingsState(
    val config: WorkspaceConfig? = null,
    val theme: String = "Dark", // "System", "Light", "Dark"
    val audibleAlertsEnabled: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val autoSyncEnabled: Boolean = false,
    val saveStatus: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class SettingsIntent {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class LoadSettings(val config: WorkspaceConfig) : SettingsIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SaveConfig(val config: WorkspaceConfig) : SettingsIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class UpdateTheme(val theme: String) : SettingsIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetAudibleAlertsEnabled(val enabled: Boolean) : SettingsIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetAutoCheckUpdates(val enabled: Boolean) : SettingsIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetAutoSyncEnabled(val enabled: Boolean) : SettingsIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ClearDatabase : SettingsIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ClearSaveStatus : SettingsIntent()
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class SettingsViewModel(
    private val databaseService: DatabaseService,
    private val environmentService: EnvironmentService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun onIntent(intent: SettingsIntent) {
        scope.launch {
            when (intent) {
                is SettingsIntent.LoadSettings -> {
                    _state.update { it.copy(config = intent.config) }
                }
                is SettingsIntent.SaveConfig -> {
                    _state.update { it.copy(isLoading = true, saveStatus = "", errorMessage = null) }
                    try {
                        withContext(Dispatchers.IO) {
                            environmentService.saveConfig(intent.config)
                        }
                        _state.update { it.copy(config = intent.config, saveStatus = "Settings saved successfully!") }
                    } catch (e: Exception) {
                        _state.update { it.copy(errorMessage = e.message ?: "Failed to save settings") }
                    } finally {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
                is SettingsIntent.UpdateTheme -> {
                    _state.update { it.copy(theme = intent.theme) }
                }
                is SettingsIntent.SetAudibleAlertsEnabled -> {
                    _state.update { it.copy(audibleAlertsEnabled = intent.enabled) }
                }
                is SettingsIntent.SetAutoCheckUpdates -> {
                    _state.update { it.copy(autoCheckUpdates = intent.enabled) }
                }
                is SettingsIntent.SetAutoSyncEnabled -> {
                    _state.update { it.copy(autoSyncEnabled = intent.enabled) }
                }
                is SettingsIntent.ClearDatabase -> {
                    _state.update { it.copy(isLoading = true, saveStatus = "", errorMessage = null) }
                    try {
                        withContext(Dispatchers.IO) {
                            val sessions = databaseService.getSessions()
                            sessions.forEach {
                                databaseService.deleteSession(it.sessionId)
                            }
                        }
                        _state.update { it.copy(saveStatus = "Database cleared successfully!") }
                    } catch (e: Exception) {
                        _state.update { it.copy(errorMessage = e.message ?: "Failed to clear database") }
                    } finally {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
                is SettingsIntent.ClearSaveStatus -> {
                    _state.update { it.copy(saveStatus = "") }
                }
            }
        }
    }
}
