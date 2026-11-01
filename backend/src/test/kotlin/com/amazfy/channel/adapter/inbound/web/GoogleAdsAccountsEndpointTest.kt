package com.amazfy.channel.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.application.port.GoogleAdsReader
import com.amazfy.channel.domain.GoogleAdsClient
import com.amazfy.channel.domain.GoogleAdsConnectionExpiredException
import com.amazfy.channel.domain.GoogleAdsReadFailedException
import com.amazfy.common.tenant.CurrentAccount
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Choosing a Google Ads account through HTTP, with Google replaced by a fake. (Real calls: GoogleAdsRestReaderTest.) */
@Import(TestcontainersConfiguration::class, GoogleAdsAccountsEndpointTest.FakeGoogleConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
class GoogleAdsAccountsEndpointTest {
    /** Google stand-in: one manager above two client accounts, and one more account the login reaches directly. */
    class FakeGoogleReader : GoogleAdsReader {
        var tokenExpired = false
        var unreadable = emptySet<String>()
        var lastRefreshToken: String? = null

        override fun accessToken(refreshToken: String): String {
            lastRefreshToken = refreshToken
            if (tokenExpired) throw GoogleAdsConnectionExpiredException()
            return "short-lived"
        }

        override fun accessibleCustomerIds(accessToken: String) = listOf(MANAGER, DIRECT)

        override fun hierarchyOf(
            customerId: String,
            accessToken: String,
        ): List<GoogleAdsClient> {
            if (customerId in unreadable) throw GoogleAdsReadFailedException("refused")
            return when (customerId) {
                MANAGER ->
                    listOf(
                        GoogleAdsClient(MANAGER, "Agency", isManager = true, "ENABLED", isTestAccount = false, level = 0),
                        GoogleAdsClient(UNDER_MANAGER, "AquaLife", isManager = false, "ENABLED", isTestAccount = false, level = 1),
                        GoogleAdsClient(CLOSED, "Old shop", isManager = false, "CLOSED", isTestAccount = false, level = 1),
                    )
                else -> listOf(GoogleAdsClient(DIRECT, null, isManager = false, "ENABLED", isTestAccount = false, level = 0))
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FakeGoogleConfig {
        @Bean
        @Primary
        fun fakeGoogleReader() = FakeGoogleReader()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var json: JsonMapper

    @Autowired
    lateinit var google: FakeGoogleReader

    @Autowired
    lateinit var connections: GoogleAdsConnections

    @BeforeEach
    fun resetGoogle() {
        google.tokenExpired = false
        google.unreadable = emptySet()
    }

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
                            """{"email":"pick-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc.","region":"NA"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            val me = read(mockMvc.get("/api/v1/me") { bearer() }.andReturn().response)
            accountId = UUID.fromString(me["accounts"][0]["account"]["id"].asString())
            userId = UUID.fromString(me["id"].asString())
        }

        fun MockHttpServletRequestDsl.bearer() = header(HttpHeaders.AUTHORIZATION, "Bearer $token")

        fun connect(refreshToken: String = "1//refresh-token") =
            CurrentAccount.runAs(accountId) { connections.save(refreshToken, Instant.parse("2026-11-01T10:00:00Z")) }

        fun list(account: UUID = accountId) = mockMvc.get("/api/v1/accounts/$account/google-ads/accounts") { bearer() }.andReturn().response

        fun choose(
            customerId: String,
            account: UUID = accountId,
        ) = mockMvc
            .put("/api/v1/accounts/$account/google-ads/connection/account") {
                bearer()
                contentType = MediaType.APPLICATION_JSON
                content = """{"customerId":"$customerId"}"""
            }.andReturn()
            .response

        fun connection(): JsonNode =
            read(mockMvc.get("/api/v1/accounts/$accountId/google-ads/connection") { bearer() }.andReturn().response)

        fun disconnect() = mockMvc.delete("/api/v1/accounts/$accountId/google-ads/connection") { bearer() }

        fun storedLoginCustomerId(): String? =
            jdbc
                .sql("SELECT login_customer_id FROM google_ads_connections WHERE account_id = :a")
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

    private fun read(response: MockHttpServletResponse): JsonNode = json.readTree(response.contentAsString)

    @Test
    fun `lists accounts that hold campaigns, sorted by name, without internal details`() {
        val seller = Seller()
        seller.connect()

        val response = seller.list()

        assertEquals(200, response.status)
        val accounts = read(response).toList()
        assertEquals(listOf(UNDER_MANAGER, DIRECT), accounts.map { it["customerId"].asString() })
        assertEquals("AquaLife", accounts[0]["name"].asString())
        assertNull(accounts[1]["name"])
        assertFalse("loginCustomerId" in response.contentAsString)
        assertEquals("1//refresh-token", google.lastRefreshToken, "the stored token, decrypted, is what reaches Google")
    }

    @Test
    fun `one unreadable account doesn't hide the others`() {
        val seller = Seller()
        seller.connect()
        google.unreadable = setOf(MANAGER)

        assertEquals(listOf(DIRECT), read(seller.list()).toList().map { it["customerId"].asString() })
    }

    @Test
    fun `says so when no account can be read at all`() {
        val seller = Seller()
        seller.connect()
        google.unreadable = setOf(MANAGER, DIRECT)

        assertEquals(502, seller.list().status)
    }

    @Test
    fun `listing needs a connection`() {
        assertEquals(409, Seller().list().status)
    }

    @Test
    fun `asks to connect again when Google refuses the token`() {
        val seller = Seller()
        seller.connect()
        google.tokenExpired = true

        val response = seller.list()

        assertEquals(409, response.status)
        assertTrue("connect Google Ads again" in response.contentAsString)
    }

    @Test
    fun `choosing stores the account with what it's reached through, and the connection shows it`() {
        val seller = Seller()
        seller.connect()
        assertNull(seller.connection()["account"])

        val response = seller.choose(UNDER_MANAGER)

        assertEquals(200, response.status)
        assertEquals("AquaLife", read(response)["name"].asString())
        val account = seller.connection()["account"]
        assertEquals(UNDER_MANAGER, account["customerId"].asString())
        assertEquals("AquaLife", account["name"].asString())
        assertEquals(MANAGER, seller.storedLoginCustomerId())
    }

    @Test
    fun `an account that isn't offered can't be chosen`() {
        val seller = Seller()
        seller.connect()

        listOf(MANAGER, CLOSED, "5555555555").forEach { assertEquals(400, seller.choose(it).status, it) }
        assertNull(seller.connection()["account"])
    }

    @Test
    fun `connecting again keeps the chosen account, and disconnecting forgets it`() {
        val seller = Seller()
        seller.connect()
        seller.choose(DIRECT)

        seller.connect("1//new-token")
        assertEquals(DIRECT, seller.connection()["account"]["customerId"].asString())

        seller.disconnect()
        seller.connect()
        assertNull(seller.connection()["account"])
    }

    @Test
    fun `the account can be changed`() {
        val seller = Seller()
        seller.connect()
        seller.choose(UNDER_MANAGER)
        seller.choose(DIRECT)

        assertEquals(DIRECT, seller.connection()["account"]["customerId"].asString())
        assertEquals(DIRECT, seller.storedLoginCustomerId())
    }

    @Test
    fun `a seller account's choice stays its own`() {
        val first = Seller()
        val second = Seller()
        first.connect()
        second.connect()

        first.choose(DIRECT)

        assertNull(second.connection()["account"])
    }

    @Test
    fun `only owners list and choose, though members see the chosen account`() {
        val seller = Seller()
        seller.connect()
        seller.choose(DIRECT)
        val members = listOf("EDITOR", "VIEWER").map { role -> Seller().also { seller.addMember(it, role) } }

        members.forEach { member ->
            assertEquals(403, member.list(seller.accountId).status)
            assertEquals(403, member.choose(UNDER_MANAGER, seller.accountId).status)
        }
        assertEquals(403, Seller().list(seller.accountId).status)
        assertEquals(DIRECT, seller.connection()["account"]["customerId"].asString())
    }

    private companion object {
        const val MANAGER = "9998887776"
        const val UNDER_MANAGER = "1234567890"
        const val DIRECT = "2223334445"
        const val CLOSED = "3334445556"
    }
}
