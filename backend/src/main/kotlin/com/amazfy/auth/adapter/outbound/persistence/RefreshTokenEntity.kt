package com.amazfy.auth.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

// JPA class for the refresh_tokens table (V2__auth_users.sql), used only inside RefreshTokenPersistenceAdapter.

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    val userId: UUID,
    val familyId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    var revokedAt: Instant? = null

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null
}
