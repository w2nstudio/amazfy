package com.amazfy.sampledata

import com.amazfy.TestcontainersConfiguration
import com.jayway.jsonpath.JsonPath
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
import kotlin.test.assertTrue

/** The local-only sample data endpoint (tests run with the default "local" profile). */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class SampleDataTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var clock: Clock

    private fun signUp(): Pair<String, UUID> {
        val body =
            mockMvc
                .post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"email":"sample-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
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

    private fun createSampleData(
        token: String,
        accountId: UUID,
        set: String? = null,
    ) = mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data${set?.let { "?set=$it" } ?: ""}") {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }

    private fun count(
        table: String,
        accountId: UUID,
        where: String = "true",
    ): Int =
        jdbc
            .sql("SELECT count(*) FROM $table WHERE account_id = :a AND $where")
            .param("a", accountId)
            .query(Int::class.java)
            .single()

    @Test
    fun `an empty seller account gets sample products, ads, spend and Attribution numbers`() {
        val (token, accountId) = signUp()

        createSampleData(token, accountId).andExpect {
            status { isOk() }
            jsonPath("$.products") { value(3) }
            // 3 product campaigns with 2 ad groups x 2 ads, plus 2 Brand Store campaigns with 2 ads each
            jsonPath("$.campaigns") { value(5) }
            jsonPath("$.ads") { value(16) }
            jsonPath("$.spendRows") { value(16 * 90) }
        }

        assertEquals(3, count("products", accountId))
        assertEquals(16 * 90, count("ad_spend_daily", accountId))
        // Every case the reports must handle is there
        assertEquals(12, count("ads", accountId, "product_id IS NOT NULL"))
        assertEquals(2, count("ads", accountId, "brand_id IS NOT NULL"))
        assertEquals(2, count("ads", accountId, "product_id IS NULL AND brand_id IS NULL"))
        assertTrue(count("attribution_daily", accountId, "ad_id IS NULL") > 0)
    }

    @Test
    fun `running it again replaces the data instead of adding to it`() {
        val (token, accountId) = signUp()
        createSampleData(token, accountId)
        createSampleData(token, accountId).andExpect { status { isOk() } }

        assertEquals(3, count("products", accountId))
        assertEquals(5, count("campaigns", accountId))
        assertEquals(16 * 90, count("ad_spend_daily", accountId))
    }

    private fun report(
        token: String,
        accountId: UUID,
        query: String,
    ) = mockMvc.get("/api/v1/accounts/$accountId/reports/$query") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

    @Test
    fun `the numbers match the expected results in docs-guides-sample-data`() {
        val (token, accountId) = signUp()
        createSampleData(token, accountId)

        // Per day: $160 spend, $584 sales, 21 purchases, 297 click-throughs, 320 clicks; 30 days = 30 times that
        report(token, accountId, "summary").andExpect {
            jsonPath("$.currencies[0].spend") { value(4800.0) }
            jsonPath("$.currencies[0].sales") { value(17520.0) }
            jsonPath("$.currencies[0].purchases") { value(630) }
            jsonPath("$.currencies[0].clickThroughs") { value(8910) }
            jsonPath("$.currencies[0].clicks") { value(9600) }
            jsonPath("$.currencies[0].roas") { value(3.65) }
            jsonPath("$.currencies[0].cpa") { value(7.62) }
            jsonPath("$.currencies[0].conversionRate") { value(7.07) }
        }
        // One day per brand, then the not-linked short links and the unmatched Attribution line
        val yesterday = LocalDate.now(clock).minusDays(1)
        val brands =
            listOf(
                Triple("AquaLife", 125.0, 460.0),
                Triple("ClearTank", 25.0, 84.0),
                Triple("Not linked to a product or brand", 10.0, 0.0),
                Triple("Not matched to an ad", 0.0, 40.0),
            )
        report(token, accountId, "breakdown?by=BRAND&from=$yesterday&to=$yesterday").andExpect {
            jsonPath("$.rows.length()") { value(brands.size) }
            brands.forEachIndexed { i, (name, spend, sales) ->
                jsonPath("$.rows[$i].name") { value(name) }
                jsonPath("$.rows[$i].totals.spend") { value(spend) }
                jsonPath("$.rows[$i].totals.sales") { value(sales) }
            }
            jsonPath("$.rows[0].totals.roas") { value(3.68) }
            jsonPath("$.rows[1].totals.roas") { value(3.36) }
            jsonPath("$.rows[3].totals.roas") { doesNotExist() } // no spend, so no ROAS
            jsonPath("$.rows[3].totals.cpa") { doesNotExist() } // and no CPA
        }
    }

    @Test
    fun `all 90 days add up exactly, whole months included`() {
        val (token, accountId) = signUp()
        createSampleData(token, accountId)
        // 90 days always hold whole calendar months: the drill-down reads them from the monthly totals and only the
        // loose days at either end from the daily rows, and must still give exactly 90 times one day
        val to = LocalDate.now(clock).minusDays(1)
        val days = "from=${to.minusDays(89)}&to=$to"

        report(token, accountId, "summary?$days").andExpect {
            jsonPath("$.currencies[0].spend") { value(14400.0) }
            jsonPath("$.currencies[0].sales") { value(52560.0) }
        }
        report(token, accountId, "breakdown?by=BRAND&$days").andExpect {
            jsonPath("$.rows[0].totals.spend") { value(11250.0) } // AquaLife: 90 × $125
            jsonPath("$.rows[0].totals.sales") { value(41400.0) } // 90 × $460
            jsonPath("$.rows[1].totals.spend") { value(2250.0) } // ClearTank: 90 × $25
            jsonPath("$.rows[2].totals.spend") { value(900.0) } // not linked: 90 × $10
            jsonPath("$.rows[3].totals.sales") { value(3600.0) } // unmatched: 90 × $40
        }
    }

    @Test
    fun `the seller's own products get no sample ads`() {
        val (token, accountId) = signUp()
        val brand =
            mockMvc
                .post("/api/v1/accounts/$accountId/brands") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"My Brand"}"""
                }.andReturn()
                .response.contentAsString
        val brandId = Regex(""""id":"([^"]+)"""").find(brand)!!.groupValues[1]
        mockMvc.post("/api/v1/accounts/$accountId/products") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"brandId":"$brandId","asin":"B0MYOWN001","marketplace":"US","name":"My product"}"""
        }

        createSampleData(token, accountId).andExpect { jsonPath("$.products") { value(3) } }

        assertEquals(4, count("products", accountId)) // the own product and the three sample products
        assertEquals(0, count("ads", accountId, "product_id IN (SELECT id FROM products WHERE asin = 'B0MYOWN001')"))
    }

    @Test
    fun `the small demo is 4 days for Leafwell, 18 to 15 days ago`() {
        val (token, accountId) = signUp()
        val today = LocalDate.now(clock)

        createSampleData(token, accountId, "SMALL_DEMO").andExpect {
            status { isOk() }
            jsonPath("$.fromDate") { value(today.minusDays(18).toString()) }
            jsonPath("$.toDate") { value(today.minusDays(15).toString()) }
            jsonPath("$.products") { value(2) }
            jsonPath("$.campaigns") { value(3) } // shears (ads A and B), gloves, brand (short link)
            jsonPath("$.ads") { value(4) }
            jsonPath("$.spendRows") { value(4 * 4) }
            jsonPath("$.attributionRows") { value(5 * 4) } // the 4 ads and the unmatched line
        }

        assertEquals(2, count("products", accountId, "brand_id IN (SELECT id FROM brands WHERE name = 'Leafwell')"))
        assertEquals(1, count("ads", accountId, "product_id IS NULL AND brand_id IS NULL"))
    }

    @Test
    fun `the small demo's numbers match the expected results in docs-guides-sample-data`() {
        val (token, accountId) = signUp()
        createSampleData(token, accountId, "SMALL_DEMO")
        val today = LocalDate.now(clock)
        val days = "from=${today.minusDays(18)}&to=${today.minusDays(15)}"

        // 4 days: $257 spend at $1 a click, $580 sales from 26 purchases and 262 click-throughs
        report(token, accountId, "summary?$days").andExpect {
            jsonPath("$.currencies[0].spend") { value(257.0) }
            jsonPath("$.currencies[0].sales") { value(580.0) }
            jsonPath("$.currencies[0].purchases") { value(26) }
            jsonPath("$.currencies[0].clicks") { value(257) }
            jsonPath("$.currencies[0].clickThroughs") { value(262) }
            jsonPath("$.currencies[0].roas") { value(2.26) }
            jsonPath("$.currencies[0].cpa") { value(9.88) }
            jsonPath("$.currencies[0].conversionRate") { value(9.92) }
        }
        val brands =
            listOf(
                Triple("Leafwell", 237.0, 530.0),
                Triple("Not linked to a product or brand", 20.0, 0.0),
                Triple("Not matched to an ad", 0.0, 50.0),
            )
        report(token, accountId, "breakdown?by=BRAND&$days").andExpect {
            jsonPath("$.rows.length()") { value(brands.size) }
            brands.forEachIndexed { i, (name, spend, sales) ->
                jsonPath("$.rows[$i].name") { value(name) }
                jsonPath("$.rows[$i].totals.spend") { value(spend) }
                jsonPath("$.rows[$i].totals.sales") { value(sales) }
            }
        }
        // Ad A and ad B advertise the same shears at about the same cost; A sells far more
        val shears =
            jdbc
                .sql("SELECT id FROM products WHERE account_id = :a AND asin = 'B0LFW00001'")
                .param("a", accountId)
                .query(UUID::class.java)
                .single()
        report(token, accountId, "breakdown?by=AD&productId=$shears&$days").andExpect {
            jsonPath("$.rows.length()") { value(2) }
            jsonPath("$.rows[0].totals.spend") { value(95.0) }
            jsonPath("$.rows[0].totals.sales") { value(325.0) }
            jsonPath("$.rows[0].totals.roas") { value(3.42) }
            jsonPath("$.rows[1].totals.spend") { value(85.0) }
            jsonPath("$.rows[1].totals.sales") { value(25.0) }
            jsonPath("$.rows[1].totals.roas") { value(0.29) }
        }
        // Day 3 is the very good day, day 4 the weak one
        val spend = listOf(55.0, 59.0, 78.0, 65.0)
        val sales = listOf(111.0, 136.0, 265.0, 68.0)
        report(token, accountId, "trend?$days").andExpect {
            jsonPath("$.series[0].days.length()") { value(4) }
            spend.indices.forEach { i ->
                jsonPath("$.series[0].days[$i].totals.spend") { value(spend[i]) }
                jsonPath("$.series[0].days[$i].totals.sales") { value(sales[i]) }
            }
        }
    }

    @Test
    fun `without Amazon's numbers, the returned CSV imported on the Import page gives the same reports`() {
        val (token, accountId) = signUp()
        val today = LocalDate.now(clock)
        val days = "from=${today.minusDays(18)}&to=${today.minusDays(15)}"

        val body =
            mockMvc
                .post("/api/v1/accounts/$accountId/dev/sample-data?set=SMALL_DEMO&amazonNumbers=false") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.spendRows") { value(4 * 4) }
                    jsonPath("$.attributionRows") { value(0) }
                }.andReturn()
                .response.contentAsString
        val csv = JsonPath.read<String>(body, "$.attributionCsv")
        assertEquals(1 + 5 * 4, csv.trimEnd().lines().size) // the header, then the 4 ads and the unmatched line per day
        assertEquals(0, count("attribution_daily", accountId))
        // Before the import: Google's spend, no sales yet
        report(token, accountId, "summary?$days").andExpect {
            jsonPath("$.currencies[0].spend") { value(257.0) }
            jsonPath("$.currencies[0].sales") { value(0.0) }
        }

        mockMvc
            .multipart("/api/v1/accounts/$accountId/attribution/imports") {
                file(MockMultipartFile("file", "leafwell-attribution.csv", "text/csv", csv.toByteArray()))
                param("marketplace", "US")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.lines") { value(20) }
                jsonPath("$.matchedLines") { value(16) } // all but the unmatched line's 4 days
            }

        // After it: exactly the one-click fill's numbers
        report(token, accountId, "summary?$days").andExpect {
            jsonPath("$.currencies[0].spend") { value(257.0) }
            jsonPath("$.currencies[0].sales") { value(580.0) }
            jsonPath("$.currencies[0].purchases") { value(26) }
            jsonPath("$.currencies[0].clickThroughs") { value(262) }
        }
        report(token, accountId, "breakdown?by=BRAND&$days").andExpect {
            jsonPath("$.rows[0].totals.sales") { value(530.0) } // Leafwell
            jsonPath("$.rows[2].name") { value("Not matched to an ad") }
            jsonPath("$.rows[2].totals.sales") { value(50.0) }
        }
    }

    @Test
    fun `switching between the sets replaces the data`() {
        val (token, accountId) = signUp()
        createSampleData(token, accountId)
        createSampleData(token, accountId, "SMALL_DEMO")

        assertEquals(3, count("campaigns", accountId))
        assertEquals(4 * 4, count("ad_spend_daily", accountId))

        createSampleData(token, accountId, "FULL")
        assertEquals(5, count("campaigns", accountId))
        assertEquals(5, count("products", accountId)) // products stay: the three aquarium and the two gardening ones
    }

    @Test
    fun `only members' own seller account can be filled`() {
        val (_, ownerAccount) = signUp()
        val (strangerToken, _) = signUp()
        createSampleData(strangerToken, ownerAccount).andExpect { status { isForbidden() } }
    }
}
