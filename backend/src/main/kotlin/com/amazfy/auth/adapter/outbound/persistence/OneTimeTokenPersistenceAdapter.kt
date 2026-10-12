package com.amazfy.auth.adapter.outbound.persistence

import com.amazfy.auth.application.port.OneTimeTokenRepository
import com.amazfy.auth.application.port.StoredOneTimeToken
import com.amazfy.auth.domain.OneTimeTokenPurpose
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

// JPA class for the table in V4__one_time_tokens.sql, used only inside this adapter.

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

// Bulk UPDATEs, flushed and cleared like the refresh-token queries (see RefreshTokenJpaRepository).
interface OneTimeTokenJpaRepository : JpaRepository<OneTimeTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): OneTimeTokenEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OneTimeTokenEntity t SET t.usedAt = :at WHERE t.id = :id AND t.usedAt IS NULL")
    fun markUsed(
        id: UUID,
        at: Instant,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OneTimeTokenEntity t SET t.usedAt = :at WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    fun markOpenUsed(
        userId: UUID,
        purpose: String,
        at: Instant,
    ): Int
}

@Component
class OneTimeTokenPersistenceAdapter(
    private val jpa: OneTimeTokenJpaRepository,
) : OneTimeTokenRepository {
    override fun save(
        userId: UUID,
        purpose: OneTimeTokenPurpose,
        tokenHash: String,
        expiresAt: Instant,
    ) {
        jpa.save(OneTimeTokenEntity(userId, purpose.name, tokenHash, expiresAt))
    }

    override fun findByHash(tokenHash: String): StoredOneTimeToken? =
        jpa.findByTokenHash(tokenHash)?.let {
            StoredOneTimeToken(
                id = it.id!!,
                userId = it.userId,
                purpose = OneTimeTokenPurpose.valueOf(it.purpose),
                expiresAt = it.expiresAt,
                usedAt = it.usedAt,
            )
        }

    // The "used_at IS NULL" condition makes this safe when two requests race: only one of them updates the row.
    override fun markUsed(
        id: UUID,
        at: Instant,
    ): Boolean = jpa.markUsed(id, at) == 1

    override fun invalidateOpen(
        userId: UUID,
        purpose: OneTimeTokenPurpose,
        at: Instant,
    ) {
        jpa.markOpenUsed(userId, purpose.name, at)
    }
}
