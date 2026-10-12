package com.amazfy.auth.application

import com.amazfy.account.application.AccountService
import com.amazfy.auth.application.port.AuthEmailSender
import com.amazfy.auth.application.port.OneTimeTokenRepository
import com.amazfy.auth.application.port.PasswordHasher
import com.amazfy.auth.application.port.RefreshTokenRepository
import com.amazfy.auth.application.port.StoredOneTimeToken
import com.amazfy.auth.application.port.UserRepository
import com.amazfy.auth.domain.Email
import com.amazfy.auth.domain.EmailAlreadyRegisteredException
import com.amazfy.auth.domain.InvalidCredentialsException
import com.amazfy.auth.domain.InvalidOneTimeTokenException
import com.amazfy.auth.domain.OneTimeTokenPurpose
import com.amazfy.auth.domain.OneTimeTokenPurpose.EMAIL_VERIFICATION
import com.amazfy.auth.domain.OneTimeTokenPurpose.PASSWORD_RESET
import com.amazfy.auth.domain.RawPassword
import com.amazfy.auth.domain.SecretTokenCodec
import com.amazfy.auth.domain.User
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Sign-up, email/password login, email verification and password reset. Tokens for a login session come from TokenService. */
@Service
@Transactional(readOnly = true)
class AuthService(
    private val users: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val accountService: AccountService,
    private val oneTimeTokens: OneTimeTokenRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val emailSender: AuthEmailSender,
    private val clock: Clock,
    @param:Value("\${amazfy.auth.email-verification-ttl}") private val emailVerificationTtl: Duration,
    @param:Value("\${amazfy.auth.password-reset-ttl}") private val passwordResetTtl: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Hash of a throwaway password, compared when an email is unknown so both cases take the same time. */
    private val timingDummyHash: String by lazy { passwordHasher.hash("timing-equalizer-not-a-real-password") }

    /**
     * Creates the user, their password login, their first account and an owner membership, all in one transaction.
     * Input is validated before anything is stored. Then a verification email is sent.
     */
    @Transactional
    fun register(
        email: String,
        password: String,
        accountName: String,
    ): Registration {
        val validEmail = Email.of(email)
        val validPassword = RawPassword.of(password)
        if (users.existsByEmail(validEmail)) throw EmailAlreadyRegisteredException()

        val user = users.createWithPassword(validEmail, passwordHasher.hash(validPassword.value))
        val account = accountService.createAccount(accountName, user.id)
        startEmailVerification(user)
        return Registration(user, account.id)
    }

    /** Returns the user if email and password match; otherwise one generic error (never says which part was wrong). */
    fun login(
        email: String,
        password: String,
    ): User {
        val login =
            runCatching { Email.of(email) }
                .getOrNull()
                ?.let { users.findPasswordLogin(it) }

        if (login == null) {
            passwordHasher.matches(password, timingDummyHash) // same work as a real check, so timing reveals nothing
            throw InvalidCredentialsException()
        }
        if (!passwordHasher.matches(password, login.passwordHash)) throw InvalidCredentialsException()
        return login.user
    }

    /** Confirms the email of the user the token belongs to. The token works once. */
    @Transactional
    fun verifyEmail(rawToken: String) {
        val now = clock.instant()
        val token = consume(rawToken, EMAIL_VERIFICATION, now)
        users.markEmailVerified(token.userId, now)
    }

    /** Sends a fresh link (older links stop working). Does nothing if the email is already verified. */
    @Transactional
    fun resendVerification(userId: UUID) {
        val user = users.findById(userId) ?: return
        if (!user.emailVerified) startEmailVerification(user)
    }

    /**
     * Sends a reset link if the email belongs to a user with a password login. Says nothing either way (the caller
     * always answers 202), so nobody can find out which emails have an account. Older reset links stop working.
     */
    @Transactional
    fun forgotPassword(email: String) {
        val login = runCatching { Email.of(email) }.getOrNull()?.let { users.findPasswordLogin(it) } ?: return
        val rawToken = issueOneTimeToken(login.user.id, PASSWORD_RESET, passwordResetTtl)
        afterCommit { sendSafely(login.user.id) { emailSender.sendPasswordReset(login.user.email, rawToken, passwordResetTtl) } }
    }

    /**
     * Sets a new password with the token from the reset email and logs the user out everywhere (a thief who knew the
     * old password loses access). The password rules are checked first, so a rejected password doesn't use up the link.
     * Opening the link also proves the user owns the email, so it counts as verified.
     */
    @Transactional
    fun resetPassword(
        rawToken: String,
        newPassword: String,
    ) {
        val validPassword = RawPassword.of(newPassword)
        val now = clock.instant()
        val token = consume(rawToken, PASSWORD_RESET, now)
        users.updatePasswordHash(token.userId, passwordHasher.hash(validPassword.value))
        users.markEmailVerified(token.userId, now)
        refreshTokens.revokeAllForUser(token.userId, now)
    }

    private fun startEmailVerification(user: User) {
        val rawToken = issueOneTimeToken(user.id, EMAIL_VERIFICATION, emailVerificationTtl)
        afterCommit { sendSafely(user.id) { emailSender.sendEmailVerification(user.email, rawToken, emailVerificationTtl) } }
    }

    /** Stores a new token (only its hash) and returns the raw one for the email link. Older open tokens stop working. */
    private fun issueOneTimeToken(
        userId: UUID,
        purpose: OneTimeTokenPurpose,
        validFor: Duration,
    ): String {
        val now = clock.instant()
        oneTimeTokens.invalidateOpen(userId, purpose, now)
        val rawToken = SecretTokenCodec.newToken()
        oneTimeTokens.save(userId, purpose, SecretTokenCodec.hash(rawToken), now.plus(validFor))
        return rawToken
    }

    /** Uses up a token from an email link; one error for unknown, expired, used or wrong-purpose tokens. */
    private fun consume(
        rawToken: String,
        purpose: OneTimeTokenPurpose,
        now: Instant,
    ): StoredOneTimeToken {
        val token = oneTimeTokens.findByHash(SecretTokenCodec.hash(rawToken))
        val usable = token != null && token.purpose == purpose && token.usedAt == null && token.expiresAt.isAfter(now)
        if (!usable || !oneTimeTokens.markUsed(token.id, now)) throw InvalidOneTimeTokenException()
        return token
    }

    /** A failed email is logged, never thrown: the user can ask for a new one. */
    private fun sendSafely(
        userId: UUID,
        send: () -> Unit,
    ) {
        runCatching(send).onFailure { log.warn("Email to user {} could not be sent; they can request it again", userId, it) }
    }

    /**
     * Runs after the transaction is saved, so an email never points to a token that was rolled back, and a broken
     * email server never undoes a sign-up (the user can press "resend").
     */
    private fun afterCommit(action: () -> Unit) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = action()
            },
        )
    }
}

/** Only the account id crosses into this module: another module's domain types stay inside it (ArchitectureTest). */
data class Registration(
    val user: User,
    val accountId: UUID,
)
