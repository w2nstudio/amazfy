package com.amazfy.channel.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import com.amazfy.channel.adapter.outbound.persistence.GoogleAdsConnectionsSql
import com.amazfy.channel.application.ConnectGoogleAds
import com.amazfy.channel.application.port.GoogleAdsOAuth
import com.amazfy.channel.domain.GoogleAdsConnectAttempt
import com.amazfy.channel.domain.GoogleAdsGrant
import com.amazfy.common.crypto.SecretCipher
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Connect Google Ads" through HTTP, with Google replaced by a fake that grants whatever the test chooses. (The real
 * calls to Google are tested in GoogleAdsOAuthClientTest.)
 */
@Import(TestcontainersConfiguration::class, GoogleAdsConnectionEndpointTest.FakeGoogleConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
class GoogleAdsConnectionEndpointTest {
    /** Google stand-in: the next code becomes [nextGrant]; the consent page's address shows the state it was given. */
    class FakeGoogleAds : GoogleAdsOAuth {
        var nextGrant = GoogleAdsGrant("1//refresh-token", setOf(ConnectGoogleAds.ADS_SCOPE))
        var configured = true

        override val isConfigured get() = configured

        override fun authorizationUrl(
            state: String,
            codeChallenge: String,
        ) = "https://google.example/consent?state=$state"

        override fun exchangeCode(
            code: String,
            codeVerifier: String,
        ) = nextGrant
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FakeGoogleConfig {
        @Bean
        @Primary
        fun fakeGoogleAds() = FakeGoogleAds()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var json: JsonMapper

    @Autowired
    lateinit var google: FakeGoogleAds

    @Autowired
    lateinit var cipher: SecretCipher

    @Autowired
    lateinit var attemptCookie: GoogleAdsConnectCookie

    private inner class Seller {
        val token: String
        val accountId: UUID
        val userId: UUID

        init {
            val body =
                mockMvc
                    .post("/api/v1/auth/register") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"email":"connect-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc.","region":"NA"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            val me = readTree(mockMvc.get("/api/v1/me") { bearer() }.andReturn().response)
            accountId = UUID.fromString(me["accounts"][0]["account"]["id"].asString())
            userId = UUID.fromString(me["id"].asString())
        }

        fun MockHttpServletRequestDsl.bearer() = header(HttpHeaders.AUTHORIZATION, "Bearer $token")

        fun connectionOf(account: UUID = accountId) =
            mockMvc.get("/api/v1/accounts/$account/google-ads/connection") { bearer() }.andReturn().response

        fun connection(): JsonNode = readTree(connectionOf())

        fun start(account: UUID = accountId) =
            mockMvc.post("/api/v1/accounts/$account/google-ads/connect") { bearer() }.andReturn().response

        fun disconnect(account: UUID = accountId) =
            mockMvc.delete("/api/v1/accounts/$account/google-ads/connection") { bearer() }.andReturn().response

        /** Start → Google's consent page (fake) → callback, as the browser would. */
        fun connect(): MockHttpServletResponse {
            val started = start()
            return callback(stateIn(started), started.getCookie(GoogleAdsConnectCookie.NAME)?.value, "code" to "the-code")
        }

        fun storedToken(): String? =
            jdbc
                .sql("SELECT encrypted_refresh_token FROM google_ads_connections WHERE account_id = :a")
                .param("a", accountId)
                .query(String::class.java)
                .optional()
                .orElse(null)

        fun addMember(
            member: Seller,
            role: String,
        ) {
            jdbc
                .sql("INSERT INTO memberships (user_id, account_id, role) VALUES (:u, :a, :r)")
                .param("u", member.userId)
                .param("a", accountId)
                .param("r", role)
                .update()
        }
    }

    private fun readTree(response: MockHttpServletResponse): JsonNode = json.readTree(response.contentAsString)

    private fun stateIn(started: MockHttpServletResponse): String =
        readTree(started)["authorizationUrl"].asString().substringAfter("state=")

    /** Google redirecting the browser back: no login, only the attempt's cookie (if any). */
    private fun callback(
        state: String,
        cookie: String?,
        vararg params: Pair<String, String>,
    ): MockHttpServletResponse =
        mockMvc
            .get("/api/v1/google-ads/callback") {
                param("state", state)
                params.forEach { (name, value) -> param(name, value) }
                cookie?.let { cookie(Cookie(GoogleAdsConnectCookie.NAME, it)) }
            }.andReturn()
            .response

    private fun assertFailed(response: MockHttpServletResponse) {
        assertEquals(302, response.status)
        assertEquals("http://localhost:5173/ads?googleAds=failed", response.redirectedUrl)
    }

    private fun withGoogleGranting(
        grant: GoogleAdsGrant,
        block: () -> Unit,
    ) {
        val before = google.nextGrant
        google.nextGrant = grant
        try {
            block()
        } finally {
            google.nextGrant = before
        }
    }

    @Test
    fun `a new seller account isn't connected`() {
        val connection = Seller().connection()
        assertFalse(connection["connected"].asBoolean())
        assertNull(connection["connectedAt"])
    }

    @Test
    fun `connecting stores the token encrypted and returns to the Ads page`() {
        val seller = Seller()

        val response = seller.connect()

        assertEquals(302, response.status)
        assertEquals("http://localhost:5173/ads?googleAds=connected", response.redirectedUrl)
        val connection = seller.connection()
        assertTrue(connection["connected"].asBoolean())
        assertNotNull(connection["connectedAt"])

        val stored = assertNotNull(seller.storedToken())
        assertFalse("refresh-token" in stored)
        assertEquals("1//refresh-token", cipher.decrypt(stored, GoogleAdsConnectionsSql.tokenContext(seller.accountId)))
    }

    @Test
    fun `the token is never in an answer`() {
        val seller = Seller()
        val response = seller.connect()
        assertFalse("refresh-token" in response.contentAsString + response.redirectedUrl + response.getHeaders("Set-Cookie"))
        assertFalse(seller.connection().toString().contains(seller.storedToken()!!))
    }

    @Test
    fun `the attempt's cookie stays with the callback, and the callback deletes it`() {
        val seller = Seller()
        val started = seller.start()
        val cookie = assertNotNull(started.getCookie(GoogleAdsConnectCookie.NAME))
        assertTrue(cookie.isHttpOnly)
        assertEquals("/api/v1/google-ads/callback", cookie.path)
        assertEquals("Lax", cookie.getAttribute("SameSite"))

        val response = callback(stateIn(started), cookie.value, "code" to "the-code")
        assertEquals(0, response.getCookie(GoogleAdsConnectCookie.NAME)?.maxAge)
    }

    @Test
    fun `connecting again replaces the token`() {
        val seller = Seller()
        seller.connect()
        val first = seller.storedToken()

        withGoogleGranting(GoogleAdsGrant("1//second-token", setOf(ConnectGoogleAds.ADS_SCOPE))) { seller.connect() }

        assertEquals(
            "1//second-token",
            cipher.decrypt(seller.storedToken()!!, GoogleAdsConnectionsSql.tokenContext(seller.accountId)),
        )
        assertFalse(first == seller.storedToken())
    }

    @Test
    fun `disconnecting deletes the token, and may be repeated`() {
        val seller = Seller()
        seller.connect()

        assertEquals(204, seller.disconnect().status)

        assertNull(seller.storedToken())
        assertFalse(seller.connection()["connected"].asBoolean())
        assertEquals(204, seller.disconnect().status)
    }

    @Test
    fun `seller accounts don't share connections`() {
        val connected = Seller()
        val other = Seller()
        connected.connect()

        assertFalse(other.connection()["connected"].asBoolean())
        assertEquals(204, other.disconnect(other.accountId).status)
        assertTrue(connected.connection()["connected"].asBoolean())
    }

    @Test
    fun `a stranger can't see or change another seller account's connection`() {
        val seller = Seller()
        seller.connect()
        val stranger = Seller()

        assertEquals(403, stranger.connectionOf(seller.accountId).status)
        assertEquals(403, stranger.start(seller.accountId).status)
        assertEquals(403, stranger.disconnect(seller.accountId).status)
        assertNotNull(seller.storedToken())
    }

    @Test
    fun `editors and viewers see the connection, but only owners change it`() {
        val seller = Seller()
        seller.connect()
        val members = listOf("EDITOR", "VIEWER").map { role -> Seller().also { seller.addMember(it, role) } }

        members.forEach { member ->
            assertEquals(200, member.connectionOf(seller.accountId).status)
            assertEquals(403, member.start(seller.accountId).status)
            assertEquals(403, member.disconnect(seller.accountId).status)
        }
        assertNotNull(seller.storedToken())
    }

    @Test
    fun `starting needs a login, but the callback doesn't`() {
        val seller = Seller()
        assertEquals(
            401,
            mockMvc
                .post("/api/v1/accounts/${seller.accountId}/google-ads/connect")
                .andReturn()
                .response.status,
        )
        assertFailed(callback("any-state", cookie = null, "code" to "the-code"))
    }

    @Test
    fun `starting says so when Google isn't set up here`() {
        google.configured = false
        try {
            assertEquals(501, Seller().start().status)
        } finally {
            google.configured = true
        }
    }

    @Test
    fun `pressing Cancel on Google's page just returns, without a message`() {
        val seller = Seller()
        val started = seller.start()

        val response = callback(stateIn(started), started.getCookie(GoogleAdsConnectCookie.NAME)?.value, "error" to "access_denied")

        assertEquals("http://localhost:5173/ads", response.redirectedUrl)
        assertNull(seller.storedToken())
    }

    @Test
    fun `a callback fails without the cookie, with another state or with a changed cookie`() {
        val seller = Seller()
        val started = seller.start()
        val state = stateIn(started)
        val cookie = started.getCookie(GoogleAdsConnectCookie.NAME)!!.value

        assertFailed(callback(state, cookie = null, "code" to "the-code"))
        assertFailed(callback("another-state", cookie, "code" to "the-code"))
        assertFailed(callback(state, cookie.dropLast(4) + "AAAA", "code" to "the-code"))
        assertFailed(callback(state, "not-a-cookie", "code" to "the-code"))
        assertFailed(callback(state, cookie, "error" to "server_error"))
        assertNull(seller.storedToken())
    }

    private fun attempt(
        seller: Seller,
        userId: UUID = seller.userId,
        expiresAt: Instant = Instant.now().plusSeconds(60),
    ) = GoogleAdsConnectAttempt("the-state", "the-verifier", seller.accountId, userId, expiresAt)

    /** The value a browser would hold for [attempt]. */
    private fun cookieValue(attempt: GoogleAdsConnectAttempt) =
        attemptCookie.of(attempt).substringAfter("${GoogleAdsConnectCookie.NAME}=").substringBefore(";")

    @Test
    fun `an old attempt can't be completed`() {
        val seller = Seller()
        val expired = cookieValue(attempt(seller, expiresAt = Instant.now().minusSeconds(1)))

        assertFailed(callback("the-state", expired, "code" to "the-code"))
        assertNull(seller.storedToken())
    }

    @Test
    fun `an attempt fails when its user is no longer an owner of the seller account`() {
        val seller = Seller()
        val stranger = Seller()

        assertFailed(callback("the-state", cookieValue(attempt(seller, userId = stranger.userId)), "code" to "the-code"))
        assertNull(seller.storedToken())
    }

    @Test
    fun `a grant without the Ads permission or a refresh token fails`() {
        val seller = Seller()

        withGoogleGranting(GoogleAdsGrant("1//refresh-token", setOf("openid"))) { assertFailed(seller.connect()) }
        withGoogleGranting(GoogleAdsGrant(null, setOf(ConnectGoogleAds.ADS_SCOPE))) { assertFailed(seller.connect()) }

        assertNull(seller.storedToken())
    }
}
