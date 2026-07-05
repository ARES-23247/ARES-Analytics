package com.ares.analytics.gateway.routes

import com.ares.analytics.gateway.auth.*
import com.ares.analytics.shared.*
import com.google.api.core.ApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.firestore.QuerySnapshot
import com.google.cloud.firestore.WriteResult
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamRobotsIntegrationTest {

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testGetRobotsFlow() = testApplication {
        val mockFirestore = mock(Firestore::class.java)
        val mockTeamsCol = mock(CollectionReference::class.java)
        val mockTeamDoc = mock(DocumentReference::class.java)
        val mockRobotsCol = mock(CollectionReference::class.java)
        val mockFuture = mock(ApiFuture::class.java) as ApiFuture<QuerySnapshot>
        val mockQuerySnapshot = mock(QuerySnapshot::class.java)

        `when`(mockFirestore.collection("teams")).thenReturn(mockTeamsCol)
        `when`(mockTeamsCol.document("9999")).thenReturn(mockTeamDoc)
        `when`(mockTeamDoc.collection("robots")).thenReturn(mockRobotsCol)
        `when`(mockRobotsCol.get()).thenReturn(mockFuture)
        `when`(mockFuture.get()).thenReturn(mockQuerySnapshot)

        val doc1 = mock(QueryDocumentSnapshot::class.java)
        `when`(doc1.id).thenReturn("ares-bot")
        `when`(doc1.data).thenReturn(mapOf(
            "league" to "FTC",
            "seasonId" to "2026",
            "name" to "Ares FTC"
        ))
        `when`(mockQuerySnapshot.documents).thenReturn(listOf(doc1))

        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
            installFirebaseAuthentication()
            routing {
                archiveRoutes(customFirestore = mockFirestore)
            }
        }

        val response = client.get("/api/team/9999/robots") {
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = Json.decodeFromString<TeamRobotsResponse>(response.bodyAsText())
        assertEquals(1, decoded.robots.size)
        assertEquals("ares-bot", decoded.robots[0].robotId)
        assertEquals(League.FTC, decoded.robots[0].league)
        assertEquals("Ares FTC", decoded.robots[0].name)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testAddAndDeleteRobotsFlow() = testApplication {
        val mockFirestore = mock(Firestore::class.java)
        val mockTeamsCol = mock(CollectionReference::class.java)
        val mockTeamDoc = mock(DocumentReference::class.java)
        val mockRobotsCol = mock(CollectionReference::class.java)
        val mockRobotDoc = mock(DocumentReference::class.java)
        val mockSetFuture = mock(ApiFuture::class.java) as ApiFuture<WriteResult>
        val mockDeleteFuture = mock(ApiFuture::class.java) as ApiFuture<WriteResult>

        // Mock users role verification
        val mockUsersCol = mock(CollectionReference::class.java)
        val mockUserDocRef = mock(DocumentReference::class.java)
        val mockUserGetFuture = mock(ApiFuture::class.java) as ApiFuture<DocumentSnapshot>
        val mockUserDocSnapshot = mock(DocumentSnapshot::class.java)

        `when`(mockFirestore.collection("authorized_users")).thenReturn(mockUsersCol)
        `when`(mockUsersCol.document("uid")).thenReturn(mockUserDocRef)
        `when`(mockUserDocRef.get()).thenReturn(mockUserGetFuture)
        `when`(mockUserGetFuture.get()).thenReturn(mockUserDocSnapshot)
        `when`(mockUserDocSnapshot.exists()).thenReturn(true)
        `when`(mockUserDocSnapshot.getString("role")).thenReturn("ADMIN")

        `when`(mockFirestore.collection("teams")).thenReturn(mockTeamsCol)
        `when`(mockTeamsCol.document("9999")).thenReturn(mockTeamDoc)
        `when`(mockTeamDoc.collection("robots")).thenReturn(mockRobotsCol)
        `when`(mockRobotsCol.document("ares-bot")).thenReturn(mockRobotDoc)
        `when`(mockRobotDoc.set(anyMap())).thenReturn(mockSetFuture)
        `when`(mockRobotDoc.delete()).thenReturn(mockDeleteFuture)

        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
            installFirebaseAuthentication()
            routing {
                archiveRoutes(customFirestore = mockFirestore)
            }
        }

        // Test Add
        val addReq = AddRobotRequest(
            teamId = "9999",
            robot = RobotProfile("ares-bot", League.FTC, "2026", "Ares FTC")
        )
        val addResponse = client.post("/api/team/robots/add") {
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AddRobotRequest.serializer(), addReq))
        }
        if (addResponse.status != HttpStatusCode.OK) {
            println("Add robot failed: ${addResponse.bodyAsText()}")
        }
        assertEquals(HttpStatusCode.OK, addResponse.status)

        // Test Delete
        val deleteReq = DeleteRobotRequest(
            teamId = "9999",
            robotId = "ares-bot"
        )
        val deleteResponse = client.post("/api/team/robots/delete") {
            header(HttpHeaders.Authorization, "Bearer mock-token:uid:email:name")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(DeleteRobotRequest.serializer(), deleteReq))
        }
        if (deleteResponse.status != HttpStatusCode.OK) {
            println("Delete robot failed: ${deleteResponse.bodyAsText()}")
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)
    }
}
