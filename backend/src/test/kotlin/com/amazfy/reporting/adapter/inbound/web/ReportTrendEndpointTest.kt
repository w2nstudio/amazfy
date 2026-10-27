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
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GET /reports/trend over HTTP, on sample data: the days add up to the summary and follow the drill-down filters. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class ReportTrendEndpointTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var json: JsonMapper

    @Autowired
    lateinit var clock: Clock

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
                            """{"email":"trend-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc.","region":"NA"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            accountId = get("/api/v1/me")["accounts"][0]["account"]["id"].asString()
            mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        }

        fun get(path: String): JsonNode =
            json.readTree(
                mockMvc
                    .get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                    .andReturn()
                    .response.contentAsString,
            )

        fun report(
            name: String,
            query: String = "",
        ): JsonNode = get("/api/v1/accounts/$accountId/reports/$name?$query")

        /** The days of the only series (sample data is all USD). */
        fun trendDays(query: String = ""): List<JsonNode> = report("trend", query)["series"].single()["days"].toList()
    }

    private fun List<JsonNode>.sum(field: String): BigDecimal = sumOf { it["totals"][field].decimalValue() }

    /** Sales add up exactly; spend is rounded to cents per day, so it may differ by up to half a cent per day. */
    private fun assertAddsUp(
        expected: JsonNode,
        days: List<JsonNode>,
    ) {
        assertEquals(0, expected["sales"].decimalValue().compareTo(days.sum("sales")))
        val difference = (expected["spend"].decimalValue() - days.sum("spend")).abs()
        assertTrue(difference <= BigDecimal("0.005") * BigDecimal(days.size), "spend differs by $difference")
    }

    @Test
    fun `the last 30 days, every day in order, adding up to the summary`() {
        val seller = Seller()
        val days = seller.trendDays()

        val yesterday = LocalDate.now(clock).minusDays(1)
        assertEquals((29L downTo 0).map { yesterday.minusDays(it).toString() }, days.map { it["date"].asString() })
        assertAddsUp(seller.report("summary")["currencies"].single(), days)
    }

    @Test
    fun `a product's trend adds up to its drill-down row`() {
        val seller = Seller()
        val product = seller.report("breakdown", "by=PRODUCT")["rows"].first { it["kind"].asString() == "PRODUCT" }
        assertAddsUp(product["totals"], seller.trendDays("productId=${product["id"].asString()}"))
    }

    @Test
    fun `one ad's trend, and the ad filter in the drill-down`() {
        val seller = Seller()
        val ad = seller.report("breakdown", "by=AD")["rows"].first { it["kind"].asString() == "AD" }
        val adId = ad["id"].asString()

        assertAddsUp(ad["totals"], seller.trendDays("adId=$adId"))
        val rows = seller.report("breakdown", "by=AD&adId=$adId")["rows"].toList()
        assertEquals(listOf(adId), rows.map { it["id"].asString() })
    }

    @Test
    fun `a filter no ad matches gives no series`() {
        val seller = Seller()
        assertEquals(0, seller.report("trend", "productId=${UUID.randomUUID()}")["series"].size())
    }

    @Test
    fun `a bad range or another seller account is refused`() {
        val owner = Seller()
        mockMvc
            .get("/api/v1/accounts/${owner.accountId}/reports/trend?from=2026-10-10&to=2026-10-01") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${owner.token}")
            }.andExpect { status { isBadRequest() } }
        mockMvc
            .get("/api/v1/accounts/${owner.accountId}/reports/trend") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${Seller().token}")
            }.andExpect { status { isForbidden() } }
    }
}
