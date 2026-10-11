package com.amazfy.auth.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class UserRulesTest {
    @Test
    fun `email is trimmed and lower-cased`() {
        assertEquals("john@aqualife.com", Email.of("  John@AquaLife.com ").value)
    }

    @Test
    fun `malformed emails are rejected`() {
        listOf("", "john", "john@", "@aqualife.com", "john@aqualife", "jo hn@aqualife.com").forEach {
            assertFailsWith<InvalidEmailException>("should reject '$it'") { Email.of(it) }
        }
    }

    @Test
    fun `password must be 10 to 128 characters`() {
        assertFailsWith<WeakPasswordException> { RawPassword.of("short") }
        assertFailsWith<WeakPasswordException> { RawPassword.of("x".repeat(129)) }
        assertFailsWith<WeakPasswordException> { RawPassword.of(" ".repeat(12)) }
        RawPassword.of("long enough pass")
    }

    @Test
    fun `password never appears in toString`() {
        assertFalse(RawPassword.of("super secret password").toString().contains("secret"))
    }
}
