package com.amazfy.auth.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertTrue

/** The auth endpoints over real HTTP handling, through every layer, with a real PostgreSQL. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthEndpointsTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private val password = "correct horse battery"

    private fun uniqueEmail() = "john-${UUID.randomUUID()}@aqualife.com"

    private fun register(email: String = uniqueEmail()): MvcResult =
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","password":"$password","accountName":"AquaLife US"}"""
            }.andReturn()

    private fun MvcResult.refreshCookie(): Cookie = response.getCookie(AuthController.REFRESH_COOKIE)!!

    private fun MvcResult.accessToken(): String = Regex(""""accessToken":"([^"]+)"""").find(response.contentAsString)!!.groupValues[1]

    @Test
    fun `register returns 201, an access token and a secure refresh cookie`() {
        val result = register()

        assertTrue(result.response.status == 201)
        assertTrue(result.response.contentAsString.contains("\"tokenType\":\"Bearer\""))
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)!!
        listOf("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth").forEach {
            assertTrue(setCookie.contains(it), "cookie should have $it: $setCookie")
        }
        assertTrue(!result.response.contentAsString.contains(result.refreshCookie().value), "refresh token never in the body")
    }

    @Test
    fun `the access token unlocks protected endpoints`() {
        val token = register().accessToken()

        mockMvc.post("/api/v1/auth/logout-all").andExpect { status { isUnauthorized() } }
        mockMvc
            .post("/api/v1/auth/logout-all") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isNoContent() } }
        mockMvc
            .post("/api/v1/auth/logout-all") { header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value") }
            .andExpect {
                status { isUnauthorized() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }
    }

    @Test
    fun `login works with the right password and fails generically otherwise`() {
        val email = uniqueEmail()
        register(email)

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","password":"$password"}"""
            }.andExpect {
                status { isOk() }
                cookie { exists(AuthController.REFRESH_COOKIE) }
            }

        mockMvc
            .post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","password":"wrong password!"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Wrong email or password.") }
            }
    }

    @Test
    fun `refresh rotates the cookie and the old cookie stops working`() {
        val oldCookie = register().refreshCookie()

        val refreshed =
            mockMvc
                .post("/api/v1/auth/refresh") { cookie(oldCookie) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.accessToken") { exists() }
                }.andReturn()
        assertTrue(refreshed.refreshCookie().value != oldCookie.value)

        mockMvc.post("/api/v1/auth/refresh") { cookie(oldCookie) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `logout clears the cookie and ends the session`() {
        val cookie = register().refreshCookie()

        mockMvc
            .post("/api/v1/auth/logout") { cookie(cookie) }
            .andExpect {
                status { isNoContent() }
                header { string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")) }
            }
        mockMvc.post("/api/v1/auth/refresh") { cookie(cookie) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `invalid sign-up input returns 400 with field errors`() {
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"not-an-email","password":"short","accountName":"Shop"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.errors[*].field") { value(hasItem("password")) }
            }
    }

    @Test
    fun `signing up twice with the same email returns 409`() {
        val email = uniqueEmail()
        register(email)
        assertTrue(register(email.uppercase()).response.status == 409)
    }

    @Test
    fun `endpoints planned for later steps return 501`() {
        mockMvc
            .post("/api/v1/auth/forgot-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"a@b.com"}"""
            }.andExpect { status { isNotImplemented() } }
    }
}
