package com.amazfy.account.application

import com.amazfy.account.application.port.AccountRepository
import com.amazfy.account.domain.Account
import com.amazfy.account.domain.AccountAccess
import com.amazfy.account.domain.AccountName
import com.amazfy.account.domain.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The account module's public API: other modules (e.g. auth at sign-up) and the web adapter use this.
 * Light module: one service for all account operations instead of one class per use case.
 */
@Service
@Transactional(readOnly = true)
class AccountService(
    private val accounts: AccountRepository,
) {
    /** Creates an account; the user becomes its owner. Throws InvalidAccountNameException for a bad name. */
    @Transactional
    fun createAccount(
        name: String,
        ownerUserId: UUID,
    ): Account = accounts.createWithOwner(AccountName.of(name), ownerUserId)

    /** Renames an account. Callers must check that the user may manage it. */
    @Transactional
    fun renameAccount(
        accountId: UUID,
        name: String,
    ): Account = accounts.rename(accountId, AccountName.of(name))

    fun findAccount(accountId: UUID): Account? = accounts.findById(accountId)

    /** Accounts the user can access, with their role in each. */
    fun accessibleAccounts(userId: UUID): List<AccountAccess> = accounts.findAccessibleBy(userId)

    /** The user's role in the account, or null if they have no access. */
    fun roleOf(
        userId: UUID,
        accountId: UUID,
    ): Role? = accounts.findRole(userId, accountId)
}
