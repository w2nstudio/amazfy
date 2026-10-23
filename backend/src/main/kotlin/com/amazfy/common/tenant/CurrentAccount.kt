package com.amazfy.common.tenant

import java.util.UUID

/**
 * The account (tenant) the current work is for. Hibernate reads it through AccountTenantResolver and then filters
 * every query on entities marked @TenantId to this account, and fills account_id on every insert.
 *
 * Set automatically for each request to /api/v1/accounts/{accountId}/... (AccountScopeInterceptor), after which
 * @PreAuthorize("@accountAccess...") checks the user is a member. Background jobs set it with [runAs].
 * Not set means [NONE]: an id no account has, so a forgotten setup shows nothing rather than everything.
 */
object CurrentAccount {
    val NONE: UUID = UUID(0, 0)

    private val current = ThreadLocal<UUID>()

    fun get(): UUID = current.get() ?: NONE

    /** Runs [block] for [accountId], then restores whatever was set before. */
    fun <T> runAs(
        accountId: UUID,
        block: () -> T,
    ): T {
        val previous = current.get()
        current.set(accountId)
        try {
            return block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    internal fun set(accountId: UUID) = current.set(accountId)

    internal fun clear() = current.remove()
}
