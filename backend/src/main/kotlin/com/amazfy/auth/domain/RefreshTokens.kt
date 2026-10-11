package com.amazfy.auth.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Creates refresh tokens and their hashes. The raw token goes to the browser (cookie); only the hash is stored. */
object RefreshTokenCodec {
    private val random = SecureRandom()
    private const val TOKEN_BYTES = 32 // 256 bits of randomness: impossible to guess

    fun newToken(): String =
        ByteArray(TOKEN_BYTES).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    /** SHA-256 is enough here (unlike passwords): the token is long and random, so it can't be brute-forced. */
    fun hash(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}

/** Missing, unknown, expired, revoked or reused refresh token: the user has to log in again. */
class InvalidRefreshTokenException : AppException(ErrorKind.UNAUTHORIZED, "Your session has expired. Please log in again.")
