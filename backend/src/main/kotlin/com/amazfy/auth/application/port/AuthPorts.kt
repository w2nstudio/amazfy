package com.amazfy.auth.application.port

import com.amazfy.auth.domain.Email
import com.amazfy.auth.domain.OneTimeTokenPurpose
import com.amazfy.auth.domain.User
import java.time.Duration
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

    fun findById(id: UUID): User?

    /** Replaces the hash of the user's password login. */
    fun updatePasswordHash(
        id: UUID,
        passwordHash: String,
    )

    /** Sets the verification time; a user who is already verified keeps the first time. */
    fun markEmailVerified(
        id: UUID,
        at: Instant,
    )
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

/**
 * Sends the auth module's emails. The application decides what to send; the adapter builds the link and the wording.
 * `token` is the raw one-time token: it goes into the link and is never stored (only its hash is).
 */
interface AuthEmailSender {
    fun sendEmailVerification(
        to: Email,
        token: String,
        validFor: Duration,
    )

    fun sendPasswordReset(
        to: Email,
        token: String,
        validFor: Duration,
    )
}

/** A stored one-time token (only its hash). */
data class StoredOneTimeToken(
    val id: UUID,
    val userId: UUID,
    val purpose: OneTimeTokenPurpose,
    val expiresAt: Instant,
    val usedAt: Instant?,
)

/** Storage for the tokens in emailed links (verification, password reset). */
interface OneTimeTokenRepository {
    fun save(
        userId: UUID,
        purpose: OneTimeTokenPurpose,
        tokenHash: String,
        expiresAt: Instant,
    )

    fun findByHash(tokenHash: String): StoredOneTimeToken?

    /** Marks the token used. False if it was already used, e.g. the link was clicked twice at the same moment. */
    fun markUsed(
        id: UUID,
        at: Instant,
    ): Boolean

    /** Marks the user's unused tokens of this purpose as used, so only the newest link works. */
    fun invalidateOpen(
        userId: UUID,
        purpose: OneTimeTokenPurpose,
        at: Instant,
    )
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
