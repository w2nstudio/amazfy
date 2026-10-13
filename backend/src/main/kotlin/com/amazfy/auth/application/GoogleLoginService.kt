package com.amazfy.auth.application

import com.amazfy.auth.application.port.GoogleIdentityProvider
import com.amazfy.auth.domain.SecretTokenCodec
import com.amazfy.common.error.NotImplementedYetException
import org.springframework.stereotype.Service

/**
 * "Sign in with Google". Kept apart from AuthService because the browser takes a round trip through Google:
 * start() sends it there, the callback (next step) brings it back with a code.
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
        return GoogleLoginStart(url, state, codeVerifier, nonce)
    }
}

data class GoogleLoginStart(
    val redirectUrl: String,
    val state: String,
    val codeVerifier: String,
    val nonce: String,
) {
    override fun toString() = "GoogleLoginStart(****)" // never log the secrets
}
