package com.amazfy.auth.adapter.outbound.persistence

import com.amazfy.auth.application.port.PasswordLogin
import com.amazfy.auth.application.port.UserRepository
import com.amazfy.auth.domain.Email
import com.amazfy.auth.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

// JPA classes for the tables in V2__auth_users.sql, used only inside this adapter.

@Entity
@Table(name = "users")
class UserEntity(
    var email: String,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    var emailVerifiedAt: Instant? = null

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null

    @UpdateTimestamp
    val updatedAt: Instant? = null
}

@Entity
@Table(name = "user_identities")
class UserIdentityEntity(
    val userId: UUID,
    val provider: String,
    val subject: String,
    var passwordHash: String?,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null

    companion object {
        const val PASSWORD = "password"
    }
}

interface UserJpaRepository : JpaRepository<UserEntity, UUID> {
    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): UserEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.emailVerifiedAt = :at, u.updatedAt = :at WHERE u.id = :id AND u.emailVerifiedAt IS NULL")
    fun markEmailVerified(
        id: UUID,
        at: Instant,
    ): Int
}

interface UserIdentityJpaRepository : JpaRepository<UserIdentityEntity, UUID> {
    @Query("SELECT i FROM UserIdentityEntity i WHERE i.userId = :userId AND i.provider = :provider")
    fun findByUserIdAndProvider(
        userId: UUID,
        provider: String,
    ): UserIdentityEntity?

    @Query("SELECT i FROM UserIdentityEntity i WHERE i.provider = :provider AND i.subject = :subject")
    fun findByProviderAndSubject(
        provider: String,
        subject: String,
    ): UserIdentityEntity?
}

/** Implements the auth module's user storage port with JPA. Emails arrive already normalized (lower-case). */
@Component
class UserPersistenceAdapter(
    private val userJpa: UserJpaRepository,
    private val identityJpa: UserIdentityJpaRepository,
) : UserRepository {
    override fun existsByEmail(email: Email): Boolean = userJpa.existsByEmail(email.value)

    override fun createWithPassword(
        email: Email,
        passwordHash: String,
    ): User {
        val user = userJpa.saveAndFlush(UserEntity(email.value))
        // For password logins the subject is the user's own id (no outside provider; emails can change)
        identityJpa.save(UserIdentityEntity(user.id!!, UserIdentityEntity.PASSWORD, user.id.toString(), passwordHash))
        return user.toDomain()
    }

    override fun findPasswordLogin(email: Email): PasswordLogin? {
        val user = userJpa.findByEmail(email.value) ?: return null
        val identity = identityJpa.findByUserIdAndProvider(user.id!!, UserIdentityEntity.PASSWORD) ?: return null
        return PasswordLogin(user.toDomain(), identity.passwordHash!!)
    }

    override fun findById(id: UUID): User? = userJpa.findById(id).orElse(null)?.toDomain()

    override fun findByIdentity(
        provider: String,
        subject: String,
    ): User? = identityJpa.findByProviderAndSubject(provider, subject)?.let { findById(it.userId) }

    override fun findByEmail(email: Email): User? = userJpa.findByEmail(email.value)?.toDomain()

    override fun findIdentitySubject(
        userId: UUID,
        provider: String,
    ): String? = identityJpa.findByUserIdAndProvider(userId, provider)?.subject

    override fun createWithIdentity(
        email: Email,
        provider: String,
        subject: String,
        verifiedAt: Instant,
    ): User {
        val user = userJpa.saveAndFlush(UserEntity(email.value).apply { emailVerifiedAt = verifiedAt })
        identityJpa.save(UserIdentityEntity(user.id!!, provider, subject, passwordHash = null))
        return user.toDomain()
    }

    override fun addIdentity(
        userId: UUID,
        provider: String,
        subject: String,
    ) {
        identityJpa.save(UserIdentityEntity(userId, provider, subject, passwordHash = null))
    }

    override fun removePasswordLogin(userId: UUID) {
        identityJpa.findByUserIdAndProvider(userId, UserIdentityEntity.PASSWORD)?.let { identityJpa.delete(it) }
    }

    override fun updatePasswordHash(
        id: UUID,
        passwordHash: String,
    ) {
        identityJpa.findByUserIdAndProvider(id, UserIdentityEntity.PASSWORD)!!.passwordHash = passwordHash
    }

    override fun markEmailVerified(
        id: UUID,
        at: Instant,
    ) {
        userJpa.markEmailVerified(id, at)
    }
}

private fun UserEntity.toDomain() = User(id = id!!, email = Email.of(email), emailVerifiedAt = emailVerifiedAt)
