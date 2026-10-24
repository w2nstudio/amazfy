package com.amazfy.auth.adapter.inbound.web

import com.amazfy.RecordingAuthEmailSender
import com.amazfy.RecordingAuthEmailSender.Kind.PASSWORD_RESET
import com.amazfy.RecordingAuthEmailSender.Kind.VERIFICATION
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
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The auth endpoints over real HTTP handling, through every layer, with a real PostgreSQL. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthEndpointsTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var emails: RecordingAuthEmailSender

    @Autowired
    lateinit var jdbc: JdbcClient

    private val password = "correct horse battery"

    // Every MockMvc request comes from 127.0.0.1. A fake address per test keeps per-IP limits from adding up across tests.
    private val ip = "test-ip-${UUID.randomUUID()}"

    private fun uniqueEmail() = "john-${UUID.randomUUID()}@aqualife.com"

    private fun register(email: String = uniqueEmail()): MvcResult =
        mockMvc
            .post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$email","password":"$password","accountName":"AquaLife US","region":"NA"}"""
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
                content = """{"email":"not-an-email","password":"short","accountName":"Shop","region":"NA"}"""
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

    private fun verifyEmail(token: String) =
        mockMvc.post("/api/v1/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"$token"}"""
        }

    private fun isEmailVerified(email: String): Boolean =
        jdbc
            .sql("SELECT email_verified_at IS NOT NULL FROM users WHERE email = :e")
            .param("e", email)
            .query(Boolean::class.java)
            .single()

    @Test
    fun `sign-up sends a verification link that works exactly once`() {
        val email = uniqueEmail()
        register(email)
        val sent = emails.sentTo(email, VERIFICATION)
        assertEquals(1, sent.size)
        assertTrue(!isEmailVerified(email))

        verifyEmail(sent.single().token).andExpect { status { isNoContent() } }
        assertTrue(isEmailVerified(email))

        verifyEmail(sent.single().token).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }

    @Test
    fun `an unknown verification token is rejected`() {
        verifyEmail("made-up-token").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `resend needs a login, sends a new link and makes the old one stop working`() {
        val email = uniqueEmail()
        val token = register(email).accessToken()

        mockMvc.post("/api/v1/auth/resend-verification").andExpect { status { isUnauthorized() } }
        mockMvc
            .post("/api/v1/auth/resend-verification") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isNoContent() } }

        val (first, second) = emails.sentTo(email, VERIFICATION)
        verifyEmail(first.token).andExpect { status { isBadRequest() } }
        verifyEmail(second.token).andExpect { status { isNoContent() } }
    }

    @Test
    fun `resend sends nothing once the email is verified`() {
        val email = uniqueEmail()
        val token = register(email).accessToken()
        verifyEmail(emails.sentTo(email, VERIFICATION).single().token)

        mockMvc
            .post("/api/v1/auth/resend-verification") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isNoContent() } }
        assertEquals(1, emails.sentTo(email, VERIFICATION).size)
    }

    private fun forgotPassword(email: String) =
        mockMvc.post("/api/v1/auth/forgot-password") {
            with { it.apply { remoteAddr = ip } }
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email"}"""
        }

    private fun resetPassword(
        token: String,
        newPassword: String,
    ) = mockMvc.post("/api/v1/auth/reset-password") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"token":"$token","newPassword":"$newPassword"}"""
    }

    private fun login(
        email: String,
        password: String,
        fromIp: String = ip,
    ) = mockMvc.post("/api/v1/auth/login") {
        with { it.apply { remoteAddr = fromIp } }
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"$email","password":"$password"}"""
    }

    @Test
    fun `forgot-password answers 202 whether or not the email has an account`() {
        val unknown = uniqueEmail()
        forgotPassword(unknown).andExpect { status { isAccepted() } }
        assertTrue(emails.sentTo(unknown, PASSWORD_RESET).isEmpty())

        val email = uniqueEmail()
        register(email)
        forgotPassword(email.uppercase()).andExpect { status { isAccepted() } }
        assertEquals(1, emails.sentTo(email, PASSWORD_RESET).size)
    }

    @Test
    fun `reset-password sets the new password, logs out every session and works once`() {
        val email = uniqueEmail()
        val session = register(email).refreshCookie()
        forgotPassword(email)
        val token = emails.sentTo(email, PASSWORD_RESET).single().token

        resetPassword(token, "a brand new passphrase").andExpect { status { isNoContent() } }

        mockMvc.post("/api/v1/auth/refresh") { cookie(session) }.andExpect { status { isUnauthorized() } }
        login(email, password).andExpect { status { isUnauthorized() } }
        login(email, "a brand new passphrase").andExpect { status { isOk() } }
        assertTrue(isEmailVerified(email), "opening the reset link proves the email")
        resetPassword(token, "yet another passphrase").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a new reset email makes the previous link stop working`() {
        val email = uniqueEmail()
        register(email)
        forgotPassword(email)
        forgotPassword(email)

        val (first, second) = emails.sentTo(email, PASSWORD_RESET)
        resetPassword(first.token, "a brand new passphrase").andExpect { status { isBadRequest() } }
        resetPassword(second.token, "a brand new passphrase").andExpect { status { isNoContent() } }
    }

    @Test
    fun `a too-short new password is rejected and the link still works`() {
        val email = uniqueEmail()
        register(email)
        forgotPassword(email)
        val token = emails.sentTo(email, PASSWORD_RESET).single().token

        resetPassword(token, "short").andExpect { status { isBadRequest() } }
        resetPassword(token, "a brand new passphrase").andExpect { status { isNoContent() } }
    }

    @Test
    fun `after 5 wrong passwords the email is locked for a while, even with the right password`() {
        val email = uniqueEmail()
        register(email)
        repeat(5) { login(email, "wrong password!").andExpect { status { isUnauthorized() } } }

        login(email, password).andExpect {
            status { isTooManyRequests() }
            header { exists(HttpHeaders.RETRY_AFTER) }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value(containsString("Too many attempts")) }
        }
    }

    @Test
    fun `a successful login clears the email's failed attempts`() {
        val email = uniqueEmail()
        register(email)
        repeat(4) { login(email, "wrong password!") }
        login(email, password).andExpect { status { isOk() } }

        repeat(4) { login(email, "wrong password!") }
        login(email, password).andExpect { status { isOk() } }
    }

    @Test
    fun `after 20 failed logins from one IP that IP is refused, other IPs are not`() {
        val email = uniqueEmail()
        register(email)
        repeat(20) { login(uniqueEmail(), "wrong password!").andExpect { status { isUnauthorized() } } }

        login(email, password).andExpect { status { isTooManyRequests() } }
        login(email, password, fromIp = "another-ip-${UUID.randomUUID()}").andExpect { status { isOk() } }
    }

    @Test
    fun `forgot-password is limited to 3 emails per address per hour`() {
        val email = uniqueEmail()
        register(email)
        repeat(3) { forgotPassword(email).andExpect { status { isAccepted() } } }

        forgotPassword(email).andExpect { status { isTooManyRequests() } }
        assertEquals(3, emails.sentTo(email, PASSWORD_RESET).size)
    }

    @Test
    fun `resend-verification is limited to 3 per hour`() {
        val email = uniqueEmail()
        val token = register(email).accessToken()
        val resend = { mockMvc.post("/api/v1/auth/resend-verification") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") } }
        repeat(3) { resend().andExpect { status { isNoContent() } } }

        resend().andExpect { status { isTooManyRequests() } }
    }
}
