package com.amazfy.auth.adapter.outbound.persistence

import com.amazfy.auth.application.port.RefreshTokenRepository
import com.amazfy.auth.application.port.StoredRefreshToken
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

// The revoke queries are bulk UPDATEs that bypass Hibernate's cache of loaded rows: flush pending changes first,
// clear the cache after, so later reads in the same transaction see the revocation.
interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :at WHERE t.id = :id AND t.revokedAt IS NULL")
    fun revokeById(
        id: UUID,
        at: Instant,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :at WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    fun revokeByFamilyId(
        familyId: UUID,
        at: Instant,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :at WHERE t.userId = :userId AND t.revokedAt IS NULL")
    fun revokeByUserId(
        userId: UUID,
        at: Instant,
    ): Int
}

@Component
class RefreshTokenPersistenceAdapter(
    private val jpa: RefreshTokenJpaRepository,
) : RefreshTokenRepository {
    override fun save(
        userId: UUID,
        familyId: UUID,
        tokenHash: String,
        expiresAt: Instant,
    ) {
        jpa.save(RefreshTokenEntity(userId, familyId, tokenHash, expiresAt))
    }

    override fun findByHash(tokenHash: String): StoredRefreshToken? =
        jpa.findByTokenHash(tokenHash)?.let {
            StoredRefreshToken(id = it.id!!, userId = it.userId, familyId = it.familyId, expiresAt = it.expiresAt, revokedAt = it.revokedAt)
        }

    override fun revoke(
        id: UUID,
        at: Instant,
    ) {
        jpa.revokeById(id, at)
    }

    override fun revokeFamily(
        familyId: UUID,
        at: Instant,
    ) {
        jpa.revokeByFamilyId(familyId, at)
    }

    override fun revokeAllForUser(
        userId: UUID,
        at: Instant,
    ) {
        jpa.revokeByUserId(userId, at)
    }
}
