package com.amazfy.common.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The random values that protect a browser's round trip through a provider's consent page (Google Ads, later Amazon). */
object OAuthValues {
    private val random = SecureRandom()
    private const val RANDOM_BYTES = 32 // 256 bits of randomness: impossible to guess

    fun newRandomValue(): String = encode(ByteArray(RANDOM_BYTES).also(random::nextBytes))

    /** PKCE "S256": the challenge is the SHA-256 of the verifier, base64url without padding. */
    fun pkceChallenge(codeVerifier: String): String = encode(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray()))

    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
