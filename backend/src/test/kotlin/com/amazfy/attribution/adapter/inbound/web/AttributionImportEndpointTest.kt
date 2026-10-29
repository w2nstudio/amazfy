package com.amazfy.attribution.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * POST /attribution/imports over HTTP, on an account with sample data (so there are Google ads to match). Sample data
 * has, for the filter's ad A in the exact ad group, 2 purchases and $80 sales every day.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AttributionImportEndpointTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var clock: Clock

    private val header =
        "Date,Publisher,Campaign ID,Ad group ID,Creative ID," +
            "Click-throughs,Detail page views,Add to carts,Purchases,Units sold,Sales"

    private inner class Seller {
        val token: String
        val accountId: UUID

        init {
            val body =
                mockMvc
                    .post("/api/v1/auth/register") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"email":"import-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc.","region":"NA"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            val me =
                mockMvc
                    .get("/api/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                    .andReturn()
                    .response.contentAsString
            accountId = UUID.fromString(Regex(""""account":\{"id":"([^"]+)"""").find(me)!!.groupValues[1])
            mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        }

        fun upload(
            csv: String,
            marketplace: String = "US",
            asToken: String = token,
            dryRun: Boolean = false,
        ) = mockMvc.multipart("/api/v1/accounts/$accountId/attribution/imports${if (dryRun) "?dryRun=true" else ""}") {
            file(MockMultipartFile("file", "attribution.csv", "text/csv", csv.toByteArray()))
            param("marketplace", marketplace)
            header(HttpHeaders.AUTHORIZATION, "Bearer $asToken")
        }

        /** Google's campaign, ad group and ad ids of the filter's ad A in the exact ad group: "campaign,ad group,ad". */
        fun filterAdIds(): String =
            jdbc
                .sql(
                    """
                    SELECT c.external_id || ',' || g.external_id || ',' || ad.external_id
                    FROM ads ad JOIN ad_groups g ON g.id = ad.ad_group_id JOIN campaigns c ON c.id = g.campaign_id
                    WHERE ad.account_id = :a AND c.name LIKE '%filter%' AND g.name LIKE '%exact' ORDER BY ad.external_id LIMIT 1
                    """,
                ).param("a", accountId)
                .query(String::class.java)
                .single()

        fun attributionRows(): Int =
            jdbc
                .sql("SELECT count(*) FROM attribution_daily WHERE account_id = :a")
                .param("a", accountId)
                .query(Int::class.java)
                .single()

        fun summarySales() =
            mockMvc
                .get("/api/v1/accounts/$accountId/reports/summary") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
                .let { Regex(""""sales":([\d.]+)""").find(it)!!.groupValues[1].toDouble() }
    }

    private fun yesterday() = LocalDate.now(clock).minusDays(1)

    @Test
    fun `an upload updates the matched ad's day, never duplicates it, and the reports follow`() {
        val seller = Seller()
        val rowsBefore = seller.attributionRows()
        // Corrected numbers for one sample line: 5 purchases and $200 instead of 2 and $80
        val csv = "$header\n${yesterday()},Google Ads,${seller.filterAdIds()},90,85,10,5,5,\"$200.00\""

        seller.upload(csv).andExpect {
            status { isOk() }
            jsonPath("$.lines") { value(1) }
            jsonPath("$.matchedLines") { value(1) }
            jsonPath("$.unmatchedLines") { value(0) }
            jsonPath("$.fromDate") { value("${yesterday()}") }
        }
        assertEquals(rowsBefore, seller.attributionRows()) // the sample line was updated, not added
        assertEquals(17520.0 + 120, seller.summarySales()) // last 30 days: $120 more than the sample's

        seller.upload(csv).andExpect { status { isOk() } } // the same report again
        assertEquals(rowsBefore, seller.attributionRows())
        assertEquals(17520.0 + 120, seller.summarySales())
    }

    @Test
    fun `a dry run shows what the upload would bring in and saves nothing`() {
        val seller = Seller()
        val rowsBefore = seller.attributionRows()
        val csv =
            "$header\n${yesterday()},Google Ads,${seller.filterAdIds()},90,85,10,5,5,\"$200.00\"\n" +
                "${yesterday()},Google Ads,1,2,3,9,9,2,1,1,25"

        seller.upload(csv, dryRun = true).andExpect {
            status { isOk() }
            jsonPath("$.saved") { value(false) }
            jsonPath("$.lines") { value(2) }
            jsonPath("$.matchedLines") { value(1) }
            jsonPath("$.sales") { value(225.0) }
            jsonPath("$.currency") { value("USD") }
        }
        assertEquals(rowsBefore, seller.attributionRows())
        assertEquals(17520.0, seller.summarySales()) // unchanged
    }

    @Test
    fun `unknown ads are kept unmatched and other publishers are left out`() {
        val seller = Seller()
        val csv =
            """
            $header
            ${yesterday()},Google Ads,111,222,333,9,9,2,1,1,25.00
            ${yesterday()},Meta,444,555,666,9,9,2,1,1,25.00
            """.trimIndent()

        seller.upload(csv).andExpect {
            status { isOk() }
            jsonPath("$.lines") { value(1) }
            jsonPath("$.unmatchedLines") { value(1) }
            jsonPath("$.otherPublisherLines") { value(1) }
        }
        assertEquals(17520.0 + 25, seller.summarySales()) // counted as "Not matched to an ad"
    }

    @Test
    fun `a wrong file, a marketplace outside the region or another seller is refused`() {
        val seller = Seller()
        seller.upload("Date,Clicks,Cost\n2026-10-27,10,5.00").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value(org.hamcrest.Matchers.containsString("no column found")) }
        }
        seller.upload("$header\n${yesterday()},Google Ads,1,2,3,1,1,1,1,1,1", marketplace = "UK").andExpect {
            status { isBadRequest() }
        }
        seller.upload("$header\n${yesterday()},Google Ads,1,2,3,1,1,1,1,1,1", asToken = Seller().token).andExpect {
            status { isForbidden() }
        }
    }
}
