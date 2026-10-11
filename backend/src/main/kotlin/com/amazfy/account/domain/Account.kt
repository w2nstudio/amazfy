package com.amazfy.account.domain

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

/** What a user may do in an account. Phase 1 creates OWNER only. */
enum class Role {
    OWNER,
    EDITOR,
    VIEWER,
    ;

    val canEdit: Boolean get() = this == OWNER || this == EDITOR
    val canManage: Boolean get() = this == OWNER
}

/** An account a user can access, with their role in it. */
data class AccountAccess(
    val account: Account,
    val role: Role,
)

class InvalidAccountNameException(
    message: String,
) : RuntimeException(message)
