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
 * Escapes a literal for use inside a single-quoted segment of a Google Drive API v3
 * query string. The Drive query language uses `'...'` string literals and escapes a
 * literal backslash as `\\` and a single quote as `''`. Failing to escape lets a `'` in
 * a name/substring break out of the literal and inject query clauses (AUDIT M9).
 */
private fun escapeDriveQuery(value: String): String = value.replace("\\", "\\\\").replace("'", "''")

private fun JsonElement.requiredDriveId(context: String): String =
    ((this as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
        ?: throw IllegalStateException("Google Drive returned $context without a file id")

internal data class DriveFileSnapshot(val bytes: ByteArray, val etag: String?)

internal class DrivePreconditionFailedException(message: String) : IllegalStateException(message)

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
    private val environmentService: EnvironmentService
) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend fun getAccessToken(): String {
        val config = environmentService.loadConfig()
            ?: throw IllegalStateException("No active workspace configuration loaded")
        val clientId = config.googleClientId ?: "205869391101-nlcsea4539vjuo50i58bpo0t10d5s0ic.apps.googleusercontent.com"
        val clientSecret = config.googleClientSecret // Optional for PKCE native apps

        return oauthService.refreshGoogleAccessToken(clientId, clientSecret)
            ?: throw IllegalStateException("Not logged in to Google. Please authenticate first.")
    }

    suspend fun findOrCreateFolder(name: String, parentId: String? = null): String = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val escapedName = escapeDriveQuery(name)
        val query = if (parentId == null) {
            "name = '$escapedName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        } else {
            val escapedParent = escapeDriveQuery(parentId)
            "name = '$escapedName' and mimeType = 'application/vnd.google-apps.folder' and '$escapedParent' in parents and trashed = false"
        }
        val searchResponse = httpClient.get("https://www.googleapis.com/drive/v3/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("fields", "files(id)")
            parameter("supportsAllDrives", "true")
            parameter("includeItemsFromAllDrives", "true")
        }

        if (searchResponse.status != HttpStatusCode.OK) {
            throw Exception("Failed to search folder: ${searchResponse.bodyAsText()}")
        }
        val searchResult = searchResponse.body<JsonObject>()
        val files = searchResult["files"]?.jsonArray
        files?.firstOrNull()?.let { return@withContext it.requiredDriveId("a folder search result") }

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
        createResponse.body<JsonObject>().requiredDriveId("a created folder")
    }

    suspend fun findFiles(name: String, parentId: String): List<String> = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val escapedName = escapeDriveQuery(name)
        val escapedParent = escapeDriveQuery(parentId)
        val query = "name = '$escapedName' and '$escapedParent' in parents and trashed = false"
        val fileIds = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val response = httpClient.get("https://www.googleapis.com/drive/v3/files") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("q", query)
                parameter("fields", "nextPageToken,files(id)")
                parameter("pageSize", DRIVE_LIST_PAGE_SIZE)
                pageToken?.let { parameter("pageToken", it) }
                parameter("supportsAllDrives", "true")
                parameter("includeItemsFromAllDrives", "true")
            }
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Failed to search file: ${response.bodyAsText()}")
            }
            val searchResult = response.body<JsonObject>()
            searchResult["files"]?.jsonArray
                ?.mapTo(fileIds) { it.requiredDriveId("a file search result") }
            pageToken = (searchResult["nextPageToken"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf(String::isNotBlank)
        } while (pageToken != null)
        fileIds
    }

    private companion object {
        const val DRIVE_LIST_PAGE_SIZE = 1_000
    }

    suspend fun findFile(name: String, parentId: String): String? =
        findFiles(name, parentId).firstOrNull()

    suspend fun findFileContaining(substring: String, parentId: String): String? = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val escapedSubstring = escapeDriveQuery(substring)
        val escapedParent = escapeDriveQuery(parentId)
        val query = "name contains '$escapedSubstring' and '$escapedParent' in parents and trashed = false"
        val response = httpClient.get("https://www.googleapis.com/drive/v3/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("fields", "files(id)")
            parameter("supportsAllDrives", "true")
            parameter("includeItemsFromAllDrives", "true")
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to search file: ${response.bodyAsText()}")
        }
        val searchResult = response.body<JsonObject>()
        searchResult["files"]?.jsonArray?.firstOrNull()
            ?.requiredDriveId("a file search result")
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

    /** Reads content together with the revision ETag used for optimistic concurrency. */
    internal suspend fun readFileSnapshot(fileId: String): DriveFileSnapshot = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val response = httpClient.get("https://www.googleapis.com/drive/v3/files/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("alt", "media")
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to download file: ${response.bodyAsText()}")
        }
        DriveFileSnapshot(response.readRawBytes(), response.headers[HttpHeaders.ETag])
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

    suspend fun writeFile(
        name: String,
        bytes: ByteArray,
        parentId: String,
        mimeType: String,
        fileId: String? = null,
        expectedEtag: String? = null
    ): String = withContext(Dispatchers.IO) {
        val token = getAccessToken()

        if (fileId != null) {
            // Overwrite existing file media content
            return@withContext httpClient.preparePatch("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media") {
                header(HttpHeaders.Authorization, "Bearer $token")
                if (expectedEtag != null) header(HttpHeaders.IfMatch, expectedEtag)
                contentType(ContentType.parse(mimeType))
                setBody(bytes)
            }.execute { response ->
                if (response.status == HttpStatusCode.PreconditionFailed) {
                    throw DrivePreconditionFailedException("Google Drive file $fileId changed concurrently")
                }
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
            response.body<JsonObject>().requiredDriveId("an uploaded file")
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
            // Create metadata first and stream the media through a resumable upload session.
            val metadata = buildJsonObject {
                put("name", name)
                put("parents", buildJsonArray { add(parentId) })
            }
            val sessionResponse = httpClient.post(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
            ) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-Upload-Content-Type", mimeType)
                header("X-Upload-Content-Length", file.length().toString())
                contentType(ContentType.Application.Json)
                setBody(metadata)
            }
            if (sessionResponse.status != HttpStatusCode.OK) {
                throw Exception("Failed to create resumable upload: ${sessionResponse.bodyAsText()}")
            }
            val uploadUrl = sessionResponse.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("Google Drive resumable upload omitted its session URL")
            return@withContext httpClient.preparePut(uploadUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.parse(mimeType))
                header(HttpHeaders.ContentLength, file.length().toString())
                setBody(io.ktor.client.request.forms.InputProvider(file.length()) {
                    file.inputStream().asInput()
                })
            }.execute { response ->
                if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
                    throw Exception("Failed to stream resumable upload: ${response.bodyAsText()}")
                }
                response.body<JsonObject>().requiredDriveId("an uploaded file")
            }
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

    /**
     * Final teardown — closes the underlying HttpClient. Call from [com.ares.analytics.di.ServiceRegistry].
     */
    fun dispose() {
        try {
            httpClient.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
