package com.amazfy.catalog.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

/** The brand endpoints over HTTP, including that each seller only ever reaches their own brands. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class BrandEndpointsTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    /** A signed-up seller: access token and the id of the Amazon seller account created at sign-up. */
    private inner class Seller {
        val token: String
        val accountId: String

        init {
            val body =
                mockMvc
                    .post("/api/v1/auth/register") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"email":"brands-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
                            """"accountName":"AquaLife Inc. – North America"}"""
                    }.andReturn()
                    .response.contentAsString
            token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
            val me =
                mockMvc
                    .get("/api/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                    .andReturn()
                    .response.contentAsString
            accountId = Regex(""""account":\{"id":"([^"]+)"""").find(me)!!.groupValues[1]
        }

        val brands get() = "/api/v1/accounts/$accountId/brands"

        fun list(): ResultActionsDsl = mockMvc.get(brands) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

        fun create(name: String): ResultActionsDsl =
            mockMvc.post(brands) {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"$name"}"""
            }

        fun createId(name: String): String =
            Regex(""""id":"([^"]+)"""").find(create(name).andReturn().response.contentAsString)!!.groupValues[1]

        fun rename(
            brandId: String,
            name: String,
        ): ResultActionsDsl =
            mockMvc.patch("$brands/$brandId") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"$name"}"""
            }

        fun delete(brandId: String): ResultActionsDsl =
            mockMvc.delete("$brands/$brandId") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
    }

    @Test
    fun `brands are added and listed by name`() {
        val seller = Seller()
        seller.create("  ClearTank ").andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("ClearTank") }
            jsonPath("$.productCount") { value(0) }
        }
        seller.create("AquaLife").andExpect { status { isCreated() } }

        seller.list().andExpect {
            status { isOk() }
            jsonPath("$[*].name") { value(contains("AquaLife", "ClearTank")) }
        }
    }

    @Test
    fun `a brand name can't be used twice in one account, whatever the case`() {
        val seller = Seller()
        seller.create("AquaLife")

        seller.create("aqualife").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("You already have a brand with this name.") }
        }
    }

    @Test
    fun `renaming checks other brands' names but allows changing the capitalisation of its own`() {
        val seller = Seller()
        val aquaLife = seller.createId("Aqualife")
        seller.create("ClearTank")

        seller.rename(aquaLife, "AquaLife").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("AquaLife") }
        }
        seller.rename(aquaLife, "cleartank").andExpect { status { isConflict() } }
    }

    @Test
    fun `a removed brand is gone`() {
        val seller = Seller()
        val brand = seller.createId("AquaLife")

        seller.delete(brand).andExpect { status { isNoContent() } }

        seller.delete(brand).andExpect { status { isNotFound() } }
        seller.list().andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `an empty or too long name is rejected`() {
        val seller = Seller()
        seller.create("   ").andExpect { status { isBadRequest() } }
        seller.create("x".repeat(101)).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `another seller's brand is not found through their own account, and their account is forbidden`() {
        val owner = Seller()
        val brand = owner.createId("AquaLife")
        val other = Seller()

        // Using their own account in the path: the brand simply doesn't exist there (@TenantId)
        other.list().andExpect { jsonPath("$.length()") { value(0) } }
        other.rename(brand, "Hijacked").andExpect { status { isNotFound() } }
        other.delete(brand).andExpect { status { isNotFound() } }

        // Using the owner's account in the path: not a member (accountAccess)
        mockMvc.get(owner.brands) { header(HttpHeaders.AUTHORIZATION, "Bearer ${other.token}") }.andExpect { status { isForbidden() } }

        owner.list().andExpect { jsonPath("$[0].name") { value("AquaLife") } }
    }
}
