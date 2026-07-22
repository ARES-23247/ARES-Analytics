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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun generateCodeVerifier(): String {
    val secureRandom = SecureRandom()
    val codeVerifier = ByteArray(32)
    secureRandom.nextBytes(codeVerifier)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier)
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun generateCodeChallenge(codeVerifier: String): String {
    val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
    val messageDigest = MessageDigest.getInstance("SHA-256")
    messageDigest.update(bytes, 0, bytes.size)
    val digest = messageDigest.digest()
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

sealed class AuthState {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object Unauthenticated : AuthState()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object Authenticating : AuthState()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class Authenticated(
        val firebaseToken: String,
        val uid: String,
        val email: String,
        val displayName: String,
        val githubToken: String? = null
    ) : AuthState()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class Error(val message: String) : AuthState()
}

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class GoogleTokenResponse(
    val access_token: String,
    val id_token: String,
    val expires_in: Int,
    val refresh_token: String? = null
)

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class GithubTokenResponse(
    val access_token: String,
    val scope: String? = null
)

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
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

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
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
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val callbackPort = 5805
        val redirectUri = "http://localhost:$callbackPort/callback"
        val loginUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$googleClientId" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&response_type=code" +
                "&scope=${URLEncoder.encode("openid email profile https://www.googleapis.com/auth/drive.file", "UTF-8")}" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256"

        bootCallbackServer(callbackPort) { code ->
            try {
                val bodyParams = mutableListOf(
                    "code" to code,
                    "client_id" to (googleClientId ?: ""),
                    "redirect_uri" to redirectUri,
                    "grant_type" to "authorization_code",
                    "code_verifier" to codeVerifier
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
                    val expiresAt = System.currentTimeMillis() + (tokenData.expires_in * 1000L)
                    // Sign in to Firebase with the obtained Google ID Token
                    firebaseClientService.signInWithGoogleToken(
                        googleIdToken = tokenData.id_token,
                        email = "user@aresrobotics.org", // Firebase signInWithIdp will return actual email
                        name = "Google User",
                        googleAccessToken = tokenData.access_token,
                        googleRefreshToken = tokenData.refresh_token,
                        googleTokenExpiresAt = expiresAt
                    )
                } else {
                    val errorText = response.bodyAsText()
                    val sentParamsInfo = "Sent client_id: $googleClientId (Secret present: ${!googleClientSecret.isNullOrBlank()})"
                    _authState.value = AuthState.Error("Failed to exchange Google code: $errorText\nDetails: $sentParamsInfo")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Google token exchange error: ${e.message}")
            }
        }

        launchBrowser(loginUrl)
    }

    suspend fun refreshGoogleAccessToken(clientId: String, clientSecret: String?): String? = withContext(Dispatchers.IO) {
        val saved = firebaseClientService.getSavedAuth() ?: return@withContext null
        val refreshToken = saved.googleRefreshToken ?: return@withContext saved.googleAccessToken

        // If not expired yet (with a 2 minute buffer), reuse current access token
        val expiresAt = saved.googleTokenExpiresAt ?: 0
        if (System.currentTimeMillis() < expiresAt - 120_000 && !saved.googleAccessToken.isNullOrBlank()) {
            return@withContext saved.googleAccessToken
        }

        try {
            val bodyParams = mutableListOf(
                "client_id" to clientId,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token"
            )
            if (!clientSecret.isNullOrBlank()) {
                bodyParams.add("client_secret" to clientSecret)
            }
            val response = httpClient.post("https://oauth2.googleapis.com/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(bodyParams.formUrlEncode())
            }

            if (response.status == HttpStatusCode.OK) {
                val data = response.body<GoogleTokenResponse>()
                val newExpiresAt = System.currentTimeMillis() + (data.expires_in * 1000L)
                val updatedAuth = saved.copy(
                    googleAccessToken = data.access_token,
                    googleTokenExpiresAt = newExpiresAt,
                    googleRefreshToken = data.refresh_token ?: refreshToken // reuse if new one not sent
                )
                firebaseClientService.saveAuth(updatedAuth)
                return@withContext data.access_token
            } else {
                println("Failed to refresh Google access token: ${response.bodyAsText()}")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
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

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
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

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun dispose() {
        stopServer()
        try {
            httpClient.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

