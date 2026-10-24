package com.amazfy.account.application

import com.amazfy.account.application.port.AccountRepository
import com.amazfy.account.domain.Account
import com.amazfy.account.domain.AccountName
import com.amazfy.common.amazon.Region
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The account module's public API: other modules (e.g. auth at sign-up) and the web adapter use this.
 * Returns API types (AccountSummary, AccountAccess, Role), never domain classes.
 * Light module: one service for all account operations instead of one class per use case.
 */
@Service
@Transactional(readOnly = true)
class AccountService(
    private val accounts: AccountRepository,
) {
    /**
     * Creates an account in an Amazon region; the user becomes its owner. Throws InvalidAccountNameException for a bad
     * name. The region can't change later.
     */
    @Transactional
    fun createAccount(
        name: String,
        region: Region,
        ownerUserId: UUID,
    ): AccountSummary = accounts.createWithOwner(AccountName.of(name), region, ownerUserId).toSummary()

    /** Renames an account (the region stays). Callers must check that the user may manage it. */
    @Transactional
    fun renameAccount(
        accountId: UUID,
        name: String,
    ): AccountSummary = accounts.rename(accountId, AccountName.of(name)).toSummary()

    fun findAccount(accountId: UUID): AccountSummary? = accounts.findById(accountId)?.toSummary()

    /** Accounts the user can access, with their role in each, sorted by name. */
    fun accessibleAccounts(userId: UUID): List<AccountAccess> = accounts.findAccessibleBy(userId)

    /** The user's role in the account, or null if they have no access. */
    fun roleOf(
        userId: UUID,
        accountId: UUID,
    ): Role? = accounts.findRole(userId, accountId)
}

internal fun Account.toSummary() = AccountSummary(id = id, name = name.value, region = region, createdAt = createdAt)
