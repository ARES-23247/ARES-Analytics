package com.ares.analytics.gateway.routes

import com.ares.analytics.gateway.auth.FirebasePrincipal
import com.google.cloud.firestore.FirestoreOptions
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GithubAuthRequest(val githubToken: String, val targetOrg: String? = null)

@Serializable
data class GithubOrg(val login: String)

@Serializable
data class GithubUser(val login: String)

@Serializable
data class AuthSuccessResponse(val status: String, val username: String, val orgs: List<String>, val role: String)

fun Route.authRoutes() {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    authenticate("firebase") {
        post("/api/auth/github") {
            val principal = call.principal<FirebasePrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "No valid Firebase principal")

            val req = call.receive<GithubAuthRequest>()

            try {
                // Query GitHub API for user's organizations
                val orgsResponse = httpClient.get("https://api.github.com/user/orgs") {
                    header(HttpHeaders.Authorization, "token ${req.githubToken}")
                    header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }

                if (orgsResponse.status != HttpStatusCode.OK) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Failed to verify GitHub token: ${orgsResponse.status}")
                }

                val orgs = orgsResponse.body<List<GithubOrg>>()
                val orgNames = orgs.map { it.login }

                // If a target organization is specified, verify membership
                if (req.targetOrg != null && !orgNames.contains(req.targetOrg)) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        "User is not a member of required organization: ${req.targetOrg}"
                    )
                }

                // Query username to resolve teams
                val userResponse = httpClient.get("https://api.github.com/user") {
                    header(HttpHeaders.Authorization, "token ${req.githubToken}")
                    header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }
                val username = if (userResponse.status == HttpStatusCode.OK) {
                    userResponse.body<GithubUser>().login
                } else null

                // Determine sub-team admin tier
                var role = "VIEWER"
                if (req.targetOrg != null && username != null) {
                    // Check mentors membership
                    val mentorsResponse = httpClient.get("https://api.github.com/orgs/${req.targetOrg}/teams/mentors/memberships/$username") {
                        header(HttpHeaders.Authorization, "token ${req.githubToken}")
                        header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                    }
                    if (mentorsResponse.status == HttpStatusCode.OK) {
                        role = "ADMIN"
                    }
                }

                // Provision/Update user document in Firestore
                val db = FirestoreOptions.getDefaultInstance().service
                val userDocRef = db.collection("users").document(principal.uid)

                val userData = mapOf(
                    "uid" to principal.uid,
                    "email" to principal.email,
                    "name" to principal.name,
                    "githubOrgs" to orgNames,
                    "role" to role,
                    "lastSeen" to System.currentTimeMillis()
                )

                userDocRef.set(userData).get() // Block wait for Cloud Run environment

                call.respond(AuthSuccessResponse("success", principal.name ?: principal.email ?: "User", orgNames, role))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "GitHub OAuth verification error: ${e.message}")
            }
        }
    }
}
