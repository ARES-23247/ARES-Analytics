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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
    val id_token: String? = null,
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
class OAuthService(
    private val environmentService: EnvironmentService,
    private val authFilePath: String = System.getProperty("user.home") + "/.ares-analytics/auth.json",
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
    private val loadPersistedAuthOnInit: Boolean = true,
    private val secretsWriter: (File, ByteArray) -> Unit = ::writeSecrets,
) {
    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    private val authLifecycleLock = Any()
    private val authGeneration = AtomicLong(0L)
    private val pendingOAuthRequest = AtomicReference<PendingOAuthRequest?>(null)
    private val authWorkJobs = mutableSetOf<Job>()

    @Volatile
    private var disposed = false

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var serverGeneration: Long? = null

    private data class PendingOAuthRequest(
        val state: String,
        val generation: Long,
        val onCodeReceived: suspend (String) -> Unit
    )

    private data class AuthAttempt(
        val generation: Long,
        val previousState: AuthState
    )

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val authFile = File(authFilePath)

    init {
        // On startup, re-establish Authenticated state from persisted Google tokens.
        if (loadPersistedAuthOnInit) {
            val generation = authGeneration.get()
            launchAuthWork(generation) { loadPersistedAuth(generation) }
        }
    }

    fun isDevMode(): Boolean = System.getenv("DEV_MODE") == "true"

    internal suspend fun loadPersistedAuth() = loadPersistedAuth(authGeneration.get())

    private suspend fun loadPersistedAuth(generation: Long) {
        if (!isGenerationCurrent(generation)) return
        if (getSavedAuth() == null) return
        val config = environmentService.loadConfig()
        val clientId = config?.googleClientId
        // No config (or network-down reading it) → leave Unauthenticated; the UI will
        // re-prompt once settings exist. Don't crash on startup.
        if (clientId.isNullOrEmpty()) return
        // Refresh yields a fresh access token and may omit the optional ID token. Only restore
        // Authenticated when the refresh actually round-tripped —
        // otherwise a revoked/disabled account would look logged-in while gateway calls 401.
        val refreshed = try {
            refreshGoogleAccessToken(clientId, config.googleClientSecret, generation) != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network-down / Drive outage while refreshing: stay Unauthenticated so the UI
            // re-prompts rather than falsely advertising an authenticated session.
            false
        }
        if (!refreshed) return
        // Restore Authenticated state from the retained or freshly returned ID token.
        val restored = getSavedAuth() ?: return
        val idToken = restored.googleIdToken
        if (!idToken.isNullOrBlank()) {
            val payload = decodeIdToken(idToken)
            commitIfCurrent(generation) {
                _authState.value = AuthState.Authenticated(
                    idToken = idToken,
                    uid = payload.sub.ifBlank { restored.uid },
                    email = payload.email ?: restored.email,
                    displayName = payload.name ?: restored.displayName
                )
            }
        }
    }

    fun startGoogleLogin(googleClientId: String?, googleClientSecret: String? = null) {
        beginGoogleLogin(googleClientId, googleClientSecret, interactive = true)
    }

    private fun beginGoogleLogin(
        googleClientId: String?,
        googleClientSecret: String?,
        interactive: Boolean
    ): String? {
        val attempt = beginAuthAttempt(
            permitted = { it !is AuthState.Authenticating },
            nextState = { AuthState.Authenticating }
        ) ?: return null
        val generation = attempt.generation

        if (interactive && isDevMode()) {
            launchAuthWork(generation) {
                applyGoogleTokens(
                    idToken = "dev-id-token",
                    accessToken = "dev-access-token",
                    refreshToken = null,
                    expiresIn = 3600,
                    emailFallback = "dev-user@aresrobotics.org",
                    nameFallback = "ARES Dev User",
                    generation = generation
                )
            }
            return null
        }
        if (googleClientId.isNullOrBlank() || googleClientId == "mock") {
            commitIfCurrent(generation) {
                _authState.value = AuthState.Error(
                    "Google Drive needs a valid Desktop OAuth client ID. Configure it in Profile → Google Drive → Developer OAuth Credentials."
                )
            }
            return null
        }
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val callbackPort = 5805
        val redirectUri = "http://localhost:$callbackPort/callback"
        // Per-request CSRF state parameter (AUDIT H1): unguessable, validated on callback.
        val state = generateCodeVerifier()
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

        val pendingRequest = PendingOAuthRequest(state, generation) { code ->
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
                    val idToken = tokenData.id_token?.takeIf(String::isNotBlank)
                        ?: error("Google did not return an ID token during authorization")
                    applyGoogleTokens(
                        idToken = idToken,
                        accessToken = tokenData.access_token,
                        refreshToken = tokenData.refresh_token,
                        expiresIn = tokenData.expires_in,
                        emailFallback = "user@aresrobotics.org",
                        nameFallback = "Google User",
                        generation = generation
                    )
                } else {
                    val errorText = response.bodyAsText()
                    val sentParamsInfo = "Sent client_id: $googleClientId (Secret present: ${!googleClientSecret.isNullOrBlank()})"
                    updateStateIfCurrent(
                        generation,
                        AuthState.Error("Failed to exchange Google code: $errorText\nDetails: $sentParamsInfo")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateStateIfCurrent(generation, AuthState.Error("Google token exchange error: ${e.message}"))
            }
        }
        if (!registerPendingRequest(pendingRequest)) return null

        if (interactive) {
            bootCallbackServer(callbackPort, generation)
            launchBrowser(loginUrl, generation)
        }
        return state
    }

    /** Deterministic non-interactive seam for callback lifecycle tests. */
    internal fun beginGoogleLoginForTest(googleClientId: String, googleClientSecret: String? = null): String =
        requireNotNull(beginGoogleLogin(googleClientId, googleClientSecret, interactive = false)) {
            "Google authentication could not be started"
        }

    /**
     * Centralizes Google token handling: decode identity from the ID token, persist the
     * access/refresh tokens, and publish [AuthState.Authenticated].
     */
    private fun applyGoogleTokens(
        idToken: String,
        accessToken: String,
        refreshToken: String?,
        expiresIn: Int,
        emailFallback: String,
        nameFallback: String,
        generation: Long
    ): Boolean {
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
        var persisted = false
        val current = commitIfCurrent(generation) {
            try {
                saveAuth(saved)
                persisted = true
                _authState.value = AuthState.Authenticated(
                    idToken = idToken,
                    uid = uid,
                    email = email,
                    displayName = name
                )
            } catch (failure: Exception) {
                _authState.value = AuthState.Error(
                    "Authentication credentials could not be saved: ${failure.message ?: failure.javaClass.simpleName}",
                )
            }
        }
        return current && persisted
    }

    suspend fun refreshGoogleAccessToken(clientId: String, clientSecret: String?): String? =
        refreshGoogleAccessToken(clientId, clientSecret, authGeneration.get())

    private suspend fun refreshGoogleAccessToken(
        clientId: String,
        clientSecret: String?,
        generation: Long
    ): String? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            if (!isGenerationCurrent(generation)) return@withLock null
            val saved = getSavedAuth() ?: return@withLock null
            val refreshToken = saved.googleRefreshToken ?: return@withLock valueIfCurrent(generation) {
                saved.googleAccessToken
            }

            // Reuse current access token if not within 2 minutes of expiry.
            val expiresAt = saved.googleTokenExpiresAt ?: 0
            if (System.currentTimeMillis() < expiresAt - 120_000 && saved.googleAccessToken.isNotBlank()) {
                return@withLock valueIfCurrent(generation) { saved.googleAccessToken }
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
                        googleIdToken = data.id_token?.takeIf(String::isNotBlank) ?: saved.googleIdToken
                    )
                    val committed = commitIfCurrent(generation) {
                        saveAuth(updatedAuth)
                        // Google commonly omits id_token on refresh. Refresh identity only when
                        // one is explicitly returned and otherwise retain the established identity.
                        val current = _authState.value
                        val refreshedIdToken = data.id_token?.takeIf(String::isNotBlank)
                        if (current is AuthState.Authenticated && refreshedIdToken != null) {
                            val payload = decodeIdToken(refreshedIdToken)
                            _authState.value = current.copy(
                                idToken = refreshedIdToken,
                                email = payload.email ?: current.email,
                                displayName = payload.name ?: current.displayName
                            )
                        }
                    }
                    return@withLock data.access_token.takeIf { committed }
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
        val observedGeneration = authGeneration.get()
        val attempt = beginAuthAttempt(
            permitted = { it is AuthState.Authenticated },
            nextState = { it }
        )
        if (attempt == null) {
            commitIfCurrent(observedGeneration) {
                if (_authState.value !is AuthState.Authenticated && _authState.value !is AuthState.Authenticating) {
                    _authState.value = AuthState.Error("Must sign in with Google before linking GitHub")
                }
            }
            return
        }
        val generation = attempt.generation
        val currentAuth = attempt.previousState as AuthState.Authenticated

        if (githubClientId.isNullOrEmpty() || githubClientId == "mock") {
            updateStateIfCurrent(generation, currentAuth.copy(githubToken = "mock-github-token"))
            return
        }
        val callbackPort = 5805
        val redirectUri = "http://localhost:$callbackPort/callback"
        // Per-request CSRF state parameter (AUDIT H1): unguessable, validated on callback.
        val state = generateCodeVerifier()
        val loginUrl = "https://github.com/login/oauth/authorize?" +
                "client_id=$githubClientId" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&scope=read:org" +
                "&state=$state"

        val pendingRequest = PendingOAuthRequest(state, generation) { code ->
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
                    commitIfCurrent(generation) {
                        val current = _authState.value
                        if (current is AuthState.Authenticated) {
                            _authState.value = current.copy(githubToken = tokenData.access_token)
                        }
                    }
                } else {
                    val errorText = response.bodyAsText()
                    updateStateIfCurrent(generation, AuthState.Error("Failed to exchange GitHub code: $errorText"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateStateIfCurrent(generation, AuthState.Error("GitHub token exchange error: ${e.message}"))
            }
        }
        if (!registerPendingRequest(pendingRequest)) return

        bootCallbackServer(callbackPort, generation)
        launchBrowser(loginUrl, generation)
    }

    fun logout() {
        invalidateAuth(
            nextState = AuthState.Unauthenticated,
            deletePersistedAuth = true,
            markDisposed = false
        )
    }

    fun getSavedAuth(): OAuthSavedAuth? {
        if (!authFile.exists()) return null
        return try {
            AppJson.decodeFromString<OAuthSavedAuth>(authFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun saveAuth(auth: OAuthSavedAuth) {
        // Shared writeSecrets helper applies owner-only POSIX perms (AUDIT H2) the same way
        // EnvironmentService does for workspaces.json — keeps auth.json secret handling in
        // one place instead of a bespoke try/setPosixFilePermissions block here.
        secretsWriter(authFile, Json.encodeToString(auth).toByteArray(Charsets.UTF_8))
    }

    private fun beginAuthAttempt(
        permitted: (AuthState) -> Boolean,
        nextState: (AuthState) -> AuthState
    ): AuthAttempt? {
        var jobsToCancel: List<Job> = emptyList()
        var serverToStop: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        val attempt = synchronized(authLifecycleLock) {
            if (disposed) return@synchronized null
            val current = _authState.value
            if (!permitted(current)) return@synchronized null

            val generation = authGeneration.incrementAndGet()
            pendingOAuthRequest.set(null)
            jobsToCancel = authWorkJobs.toList()
            authWorkJobs.clear()
            serverToStop = server
            server = null
            serverGeneration = null
            _authState.value = nextState(current)
            AuthAttempt(generation, current)
        }
        if (attempt != null) {
            jobsToCancel.forEach { it.cancel() }
            stopEmbeddedServer(serverToStop)
        }
        return attempt
    }

    private fun registerPendingRequest(request: PendingOAuthRequest): Boolean =
        synchronized(authLifecycleLock) {
            if (!isGenerationCurrent(request.generation)) {
                false
            } else {
                pendingOAuthRequest.compareAndSet(null, request)
            }
        }

    /** Atomically consumes a matching state value; callback replays leave the request untouched. */
    private fun consumePendingRequest(returnedState: String?): PendingOAuthRequest? =
        synchronized(authLifecycleLock) {
            val pending = pendingOAuthRequest.get() ?: return@synchronized null
            if (
                returnedState == null ||
                returnedState != pending.state ||
                !isGenerationCurrent(pending.generation)
            ) {
                return@synchronized null
            }
            if (pendingOAuthRequest.compareAndSet(pending, null)) pending else null
        }

    private fun launchAuthWork(generation: Long, block: suspend () -> Unit): Job? {
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            if (isGenerationCurrent(generation)) block()
        }
        job.invokeOnCompletion {
            synchronized(authLifecycleLock) { authWorkJobs.remove(job) }
        }
        val registered = synchronized(authLifecycleLock) {
            if (isGenerationCurrent(generation)) {
                authWorkJobs.add(job)
                true
            } else {
                false
            }
        }
        if (registered) {
            job.start()
            return job
        }
        job.cancel()
        return null
    }

    private fun launchPendingCodeExchange(pending: PendingOAuthRequest, code: String): Job? =
        launchAuthWork(pending.generation) {
            try {
                pending.onCodeReceived(code)
            } finally {
                stopServer(pending.generation)
            }
        }

    /** Deterministic seam used to exercise callback replay and cancellation without a TCP server. */
    internal fun dispatchOAuthCallbackForTest(returnedState: String?, code: String): Job? {
        val pending = consumePendingRequest(returnedState) ?: return null
        return launchPendingCodeExchange(pending, code)
    }

    private fun commitIfCurrent(generation: Long, block: () -> Unit): Boolean =
        synchronized(authLifecycleLock) {
            if (!isGenerationCurrent(generation)) {
                false
            } else {
                block()
                true
            }
        }

    private fun <T> valueIfCurrent(generation: Long, block: () -> T): T? =
        synchronized(authLifecycleLock) {
            if (isGenerationCurrent(generation)) block() else null
        }

    private fun updateStateIfCurrent(generation: Long, state: AuthState): Boolean =
        commitIfCurrent(generation) { _authState.value = state }

    private fun isGenerationCurrent(generation: Long): Boolean =
        !disposed && authGeneration.get() == generation

    private fun invalidateAuth(
        nextState: AuthState,
        deletePersistedAuth: Boolean,
        markDisposed: Boolean
    ) {
        var jobsToCancel: List<Job> = emptyList()
        var serverToStop: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        synchronized(authLifecycleLock) {
            authGeneration.incrementAndGet()
            if (markDisposed) disposed = true
            pendingOAuthRequest.set(null)
            jobsToCancel = authWorkJobs.toList()
            authWorkJobs.clear()
            serverToStop = server
            server = null
            serverGeneration = null
            _authState.value = nextState
            if (deletePersistedAuth && authFile.exists()) authFile.delete()
        }
        jobsToCancel.forEach { it.cancel() }
        stopEmbeddedServer(serverToStop)
    }

    private fun bootCallbackServer(port: Int, generation: Long) {
        stopServer(generation)
        val candidate = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            routing {
                get("/callback") {
                    val returnedState = call.request.queryParameters["state"]
                    val pending = consumePendingRequest(returnedState)
                    if (pending == null) {
                        call.respondText("Authentication failed: invalid state parameter (possible CSRF attack).")
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
                        launchPendingCodeExchange(pending, code)
                    } else {
                        val msg = error ?: "Unknown auth error"
                        call.respondText("Authentication failed: $msg")
                        updateStateIfCurrent(pending.generation, AuthState.Error(msg))
                        serviceScope.launch { stopServer(pending.generation) }
                    }
                }
            }
        }
        val installed = synchronized(authLifecycleLock) {
            if (!isGenerationCurrent(generation)) {
                false
            } else {
                candidate.start(wait = false)
                server = candidate
                serverGeneration = generation
                true
            }
        }
        if (!installed) stopEmbeddedServer(candidate)
    }

    private fun launchBrowser(url: String, generation: Long) {
        launchAuthWork(generation) {
            try {
                withContext(Dispatchers.IO) {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(URI(url))
                    } else {
                        updateStateIfCurrent(
                            generation,
                            AuthState.Error("System browser not supported on this platform.")
                        )
                        stopServer(generation)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateStateIfCurrent(generation, AuthState.Error("Failed to launch system browser: ${e.message}"))
                stopServer(generation)
            }
        }
    }

    private fun stopServer(expectedGeneration: Long? = null) {
        val serverToStop = synchronized(authLifecycleLock) {
            if (expectedGeneration != null && serverGeneration != expectedGeneration) {
                null
            } else {
                server.also {
                    server = null
                    serverGeneration = null
                }
            }
        }
        stopEmbeddedServer(serverToStop)
    }

    private fun stopEmbeddedServer(
        target: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>?
    ) {
        target?.let { runCatching { it.stop(1000, 2000) } }
    }

    fun dispose() {
        invalidateAuth(
            nextState = AuthState.Unauthenticated,
            deletePersistedAuth = false,
            markDisposed = true
        )
        serviceScope.cancel()
        try {
            httpClient.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
