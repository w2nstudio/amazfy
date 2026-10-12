package com.amazfy.auth.application

import com.amazfy.auth.application.port.AccessTokenIssuer
import com.amazfy.auth.application.port.RefreshTokenRepository
import com.amazfy.auth.domain.InvalidRefreshTokenException
import com.amazfy.auth.domain.SecretTokenCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Issues, rotates and revokes tokens: a short-lived access token (JWT) plus a long-lived refresh token. */
@Service
@Transactional
class TokenService(
    private val accessTokens: AccessTokenIssuer,
    private val refreshTokens: RefreshTokenRepository,
    private val clock: Clock,
    @param:Value("\${amazfy.jwt.access-token-ttl}") private val accessTokenTtl: Duration,
    @param:Value("\${amazfy.auth.refresh-token-ttl}") private val refreshTokenTtl: Duration,
) {
    /** New login session: a new refresh-token family. */
    fun issueFor(userId: UUID): Tokens = issue(userId, familyId = UUID.randomUUID())

    /**
     * Exchanges a refresh token for new tokens (rotation). Reusing an already-rotated token means it was copied:
     * the whole family is revoked, logging out both the attacker and the user.
     * Note: two tabs refreshing at the same moment can trigger this too; they then log in again (accepted trade-off).
     */
    fun refresh(rawRefreshToken: String?): Tokens {
        val stored = rawRefreshToken?.let { refreshTokens.findByHash(SecretTokenCodec.hash(it)) } ?: throw InvalidRefreshTokenException()
        val now = clock.instant()
        if (stored.revokedAt != null) {
            refreshTokens.revokeFamily(stored.familyId, now)
            throw InvalidRefreshTokenException()
        }
        if (!stored.expiresAt.isAfter(now)) throw InvalidRefreshTokenException()

        refreshTokens.revoke(stored.id, now)
        return issue(stored.userId, stored.familyId)
    }

    /** Logs out one browser. Unknown or missing tokens are ignored: logout always succeeds. */
    fun logout(rawRefreshToken: String?) {
        val stored = rawRefreshToken?.let { refreshTokens.findByHash(SecretTokenCodec.hash(it)) } ?: return
        refreshTokens.revokeFamily(stored.familyId, clock.instant())
    }

    /** Logs out everywhere (e.g. after a password change). */
    fun logoutAll(userId: UUID) = refreshTokens.revokeAllForUser(userId, clock.instant())

    private fun issue(
        userId: UUID,
        familyId: UUID,
    ): Tokens {
        val now = clock.instant()
        val rawRefreshToken = SecretTokenCodec.newToken()
        refreshTokens.save(userId, familyId, SecretTokenCodec.hash(rawRefreshToken), now.plus(refreshTokenTtl))
        return Tokens(
            accessToken = accessTokens.issue(userId, now.plus(accessTokenTtl)),
            accessTokenExpiresIn = accessTokenTtl,
            refreshToken = rawRefreshToken,
            refreshTokenExpiresIn = refreshTokenTtl,
        )
    }
}

data class Tokens(
    val accessToken: String,
    val accessTokenExpiresIn: Duration,
    val refreshToken: String,
    val refreshTokenExpiresIn: Duration,
) {
    override fun toString() = "Tokens(****)" // never log tokens
}
