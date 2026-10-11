package com.amazfy.account.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

// JPA classes for the tables in V3__accounts_memberships.sql. Used only inside this adapter;
// the rest of the app sees the domain classes. Plain classes, not data classes (data classes break Hibernate).

@Entity
@Table(name = "accounts")
class AccountEntity(
    var name: String,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null

    @UpdateTimestamp
    val updatedAt: Instant? = null
}

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
