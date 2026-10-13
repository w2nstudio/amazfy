package com.amazfy.account.application

import com.amazfy.common.security.currentUserId
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The one place that decides who may do what in an account (requirements section 3, Authorization).
 * Endpoints declare what they need with Spring method security, for example
 * `@PreAuthorize("@accountAccess.canEdit(#accountId)")`. A false answer becomes 403 (GlobalExceptionHandler).
 * No membership means no access at all, whatever the endpoint.
 */
@Component("accountAccess")
class AccountAccessGuard(
    private val accountService: AccountService,
) {
    /** Any member: read the account's data. */
    fun canView(accountId: UUID): Boolean = roleOf(accountId) != null

    /** Owners and editors: change the account's data (brands, products, campaigns). */
    fun canEdit(accountId: UUID): Boolean = roleOf(accountId)?.canEdit == true

    /** Owners only: change the account itself (name, connections, members). */
    fun canManage(accountId: UUID): Boolean = roleOf(accountId)?.canManage == true

    private fun roleOf(accountId: UUID): Role? = accountService.roleOf(currentUserId(), accountId)
}
