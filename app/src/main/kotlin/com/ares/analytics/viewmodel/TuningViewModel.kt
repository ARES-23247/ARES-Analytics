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
import com.areslib.tuning.TuningTopics

data class BackupInfo(
    val filename: String,
    val formattedDate: String,
    val filePath: String,
    val count: Int
)

data class TuningState(
    val variables: Map<String, Double> = emptyMap(),      // Live values from Robot over NT4
    val appVariables: Map<String, Double> = emptyMap(),   // Local App JSON values (robot_constants.json)
    val projectPath: String = "",
    val availableBackups: List<BackupInfo> = emptyList(),
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
    object CreateBackup : TuningIntent()
    data class LoadBackup(val filename: String) : TuningIntent()
    object RefreshBackups : TuningIntent()
    object ClearSaveStatus : TuningIntent()
}

/** Maintains editable tuning values and synchronizes them with robot NT4 topics and source files. */
class TuningViewModel(
    val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(TuningState())
    val state: StateFlow<TuningState> = _state.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                val topics = nt4ClientService.getActiveTopics().filter {
                    it.startsWith("Tuning/") && it != TuningTopics.SCHEMA_VERSION_TOPIC
                }
                val currentMap = _state.value.variables.toMutableMap()
                var changed = false

                val activeKeys = mutableSetOf<String>()

                for (topic in topics) {
                    val canonicalTopic = TuningTopics.canonicalize(topic)
                    activeKeys.add(canonicalTopic)
                    val value = nt4ClientService.latestValues[topic]?.value ?: 0.0
                    if (currentMap[canonicalTopic] != value) {
                        currentMap[canonicalTopic] = value
                        changed = true
                    }
                }
                val keysToRemove = currentMap.keys - activeKeys
                if (keysToRemove.isNotEmpty()) {
                    keysToRemove.forEach { currentMap.remove(it) }
                    changed = true
                }

                if (changed) {
                    // Live telemetry is observation only. Project source changes require an
                    // explicit Pull action so connecting a robot cannot rewrite constants.
                    _state.update { currentState -> currentState.copy(variables = currentMap) }
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
                    if (path.isBlank()) {
                        _state.update {
                            it.copy(
                                projectPath = "",
                                appVariables = emptyMap(),
                                availableBackups = emptyList(),
                                saveStatus = "",
                                errorMessage = null
                            )
                        }
                    } else {
                        val loadedMap = withContext(Dispatchers.IO) { loadAppConstants(path) }
                        val backups = withContext(Dispatchers.IO) { listBackups(path) }
                        _state.update { currentState ->
                            currentState.copy(
                                projectPath = path,
                                appVariables = loadedMap,
                                availableBackups = backups,
                                saveStatus = "",
                                errorMessage = null
                            )
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
                        } catch (_: Exception) {
                            // Continue the batch; the success count intentionally excludes failed topics.
                        }
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
                is TuningIntent.CreateBackup -> {
                    val appVars = _state.value.appVariables
                    val path = _state.value.projectPath
                    if (appVars.isNotEmpty() && path.isNotBlank()) {
                        val filename = withContext(Dispatchers.IO) { createBackup(path, appVars) }
                        if (filename != null) {
                            val backups = withContext(Dispatchers.IO) { listBackups(path) }
                            _state.update { it.copy(availableBackups = backups, saveStatus = "Backup saved: $filename (${appVars.size} constants)") }
                        } else {
                            _state.update { it.copy(errorMessage = "Failed to create backup file") }
                        }
                    } else {
                        _state.update { it.copy(errorMessage = "No constants available to backup") }
                    }
                }
                is TuningIntent.LoadBackup -> {
                    val path = _state.value.projectPath
                    val file = File(File(path, "constants_backups"), intent.filename)
                    if (file.exists()) {
                        val loadedMap = withContext(Dispatchers.IO) {
                            try {
                                val text = file.readText()
                                val jsonObj = Json.parseToJsonElement(text).jsonObject
                                jsonObj.mapValues { it.value.jsonPrimitive.double }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        if (loadedMap != null) {
                            _state.update { currentState ->
                                currentState.copy(
                                    appVariables = loadedMap,
                                    saveStatus = "Loaded backup '${intent.filename}' (${loadedMap.size} constants restored)"
                                )
                            }
                            withContext(Dispatchers.IO) {
                                saveAppConstants(path, loadedMap)
                            }
                        } else {
                            _state.update { it.copy(errorMessage = "Failed to parse backup file ${intent.filename}") }
                        }
                    }
                }
                is TuningIntent.RefreshBackups -> {
                    val path = _state.value.projectPath
                    if (path.isNotBlank()) {
                        val backups = withContext(Dispatchers.IO) { listBackups(path) }
                        _state.update { it.copy(availableBackups = backups) }
                    }
                }
                is TuningIntent.ClearSaveStatus -> {
                    _state.update { it.copy(saveStatus = "") }
                }
            }
        }
    }

    private fun createBackup(projectPath: String, map: Map<String, Double>): String? {
        if (projectPath.isBlank() || map.isEmpty()) return null
        val dir = File(projectPath, "constants_backups")
        if (!dir.exists()) dir.mkdirs()

        val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val filename = "constants_$dateStr.json"
        val file = File(dir, filename)

        return try {
            val jsonMap = map.mapValues { JsonPrimitive(it.value) }
            val jsonObj = JsonObject(jsonMap)
            val jsonFormatter = Json { prettyPrint = true }
            file.writeText(jsonFormatter.encodeToString(JsonObject.serializer(), jsonObj))
            filename
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun listBackups(projectPath: String): List<BackupInfo> {
        if (projectPath.isBlank()) return emptyList()
        val dir = File(projectPath, "constants_backups")
        if (!dir.exists()) return emptyList()

        return dir.listFiles { _, name -> name.startsWith("constants_") && name.endsWith(".json") }
            ?.mapNotNull { file ->
                try {
                    val text = file.readText()
                    val jsonObj = Json.parseToJsonElement(text).jsonObject
                    val count = jsonObj.size

                    val rawTime = file.name.removePrefix("constants_").removeSuffix(".json")
                    val formatted = try {
                        val date = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).parse(rawTime)
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(date)
                    } catch (_: Exception) {
                        rawTime
                    }

                    BackupInfo(
                        filename = file.name,
                        formattedDate = formatted,
                        filePath = file.absolutePath,
                        count = count
                    )
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.filename }
            ?: emptyList()
    }

    private fun loadAppConstants(projectPath: String): Map<String, Double> {
        if (projectPath.isBlank()) return emptyMap()
        val file = File(projectPath, "robot_constants.json")
        if (!file.exists()) return emptyMap()
        return try {
            val text = file.readText()
            val jsonObj = Json.parseToJsonElement(text).jsonObject
            jsonObj.mapNotNull { (key, value) ->
                value.jsonPrimitive.doubleOrNull?.takeIf { it.isFinite() }?.let {
                    TuningTopics.canonicalize(key) to it
                }
            }.toMap()
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
