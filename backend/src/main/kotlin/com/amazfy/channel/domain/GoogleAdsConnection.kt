package com.amazfy.channel.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.time.Instant
import java.util.UUID

/**
 * The seller account has allowed Amazfy to read its Google Ads, and which Google Ads account it chose (none yet right
 * after connecting). The refresh token stays with the application's use cases and the persistence adapter.
 */
data class GoogleAdsConnection(
    val connectedAt: Instant,
    val account: GoogleAdsAccount?,
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

class GoogleAdsNotConnectedException : AppException(ErrorKind.CONFLICT, "Connect Google Ads first.")

/** Google refuses the stored token: expired (after 7 days while the app is in Testing), or the seller removed Amazfy. */
class GoogleAdsConnectionExpiredException :
    AppException(ErrorKind.CONFLICT, "Google no longer accepts this connection. Please connect Google Ads again.")

/** Google failed or refused the call, for a reason the seller can't do much about. [reason] is for the log. */
class GoogleAdsReadFailedException(
    val reason: String? = null,
) : AppException(ErrorKind.BAD_GATEWAY, "Google Ads couldn't be read right now. Please try again.")

/** The chosen account isn't among those this connection can reach. */
class UnknownGoogleAdsAccountException : AppException(ErrorKind.BAD_REQUEST, "Choose one of the Google Ads accounts listed.")
