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

/** The product endpoints over HTTP, the brand product counts, and that brands with products stay. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class ProductEndpointsTest {
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
                            """{"email":"products-${UUID.randomUUID()}@aqualife.com","password":"correct horse battery",""" +
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
        val products get() = "/api/v1/accounts/$accountId/products"

        fun brand(name: String): String =
            idOf(
                mockMvc.post(brands) {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"$name"}"""
                },
            )

        fun listBrands(): ResultActionsDsl = mockMvc.get(brands) { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

        fun deleteBrand(brandId: String): ResultActionsDsl =
            mockMvc.delete("$brands/$brandId") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

        fun list(brandId: String? = null): ResultActionsDsl =
            mockMvc.get(products) {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                brandId?.let { param("brandId", it) }
            }

        fun create(
            brandId: String,
            asin: String,
            name: String,
            marketplace: String = "US",
        ): ResultActionsDsl =
            mockMvc.post(products) {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"brandId":"$brandId","asin":"$asin","marketplace":"$marketplace","name":"$name"}"""
            }

        fun createId(
            brandId: String,
            asin: String,
            name: String,
        ): String = idOf(create(brandId, asin, name))

        fun update(
            productId: String,
            json: String,
        ): ResultActionsDsl =
            mockMvc.patch("$products/$productId") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json
            }

        fun delete(productId: String): ResultActionsDsl =
            mockMvc.delete("$products/$productId") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

        private fun idOf(result: ResultActionsDsl): String =
            Regex(""""id":"([^"]+)"""").find(result.andReturn().response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `a product is added with a cleaned-up ASIN and a link to its Amazon listing`() {
        val seller = Seller()
        val aquaLife = seller.brand("AquaLife")

        seller.create(aquaLife, " b0aaa11111 ", " Aquarium filter 40L ", marketplace = "UK").andExpect {
            status { isCreated() }
            jsonPath("$.brandId") { value(aquaLife) }
            jsonPath("$.asin") { value("B0AAA11111") }
            jsonPath("$.marketplace") { value("UK") }
            jsonPath("$.name") { value("Aquarium filter 40L") }
            jsonPath("$.amazonUrl") { value("https://www.amazon.co.uk/dp/B0AAA11111") }
        }
    }

    @Test
    fun `products are listed by brand, then name, and can be filtered to one brand`() {
        val seller = Seller()
        val clearTank = seller.brand("ClearTank")
        val aquaLife = seller.brand("AquaLife")
        seller.create(clearTank, "B0CCC11111", "Glass cleaner")
        seller.create(aquaLife, "B0AAA22222", "Heater 100W")
        seller.create(aquaLife, "B0AAA11111", "Filter 40L")

        seller.list().andExpect { jsonPath("$[*].name") { value(contains("Filter 40L", "Heater 100W", "Glass cleaner")) } }
        seller.list(brandId = clearTank).andExpect { jsonPath("$[*].name") { value(contains("Glass cleaner")) } }
    }

    @Test
    fun `brands show how many products they have`() {
        val seller = Seller()
        val aquaLife = seller.brand("AquaLife")
        seller.brand("ClearTank")
        seller.create(aquaLife, "B0AAA11111", "Filter 40L")
        seller.create(aquaLife, "B0AAA22222", "Heater 100W")

        seller.listBrands().andExpect {
            jsonPath("$[*].name") { value(contains("AquaLife", "ClearTank")) }
            jsonPath("$[*].productCount") { value(contains(2, 0)) }
        }
    }

    @Test
    fun `an ASIN is listed once per marketplace`() {
        val seller = Seller()
        val aquaLife = seller.brand("AquaLife")
        seller.create(aquaLife, "B0AAA11111", "Filter 40L")

        seller.create(aquaLife, "b0aaa11111", "Filter again").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("This ASIN is already in your catalog for this marketplace.") }
        }
        // The same listing on another Amazon site is a separate product
        seller.create(aquaLife, "B0AAA11111", "Filter 40L (Germany)", marketplace = "DE").andExpect { status { isCreated() } }
    }

    @Test
    fun `a wrong ASIN, name or marketplace is rejected`() {
        val seller = Seller()
        val aquaLife = seller.brand("AquaLife")

        seller.create(aquaLife, "B0AAA1111", "Filter").andExpect { status { isBadRequest() } }
        seller.create(aquaLife, "B0AAA11111", "   ").andExpect { status { isBadRequest() } }
        seller.create(aquaLife, "B0AAA11111", "Filter", marketplace = "GB").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a product is renamed or moved to another brand, keeping its ASIN and marketplace`() {
        val seller = Seller()
        val aquaLife = seller.brand("AquaLife")
        val clearTank = seller.brand("ClearTank")
        val filter = seller.createId(aquaLife, "B0AAA11111", "Filter")

        seller.update(filter, """{"name":"Filter 40L"}""").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Filter 40L") }
            jsonPath("$.brandId") { value(aquaLife) }
        }
        seller.update(filter, """{"brandId":"$clearTank"}""").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Filter 40L") }
            jsonPath("$.brandId") { value(clearTank) }
            jsonPath("$.asin") { value("B0AAA11111") }
            jsonPath("$.marketplace") { value("US") }
        }
    }

    @Test
    fun `a brand with products can't be removed until its products are gone`() {
        val seller = Seller()
        val aquaLife = seller.brand("AquaLife")
        val filter = seller.createId(aquaLife, "B0AAA11111", "Filter 40L")

        seller.deleteBrand(aquaLife).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("This brand still has products. Move them to another brand or remove them first.") }
        }

        seller.delete(filter).andExpect { status { isNoContent() } }
        seller.delete(filter).andExpect { status { isNotFound() } }
        seller.deleteBrand(aquaLife).andExpect { status { isNoContent() } }
    }

    @Test
    fun `another seller's brands and products can't be used or reached`() {
        val owner = Seller()
        val ownerBrand = owner.brand("AquaLife")
        val ownerProduct = owner.createId(ownerBrand, "B0AAA11111", "Filter 40L")
        val other = Seller()
        val otherBrand = other.brand("ClearTank")
        val otherProduct = other.createId(otherBrand, "B0CCC11111", "Glass cleaner")

        // A product can't be put under another seller's brand: from this account, that brand doesn't exist
        other.create(ownerBrand, "B0CCC22222", "Sneaky").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("Choose one of your brands.") }
        }
        other.update(otherProduct, """{"brandId":"$ownerBrand"}""").andExpect { status { isBadRequest() } }

        // Another seller's product doesn't exist through one's own account
        other.update(ownerProduct, """{"name":"Hijacked"}""").andExpect { status { isNotFound() } }
        other.delete(ownerProduct).andExpect { status { isNotFound() } }
        other.list().andExpect { jsonPath("$[*].name") { value(contains("Glass cleaner")) } }

        // The same ASIN in another seller's catalog is no clash: each account has its own catalog
        other.create(otherBrand, "B0AAA11111", "Filter (their listing)").andExpect { status { isCreated() } }

        owner.list().andExpect { jsonPath("$[*].name") { value(contains("Filter 40L")) } }
    }
}
