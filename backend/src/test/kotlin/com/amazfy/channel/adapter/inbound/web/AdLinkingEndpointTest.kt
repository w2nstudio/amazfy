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
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals

/** Linking ads over HTTP, on sample data: its two short-link ads are unlinked, at $5 a day each ($300 in 30 days). */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AdLinkingEndpointTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var json: JsonMapper

    private inner class Seller {
        val token: String
        val accountId: UUID

        init {
            val body =
                mockMvc
                    .post("/api/v1/auth/register") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"email":"links-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc.","region":"NA"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            accountId = UUID.fromString(get("/api/v1/me")["accounts"][0]["account"]["id"].asString())
            mockMvc.post("/api/v1/accounts/$accountId/dev/sample-data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        }

        fun get(path: String): JsonNode =
            json.readTree(
                mockMvc
                    .get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                    .andReturn()
                    .response.contentAsString,
            )

        fun unlinked() = get("/api/v1/accounts/$accountId/ads/unlinked").toList()

        fun link(
            adId: Any,
            body: String,
            asToken: String = token,
        ) = mockMvc.put("/api/v1/accounts/$accountId/ads/$adId/link") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $asToken")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

        fun autoLink() = mockMvc.post("/api/v1/accounts/$accountId/ads/auto-link") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

        /** Brand rows of the last 30 days as name to spend. */
        fun brandSpend(): Map<String, Double> =
            get("/api/v1/accounts/$accountId/reports/breakdown?by=BRAND")["rows"].associate {
                it["name"].asString() to it["totals"]["spend"].asDouble()
            }

        fun firstBrandId(): String = get("/api/v1/accounts/$accountId/brands")[0]["id"].asString()

        fun firstProductId(): String = get("/api/v1/accounts/$accountId/products")[0]["id"].asString()

        /** Makes one product ad unlinked again, as if it came from a sync before its product was in Amazfy. */
        fun unlinkOneProductAd(): UUID =
            jdbc
                .sql(
                    """
                    UPDATE ads SET product_id = NULL WHERE id =
                        (SELECT id FROM ads WHERE account_id = :a AND product_id IS NOT NULL ORDER BY external_id LIMIT 1)
                    RETURNING id
                    """,
                ).param("a", accountId)
                .query(UUID::class.java)
                .single()
    }

    @Test
    fun `lists the unlinked ads with what their link is`() {
        val ads = Seller().unlinked()
        assertEquals(2, ads.size)
        assertEquals(listOf("OTHER", "OTHER"), ads.map { it["linkKind"].asString() })
        assertEquals("AquaLife – Brand Store (US)", ads[0]["campaignName"].asString())
    }

    @Test
    fun `linking an ad to a brand moves its spend in the reports at once`() {
        val seller = Seller()
        val before = seller.brandSpend()
        assertEquals(300.0, before["Not linked to a product or brand"])

        seller.link(seller.unlinked()[0]["id"].asString(), """{"brandId":"${seller.firstBrandId()}"}""").andExpect {
            status { isNoContent() }
        }

        val after = seller.brandSpend()
        assertEquals(150.0, after["Not linked to a product or brand"])
        assertEquals(before.getValue("AquaLife") + 150, after["AquaLife"])
        assertEquals(1, seller.unlinked().size)
    }

    @Test
    fun `auto-link links product pages of known products and leaves the seller's links alone`() {
        val seller = Seller()
        val ad = seller.unlinkOneProductAd()
        // The seller's own choice for a short-link ad stays
        val shortLink = seller.unlinked().first { it["id"].asString() != "$ad" }["id"].asString()
        seller.link(shortLink, """{"brandId":"${seller.firstBrandId()}"}""")

        seller.autoLink().andExpect {
            status { isOk() }
            jsonPath("$.linked") { value(1) }
            jsonPath("$.stillUnlinked") { value(1) } // the other short link
        }

        assertEquals(1, seller.unlinked().size)
        val brandOfShortLink =
            jdbc
                .sql("SELECT brand_id FROM ads WHERE id = :id")
                .param("id", UUID.fromString(shortLink))
                .query(UUID::class.java)
                .single()
        assertEquals(seller.firstBrandId(), "$brandOfShortLink")
    }

    @Test
    fun `a product page of an unknown product is listed with its ASIN`() {
        val seller = Seller()
        val ad = seller.unlinkOneProductAd()
        jdbc
            .sql("UPDATE ads SET final_url = 'https://www.amazon.com/dp/B0NOTKNOWN' WHERE id = :id")
            .param("id", ad)
            .update()

        val listed = seller.unlinked().first { it["id"].asString() == "$ad" }
        assertEquals("PRODUCT_PAGE", listed["linkKind"].asString())
        assertEquals("B0NOTKNOWN", listed["asin"].asString())
        assertEquals("US", listed["marketplace"].asString())
        seller.autoLink().andExpect { jsonPath("$.linked") { value(0) } }
    }

    @Test
    fun `wrong links, unknown ads and other sellers are refused`() {
        val seller = Seller()
        val ad = seller.unlinked()[0]["id"].asString()

        seller.link(ad, "{}").andExpect { status { isBadRequest() } }
        seller.link(ad, """{"brandId":"${seller.firstBrandId()}","productId":"${seller.firstProductId()}"}""").andExpect {
            status { isBadRequest() }
        }
        val other = Seller()
        seller.link(ad, """{"productId":"${other.firstProductId()}"}""").andExpect { status { isBadRequest() } }
        seller.link(UUID.randomUUID(), """{"brandId":"${seller.firstBrandId()}"}""").andExpect { status { isNotFound() } }
        seller.link(ad, """{"brandId":"${seller.firstBrandId()}"}""", asToken = other.token).andExpect { status { isForbidden() } }
    }
}
