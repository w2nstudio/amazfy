package com.amazfy.channel.application

import com.amazfy.account.application.AccountService
import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.application.port.GoogleAdsOAuth
import com.amazfy.channel.domain.GoogleAdsConnectAttempt
import com.amazfy.channel.domain.GoogleAdsConnectCancelledException
import com.amazfy.channel.domain.GoogleAdsConnectFailedException
import com.amazfy.common.crypto.OAuthValues
import com.amazfy.common.error.NotImplementedYetException
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Use case: a seller account's owner allows Amazfy to read its Google Ads (requirement A4). The browser takes a round
 * trip through Google: [start] sends it to the consent page, [complete] handles the callback that brings it back.
 * Separate from "Sign in with Google": another consent, and this one keeps a token.
 * Callers of [start] must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
class ConnectGoogleAds(
    private val google: GoogleAdsOAuth,
    private val connections: GoogleAdsConnections,
    private val accounts: AccountService,
    private val clock: Clock,
) {
    /**
     * The caller keeps the attempt in the browser until Google sends it back. Its state must come back unchanged, so a
     * callback can't be started from another site (CSRF); only the code verifier's hash goes to Google (PKCE), so a
     * stolen code is useless without the attempt.
     */
    fun start(
        accountId: UUID,
        userId: UUID,
    ): GoogleAdsConnectStart {
        if (!google.isConfigured) throw NotImplementedYetException("Connecting Google Ads")
        val attempt =
            GoogleAdsConnectAttempt(
                state = OAuthValues.newRandomValue(),
                codeVerifier = OAuthValues.newRandomValue(),
                accountId = accountId,
                userId = userId,
                expiresAt = clock.instant().plus(ATTEMPT_LIFETIME),
            )
        val url = google.authorizationUrl(attempt.state, OAuthValues.pkceChallenge(attempt.codeVerifier))
        return GoogleAdsConnectStart(url, attempt)
    }

    /**
     * Handles Google's redirect back to us. [attempt] is what [start] returned, as kept by the browser (null if it's
     * missing or was changed). Throws [GoogleAdsConnectCancelledException] when the seller pressed Cancel,
     * [GoogleAdsConnectFailedException] otherwise.
     */
    fun complete(
        code: String?,
        error: String?,
        state: String,
        attempt: GoogleAdsConnectAttempt?,
    ) {
        if (error == "access_denied") throw GoogleAdsConnectCancelledException()
        if (attempt == null) throw GoogleAdsConnectFailedException("no valid attempt in this browser")
        if (clock.instant().isAfter(attempt.expiresAt)) throw GoogleAdsConnectFailedException("the attempt expired")
        // Constant-time comparison, so response timing reveals nothing about the expected state
        if (!MessageDigest.isEqual(state.toByteArray(), attempt.state.toByteArray())) {
            throw GoogleAdsConnectFailedException("the state doesn't match this attempt")
        }
        if (error != null || code == null) throw GoogleAdsConnectFailedException("Google reported an error")
        // This request has no login, and the role may have changed while the browser was at Google
        if (accounts.roleOf(attempt.userId, attempt.accountId)?.canManage != true) {
            throw GoogleAdsConnectFailedException("the user can't manage this seller account")
        }

        val grant = google.exchangeCode(code, attempt.codeVerifier)
        // The consent page can let a person allow only part of what was asked for
        if (ADS_SCOPE !in grant.scopes) throw GoogleAdsConnectFailedException("the Google Ads permission wasn't allowed")
        val refreshToken = grant.refreshToken ?: throw GoogleAdsConnectFailedException("Google sent no refresh token")
        CurrentAccount.runAs(attempt.accountId) { connections.save(refreshToken, clock.instant()) }
    }

    companion object {
        const val ADS_SCOPE = "https://www.googleapis.com/auth/adwords"
        val ATTEMPT_LIFETIME: Duration = Duration.ofMinutes(10)
    }
}

data class GoogleAdsConnectStart(
    val redirectUrl: String,
    val attempt: GoogleAdsConnectAttempt,
)
