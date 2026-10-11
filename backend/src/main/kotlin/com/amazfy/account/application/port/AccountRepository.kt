package com.amazfy.account.application.port

import com.amazfy.account.domain.Account
import com.amazfy.account.domain.AccountAccess
import com.amazfy.account.domain.AccountName
import com.amazfy.account.domain.Role
import java.util.UUID

/** Storage the account module needs. Implemented by the persistence adapter. */
interface AccountRepository {
    /** Creates an account and gives [ownerUserId] the OWNER role in it. */
    fun createWithOwner(
        name: AccountName,
        ownerUserId: UUID,
    ): Account

    fun findById(accountId: UUID): Account?

    fun rename(
        accountId: UUID,
        name: AccountName,
    ): Account

    fun findAccessibleBy(userId: UUID): List<AccountAccess>

    fun findRole(
        userId: UUID,
        accountId: UUID,
    ): Role?
}
