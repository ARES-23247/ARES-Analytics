package com.ares.analytics.service

import com.ares.analytics.BuildConfig
import com.ares.analytics.shared.WorkspaceConfig

private const val GOOGLE_DESKTOP_CLIENT_SUFFIX = ".apps.googleusercontent.com"

enum class GoogleOAuthClientSource {
    ARES_MANAGED,
    CUSTOM,
}

data class GoogleOAuthClientCredentials(
    val clientId: String,
    val source: GoogleOAuthClientSource,
)

sealed interface GoogleOAuthClientResolution {
    data class Available(val credentials: GoogleOAuthClientCredentials) : GoogleOAuthClientResolution
    data class Unavailable(val message: String) : GoogleOAuthClientResolution
}

internal fun isValidGoogleDesktopClientId(value: String?): Boolean {
    val normalized = value?.trim().orEmpty()
    return normalized.length in 30..256 &&
        normalized.endsWith(GOOGLE_DESKTOP_CLIENT_SUFFIX) &&
        normalized.none(Char::isWhitespace)
}

/**
 * Resolves the OAuth application identity without ever requiring a client secret.
 *
 * Existing workspace client IDs are intentionally ignored unless the administrator explicitly
 * enables the custom-client switch. This safely migrates installations that still contain the
 * deleted legacy client while preserving bring-your-own Google Cloud projects.
 */
class GoogleOAuthClientResolver(
    private val managedClientId: String = BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
) {
    val managedClientAvailable: Boolean
        get() = isValidGoogleDesktopClientId(managedClientId)

    fun resolve(config: WorkspaceConfig?): GoogleOAuthClientResolution {
        if (config?.googleOAuthUseCustomClient == true) {
            val customId = config.googleClientId?.trim()
            return if (isValidGoogleDesktopClientId(customId)) {
                GoogleOAuthClientResolution.Available(
                    GoogleOAuthClientCredentials(customId!!, GoogleOAuthClientSource.CUSTOM),
                )
            } else {
                GoogleOAuthClientResolution.Unavailable(
                    "The custom Google OAuth client ID is invalid. Disable the custom client to use ARES-managed sign-in, or enter a Desktop client ID ending in .apps.googleusercontent.com.",
                )
            }
        }

        val bundled = managedClientId.trim()
        return if (isValidGoogleDesktopClientId(bundled)) {
            GoogleOAuthClientResolution.Available(
                GoogleOAuthClientCredentials(bundled, GoogleOAuthClientSource.ARES_MANAGED),
            )
        } else {
            GoogleOAuthClientResolution.Unavailable(
                "Google sign-in is unavailable in this build. Install an official ARES Analytics release or ask an administrator to configure a custom Desktop OAuth client.",
            )
        }
    }
}
