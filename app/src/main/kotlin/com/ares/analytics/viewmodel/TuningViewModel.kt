package com.ares.analytics.viewmodel

import com.ares.analytics.service.Nt4ClientService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class TuningState(
    val variables: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false,
    val saveStatus: String = "",
    val errorMessage: String? = null
)

sealed class TuningIntent {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class LoadConstants(val projectPath: String) : TuningIntent() // Kept for compatibility with existing UI if it passes projectPath
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SaveConstant(val key: String, val newValue: Double) : TuningIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ClearSaveStatus : TuningIntent()
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class TuningViewModel(
    val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(TuningState())
    val state: StateFlow<TuningState> = _state.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                val topics = nt4ClientService.getActiveTopics().filter { it.startsWith("Tuning/") }
                val currentMap = _state.value.variables.toMutableMap()
                var changed = false
                
                // Track missing keys to remove them if a new session starts
                val activeKeys = mutableSetOf<String>()

                for (topic in topics) {
                    activeKeys.add(topic)
                    val value = nt4ClientService.latestValues[topic]?.value ?: 0.0
                    if (currentMap[topic] != value) {
                        currentMap[topic] = value
                        changed = true
                    }
                }
                val keysToRemove = currentMap.keys - activeKeys
                if (keysToRemove.isNotEmpty()) {
                    keysToRemove.forEach { currentMap.remove(it) }
                    changed = true
                }

                if (changed) {
                    _state.update { it.copy(variables = currentMap) }
                }
                
                delay(200) // Poll at 5Hz
            }
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun onIntent(intent: TuningIntent) {
        scope.launch {
            when (intent) {
                is TuningIntent.LoadConstants -> {
                    // No-op. Data streams live from NT4 automatically.
                }
                is TuningIntent.SaveConstant -> {
                    _state.update { it.copy(saveStatus = "") }
                    try {
                        nt4ClientService.publishDouble(intent.key, intent.newValue)
                        _state.update { it.copy(saveStatus = "Updated ${intent.key.removePrefix("Tuning/")}") }
                    } catch (e: Exception) {
                        _state.update { it.copy(errorMessage = e.message ?: "Failed to save constant") }
                    }
                }
                is TuningIntent.ClearSaveStatus -> {
                    _state.update { it.copy(saveStatus = "") }
                }
            }
        }
    }
}
