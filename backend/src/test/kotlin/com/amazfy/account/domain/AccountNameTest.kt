package com.amazfy.account.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountNameTest {
    @Test
    fun `name is trimmed`() {
        assertEquals("AquaLife US", AccountName.of("  AquaLife US  ").value)
    }

    @Test
    fun `blank name is rejected`() {
        assertFailsWith<InvalidAccountNameException> { AccountName.of("   ") }
    }

    @Test
    fun `name longer than 100 characters is rejected`() {
        assertEquals(100, AccountName.of("a".repeat(100)).value.length)
        assertFailsWith<InvalidAccountNameException> { AccountName.of("a".repeat(101)) }
    }
}
