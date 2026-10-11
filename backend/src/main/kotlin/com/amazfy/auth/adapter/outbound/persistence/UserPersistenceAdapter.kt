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
}

interface UserIdentityJpaRepository : JpaRepository<UserIdentityEntity, UUID> {
    @Query("SELECT i FROM UserIdentityEntity i WHERE i.userId = :userId AND i.provider = :provider")
    fun findByUserIdAndProvider(
        userId: UUID,
        provider: String,
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
}

private fun UserEntity.toDomain() = User(id = id!!, email = Email.of(email), emailVerifiedAt = emailVerifiedAt)
