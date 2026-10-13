package com.amazfy.account.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

/** Me and Accounts endpoints, and the rule that a user only ever reaches accounts they are a member of. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountEndpointsTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    /** A signed-up seller: their access token and the id of the account created at sign-up. */
    private data class Seller(
        val email: String,
        val token: String,
        val accountId: String,
    )

    private fun signUp(accountName: String): Seller {
        val email = "seller-${UUID.randomUUID()}@aqualife.com"
        val body =
            mockMvc
                .post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"correct horse battery","accountName":"$accountName"}"""
                }.andReturn()
                .response.contentAsString
        val token = Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
        val me =
            mockMvc
                .get("/api/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
        val accountId = Regex(""""account":\{"id":"([^"]+)"""").find(me)!!.groupValues[1]
        return Seller(email, token, accountId)
    }

    private fun Seller.auth(): String = "Bearer $token"

    @Test
    fun `me shows the user and the account created at sign-up, as owner`() {
        val seller = signUp("AquaLife US")

        mockMvc.get("/api/v1/me") { header(HttpHeaders.AUTHORIZATION, seller.auth()) }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(seller.email) }
            jsonPath("$.emailVerified") { value(false) }
            jsonPath("$.accounts.length()") { value(1) }
            jsonPath("$.accounts[0].account.name") { value("AquaLife US") }
            jsonPath("$.accounts[0].role") { value("OWNER") }
        }
    }

    @Test
    fun `me and accounts need a login`() {
        mockMvc.get("/api/v1/me").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/accounts/${UUID.randomUUID()}").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `add account creates a second account the user owns`() {
        val seller = signUp("AquaLife US")

        mockMvc
            .post("/api/v1/accounts") {
                header(HttpHeaders.AUTHORIZATION, seller.auth())
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"AquaLife EU"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("AquaLife EU") }
            }

        mockMvc.get("/api/v1/me") { header(HttpHeaders.AUTHORIZATION, seller.auth()) }.andExpect {
            jsonPath("$.accounts[*].account.name") { value(containsInAnyOrder("AquaLife US", "AquaLife EU")) }
        }
    }

    @Test
    fun `an owner can read and rename their account`() {
        val seller = signUp("AquaLife US")

        mockMvc.get("/api/v1/accounts/${seller.accountId}") { header(HttpHeaders.AUTHORIZATION, seller.auth()) }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("AquaLife US") }
        }
        mockMvc
            .patch("/api/v1/accounts/${seller.accountId}") {
                header(HttpHeaders.AUTHORIZATION, seller.auth())
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"AquaLife North America"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("AquaLife North America") }
            }
    }

    @Test
    fun `nobody can read or rename an account they are not a member of`() {
        val owner = signUp("AquaLife US")
        val stranger = signUp("Other Shop")

        mockMvc.get("/api/v1/accounts/${owner.accountId}") { header(HttpHeaders.AUTHORIZATION, stranger.auth()) }.andExpect {
            status { isForbidden() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        }
        mockMvc
            .patch("/api/v1/accounts/${owner.accountId}") {
                header(HttpHeaders.AUTHORIZATION, stranger.auth())
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Hijacked"}"""
            }.andExpect { status { isForbidden() } }

        // Unchanged for the owner
        mockMvc.get("/api/v1/accounts/${owner.accountId}") { header(HttpHeaders.AUTHORIZATION, owner.auth()) }.andExpect {
            jsonPath("$.name") { value("AquaLife US") }
        }
    }

    @Test
    fun `an account that doesn't exist is also 403, so ids can't be probed`() {
        val seller = signUp("AquaLife US")
        mockMvc.get("/api/v1/accounts/${UUID.randomUUID()}") { header(HttpHeaders.AUTHORIZATION, seller.auth()) }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `an empty account name is rejected`() {
        val seller = signUp("AquaLife US")
        mockMvc
            .patch("/api/v1/accounts/${seller.accountId}") {
                header(HttpHeaders.AUTHORIZATION, seller.auth())
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"   "}"""
            }.andExpect { status { isBadRequest() } }
    }
}
