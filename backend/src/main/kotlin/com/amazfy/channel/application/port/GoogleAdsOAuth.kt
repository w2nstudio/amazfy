package com.amazfy.channel.application.port

import com.amazfy.channel.domain.GoogleAdsGrant

/** Google's side of "Connect Google Ads". */
interface GoogleAdsOAuth {
    /** False when no OAuth client is set up here (Google setup: docs/guides/google-ads-setup.md). */
    val isConfigured: Boolean

    /** The consent page asking "may Amazfy read your Google Ads?". */
    fun authorizationUrl(
        state: String,
        codeChallenge: String,
    ): String

    /** Trades the one-time code from the consent page for what Google granted. Throws GoogleAdsConnectFailedException. */
    fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): GoogleAdsGrant
}
