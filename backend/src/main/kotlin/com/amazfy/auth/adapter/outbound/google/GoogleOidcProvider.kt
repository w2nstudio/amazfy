package com.amazfy.auth.adapter.outbound.google

import com.amazfy.auth.application.port.GoogleIdentityProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * Google as an OpenID Connect provider. Only basic profile scopes (openid, email, profile): no Google Ads access,
 * which is a separate consent ("Connect Google Ads", M4).
 */
@Component
class GoogleOidcProvider(
    @param:Value("\${amazfy.google.client-id}") private val clientId: String,
    @param:Value("\${amazfy.google.redirect-uri}") private val redirectUri: String,
) : GoogleIdentityProvider {
    override val isConfigured: Boolean get() = clientId.isNotBlank() && redirectUri.isNotBlank()

    override fun authorizationUrl(
        state: String,
        codeChallenge: String,
        nonce: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(AUTHORIZATION_ENDPOINT)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid email profile")
            .queryParam("state", state)
            .queryParam("nonce", nonce)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .queryParam("prompt", "select_account") // always let the user pick which Google account to use
            .encode()
            .build()
            .toUriString()

    companion object {
        const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    }
}
