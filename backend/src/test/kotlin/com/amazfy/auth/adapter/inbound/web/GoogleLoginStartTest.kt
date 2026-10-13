package com.amazfy.auth.adapter.inbound.web

import com.amazfy.TestcontainersConfiguration
import com.amazfy.auth.domain.SecretTokenCodec
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GET /auth/google with a (fake) Google client configured: redirect to Google plus the round-trip cookie. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "amazfy.google.client-id=test-client-id.apps.googleusercontent.com",
        "amazfy.google.redirect-uri=http://localhost:8080/api/v1/auth/google/callback",
    ],
)
@AutoConfigureMockMvc
class GoogleLoginStartTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `start redirects to Google's sign-in page with state, nonce and PKCE challenge`() {
        val response =
            mockMvc
                .get("/api/v1/auth/google")
                .andExpect { status { isFound() } }
                .andReturn()
                .response

        val location = UriComponentsBuilder.fromUriString(response.getHeader(HttpHeaders.LOCATION)!!).build()
        assertEquals("accounts.google.com", location.host)
        val query = location.queryParams.toSingleValueMap()
        assertEquals("test-client-id.apps.googleusercontent.com", query["client_id"])
        assertEquals("http://localhost:8080/api/v1/auth/google/callback", query["redirect_uri"])
        assertEquals("code", query["response_type"])
        assertEquals("openid%20email%20profile", query["scope"])
        assertEquals("S256", query["code_challenge_method"])

        // The cookie holds what the callback will check: the same state and nonce, and the verifier behind the challenge
        val (state, codeVerifier, nonce) = response.getCookie(AuthController.GOOGLE_LOGIN_COOKIE)!!.value.split(".")
        assertEquals(state, query["state"])
        assertEquals(nonce, query["nonce"])
        assertEquals(SecretTokenCodec.hash(codeVerifier), query["code_challenge"])

        val setCookie = response.getHeader(HttpHeaders.SET_COOKIE)!!
        listOf("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth/google", "Max-Age=600").forEach {
            assertTrue(setCookie.contains(it), "cookie should have $it: $setCookie")
        }
    }

    @Test
    fun `every start uses new random values`() {
        val first =
            mockMvc
                .get("/api/v1/auth/google")
                .andReturn()
                .response
                .getCookie(AuthController.GOOGLE_LOGIN_COOKIE)!!
                .value
        val second =
            mockMvc
                .get("/api/v1/auth/google")
                .andReturn()
                .response
                .getCookie(AuthController.GOOGLE_LOGIN_COOKIE)!!
                .value
        assertTrue(first != second)
    }
}
