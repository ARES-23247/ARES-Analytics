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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun mockClient(refreshSucceeds: Boolean): HttpClient = HttpClient(MockEngine { _ ->
        if (refreshSucceeds) {
            val body = """{"access_token":"new-access","id_token":"${makeIdToken("sub-9", "u@x.com", "Refreshed")}","expires_in":3600,"refresh_token":"rt"}"""
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        } else {
            respond("invalid_grant", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
        }
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
            httpClient = mockClient(refreshSucceeds = true)
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
    fun `refresh failure leaves Unauthenticated`() = runBlocking {
        writeAuth(refreshToken = "rt", expired = true)
        writeConfig(clientId = "client-123")
        val service = OAuthService(
            environmentService = envService,
            authFilePath = authFile.absolutePath,
            httpClient = mockClient(refreshSucceeds = false)
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
            httpClient = mockClient(refreshSucceeds = true) // must never be called
        )
        try {
            service.loadPersistedAuth()
            assertEquals(AuthState.Unauthenticated, service.authState.value)
        } finally {
            service.dispose()
        }
    }
}
