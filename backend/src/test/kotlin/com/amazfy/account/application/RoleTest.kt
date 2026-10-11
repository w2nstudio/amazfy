package com.amazfy.account.application

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoleTest {
    @Test
    fun `only owners and editors can edit, only owners can manage`() {
        assertTrue(Role.OWNER.canEdit && Role.OWNER.canManage)
        assertTrue(Role.EDITOR.canEdit)
        assertFalse(Role.EDITOR.canManage)
        assertFalse(Role.VIEWER.canEdit || Role.VIEWER.canManage)
    }
}
