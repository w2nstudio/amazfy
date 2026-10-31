package com.amazfy.channel.domain

import java.time.Instant
import java.util.UUID

/** The seller account has allowed Amazfy to read its Google Ads. The refresh token stays in the persistence adapter. */
data class GoogleAdsConnection(
    val connectedAt: Instant,
)

/** What Google granted after the consent page. Not a data class: its toString must never print the token. */
class GoogleAdsGrant(
    val refreshToken: String?,
    val scopes: Set<String>,
)

/**
 * One try at connecting, kept in the browser (encrypted) between leaving for Google's consent page and coming back.
 * Google's callback carries no login, so this is what says who started it, for which seller account.
 */
data class GoogleAdsConnectAttempt(
    val state: String,
    val codeVerifier: String,
    val accountId: UUID,
    val userId: UUID,
    val expiresAt: Instant,
) {
    override fun toString() = "GoogleAdsConnectAttempt(****)"
}

/** The reason is for the log only. The seller just sees that connecting failed. */
class GoogleAdsConnectFailedException(
    reason: String,
) : RuntimeException("Connecting Google Ads failed: $reason")

/** Not an error: the seller pressed Cancel on Google's consent page. */
class GoogleAdsConnectCancelledException : RuntimeException("Connecting Google Ads was cancelled")
