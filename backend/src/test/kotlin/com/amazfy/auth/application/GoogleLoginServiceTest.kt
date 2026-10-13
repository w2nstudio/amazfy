package com.amazfy.auth.application

import com.amazfy.auth.application.port.GoogleIdentityProvider
import com.amazfy.auth.domain.GoogleIdentity
import com.amazfy.auth.domain.GoogleLoginFailedException
import com.amazfy.common.error.NotImplementedYetException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoogleLoginServiceTest {
    private class FakeGoogle(
        override val isConfigured: Boolean,
    ) : GoogleIdentityProvider {
        override fun authorizationUrl(
            state: String,
            codeChallenge: String,
            nonce: String,
        ) = "https://google.example/auth?state=$state"

        var exchanged: Triple<String, String, String>? = null

        override fun exchangeCode(
            code: String,
            codeVerifier: String,
            nonce: String,
        ): GoogleIdentity {
            exchanged = Triple(code, codeVerifier, nonce)
            return GoogleIdentity(subject = "109876543210", email = "john@gmail.com", emailVerified = true, name = "John")
        }
    }

    private val google = FakeGoogle(isConfigured = true)
    private val service = GoogleLoginService(google)
    private val roundTrip = GoogleRoundTrip(state = "the-state", codeVerifier = "the-verifier", nonce = "the-nonce")

    @Test
    fun `complete exchanges the code with the verifier and nonce from the round trip`() {
        val identity = service.complete(code = "the-code", error = null, state = "the-state", roundTrip = roundTrip)

        assertEquals("109876543210", identity.subject)
        assertEquals(Triple("the-code", "the-verifier", "the-nonce"), google.exchanged)
    }

    @Test
    fun `a state that doesn't match the cookie is rejected before calling Google`() {
        assertFailsWith<GoogleLoginFailedException> {
            service.complete(code = "the-code", error = null, state = "forged-state", roundTrip = roundTrip)
        }
        assertEquals(null, google.exchanged)
    }

    @Test
    fun `a missing cookie (expired or started elsewhere) is rejected`() {
        assertFailsWith<GoogleLoginFailedException> {
            service.complete(
                code = "the-code",
                error = null,
                state = "the-state",
                roundTrip = null,
            )
        }
    }

    @Test
    fun `pressing Cancel on Google's page is reported as cancelled, not as an error`() {
        assertFailsWith<GoogleSignInCancelledException> {
            service.complete(code = null, error = "access_denied", state = "the-state", roundTrip = roundTrip)
        }
    }

    @Test
    fun `any other error from Google fails the sign-in`() {
        assertFailsWith<GoogleLoginFailedException> {
            service.complete(code = null, error = "server_error", state = "the-state", roundTrip = roundTrip)
        }
    }

    @Test
    fun `without a Google client, sign-in is not available (501)`() {
        val error = assertFailsWith<NotImplementedYetException> { GoogleLoginService(FakeGoogle(isConfigured = false)).start() }
        assertEquals("Google login is not available yet.", error.message)
    }

    @Test
    fun `with a Google client, start returns the sign-in address and its secrets`() {
        val start = GoogleLoginService(FakeGoogle(isConfigured = true)).start()
        assertEquals("https://google.example/auth?state=${start.roundTrip.state}", start.redirectUrl)
        assertEquals("GoogleRoundTrip(****)", start.roundTrip.toString())
    }
}
