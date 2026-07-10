package com.ares.analytics.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Serializable
data class FirebaseSignInResponse(
    val idToken: String,
    val refreshToken: String,
    val expiresIn: String,
    val localId: String,
    val displayName: String? = null,
    val email: String? = null
)

@Serializable
data class FirebaseTokenRefreshResponse(
    val expires_in: String,
    val token_type: String,
    val refresh_token: String,
    val id_token: String,
    val user_id: String,
    val project_id: String
)

@Serializable
data class SavedAuth(
    val refreshToken: String,
    val uid: String,
    val email: String,
    val displayName: String,
    val googleAccessToken: String? = null,
    val googleRefreshToken: String? = null,
    val googleTokenExpiresAt: Long? = null
)

sealed class FirebaseAuthState {
    object Unauthenticated : FirebaseAuthState()
    object Authenticating : FirebaseAuthState()
    data class Authenticated(
        val firebaseToken: String,
        val uid: String,
        val email: String,
        val displayName: String,
        val githubToken: String? = null
    ) : FirebaseAuthState()
    data class Error(val message: String) : FirebaseAuthState()
}

class FirebaseClientService {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val _authState = MutableStateFlow<FirebaseAuthState>(FirebaseAuthState.Unauthenticated)
    val authState: StateFlow<FirebaseAuthState> = _authState.asStateFlow()

    // Configuration values (loaded from workspace config or env)
    var apiKey: String = System.getenv("FIREBASE_API_KEY") ?: "AIzaSyB4cU7pgHpqoxtqtQalIE4HqZoz3X7bJH0"
    var projectId: String = System.getenv("FIREBASE_PROJECT_ID") ?: "aresfirst-portal"

    fun isDevMode(): Boolean {
        return apiKey.isEmpty() || apiKey == "mock" || System.getenv("DEV_MODE") == "true"
    }

    private val authFile = File(System.getProperty("user.home"), ".ares-analytics/auth.json")

    init {
        CoroutineScope(Dispatchers.IO).launch {
            loadPersistedAuth()
        }
    }

    private suspend fun loadPersistedAuth() {
        if (!authFile.exists()) return
        try {
            val jsonStr = authFile.readText()
            val savedAuth = Json { ignoreUnknownKeys = true }.decodeFromString<SavedAuth>(jsonStr)
            refreshFirebaseToken(savedAuth)
        } catch (e: Exception) {
            println("Failed to load persisted auth: ${e.message}")
            authFile.delete()
        }
    }

    private suspend fun refreshFirebaseToken(savedAuth: SavedAuth) {
        _authState.value = FirebaseAuthState.Authenticating
        try {
            val url = "https://securetoken.googleapis.com/v1/token?key=$apiKey"
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("grant_type", "refresh_token")
                    put("refresh_token", savedAuth.refreshToken)
                })
            }
            if (response.status == HttpStatusCode.OK) {
                val data = response.body<FirebaseTokenRefreshResponse>()
                
                // Update file with the potential new refresh token
                val newSavedAuth = savedAuth.copy(refreshToken = data.refresh_token)
                authFile.writeText(Json.encodeToString(newSavedAuth))
                
                _authState.value = FirebaseAuthState.Authenticated(
                    firebaseToken = data.id_token,
                    uid = newSavedAuth.uid,
                    email = newSavedAuth.email,
                    displayName = newSavedAuth.displayName
                )
            } else {
                println("Failed to refresh token: ${response.bodyAsText()}")
                _authState.value = FirebaseAuthState.Unauthenticated
                authFile.delete()
            }
        } catch (e: Exception) {
            println("Network error during token refresh: ${e.message}")
            _authState.value = FirebaseAuthState.Unauthenticated
        }
    }

    suspend fun signInWithGoogleToken(
        googleIdToken: String,
        email: String,
        name: String,
        googleAccessToken: String? = null,
        googleRefreshToken: String? = null,
        googleTokenExpiresAt: Long? = null
    ) {
        _authState.value = FirebaseAuthState.Authenticating
        if (isDevMode()) {
            // Local dev fallback
            val mockToken = "mock-token:$email:$email:$name"
            _authState.value = FirebaseAuthState.Authenticated(
                firebaseToken = mockToken,
                uid = "mock-$email",
                email = email,
                displayName = name
            )
            return
        }

        try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey"
            val postBody = "id_token=$googleIdToken&providerId=google.com"
            
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header("Referer", "https://${projectId}.firebaseapp.com")
                header("Origin", "https://${projectId}.firebaseapp.com")
                setBody(buildJsonObject {
                    put("requestUri", "http://localhost")
                    put("postBody", postBody)
                    put("returnSecureToken", true)
                    put("returnIdToken", true)
                })
            }

            if (response.status == HttpStatusCode.OK) {
                val data = response.body<FirebaseSignInResponse>()
                _authState.value = FirebaseAuthState.Authenticated(
                    firebaseToken = data.idToken,
                    uid = data.localId,
                    email = data.email ?: email,
                    displayName = data.displayName ?: name
                )
                
                try {
                    val savedAuth = SavedAuth(
                        refreshToken = data.refreshToken,
                        uid = data.localId,
                        email = data.email ?: email,
                        displayName = data.displayName ?: name,
                        googleAccessToken = googleAccessToken,
                        googleRefreshToken = googleRefreshToken,
                        googleTokenExpiresAt = googleTokenExpiresAt
                    )
                    authFile.parentFile?.mkdirs()
                    authFile.writeText(Json.encodeToString(savedAuth))
                } catch (e: Exception) {
                    println("Failed to persist auth data: ${e.message}")
                }
            } else {
                val body = response.bodyAsText()
                _authState.value = FirebaseAuthState.Error("Firebase Sign-In failed (${response.status}): $body")
            }
        } catch (e: Exception) {
            _authState.value = FirebaseAuthState.Error("Network error during Firebase Sign-In: ${e.message}")
        }
    }

    fun getSavedAuth(): SavedAuth? {
        if (!authFile.exists()) return null
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString<SavedAuth>(authFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun saveAuth(auth: SavedAuth) {
        try {
            authFile.parentFile?.mkdirs()
            authFile.writeText(Json.encodeToString(auth))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun linkGitHubToken(githubToken: String) {
        val current = _authState.value
        if (current is FirebaseAuthState.Authenticated) {
            _authState.value = current.copy(githubToken = githubToken)
        }
    }

    fun logout() {
        _authState.value = FirebaseAuthState.Unauthenticated
        if (authFile.exists()) {
            authFile.delete()
        }
    }

    fun getFirebaseToken(): String? {
        val current = _authState.value
        return if (current is FirebaseAuthState.Authenticated) {
            current.firebaseToken
        } else null
    }

    fun close() {
        try {
            httpClient.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
