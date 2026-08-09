package com.ares.analytics.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import io.ktor.utils.io.streams.*
import io.ktor.utils.io.jvm.javaio.copyTo

/**
 * Service managing Google Drive API v3 interactions for cloud backup of match telemetry logs and session archives.
 *
 * Utilizes OAuth 2.0 PKCE authentication via [OAuthService] to request OAuth access tokens, uploading Parquet and JSONL log files
 * directly to the user's Google Drive storage.
 *
 * ### REST Endpoint Targets:
 * - File Search: `GET https://www.googleapis.com/drive/v3/files`
 * - Resumable Upload: `POST https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable`
 *
 * ### Thread Safety & Performance Guarantees:
 * All file upload network calls run asynchronously on `Dispatchers.IO`. Uses Ktor CIO engine.
 *
 * @param oauthService OAuth authentication provider service.
 * @param environmentService Workspace settings service.
 * @param firebaseClientService Firebase auth service.
 *
 * @see OAuthService
 * @see SyncEngineService
 */
class GoogleDriveService(
    private val oauthService: OAuthService,
    private val environmentService: EnvironmentService,
    private val firebaseClientService: FirebaseClientService
) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend fun getAccessToken(): String {
        val config = environmentService.loadConfig()
            ?: throw IllegalStateException("No active workspace configuration loaded")
        val clientId = config.googleClientId
            ?: throw IllegalStateException("Google Client ID not configured. Set 'googleClientId' in workspace config.")
        val clientSecret = config.googleClientSecret
            ?: throw IllegalStateException("Google Client Secret not configured. Set 'googleClientSecret' in workspace config.")

        return oauthService.refreshGoogleAccessToken(clientId, clientSecret)
            ?: throw IllegalStateException("Not logged in to Google. Please authenticate first.")
    }

    suspend fun findOrCreateFolder(name: String, parentId: String? = null): String = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val query = if (parentId == null) {
            "name = '$name' and mimeType = 'application/vnd.google-apps.folder' and 'root' in parents and trashed = false"
        } else {
            "name = '$name' and mimeType = 'application/vnd.google-apps.folder' and '$parentId' in parents and trashed = false"
        }
        val searchResponse = httpClient.get("https://www.googleapis.com/drive/v3/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("fields", "files(id)")
        }

        if (searchResponse.status != HttpStatusCode.OK) {
            throw Exception("Failed to search folder: ${searchResponse.bodyAsText()}")
        }
        val searchResult = searchResponse.body<JsonObject>()
        val files = searchResult["files"]?.jsonArray
        if (files != null && files.isNotEmpty()) {
            return@withContext files[0].jsonObject["id"]!!.jsonPrimitive.content
        }

        // Create new folder
        val createBody = buildJsonObject {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parentId != null) {
                put("parents", buildJsonArray { add(parentId) })
            }
        }
        val createResponse = httpClient.post("https://www.googleapis.com/drive/v3/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(createBody)
        }

        if (createResponse.status != HttpStatusCode.OK) {
            throw Exception("Failed to create folder: ${createResponse.bodyAsText()}")
        }
        val createdObj = createResponse.body<JsonObject>()
        createdObj["id"]!!.jsonPrimitive.content
    }

    suspend fun findFile(name: String, parentId: String): String? = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val query = "name = '$name' and '$parentId' in parents and trashed = false"
        val response = httpClient.get("https://www.googleapis.com/drive/v3/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("fields", "files(id)")
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to search file: ${response.bodyAsText()}")
        }
        val searchResult = response.body<JsonObject>()
        val files = searchResult["files"]?.jsonArray
        if (files != null && files.isNotEmpty()) {
            files[0].jsonObject["id"]!!.jsonPrimitive.content
        } else {
            null
        }
    }

    suspend fun findFileContaining(substring: String, parentId: String): String? = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val query = "name contains '$substring' and '$parentId' in parents and trashed = false"
        val response = httpClient.get("https://www.googleapis.com/drive/v3/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("fields", "files(id)")
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to search file: ${response.bodyAsText()}")
        }
        val searchResult = response.body<JsonObject>()
        val files = searchResult["files"]?.jsonArray
        if (files != null && files.isNotEmpty()) {
            files[0].jsonObject["id"]!!.jsonPrimitive.content
        } else {
            null
        }
    }

    suspend fun readFile(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val response = httpClient.get("https://www.googleapis.com/drive/v3/files/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("alt", "media")
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to download file: ${response.bodyAsText()}")
        }

        response.readRawBytes()
    }

    /**
     * Downloads a file from Google Drive by streaming directly to disk.
     * Use this for large files (Parquet) to avoid loading the entire file into memory.
     */
    suspend fun readFileStreaming(fileId: String, destination: File): Unit = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        httpClient.prepareGet("https://www.googleapis.com/drive/v3/files/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("alt", "media")
        }.execute { response ->
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Failed to download file: ${response.bodyAsText()}")
            }
            val channel = response.bodyAsChannel()
            java.io.FileOutputStream(destination).use { outputStream ->
                channel.copyTo(outputStream)
            }
        }
    }

    suspend fun writeFile(name: String, bytes: ByteArray, parentId: String, mimeType: String, fileId: String? = null): String = withContext(Dispatchers.IO) {
        val token = getAccessToken()

        if (fileId != null) {
            // Overwrite existing file media content
            return@withContext httpClient.preparePatch("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.parse(mimeType))
                setBody(bytes)
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    throw Exception("Failed to overwrite file content: ${response.bodyAsText()}")
                }
                fileId
            }
        } else {
            // Create a new file with multipart metadata + media content
            val metadataPart = buildJsonObject {
                put("name", name)
                put("parents", buildJsonArray { add(parentId) })
            }.toString()
            val response = httpClient.post("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    io.ktor.client.request.forms.MultiPartFormDataContent(
                        io.ktor.client.request.forms.formData {
                            append("metadata", metadataPart, Headers.build {
                                append(HttpHeaders.ContentType, "application/json; charset=UTF-8")
                            })
                            append("file", bytes, Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                            })
                        },
                        boundary = "Boundary_${System.currentTimeMillis()}"
                    )
                )
            }

            if (response.status != HttpStatusCode.OK) {
                throw Exception("Failed to upload multipart file: ${response.bodyAsText()}")
            }
            val created = response.body<JsonObject>()
            created["id"]!!.jsonPrimitive.content
        }
    }

    /**
     * Uploads a file to Google Drive by streaming directly from disk.
     * Use this for large files (Parquet) to avoid loading the entire file into memory.
     */
    suspend fun writeFileStreaming(name: String, file: File, parentId: String, mimeType: String, fileId: String? = null): String = withContext(Dispatchers.IO) {
        val token = getAccessToken()

        if (fileId != null) {
            // Overwrite existing file with streaming content
            return@withContext httpClient.preparePatch("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.parse(mimeType))
                setBody(io.ktor.client.request.forms.InputProvider(file.length()) {
                    file.inputStream().asInput()
                })
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    throw Exception("Failed to overwrite file content: ${response.bodyAsText()}")
                }
                fileId
            }
        } else {
            // For new file creation, read bytes (metadata + content multipart requires it)
            // This path is acceptable because new uploads are rarer than overwrites
            val bytes = file.readBytes()
            return@withContext writeFile(name, bytes, parentId, mimeType, null)
        }
    }

    suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        httpClient.prepareDelete("https://www.googleapis.com/drive/v3/files/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.execute { response ->
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.NoContent) {
                throw Exception("Failed to delete file: ${response.bodyAsText()}")
            }
        }
    }
}
