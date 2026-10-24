package com.amazfy.account.adapter.outbound.persistence

import com.amazfy.account.application.AccountAccess
import com.amazfy.account.application.AccountSummary
import com.amazfy.account.application.Role
import com.amazfy.account.application.port.AccountRepository
import com.amazfy.account.domain.Account
import com.amazfy.account.domain.AccountName
import com.amazfy.common.amazon.Region
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.util.UUID

interface AccountJpaRepository : JpaRepository<AccountEntity, UUID>

interface MembershipJpaRepository : JpaRepository<MembershipEntity, MembershipId> {
    @Query("SELECT m, a FROM MembershipEntity m JOIN AccountEntity a ON a.id = m.id.accountId WHERE m.id.userId = :userId ORDER BY a.name")
    fun findWithAccountsByUserId(userId: UUID): List<Array<Any>>
}

/** Implements the account module's storage port with JPA. */
@Component
class AccountPersistenceAdapter(
    private val accountJpa: AccountJpaRepository,
    private val membershipJpa: MembershipJpaRepository,
) : AccountRepository {
    override fun createWithOwner(
        name: AccountName,
        region: Region,
        ownerUserId: UUID,
    ): Account {
        // saveAndFlush so the generated id and timestamps are available immediately
        val account = accountJpa.saveAndFlush(AccountEntity(name.value, region.name))
        membershipJpa.save(MembershipEntity(MembershipId(ownerUserId, account.id!!), Role.OWNER.name))
        return account.toDomain()
    }

    override fun findById(accountId: UUID): Account? = accountJpa.findById(accountId).orElse(null)?.toDomain()

    override fun rename(
        accountId: UUID,
        name: AccountName,
    ): Account {
        val account = accountJpa.findById(accountId).orElseThrow { NoSuchElementException("Account $accountId not found") }
        account.name = name.value
        return accountJpa.saveAndFlush(account).toDomain()
    }

    override fun findAccessibleBy(userId: UUID): List<AccountAccess> =
        membershipJpa.findWithAccountsByUserId(userId).map { row ->
            val membership = row[0] as MembershipEntity
            val account = row[1] as AccountEntity
            AccountAccess(account.toSummary(), Role.valueOf(membership.role))
        }

    override fun findRole(
        userId: UUID,
        accountId: UUID,
    ): Role? =
        membershipJpa
            .findById(MembershipId(userId, accountId))
            .orElse(null)
            ?.let { Role.valueOf(it.role) }
}

private fun AccountEntity.toDomain() =
    Account(id = id!!, name = AccountName.of(name), region = Region.valueOf(region), createdAt = createdAt!!)

private fun AccountEntity.toSummary() = AccountSummary(id = id!!, name = name, region = Region.valueOf(region), createdAt = createdAt!!)
