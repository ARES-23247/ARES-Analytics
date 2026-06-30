package com.ares.analytics.service

import com.ares.analytics.shared.*
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class TeamApiService(
    private val firebaseClientService: FirebaseClientService,
    private val gatewayUrl: String = "https://ares-analytics-gateway-staging-205869391101.us-central1.run.app",
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) {

    private fun getActiveToken(overrideToken: String?): String {
        val token = overrideToken ?: firebaseClientService.getFirebaseToken()
        if (token == null && firebaseClientService.isDevMode()) {
            return "mock-token:dev-user@aresrobotics.org:dev-user@aresrobotics.org:ARES Dev User"
        }
        return token ?: throw IllegalStateException("User is not authenticated with Firebase")
    }

    suspend fun fetchTeamRobots(teamId: String, authToken: String? = null): List<RobotProfile> = withContext(Dispatchers.IO) {
        val token = try { getActiveToken(authToken) } catch (e: Exception) { return@withContext emptyList() }
        try {
            val response = httpClient.get("$gatewayUrl/api/team/$teamId/robots") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.OK) {
                response.body<TeamRobotsResponse>().robots
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addRobotProfile(teamId: String, robot: RobotProfile, authToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        val token = getActiveToken(authToken)
        try {
            val response = httpClient.post("$gatewayUrl/api/team/robots/add") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(AddRobotRequest(teamId, robot))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteRobotProfile(teamId: String, robotId: String, authToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        val token = getActiveToken(authToken)
        try {
            val response = httpClient.post("$gatewayUrl/api/team/robots/delete") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(DeleteRobotRequest(teamId, robotId))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun close() {
        httpClient.close()
    }
}
