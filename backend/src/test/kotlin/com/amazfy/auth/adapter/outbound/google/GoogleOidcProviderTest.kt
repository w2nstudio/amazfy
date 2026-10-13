package com.amazfy.auth.adapter.outbound.google

import com.amazfy.auth.domain.GoogleLoginFailedException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Instant
import java.util.Date
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The real adapter against a fake Google: a local server with a token endpoint and a public key, like Google's. */
class GoogleOidcProviderTest {
    private val clientId = "test-client-id.apps.googleusercontent.com"
    private val googleKey: RSAKey = RSAKeyGenerator(2048).keyID("google-key-1").generate()

    /** What the fake token endpoint answers next, and the form it received last. */
    private var tokenResponse: Pair<Int, String> = 500 to "{}"
    private var lastTokenForm: Map<String, String> = emptyMap()

    private val fakeGoogle =
        HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/certs") { respond(it, 200, JWKSet(googleKey.toPublicJWK()).toString()) }
            createContext("/token") { exchange ->
                lastTokenForm =
                    exchange.requestBody
                        .readAllBytes()
                        .decodeToString()
                        .split("&")
                        .associate { pair -> pair.split("=").let { it[0] to URLDecoder.decode(it[1], Charsets.UTF_8) } }
                respond(exchange, tokenResponse.first, tokenResponse.second)
            }
            start()
        }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String,
    ) {
        exchange.responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @AfterEach
    fun stopFakeGoogle() = fakeGoogle.stop(0)

    private val provider by lazy {
        val base = "http://localhost:${fakeGoogle.address.port}"
        GoogleOidcProvider(
            clientId = clientId,
            clientSecret = "test-secret",
            redirectUri = "http://localhost:8080/api/v1/auth/google/callback",
            tokenUri = "$base/token",
            jwkSetUri = "$base/certs",
        )
    }

    private fun claims(
        nonce: String = "the-nonce",
        audience: String = clientId,
        issuer: String = "https://accounts.google.com",
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ): JWTClaimsSet =
        JWTClaimsSet
            .Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("109876543210")
            .claim("email", "john@gmail.com")
            .claim("email_verified", true)
            .claim("name", "John Seller")
            .claim("nonce", nonce)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(expiresAt))
            .build()

    private fun googleReturns(
        claims: JWTClaimsSet,
        signedBy: RSAKey = googleKey,
    ) {
        val idToken =
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signedBy.keyID).build(), claims)
                .apply { sign(RSASSASigner(signedBy)) }
                .serialize()
        tokenResponse = 200 to """{"id_token":"$idToken","access_token":"unused","token_type":"Bearer"}"""
    }

    private fun exchange() = provider.exchangeCode(code = "the-code", codeVerifier = "the-verifier", nonce = "the-nonce")

    @Test
    fun `a valid ID token gives the Google identity`() {
        googleReturns(claims())

        val identity = exchange()

        assertEquals("109876543210", identity.subject)
        assertEquals("john@gmail.com", identity.email)
        assertEquals(true, identity.emailVerified)
        assertEquals("John Seller", identity.name)
    }

    @Test
    fun `the code is exchanged with the PKCE verifier and our client credentials`() {
        googleReturns(claims())
        exchange()

        assertEquals("authorization_code", lastTokenForm["grant_type"])
        assertEquals("the-code", lastTokenForm["code"])
        assertEquals("the-verifier", lastTokenForm["code_verifier"])
        assertEquals(clientId, lastTokenForm["client_id"])
        assertEquals("test-secret", lastTokenForm["client_secret"])
    }

    @Test
    fun `a token from a different sign-in (wrong nonce) is rejected`() {
        googleReturns(claims(nonce = "another-nonce"))
        assertFailsWith<GoogleLoginFailedException> { exchange() }
    }

    @Test
    fun `a token meant for another app (wrong audience) is rejected`() {
        googleReturns(claims(audience = "someone-elses-app.apps.googleusercontent.com"))
        assertFailsWith<GoogleLoginFailedException> { exchange() }
    }

    @Test
    fun `a token not issued by Google is rejected`() {
        googleReturns(claims(issuer = "https://evil.example"))
        assertFailsWith<GoogleLoginFailedException> { exchange() }
    }

    @Test
    fun `an expired token is rejected`() {
        googleReturns(claims(expiresAt = Instant.now().minusSeconds(600)))
        assertFailsWith<GoogleLoginFailedException> { exchange() }
    }

    @Test
    fun `a token signed with someone else's key is rejected`() {
        val forger = RSAKeyGenerator(2048).keyID("google-key-1").generate() // same key id, different key
        googleReturns(claims(), signedBy = forger)
        assertFailsWith<GoogleLoginFailedException> { exchange() }
    }

    @Test
    fun `Google refusing the code (e g used twice) fails the sign-in`() {
        tokenResponse = 400 to """{"error":"invalid_grant"}"""
        val error = assertFailsWith<GoogleLoginFailedException> { exchange() }
        assertContains(error.message!!, "didn't accept")
    }
}
