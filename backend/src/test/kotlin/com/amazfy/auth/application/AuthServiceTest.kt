package com.amazfy.auth.application

import com.amazfy.TestcontainersConfiguration
import com.amazfy.account.application.AccountService
import com.amazfy.account.application.Role
import com.amazfy.account.domain.InvalidAccountNameException
import com.amazfy.auth.application.port.OneTimeTokenRepository
import com.amazfy.auth.domain.EmailAlreadyRegisteredException
import com.amazfy.auth.domain.InvalidCredentialsException
import com.amazfy.auth.domain.InvalidEmailException
import com.amazfy.auth.domain.InvalidOneTimeTokenException
import com.amazfy.auth.domain.OneTimeTokenPurpose
import com.amazfy.auth.domain.SecretTokenCodec
import com.amazfy.auth.domain.WeakPasswordException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Register and login end to end: service → adapters → real PostgreSQL (rolled back after each test). */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class AuthServiceTest {
    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var oneTimeTokens: OneTimeTokenRepository

    private val password = "correct horse battery"

    // A fresh address per test, so failed logins in one test never trip another test's limits
    private val testIp = "test-ip-${UUID.randomUUID()}"

    @Test
    fun `register creates the user, a hashed password and an owned account`() {
        val registration = authService.register("  John@AquaLife.com ", password, "AquaLife US")

        assertEquals("john@aqualife.com", registration.user.email.value)
        assertFalse(registration.user.emailVerified)
        assertEquals(Role.OWNER, accountService.roleOf(registration.user.id, registration.accountId))

        val storedHash =
            jdbc
                .sql("SELECT password_hash FROM user_identities WHERE user_id = :u AND provider = 'password'")
                .param("u", registration.user.id)
                .query(String::class.java)
                .single()
        assertNotEquals(password, storedHash)
        assertTrue(storedHash.startsWith("\$2a\$12\$"), "bcrypt with cost 12")
    }

    @Test
    fun `registering the same email twice is a conflict, whatever the case`() {
        authService.register("john@aqualife.com", password, "AquaLife US")
        assertFailsWith<EmailAlreadyRegisteredException> { authService.register("JOHN@aqualife.com", password, "Another") }
    }

    @Test
    fun `invalid input is rejected before anything is stored`() {
        assertFailsWith<InvalidEmailException> { authService.register("not-an-email", password, "Shop") }
        assertFailsWith<WeakPasswordException> { authService.register("a@b.com", "short", "Shop") }
        assertEquals(0, jdbc.sql("SELECT count(*) FROM users").query(Int::class.java).single())
    }

    @Test
    fun `an invalid account name fails the registration`() {
        // In production the whole registration is one transaction, so the user row is rolled back too
        assertFailsWith<InvalidAccountNameException> { authService.register("a@b.com", password, "  ") }
    }

    @Test
    fun `login succeeds with the right password, email in any case`() {
        val registered = authService.register("john@aqualife.com", password, "AquaLife US").user
        assertEquals(registered.id, authService.login("John@AquaLife.COM", password, testIp).id)
    }

    @Test
    fun `wrong password and unknown email fail the same way`() {
        authService.register("john@aqualife.com", password, "AquaLife US")
        val wrongPassword =
            assertFailsWith<InvalidCredentialsException> { authService.login("john@aqualife.com", "wrong password!", testIp) }
        val unknownEmail = assertFailsWith<InvalidCredentialsException> { authService.login("nobody@aqualife.com", password, testIp) }
        val malformedEmail = assertFailsWith<InvalidCredentialsException> { authService.login("nonsense", password, testIp) }
        assertEquals(wrongPassword.message, unknownEmail.message)
        assertEquals(wrongPassword.message, malformedEmail.message)
    }

    private fun storeToken(
        purpose: OneTimeTokenPurpose,
        expiresAt: Instant,
    ): String {
        val userId = authService.register("token-${purpose.name.lowercase()}@aqualife.com", password, "AquaLife US").user.id
        val rawToken = SecretTokenCodec.newToken()
        oneTimeTokens.save(userId, purpose, SecretTokenCodec.hash(rawToken), expiresAt)
        return rawToken
    }

    @Test
    fun `an expired verification link is rejected`() {
        val expired = storeToken(OneTimeTokenPurpose.EMAIL_VERIFICATION, Instant.now().minusSeconds(1))
        assertFailsWith<InvalidOneTimeTokenException> { authService.verifyEmail(expired) }
    }

    @Test
    fun `a verification token can't reset a password`() {
        val verificationToken = storeToken(OneTimeTokenPurpose.EMAIL_VERIFICATION, Instant.now().plusSeconds(3600))
        assertFailsWith<InvalidOneTimeTokenException> { authService.resetPassword(verificationToken, "a brand new passphrase") }
    }

    @Test
    fun `an expired reset link is rejected`() {
        val expired = storeToken(OneTimeTokenPurpose.PASSWORD_RESET, Instant.now().minusSeconds(1))
        assertFailsWith<InvalidOneTimeTokenException> { authService.resetPassword(expired, "a brand new passphrase") }
    }

    @Test
    fun `a password-reset token can't verify an email`() {
        val resetToken = storeToken(OneTimeTokenPurpose.PASSWORD_RESET, Instant.now().plusSeconds(3600))
        assertFailsWith<InvalidOneTimeTokenException> { authService.verifyEmail(resetToken) }
    }
}
