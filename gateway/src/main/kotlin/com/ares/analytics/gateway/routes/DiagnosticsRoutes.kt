package com.ares.analytics.gateway.routes

import com.ares.analytics.shared.ForensicsRequest
import com.ares.analytics.shared.ForensicsResponse
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.generativeai.GenerativeModel
import com.google.cloud.vertexai.generativeai.ResponseHandler
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.ratelimit.*
import kotlinx.serialization.json.Json

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun Route.diagnosticsRoutes() {
    val projectId = System.getenv("GOOGLE_CLOUD_PROJECT") ?: "ares-analytics"
    val location = System.getenv("GOOGLE_CLOUD_LOCATION") ?: "us-central1"

    authenticate("firebase") {
        rateLimit(RateLimitName("forensics")) {
            post("/api/diagnostics/forensics") {
                val req = call.receive<ForensicsRequest>()

            try {
                // Initialize Vertex AI client
                VertexAI(projectId, location).use { vertexAi ->
                    // Configure model directly using simple constructor
                    val model = GenerativeModel("gemini-1.5-flash", vertexAi)
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
                    val response = model.generateContent(prompt)
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
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "AI diagnostics failed: ${e.message}")
            }
            }
        }
    }
}
