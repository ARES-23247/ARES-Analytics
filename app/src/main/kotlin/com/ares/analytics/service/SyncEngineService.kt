package com.ares.analytics.service

import com.ares.analytics.shared.*
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
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
    private val gatewayUrl: String = "https://ares-analytics-gateway-staging-205869391101.us-central1.run.app", // default cloud run address
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
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
            ?: throw IllegalArgumentException("Session summary not found for $sessionId")

        val token = getActiveToken(authToken)

        // 1. Export local session to temporary Parquet file
        val tempDir = File(System.getProperty("java.io.tmpdir"), "ares-sync")
        tempDir.mkdirs()
        val tempFile = File(tempDir, "$sessionId.parquet")
        parquetExporterService.exportSessionToParquet(sessionId, tempFile)

        try {
            // 2. Request pre-signed GCS upload URL from Ktor Gateway
            val uploadReq = UploadUrlRequest(
                teamId = summary.teamId,
                seasonId = summary.seasonId,
                robotId = summary.robotId,
                sessionId = sessionId,
                createdAt = summary.createdAt,
                summary = summary
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
     * Performs a delta sync: downloads missing cloud summaries and writes them to SQLite.
     */
    suspend fun performDeltaSync(teamId: String, seasonId: String, authToken: String? = null) = withContext(Dispatchers.IO) {
        val token = getActiveToken(authToken)
        
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

        // 3. Store missing summaries locally
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

    fun close() {
        httpClient.close()
    }
}

