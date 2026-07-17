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
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import java.io.File

class SyncEngineService(
    private val databaseService: DatabaseService,
    private val parquetExporterService: ParquetExporterService,
    private val firebaseClientService: FirebaseClientService,
    private val environmentService: EnvironmentService,
    private val teamApiService: TeamApiService,
    private val summaryEngineService: SummaryEngineService,
    private val googleDriveService: GoogleDriveService,
    private val gatewayUrl: String = "https://ares-analytics-gateway-staging-205869391101.us-central1.run.app",
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30 * 60 * 1000L
            connectTimeoutMillis = 60 * 1000L
            socketTimeoutMillis = 30 * 60 * 1000L
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
     * Uploads a local session's log file to Google Drive.
     */
    suspend fun uploadSession(sessionId: String, authToken: String? = null) = withContext(Dispatchers.IO) {
        val summary = databaseService.getSessionSummary(sessionId)
            ?: run {
                val session = databaseService.getSessions().find { it.sessionId == sessionId }
                    ?: throw IllegalArgumentException("Session not found for $sessionId")
                val generated = summaryEngineService.generateSummary(session)
                databaseService.insertSessionSummary(generated)
                generated
            }

        // 1. Export local session to temporary Parquet file
        val tempDir = File(System.getProperty("java.io.tmpdir"), "ares-sync")
        tempDir.mkdirs()

        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(java.util.Date(summary.createdAt))
        val robotStr = "_${summary.robotId}"
        val matchStr = if (summary.matchNumber != null) "_Match_${summary.matchNumber}" else ""
        val allianceStr = if (!summary.allianceColor.isNullOrEmpty()) "_${summary.allianceColor}" else ""
        val mode = when {
            summary.tags.contains("Auto") -> "Auto"
            summary.tags.contains("TeleOp") -> "TeleOp"
            summary.tags.contains("Init") -> "Init"
            else -> "Init"
        }
        val modeStr = "_$mode"
        val descriptiveName = "ARES_Telemetry_${dateStr}${robotStr}${matchStr}${allianceStr}${modeStr}_$sessionId.parquet"

        val tempFile = File(tempDir, descriptiveName)
        parquetExporterService.exportSessionToParquet(sessionId, tempFile)

        val updatedSummary = summary.copy(fileSizeBytes = tempFile.length())

        try {
            // 2. Locate or create folder structure in Google Drive
            val rootFolderId = googleDriveService.findOrCreateFolder("ARES-Analytics")
            val sessionsFolderId = googleDriveService.findOrCreateFolder("sessions", rootFolderId)

            // 3. Upload Parquet file to sessions/ folder
            val existingParquetId = googleDriveService.findFileContaining(sessionId, sessionsFolderId)
            googleDriveService.writeFileStreaming(
                name = descriptiveName,
                file = tempFile,
                parentId = sessionsFolderId,
                mimeType = "application/octet-stream",
                fileId = existingParquetId
            )

            // 4. Update the index.json file
            val indexFileId = googleDriveService.findFile("index.json", rootFolderId)
            val indexList = if (indexFileId != null) {
                val indexBytes = googleDriveService.readFile(indexFileId)
                try {
                    Json { ignoreUnknownKeys = true }.decodeFromString<List<SessionSummary>>(String(indexBytes, Charsets.UTF_8))
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val updatedList = indexList.filter { it.sessionId != sessionId } + updatedSummary
            val updatedIndexBytes = Json.encodeToString<List<SessionSummary>>(updatedList).toByteArray(Charsets.UTF_8)
            googleDriveService.writeFile(
                name = "index.json",
                bytes = updatedIndexBytes,
                parentId = rootFolderId,
                mimeType = "application/json",
                fileId = indexFileId
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Unused in Google Drive local-first sync.
     */
    suspend fun uploadRawFiles(
        teamId: String,
        runTimestamp: String,
        files: List<File>,
        authToken: String? = null
    ): String {
        return "raw/$teamId/$runTimestamp"
    }

    /**
     * Gets all session summaries recorded in the Google Drive index.json file.
     */
    suspend fun getRemoteSummaries(): List<SessionSummary> = withContext(Dispatchers.IO) {
        try {
            val rootFolderId = googleDriveService.findOrCreateFolder("ARES-Analytics")
            val indexFileId = googleDriveService.findFile("index.json", rootFolderId) ?: return@withContext emptyList()
            val indexBytes = googleDriveService.readFile(indexFileId)
            Json { ignoreUnknownKeys = true }.decodeFromString<List<SessionSummary>>(String(indexBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Gets all registered robot profiles recorded in the Google Drive robots.json file.
     */
    suspend fun getRemoteRobotProfiles(): List<RobotProfile> = withContext(Dispatchers.IO) {
        try {
            val rootFolderId = googleDriveService.findOrCreateFolder("ARES-Analytics")
            val fileId = googleDriveService.findFile("robots.json", rootFolderId) ?: return@withContext emptyList()
            val bytes = googleDriveService.readFile(fileId)
            Json { ignoreUnknownKeys = true }.decodeFromString<List<RobotProfile>>(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Saves the registered robot profiles list to the Google Drive robots.json file.
     */
    suspend fun saveRemoteRobotProfiles(profiles: List<RobotProfile>): Unit = withContext(Dispatchers.IO) {
        try {
            val rootFolderId = googleDriveService.findOrCreateFolder("ARES-Analytics")
            val fileId = googleDriveService.findFile("robots.json", rootFolderId)
            val bytes = Json.encodeToString<List<RobotProfile>>(profiles).toByteArray(Charsets.UTF_8)
            googleDriveService.writeFile(
                name = "robots.json",
                bytes = bytes,
                parentId = rootFolderId,
                mimeType = "application/json",
                fileId = fileId
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Downloads a single session's Parquet file from Google Drive and imports it into DuckDB.
     */
    suspend fun downloadSession(summary: SessionSummary) = withContext(Dispatchers.IO) {
        val rootFolderId = googleDriveService.findOrCreateFolder("ARES-Analytics")
        val sessionsFolderId = googleDriveService.findOrCreateFolder("sessions", rootFolderId)
        val parquetFileId = googleDriveService.findFileContaining(summary.sessionId, sessionsFolderId)
            ?: throw Exception("Session Parquet file not found on Google Drive for session: ${summary.sessionId}")

        val tempFile = File.createTempFile("cloud_sync_${summary.sessionId}_", ".parquet")
        googleDriveService.readFileStreaming(parquetFileId, tempFile)

        try {
            databaseService.importParquet(tempFile)
            databaseService.insertSessionSummary(summary)
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
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Syncs local sessions with Google Drive repository.
     */
    suspend fun performDeltaSync(teamId: String, seasonId: String, authToken: String? = null) = withContext(Dispatchers.IO) {
        val remoteSummaries = getRemoteSummaries()

        // 3. Filter for active team/season summaries that we do not have locally
        val localSummaries = databaseService.getAllSessionSummaries()
        val localIds = localSummaries.map { it.sessionId }.toSet()

        val missingSummaries = remoteSummaries.filter {
            it.teamId == teamId && it.seasonId == seasonId && !localIds.contains(it.sessionId)
        }

        // 4. Download missing parquets and insert summaries
        for (summary in missingSummaries) {
            try {
                downloadSession(summary)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getPrivateKey(privateKeyPem: String): java.security.PrivateKey {
        val cleanPem = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val decoded = java.util.Base64.getDecoder().decode(cleanPem)
        val spec = java.security.spec.PKCS8EncodedKeySpec(decoded)
        val kf = java.security.KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec)
    }

    private fun createGcpJwt(clientEmail: String, privateKeyPem: String, tokenUri: String): String {
        val privateKey = getPrivateKey(privateKeyPem)
        val header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
        val nowSec = System.currentTimeMillis() / 1000L
        val claims = """
            {
              "iss": "$clientEmail",
              "scope": "https://www.googleapis.com/auth/cloud-platform",
              "aud": "$tokenUri",
              "exp": ${nowSec + 3600},
              "iat": $nowSec
            }
        """.trimIndent()
        
        val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
        val headerBase64 = encoder.encodeToString(header.toByteArray(Charsets.UTF_8))
        val claimsBase64 = encoder.encodeToString(claims.toByteArray(Charsets.UTF_8))
        
        val input = "$headerBase64.$claimsBase64"
        val signatureInstance = java.security.Signature.getInstance("SHA256withRSA")
        signatureInstance.initSign(privateKey)
        signatureInstance.update(input.toByteArray(Charsets.UTF_8))
        val signatureBytes = signatureInstance.sign()
        val signatureBase64 = encoder.encodeToString(signatureBytes)
        
        return "$input.$signatureBase64"
    }

    private suspend fun getVertexAccessToken(serviceAccountJsonPath: String): String {
        val file = File(serviceAccountJsonPath)
        if (!file.exists()) throw IllegalArgumentException("Service Account file not found at: $serviceAccountJsonPath")
        val parsedJson = Json { ignoreUnknownKeys = true }.parseToJsonElement(file.readText()).jsonObject
        val clientEmail = parsedJson["client_email"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing client_email")
        val privateKeyPem = parsedJson["private_key"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing private_key")
        val tokenUri = parsedJson["token_uri"]?.jsonPrimitive?.content ?: "https://oauth2.googleapis.com/token"
        
        val jwt = createGcpJwt(clientEmail, privateKeyPem, tokenUri)
        val response = httpClient.post(tokenUri) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
                    "assertion" to jwt
                ).formUrlEncode()
            )
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to exchange Service Account JWT for access token: ${response.bodyAsText()}")
        }
        val responseObj = response.body<JsonObject>()
        return responseObj["access_token"]?.jsonPrimitive?.content ?: throw Exception("Missing access_token in response")
    }

    /**
     * Requests diagnostics directly on the client using Google AI Studio or Vertex AI REST API.
     */
    suspend fun requestForensics(request: ForensicsRequest, authToken: String? = null): ForensicsResponse = withContext(Dispatchers.IO) {
        val config = environmentService.loadConfig()
            ?: throw IllegalStateException("No active workspace configuration loaded")

        val aiMode = config.aiMode ?: "STUDIO"

        val prompt = """
            You are ARES Pit Forensics AI, a diagnostic copilot for FTC/FRC robotics teams.
            Analyze the following telemetry packet containing session statistics, triggered threshold alerts, motor currents, EKF positioning drift, and hardware topology.
            
            Identify the most likely hardware failure (e.g., loose CAN bus wire, brownout, battery sag, motor stall, camera disconnection, pinpoint encoder drift).
            
            Respond ONLY with a JSON object conforming exactly to this schema:
            {
              "probableRootCause": "Detailed description of what failed and why",
              "confidenceScore": 0.85, 
              "cascadingNodesAffected": ["node_id_1", "node_id_2"],
              "hardwareFaultLocus": {
                "failedNodeId": "id of the primary node that failed",
                "interruptedLinkId": "optional link connection id that was broken"
              },
              "recommendedActions": [
                "Step-by-step checklist action 1",
                "Step-by-step checklist action 2"
              ]
            }

            Data Packet:
            ${Json.encodeToString(ForensicsRequest.serializer(), request)}
        """.trimIndent()

        val modelName = config.geminiModel ?: "gemini-1.5-flash"

        val jsonResponse = if (aiMode == "STUDIO") {
            val apiKey = config.geminiApiKey ?: throw IllegalStateException("Gemini API key is not configured in settings")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", prompt)
                                    })
                                })
                            })
                        })
                    }
                )
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Google AI Studio request failed: ${response.bodyAsText()}")
            }
            val resObj = response.body<JsonObject>()
            resObj["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "{}"
        } else {
            val saPath = config.vertexServiceAccountPath ?: throw IllegalStateException("GCP Service Account path is not configured in settings")
            val projectId = config.vertexProjectId ?: throw IllegalStateException("GCP Project ID is not configured in settings")
            val location = config.vertexLocation ?: "us-central1"
            
            val accessToken = getVertexAccessToken(saPath)
            val url = "https://$location-aiplatform.googleapis.com/v1/projects/$projectId/locations/$location/publishers/google/models/$modelName:generateContent"
            
            val response = httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", prompt)
                                    })
                                })
                            })
                        })
                    }
                )
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Vertex AI request failed: ${response.bodyAsText()}")
            }
            val resObj = response.body<JsonObject>()
            resObj["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "{}"
        }

        val sanitizedJson = jsonResponse.replace(Regex("```(?:json)?\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL), "$1").trim()

        try {
            Json { ignoreUnknownKeys = true }.decodeFromString<ForensicsResponse>(sanitizedJson)
        } catch (e: Exception) {
            ForensicsResponse(
                probableRootCause = "AI produced unparseable diagnostics: $sanitizedJson",
                confidenceScore = 0.0,
                cascadingNodesAffected = emptyList(),
                hardwareFaultLocus = null,
                recommendedActions = listOf("Retry diagnostics", "Check logs manually")
            )
        }
    }

    suspend fun requestChatCoach(
        request: ForensicsRequest,
        userQuestion: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val config = environmentService.loadConfig()
            ?: throw IllegalStateException("No active workspace configuration loaded")

        val aiMode = config.aiMode ?: "STUDIO"
        val modelName = config.geminiModel ?: "gemini-1.5-flash"

        val historyStr = chatHistory.joinToString("\n") { (role, text) ->
            if (role == "user") "User: $text" else "Coach: $text"
        }

        val prompt = """
            You are ARES Pit Coach AI, a diagnostic copilot for FTC/FRC robotics teams.
            You are helping the team debug their robot using the following telemetry, alerts, and forensics context.
            
            Diagnostics Context:
            - Team: ${request.teamId}
            - Session: ${request.sessionId}
            - Alerts: ${request.alerts.joinToString { it.ruleKey }}
            
            Conversation History:
            $historyStr
            
            Analyze the context and answer the user's question. Provide specific, concise, actionable advice (e.g. recommend PID tuning changes, check specific cables, calibrate sensors) for a robotics student. Use markdown formatting.
            
            User's Question: $userQuestion
        """.trimIndent()

        val jsonResponse = if (aiMode == "STUDIO") {
            val apiKey = config.geminiApiKey ?: throw IllegalStateException("Gemini API key is not configured in settings")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", prompt)
                                    })
                                })
                            })
                        })
                    }
                )
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Google AI Studio request failed: ${response.bodyAsText()}")
            }
            val resObj = response.body<JsonObject>()
            resObj["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        } else {
            val saPath = config.vertexServiceAccountPath ?: throw IllegalStateException("GCP Service Account path is not configured in settings")
            val projectId = config.vertexProjectId ?: throw IllegalStateException("GCP Project ID is not configured in settings")
            val location = config.vertexLocation ?: "us-central1"
            
            val accessToken = getVertexAccessToken(saPath)
            val url = "https://$location-aiplatform.googleapis.com/v1/projects/$projectId/locations/$location/publishers/google/models/$modelName:generateContent"
            
            val response = httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", prompt)
                                    })
                                })
                            })
                        })
                    }
                )
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Vertex AI request failed: ${response.bodyAsText()}")
            }
            val resObj = response.body<JsonObject>()
            resObj["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        }
        jsonResponse
    }

    suspend fun requestSqlAnalysis(
        userQuestion: String,
        databaseService: DatabaseService
    ): String = withContext(Dispatchers.IO) {
        val config = environmentService.loadConfig()
            ?: throw IllegalStateException("No active workspace configuration loaded")

        val aiMode = config.aiMode ?: "STUDIO"
        val modelName = config.geminiModel ?: "gemini-1.5-flash"

        val schemaPrompt = """
            You are ARES SQL Data Analyst, a diagnostic agent for a robotics team telemetry database.
            We run on DuckDB.
            
            Database Tables:
            1. `sessions`:
               - `session_id` VARCHAR (PRIMARY KEY)
               - `team_id` VARCHAR
               - `season_id` VARCHAR
               - `robot_id` VARCHAR
               - `created_at` BIGINT (epoch ms)
               - `duration_ms` BIGINT
               - `tags` VARCHAR (json array of strings)
               - `match_number` BIGINT
               - `alliance_color` VARCHAR
            2. `session_summaries`:
               - `session_id` VARCHAR (PRIMARY KEY)
               - `team_id` VARCHAR
               - `season_id` VARCHAR
               - `robot_id` VARCHAR
               - `created_at` BIGINT
               - `duration_ms` BIGINT
               - `min_battery_voltage` DOUBLE
               - `max_ekf_drift` DOUBLE
               - `avg_loop_time_ms` DOUBLE
               - `p95_loop_time_ms` DOUBLE
               - `motor_current_averages` VARCHAR (json map of motor names to averages, e.g. '{"fl": 2.5, "fr": 2.3}')
               - `vision_acceptance_rate` DOUBLE
               - `avg_cross_track_error` DOUBLE
               - `avg_battery_resistance` DOUBLE
               - `max_motor_temps` VARCHAR (json map)
               - `avg_vision_latency_ms` DOUBLE
            3. `alerts`:
               - `alert_id` VARCHAR (PRIMARY KEY)
               - `session_id` VARCHAR
               - `rule_key` VARCHAR
               - `trigger_timestamp_ms` BIGINT
               - `resolve_timestamp_ms` BIGINT
               - `duration_ms` BIGINT
               - `peak_value` DOUBLE
               - `triaged` BIGINT (0 or 1)

            Task: Generate a single read-only SQL SELECT statement to extract the data needed to answer this user question.
            Provide ONLY a JSON object matching this schema:
            {
              "sql": "SELECT ... FROM session_summaries ..."
            }
            Do NOT run modifying queries (INSERT, UPDATE, DELETE, DROP). Keep it strictly read-only.
            
            User's Question: $userQuestion
        """.trimIndent()

        val jsonResponse = if (aiMode == "STUDIO") {
            val apiKey = config.geminiApiKey ?: throw IllegalStateException("Gemini API key is not configured in settings")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", schemaPrompt)
                                    })
                                })
                            })
                        })
                    }
                )
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Google AI Studio request failed: ${response.bodyAsText()}")
            }
            val resObj = response.body<JsonObject>()
            resObj["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "{}"
        } else {
            val saPath = config.vertexServiceAccountPath ?: throw IllegalStateException("GCP Service Account path is not configured in settings")
            val projectId = config.vertexProjectId ?: throw IllegalStateException("GCP Project ID is not configured in settings")
            val location = config.vertexLocation ?: "us-central1"
            
            val accessToken = getVertexAccessToken(saPath)
            val url = "https://$location-aiplatform.googleapis.com/v1/projects/$projectId/locations/$location/publishers/google/models/$modelName:generateContent"
            
            val response = httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", schemaPrompt)
                                    })
                                })
                            })
                        })
                    }
                )
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Vertex AI request failed: ${response.bodyAsText()}")
            }
            val resObj = response.body<JsonObject>()
            resObj["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "{}"
        }

        val sanitizedJson = jsonResponse.replace(Regex("```(?:json)?\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL), "$1").trim()
        val sqlQuery = try {
            val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(sanitizedJson).jsonObject
            parsed["sql"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("No SQL generated")
        } catch (e: Exception) {
            return@withContext "I was unable to formulate a SQL query to extract the data. Details: $sanitizedJson"
        }

        val queryResult = try {
            databaseService.executeQueryRaw(sqlQuery)
        } catch (e: Exception) {
            return@withContext "Failed to execute generated SQL query:\n```sql\n$sqlQuery\n```\nError: ${e.message}"
        }

        val summaryPrompt = """
            You are ARES SQL Data Analyst. 
            The user asked: "$userQuestion"
            
            To answer it, we ran this SQL query:
            ```sql
            $sqlQuery
            ```
            
            And got these results:
            Columns: ${queryResult.columns.joinToString(", ")}
            Rows:
            ${queryResult.rows.joinToString("\n") { it.joinToString(", ") }}
            
            Write a clear, concise, and helpful summary answering the user's question based on the retrieved data. Use markdown formatting. Mention match numbers or averages clearly.
        """.trimIndent()

        val finalResponse = if (aiMode == "STUDIO") {
            val apiKey = config.geminiApiKey ?: throw IllegalStateException("Gemini API key is not configured in settings")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", summaryPrompt)
                                    })
                                })
                            })
                        })
                    }
                )
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Google AI Studio request failed: ${response.bodyAsText()}")
            }
            val resObj = response.body<JsonObject>()
            resObj["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        } else {
            val saPath = config.vertexServiceAccountPath ?: throw IllegalStateException("GCP Service Account path is not configured in settings")
            val projectId = config.vertexProjectId ?: throw IllegalStateException("GCP Project ID is not configured in settings")
            val location = config.vertexLocation ?: "us-central1"
            
            val accessToken = getVertexAccessToken(saPath)
            val url = "https://$location-aiplatform.googleapis.com/v1/projects/$projectId/locations/$location/publishers/google/models/$modelName:generateContent"
            
            val response = httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", summaryPrompt)
                                    })
                                })
                            })
                        })
                    }
                )
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Vertex AI request failed: ${response.bodyAsText()}")
            }
            val resObj = response.body<JsonObject>()
            resObj["candidates"]?.jsonArray?.get(0)?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        }
        
        finalResponse
    }

    /**
     * Deletes a cloud session and removes it locally.
     */
    suspend fun deleteCloudSession(sessionId: String, teamId: String, authToken: String? = null) = withContext(Dispatchers.IO) {
        try {
            val rootFolderId = googleDriveService.findOrCreateFolder("ARES-Analytics")
            val sessionsFolderId = googleDriveService.findOrCreateFolder("sessions", rootFolderId)
            val parquetFileId = googleDriveService.findFileContaining(sessionId, sessionsFolderId)
            if (parquetFileId != null) {
                googleDriveService.deleteFile(parquetFileId)
            }

            val indexFileId = googleDriveService.findFile("index.json", rootFolderId)
            if (indexFileId != null) {
                val indexBytes = googleDriveService.readFile(indexFileId)
                val indexList = try {
                    Json { ignoreUnknownKeys = true }.decodeFromString<List<SessionSummary>>(String(indexBytes, Charsets.UTF_8))
                } catch (e: Exception) {
                    emptyList()
                }

                val updatedList = indexList.filter { it.sessionId != sessionId }
                val updatedIndexBytes = Json.encodeToString<List<SessionSummary>>(updatedList).toByteArray(Charsets.UTF_8)
                googleDriveService.writeFile(
                    name = "index.json",
                    bytes = updatedIndexBytes,
                    parentId = rootFolderId,
                    mimeType = "application/json",
                    fileId = indexFileId
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        databaseService.deleteSession(sessionId)
    }

    fun close() {
        httpClient.close()
    }
}
