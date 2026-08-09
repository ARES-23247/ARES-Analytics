package com.ares.analytics.service

import com.ares.analytics.shared.AppJson
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates a cryptographically secure 256-bit PKCE code verifier string for OAuth 2.0 authorization requests.
 *
 * @return URL-safe Base64-encoded random code verifier string.
 */
fun generateCodeVerifier(): String {
    val secureRandom = SecureRandom()
    val codeVerifier = ByteArray(32)
    secureRandom.nextBytes(codeVerifier)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier)
}

fun generateCodeChallenge(codeVerifier: String): String {
    val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
    val messageDigest = MessageDigest.getInstance("SHA-256")
    messageDigest.update(bytes, 0, bytes.size)
    val digest = messageDigest.digest()
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    data class Authenticated(
        val idToken: String,
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
    val expires_in: Int,
    val refresh_token: String? = null
)

@Serializable
data class GithubTokenResponse(
    val access_token: String,
    val scope: String? = null
)

/**
 * Persisted Google identity + OAuth tokens, stored at `~/.ares-analytics/auth.json`.
 * Replaces the former Firebase SavedAuth: no Firebase refresh token, identity comes
 * straight from the verified Google ID token.
 */
@Serializable
data class OAuthSavedAuth(
    val googleAccessToken: String,
    val googleRefreshToken: String?,
    val googleTokenExpiresAt: Long?,
    val googleIdToken: String? = null,
    val uid: String,
    val email: String,
    val displayName: String
)

@Serializable
private data class GoogleIdPayload(
    val sub: String = "",
    val email: String? = null,
    val name: String? = null
)

/** Decodes (without verifying signature — the gateway verifies) the payload of a Google ID token JWT. */
private fun decodeIdToken(idToken: String): GoogleIdPayload = try {
    val payload = idToken.split(".").getOrNull(1) ?: return GoogleIdPayload()
    val json = String(Base64.getUrlDecoder().decode(payload))
    AppJson.decodeFromString<GoogleIdPayload>(json)
} catch (e: Exception) {
    GoogleIdPayload()
}

/**
 * Google-OAuth-first authentication service. Owns the local token store; no Firebase
 * Identity Toolkit round-trip. The Google ID token returned at login (and on refresh)
 * carries identity (sub/email/name) and is the credential used to call the gateway's
 * OIDC-authed endpoints. Google Drive access tokens are refreshed on demand for
 * [GoogleDriveService].
 */
class OAuthService(private val environmentService: EnvironmentService) {
    private val refreshMutex = kotlinx.coroutines.sync.Mutex()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    /**
     * Per-request CSRF `state` value for the OAuth redirect. Set in [startGoogleLogin] /
     * [startGithubLogin] before booting the callback server and validated in the
     * `/callback` handler to block login-CSRF (AUDIT H1).
     *
     * Marked `@Volatile`: it is written from the caller thread (startGoogleLogin on the UI
     * thread) and read from the CIO callback dispatcher; without `@Volatile` the callback
     * handler could see a stale null and reject a legitimate redirect.
     */
    @Volatile
    private var expectedState: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val authFile = File(System.getProperty("user.home"), ".ares-analytics/auth.json")

    init {
        // On startup, re-establish Authenticated state from persisted Google tokens.
        serviceScope.launch { loadPersistedAuth() }
    }

    fun isDevMode(): Boolean = System.getenv("DEV_MODE") == "true"

    private suspend fun loadPersistedAuth() {
        val saved = getSavedAuth() ?: return
        val config = environmentService.loadConfig()
        val clientId = config?.googleClientId
        // No config (or network-down reading it) → leave Unauthenticated; the UI will
        // re-prompt once settings exist. Don't crash on startup.
        if (clientId.isNullOrEmpty()) return
        // Refresh yields a fresh access_token + id_token; identity is re-derived from the
        // id_token. Only restore Authenticated when the refresh actually round-tripped —
        // otherwise a revoked/disabled account would look logged-in while gateway calls 401.
        val refreshed = try {
            refreshGoogleAccessToken(clientId, config.googleClientSecret) != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network-down / Drive outage while refreshing: stay Unauthenticated so the UI
            // re-prompts rather than falsely advertising an authenticated session.
            false
        }
        if (!refreshed) return
        // Restore Authenticated state from the freshly-persisted id_token.
        val restored = getSavedAuth() ?: return
        val idToken = restored.googleIdToken
        if (!idToken.isNullOrBlank()) {
            val payload = decodeIdToken(idToken)
            _authState.value = AuthState.Authenticated(
                idToken = idToken,
                uid = payload.sub.ifBlank { restored.uid },
                email = payload.email ?: restored.email,
                displayName = payload.name ?: restored.displayName
            )
        }
    }

    fun startGoogleLogin(googleClientId: String?, googleClientSecret: String? = null) {
        if (_authState.value is AuthState.Authenticating) return
        _authState.value = AuthState.Authenticating

        if (isDevMode() || googleClientId.isNullOrEmpty() || googleClientId == "mock") {
            serviceScope.launch {
                applyGoogleTokens(
                    idToken = "dev-id-token",
                    accessToken = "dev-access-token",
                    refreshToken = null,
                    expiresIn = 3600,
                    emailFallback = "dev-user@aresrobotics.org",
                    nameFallback = "ARES Dev User"
                )
            }
            return
        }
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val callbackPort = 5805
        val redirectUri = "http://localhost:$callbackPort/callback"
        // Per-request CSRF state parameter (AUDIT H1): unguessable, validated on callback.
        val state = generateCodeVerifier()
        expectedState = state
        val loginUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$googleClientId" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&response_type=code" +
                "&scope=${URLEncoder.encode("openid email profile https://www.googleapis.com/auth/drive.file", "UTF-8")}" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256" +
                "&state=$state"

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
                    applyGoogleTokens(
                        idToken = tokenData.id_token,
                        accessToken = tokenData.access_token,
                        refreshToken = tokenData.refresh_token,
                        expiresIn = tokenData.expires_in,
                        emailFallback = "user@aresrobotics.org",
                        nameFallback = "Google User"
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

    /**
     * Centralizes Google token handling: decode identity from the ID token, persist the
     * access/refresh tokens, and publish [AuthState.Authenticated].
     */
    private suspend fun applyGoogleTokens(
        idToken: String,
        accessToken: String,
        refreshToken: String?,
        expiresIn: Int,
        emailFallback: String,
        nameFallback: String
    ) {
        val payload = decodeIdToken(idToken)
        val email = payload.email ?: emailFallback
        val name = payload.name ?: nameFallback
        val uid = payload.sub.ifEmpty { "google-$email" }
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
        val saved = OAuthSavedAuth(
            googleAccessToken = accessToken,
            googleRefreshToken = refreshToken,
            googleTokenExpiresAt = expiresAt,
            googleIdToken = idToken,
            uid = uid,
            email = email,
            displayName = name
        )
        saveAuth(saved)
        _authState.value = AuthState.Authenticated(
            idToken = idToken,
            uid = uid,
            email = email,
            displayName = name
        )
    }

    suspend fun refreshGoogleAccessToken(clientId: String, clientSecret: String?): String? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val saved = getSavedAuth() ?: return@withLock null
            val refreshToken = saved.googleRefreshToken ?: return@withLock saved.googleAccessToken

            // Reuse current access token if not within 2 minutes of expiry.
            val expiresAt = saved.googleTokenExpiresAt ?: 0
            if (System.currentTimeMillis() < expiresAt - 120_000 && saved.googleAccessToken.isNotBlank()) {
                return@withLock saved.googleAccessToken
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
                        googleRefreshToken = data.refresh_token ?: saved.googleRefreshToken,
                        googleIdToken = data.id_token.ifBlank { saved.googleIdToken }
                    )
                    saveAuth(updatedAuth)
                    // Refresh also returns a fresh ID token; refresh identity in-state if present.
                    val current = _authState.value
                    if (current is AuthState.Authenticated && data.id_token.isNotBlank()) {
                        val payload = decodeIdToken(data.id_token)
                        _authState.value = current.copy(idToken = data.id_token,
                            email = payload.email ?: current.email,
                            displayName = payload.name ?: current.displayName)
                    }
                    return@withLock data.access_token
                } else {
                    println("Failed to refresh Google access token: ${response.bodyAsText()}")
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun startGithubLogin(githubClientId: String?, githubClientSecret: String? = null) {
        val currentAuth = _authState.value
        if (currentAuth !is AuthState.Authenticated) {
            _authState.value = AuthState.Error("Must sign in with Google before linking GitHub")
            return
        }

        if (githubClientId.isNullOrEmpty() || githubClientId == "mock") {
            _authState.value = currentAuth.copy(githubToken = "mock-github-token")
            return
        }
        val callbackPort = 5805
        val redirectUri = "http://localhost:$callbackPort/callback"
        // Per-request CSRF state parameter (AUDIT H1): unguessable, validated on callback.
        val state = generateCodeVerifier()
        expectedState = state
        val loginUrl = "https://github.com/login/oauth/authorize?" +
                "client_id=$githubClientId" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&scope=read:org" +
                "&state=$state"

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
                    val current = _authState.value
                    if (current is AuthState.Authenticated) {
                        _authState.value = current.copy(githubToken = tokenData.access_token)
                    }
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
        if (authFile.exists()) authFile.delete()
        stopServer()
    }

    fun getSavedAuth(): OAuthSavedAuth? {
        if (!authFile.exists()) return null
        return try {
            AppJson.decodeFromString<OAuthSavedAuth>(authFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun saveAuth(auth: OAuthSavedAuth) {
        try {
            authFile.parentFile?.mkdirs()
            authFile.writeText(Json.encodeToString(auth))
            // Best-effort OS-level restriction: owner-only read/write (AUDIT H2). POSIX-only;
            // silently ignored on Windows / unsupported filesystems.
            try {
                java.nio.file.Files.setPosixFilePermissions(
                    authFile.toPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
                )
            } catch (e: UnsupportedOperationException) {
                // Windows / non-POSIX FS — no action possible.
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bootCallbackServer(port: Int, onCodeReceived: suspend (String) -> Unit) {
        stopServer()
        server = embeddedServer(CIO, port = port) {
            routing {
                get("/callback") {
                    // Validate the per-request CSRF state parameter (AUDIT H1). The expected
                    // value is set in startGoogleLogin/startGithubLogin before booting.
                    val returnedState = call.request.queryParameters["state"]
                    val expected = expectedState
                    if (expected == null || returnedState != expected) {
                        call.respondText("Authentication failed: invalid state parameter (possible CSRF attack).")
                        _authState.value = AuthState.Error("Invalid OAuth state parameter")
                        serviceScope.launch { stopServer() }
                        return@get
                    }
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
                        serviceScope.launch {
                            onCodeReceived(code)
                            stopServer()
                        }
                    } else {
                        val msg = error ?: "Unknown auth error"
                        call.respondText("Authentication failed: $msg")
                        _authState.value = AuthState.Error(msg)
                        serviceScope.launch {
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
        serviceScope.launch(Dispatchers.IO) {
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

    fun dispose() {
        // Cancel the process-lifetime scope first so loadPersistedAuth / callback
        // coroutines stop touching httpClient before we close it. Without this, a
        // in-flight refresh could use a closed client and throw into the void.
        serviceScope.cancel()
        stopServer()
        try {
            httpClient.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
