package com.amazfy.channel.adapter.outbound.google

import com.amazfy.channel.application.ConnectGoogleAds
import com.amazfy.channel.application.port.GoogleAdsOAuth
import com.amazfy.channel.domain.GoogleAdsConnectFailedException
import com.amazfy.channel.domain.GoogleAdsGrant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder

/**
 * Google's OAuth for the Ads permission, with the same OAuth client as "Sign in with Google". Developer tokens were
 * retired in September 2026: API access belongs to the Google Cloud project that owns this client.
 * Setup: docs/guides/google-ads-setup.md.
 */
@Component
class GoogleAdsOAuthClient(
    @param:Value("\${amazfy.google.client-id}") private val clientId: String,
    @param:Value("\${amazfy.google.client-secret}") private val clientSecret: String,
    @param:Value("\${amazfy.google-ads.redirect-uri}") private val redirectUri: String,
    @param:Value("\${amazfy.google.token-uri}") private val tokenUri: String,
) : GoogleAdsOAuth {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = RestClient.create()

    override val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank() && redirectUri.isNotBlank()

    override fun authorizationUrl(
        state: String,
        codeChallenge: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(AUTHORIZATION_ENDPOINT)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", ConnectGoogleAds.ADS_SCOPE)
            .queryParam("state", state)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .queryParam("access_type", "offline") // a refresh token: the daily sync runs with nobody signed in
            // select_account: the Google login with Ads access is often not the one in use. consent: Google only sends
            // a refresh token on a consent, so connecting again must always ask
            .queryParam("prompt", "select_account consent")
            .encode()
            .build()
            .toUriString()

    /** Server-to-server call: the code alone is useless without our client secret and the PKCE verifier. */
    override fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): GoogleAdsGrant {
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", code)
                add("code_verifier", codeVerifier)
                add("client_id", clientId)
                add("client_secret", clientSecret)
                add("redirect_uri", redirectUri)
            }
        val response =
            try {
                http
                    .post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map::class.java)
            } catch (e: RestClientException) {
                log.warn("Google token request failed: {}", e.message) // the message never contains our secret
                throw GoogleAdsConnectFailedException("Google didn't accept the code")
            } ?: throw GoogleAdsConnectFailedException("Google sent an empty answer")
        return GoogleAdsGrant(
            refreshToken = response["refresh_token"] as? String,
            scopes = (response["scope"] as? String).orEmpty().split(" ").toSet(),
        )
    }

    private companion object {
        const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    }
}
