package com.amazfy.account.application

import java.time.Instant
import java.util.UUID

// Types the account module's API (AccountService) returns to other modules and to its web adapter.
// The domain classes (Account, AccountName) stay inside the module.

data class AccountSummary(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
)

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
    val account: AccountSummary,
    val role: Role,
)
