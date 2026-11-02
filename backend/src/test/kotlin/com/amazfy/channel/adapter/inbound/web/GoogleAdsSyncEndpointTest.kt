package com.amazfy.channel.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.application.port.GoogleAdsReader
import com.amazfy.channel.domain.GoogleAd
import com.amazfy.channel.domain.GoogleAdGroup
import com.amazfy.channel.domain.GoogleAdSpend
import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.GoogleAdsClient
import com.amazfy.channel.domain.GoogleAdsConnectionExpiredException
import com.amazfy.channel.domain.GoogleAdsCustomer
import com.amazfy.channel.domain.GoogleAdsReadFailedException
import com.amazfy.channel.domain.GoogleAdsStructure
import com.amazfy.channel.domain.GoogleCampaign
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Bringing in campaigns, ad groups and ads through HTTP, with Google replaced by a fake. (Real calls: GoogleAdsRestReaderTest.) */
@Import(TestcontainersConfiguration::class, GoogleAdsSyncEndpointTest.FakeGoogleConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
class GoogleAdsSyncEndpointTest {
    /** Google stand-in holding one structure per Google Ads account. */
    class FakeGoogleReader : GoogleAdsReader {
        var structures = emptyMap<String, GoogleAdsStructure>()
        var tokenExpired = false
        var readFails = false
        var spend = emptyMap<String, List<GoogleAdSpend>>()
        var spendFails = false
        val spendReads = mutableListOf<ClosedRange<LocalDate>>()

        override fun accessToken(refreshToken: String): String {
            if (tokenExpired) throw GoogleAdsConnectionExpiredException()
            return "short-lived"
        }

        override fun accessibleCustomerIds(accessToken: String) = structures.keys.toList()

        override fun hierarchyOf(
            customerId: String,
            accessToken: String,
        ) = listOf(GoogleAdsClient(customerId, null, isManager = false, "ENABLED", isTestAccount = false, level = 0))

        override fun structureOf(
            account: GoogleAdsAccount,
            accessToken: String,
        ): GoogleAdsStructure {
            if (readFails) throw GoogleAdsReadFailedException("refused")
            return structures.getValue(account.customerId)
        }

        override fun customerOf(
            account: GoogleAdsAccount,
            accessToken: String,
        ) = GoogleAdsCustomer(if (account.customerId == SECOND_ACCOUNT) "EUR" else "USD", ZoneOffset.UTC)

        override fun spendOf(
            account: GoogleAdsAccount,
            days: ClosedRange<LocalDate>,
            accessToken: String,
        ): List<GoogleAdSpend> {
            if (spendFails) throw GoogleAdsReadFailedException("refused")
            spendReads += days
            return spend[account.customerId].orEmpty().filter { it.date in days }
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
        google.structures = mapOf(FIRST_ACCOUNT to structure(), SECOND_ACCOUNT to otherAccountStructure())
        google.tokenExpired = false
        google.readFails = false
        google.spend = emptyMap()
        google.spendFails = false
        google.spendReads.clear()
    }

    private val today = LocalDate.now(ZoneOffset.UTC)

    private fun spendOf(
        adId: String,
        date: LocalDate,
        costMicros: Long,
    ) = GoogleAdSpend("21", "31", adId, date, impressions = 100, clicks = 10, costMicros = costMicros)

    private fun structure(
        campaignName: String = "Filters",
        campaignStatus: String = "ENABLED",
        productUrl: String = PRODUCT_URL,
    ) = GoogleAdsStructure(
        listOf(GoogleCampaign("21", campaignName, campaignStatus, 15_000_000, "USD")),
        listOf(GoogleAdGroup("31", "21", "Filter 40L", "ENABLED")),
        listOf(
            GoogleAd("41", "31", "21", productUrl, "ENABLED"),
            GoogleAd("42", "31", "21", "https://www.amazon.com/stores/AquaLife/page/1", "ENABLED"),
            GoogleAd("43", "31", "21", "", "ENABLED"),
        ),
    )

    private fun otherAccountStructure() =
        GoogleAdsStructure(
            listOf(GoogleCampaign("22", "Heaters", "ENABLED", null, "EUR")),
            listOf(GoogleAdGroup("32", "22", "Heater", "ENABLED")),
            listOf(GoogleAd("44", "32", "22", "https://www.amazon.com/dp/B0BBB22222", "ENABLED")),
        )

    private fun read(response: MockHttpServletResponse): JsonNode = json.readTree(response.contentAsString)

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
                            """{"email":"sync-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc.","region":"NA"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            val me = get("/api/v1/me")
            accountId = UUID.fromString(me["accounts"][0]["account"]["id"].asString())
            userId = UUID.fromString(me["id"].asString())
        }

        fun get(path: String): JsonNode =
            read(mockMvc.get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andReturn().response)

        fun connect() = CurrentAccount.runAs(accountId) { connections.save("1//refresh-token", Instant.parse("2026-11-01T10:00:00Z")) }

        fun choose(customerId: String) =
            mockMvc.put("/api/v1/accounts/$accountId/google-ads/connection/account") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"customerId":"$customerId"}"""
            }

        fun connectAndChoose(customerId: String = FIRST_ACCOUNT) {
            connect()
            choose(customerId)
        }

        fun sync(account: UUID = accountId) =
            mockMvc
                .post("/api/v1/accounts/$account/google-ads/sync") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response

        fun connection() = get("/api/v1/accounts/$accountId/google-ads/connection")

        fun unlinked() = get("/api/v1/accounts/$accountId/ads/unlinked").toList()

        /** The product the first ad points to, under a new brand. */
        fun addProduct(asin: String = "B0AAA11111"): String {
            val brand =
                read(
                    mockMvc
                        .post("/api/v1/accounts/$accountId/brands") {
                            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"name":"AquaLife"}"""
                        }.andReturn()
                        .response,
                )["id"].asString()
            return read(
                mockMvc
                    .post("/api/v1/accounts/$accountId/products") {
                        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"asin":"$asin","marketplace":"US","name":"Filter 40L","brandId":"$brand"}"""
                    }.andReturn()
                    .response,
            )["id"].asString()
        }

        fun linkToBrandOf(adExternalId: String) {
            val brand = get("/api/v1/accounts/$accountId/brands")[0]["id"].asString()
            mockMvc.put("/api/v1/accounts/$accountId/ads/${adId(adExternalId)}/link") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"brandId":"$brand"}"""
            }
        }

        fun adId(externalId: String): UUID =
            jdbc
                .sql("SELECT id FROM ads WHERE account_id = :a AND external_id = :e")
                .param("a", accountId)
                .param("e", externalId)
                .query(UUID::class.java)
                .single()

        fun count(table: String): Int =
            jdbc
                .sql("SELECT count(*) FROM $table WHERE account_id = :a")
                .param("a", accountId)
                .query(Int::class.java)
                .single()

        /** Stored spend of one day, in micros. */
        fun storedSpend(date: LocalDate): Long =
            jdbc
                .sql("SELECT coalesce(sum(cost_micros), 0) FROM ad_spend_daily WHERE account_id = :a AND date = :d")
                .param("a", accountId)
                .param("d", date)
                .query(Long::class.java)
                .single()

        /** The dashboard's spend per currency: last 30 days up to yesterday. */
        fun reportedSpend(): Map<String, Double> =
            get("/api/v1/accounts/$accountId/reports/summary")["currencies"].toList().associate {
                it["currency"].asString() to it["spend"].asDouble()
            }

        fun campaign(externalId: String): Map<String, Any?> =
            jdbc
                .sql("SELECT * FROM campaigns WHERE account_id = :a AND external_id = :e")
                .param("a", accountId)
                .param("e", externalId)
                .query()
                .singleRow()

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

    @Test
    fun `brings in the structure with name, status, budget and final URL`() {
        val seller = Seller()
        seller.connectAndChoose()
        assertNull(seller.connection()["syncedAt"])

        val response = seller.sync()

        assertEquals(200, response.status)
        val result = read(response)
        assertEquals(1, result["campaigns"].asInt())
        assertEquals(1, result["adGroups"].asInt())
        assertEquals(3, result["ads"].asInt())
        val campaign = seller.campaign("21")
        assertEquals("Filters", campaign["name"])
        assertEquals("ENABLED", campaign["status"])
        assertEquals("GOOGLE", campaign["channel"])
        assertEquals(FIRST_ACCOUNT, campaign["channel_account_id"])
        assertEquals(15_000_000L, campaign["daily_budget_micros"])
        assertEquals("USD", campaign["budget_currency"])
        assertNotNull(seller.connection()["syncedAt"])
    }

    @Test
    fun `new ads link automatically where they can, and the rest wait to be linked`() {
        val seller = Seller()
        seller.addProduct()
        seller.connectAndChoose()

        val result = read(seller.sync())

        assertEquals(1, result["linked"].asInt())
        assertEquals(2, result["stillUnlinked"].asInt())
        val unlinked = seller.unlinked()
        assertEquals(setOf("42", "43"), unlinked.map { it["externalId"].asString() }.toSet())
        assertEquals("OTHER", unlinked.first { it["externalId"].asString() == "43" }["linkKind"].asString())
    }

    @Test
    fun `syncing again updates rows, without duplicates, and keeps the seller's links`() {
        val seller = Seller()
        seller.addProduct()
        seller.connectAndChoose()
        seller.sync()
        seller.linkToBrandOf("42")
        google.structures = mapOf(FIRST_ACCOUNT to structure("Filters renamed", "REMOVED", "https://www.amazon.com/dp/B0CCC33333"))

        val result = read(seller.sync())

        assertEquals(0, result["linked"].asInt())
        assertEquals(1, seller.count("campaigns"))
        assertEquals(1, seller.count("ad_groups"))
        assertEquals(3, seller.count("ads"))
        assertEquals("Filters renamed", seller.campaign("21")["name"])
        assertEquals("REMOVED", seller.campaign("21")["status"])
        assertEquals(setOf("43"), seller.unlinked().map { it["externalId"].asString() }.toSet())
        val finalUrl =
            jdbc
                .sql("SELECT final_url FROM ads WHERE id = :id")
                .param("id", seller.adId("41"))
                .query(String::class.java)
                .single()
        assertEquals("https://www.amazon.com/dp/B0CCC33333", finalUrl)
    }

    @Test
    fun `changing account keeps earlier campaigns untouched, and the sync time starts over`() {
        val seller = Seller()
        seller.connectAndChoose()
        seller.sync()
        val keptAd = seller.adId("41")

        seller.choose(SECOND_ACCOUNT)
        assertNull(seller.connection()["syncedAt"])
        google.structures = mapOf(FIRST_ACCOUNT to structure("Should not be read"), SECOND_ACCOUNT to otherAccountStructure())
        val result = read(seller.sync())

        assertEquals(1, result["campaigns"].asInt())
        assertEquals(2, seller.count("campaigns"))
        assertEquals(keptAd, seller.adId("41"))
        assertEquals("Filters", seller.campaign("21")["name"])
        assertEquals(FIRST_ACCOUNT, seller.campaign("21")["channel_account_id"])
        assertEquals(SECOND_ACCOUNT, seller.campaign("22")["channel_account_id"])
        assertNull(seller.campaign("22")["daily_budget_micros"])
    }

    @Test
    fun `choosing the same account again keeps the sync time`() {
        val seller = Seller()
        seller.connectAndChoose()
        seller.sync()

        seller.choose(FIRST_ACCOUNT)

        assertNotNull(seller.connection()["syncedAt"])
    }

    @Test
    fun `an account without campaigns is fine`() {
        val seller = Seller()
        google.structures = mapOf(FIRST_ACCOUNT to GoogleAdsStructure(emptyList(), emptyList(), emptyList()))
        seller.connectAndChoose()

        assertEquals(200, seller.sync().status)
        assertEquals(0, seller.count("campaigns"))
        assertNotNull(seller.connection()["syncedAt"])
    }

    @Test
    fun `needs a connection and a chosen account`() {
        val seller = Seller()
        assertEquals(409, seller.sync().status)

        seller.connect()
        val response = seller.sync()

        assertEquals(409, response.status)
        assertEquals("Choose a Google Ads account first.", read(response)["detail"].asString())
    }

    @Test
    fun `asks to connect again when Google refuses the token`() {
        val seller = Seller()
        seller.connectAndChoose()
        google.tokenExpired = true

        assertEquals(409, seller.sync().status)
    }

    @Test
    fun `a failed read changes nothing`() {
        val seller = Seller()
        seller.connectAndChoose()
        google.readFails = true

        assertEquals(502, seller.sync().status)

        assertEquals(0, seller.count("campaigns"))
        assertNull(seller.connection()["syncedAt"])
    }

    @Test
    fun `two seller accounts can read the same Google Ads account, each with its own rows and links`() {
        val first = Seller()
        val second = Seller()
        first.addProduct()
        first.connectAndChoose()
        second.connectAndChoose()

        first.sync()
        second.sync()

        assertEquals(3, first.count("ads"))
        assertEquals(3, second.count("ads"))
        assertEquals(2, first.unlinked().size)
        assertEquals(3, second.unlinked().size)
    }

    @Test
    fun `owners and editors can sync, viewers and strangers can't`() {
        val seller = Seller()
        seller.connectAndChoose()
        val editor = Seller().also { seller.addMember(it, "EDITOR") }
        val viewer = Seller().also { seller.addMember(it, "VIEWER") }

        assertEquals(200, editor.sync(seller.accountId).status)
        assertEquals(403, viewer.sync(seller.accountId).status)
        assertEquals(403, Seller().sync(seller.accountId).status)
    }

    @Test
    fun `spend comes in, and the reports show it at once`() {
        val seller = Seller()
        seller.connectAndChoose()
        val yesterday = today.minusDays(1)
        google.spend =
            mapOf(
                FIRST_ACCOUNT to
                    listOf(
                        spendOf("41", yesterday, 12_500_000),
                        spendOf("42", yesterday, 2_500_000),
                        spendOf("41", yesterday.minusDays(1), 5_000_000),
                    ),
            )

        val result = read(seller.sync())

        assertEquals(20.0, result["spend"].asDouble())
        assertEquals("USD", result["currency"].asString())
        assertEquals(today.minusDays(89).toString(), result["spendFrom"].asString())
        assertEquals(today.toString(), result["spendTo"].asString())
        assertEquals(mapOf("USD" to 20.0), seller.reportedSpend())
        val notLinked =
            seller
                .get("/api/v1/accounts/${seller.accountId}/reports/breakdown?by=BRAND")["rows"]
                .toList()
                .single { it["name"].asString() == "Not linked to a product or brand" }
        assertEquals(20.0, notLinked["totals"]["spend"].asDouble())
    }

    @Test
    fun `an account without spend brings in none`() {
        val seller = Seller()
        seller.connectAndChoose()

        val result = read(seller.sync())

        assertEquals(0.0, result["spend"].asDouble())
        assertEquals("USD", result["currency"].asString())
        assertEquals(0, seller.count("ad_spend_daily"))
    }

    @Test
    fun `the first sync reads 90 days, and later ones two weeks`() {
        val seller = Seller()
        seller.connectAndChoose()

        seller.sync()
        seller.sync()

        assertEquals(listOf(today.minusDays(89)..today, today.minusDays(13)..today), google.spendReads)
    }

    @Test
    fun `syncing again replaces recent days, removes what Google corrected away, and keeps older days`() {
        val seller = Seller()
        seller.connectAndChoose()
        val changed = today.minusDays(1)
        val correctedAway = today.minusDays(2)
        val older = today.minusDays(40)
        google.spend =
            mapOf(
                FIRST_ACCOUNT to
                    listOf(spendOf("41", changed, 10_000_000), spendOf("41", correctedAway, 7_000_000), spendOf("41", older, 3_000_000)),
            )
        seller.sync()
        assertEquals(mapOf("USD" to 17.0), seller.reportedSpend())

        google.spend = mapOf(FIRST_ACCOUNT to listOf(spendOf("41", changed, 9_000_000)))
        val result = read(seller.sync())

        assertEquals(9.0, result["spend"].asDouble())
        assertEquals(9_000_000, seller.storedSpend(changed))
        assertEquals(0, seller.storedSpend(correctedAway))
        assertEquals(3_000_000, seller.storedSpend(older))
        assertEquals(mapOf("USD" to 9.0), seller.reportedSpend())
    }

    @Test
    fun `spend of an account chosen earlier stays, in its own currency`() {
        val seller = Seller()
        seller.connectAndChoose()
        val yesterday = today.minusDays(1)
        google.spend =
            mapOf(
                FIRST_ACCOUNT to listOf(spendOf("41", yesterday, 10_000_000)),
                SECOND_ACCOUNT to listOf(GoogleAdSpend("22", "32", "44", yesterday, 50, 5, 4_000_000)),
            )
        seller.sync()

        seller.choose(SECOND_ACCOUNT)
        seller.sync()

        assertEquals(mapOf("USD" to 10.0, "EUR" to 4.0), seller.reportedSpend())
    }

    @Test
    fun `sample data's spend stays`() {
        val seller = Seller()
        mockMvc.post("/api/v1/accounts/${seller.accountId}/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer ${seller.token}") }
        val sampleRows = seller.count("ad_spend_daily")
        seller.connectAndChoose()
        google.spend = mapOf(FIRST_ACCOUNT to listOf(spendOf("41", today.minusDays(1), 1_000_000)))

        seller.sync()

        assertEquals(sampleRows + 1, seller.count("ad_spend_daily"))
    }

    @Test
    fun `spend of an ad that isn't stored is left out`() {
        val seller = Seller()
        seller.connectAndChoose()
        google.spend = mapOf(FIRST_ACCOUNT to listOf(spendOf("41", today, 1_000_000), spendOf("not-stored", today, 9_000_000)))

        val result = read(seller.sync())

        assertEquals(1.0, result["spend"].asDouble())
        assertEquals(1, seller.count("ad_spend_daily"))
    }

    @Test
    fun `a failed spend read changes nothing, not even campaigns`() {
        val seller = Seller()
        seller.connectAndChoose()
        google.spendFails = true

        assertEquals(502, seller.sync().status)

        assertEquals(0, seller.count("campaigns"))
        assertNull(seller.connection()["syncedAt"])
    }

    @Test
    fun `two seller accounts reading the same Google Ads account each have their own spend`() {
        val first = Seller()
        val second = Seller()
        first.connectAndChoose()
        second.connectAndChoose()
        google.spend = mapOf(FIRST_ACCOUNT to listOf(spendOf("41", today.minusDays(1), 1_000_000)))
        first.sync()
        second.sync()

        google.spend = emptyMap()
        first.sync()

        assertEquals(0, first.count("ad_spend_daily"))
        assertEquals(1, second.count("ad_spend_daily"))
    }

    @Test
    fun `a large account's spend comes in whole, more than once`() {
        val seller = Seller()
        val adGroups = (1..500).map { GoogleAdGroup("g$it", "c", "Group $it", "ENABLED") }
        val ads = adGroups.flatMap { g -> (1..10).map { GoogleAd("a$it", g.id, "c", "https://amzn.to/$it", "ENABLED") } }
        google.structures =
            mapOf(FIRST_ACCOUNT to GoogleAdsStructure(listOf(GoogleCampaign("c", "Large", "ENABLED", 1_000_000, "USD")), adGroups, ads))
        google.spend =
            mapOf(
                FIRST_ACCOUNT to
                    (1L..14L).flatMap { daysAgo ->
                        ads.map { GoogleAdSpend("c", it.adGroupId, it.id, today.minusDays(daysAgo), 100, 10, 1_000_000) }
                    },
            )
        seller.connectAndChoose()

        repeat(2) { assertEquals(200, seller.sync().status) }

        assertEquals(5000 * 14, seller.count("ad_spend_daily"))
        assertEquals(mapOf("USD" to 70_000.0), seller.reportedSpend())
    }

    @Test
    fun `a large account comes in whole`() {
        val seller = Seller()
        val campaigns = (1..50).map { GoogleCampaign("c$it", "Campaign $it", "ENABLED", 1_000_000, "USD") }
        val adGroups = campaigns.flatMap { c -> (1..10).map { GoogleAdGroup("${c.id}g$it", c.id, "Group $it", "ENABLED") } }
        val ads =
            adGroups.flatMap { g ->
                (1..10).map { GoogleAd("${g.id}a$it", g.id, g.campaignId, "https://www.amazon.com/dp/B0AAA11111", "ENABLED") }
            }
        google.structures = mapOf(FIRST_ACCOUNT to GoogleAdsStructure(campaigns, adGroups, ads))
        seller.connectAndChoose()

        repeat(2) { assertEquals(200, seller.sync().status) }

        assertEquals(50, seller.count("campaigns"))
        assertEquals(500, seller.count("ad_groups"))
        assertEquals(5000, seller.count("ads"))
    }

    private companion object {
        const val FIRST_ACCOUNT = "1234567890"
        const val SECOND_ACCOUNT = "2223334445"
        const val PRODUCT_URL = "https://www.amazon.com/dp/B0AAA11111"
    }
}
