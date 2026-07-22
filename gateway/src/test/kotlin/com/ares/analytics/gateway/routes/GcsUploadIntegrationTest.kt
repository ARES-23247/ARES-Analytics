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
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.seconds
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

/**
 * GcsUploadIntegrationTest class.
 */
class GcsUploadIntegrationTest {

    @Suppress("UNCHECKED_CAST")
    @Test
    /**
     * testGcsUploadFlow fun.
     */
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
            install(RateLimit) {
                register(RateLimitName("archive")) {
                    rateLimiter(limit = 30, refillPeriod = 60.seconds)
                }
            }
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
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name:9999")
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
    
    @Test
    /**
     * testIdorAuthorizationBypass fun.
     */
    fun testIdorAuthorizationBypass() = testApplication {
        val mockStorage = mock(Storage::class.java)
        val mockFirestore = mock(Firestore::class.java)
        
        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
            installFirebaseAuthentication()
            install(RateLimit) {
                register(RateLimitName("archive")) {
                    rateLimiter(limit = 30, refillPeriod = 60.seconds)
                }
            }
            routing {
                archiveRoutes(mockStorage, mockFirestore)
            }
        }
        val summary = SessionSummary(
            sessionId = "session-123",
            teamId = "9999", // Requesting team 9999
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

        // The mock token does not have the team claim setup correctly for team 9999, so this should fail
        val response = client.post("/api/archive/upload-url") {
            // Note: Our installFirebaseAuthentication sets teamId based on a hack if missing? Let's check auth logic, but we can pass a token with a different team or no team claim.
            // If the mock token doesn't include the 'team_id' claim, FirebasePrincipal.teamId will be null.
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name") 
            contentType(ContentType.Application.Json)
            setBody(reqJson)
        }
        
        // Assert that a missing/mismatched team claim returns 403 Forbidden instead of 200/500
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    /**
     * testIdorAuthorizationMultipleEndpoints fun.
     */
    fun testIdorAuthorizationMultipleEndpoints() = testApplication {
        val mockStorage = mock(Storage::class.java)
        val mockFirestore = mock(Firestore::class.java)
        
        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
            installFirebaseAuthentication()
            install(RateLimit) {
                register(RateLimitName("archive")) {
                    rateLimiter(limit = 30, refillPeriod = 60.seconds)
                }
            }
            routing {
                archiveRoutes(mockStorage, mockFirestore)
            }
        }
        
        // 1. GET /api/team/{teamId}/robots with mismatched team token
        val getRobotsResp = client.get("/api/team/9999/robots") {
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name:1111")
        }
        assertEquals(HttpStatusCode.Forbidden, getRobotsResp.status)

        // 2. POST /api/team/robots/add with mismatched team token
        val addReq = AddRobotRequest(
            teamId = "9999",
            robot = RobotProfile("robot1", League.FTC, "2026", "Bot")
        )
        val addResp = client.post("/api/team/robots/add") {
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name:1111")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AddRobotRequest.serializer(), addReq))
        }
        assertEquals(HttpStatusCode.Forbidden, addResp.status)

        // 3. POST /api/team/robots/delete with mismatched team token
        val delReq = DeleteRobotRequest(teamId = "9999", robotId = "robot1")
        val delResp = client.post("/api/team/robots/delete") {
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name:1111")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(DeleteRobotRequest.serializer(), delReq))
        }
        assertEquals(HttpStatusCode.Forbidden, delResp.status)

        // 4. POST /api/archive/upload-raw-urls with mismatched team token
        val rawReq = RawUploadUrlsRequest(teamId = "9999", runTimestamp = "123", fileNames = listOf("file1"))
        val rawResp = client.post("/api/archive/upload-raw-urls") {
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name:1111")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RawUploadUrlsRequest.serializer(), rawReq))
        }
        assertEquals(HttpStatusCode.Forbidden, rawResp.status)
    }
}
