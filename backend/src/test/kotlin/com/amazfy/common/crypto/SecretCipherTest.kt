package com.amazfy.common.crypto

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretCipherTest {
    private fun key(fill: Int = 7) = Base64.getEncoder().encodeToString(ByteArray(32) { fill.toByte() })

    private val cipher = SecretCipher(key())

    @Test
    fun `a secret comes back only with its own context`() {
        val encrypted = cipher.encrypt("1//refresh-token", "account-a")
        assertEquals("1//refresh-token", cipher.decrypt(encrypted, "account-a"))
        assertNull(cipher.decrypt(encrypted, "account-b"))
    }

    @Test
    fun `the encrypted text shows nothing of the secret and differs every time`() {
        val first = cipher.encrypt("1//refresh-token", "account-a")
        assertFalse("refresh-token" in first)
        assertNotEquals(first, cipher.encrypt("1//refresh-token", "account-a"))
    }

    @Test
    fun `the encrypted text fits in a cookie`() {
        assertTrue(Regex("^[A-Za-z0-9._-]+$").matches(cipher.encrypt("a.b.c secret", "cookie")))
    }

    @Test
    fun `a changed value or another key gives nothing, without failing`() {
        val encrypted = cipher.encrypt("1//refresh-token", "account-a")
        val changed = encrypted.dropLast(2) + if (encrypted.endsWith("AA")) "BB" else "AA"
        assertNull(cipher.decrypt(changed, "account-a"))
        assertNull(SecretCipher(key(fill = 8)).decrypt(encrypted, "account-a"))
        listOf("", "v1.", "v1.AAAA", "v1.not base64!", "not ours").forEach { assertNull(cipher.decrypt(it, "account-a"), it) }
    }

    @Test
    fun `a key of the wrong size stops the application from starting`() {
        val tooShort = Base64.getEncoder().encodeToString(ByteArray(16))
        assertFailsWith<IllegalArgumentException> { SecretCipher(tooShort) }
        assertFailsWith<IllegalArgumentException> { SecretCipher("not base64!") }
    }
}
