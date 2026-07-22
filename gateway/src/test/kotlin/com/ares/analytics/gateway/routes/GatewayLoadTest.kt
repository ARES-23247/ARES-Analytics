package com.ares.analytics.gateway.routes

import com.ares.analytics.gateway.auth.firebase
import com.ares.analytics.shared.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * GatewayLoadTest class.
 */
class GatewayLoadTest {

    @Test
    /**
     * testConcurrentSyncRequests fun.
     */
    fun testConcurrentSyncRequests() = testApplication {
        val mockFirestore = mock(com.google.cloud.firestore.Firestore::class.java)
        val mockCollection = mock(com.google.cloud.firestore.CollectionReference::class.java)
        val mockQuery1 = mock(com.google.cloud.firestore.Query::class.java)
        val mockQuery2 = mock(com.google.cloud.firestore.Query::class.java)
        @Suppress("UNCHECKED_CAST")
        val mockFuture = mock(com.google.api.core.ApiFuture::class.java) as com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot>
        val mockQuerySnapshot = mock(com.google.cloud.firestore.QuerySnapshot::class.java)

        org.mockito.Mockito.`when`(mockFirestore.collection("summaries")).thenReturn(mockCollection)
        org.mockito.Mockito.`when`(mockCollection.whereEqualTo("teamId", "9999")).thenReturn(mockQuery1)
        org.mockito.Mockito.`when`(mockQuery1.whereEqualTo("seasonId", "2026")).thenReturn(mockQuery2)
        org.mockito.Mockito.`when`(mockQuery2.get()).thenReturn(mockFuture)
        org.mockito.Mockito.`when`(mockFuture.get()).thenReturn(mockQuerySnapshot)
        org.mockito.Mockito.`when`(mockQuerySnapshot.documents).thenReturn(emptyList())
        val mockUserCollection = mock(com.google.cloud.firestore.CollectionReference::class.java)
        val mockUserDocRef = mock(com.google.cloud.firestore.DocumentReference::class.java)
        @Suppress("UNCHECKED_CAST")
        val mockUserFuture = mock(com.google.api.core.ApiFuture::class.java) as com.google.api.core.ApiFuture<com.google.cloud.firestore.DocumentSnapshot>
        val mockUserDoc = mock(com.google.cloud.firestore.DocumentSnapshot::class.java)

        org.mockito.Mockito.`when`(mockFirestore.collection("users")).thenReturn(mockUserCollection)
        org.mockito.Mockito.`when`(mockUserCollection.document("uid")).thenReturn(mockUserDocRef)
        org.mockito.Mockito.`when`(mockUserDocRef.get()).thenReturn(mockUserFuture)
        org.mockito.Mockito.`when`(mockUserFuture.get()).thenReturn(mockUserDoc)
        org.mockito.Mockito.`when`(mockUserDoc.get("githubOrgs")).thenReturn(listOf("9999"))

        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
            install(Authentication) {
                firebase("firebase")
            }
            install(RateLimit) {
                register(RateLimitName("archive")) {
                    rateLimiter(limit = 30, refillPeriod = 60.seconds)
                }
            }
            routing {
                archiveRoutes(customFirestore = mockFirestore)
            }
        }
        val syncReq = SyncRequest(
            teamId = "9999",
            seasonId = "2026",
            knownSessionIds = listOf("session-1")
        )
        val reqJson = Json.encodeToString(SyncRequest.serializer(), syncReq)
        val responses = coroutineScope {
            (1..10).map {
                async {
                    client.post("/api/archive/sync") {
                        header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name:9999")
                        contentType(ContentType.Application.Json)
                        setBody(reqJson)
                    }
                }
            }.awaitAll()
        }
        for (response in responses) {
            // Note: Since Firestore calls are mocked and return null references (empty list docs),
            // it will succeed with 200 OK returning empty list summaries.
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
