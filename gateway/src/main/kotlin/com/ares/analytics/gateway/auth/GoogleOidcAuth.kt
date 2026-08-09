package com.ares.analytics.gateway.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

private val oidcLogger = LoggerFactory.getLogger("GoogleOidcAuth")

/**
 * Known ARES Google OAuth client ID (the same one the desktop app uses). Used as the
 * mandatory audience default so that ID tokens minted for any other client are rejected.
 */
private const val DEFAULT_OIDC_AUDIENCE =
    "205869391101-nlcsea4539vjuo50i58bpo0t10d5s0ic.apps.googleusercontent.com"

private val clientId: String =
    System.getenv("GOOGLE_OIDC_CLIENT_ID") ?: DEFAULT_OIDC_AUDIENCE

/**
 * Hoisted, lazily-built verifier. A new `NetHttpTransport()` + `GoogleIdTokenVerifier`
 * was previously built on every request, re-fetching Google's public certs each time.
 * This single instance caches the JWK set and is reused for the process lifetime.
 */
private val idTokenVerifier by lazy {
    GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(listOf(clientId))
        .build()
}

/** Authenticated Google identity extracted from a verified OIDC ID token. */
data class GooglePrincipal(
    val subject: String,
    val email: String?,
    val name: String?
) : Principal

/**
 * Verifies a Google OIDC ID token: signature against Google's published certs plus issuer.
 * Audience is **always** enforced (defaults to [DEFAULT_OIDC_AUDIENCE] when
 * `GOOGLE_OIDC_CLIENT_ID` is unset) so a token minted for any other OAuth client is
 * rejected. Tests inject a fake via [GoogleOidcAuthenticationProvider.Config.tokenVerifier].
 */
fun verifyGoogleIdToken(idTokenString: String): GooglePrincipal? {
    val token = idTokenVerifier.verify(idTokenString) ?: return null
    val payload = token.payload
    return GooglePrincipal(
        subject = payload.subject,
        email = payload.email,
        name = payload.get("name") as? String
    )
}

/**
 * Ktor authentication provider that verifies a Google OIDC ID token from the
 * `Authorization: Bearer <token>` header. The token verifier is injectable so
 * tests can bypass real signature verification without a production backdoor.
 */
class GoogleOidcAuthenticationProvider(config: Config) : AuthenticationProvider(config) {
    private val tokenVerifier: (String) -> GooglePrincipal? = config.tokenVerifier

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val authHeader = context.call.request.headers[HttpHeaders.Authorization]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            context.challenge("GoogleOidc", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, "Missing Authorization Header with Bearer token")
                challenge.complete()
            }
            return
        }
        val token = authHeader.substring(7)
        try {
            val principal = tokenVerifier(token)
            if (principal == null) {
                context.challenge("GoogleOidc", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                    call.respond(HttpStatusCode.Unauthorized, "Invalid Google ID token")
                    challenge.complete()
                }
            } else {
                context.principal(principal)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            oidcLogger.warn("Google ID token verification failed", e)
            context.challenge("GoogleOidc", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, "Invalid Google ID token")
                challenge.complete()
            }
        }
    }

    class Config(name: String?) : AuthenticationProvider.Config(name) {
        var tokenVerifier: (String) -> GooglePrincipal? = ::verifyGoogleIdToken
    }
}

fun AuthenticationConfig.googleOidc(
    name: String? = "google",
    configure: GoogleOidcAuthenticationProvider.Config.() -> Unit = {}
) {
    register(GoogleOidcAuthenticationProvider(GoogleOidcAuthenticationProvider.Config(name).apply(configure)))
}

fun Application.installGoogleOidcAuthentication() {
    install(Authentication) {
        googleOidc("google")
    }
}
