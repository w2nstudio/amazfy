package com.amazfy.account.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `only owners and editors can edit, only owners can manage`() {
        assertTrue(Role.OWNER.canEdit && Role.OWNER.canManage)
        assertTrue(Role.EDITOR.canEdit)
        assertFalse(Role.EDITOR.canManage)
        assertFalse(Role.VIEWER.canEdit || Role.VIEWER.canManage)
    }
}
