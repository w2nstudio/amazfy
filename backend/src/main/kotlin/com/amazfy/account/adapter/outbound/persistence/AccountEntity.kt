package com.amazfy.account.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

// JPA class for the accounts table (V3__accounts_memberships.sql), used only inside AccountPersistenceAdapter.
// Plain classes, not data classes (data classes break Hibernate).

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
