package com.amazfy.channel.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Seeing campaigns, ad groups and ads over HTTP. Rows are written directly: how a sync writes them is in GoogleAdsSyncEndpointTest. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class CampaignsEndpointTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var json: JsonMapper

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
                            """{"email":"campaigns-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc.","region":"NA"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            val me = get("/api/v1/me")
            accountId = UUID.fromString(me["accounts"][0]["account"]["id"].asString())
            userId = UUID.fromString(me["id"].asString())
        }

        fun status(path: String) =
            mockMvc
                .get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.status

        fun get(path: String): JsonNode =
            json.readTree(
                mockMvc
                    .get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                    .andReturn()
                    .response.contentAsString,
            )

        fun campaigns() = get("/api/v1/accounts/$accountId/campaigns").toList()

        fun adGroups(campaignId: UUID) = get("/api/v1/accounts/$accountId/campaigns/$campaignId/ad-groups").toList()

        fun addCampaign(
            name: String,
            status: String = "ENABLED",
            budgetMicros: Long? = null,
            currency: String? = null,
        ): UUID =
            jdbc
                .sql(
                    """
                    INSERT INTO campaigns (account_id, channel, external_id, channel_account_id, name, status, daily_budget_micros, budget_currency)
                    VALUES (:a, 'GOOGLE', :e, '1234567890', :n, :s, :b, :c) RETURNING id
                    """,
                ).param("a", accountId)
                .param("e", UUID.randomUUID().toString())
                .param("n", name)
                .param("s", status)
                .param("b", budgetMicros, java.sql.Types.BIGINT)
                .param("c", currency, java.sql.Types.VARCHAR)
                .query(UUID::class.java)
                .single()

        fun addAdGroup(
            campaignId: UUID,
            name: String,
            status: String = "ENABLED",
        ): UUID =
            jdbc
                .sql(
                    """
                    INSERT INTO ad_groups (account_id, campaign_id, external_id, name, status)
                    VALUES (:a, :c, :e, :n, :s) RETURNING id
                    """,
                ).param("a", accountId)
                .param("c", campaignId)
                .param("e", UUID.randomUUID().toString())
                .param("n", name)
                .param("s", status)
                .query(UUID::class.java)
                .single()

        fun addAd(
            adGroupId: UUID,
            finalUrl: String,
            status: String = "ENABLED",
            productId: UUID? = null,
            brandId: UUID? = null,
        ) {
            jdbc
                .sql(
                    """
                    INSERT INTO ads (account_id, ad_group_id, external_id, final_url, status, product_id, brand_id)
                    VALUES (:a, :g, :e, :u, :s, :p, :b)
                    """,
                ).param("a", accountId)
                .param("g", adGroupId)
                .param("e", UUID.randomUUID().toString())
                .param("u", finalUrl)
                .param("s", status)
                .param("p", productId, java.sql.Types.OTHER)
                .param("b", brandId, java.sql.Types.OTHER)
                .update()
        }

        fun addBrand(name: String): UUID =
            jdbc
                .sql("INSERT INTO brands (account_id, name) VALUES (:a, :n) RETURNING id")
                .param("a", accountId)
                .param("n", name)
                .query(UUID::class.java)
                .single()

        fun addProduct(
            name: String,
            brandId: UUID,
        ): UUID =
            jdbc
                .sql(
                    """
                    INSERT INTO products (account_id, brand_id, asin, marketplace, name)
                    VALUES (:a, :b, 'B0AAA11111', 'US', :n) RETURNING id
                    """,
                ).param("a", accountId)
                .param("b", brandId)
                .param("n", name)
                .query(UUID::class.java)
                .single()

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
    fun `lists campaigns with status, budget and counts`() {
        val seller = Seller()
        val filters = seller.addCampaign("Filters", budgetMicros = 15_500_000, currency = "USD")
        val group = seller.addAdGroup(filters, "Filter 40L")
        seller.addAdGroup(filters, "Filter 60L")
        seller.addAd(group, "https://www.amazon.com/dp/B0AAA11111")
        seller.addAd(group, "https://amzn.to/123456")

        val campaign = seller.campaigns().single()

        assertEquals(filters.toString(), campaign["id"].asString())
        assertEquals("Filters", campaign["name"].asString())
        assertEquals("ENABLED", campaign["status"].asString())
        assertEquals(15.5, campaign["dailyBudget"].asDouble())
        assertEquals("USD", campaign["budgetCurrency"].asString())
        assertEquals("1234567890", campaign["channelAccountId"].asString())
        assertEquals(2, campaign["adGroupCount"].asInt())
        assertEquals(2, campaign["adCount"].asInt())
    }

    @Test
    fun `a campaign without a daily budget or ad groups is shown too`() {
        val seller = Seller()
        seller.addCampaign("Period budget")

        val campaign = seller.campaigns().single()

        assertNull(campaign["dailyBudget"])
        assertEquals(0, campaign["adGroupCount"].asInt())
        assertEquals(0, campaign["adCount"].asInt())
    }

    @Test
    fun `campaigns are sorted by name, removed ones last`() {
        val seller = Seller()
        seller.addCampaign("Pumps", "PAUSED")
        seller.addCampaign("Heaters", "REMOVED")
        seller.addCampaign("Filters")

        assertEquals(listOf("Filters", "Pumps", "Heaters"), seller.campaigns().map { it["name"].asString() })
    }

    @Test
    fun `ad groups come with their ads and what each ad is linked to`() {
        val seller = Seller()
        val brand = seller.addBrand("AquaLife")
        val product = seller.addProduct("Filter 40L", brand)
        val campaign = seller.addCampaign("Filters")
        val second = seller.addAdGroup(campaign, "Second")
        val first = seller.addAdGroup(campaign, "First")
        seller.addAd(first, "https://www.amazon.com/dp/B0AAA11111", productId = product)
        seller.addAd(first, "https://www.amazon.com/stores/AquaLife/page/1", brandId = brand)
        seller.addAd(second, "https://amzn.to/123456", "PAUSED")
        seller.addAd(second, "", "ENABLED")

        val groups = seller.adGroups(campaign)

        assertEquals(listOf("First", "Second"), groups.map { it["name"].asString() })
        val linked = groups[0]["ads"].toList()
        assertEquals("https://www.amazon.com/dp/B0AAA11111", linked[0]["finalUrl"].asString())
        assertEquals("Filter 40L", linked[0]["productName"].asString())
        assertNull(linked[0]["brandName"])
        assertEquals("AquaLife", linked[1]["brandName"].asString())
        assertNull(linked[1]["productName"])
        val notLinked = groups[1]["ads"].toList()
        assertEquals(listOf("", "https://amzn.to/123456"), notLinked.map { it["finalUrl"].asString() })
        assertEquals(listOf("ENABLED", "PAUSED"), notLinked.map { it["status"].asString() })
        assertNull(notLinked[0]["productName"])
        assertNull(notLinked[0]["brandName"])
    }

    @Test
    fun `removed ad groups and ads come last`() {
        val seller = Seller()
        val campaign = seller.addCampaign("Filters")
        val removed = seller.addAdGroup(campaign, "A removed", "REMOVED")
        val active = seller.addAdGroup(campaign, "B active")
        seller.addAd(removed, "https://amzn.to/1")
        seller.addAd(active, "https://amzn.to/1", "REMOVED")
        seller.addAd(active, "https://amzn.to/2")

        val groups = seller.adGroups(campaign)

        assertEquals(listOf("B active", "A removed"), groups.map { it["name"].asString() })
        assertEquals(listOf("https://amzn.to/2", "https://amzn.to/1"), groups[0]["ads"].toList().map { it["finalUrl"].asString() })
    }

    @Test
    fun `an ad group without ads is empty, and a campaign without ad groups too`() {
        val seller = Seller()
        val empty = seller.addCampaign("Empty")
        val campaign = seller.addCampaign("Filters")
        seller.addAdGroup(campaign, "No ads")

        assertTrue(seller.adGroups(empty).isEmpty())
        assertEquals(0, seller.adGroups(campaign).single()["ads"].size())
    }

    @Test
    fun `an unknown campaign and another seller's campaign are not found`() {
        val seller = Seller()
        val other = Seller()
        val theirs = other.addCampaign("Theirs")

        assertEquals(404, seller.status("/api/v1/accounts/${seller.accountId}/campaigns/${UUID.randomUUID()}/ad-groups"))
        assertEquals(404, seller.status("/api/v1/accounts/${seller.accountId}/campaigns/$theirs/ad-groups"))
        assertTrue(seller.campaigns().isEmpty())
    }

    @Test
    fun `any member can read, strangers can't`() {
        val seller = Seller()
        val viewer = Seller()
        val stranger = Seller()
        seller.addMember(viewer, "VIEWER")
        val campaign = seller.addCampaign("Filters")
        val list = "/api/v1/accounts/${seller.accountId}/campaigns"

        assertEquals(200, viewer.status(list))
        assertEquals(200, viewer.status("$list/$campaign/ad-groups"))
        assertEquals(403, stranger.status(list))
        assertEquals(403, stranger.status("$list/$campaign/ad-groups"))
    }

    @Test
    fun `a large seller's campaigns and a very large campaign can be read`() {
        val seller = Seller()
        jdbc
            .sql(
                """
                WITH new_campaigns AS (
                    INSERT INTO campaigns (account_id, channel, external_id, name, status)
                    SELECT :a, 'GOOGLE', 'c' || n, 'Campaign ' || n, 'ENABLED' FROM generate_series(1, 500) n
                    RETURNING id, account_id
                ), new_groups AS (
                    INSERT INTO ad_groups (account_id, campaign_id, external_id, name, status)
                    SELECT account_id, id, 'g' || id, 'Group', 'ENABLED' FROM new_campaigns
                    RETURNING id, account_id
                )
                INSERT INTO ads (account_id, ad_group_id, external_id, final_url, status)
                SELECT account_id, id, 'a' || n, 'https://amzn.to/' || n, 'ENABLED' FROM new_groups, generate_series(1, 10) n
                """,
            ).param("a", seller.accountId)
            .update()
        val huge = seller.addCampaign("Huge")
        val group = seller.addAdGroup(huge, "All of them")
        jdbc
            .sql(
                """
                INSERT INTO ads (account_id, ad_group_id, external_id, final_url, status)
                SELECT :a, :g, 'h' || n, 'https://amzn.to/' || n, 'ENABLED' FROM generate_series(1, 5000) n
                """,
            ).param("a", seller.accountId)
            .param("g", group)
            .update()

        val campaigns = seller.campaigns()
        val groups = seller.adGroups(huge)

        assertEquals(501, campaigns.size)
        assertEquals(10_000, campaigns.sumOf { it["adCount"].asInt() })
        assertEquals(5000, groups.single()["ads"].size())
    }
}
