package com.amazfy.channel.adapter.inbound.web

import com.amazfy.channel.application.ConnectGoogleAds
import com.amazfy.channel.domain.GoogleAdsConnectAttempt
import com.amazfy.common.crypto.SecretCipher
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Keeps a connect attempt in the browser while it's at Google. Encrypted, because Google's callback carries no login and
 * the attempt says who is connecting which seller account: changing it must not be possible, unlike the login's cookie,
 * which holds only values the browser could at worst spoil for itself.
 */
@Component
class GoogleAdsConnectCookie(
    private val cipher: SecretCipher,
    @param:Value("\${amazfy.auth.cookie-secure}") private val cookieSecure: Boolean,
) {
    fun of(attempt: GoogleAdsConnectAttempt): String {
        val value = with(attempt) { listOf(state, codeVerifier, accountId, userId, expiresAt.epochSecond).joinToString(SEPARATOR) }
        return cookie(cipher.encrypt(value, CONTEXT), ConnectGoogleAds.ATTEMPT_LIFETIME)
    }

    /** Each attempt can be completed only once. */
    fun deleted(): String = cookie("", Duration.ZERO)

    /** The attempt in the cookie's value; null when there is none or it was changed. */
    fun read(value: String?): GoogleAdsConnectAttempt? {
        val parts = value?.let { cipher.decrypt(it, CONTEXT) }?.split(SEPARATOR) ?: return null
        return GoogleAdsConnectAttempt(
            state = parts[0],
            codeVerifier = parts[1],
            accountId = UUID.fromString(parts[2]),
            userId = UUID.fromString(parts[3]),
            expiresAt = Instant.ofEpochSecond(parts[4].toLong()),
        )
    }

    private fun cookie(
        value: String,
        maxAge: Duration,
    ) = ResponseCookie
        .from(NAME, value)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax") // Lax, not Strict: the browser must send it when Google redirects back to us
        .path(PATH)
        .maxAge(maxAge)
        .build()
        .toString()

    companion object {
        const val NAME = "amazfy_google_ads_connect"
        private const val PATH = "/api/v1/google-ads/callback"
        private const val CONTEXT = "google-ads-connect-attempt"
        private const val SEPARATOR = "."
    }
}
