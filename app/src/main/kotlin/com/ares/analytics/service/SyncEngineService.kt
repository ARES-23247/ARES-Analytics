package com.ares.analytics.service

import com.ares.analytics.shared.*
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SyncEngineService(
    private val databaseService: DatabaseService,
    private val parquetExporterService: ParquetExporterService,
    private val firebaseClientService: FirebaseClientService,
    private val environmentService: EnvironmentService,
    private val teamApiService: TeamApiService,
    private val summaryEngineService: SummaryEngineService,
    private val gatewayUrl: String = "https://ares-analytics-gateway-staging-205869391101.us-central1.run.app", // default cloud run address
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30 * 60 * 1000L // 30 minutes for large uploads
            connectTimeoutMillis = 60 * 1000L // 60 seconds connection
            socketTimeoutMillis = 30 * 60 * 1000L // 30 minutes read/write
        }
    }
) {

    private fun getActiveToken(overrideToken: String?): String {
        val token = overrideToken ?: firebaseClientService.getFirebaseToken()
        if (token == null && firebaseClientService.isDevMode()) {
            return "mock-token:dev-user@aresrobotics.org:dev-user@aresrobotics.org:ARES Dev User"
        }
        return token ?: throw IllegalStateException("User is not authenticated with Firebase")
    }

    /**
     * Uploads a local session's log file to cloud storage via pre-signed URL.
     */
    suspend fun uploadSession(sessionId: String, authToken: String? = null) = withContext(Dispatchers.IO) {
        val summary = databaseService.getSessionSummary(sessionId)
            ?: run {
                // Auto-generate summary for sessions imported before summary generation was added
                val session = databaseService.getSessions().find { it.sessionId == sessionId }
                    ?: throw IllegalArgumentException("Session not found for $sessionId")
                val generated = summaryEngineService.generateSummary(session)
                databaseService.insertSessionSummary(generated)
                generated
            }

        val token = getActiveToken(authToken)

        // 1. Export local session to temporary Parquet file
        val tempDir = File(System.getProperty("java.io.tmpdir"), "ares-sync")
        tempDir.mkdirs()
        val tempFile = File(tempDir, "$sessionId.parquet")
        parquetExporterService.exportSessionToParquet(sessionId, tempFile)

        val updatedSummary = summary.copy(fileSizeBytes = tempFile.length())

        try {
            // 2. Request pre-signed GCS upload URL from Ktor Gateway
            val uploadReq = UploadUrlRequest(
                teamId = summary.teamId,
                seasonId = summary.seasonId,
                robotId = summary.robotId,
                sessionId = sessionId,
                createdAt = summary.createdAt,
                summary = updatedSummary
            )

            val urlResponse = httpClient.post("$gatewayUrl/api/archive/upload-url") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(uploadReq)
            }

            if (urlResponse.status != HttpStatusCode.OK) {
                throw Exception("Failed to request upload URL: ${urlResponse.bodyAsText()}")
            }

            val uploadResponse = urlResponse.body<UploadUrlResponse>()

            // 3. Upload file bytes via PUT to GCS pre-signed URL
            val fileBytes = tempFile.readBytes()
            val putResponse = httpClient.put(uploadResponse.uploadUrl) {
                setBody(fileBytes)
            }

            if (putResponse.status != HttpStatusCode.OK) {
                throw Exception("GCS upload failed: ${putResponse.bodyAsText()}")
            }
        } finally {
            // Clean up temp file
            tempFile.delete()
        }
    }

    /**
     * Uploads raw log files to GCS via pre-signed URLs.
     * Returns the GCS path prefix (e.g. "raw/23247/2026-07-04_20-15-00") for storage in Firestore.
     */
    suspend fun uploadRawFiles(
        teamId: String,
        runTimestamp: String,
        files: List<File>,
        authToken: String? = null
    ): String = withContext(Dispatchers.IO) {
        val token = getActiveToken(authToken)
        val fileNames = files.map { it.name }

        // 1. Request signed URLs for all raw files
        val urlResponse = httpClient.post("$gatewayUrl/api/archive/upload-raw-urls") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(RawUploadUrlsRequest(
                teamId = teamId,
                runTimestamp = runTimestamp,
                fileNames = fileNames
            ))
        }

        if (urlResponse.status != HttpStatusCode.OK) {
            throw Exception("Failed to request raw upload URLs: ${urlResponse.bodyAsText()}")
        }

        val rawResponse = urlResponse.body<RawUploadUrlsResponse>()

        // 2. Upload each file to its signed URL
        for (file in files) {
            val signedUrl = rawResponse.uploadUrls[file.name]
                ?: throw Exception("No signed URL returned for ${file.name}")

            val fileBytes = file.readBytes()
            val putResponse = httpClient.put(signedUrl) {
                setBody(fileBytes)
            }

            if (putResponse.status != HttpStatusCode.OK) {
                throw Exception("GCS raw upload failed for ${file.name}: ${putResponse.bodyAsText()}")
            }
        }

        // Return the GCS path prefix
        "raw/$teamId/$runTimestamp"
    }


    suspend fun performDeltaSync(teamId: String, seasonId: String, authToken: String? = null) = withContext(Dispatchers.IO) {
        val token = getActiveToken(authToken)
        
        // --- Sync local workspaces to cloud ---
        try {
            val workspaces = environmentService.loadWorkspaces().workspaces
            val remoteRobots = teamApiService.fetchTeamRobots(teamId, authToken).map { it.robotId }.toSet()
            
            for (ws in workspaces) {
                if (ws.teamId == teamId && !remoteRobots.contains(ws.robotId)) {
                    val profile = RobotProfile(
                        robotId = ws.robotId,
                        league = ws.league,
                        seasonId = ws.seasonId,
                        name = "${ws.robotId} Local Config"
                    )
                    teamApiService.addRobotProfile(teamId, profile, authToken)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue with delta sync even if robot upload fails
        }

        // 1. Collect all local session IDs
        val localSummaries = databaseService.getAllSessionSummaries()
        val localIds = localSummaries.map { it.sessionId }

        // 2. Call gateway sync API
        val syncReq = SyncRequest(
            teamId = teamId,
            seasonId = seasonId,
            knownSessionIds = localIds
        )

        val response = httpClient.post("$gatewayUrl/api/archive/sync") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(syncReq)
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to sync: ${response.bodyAsText()}")
        }

        val syncResponse = response.body<SyncResponse>()

        // 3. Store missing summaries locally and download parquet
        for (summary in syncResponse.missingSummaries) {
            databaseService.insertSessionSummary(summary)
            // Save mock local session reference so they show up in index
            val session = Session(
                sessionId = summary.sessionId,
                teamId = summary.teamId,
                seasonId = summary.seasonId,
                robotId = summary.robotId,
                createdAt = summary.createdAt,
                durationMs = summary.durationMs,
                tags = summary.tags,
                matchNumber = summary.matchNumber,
                allianceColor = summary.allianceColor
            )
            databaseService.insertSession(session)

            // Download and import Parquet file
            try {
                val urlResponse = httpClient.get("$gatewayUrl/api/archive/download-url?sessionId=${summary.sessionId}&teamId=${summary.teamId}") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (urlResponse.status.isSuccess()) {
                    val downloadUrl = urlResponse.body<DownloadUrlResponse>().downloadUrl
                    val fileBytes = httpClient.get(downloadUrl).readBytes()
                    val tempFile = File.createTempFile("cloud_sync_${summary.sessionId}_", ".parquet")
                    tempFile.writeBytes(fileBytes)
                    
                    databaseService.importParquet(tempFile)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Requests Vertex AI diagnostics for a telemetry session.
     */
    suspend fun requestForensics(request: ForensicsRequest, authToken: String? = null): ForensicsResponse = withContext(Dispatchers.IO) {
        val token = getActiveToken(authToken)

        val response = httpClient.post("$gatewayUrl/api/diagnostics/forensics") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(request)
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to request diagnostics: ${response.bodyAsText()}")
        }

        response.body<ForensicsResponse>()
    }

    /**
     * Deletes a cloud session via the gateway (admin-only) and removes it locally.
     */
    suspend fun deleteCloudSession(sessionId: String, teamId: String, authToken: String? = null) = withContext(Dispatchers.IO) {
        val token = getActiveToken(authToken)

        val response = httpClient.post("$gatewayUrl/api/archive/delete") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(DeleteSessionRequest(sessionId = sessionId, teamId = teamId))
        }

        if (response.status == HttpStatusCode.Forbidden) {
            throw SecurityException(response.bodyAsText())
        }

        if (!response.status.isSuccess()) {
            throw Exception("Failed to delete cloud session: ${response.bodyAsText()}")
        }

        // Also remove from local database
        databaseService.deleteSession(sessionId)
    }

    fun close() {
        httpClient.close()
    }
}

