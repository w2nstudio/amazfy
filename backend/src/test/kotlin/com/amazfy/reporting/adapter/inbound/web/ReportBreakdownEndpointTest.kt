package com.amazfy.reporting.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GET /reports/breakdown over HTTP, on sample data: every level adds up to the summary, and drilling down works. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class ReportBreakdownEndpointTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var json: JsonMapper

    /** A signed-up seller whose account is filled with sample data. */
    private inner class Seller {
        val token: String
        val accountId: String

        init {
            val body =
                mockMvc
                    .post("/api/v1/auth/register") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"email":"breakdown-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc.","region":"NA"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            val me = get("/api/v1/me")
            accountId = me["accounts"][0]["account"]["id"].asString()
            mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        }

        fun get(path: String): JsonNode =
            json.readTree(
                mockMvc
                    .get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                    .andReturn()
                    .response.contentAsString,
            )

        fun breakdown(query: String): List<JsonNode> = get("/api/v1/accounts/$accountId/reports/breakdown?$query")["rows"].toList()
    }

    private fun List<JsonNode>.spend(): BigDecimal = sumOf { it["totals"]["spend"].decimalValue() }

    private fun List<JsonNode>.kinds(): List<String> = map { it["kind"].asString() }

    @Test
    fun `every level adds up to the summary`() {
        val seller = Seller()
        val summary = seller.get("/api/v1/accounts/${seller.accountId}/reports/summary")["currencies"][0]
        listOf("BRAND", "PRODUCT", "CAMPAIGN", "AD_GROUP", "AD").forEach { level ->
            val rows = seller.breakdown("by=$level")
            // Sales are stored in cents and add up exactly
            assertEquals(
                0,
                summary["sales"].decimalValue().compareTo(rows.sumOf { it["totals"]["sales"].decimalValue() }),
                "sales at $level",
            )
            // Spend is exact inside and rounded to cents per row, so the rows may differ by up to half a cent each
            val difference = (summary["spend"].decimalValue() - rows.spend()).abs()
            assertTrue(difference <= BigDecimal("0.005") * BigDecimal(rows.size), "spend at $level differs by $difference")
        }
    }

    @Test
    fun `the product level shows products, Brand Store, unlinked ads and unmatched numbers`() {
        val rows = Seller().breakdown("by=PRODUCT")
        assertEquals(listOf("PRODUCT", "PRODUCT", "PRODUCT", "BRAND_LEVEL", "BRAND_LEVEL", "NOT_LINKED", "UNMATCHED"), rows.kinds())
        assertEquals("B0AQL00001 · US", rows.first { it["name"].asString() == "Aquarium filter 40L" }["detail"].asString())
    }

    @Test
    fun `drilling down from a product to its campaign, ad group and ads`() {
        val seller = Seller()
        val product = seller.breakdown("by=PRODUCT").first { it["kind"].asString() == "PRODUCT" }

        val campaigns = seller.breakdown("by=CAMPAIGN&productId=${product["id"].asString()}")
        assertEquals(listOf("CAMPAIGN"), campaigns.kinds())
        assertEquals(0, product["totals"]["spend"].decimalValue().compareTo(campaigns.spend()))

        val adGroups = seller.breakdown("by=AD_GROUP&campaignId=${campaigns[0]["id"].asString()}")
        assertEquals(2, adGroups.size)
        val ads = seller.breakdown("by=AD&adGroupId=${adGroups[0]["id"].asString()}")
        assertEquals(2, ads.size)
        assertTrue(ads.all { it["detail"].asString().contains("/dp/") }) // the ads' final URLs
    }

    @Test
    fun `a brand-level row drills down to its Brand Store ads`() {
        val seller = Seller()
        val brandLevel = seller.breakdown("by=PRODUCT").first { it["kind"].asString() == "BRAND_LEVEL" }
        val ads = seller.breakdown("by=AD&brandId=${brandLevel["id"].asString()}&brandLevel=true")
        assertEquals(1, ads.size)
        assertTrue(ads[0]["detail"].asString().contains("/stores/"))
    }

    @Test
    fun `without a level, or for another seller account, the request is refused`() {
        val owner = Seller()
        mockMvc
            .get("/api/v1/accounts/${owner.accountId}/reports/breakdown") { header(HttpHeaders.AUTHORIZATION, "Bearer ${owner.token}") }
            .andExpect { status { isBadRequest() } }
        mockMvc
            .get("/api/v1/accounts/${owner.accountId}/reports/breakdown?by=BRAND") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${Seller().token}")
            }.andExpect { status { isForbidden() } }
    }
}
