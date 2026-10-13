package com.amazfy.auth.application

import com.amazfy.auth.application.port.GoogleIdentityProvider
import com.amazfy.auth.domain.GoogleIdentity
import com.amazfy.auth.domain.GoogleLoginFailedException
import com.amazfy.auth.domain.SecretTokenCodec
import com.amazfy.common.error.NotImplementedYetException
import org.springframework.stereotype.Service
import java.security.MessageDigest

/**
 * "Sign in with Google". Kept apart from AuthService because the browser takes a round trip through Google:
 * start() sends it there, complete() handles the callback that brings it back with a code.
 */
@Service
class GoogleLoginService(
    private val google: GoogleIdentityProvider,
) {
    /**
     * Starts a sign-in. Three random values protect it; the caller keeps them in a short-lived cookie until Google
     * sends the browser back:
     * - state: must come back unchanged, so a callback can't be started from another site (CSRF)
     * - code verifier (PKCE): only its hash goes to Google, so a stolen code is useless without the cookie
     * - nonce: Google puts it into the ID token, so an old token can't be replayed
     */
    fun start(): GoogleLoginStart {
        if (!google.isConfigured) throw NotImplementedYetException("Google login")
        val state = SecretTokenCodec.newToken()
        val codeVerifier = SecretTokenCodec.newToken()
        val nonce = SecretTokenCodec.newToken()
        // PKCE "S256": the challenge is the SHA-256 of the verifier, base64url without padding (SecretTokenCodec.hash)
        val url = google.authorizationUrl(state, codeChallenge = SecretTokenCodec.hash(codeVerifier), nonce = nonce)
        return GoogleLoginStart(url, GoogleRoundTrip(state, codeVerifier, nonce))
    }

    /**
     * Handles Google's redirect back to us. [roundTrip] is what start() returned, as kept in the cookie (null if the
     * cookie is missing, e.g. it expired after 10 minutes). Returns the verified Google identity.
     * Throws [GoogleSignInCancelledException] when the user pressed Cancel, GoogleLoginFailedException otherwise.
     */
    fun complete(
        code: String?,
        error: String?,
        state: String,
        roundTrip: GoogleRoundTrip?,
    ): GoogleIdentity {
        if (error == "access_denied") throw GoogleSignInCancelledException()
        if (roundTrip == null) throw GoogleLoginFailedException("the sign-in took too long or was started elsewhere")
        // Constant-time comparison, so response timing reveals nothing about the expected state
        if (!MessageDigest.isEqual(state.toByteArray(), roundTrip.state.toByteArray())) {
            throw GoogleLoginFailedException("the sign-in was started elsewhere")
        }
        if (error != null || code == null) throw GoogleLoginFailedException("Google reported an error")
        return google.exchangeCode(code, roundTrip.codeVerifier, roundTrip.nonce)
    }
}

/** The values from start() that must match when Google sends the browser back. */
data class GoogleRoundTrip(
    val state: String,
    val codeVerifier: String,
    val nonce: String,
) {
    override fun toString() = "GoogleRoundTrip(****)"
}

/** Not an error: the user chose not to sign in with Google. */
class GoogleSignInCancelledException : RuntimeException("Google sign-in cancelled")

data class GoogleLoginStart(
    val redirectUrl: String,
    val roundTrip: GoogleRoundTrip,
)
