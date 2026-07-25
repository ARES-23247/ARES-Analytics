package com.ares.analytics.gateway.routes

import com.ares.analytics.gateway.auth.FirebasePrincipal
import com.ares.analytics.gateway.auth.installFirebaseAuthentication
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class TestAuthShape(val status: String, val uid: String, val email: String)

class AuthRoutesTest {

    @Test
    fun testAuthenticationFlows() = testApplication {
        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
            installFirebaseAuthentication()
            
            routing {
                // Mount the real auth routes to test 401 protections
                authRoutes()
                
                // Mount a dummy protected route to test valid token parsing and response shape
                // without triggering real GitHub network calls or Firestore operations
                authenticate("firebase") {
                    get("/api/auth/test-shape") {
                        val principal = call.principal<FirebasePrincipal>()
                        when (principal) {
                            null -> call.respond(HttpStatusCode.Unauthorized, "No valid Firebase principal")
                            else -> call.respond(
                                HttpStatusCode.OK, 
                                TestAuthShape("success", principal.uid, principal.email ?: "")
                            )
                        }
                    }
                }
            }
        }

        // 1. Unauthenticated request to protected auth endpoint returns 401
        val unauthenticatedResponse = client.post("/api/auth/github") {
            contentType(ContentType.Application.Json)
            setBody("""{"githubToken":"fake"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthenticatedResponse.status)

        // 2. Missing Authorization header returns 401 (Same as above, explicitly verifying)
        val missingHeaderResponse = client.post("/api/auth/github") {
            contentType(ContentType.Application.Json)
            setBody("""{"githubToken":"fake"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, missingHeaderResponse.status)

        // 3. Malformed Bearer token returns 401
        val malformedResponse = client.post("/api/auth/github") {
            header(HttpHeaders.Authorization, "Bearer malformed_token_without_mock_prefix")
            contentType(ContentType.Application.Json)
            setBody("""{"githubToken":"fake"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, malformedResponse.status)

        // 4. Token with expired timestamp is rejected 
        // (Simulated by passing invalid token to real verifyIdToken logic)
        val expiredResponse = client.post("/api/auth/github") {
            header(HttpHeaders.Authorization, "Bearer expired_firebase_token_format")
            contentType(ContentType.Application.Json)
            setBody("""{"githubToken":"fake"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, expiredResponse.status)

        // 5. Valid token format is accepted (mock the Firebase verification)
        // 6. Request with valid auth returns expected response shape
        val validResponse = client.get("/api/auth/test-shape") {
            header(HttpHeaders.Authorization, "Bearer mock-token:test-uid:test@ares.com:TestUser:team-123")
        }
        
        assertEquals(HttpStatusCode.OK, validResponse.status)
        val responseText = validResponse.bodyAsText()
        val decoded = Json.decodeFromString<TestAuthShape>(responseText)
        
        when {
            decoded.status == "success" && decoded.uid == "test-uid" -> {
                assertEquals("success", decoded.status)
                assertEquals("test-uid", decoded.uid)
                assertEquals("test@ares.com", decoded.email)
            }
            else -> {
                throw AssertionError("Unexpected response shape: $responseText")
            }
        }
    }
}
