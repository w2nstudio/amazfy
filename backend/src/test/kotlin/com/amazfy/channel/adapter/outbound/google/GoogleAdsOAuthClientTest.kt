package com.amazfy.channel.adapter.outbound.google

import com.amazfy.channel.domain.GoogleAdsConnectFailedException
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriComponentsBuilder
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The real adapter against a fake Google: a local server with a token endpoint like Google's. */
class GoogleAdsOAuthClientTest {
    private var tokenResponse: Pair<Int, String> = 500 to "{}"
    private var lastTokenForm: Map<String, String> = emptyMap()

    private val fakeGoogle =
        HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/token") { exchange ->
                lastTokenForm =
                    exchange.requestBody
                        .readAllBytes()
                        .decodeToString()
                        .split("&")
                        .associate { pair -> pair.split("=").let { it[0] to URLDecoder.decode(it[1], Charsets.UTF_8) } }
                exchange.responseHeaders.add("Content-Type", "application/json")
                val bytes = tokenResponse.second.toByteArray()
                exchange.sendResponseHeaders(tokenResponse.first, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

    @AfterEach
    fun stopFakeGoogle() = fakeGoogle.stop(0)

    private fun client(clientId: String = "test-client-id") =
        GoogleAdsOAuthClient(
            clientId = clientId,
            clientSecret = "test-secret",
            redirectUri = "http://localhost:8080/api/v1/google-ads/callback",
            tokenUri = "http://localhost:${fakeGoogle.address.port}/token",
        )

    @Test
    fun `isn't configured without a client id`() {
        assertTrue(client().isConfigured)
        assertFalse(client(clientId = "").isConfigured)
    }

    @Test
    fun `the consent page asks for the Ads permission and a refresh token`() {
        val url = client().authorizationUrl(state = "the-state", codeChallenge = "the-challenge")
        val params =
            UriComponentsBuilder
                .fromUriString(url)
                .build()
                .queryParams
                .toSingleValueMap()

        fun decoded(name: String) = URLDecoder.decode(params[name], Charsets.UTF_8)

        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertEquals("test-client-id", decoded("client_id"))
        assertEquals("http://localhost:8080/api/v1/google-ads/callback", decoded("redirect_uri"))
        assertEquals("code", decoded("response_type"))
        assertEquals("https://www.googleapis.com/auth/adwords", decoded("scope"))
        assertEquals("the-state", decoded("state"))
        assertEquals("the-challenge", decoded("code_challenge"))
        assertEquals("S256", decoded("code_challenge_method"))
        assertEquals("offline", decoded("access_type"))
        assertEquals("select_account consent", decoded("prompt"))
    }

    @Test
    fun `a code becomes what Google granted, sent with our secret and the PKCE verifier`() {
        tokenResponse =
            200 to
            """{"access_token":"short","refresh_token":"1//long","scope":"https://www.googleapis.com/auth/adwords openid"}"""

        val grant = client().exchangeCode("the-code", "the-verifier")

        assertEquals("1//long", grant.refreshToken)
        assertEquals(setOf("https://www.googleapis.com/auth/adwords", "openid"), grant.scopes)
        assertEquals("authorization_code", lastTokenForm["grant_type"])
        assertEquals("the-code", lastTokenForm["code"])
        assertEquals("the-verifier", lastTokenForm["code_verifier"])
        assertEquals("test-secret", lastTokenForm["client_secret"])
        assertEquals("http://localhost:8080/api/v1/google-ads/callback", lastTokenForm["redirect_uri"])
    }

    @Test
    fun `an answer without a refresh token gives a grant without one`() {
        tokenResponse = 200 to """{"access_token":"short","scope":"https://www.googleapis.com/auth/adwords"}"""
        assertNull(client().exchangeCode("the-code", "the-verifier").refreshToken)
    }

    @Test
    fun `a refused code fails`() {
        tokenResponse = 400 to """{"error":"invalid_grant"}"""
        assertFailsWith<GoogleAdsConnectFailedException> { client().exchangeCode("old-code", "the-verifier") }
    }
}
