package com.ares.analytics.gateway.routes

import com.ares.analytics.gateway.auth.firebase
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
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * GatewayRouteTest class.
 */
class GatewayRouteTest {

    @Test
    /**
     * testHealthz fun.
     */
    fun testHealthz() = testApplication {
        application {
            routing {
                get("/healthz") {
                    call.respondText("ok")
                }
            }
        }
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    /**
     * testAuthDiagnosticsWithoutToken fun.
     */
    fun testAuthDiagnosticsWithoutToken() = testApplication {
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
                register(RateLimitName("forensics")) {
                    rateLimiter(limit = 5, refillPeriod = 60.seconds)
                }
            }
            routing {
                diagnosticsRoutes()
            }
        }
        val response = client.post("/api/diagnostics/forensics") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
