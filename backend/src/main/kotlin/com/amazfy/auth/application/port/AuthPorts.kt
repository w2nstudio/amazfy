package com.amazfy.auth.application.port

import com.amazfy.auth.domain.Email
import com.amazfy.auth.domain.User
import java.time.Instant
import java.util.UUID

/** Storage the auth module needs for users and their login identities. */
interface UserRepository {
    fun existsByEmail(email: Email): Boolean

    /** Creates the user and their password identity in one go. */
    fun createWithPassword(
        email: Email,
        passwordHash: String,
    ): User

    /** The user and their password hash, or null if no user with a password login has this email. */
    fun findPasswordLogin(email: Email): PasswordLogin?
}

data class PasswordLogin(
    val user: User,
    val passwordHash: String,
)

/** One-way password hashing (bcrypt in production). */
interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun matches(
        rawPassword: String,
        hash: String,
    ): Boolean
}

/** Creates signed access tokens (JWT) for a user. */
interface AccessTokenIssuer {
    fun issue(
        userId: UUID,
        expiresAt: Instant,
    ): String
}

/** A stored refresh token (only its hash). */
data class StoredRefreshToken(
    val id: UUID,
    val userId: UUID,
    val familyId: UUID,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

/** Storage for refresh tokens. */
interface RefreshTokenRepository {
    fun save(
        userId: UUID,
        familyId: UUID,
        tokenHash: String,
        expiresAt: Instant,
    )

    fun findByHash(tokenHash: String): StoredRefreshToken?

    fun revoke(
        id: UUID,
        at: Instant,
    )

    fun revokeFamily(
        familyId: UUID,
        at: Instant,
    )

    fun revokeAllForUser(
        userId: UUID,
        at: Instant,
    )
}
