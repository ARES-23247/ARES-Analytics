package com.ares.analytics.gateway.routes

import com.ares.analytics.gateway.auth.*
import com.ares.analytics.shared.*
import com.google.api.core.ApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.WriteResult
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class GcsUploadIntegrationTest {

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testGcsUploadFlow() = testApplication {
        val mockStorage = mock(Storage::class.java)
        val mockFirestore = mock(Firestore::class.java)
        val mockCollection = mock(CollectionReference::class.java)
        val mockDoc = mock(DocumentReference::class.java)
        val mockFuture = mock(ApiFuture::class.java) as ApiFuture<WriteResult>

        `when`(mockFirestore.collection("summaries")).thenReturn(mockCollection)
        `when`(mockCollection.document(anyString())).thenReturn(mockDoc)
        `when`(mockDoc.set(any())).thenReturn(mockFuture)
        `when`(mockFuture.get()).thenReturn(mock(WriteResult::class.java))

        val mockUserCollection = mock(CollectionReference::class.java)
        val mockUserDocRef = mock(DocumentReference::class.java)
        val mockUserFuture = mock(ApiFuture::class.java) as ApiFuture<DocumentSnapshot>
        val mockUserDoc = mock(DocumentSnapshot::class.java)

        `when`(mockFirestore.collection("authorized_users")).thenReturn(mockUserCollection)
        `when`(mockUserCollection.document("uid")).thenReturn(mockUserDocRef)
        `when`(mockUserDocRef.get()).thenReturn(mockUserFuture)
        `when`(mockUserFuture.get()).thenReturn(mockUserDoc)
        `when`(mockUserDoc.get("githubOrgs")).thenReturn(listOf("9999"))

        val mockUrl = URL("http://localhost/gcs-mock-upload")
        
        // Mock storage.signUrl(blobInfo, duration, unit, options)
        `when`(mockStorage.signUrl(
            any(BlobInfo::class.java),
            org.mockito.ArgumentMatchers.anyLong(),
            any(TimeUnit::class.java),
            any(Storage.SignUrlOption::class.java),
            any(Storage.SignUrlOption::class.java)
        )).thenReturn(mockUrl)

        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
            installFirebaseAuthentication()
            routing {
                archiveRoutes(mockStorage, mockFirestore)
                
                // Mock GCS upload destination
                put("/gcs-mock-upload") {
                    val bytes = call.receive<ByteArray>()
                    assertEquals("dummy-parquet-data", String(bytes))
                    call.respond(HttpStatusCode.OK, "Uploaded")
                }
            }
        }

        val summary = SessionSummary(
            sessionId = "session-123",
            teamId = "9999",
            seasonId = "2026",
            robotId = "ares",
            createdAt = 123456789,
            durationMs = 5000,
            minBatteryVoltage = 11.5,
            maxEkfDrift = 0.05,
            avgLoopTimeMs = 15.0,
            p95LoopTimeMs = 22.0,
            motorCurrentAverages = emptyMap(),
            visionAcceptanceRate = 0.95,
            tags = listOf("test"),
            matchNumber = 1,
            allianceColor = "blue",
            rawGcsPath = null,
            fileSizeBytes = 0L
        )

        val uploadReq = UploadUrlRequest(
            teamId = "9999",
            seasonId = "2026",
            robotId = "ares",
            sessionId = "session-123",
            createdAt = 123456789,
            summary = summary
        )

        val reqJson = Json.encodeToString(UploadUrlRequest.serializer(), uploadReq)

        val response = client.post("/api/archive/upload-url") {
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name")
            contentType(ContentType.Application.Json)
            setBody(reqJson)
        }
        
        if (response.status != HttpStatusCode.OK) {
            println("Upload failed: ${response.bodyAsText()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val uploadUrlResponse = Json.decodeFromString<UploadUrlResponse>(response.bodyAsText())
        assertEquals("http://localhost/gcs-mock-upload", uploadUrlResponse.uploadUrl)

        // Upload file bytes to the pre-signed URL
        val putResponse = client.put(uploadUrlResponse.uploadUrl) {
            setBody("dummy-parquet-data".toByteArray())
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)
        assertEquals("Uploaded", putResponse.bodyAsText())
    }
}
