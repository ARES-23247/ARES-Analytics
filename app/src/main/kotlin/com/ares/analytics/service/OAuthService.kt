package com.ares.analytics.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    data class Authenticated(
        val firebaseToken: String,
        val uid: String,
        val email: String,
        val displayName: String,
        val githubToken: String? = null
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

@Serializable
data class GoogleTokenResponse(
    val access_token: String,
    val id_token: String,
    val expires_in: Int
)

@Serializable
data class GithubTokenResponse(
    val access_token: String,
    val scope: String? = null
)

class OAuthService(
    private val firebaseClientService: FirebaseClientService
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    init {
        // Observe FirebaseClientService auth state and map it to AuthState
        CoroutineScope(Dispatchers.Default).launch {
            firebaseClientService.authState.collect { firebaseState ->
                when (firebaseState) {
                    is FirebaseAuthState.Unauthenticated -> {
                        _authState.value = AuthState.Unauthenticated
                    }
                    is FirebaseAuthState.Authenticating -> {
                        _authState.value = AuthState.Authenticating
                    }
                    is FirebaseAuthState.Authenticated -> {
                        _authState.value = AuthState.Authenticated(
                            firebaseToken = firebaseState.firebaseToken,
                            uid = firebaseState.uid,
                            email = firebaseState.email,
                            displayName = firebaseState.displayName,
                            githubToken = firebaseState.githubToken
                        )
                    }
                    is FirebaseAuthState.Error -> {
                        _authState.value = AuthState.Error(firebaseState.message)
                    }
                }
            }
        }
    }

    fun startGoogleLogin(googleClientId: String?, googleClientSecret: String? = null) {
        if (_authState.value is AuthState.Authenticating) return
        _authState.value = AuthState.Authenticating

        if (firebaseClientService.isDevMode() || googleClientId.isNullOrEmpty() || googleClientId == "mock") {
            // Local dev bypass
            CoroutineScope(Dispatchers.Default).launch {
                firebaseClientService.signInWithGoogleToken(
                    googleIdToken = "mock-google-id-token",
                    email = "dev-user@aresrobotics.org",
                    name = "ARES Dev User"
                )
            }
            return
        }

        val callbackPort = 5805
        val redirectUri = "http://localhost:$callbackPort/callback"
        val loginUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$googleClientId" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&response_type=code" +
                "&scope=${URLEncoder.encode("openid email profile", "UTF-8")}"

        bootCallbackServer(callbackPort) { code ->
            try {
                val bodyParams = mutableListOf(
                    "code" to code,
                    "client_id" to (googleClientId ?: ""),
                    "redirect_uri" to redirectUri,
                    "grant_type" to "authorization_code"
                )
                if (!googleClientSecret.isNullOrBlank()) {
                    bodyParams.add("client_secret" to googleClientSecret)
                }

                val response = httpClient.post("https://oauth2.googleapis.com/token") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(bodyParams.formUrlEncode())
                }

                if (response.status == HttpStatusCode.OK) {
                    val tokenData = response.body<GoogleTokenResponse>()
                    // Sign in to Firebase with the obtained Google ID Token
                    firebaseClientService.signInWithGoogleToken(
                        googleIdToken = tokenData.id_token,
                        email = "user@aresrobotics.org", // Firebase signInWithIdp will return actual email
                        name = "Google User"
                    )
                } else {
                    val errorText = response.bodyAsText()
                    _authState.value = AuthState.Error("Failed to exchange Google code: $errorText")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Google token exchange error: ${e.message}")
            }
        }

        launchBrowser(loginUrl)
    }

    fun startGithubLogin(githubClientId: String?, githubClientSecret: String? = null) {
        val currentAuth = _authState.value
        if (currentAuth !is AuthState.Authenticated) {
            _authState.value = AuthState.Error("Must sign in with Google/Firebase before linking GitHub")
            return
        }

        if (githubClientId.isNullOrEmpty() || githubClientId == "mock") {
            // Dev bypass for GitHub linking
            firebaseClientService.linkGitHubToken("mock-github-token")
            return
        }

        val callbackPort = 5805
        val redirectUri = "http://localhost:$callbackPort/callback"
        val loginUrl = "https://github.com/login/oauth/authorize?" +
                "client_id=$githubClientId" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&scope=read:org"

        bootCallbackServer(callbackPort) { code ->
            try {
                val response = httpClient.post("https://github.com/login/oauth/access_token") {
                    header(HttpHeaders.Accept, "application/json")
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf(
                        "client_id" to githubClientId,
                        "client_secret" to (githubClientSecret ?: ""),
                        "code" to code,
                        "redirect_uri" to redirectUri
                    ).formUrlEncode())
                }

                if (response.status == HttpStatusCode.OK) {
                    val tokenData = response.body<GithubTokenResponse>()
                    firebaseClientService.linkGitHubToken(tokenData.access_token)
                } else {
                    val errorText = response.bodyAsText()
                    _authState.value = AuthState.Error("Failed to exchange GitHub code: $errorText")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("GitHub token exchange error: ${e.message}")
            }
        }

        launchBrowser(loginUrl)
    }

    fun logout() {
        _authState.value = AuthState.Unauthenticated
        firebaseClientService.logout()
        stopServer()
    }

    private fun bootCallbackServer(port: Int, onCodeReceived: suspend (String) -> Unit) {
        stopServer()
        server = embeddedServer(CIO, port = port) {
            routing {
                get("/callback") {
                    val code = call.request.queryParameters["code"]
                    val error = call.request.queryParameters["error"]

                    if (code != null) {
                        call.respondText(
                            """
                            <html>
                            <head>
                                <title>ARES Mission Control Sign-In</title>
                                <style>
                                    body {
                                        background-color: #0D0F14;
                                        color: #E8ECF4;
                                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                                        display: flex;
                                        align-items: center;
                                        justify-content: center;
                                        height: 100vh;
                                        margin: 0;
                                    }
                                    .card {
                                        background-color: #161A22;
                                        border: 1px solid #2A2F3C;
                                        padding: 40px;
                                        border-radius: 16px;
                                        text-align: center;
                                        box-shadow: 0 4px 20px rgba(0,0,0,0.5);
                                    }
                                    h1 { color: #00E5FF; margin-bottom: 8px; }
                                    p { color: #9CA3B4; }
                                </style>
                            </head>
                            <body>
                                <div class="card">
                                    <h1>Sign-In Successful</h1>
                                    <p>Verification completed. You can safely close this browser window and return to the application.</p>
                                </div>
                            </body>
                            </html>
                            """.trimIndent(),
                            io.ktor.http.ContentType.Text.Html
                        )
                        CoroutineScope(Dispatchers.Default).launch {
                            onCodeReceived(code)
                            stopServer()
                        }
                    } else {
                        val msg = error ?: "Unknown auth error"
                        call.respondText("Authentication failed: $msg")
                        _authState.value = AuthState.Error(msg)
                        CoroutineScope(Dispatchers.Default).launch {
                            stopServer()
                        }
                    }
                }
            }
        }.apply {
            start(wait = false)
        }
    }

    private fun launchBrowser(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI(url))
                } else {
                    _authState.value = AuthState.Error("System browser not supported on this platform.")
                    stopServer()
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Failed to launch system browser: ${e.message}")
                stopServer()
            }
        }
    }

    private fun stopServer() {
        server?.let {
            it.stop(1000, 2000)
            server = null
        }
    }
}

