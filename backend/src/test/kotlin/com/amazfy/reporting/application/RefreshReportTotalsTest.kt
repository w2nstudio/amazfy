package com.amazfy.reporting.application

import com.amazfy.TestcontainersConfiguration
import com.amazfy.common.tenant.CurrentAccount
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

/** Reports read the totals added up in advance, so they change only once the changed days are refreshed. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class RefreshReportTotalsTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var clock: Clock

    @Autowired
    lateinit var refresh: RefreshReportTotals

    @Test
    fun `changed daily numbers reach the reports after refreshing their days`() {
        val (token, accountId) = signUp()
        mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        val yesterday = LocalDate.now(clock).minusDays(1)
        val ninetyDays = "from=${yesterday.minusDays(89)}&to=$yesterday"

        // An import corrects yesterday's spend of one ad: $100 more
        jdbc
            .sql(
                "UPDATE ad_spend_daily SET cost_micros = cost_micros + 100000000 " +
                    "WHERE ad_id = (SELECT id FROM ads WHERE account_id = :a ORDER BY id LIMIT 1) AND date = :d",
            ).param("a", accountId)
            .param("d", yesterday)
            .update()

        // Not refreshed yet: the reports still show the totals
        summarySpend(token, accountId, "", 4800.0)

        CurrentAccount.runAs(accountId) { refresh.forDays(yesterday, yesterday) }

        summarySpend(token, accountId, "", 4900.0) // last 30 days, from the account's daily totals
        mockMvc
            .get("/api/v1/accounts/$accountId/reports/breakdown?by=BRAND&$ninetyDays") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { jsonPath("$.rows[0].totals.spend") { value(11350.0) } } // AquaLife: 90 × $125 + $100
    }

    private fun summarySpend(
        token: String,
        accountId: UUID,
        query: String,
        expected: Double,
    ) = mockMvc
        .get("/api/v1/accounts/$accountId/reports/summary?$query") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        .andExpect { jsonPath("$.currencies[0].spend") { value(expected) } }

    private fun signUp(): Pair<String, UUID> {
        val body =
            mockMvc
                .post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"email":"totals-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
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
}
