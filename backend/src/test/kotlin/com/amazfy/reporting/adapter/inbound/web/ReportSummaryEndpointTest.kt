package com.amazfy.reporting.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import com.amazfy.common.tenant.CurrentAccount
import com.amazfy.reporting.application.RefreshReportTotals
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** GET /reports/summary over HTTP, checked against plain SQL sums of the same days. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class ReportSummaryEndpointTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    /** The backend's clock (UTC), so "yesterday" here is the same day as in the backend, whatever this computer's zone. */
    @Autowired
    lateinit var clock: Clock

    @Autowired
    lateinit var refresh: RefreshReportTotals

    private fun today() = LocalDate.now(clock)

    private fun signUp(): Pair<String, UUID> {
        val body =
            mockMvc
                .post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"email":"reports-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                        """"accountName":"AquaLife Inc.","region":"NA"}"""
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

    private fun summary(
        token: String,
        accountId: UUID,
        query: String = "",
    ) = mockMvc.get("/api/v1/accounts/$accountId/reports/summary$query") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

    private fun sum(
        sql: String,
        accountId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): BigDecimal =
        jdbc
            .sql("$sql WHERE account_id = :a AND date BETWEEN :f AND :t")
            .param("a", accountId)
            .param("f", from)
            .param("t", to)
            .query(BigDecimal::class.java)
            .single()

    @Test
    fun `the summary adds up the last 30 days of spend and sales`() {
        val (token, accountId) = signUp()
        mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        val to = today().minusDays(1)
        val from = to.minusDays(29)

        val spend = sum("SELECT sum(cost_micros) / 1000000.0 FROM ad_spend_daily", accountId, from, to).setScale(2, RoundingMode.HALF_UP)
        val sales = sum("SELECT sum(sales) FROM attribution_daily", accountId, from, to)
        val purchases = sum("SELECT sum(purchases) FROM attribution_daily", accountId, from, to).toLong()

        summary(token, accountId).andExpect {
            status { isOk() }
            jsonPath("$.from") { value(from.toString()) }
            jsonPath("$.to") { value(to.toString()) }
            jsonPath("$.currencies.length()") { value(1) }
            jsonPath("$.currencies[0].currency") { value("USD") }
            jsonPath("$.currencies[0].spend") { value(spend.toDouble()) }
            jsonPath("$.currencies[0].sales") { value(sales.toDouble()) }
            jsonPath("$.currencies[0].purchases") { value(purchases) }
            jsonPath("$.currencies[0].roas") { value(sales.divide(spend, 2, RoundingMode.HALF_UP).toDouble()) }
            jsonPath("$.currencies[0].cpa") { exists() }
            jsonPath("$.currencies[0].conversionRate") { exists() }
        }
    }

    @Test
    fun `a chosen range only counts its own days`() {
        val (token, accountId) = signUp()
        mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        val day = today().minusDays(10)
        val spend = sum("SELECT sum(cost_micros) / 1000000.0 FROM ad_spend_daily", accountId, day, day).setScale(2, RoundingMode.HALF_UP)

        summary(token, accountId, "?from=$day&to=$day").andExpect {
            jsonPath("$.currencies[0].spend") { value(spend.toDouble()) }
        }
    }

    @Test
    fun `without data there are no currencies, and a metric without spend is left out`() {
        val (token, accountId) = signUp()
        summary(token, accountId).andExpect { jsonPath("$.currencies.length()") { value(0) } }

        // Sales but no spend (e.g. the Google side isn't synced yet): no ROAS rather than a division by zero
        jdbc
            .sql(
                "INSERT INTO attribution_daily (account_id, source, marketplace, date, publisher, campaign_external_id, " +
                    "ad_group_external_id, creative_external_id, click_throughs, detail_page_views, add_to_carts, purchases, " +
                    "units_sold, sales) " +
                    "VALUES (:a, 'CSV', 'US', :d, 'Google Ads', '1', '2', '3', 50, 40, 8, 4, 4, 99.96)",
            ).param("a", accountId)
            .param("d", today().minusDays(2))
            .update()
        // Like an import: written days are refreshed, since reports read the totals added up in advance
        CurrentAccount.runAs(accountId) { refresh.forDays(today().minusDays(2), today().minusDays(2)) }
        summary(token, accountId).andExpect {
            jsonPath("$.currencies[0].sales") { value(99.96) }
            jsonPath("$.currencies[0].roas") { doesNotExist() }
            jsonPath("$.currencies[0].conversionRate") { value(8.0) }
        }
    }

    @Test
    fun `a backwards or too long range is rejected`() {
        val (token, accountId) = signUp()
        summary(token, accountId, "?from=2026-10-02&to=2026-10-01").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("The start date must not be after the end date.") }
        }
        summary(token, accountId, "?from=2024-01-01&to=2026-01-01").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `another seller account's report is forbidden`() {
        val (_, ownerAccount) = signUp()
        val (strangerToken, _) = signUp()
        summary(strangerToken, ownerAccount).andExpect { status { isForbidden() } }
    }
}
