package com.amazfy.auth.application

import com.amazfy.TestcontainersConfiguration
import com.amazfy.auth.application.port.RefreshTokenRepository
import com.amazfy.auth.domain.InvalidRefreshTokenException
import com.amazfy.auth.domain.RefreshTokenCodec
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class TokenServiceTest {
    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var refreshTokens: RefreshTokenRepository

    @Autowired
    lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var jdbc: JdbcClient

    private fun newUser(): UUID =
        jdbc
            .sql("INSERT INTO users (email) VALUES (:e) RETURNING id")
            .param("e", "user-${UUID.randomUUID()}@example.com")
            .query(UUID::class.java)
            .single()

    @Test
    fun `issued access token is a valid JWT for the user, lasting 15 minutes`() {
        val userId = newUser()
        val tokens = tokenService.issueFor(userId)

        val jwt = jwtDecoder.decode(tokens.accessToken)
        assertEquals(userId.toString(), jwt.subject)
        assertEquals("amazfy", jwt.getClaimAsString("iss"))
        assertEquals(900, tokens.accessTokenExpiresIn.toSeconds())
    }

    @Test
    fun `refresh rotates the token - the new one works, the old one is spent`() {
        val first = tokenService.issueFor(newUser())

        val second = tokenService.refresh(first.refreshToken)
        assertNotEquals(first.refreshToken, second.refreshToken)

        tokenService.refresh(second.refreshToken) // the new token works
    }

    @Test
    fun `reusing a spent token revokes the whole family`() {
        val first = tokenService.issueFor(newUser())
        val second = tokenService.refresh(first.refreshToken)

        assertFailsWith<InvalidRefreshTokenException> { tokenService.refresh(first.refreshToken) } // stolen copy used
        assertFailsWith<InvalidRefreshTokenException> { tokenService.refresh(second.refreshToken) } // so the real one is dead too
    }

    @Test
    fun `missing, unknown and expired tokens are rejected`() {
        assertFailsWith<InvalidRefreshTokenException> { tokenService.refresh(null) }
        assertFailsWith<InvalidRefreshTokenException> { tokenService.refresh("not-a-real-token") }

        val expired = RefreshTokenCodec.newToken()
        refreshTokens.save(newUser(), UUID.randomUUID(), RefreshTokenCodec.hash(expired), Instant.now().minusSeconds(1))
        assertFailsWith<InvalidRefreshTokenException> { tokenService.refresh(expired) }
    }

    @Test
    fun `logout ends that session, logout-all ends every session`() {
        val userId = newUser()
        val laptop = tokenService.issueFor(userId)
        val phone = tokenService.issueFor(userId)
        val tablet = tokenService.issueFor(userId)

        tokenService.logout(laptop.refreshToken)
        assertFailsWith<InvalidRefreshTokenException> { tokenService.refresh(laptop.refreshToken) }
        tokenService.refresh(phone.refreshToken) // other sessions still work

        tokenService.logoutAll(userId)
        assertFailsWith<InvalidRefreshTokenException> { tokenService.refresh(tablet.refreshToken) }
    }

    @Test
    fun `only a hash of the refresh token is stored`() {
        val tokens = tokenService.issueFor(newUser())
        val stored =
            jdbc
                .sql("SELECT count(*) FROM refresh_tokens WHERE token_hash = :raw")
                .param("raw", tokens.refreshToken)
                .query(Int::class.java)
                .single()
        assertEquals(0, stored)
    }
}
