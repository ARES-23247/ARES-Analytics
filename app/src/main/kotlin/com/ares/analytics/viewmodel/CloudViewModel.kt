package com.ares.analytics.viewmodel

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FirebaseClientService
import com.ares.analytics.service.SyncEngineService
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.SessionSummary
import com.ares.analytics.service.LogParserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class RobotLogFileInfo(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val lastModifiedFmt: String,
    val synced: Boolean,
    val isActive: Boolean? = false
)

@Serializable
data class DownloadUrlResponse(
    val downloadUrl: String
)

data class RobotRun(
    val runId: String,
    val files: List<RobotLogFileInfo>,
    val totalSizeBytes: Long,
    val lastModifiedMs: Long,
    val lastModifiedFmt: String,
    val allSynced: Boolean,
    val isActive: Boolean = false
)

data class CloudState(
    val cloudLogs: List<SessionSummary> = emptyList(),
    val robotRuns: List<RobotRun> = emptyList(),
    val isSyncing: Boolean = false,
    val isFetchingRobotLogs: Boolean = false,
    val isUploadingRobotLog: String? = null,
    val isDownloadingCloudLog: String? = null,
    val isDeletingCloudLog: String? = null,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
)

sealed class CloudIntent {
    object RefreshCloudLogs : CloudIntent()
    object RefreshRobotLogs : CloudIntent()
    data class PerformDeltaSync(val teamId: String, val seasonId: String) : CloudIntent()
    data class UploadRobotRun(val runId: String, val teamId: String, val seasonId: String, val robotId: String) : CloudIntent()
    data class DeleteRobotRun(val runId: String) : CloudIntent()
    data class DownloadCloudLog(val sessionId: String) : CloudIntent()
    data class DeleteCloudLog(val sessionId: String, val teamId: String) : CloudIntent()
    object ClearError : CloudIntent()
}

class CloudViewModel(
    private val databaseService: DatabaseService,
    private val syncEngineService: SyncEngineService,
    private val firebaseClientService: FirebaseClientService,
    private val nt4ClientService: Nt4ClientService,
    private val logParserService: LogParserService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(CloudState())
    val state: StateFlow<CloudState> = _state.asStateFlow()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    init {
        checkAuth()
        onIntent(CloudIntent.RefreshCloudLogs)
        onIntent(CloudIntent.RefreshRobotLogs)
    }

    private fun checkAuth() {
        val hasToken = firebaseClientService.getFirebaseToken() != null || firebaseClientService.isDevMode()
        _state.update { it.copy(isAuthenticated = hasToken) }
    }

    private fun getRobotIp(): String {
        val ip = nt4ClientService.serverIp
        if (ip.isBlank() || ip == "0.0.0.0") return "127.0.0.1"
        return ip
    }

    fun onIntent(intent: CloudIntent) {
        scope.launch {
            when (intent) {
                is CloudIntent.RefreshCloudLogs -> {
                    checkAuth()
                    val summaries = databaseService.getAllSessionSummaries().sortedByDescending { it.createdAt }
                    _state.update { it.copy(cloudLogs = summaries) }
                }
                is CloudIntent.RefreshRobotLogs -> {
                    fetchRobotLogs()
                }
                is CloudIntent.PerformDeltaSync -> {
                    _state.update { it.copy(isSyncing = true, errorMessage = null) }
                    try {
                        syncEngineService.performDeltaSync(intent.teamId, intent.seasonId)
                        val summaries = databaseService.getAllSessionSummaries().sortedByDescending { it.createdAt }
                        _state.update { it.copy(cloudLogs = summaries, isSyncing = false) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _state.update { it.copy(isSyncing = false, errorMessage = e.message ?: "Sync failed") }
                    }
                }
                is CloudIntent.UploadRobotRun -> {
                    _state.update { it.copy(isUploadingRobotLog = intent.runId, errorMessage = null) }
                    try {
                        val run = _state.value.robotRuns.find { it.runId == intent.runId }
                        if (run != null) {
                            val errors = mutableListOf<String>()
                            val downloadedFiles = mutableListOf<File>()
                            
                            for (file in run.files) {
                                try {
                                    val tempFile = withContext(Dispatchers.IO) {
                                        val fileBytes = httpClient.get("http://${getRobotIp()}:5002/api/download?file=${file.name}").readBytes()
                                        val f = File.createTempFile("robot_log_", "_${file.name}")
                                        f.writeBytes(fileBytes)
                                        f
                                    }
                                    downloadedFiles.add(tempFile)
                                } catch (e: Exception) {
                                    errors.add("${file.name}: ${e.message}")
                                }
                            }
                            
                            if (errors.isEmpty() && downloadedFiles.isNotEmpty()) {
                                // Import the downloaded files into the local SQLite database as a new session
                                val session = logParserService.parseLogFiles(
                                    files = downloadedFiles,
                                    teamId = intent.teamId,
                                    seasonId = intent.seasonId,
                                    robotId = intent.robotId
                                )
                                // Now that it's imported, trigger the delta sync to upload it to GCS
                                try {
                                    syncEngineService.uploadSession(session.sessionId)
                                    syncEngineService.performDeltaSync(intent.teamId, intent.seasonId)
                                } catch (syncEx: Exception) {
                                    errors.add("Imported locally but cloud sync failed: ${syncEx.message}")
                                }
                                
                                // Delete temp files
                                downloadedFiles.forEach { it.delete() }
                                
                                // Refresh UI
                                fetchRobotLogs()
                                val summaries = databaseService.getAllSessionSummaries().sortedByDescending { it.createdAt }
                                _state.update { it.copy(cloudLogs = summaries, isUploadingRobotLog = null) }
                            }
                            
                            if (errors.isNotEmpty()) {
                                _state.update { it.copy(errorMessage = "Failed to upload:\n" + errors.joinToString("\n")) }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _state.update { it.copy(errorMessage = e.message ?: "Upload request failed") }
                    } finally {
                        _state.update { it.copy(isUploadingRobotLog = null) }
                    }
                }
                is CloudIntent.DeleteRobotRun -> {
                    try {
                        val run = _state.value.robotRuns.find { it.runId == intent.runId }
                        if (run != null) {
                            withContext(Dispatchers.IO) {
                                for (file in run.files) {
                                    httpClient.post("http://${getRobotIp()}:5002/api/delete") {
                                        parameter("file", file.name)
                                    }
                                }
                            }
                            fetchRobotLogs()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _state.update { it.copy(errorMessage = e.message ?: "Delete request failed") }
                    }
                }
                is CloudIntent.DownloadCloudLog -> {
                    _state.update { it.copy(isDownloadingCloudLog = intent.sessionId, errorMessage = null) }
                    try {
                        withContext(Dispatchers.IO) {
                            val token = firebaseClientService.getFirebaseToken() ?: "mock-token:dev-user@aresrobotics.org:dev-user@aresrobotics.org:ARES Dev User"
                            val gatewayUrl = "https://ares-analytics-gateway-staging-205869391101.us-central1.run.app"
                            
                            val urlResponse = httpClient.get("$gatewayUrl/api/archive/download-url?sessionId=${intent.sessionId}") {
                                header(HttpHeaders.Authorization, "Bearer $token")
                            }

                            if (!urlResponse.status.isSuccess()) {
                                throw Exception("Failed to request download URL: ${urlResponse.status}")
                            }

                            val downloadUrl = urlResponse.body<DownloadUrlResponse>().downloadUrl
                            val fileBytes = httpClient.get(downloadUrl).readBytes()

                            val tempFile = File.createTempFile("cloud_log_${intent.sessionId}_", ".jsonl")
                            tempFile.writeBytes(fileBytes)

                            val summary = databaseService.getSessionSummary(intent.sessionId)
                            logParserService.parseLogFile(
                                file = tempFile,
                                teamId = summary?.teamId ?: "unknown",
                                seasonId = summary?.seasonId ?: "unknown",
                                robotId = summary?.robotId ?: "unknown"
                            )
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _state.update { it.copy(errorMessage = e.message ?: "Download failed") }
                    } finally {
                        _state.update { it.copy(isDownloadingCloudLog = null) }
                    }
                }
                is CloudIntent.DeleteCloudLog -> {
                    _state.update { it.copy(isDeletingCloudLog = intent.sessionId, errorMessage = null) }
                    try {
                        syncEngineService.deleteCloudSession(intent.sessionId, intent.teamId)
                        // Refresh cloud logs list
                        val summaries = databaseService.getAllSessionSummaries().sortedByDescending { it.createdAt }
                        _state.update { it.copy(cloudLogs = summaries, isDeletingCloudLog = null) }
                    } catch (e: SecurityException) {
                        _state.update { it.copy(isDeletingCloudLog = null, errorMessage = "Permission denied: Only admins and coaches can delete cloud sessions") }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _state.update { it.copy(isDeletingCloudLog = null, errorMessage = e.message ?: "Delete failed") }
                    }
                }
                is CloudIntent.ClearError -> {
                    _state.update { it.copy(errorMessage = null) }
                }
            }
        }
    }

    private suspend fun fetchRobotLogs() {
        _state.update { it.copy(isFetchingRobotLogs = true, errorMessage = null) }
        try {
            val logs: List<RobotLogFileInfo> = withContext(Dispatchers.IO) {
                httpClient.get("http://${getRobotIp()}:5002/api/logs").body()
            }
            
            val runs = logs.groupBy { 
                val nameWithoutExt = it.name.substringBeforeLast(".")
                if (nameWithoutExt.length > 15 && nameWithoutExt.takeLast(15).matches(Regex("\\d{8}_\\d{6}"))) {
                    nameWithoutExt.takeLast(15)
                } else {
                    it.name
                }
            }.map { (runId, files) ->
                RobotRun(
                    runId = runId,
                    files = files,
                    totalSizeBytes = files.sumOf { it.sizeBytes },
                    lastModifiedMs = files.maxOf { it.lastModifiedMs },
                    lastModifiedFmt = files.maxByOrNull { it.lastModifiedMs }?.lastModifiedFmt ?: "",
                    allSynced = files.all { it.synced },
                    isActive = files.any { it.isActive == true }
                )
            }.sortedByDescending { it.lastModifiedMs }
            
            _state.update { it.copy(robotRuns = runs, isFetchingRobotLogs = false) }
        } catch (e: Exception) {
            e.printStackTrace()
            _state.update { it.copy(robotRuns = emptyList(), isFetchingRobotLogs = false, errorMessage = "Failed to fetch logs: ${e.message}") }
        }
    }
}
