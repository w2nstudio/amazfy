package com.amazfy.account.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

// JPA classes for the memberships table (V3__accounts_memberships.sql), used only inside AccountPersistenceAdapter.

@Embeddable
class MembershipId(
    val userId: UUID,
    val accountId: UUID,
) : java.io.Serializable {
    override fun equals(other: Any?) = other is MembershipId && other.userId == userId && other.accountId == accountId

    override fun hashCode() = 31 * userId.hashCode() + accountId.hashCode()
}

@Entity
@Table(name = "memberships")
class MembershipEntity(
    @EmbeddedId
    val id: MembershipId,
    var role: String,
) {
    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null
}
