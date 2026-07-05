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
import io.ktor.client.plugins.HttpTimeout
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
    val isDeletingCloudLog: String? = null,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val uploadLogs: List<String> = emptyList()
)

sealed class CloudIntent {
    object RefreshCloudLogs : CloudIntent()
    object RefreshRobotLogs : CloudIntent()
    data class PerformDeltaSync(val teamId: String, val seasonId: String) : CloudIntent()
    data class UploadRobotRun(val runId: String, val teamId: String, val seasonId: String, val robotId: String) : CloudIntent()
    data class DeleteRobotRun(val runId: String) : CloudIntent()
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
        install(HttpTimeout) {
            requestTimeoutMillis = 30 * 60 * 1000L
            connectTimeoutMillis = 60 * 1000L
            socketTimeoutMillis = 30 * 60 * 1000L
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

    private fun logUpload(message: String) {
        _state.update { it.copy(uploadLogs = it.uploadLogs + message) }
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
                    _state.update { it.copy(isUploadingRobotLog = intent.runId, errorMessage = null, uploadLogs = listOf("Starting upload for run ${intent.runId}...")) }
                    try {
                        val run = _state.value.robotRuns.find { it.runId == intent.runId }
                        if (run != null) {
                            val errors = mutableListOf<String>()
                            val downloadedFiles = mutableListOf<File>()
                            
                            logUpload("1/5: Downloading ${run.files.size} raw files from robot at ${getRobotIp()}...")
                            // 1. Download all raw files from robot
                            for (file in run.files) {
                                try {
                                    val tempFile = withContext(Dispatchers.IO) {
                                        val fileBytes = httpClient.get("http://${getRobotIp()}:5002/api/download?file=${file.name}").readBytes()
                                        // Preserve original filename for GCS path
                                        val tempDir = File(System.getProperty("java.io.tmpdir"), "ares-raw-upload")
                                        tempDir.mkdirs()
                                        val f = File(tempDir, file.name)
                                        f.writeBytes(fileBytes)
                                        f
                                    }
                                    downloadedFiles.add(tempFile)
                                    logUpload("      -> Downloaded ${file.name} (${tempFile.length() / 1024} KB)")
                                } catch (e: Exception) {
                                    errors.add("${file.name}: ${e.message}")
                                    logUpload("      -> Error downloading ${file.name}: ${e.message}")
                                }
                            }
                            
                            if (errors.isEmpty() && downloadedFiles.isNotEmpty()) {
                                logUpload("2/5: Uploading raw files to GCS bucket...")
                                // 2. Upload raw files to GCS (archival — preserves originals)
                                val runTimestamp = run.runId.let { id ->
                                    // Convert "20260704_201500" to "2026-07-04_20-15-00"
                                    if (id.length == 15 && id[8] == '_') {
                                        "${id.substring(0, 4)}-${id.substring(4, 6)}-${id.substring(6, 8)}_${id.substring(9, 11)}-${id.substring(11, 13)}-${id.substring(13, 15)}"
                                    } else id
                                }
                                
                                var rawGcsPath: String? = null
                                try {
                                    rawGcsPath = syncEngineService.uploadRawFiles(
                                        teamId = intent.teamId,
                                        runTimestamp = runTimestamp,
                                        files = downloadedFiles.filter { !it.name.endsWith(".csv", ignoreCase = true) }
                                    )
                                } catch (rawEx: Exception) {
                                    errors.add("Raw file archival failed: ${rawEx.message}")
                                    logUpload("      -> Raw archival failed: ${rawEx.message}")
                                }

                                val totalSizeKb = downloadedFiles.sumOf { it.length() } / 1024
                                logUpload("3/5: Parsing ${downloadedFiles.size} log files (${totalSizeKb} KB) into DuckDB...")
                                // 3. Parse into DuckDB
                                val session = logParserService.parseLogFiles(
                                    files = downloadedFiles,
                                    teamId = intent.teamId,
                                    seasonId = intent.seasonId,
                                    robotId = intent.robotId
                                )
                                logUpload("      -> Parsed session: ${session.sessionId} (${session.durationMs?.let { "${it / 1000}s" } ?: "unknown"} duration)")

                                logUpload("4/5: Pushing DuckDB Parquet blob to Cloud & syncing...")
                                // 4. Upload Parquet to GCS + delta sync
                                try {
                                    syncEngineService.uploadSession(session.sessionId)
                                    syncEngineService.performDeltaSync(intent.teamId, intent.seasonId)
                                    logUpload("      -> Cloud sync completed successfully.")
                                } catch (syncEx: Exception) {
                                    errors.add("Imported locally but cloud sync failed: ${syncEx.message}")
                                    logUpload("      -> Cloud sync failed: ${syncEx.message}")
                                }
                                
                                // 5. Delete temp files on desktop
                                downloadedFiles.forEach { it.delete() }
                                
                                logUpload("5/5: Deleting remote files from Robot...")
                                // 6. Delete files from robot after confirmed GCS upload
                                if (rawGcsPath != null && errors.isEmpty()) {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            for (file in run.files) {
                                                httpClient.post("http://${getRobotIp()}:5002/api/delete") {
                                                    parameter("file", file.name)
                                                }
                                            }
                                        }
                                    } catch (deleteEx: Exception) {
                                        // Non-fatal: files uploaded but robot cleanup failed
                                        errors.add("Uploaded successfully but robot cleanup failed: ${deleteEx.message}")
                                    }
                                }
                                
                                // Refresh UI
                                logUpload("Upload finished! Refreshing UI...")
                                fetchRobotLogs()
                                val summaries = databaseService.getAllSessionSummaries().sortedByDescending { it.createdAt }
                                _state.update { it.copy(cloudLogs = summaries, isUploadingRobotLog = null) }
                            } else {
                                logUpload("Upload encountered errors. Aborting.")
                                _state.update { it.copy(errorMessage = "Upload errors:\n" + errors.joinToString("\n"), isUploadingRobotLog = null) }
                            }
                        } else {
                            logUpload("Run not found in local state.")
                            _state.update { it.copy(isUploadingRobotLog = null) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        logUpload("CRITICAL FATAL: ${e.message}")
                        _state.update { it.copy(errorMessage = e.message ?: "Upload failed", isUploadingRobotLog = null) }
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
