package com.ares.analytics.gateway.routes

import com.ares.analytics.shared.ForensicsRequest
import com.ares.analytics.shared.ForensicsResponse
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.generativeai.GenerativeModel
import com.google.cloud.vertexai.generativeai.ResponseHandler
import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.ratelimit.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val vertexAiLogger = LoggerFactory.getLogger("DiagnosticsRoutes")

private val projectId = System.getenv("GOOGLE_CLOUD_PROJECT") ?: "ares-analytics"
private val location = System.getenv("GOOGLE_CLOUD_LOCATION") ?: "us-central1"
// Lazy so the VertexAI client is not constructed until the first diagnostics request.
// VertexAI is Closeable but is intentionally never closed here — it lives for the process
// lifetime and closing it on a hot server would break all subsequent requests.
private val vertexAi by lazy { VertexAI(projectId, location) }
private val model by lazy { GenerativeModel(com.ares.analytics.shared.DEFAULT_GEMINI_MODEL, vertexAi) }
private val diagnosticsConcurrency = Semaphore(4)

internal suspend fun <T> awaitApiFuture(future: ApiFuture<T>): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { future.cancel(true) }
    ApiFutures.addCallback(
        future,
        object : ApiFutureCallback<T> {
            override fun onSuccess(result: T) {
                if (continuation.isActive) continuation.resume(result)
            }

            override fun onFailure(error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        },
        java.util.concurrent.Executor { command -> command.run() }
    )
}

/** Registers the authenticated, per-user-rate-limited pit-forensics endpoint. */
fun Route.diagnosticsRoutes() {
    authenticate("google") {
        rateLimit(RateLimitName("forensics")) {
            post("/api/diagnostics/forensics") {
                val req = call.receive<ForensicsRequest>()

                try {
                    val prompt = """
                        You are ARES Pit Forensics AI, a diagnostic copilot for FTC/FRC robotics teams.
                        Analyze the following telemetry packet containing session statistics, triggered threshold alerts, motor currents, EKF positioning drift, and hardware topology.

                        Identify the most likely hardware failure (e.g., loose CAN bus wire, brownout, battery sag, motor stall, camera disconnection, pinpoint encoder drift).

                        Respond ONLY with a JSON object conforming exactly to this schema:
                        {
                          "probableRootCause": "Detailed description of what failed and why",
                          "confidenceScore": 0.85,
                          "cascadingNodesAffected": ["node_id_1", "node_id_2"],
                          "hardwareFaultLocus": {
                            "failedNodeId": "id of the primary node that failed",
                            "interruptedLinkId": "optional link connection id that was broken"
                          },
                          "recommendedActions": [
                            "Step-by-step checklist action 1",
                            "Step-by-step checklist action 2"
                          ]
                        }

                        Data Packet:
                        ${Json.encodeToString(ForensicsRequest.serializer(), req)}
                    """.trimIndent()
                    val response = withTimeout(60.seconds) {
                        diagnosticsConcurrency.withPermit {
                            awaitApiFuture(model.generateContentAsync(prompt))
                        }
                    }
                    val jsonResponse = ResponseHandler.getText(response) ?: "{}"
                    val sanitizedJson = jsonResponse.replace(Regex("```(?:json)?\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL), "$1").trim()

                    // Parse to verify compliance and return to client
                    val parsed = try {
                        Json.decodeFromString<ForensicsResponse>(sanitizedJson)
                    } catch (e: Exception) {
                        ForensicsResponse(
                            probableRootCause = "AI produced unparseable diagnostics.",
                            confidenceScore = 0.0,
                            cascadingNodesAffected = emptyList(),
                            hardwareFaultLocus = null,
                            recommendedActions = listOf("Retry diagnostics", "Check logs manually")
                        )
                    }
                    call.respond(parsed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    vertexAiLogger.error("AI diagnostics failed", e)
                    call.respond(HttpStatusCode.InternalServerError, "AI diagnostics failed")
                }
            }
        }
    }
}
