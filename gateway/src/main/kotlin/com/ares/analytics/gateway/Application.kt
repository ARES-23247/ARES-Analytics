package com.ares.analytics.gateway

import com.ares.analytics.gateway.auth.googleOidc
import com.ares.analytics.gateway.auth.GooglePrincipal
import com.ares.analytics.gateway.routes.diagnosticsRoutes
import com.ares.analytics.shared.ForensicsRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

private val allowedCorsHosts: List<String> = System.getenv("CORS_ALLOWED_HOSTS")
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

/**
 * Gateway entry point. Slim, forensics-only service: authenticates callers via a
 * Google OIDC ID token and exposes the Vertex AI pit-forensics endpoint behind rate
 * limiting. Storage (session logs/summaries) is handled client-side by the desktop
 * app directly against a shared Google Drive; this gateway no longer touches
 * Firebase, Firestore, or GCS.
 */
fun main() {
    // Force gRPC and Ktor onto the JDK JSSE provider instead of netty-tcnative OpenSSL,
    // which SIGSEGVs inside Google Cloud Run.
    System.setProperty("io.netty.handler.ssl.openssl.useOpenssl", "false")
    System.setProperty("io.grpc.netty.shaded.io.netty.handler.ssl.openssl.useOpenssl", "false")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }

        install(CORS) {
            // This gateway is consumed by the Compose desktop app, which does not
            // need browser CORS. Browser access is opt-in through a deployment
            // allowlist (for example: "dashboard.example.org").
            allowedCorsHosts.forEach { host ->
                allowHost(host, schemes = listOf("https"))
            }
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
            googleOidc("google")
        }

        install(RateLimit) {
            register(RateLimitName("forensics")) {
                // The default Ktor key is Unit, which would make every authenticated
                // user share one global five-request bucket.
                requestKey { call -> call.principal<GooglePrincipal>()?.subject ?: "unauthenticated" }
                rateLimiter(limit = 5, refillPeriod = 60.seconds)
            }
        }

        install(RequestBodyLimit) {
            bodyLimit { MAX_REQUEST_BODY_BYTES }
        }

        install(RequestValidation) {
            validate<ForensicsRequest> { req ->
                when {
                    req.alerts.size > 2000 -> ValidationResult.Invalid("Payload too large: max alerts exceeded")
                    (req.topology?.nodes?.size ?: 0) > 500 ->
                        ValidationResult.Invalid("Payload too large: max topology nodes exceeded")
                    else -> ValidationResult.Valid
                }
            }
        }

        routing {
            get("/healthz") {
                call.respondText("ok")
            }
            diagnosticsRoutes()
        }
    }.start(wait = true)
}

private const val MAX_REQUEST_BODY_BYTES = 1L * 1024 * 1024
