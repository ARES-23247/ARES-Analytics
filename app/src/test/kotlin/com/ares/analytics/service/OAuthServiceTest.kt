package com.ares.analytics.service

import com.ares.analytics.shared.AppWorkspaces
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the round-1/round-2 [OAuthService.loadPersistedAuth] rewrite: it must only restore
 * [AuthState.Authenticated] when the persisted-token refresh truly succeeds, stay
 * [AuthState.Unauthenticated] on refresh failure, and stay Unauthenticated when no client
 * config is present. Uses [OAuthService]'s injectable `httpClient` (MockEngine) and
 * `authFilePath` testability seams plus the internal [loadPersistedAuth] entry point so the
 * async init path can be awaited deterministically.
 */
class OAuthServiceTest {

    private lateinit var tempDir: File
    private lateinit var authFile: File
    private lateinit var envService: EnvironmentService

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "ares-oauth-test-${System.nanoTime()}").apply { mkdirs() }
        authFile = File(tempDir, "auth.json")
        envService = EnvironmentService(
            configPath = File(tempDir, "config.json").absolutePath,
            workspacesPath = File(tempDir, "workspaces.json").absolutePath
        )
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun writeAuth(refreshToken: String?, expired: Boolean) {
        val auth = OAuthSavedAuth(
            googleAccessToken = "old-access",
            googleRefreshToken = refreshToken,
            googleTokenExpiresAt = if (expired) System.currentTimeMillis() - 60_000 else System.currentTimeMillis() + 3_600_000,
            googleIdToken = makeIdToken("sub-9", "u@x.com", "User"),
            uid = "sub-9",
            email = "u@x.com",
            displayName = "User"
        )
        authFile.writeText(Json.encodeToString(auth))
    }

    private fun writeConfig(clientId: String?) {
        val workspaces = AppWorkspaces(
            activeWorkspaceId = "ws",
            workspaces = listOf(
                WorkspaceConfig(
                    id = "ws",
                    teamId = "23247",
                    seasonId = "2526",
                    robotId = "r1",
                    projectPath = tempDir.absolutePath,
                    league = League.FTC,
                    googleClientId = clientId,
                    googleClientSecret = "secret"
                )
            )
        )
        File(tempDir, "workspaces.json").writeText(Json.encodeToString(workspaces))
    }

    /** Builds an unsigned JWT whose payload carries the fields [OAuthService] decodes for identity. */
    private fun makeIdToken(sub: String, email: String, name: String): String {
        val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
        val payload = """{"sub":"$sub","email":"$email","name":"$name"}"""
        return "header." + encoder.encodeToString(payload.toByteArray()) + ".signature"
    }

    private fun mockClient(refreshSucceeds: Boolean, includeIdToken: Boolean = true): HttpClient = HttpClient(MockEngine { _ ->
        if (refreshSucceeds) {
            val idTokenField = if (includeIdToken) {
                "\"id_token\":\"${makeIdToken("sub-9", "u@x.com", "Refreshed")}\","
            } else {
                ""
            }
            val body = """{"access_token":"new-access",$idTokenField"expires_in":3600,"refresh_token":"rt"}"""
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        } else {
            respond("invalid_grant", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
        }
    }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun delayedSuccessClient(
        requestStarted: CompletableDeferred<Unit>,
        releaseResponse: CompletableDeferred<Unit>,
        requestCount: AtomicInteger = AtomicInteger()
    ): HttpClient = HttpClient(MockEngine { _ ->
        requestCount.incrementAndGet()
        requestStarted.complete(Unit)
        releaseResponse.await()
        val body = """{"access_token":"new-access","id_token":"${makeIdToken("sub-9", "u@x.com", "Refreshed")}","expires_in":3600,"refresh_token":"rt"}"""
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `refresh success restores Authenticated`() = runBlocking {
        writeAuth(refreshToken = "rt", expired = true)
        writeConfig(clientId = "client-123")
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = mockClient(refreshSucceeds = true),
            loadPersistedAuthOnInit = false
        )
        try {
            service.loadPersistedAuth()
            val state = service.authState.value
            assertTrue(state is AuthState.Authenticated, "Expected Authenticated after successful refresh, got $state")
        } finally {
            service.dispose()
        }
    }

    @Test
    fun `refresh may omit optional id token and retains established identity`() = runBlocking {
        writeAuth(refreshToken = "rt", expired = true)
        writeConfig(clientId = "client-123")
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = mockClient(refreshSucceeds = true, includeIdToken = false),
            loadPersistedAuthOnInit = false
        )
        try {
            service.loadPersistedAuth()
            val state = service.authState.value as AuthState.Authenticated
            assertEquals("User", state.displayName)
        } finally {
            service.dispose()
        }
    }

    @Test
    fun `refresh failure leaves Unauthenticated`() = runBlocking {
        writeAuth(refreshToken = "rt", expired = true)
        writeConfig(clientId = "client-123")
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = mockClient(refreshSucceeds = false),
            loadPersistedAuthOnInit = false
        )
        try {
            service.loadPersistedAuth()
            assertEquals(AuthState.Unauthenticated, service.authState.value)
        } finally {
            service.dispose()
        }
    }

    @Test
    fun `no client config leaves Unauthenticated`() = runBlocking {
        writeAuth(refreshToken = "rt", expired = true)
        writeConfig(clientId = null)
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = mockClient(refreshSucceeds = true), // must never be called
            loadPersistedAuthOnInit = false
        )
        try {
            service.loadPersistedAuth()
            assertEquals(AuthState.Unauthenticated, service.authState.value)
        } finally {
            service.dispose()
        }
    }

    @Test
    fun `logout generation rejects a delayed refresh commit`() = runBlocking {
        writeAuth(refreshToken = "rt", expired = true)
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = delayedSuccessClient(requestStarted, releaseResponse),
            loadPersistedAuthOnInit = false
        )
        try {
            val refresh = async(Dispatchers.Default) {
                service.refreshGoogleAccessToken("client-123", "secret")
            }
            withTimeout(5_000) { requestStarted.await() }

            service.logout()
            releaseResponse.complete(Unit)

            assertNull(withTimeout(5_000) { refresh.await() })
            assertEquals(AuthState.Unauthenticated, service.authState.value)
            assertFalse(authFile.exists(), "A stale refresh must not recreate auth.json after logout")
        } finally {
            releaseResponse.complete(Unit)
            service.dispose()
        }
    }

    @Test
    fun `logout cancels a delayed authorization-code exchange`() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = delayedSuccessClient(requestStarted, releaseResponse),
            loadPersistedAuthOnInit = false
        )
        try {
            val state = service.beginGoogleLoginForTest("client-123", "secret")
            val exchangeJob = assertNotNull(service.dispatchOAuthCallbackForTest(state, "authorization-code"))
            withTimeout(5_000) { requestStarted.await() }

            service.logout()
            releaseResponse.complete(Unit)
            withTimeout(5_000) { exchangeJob.join() }

            assertTrue(exchangeJob.isCancelled, "Logout must cancel service-owned token exchanges")
            assertEquals(AuthState.Unauthenticated, service.authState.value)
            assertFalse(authFile.exists(), "A canceled exchange must not recreate auth.json after logout")
        } finally {
            releaseResponse.complete(Unit)
            service.dispose()
        }
    }

    @Test
    fun `oauth state is consumed exactly once across duplicate callbacks`() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>().apply { complete(Unit) }
        val requestCount = AtomicInteger()
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = delayedSuccessClient(requestStarted, releaseResponse, requestCount),
            loadPersistedAuthOnInit = false
        )
        try {
            val state = service.beginGoogleLoginForTest("client-123", "secret")
            val dispatched = listOf("code-a", "code-b").map { code ->
                async(Dispatchers.Default) { service.dispatchOAuthCallbackForTest(state, code) }
            }.awaitAll()
            val accepted = dispatched.filterNotNull()

            assertEquals(1, accepted.size, "Only one callback may consume an OAuth state value")
            withTimeout(5_000) { accepted.single().join() }
            assertEquals(1, requestCount.get(), "Duplicate callbacks must not start another token exchange")
            assertTrue(service.authState.value is AuthState.Authenticated)
            assertTrue(authFile.isFile)
        } finally {
            service.dispose()
        }
    }

    @Test
    fun `credential replace failure preserves prior bytes and never publishes Authenticated`() = runBlocking {
        val previousBytes = "previous-auth-state".toByteArray()
        authFile.writeBytes(previousBytes)
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = mockClient(refreshSucceeds = true),
            loadPersistedAuthOnInit = false,
            secretsWriter = { file, bytes ->
                writeSecrets(file, bytes) { _, _ ->
                    throw IOException("injected auth replace failure")
                }
            },
        )
        try {
            val state = service.beginGoogleLoginForTest("client-123", "secret")
            val exchange = assertNotNull(service.dispatchOAuthCallbackForTest(state, "authorization-code"))
            withTimeout(5_000) { exchange.join() }

            val failure = assertIs<AuthState.Error>(service.authState.value)
            assertTrue(failure.message.contains("could not be saved"))
            assertTrue(previousBytes.contentEquals(authFile.readBytes()))
        } finally {
            service.dispose()
        }
    }
}
