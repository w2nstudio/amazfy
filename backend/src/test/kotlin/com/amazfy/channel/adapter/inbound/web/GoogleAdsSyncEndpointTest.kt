package com.amazfy.channel.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import com.amazfy.channel.application.DailyGoogleAdsSync
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
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
        var structureReads = 0

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
            structureReads++
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

    @Autowired
    lateinit var daily: DailyGoogleAdsSync

    @BeforeEach
    fun resetGoogle() {
        google.structures = mapOf(FIRST_ACCOUNT to structure(), SECOND_ACCOUNT to otherAccountStructure())
        google.tokenExpired = false
        google.readFails = false
        google.spend = emptyMap()
        google.spendFails = false
        google.spendReads.clear()
        google.structureReads = 0
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

        /** An Attribution report for amazon.com, one line per [lines] entry: creative id to sales. All for yesterday. */
        fun uploadAttribution(vararg lines: Pair<String, String>) =
            mockMvc
                .multipart("/api/v1/accounts/$accountId/attribution/imports") {
                    val rows = lines.map { (creative, sales) -> "${today.minusDays(1)},Google Ads,21,31,$creative,9,9,2,1,1,$sales" }
                    val csv = (listOf(ATTRIBUTION_HEADER) + rows).joinToString("\n")
                    file(MockMultipartFile("file", "attribution.csv", "text/csv", csv.toByteArray()))
                    param("marketplace", "US")
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                }.andExpect { status { isOk() } }

        /** Ad each stored Attribution line is matched to, by creative id (null: unmatched). */
        fun attributionMatches(): Map<String, UUID?> =
            jdbc
                .sql("SELECT creative_external_id, ad_id FROM attribution_daily WHERE account_id = :a")
                .param("a", accountId)
                .query { rs, _ -> rs.getString("creative_external_id") to rs.getObject("ad_id", UUID::class.java) }
                .list()
                .toMap()

        /** The dashboard's sales on the "Not matched to an ad" row, null when there's no such row. */
        fun unmatchedSales(): Double? =
            get("/api/v1/accounts/$accountId/reports/breakdown?by=CAMPAIGN")["rows"]
                .toList()
                .singleOrNull { it["kind"].asString() == "UNMATCHED" }
                ?.get("totals")
                ?.get("sales")
                ?.asDouble()

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

    /** The daily sync looks at every seller account, so job tests start with none connected. */
    private fun sellerAloneConnected(): Seller {
        jdbc.sql("DELETE FROM google_ads_connections").update()
        return Seller().also { it.connectAndChoose() }
    }

    private fun Seller.setConnection(
        column: String,
        hoursAgo: Long,
    ) {
        jdbc
            .sql("UPDATE google_ads_connections SET $column = now() - make_interval(hours => :h) WHERE account_id = :a")
            .param("h", hoursAgo.toInt())
            .param("a", accountId)
            .update()
    }

    @Test
    fun `the daily sync brings in a newly chosen account without anyone asking`() {
        val seller = sellerAloneConnected()
        google.spend = mapOf(FIRST_ACCOUNT to listOf(spendOf("41", today.minusDays(1), 2_000_000)))

        daily.runDue()

        assertEquals(1, seller.count("campaigns"))
        assertEquals(mapOf("USD" to 2.0), seller.reportedSpend())
        assertNotNull(seller.connection()["syncedAt"])
        assertNull(seller.connection()["syncProblem"])
    }

    @Test
    fun `an account synced recently isn't read again, and one synced a day ago is`() {
        val seller = sellerAloneConnected()
        seller.sync()
        google.spendReads.clear()

        daily.runDue()
        assertEquals(0, google.spendReads.size)

        seller.setConnection("synced_at", 22)
        daily.runDue()
        assertEquals(0, google.spendReads.size)

        seller.setConnection("synced_at", 24)
        daily.runDue()
        assertEquals(1, google.spendReads.size)
    }

    @Test
    fun `one run takes each account once, also when it fails`() {
        sellerAloneConnected()
        google.readFails = true

        daily.runDue()
        daily.runDue()

        assertEquals(1, google.structureReads)
    }

    @Test
    fun `a failed read shows on the connection, is tried again after an hour, and goes away when it works`() {
        val seller = sellerAloneConnected()
        google.readFails = true

        daily.runDue()

        assertEquals("READ_FAILED", seller.connection()["syncProblem"].asString())
        assertEquals(0, seller.count("campaigns"))

        google.readFails = false
        daily.runDue()
        assertEquals(0, seller.count("campaigns"), "not within the hour")

        seller.setConnection("sync_attempted_at", 2)
        daily.runDue()
        assertEquals(1, seller.count("campaigns"))
        assertNull(seller.connection()["syncProblem"])
    }

    @Test
    fun `an expired connection waits until the seller connects again`() {
        val seller = sellerAloneConnected()
        google.tokenExpired = true

        daily.runDue()
        assertEquals("CONNECTION_EXPIRED", seller.connection()["syncProblem"].asString())

        google.tokenExpired = false
        seller.setConnection("sync_attempted_at", 48)
        daily.runDue()
        assertEquals(0, seller.count("campaigns"), "calling Google with a dead token only wastes its daily limit")

        seller.connect()
        assertNull(seller.connection()["syncProblem"])
        daily.runDue()
        assertEquals(1, seller.count("campaigns"))
        assertEquals(FIRST_ACCOUNT, seller.connection()["account"]["customerId"].asString())
    }

    @Test
    fun `an unexpected failure at one seller doesn't stop the others`() {
        val failing = sellerAloneConnected()
        val fine = Seller().also { it.connectAndChoose(SECOND_ACCOUNT) }
        google.structures = mapOf(SECOND_ACCOUNT to otherAccountStructure()) // the first account's read now throws

        daily.runDue()

        assertEquals("READ_FAILED", failing.connection()["syncProblem"].asString())
        assertEquals(1, fine.count("campaigns"))
        assertNull(fine.connection()["syncProblem"])
    }

    @Test
    fun `each seller's data goes to that seller`() {
        val first = sellerAloneConnected()
        val second = Seller().also { it.connectAndChoose(SECOND_ACCOUNT) }

        daily.runDue()

        assertEquals(
            "21",
            jdbc
                .sql(
                    "SELECT external_id FROM campaigns WHERE account_id = :a",
                ).param("a", first.accountId)
                .query(String::class.java)
                .single(),
        )
        assertEquals(
            "22",
            jdbc
                .sql(
                    "SELECT external_id FROM campaigns WHERE account_id = :a",
                ).param("a", second.accountId)
                .query(String::class.java)
                .single(),
        )
    }

    @Test
    fun `accounts without a chosen Google Ads account are skipped`() {
        jdbc.sql("DELETE FROM google_ads_connections").update()
        val notChosen = Seller().also { it.connect() }

        daily.runDue()

        assertEquals(0, notChosen.count("campaigns"))
        assertNull(notChosen.connection()["syncProblem"])
    }

    @Test
    fun `the button shows a failure too, and clears it when it works`() {
        val seller = Seller()
        seller.connectAndChoose()
        google.readFails = true
        seller.sync()
        assertEquals("READ_FAILED", seller.connection()["syncProblem"].asString())

        google.readFails = false
        seller.sync()
        assertNull(seller.connection()["syncProblem"])
    }

    @Test
    fun `choosing another account clears the problem`() {
        val seller = Seller()
        seller.connectAndChoose()
        google.readFails = true
        seller.sync()

        seller.choose(SECOND_ACCOUNT)

        assertNull(seller.connection()["syncProblem"])
    }

    @Test
    fun `syncs of one seller account at the same time wait for each other`() {
        val seller = Seller()
        seller.connectAndChoose()
        google.spend = mapOf(FIRST_ACCOUNT to (1L..60L).map { spendOf("41", today.minusDays(it), 1_000_000) })
        seller.sync()
        // An account without campaigns: saving campaigns isn't what makes them wait, and the earlier account's report totals
        // are still rebuilt, which two syncs at the same moment couldn't both do
        google.structures = mapOf(SECOND_ACCOUNT to GoogleAdsStructure(emptyList(), emptyList(), emptyList()))
        seller.choose(SECOND_ACCOUNT)
        val pool = Executors.newFixedThreadPool(8)

        val statuses =
            try {
                (1..40).map { pool.submit<Int> { seller.sync().status } }.map { it.get() }
            } finally {
                pool.shutdown()
            }

        assertEquals(List(40) { 200 }, statuses)
        assertEquals(60, seller.count("ad_spend_daily"))
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

    @Test
    fun `Attribution lines uploaded before their ads match once the sync brings the ads in`() {
        val seller = Seller()
        seller.connectAndChoose()
        seller.uploadAttribution("41" to "25.00", "999" to "10.00")
        assertEquals(mapOf("41" to null, "999" to null), seller.attributionMatches())
        assertEquals(35.0, seller.unmatchedSales())

        val result = read(seller.sync())

        assertEquals(1, result["attributionLinesMatched"].asInt())
        assertEquals(mapOf("41" to seller.adId("41"), "999" to null), seller.attributionMatches())
        assertEquals(10.0, seller.unmatchedSales()) // the reports moved the sales to the ad straight away
        assertEquals(0, read(seller.sync())["attributionLinesMatched"].asInt())
    }

    @Test
    fun `the daily sync matches them too`() {
        val seller = sellerAloneConnected()
        seller.uploadAttribution("41" to "25.00")

        daily.runDue()

        assertEquals(mapOf("41" to seller.adId("41")), seller.attributionMatches())
    }

    @Test
    fun `another seller account's lines with the same ids stay unmatched`() {
        val synced = Seller()
        val other = Seller()
        synced.connectAndChoose()
        other.uploadAttribution("41" to "25.00")

        synced.sync()

        assertEquals(mapOf("41" to null), other.attributionMatches())
    }

    private companion object {
        const val FIRST_ACCOUNT = "1234567890"
        const val SECOND_ACCOUNT = "2223334445"
        const val PRODUCT_URL = "https://www.amazon.com/dp/B0AAA11111"
        const val ATTRIBUTION_HEADER =
            "Date,Publisher,Campaign ID,Ad group ID,Creative ID,Click-throughs,Detail page views,Add to carts,Purchases,Units sold,Sales"
    }
}
