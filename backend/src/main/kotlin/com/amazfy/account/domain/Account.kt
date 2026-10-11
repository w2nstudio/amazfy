package com.amazfy.account.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.time.Instant
import java.util.UUID

/** One seller business = exactly one Amazon seller account (see CLAUDE.md, Domain naming). */
data class Account(
    val id: UUID,
    val name: AccountName,
    val createdAt: Instant,
)

/** An account name: trimmed, 1–100 characters. Invalid names can't be constructed. */
@JvmInline
value class AccountName private constructor(
    val value: String,
) {
    companion object {
        const val MAX_LENGTH = 100

        fun of(raw: String): AccountName {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) {
                throw InvalidAccountNameException("Account name must be 1–$MAX_LENGTH characters.")
            }
            return AccountName(trimmed)
        }
    }
}

class InvalidAccountNameException(
    message: String,
) : AppException(ErrorKind.BAD_REQUEST, message)
