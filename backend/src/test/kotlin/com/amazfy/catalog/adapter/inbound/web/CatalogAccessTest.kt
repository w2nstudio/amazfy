package com.amazfy.catalog.adapter.inbound.web

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
import java.util.UUID

/** The catalog endpoints' access rules work before the endpoints themselves are built (501 for members until then). */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogAccessTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    /** Signs up a seller; returns their access token and the id of their account. */
    private fun signUp(): Pair<String, String> {
        val email = "catalog-${UUID.randomUUID()}@aqualife.com"
        val body =
            mockMvc
                .post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"correct horse battery","accountName":"AquaLife US"}"""
                }.andReturn()
                .response.contentAsString
        val token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
        val me =
            mockMvc
                .get("/api/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
        return token to Regex(""""account":\{"id":"([^"]+)"""").find(me)!!.groupValues[1]
    }

    @Test
    fun `a member reaches the catalog endpoints (not built yet, 501)`() {
        val (token, accountId) = signUp()
        mockMvc.get("/api/v1/accounts/$accountId/brands") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isNotImplemented() }
        }
    }

    @Test
    fun `someone else's catalog is forbidden`() {
        val (_, ownerAccountId) = signUp()
        val (strangerToken, _) = signUp()

        mockMvc
            .get("/api/v1/accounts/$ownerAccountId/products") { header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/accounts/$ownerAccountId/brands") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Hijacked"}"""
            }.andExpect { status { isForbidden() } }
    }
}
