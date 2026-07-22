package com.ares.analytics.gateway

import com.ares.analytics.gateway.auth.firebase
import com.ares.analytics.gateway.routes.archiveRoutes
import com.ares.analytics.gateway.routes.authRoutes
import com.ares.analytics.gateway.routes.diagnosticsRoutes
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.requestvalidation.*
import kotlin.time.Duration.Companion.seconds
import com.ares.analytics.shared.ForensicsRequest
import com.ares.analytics.shared.UploadUrlRequest
import kotlinx.serialization.json.Json

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun main() {
    // Disable Netty OpenSSL to force gRPC and Ktor to fall back to the JDK JSSE provider.
    // This prevents SIGSEGV crashes in netty-tcnative when running inside Google Cloud Run.
    System.setProperty("io.netty.handler.ssl.openssl.useOpenssl", "false")
    System.setProperty("io.grpc.netty.shaded.io.netty.handler.ssl.openssl.useOpenssl", "false")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    // Initialize Firebase Admin SDK using Application Default Credentials
    try {
        if (FirebaseApp.getApps().isEmpty()) {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
            FirebaseApp.initializeApp(options)
        }
    } catch (e: Exception) {
        println("Warning: Firebase Admin SDK initialization failed: ${e.message}")
        println("Make sure GOOGLE_APPLICATION_CREDENTIALS points to a valid service account JSON if running locally.")
    }

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Get)
        }

        install(StatusPages) {
            exception<RequestValidationException> { call, cause ->
                call.respondText(text = "400: Bad Request: ${cause.reasons.joinToString()}", status = HttpStatusCode.BadRequest)
            }
            exception<Throwable> { call, cause ->
                call.application.environment.log.error("Internal Server Error", cause)
                call.respondText(
                    text = "500: Internal Server Error: An internal error occurred.",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }

        install(Authentication) {
            firebase("firebase")
        }

        install(RateLimit) {
            register(RateLimitName("forensics")) {
                rateLimiter(limit = 5, refillPeriod = 60.seconds)
            }
            register(RateLimitName("archive")) {
                rateLimiter(limit = 30, refillPeriod = 60.seconds)
            }
        }

        install(RequestValidation) {
            validate<UploadUrlRequest> { req ->
                if (req.summary.tags.size > 100) ValidationResult.Invalid("Payload too large: too many tags")
                else ValidationResult.Valid
            }
            validate<ForensicsRequest> { req ->
                when {
                    req.alerts.size > 2000 -> ValidationResult.Invalid("Payload too large: max alerts exceeded")
                    req.topology != null && req.topology!!.nodes.size > 500 -> ValidationResult.Invalid("Payload too large: max topology nodes exceeded")
                    else -> ValidationResult.Valid
                }
            }
        }

        routing {
            get("/healthz") {
                call.respondText("ok")
            }

            // Register gateway API endpoints
            authRoutes()
            archiveRoutes()
            diagnosticsRoutes()
        }
    }.start(wait = true)
}
