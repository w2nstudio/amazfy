package com.amazfy.auth.application

import com.amazfy.TestcontainersConfiguration
import com.amazfy.auth.domain.GoogleIdentity
import com.amazfy.auth.domain.GoogleLoginFailedException
import com.amazfy.auth.domain.InvalidCredentialsException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which Amazfy user a verified Google sign-in belongs to (AuthService.loginWithGoogle), with a real database. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class GoogleUserLinkingTest {
    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var jdbc: JdbcClient

    private val password = "correct horse battery"
    private val testIp = "test-ip-${UUID.randomUUID()}"

    private fun google(
        email: String,
        subject: String = "google-${UUID.randomUUID()}",
        emailVerified: Boolean = true,
    ) = GoogleIdentity(subject = subject, email = email, emailVerified = emailVerified, name = "John Seller")

    private fun googleSubjectOf(userId: UUID): String? =
        jdbc
            .sql("SELECT subject FROM user_identities WHERE user_id = :u AND provider = 'google'")
            .param("u", userId)
            .query(String::class.java)
            .optional()
            .orElse(null)

    private fun markVerified(userId: UUID) {
        jdbc.sql("UPDATE users SET email_verified_at = now() WHERE id = :u").param("u", userId).update()
    }

    @Test
    fun `a new Google user is created with a verified email and no account yet`() {
        val login = authService.loginWithGoogle(google("john@gmail.com", subject = "109876543210"))

        assertTrue(login.needsOnboarding)
        val user = authService.findUser(login.userId)
        assertEquals("john@gmail.com", user.email.value)
        assertTrue(user.emailVerified)
        assertEquals("109876543210", googleSubjectOf(login.userId))
    }

    @Test
    fun `a returning Google user is found by Google's id, even after changing their Gmail address`() {
        val first = authService.loginWithGoogle(google("john@gmail.com", subject = "109876543210"))
        val again = authService.loginWithGoogle(google("john.renamed@gmail.com", subject = "109876543210"))

        assertEquals(first.userId, again.userId)
    }

    @Test
    fun `Google sign-in is linked to an existing verified user with the same email, whose password keeps working`() {
        val registered = authService.register("john@aqualife.com", password, "AquaLife US").user
        markVerified(registered.id)

        val login = authService.loginWithGoogle(google("John@AquaLife.com"))

        assertEquals(registered.id, login.userId)
        assertFalse(login.needsOnboarding, "they already own an account")
        assertEquals(registered.id, authService.login("john@aqualife.com", password, testIp).id)
    }

    @Test
    fun `linking to a never-verified user removes their password and ends their sessions`() {
        // Someone signed up with this address but never proved they own it
        val registered = authService.register("victim@gmail.com", password, "Squatter Shop").user
        val squatterSession = tokenService.issueFor(registered.id)

        val login = authService.loginWithGoogle(google("victim@gmail.com"))

        assertEquals(registered.id, login.userId)
        assertFailsWith<InvalidCredentialsException> { authService.login("victim@gmail.com", password, testIp) }
        assertFailsWith<Exception> { tokenService.refresh(squatterSession.refreshToken) }
        assertTrue(authService.findUser(registered.id).emailVerified)
    }

    @Test
    fun `an email Google hasn't verified is refused`() {
        assertFailsWith<GoogleLoginFailedException> { authService.loginWithGoogle(google("john@example.com", emailVerified = false)) }
    }

    @Test
    fun `a second Google account with an already linked email is refused`() {
        authService.loginWithGoogle(google("john@gmail.com", subject = "first-google-account"))

        assertFailsWith<GoogleLoginFailedException> {
            authService.loginWithGoogle(google("john@gmail.com", subject = "another-google-account"))
        }
    }
}
