package com.amazfy.auth.application

import com.amazfy.auth.application.port.GoogleIdentityProvider
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
    }

    @Test
    fun `without a Google client, sign-in is not available (501)`() {
        val error = assertFailsWith<NotImplementedYetException> { GoogleLoginService(FakeGoogle(isConfigured = false)).start() }
        assertEquals("Google login is not available yet.", error.message)
    }

    @Test
    fun `with a Google client, start returns the sign-in address and its secrets`() {
        val start = GoogleLoginService(FakeGoogle(isConfigured = true)).start()
        assertEquals("https://google.example/auth?state=${start.state}", start.redirectUrl)
        assertEquals("GoogleLoginStart(****)", start.toString())
    }
}
