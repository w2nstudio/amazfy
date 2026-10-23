package com.amazfy.auth.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

// JPA class for the table in V4__one_time_tokens.sql, used only inside OneTimeTokenPersistenceAdapter.

@Entity
@Table(name = "one_time_tokens")
class OneTimeTokenEntity(
    val userId: UUID,
    val purpose: String,
    val tokenHash: String,
    val expiresAt: Instant,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    var usedAt: Instant? = null

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null
}
