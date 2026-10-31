package com.amazfy.common.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts secrets Amazfy has to read back later, such as a seller's Google Ads refresh token. (Secrets it only has to
 * recognise, like its own refresh tokens, are hashed instead.) AES-256-GCM: anyone without the key can neither read a
 * value nor change it unnoticed.
 *
 * [encrypt]'s context says what the value is for, e.g. which seller account's token. It isn't stored; [decrypt] must be
 * given the same one, so a value copied to another account or another purpose can't be decrypted.
 */
@Component
class SecretCipher(
    @Value("\${amazfy.crypto.secret-key}") base64Key: String,
) {
    private val key = SecretKeySpec(decodeKey(base64Key), "AES")
    private val random = SecureRandom()

    /** A letters-and-digits text, safe in a cookie: the format version, then the random IV and the encrypted value. */
    fun encrypt(
        secret: String,
        context: String,
    ): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes) // new for every value: GCM is unsafe if one is reused
        val cipher = cipher(Cipher.ENCRYPT_MODE, iv, context)
        val encrypted = cipher.doFinal(secret.toByteArray())
        return VERSION + Base64.getUrlEncoder().withoutPadding().encodeToString(iv + encrypted)
    }

    /** The secret, or null when the value was changed, was encrypted for another context or with another key. */
    fun decrypt(
        encrypted: String,
        context: String,
    ): String? {
        if (!encrypted.startsWith(VERSION)) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(encrypted.removePrefix(VERSION))
            if (bytes.size < IV_BYTES) return null
            val cipher = cipher(Cipher.DECRYPT_MODE, bytes.copyOf(IV_BYTES), context)
            cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null // not valid Base64
        }
    }

    private fun cipher(
        mode: Int,
        iv: ByteArray,
        context: String,
    ) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, key, GCMParameterSpec(TAG_BITS, iv))
        updateAAD(context.toByteArray())
    }

    private companion object {
        /** Tells a later key or algorithm (M6, secrets manager) which values were written how. */
        const val VERSION = "v1."
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BYTES = 32

        /** Fails at startup rather than at the first seller who connects. */
        fun decodeKey(base64Key: String): ByteArray {
            val bytes =
                try {
                    Base64.getDecoder().decode(base64Key)
                } catch (_: IllegalArgumentException) {
                    throw IllegalArgumentException("SECRET_ENCRYPTION_KEY must be Base64")
                }
            require(bytes.size == KEY_BYTES) { "SECRET_ENCRYPTION_KEY must be $KEY_BYTES bytes (openssl rand -base64 32)" }
            return bytes
        }
    }
}
