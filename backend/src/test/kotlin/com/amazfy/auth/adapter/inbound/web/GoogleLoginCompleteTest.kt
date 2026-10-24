package com.amazfy.auth.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import com.amazfy.auth.application.port.GoogleIdentityProvider
import com.amazfy.auth.domain.GoogleIdentity
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The whole Google sign-in through HTTP, with Google replaced by a fake that "verifies" whoever the test chooses.
 * (The real token checks are tested in GoogleOidcProviderTest.)
 */
@Import(TestcontainersConfiguration::class, GoogleLoginCompleteTest.FakeGoogleConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
class GoogleLoginCompleteTest {
    /** Google stand-in: the next sign-in "is" [nextIdentity]. */
    class FakeGoogle : GoogleIdentityProvider {
        var nextIdentity: GoogleIdentity? = null

        override val isConfigured = true

        override fun authorizationUrl(
            state: String,
            codeChallenge: String,
            nonce: String,
        ) = "https://google.example/auth"

        override fun exchangeCode(
            code: String,
            codeVerifier: String,
            nonce: String,
        ) = nextIdentity!!
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FakeGoogleConfig {
        @Bean
        @Primary
        fun fakeGoogle() = FakeGoogle()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var google: FakeGoogle

    /** Start → Google (fake) → callback, as the browser would; returns the callback's response. */
    private fun signInWithGoogle(email: String): org.springframework.mock.web.MockHttpServletResponse {
        google.nextIdentity = GoogleIdentity(subject = "google-$email", email = email, emailVerified = true, name = "John")
        val roundTrip =
            mockMvc
                .get("/api/v1/auth/google")
                .andReturn()
                .response
                .getCookie(AuthController.GOOGLE_LOGIN_COOKIE)!!
        val state = roundTrip.value.split(".")[0]
        return mockMvc
            .get("/api/v1/auth/google/callback?state=$state&code=the-code") { cookie(roundTrip) }
            .andReturn()
            .response
    }

    /** What the frontend does after landing: exchange the refresh cookie for an access token. */
    private fun accessTokenFrom(refreshCookie: Cookie): String {
        val body =
            mockMvc
                .post("/api/v1/auth/refresh") { cookie(refreshCookie) }
                .andReturn()
                .response.contentAsString
        return Regex(""""accessToken":"([^"]+)"""").find(body)!!.groupValues[1]
    }

    @Test
    fun `a new Google user is logged in and sent to onboarding, then creates their account`() {
        val email = "new-${UUID.randomUUID()}@gmail.com"

        val callback = signInWithGoogle(email)

        assertEquals(302, callback.status)
        assertEquals("http://localhost:5173/onboarding", callback.getHeader(HttpHeaders.LOCATION))
        val token = accessTokenFrom(callback.getCookie(AuthController.REFRESH_COOKIE)!!)
        mockMvc.get("/api/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            jsonPath("$.email") { value(email) }
            jsonPath("$.emailVerified") { value(true) }
            jsonPath("$.accounts.length()") { value(0) }
        }

        // Onboarding: "What's your business called?"
        mockMvc
            .post("/api/v1/accounts") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"AquaLife US","region":"NA"}"""
            }.andExpect { status { isCreated() } }

        // Next time: straight to the home page
        assertEquals("http://localhost:5173/", signInWithGoogle(email).getHeader(HttpHeaders.LOCATION))
    }

    @Test
    fun `the round-trip cookie is deleted and the refresh cookie set in the same response`() {
        val callback = signInWithGoogle("cookies-${UUID.randomUUID()}@gmail.com")

        val setCookies = callback.getHeaders(HttpHeaders.SET_COOKIE)
        assertEquals(1, setCookies.count { it.startsWith("${AuthController.GOOGLE_LOGIN_COOKIE}=;") && it.contains("Max-Age=0") })
        assertEquals(1, setCookies.count { it.startsWith("${AuthController.REFRESH_COOKIE}=") && it.contains("HttpOnly") })
    }
}
