package com.ares.analytics.gateway.routes

import com.ares.analytics.shared.AppJson


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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class GithubAuthRequest(val githubToken: String, val targetOrg: String? = null)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class GithubOrg(val login: String)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class GithubUser(val login: String)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class AuthSuccessResponse(val status: String, val username: String, val orgs: List<String>, val role: String)

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun Route.authRoutes() {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(AppJson)
        }
    }

    authenticate("firebase") {
        post("/api/auth/github") {
            val principal = call.principal<FirebasePrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "No valid Firebase principal")
            val req = call.receive<GithubAuthRequest>()

            try {
                // Query GitHub API for user's organizations
                val orgs = httpClient.prepareGet("https://api.github.com/user/orgs") {
                    header(HttpHeaders.Authorization, "token ${req.githubToken}")
                    header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }.execute { orgsResponse ->
                    if (orgsResponse.status != HttpStatusCode.OK) {
                        return@execute null
                    }
                    orgsResponse.body<List<GithubOrg>>()
                } ?: return@post call.respond(HttpStatusCode.BadRequest, "Failed to verify GitHub token")
                val orgNames = orgs.map { it.login }

                // If a target organization is specified, verify membership
                if (req.targetOrg != null && !orgNames.contains(req.targetOrg)) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        "User is not a member of required organization: ${req.targetOrg}"
                    )
                }

                // Query username to resolve teams
                val username = httpClient.prepareGet("https://api.github.com/user") {
                    header(HttpHeaders.Authorization, "token ${req.githubToken}")
                    header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }.execute { userResponse ->
                    if (userResponse.status == HttpStatusCode.OK) {
                        userResponse.body<GithubUser>().login
                    } else null
                }

                // Determine sub-team admin tier
                var role = "VIEWER"
                if (req.targetOrg != null && username != null) {
                    // Check mentors membership
                    httpClient.prepareGet("https://api.github.com/orgs/${req.targetOrg}/teams/mentors/memberships/$username") {
                        header(HttpHeaders.Authorization, "token ${req.githubToken}")
                        header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                    }.execute { mentorsResponse ->
                        if (mentorsResponse.status == HttpStatusCode.OK) {
                            role = "ADMIN"
                        }
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

                withContext(Dispatchers.IO) {
                    userDocRef.set(userData).get() // Wait in IO dispatcher for Cloud Run environment
                }
                call.respond(AuthSuccessResponse("success", principal.name ?: principal.email ?: "User", orgNames, role))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "GitHub OAuth verification error: ${e.message}")
            }
        }
    }
}
