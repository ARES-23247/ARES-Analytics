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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

data class TuningState(
    val variables: Map<String, Double> = emptyMap(),      // Live values from Robot over NT4
    val appVariables: Map<String, Double> = emptyMap(),   // Local App JSON values (robot_constants.json)
    val projectPath: String = "",
    val isLoading: Boolean = false,
    val saveStatus: String = "",
    val errorMessage: String? = null
)

sealed class TuningIntent {
    data class LoadConstants(val projectPath: String) : TuningIntent()
    data class UpdateAppConstant(val key: String, val newValue: Double) : TuningIntent()
    data class SaveConstant(val key: String, val newValue: Double) : TuningIntent()
    data class PushToRobot(val key: String) : TuningIntent()
    data class PullFromRobot(val key: String) : TuningIntent()
    object PushAllToRobot : TuningIntent()
    object PullAllFromRobot : TuningIntent()
    object ClearSaveStatus : TuningIntent()
}

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
                    _state.update { currentState ->
                        // Auto-seed appVariables for any new keys if appVariables doesn't have them yet
                        val updatedAppVars = currentState.appVariables.toMutableMap()
                        var appVarsChanged = false
                        currentMap.forEach { (k, v) ->
                            if (!updatedAppVars.containsKey(k)) {
                                updatedAppVars[k] = v
                                appVarsChanged = true
                            }
                        }
                        if (appVarsChanged && currentState.projectPath.isNotBlank()) {
                            saveAppConstants(currentState.projectPath, updatedAppVars)
                        }
                        currentState.copy(
                            variables = currentMap,
                            appVariables = if (appVarsChanged) updatedAppVars else currentState.appVariables
                        )
                    }
                }
                
                delay(200) // Poll at 5Hz
            }
        }
    }

    fun onIntent(intent: TuningIntent) {
        scope.launch {
            when (intent) {
                is TuningIntent.LoadConstants -> {
                    val path = intent.projectPath
                    if (path.isNotBlank()) {
                        val loadedMap = withContext(Dispatchers.IO) { loadAppConstants(path) }
                        _state.update { currentState ->
                            val mergedAppVars = loadedMap.toMutableMap()
                            // Also populate from current live variables if missing
                            currentState.variables.forEach { (k, v) ->
                                if (!mergedAppVars.containsKey(k)) {
                                    mergedAppVars[k] = v
                                }
                            }
                            currentState.copy(projectPath = path, appVariables = mergedAppVars)
                        }
                    }
                }
                is TuningIntent.UpdateAppConstant -> {
                    val updated = _state.value.appVariables + (intent.key to intent.newValue)
                    _state.update { it.copy(appVariables = updated) }
                    withContext(Dispatchers.IO) {
                        saveAppConstants(_state.value.projectPath, updated)
                    }
                }
                is TuningIntent.SaveConstant -> {
                    val updated = _state.value.appVariables + (intent.key to intent.newValue)
                    _state.update { it.copy(appVariables = updated, saveStatus = "") }
                    withContext(Dispatchers.IO) {
                        saveAppConstants(_state.value.projectPath, updated)
                    }
                    try {
                        nt4ClientService.publishDouble(intent.key, intent.newValue)
                        _state.update { it.copy(saveStatus = "Updated & Pushed ${intent.key.removePrefix("Tuning/")}") }
                    } catch (e: Exception) {
                        _state.update { it.copy(errorMessage = e.message ?: "Failed to push constant") }
                    }
                }
                is TuningIntent.PushToRobot -> {
                    val appVal = _state.value.appVariables[intent.key]
                    if (appVal != null) {
                        try {
                            nt4ClientService.publishDouble(intent.key, appVal)
                            _state.update { it.copy(saveStatus = "Pushed App Value to Robot: ${intent.key.removePrefix("Tuning/")} = $appVal") }
                        } catch (e: Exception) {
                            _state.update { it.copy(errorMessage = e.message ?: "Failed to push to robot") }
                        }
                    }
                }
                is TuningIntent.PullFromRobot -> {
                    val robotVal = _state.value.variables[intent.key]
                    if (robotVal != null) {
                        val updated = _state.value.appVariables + (intent.key to robotVal)
                        _state.update { it.copy(appVariables = updated, saveStatus = "Pulled Robot Value to App: ${intent.key.removePrefix("Tuning/")} = $robotVal") }
                        withContext(Dispatchers.IO) {
                            saveAppConstants(_state.value.projectPath, updated)
                        }
                    }
                }
                is TuningIntent.PushAllToRobot -> {
                    val appVars = _state.value.appVariables
                    var count = 0
                    appVars.forEach { (key, value) ->
                        try {
                            nt4ClientService.publishDouble(key, value)
                            count++
                        } catch (_: Exception) {}
                    }
                    _state.update { it.copy(saveStatus = "Pushed $count App values to Robot") }
                }
                is TuningIntent.PullAllFromRobot -> {
                    val liveVars = _state.value.variables
                    if (liveVars.isNotEmpty()) {
                        val updated = _state.value.appVariables + liveVars
                        _state.update { it.copy(appVariables = updated, saveStatus = "Pulled ${liveVars.size} Robot values into App JSON") }
                        withContext(Dispatchers.IO) {
                            saveAppConstants(_state.value.projectPath, updated)
                        }
                    } else {
                        _state.update { it.copy(errorMessage = "No active Robot variables to pull") }
                    }
                }
                is TuningIntent.ClearSaveStatus -> {
                    _state.update { it.copy(saveStatus = "") }
                }
            }
        }
    }

    private fun loadAppConstants(projectPath: String): Map<String, Double> {
        if (projectPath.isBlank()) return emptyMap()
        val file = File(projectPath, "robot_constants.json")
        if (!file.exists()) return emptyMap()
        return try {
            val text = file.readText()
            val jsonObj = Json.parseToJsonElement(text).jsonObject
            jsonObj.mapValues { it.value.jsonPrimitive.double }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveAppConstants(projectPath: String, map: Map<String, Double>) {
        if (projectPath.isBlank()) return
        try {
            val file = File(projectPath, "robot_constants.json")
            val jsonMap = map.mapValues { JsonPrimitive(it.value) }
            val jsonObj = JsonObject(jsonMap)
            val jsonFormatter = Json { prettyPrint = true }
            file.writeText(jsonFormatter.encodeToString(JsonObject.serializer(), jsonObj))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
