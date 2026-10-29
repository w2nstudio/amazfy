package com.amazfy.reporting

import com.amazfy.TestcontainersConfiguration
import com.amazfy.common.tenant.CurrentAccount
import com.amazfy.reporting.application.RefreshReportTotals
import org.junit.jupiter.api.Tag
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
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * How fast the reports are for a large seller: 500 products with 5,000 Google ads and a year of daily numbers
 * (1.83 million rows of spend and as many of Attribution). Target: every report under [TARGET_MS] for the full
 * 366 days, so the dashboard stays fast for big accounts. Each report is timed over HTTP as the browser calls it:
 * one warm-up call, then the median of five.
 *
 * Slow to set up, so it is not part of `test`: run it with `./gradlew performanceTest`.
 */
@Tag("performance")
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class ReportPerformanceTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var clock: Clock

    @Autowired
    lateinit var reportTotals: RefreshReportTotals

    @Test
    fun `every report answers within the target for a large seller`() {
        val (token, accountId) = signUp()
        val to = LocalDate.now(clock).minusDays(1)
        val from = to.minusDays(365)
        val loading = measureTimeMillis { fillLargeSeller(accountId, from, to) }
        println("Large seller loaded in ${loading / 1000} s")
        val totals = measureTimeMillis { CurrentAccount.runAs(accountId) { reportTotals.forAllDays() } }
        println("Report totals built in $totals ms")

        val brandId = firstId("brands", accountId)
        val productId = firstId("products", accountId)
        val reports =
            linkedMapOf(
                "summary" to "summary?",
                "breakdown by brand" to "breakdown?by=BRAND&",
                "breakdown by product" to "breakdown?by=PRODUCT&",
                "breakdown by campaign" to "breakdown?by=CAMPAIGN&",
                "breakdown, one brand's products" to "breakdown?by=PRODUCT&brandId=$brandId&",
                "trend, whole account" to "trend?",
                "trend, one brand (250 ads)" to "trend?brandId=$brandId&",
                "trend, one product (10 ads)" to "trend?productId=$productId&",
            )
        val timings =
            reports.mapValues { (_, query) ->
                medianMillis {
                    mockMvc
                        .get("/api/v1/accounts/$accountId/reports/${query}from=$from&to=$to") {
                            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        }.andExpect { status { isOk() } }
                }
            }

        println("Report times for 366 days (median of 5, target $TARGET_MS ms):")
        timings.forEach { (name, ms) -> println("  ${name.padEnd(34)} ${"$ms".padStart(6)} ms${if (ms > TARGET_MS) "  SLOW" else ""}") }
        val slow = timings.filterValues { it > TARGET_MS }
        assertTrue(slow.isEmpty(), "Over $TARGET_MS ms: $slow")
    }

    private fun signUp(): Pair<String, UUID> {
        val body =
            mockMvc
                .post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"email":"large-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                        """"accountName":"Large Seller","region":"NA"}"""
                }.andReturn()
                .response.contentAsString
        val token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
        val me =
            mockMvc
                .get("/api/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
        return token to UUID.fromString(Regex(""""account":\{"id":"([^"]+)"""").find(me)!!.groupValues[1])
    }

    /**
     * 20 brands × 25 products; per product one campaign with 2 ad groups of 5 ads; every ad has spend and
     * Attribution numbers on every day. Written with set-based SQL (generate_series): millions of rows in seconds.
     */
    private fun fillLargeSeller(
        accountId: UUID,
        from: LocalDate,
        to: LocalDate,
    ) {
        fun sql(statement: String) =
            jdbc
                .sql(statement)
                .param("a", accountId)
                .param("from", from)
                .param("to", to)
                .update()
        val days = "generate_series(CAST(:from AS timestamp), CAST(:to AS timestamp), interval '1 day') AS d"

        sql("INSERT INTO brands (account_id, name) SELECT :a, 'Brand ' || lpad(b::text, 2, '0') FROM generate_series(1, 20) AS b")
        sql(
            """
            INSERT INTO products (account_id, brand_id, asin, marketplace, name)
            SELECT :a, br.id, 'B0' || lpad(p::text, 8, '0'), 'US', 'Product ' || p
            FROM generate_series(1, 500) AS p
            JOIN (SELECT id, row_number() OVER (ORDER BY name) AS n FROM brands WHERE account_id = :a) AS br ON br.n = (p - 1) % 20 + 1
            """,
        )
        // The campaign's external id is the product's ASIN here, which makes linking the ads below a plain join
        sql(
            "INSERT INTO campaigns (account_id, channel, external_id, name, status) " +
                "SELECT :a, 'GOOGLE', asin, 'Campaign ' || name, 'ENABLED' FROM products WHERE account_id = :a",
        )
        sql(
            """
            INSERT INTO ad_groups (account_id, campaign_id, external_id, name, status)
            SELECT :a, c.id, c.external_id || '-' || g, 'Ad group ' || g, 'ENABLED'
            FROM campaigns AS c CROSS JOIN generate_series(1, 2) AS g
            WHERE c.account_id = :a
            """,
        )
        sql(
            """
            INSERT INTO ads (account_id, ad_group_id, external_id, final_url, status, product_id)
            SELECT :a, g.id, g.external_id || '-' || n, 'https://www.amazon.com/dp/' || p.asin, 'ENABLED', p.id
            FROM ad_groups AS g
            JOIN campaigns AS c ON c.id = g.campaign_id
            JOIN products AS p ON p.account_id = :a AND p.asin = c.external_id
            CROSS JOIN generate_series(1, 5) AS n
            WHERE g.account_id = :a
            """,
        )
        sql(
            """
            INSERT INTO ad_spend_daily (account_id, ad_id, date, impressions, clicks, cost_micros, currency)
            SELECT :a, ad.id, d::date, 400, 20, 10000000, 'USD'
            FROM ads AS ad CROSS JOIN $days
            WHERE ad.account_id = :a
            """,
        )
        sql(
            """
            INSERT INTO attribution_daily (account_id, source, marketplace, date, publisher, campaign_external_id,
                ad_group_external_id, creative_external_id, ad_id, click_throughs, detail_page_views, add_to_carts,
                purchases, units_sold, sales)
            SELECT :a, 'CSV', 'US', d::date, 'Google Ads', c.external_id, g.external_id, ad.external_id, ad.id, 18, 18, 2, 1, 1, 40
            FROM ads AS ad
            JOIN ad_groups AS g ON g.id = ad.ad_group_id
            JOIN campaigns AS c ON c.id = g.campaign_id
            CROSS JOIN $days
            WHERE ad.account_id = :a
            """,
        )
        jdbc.sql("ANALYZE").update() // fresh statistics, as autovacuum would have after a real import
    }

    private fun firstId(
        table: String,
        accountId: UUID,
    ): UUID =
        jdbc
            .sql("SELECT id FROM $table WHERE account_id = :a ORDER BY name LIMIT 1")
            .param("a", accountId)
            .query(UUID::class.java)
            .single()

    private fun medianMillis(call: () -> Unit): Long {
        call() // warm-up: the first call also pays for loading classes and filling caches
        return List(5) { measureNanoTime(call) / 1_000_000 }.sorted()[2]
    }

    private companion object {
        const val TARGET_MS = 500
    }
}
