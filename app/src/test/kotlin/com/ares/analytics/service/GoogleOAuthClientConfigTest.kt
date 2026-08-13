package com.ares.analytics.service

import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GoogleOAuthClientConfigTest {
    private val managed = "123456789012-managed.apps.googleusercontent.com"
    private val custom = "987654321098-custom.apps.googleusercontent.com"

    @Test
    fun `normal workspaces use managed application identity and ignore stale legacy values`() {
        val resolution = GoogleOAuthClientResolver(managed).resolve(
            workspace(googleClientId = "deleted.apps.googleusercontent.com"),
        )

        val available = assertIs<GoogleOAuthClientResolution.Available>(resolution)
        assertEquals(managed, available.credentials.clientId)
        assertEquals(GoogleOAuthClientSource.ARES_MANAGED, available.credentials.source)
    }

    @Test
    fun `administrator opt in uses a valid custom desktop client without a secret`() {
        val resolution = GoogleOAuthClientResolver(managed).resolve(
            workspace(googleClientId = custom, googleOAuthUseCustomClient = true),
        )

        val available = assertIs<GoogleOAuthClientResolution.Available>(resolution)
        assertEquals(custom, available.credentials.clientId)
        assertEquals(GoogleOAuthClientSource.CUSTOM, available.credentials.source)
    }

    @Test
    fun `invalid custom client fails closed with managed recovery guidance`() {
        val resolution = GoogleOAuthClientResolver(managed).resolve(
            workspace(googleClientId = "not-a-google-client", googleOAuthUseCustomClient = true),
        )

        val unavailable = assertIs<GoogleOAuthClientResolution.Unavailable>(resolution)
        assertTrue(unavailable.message.contains("Disable the custom client"))
    }

    @Test
    fun `deleted and revoked errors are actionable and do not expose client ids`() {
        val deleted = googleOAuthRecoveryMessage(
            """{"error":"deleted_client","error_description":"gone"}""",
            GoogleOAuthClientSource.CUSTOM,
        )
        val revoked = googleOAuthRecoveryMessage(
            """{"error":"invalid_grant"}""",
            GoogleOAuthClientSource.ARES_MANAGED,
        )

        assertTrue(deleted.contains("Disable the custom client"))
        assertTrue(revoked.contains("session was cleared"))
        assertTrue(!deleted.contains(custom))
    }

    @Test
    fun `confidential client failure explains the safe administrator recovery`() {
        val managedFailure = googleOAuthRecoveryMessage(
            """{"error":"invalid_request","error_description":"client_secret is missing."}""",
            GoogleOAuthClientSource.ARES_MANAGED,
        )
        val customFailure = googleOAuthRecoveryMessage(
            """{"error":"invalid_request","error_description":"client_secret is missing."}""",
            GoogleOAuthClientSource.CUSTOM,
        )

        assertTrue(managedFailure.contains("requires a client secret"))
        assertTrue(managedFailure.contains("public Desktop OAuth client"))
        assertTrue(customFailure.contains("custom OAuth client"))
        assertTrue(!managedFailure.contains(managed))
        assertTrue(!customFailure.contains(custom))
    }

    @Test
    fun `desktop callback uses the numeric loopback address`() {
        assertEquals("http://127.0.0.1:5805/callback", GOOGLE_DESKTOP_REDIRECT_URI)
    }

    private fun workspace(
        googleClientId: String?,
        googleOAuthUseCustomClient: Boolean = false,
    ) = WorkspaceConfig(
        id = "workspace",
        teamId = "23247",
        seasonId = "2026",
        robotId = "robot",
        projectPath = ".",
        league = League.FTC,
        googleClientId = googleClientId,
        googleOAuthUseCustomClient = googleOAuthUseCustomClient,
    )
}
