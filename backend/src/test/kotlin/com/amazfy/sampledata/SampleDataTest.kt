package com.amazfy.sampledata

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
    ) = mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

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

    @Test
    fun `only members' own seller account can be filled`() {
        val (_, ownerAccount) = signUp()
        val (strangerToken, _) = signUp()
        createSampleData(strangerToken, ownerAccount).andExpect { status { isForbidden() } }
    }
}
