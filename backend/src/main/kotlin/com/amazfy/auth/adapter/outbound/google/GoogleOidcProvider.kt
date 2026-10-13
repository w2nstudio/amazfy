package com.amazfy.auth.adapter.outbound.google

import com.amazfy.auth.application.port.GoogleIdentityProvider
import com.amazfy.auth.domain.GoogleIdentity
import com.amazfy.auth.domain.GoogleLoginFailedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder

/**
 * Google as an OpenID Connect provider. Only basic profile scopes (openid, email, profile): no Google Ads access,
 * which is a separate consent ("Connect Google Ads", M4).
 */
@Component
class GoogleOidcProvider(
    @param:Value("\${amazfy.google.client-id}") private val clientId: String,
    @param:Value("\${amazfy.google.client-secret}") private val clientSecret: String,
    @param:Value("\${amazfy.google.redirect-uri}") private val redirectUri: String,
    @param:Value("\${amazfy.google.token-uri}") private val tokenUri: String,
    @Value("\${amazfy.google.jwk-set-uri}") jwkSetUri: String,
) : GoogleIdentityProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = RestClient.create()

    /**
     * Checks ID tokens against Google's public keys (downloaded from [jwkSetUri] and cached), so only Google can have
     * signed them. Plus: issued by Google, meant for our client id, not expired.
     */
    private val idTokenDecoder =
        NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtTimestampValidator(),
                    JwtClaimValidator<Any>(JwtClaimNames.ISS) { it in GOOGLE_ISSUERS },
                    JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD) { clientId in it },
                ),
            )
        }

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

    override fun exchangeCode(
        code: String,
        codeVerifier: String,
        nonce: String,
    ): GoogleIdentity {
        val idToken = requestIdToken(code, codeVerifier)
        val jwt =
            try {
                idTokenDecoder.decode(idToken)
            } catch (e: JwtException) {
                log.warn("Google ID token rejected: {}", e.message)
                throw GoogleLoginFailedException("the token from Google is not valid")
            }
        if (jwt.getClaimAsString("nonce") != nonce) throw GoogleLoginFailedException("the token from Google doesn't match this sign-in")

        return GoogleIdentity(
            subject = jwt.subject ?: throw GoogleLoginFailedException("the token from Google names no user"),
            email = jwt.getClaimAsString("email") ?: throw GoogleLoginFailedException("Google didn't share an email address"),
            emailVerified = jwt.getClaim<Any>("email_verified")?.toString() == "true",
            name = jwt.getClaimAsString("name"),
        )
    }

    /** Server-to-server call: the code alone is useless without our client secret and the PKCE verifier. */
    private fun requestIdToken(
        code: String,
        codeVerifier: String,
    ): String {
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
                throw GoogleLoginFailedException("Google didn't accept the sign-in")
            }
        return response?.get("id_token") as? String ?: throw GoogleLoginFailedException("Google sent no ID token")
    }

    companion object {
        const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"

        /** Google uses both spellings as the "iss" claim. */
        val GOOGLE_ISSUERS = setOf("https://accounts.google.com", "accounts.google.com")
    }
}
