package com.amazfy.auth.application

import com.amazfy.account.application.AccountService
import com.amazfy.auth.application.port.AuthEmailSender
import com.amazfy.auth.application.port.OneTimeTokenRepository
import com.amazfy.auth.application.port.PasswordHasher
import com.amazfy.auth.application.port.RefreshTokenRepository
import com.amazfy.auth.application.port.StoredOneTimeToken
import com.amazfy.auth.application.port.UserRepository
import com.amazfy.auth.domain.AttemptLimit
import com.amazfy.auth.domain.Email
import com.amazfy.auth.domain.EmailAlreadyRegisteredException
import com.amazfy.auth.domain.GoogleIdentity
import com.amazfy.auth.domain.GoogleLoginFailedException
import com.amazfy.auth.domain.InvalidCredentialsException
import com.amazfy.auth.domain.InvalidOneTimeTokenException
import com.amazfy.auth.domain.InvalidRefreshTokenException
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
    private val limiter: AttemptLimiter,
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

    /**
     * Returns the user if email and password match; otherwise one generic error (never says which part was wrong).
     * Failed attempts count per email and per client IP; over the limit, logins are refused for a while (429), even
     * with the right password, so guessing gains nothing. A successful login clears the email's failures.
     */
    fun login(
        email: String,
        password: String,
        clientIp: String,
    ): User {
        val emailKey = email.trim()
        limiter.check(LOGIN_FAILURES_PER_IP, clientIp)
        limiter.check(LOGIN_FAILURES_PER_EMAIL, emailKey)
        val user =
            try {
                checkPassword(email, password)
            } catch (e: InvalidCredentialsException) {
                limiter.record(LOGIN_FAILURES_PER_IP, clientIp)
                limiter.record(LOGIN_FAILURES_PER_EMAIL, emailKey)
                throw e
            }
        limiter.reset(LOGIN_FAILURES_PER_EMAIL, emailKey)
        return user
    }

    private fun checkPassword(
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

    /**
     * Finds or creates the Amazfy user for a verified Google sign-in (the identity comes from GoogleLoginService):
     * 1. Known Google account (by Google's permanent id): that user, even if their Gmail address changed since.
     * 2. Otherwise a user with the same email gets this Google login added ("linking"), so both ways work.
     *    If that user never verified their email, whoever set their password never proved they own the inbox (it
     *    could be someone who signed up with another person's address): their password is removed and their
     *    sessions end. The Google owner has proved the inbox is theirs.
     * 3. Otherwise a new user, email already verified, with no account yet: onboarding asks for the account name.
     */
    @Transactional
    fun loginWithGoogle(identity: GoogleIdentity): GoogleLogin {
        if (!identity.emailVerified) throw GoogleLoginFailedException("Google hasn't verified this email address")
        val now = clock.instant()
        val email = Email.of(identity.email)

        val user =
            users.findByIdentity(GOOGLE, identity.subject)
                ?: users.findByEmail(email)?.also { linkGoogle(it, identity.subject) }
                ?: users.createWithIdentity(email, GOOGLE, identity.subject, verifiedAt = now)
        users.markEmailVerified(user.id, now)
        return GoogleLogin(userId = user.id, needsOnboarding = accountService.accessibleAccounts(user.id).isEmpty())
    }

    private fun linkGoogle(
        user: User,
        googleSubject: String,
    ) {
        // A user has at most one Google login; a different Google account with the same email is refused
        if (users.findIdentitySubject(user.id, GOOGLE) != null) {
            throw GoogleLoginFailedException("this email is already linked to another Google account")
        }
        if (!user.emailVerified) {
            users.removePasswordLogin(user.id)
            refreshTokens.revokeAllForUser(user.id, clock.instant())
        }
        users.addIdentity(user.id, GOOGLE, googleSubject)
    }

    /** The logged-in user. A valid access token for a deleted user counts as an expired session (401). */
    fun findUser(userId: UUID): User = users.findById(userId) ?: throw InvalidRefreshTokenException()

    /** Sends a fresh link (older links stop working). Does nothing if the email is already verified. */
    @Transactional
    fun resendVerification(userId: UUID) {
        limiter.check(VERIFICATION_EMAILS_PER_USER, userId.toString())
        limiter.record(VERIFICATION_EMAILS_PER_USER, userId.toString())
        val user = users.findById(userId) ?: return
        if (!user.emailVerified) startEmailVerification(user)
    }

    /**
     * Sends a reset link if the email belongs to a user with a password login. Says nothing either way (the caller
     * always answers 202), so nobody can find out which emails have an account. Older reset links stop working.
     * Requests count per email and per client IP whether or not the email exists, so the limit reveals nothing either.
     */
    @Transactional
    fun forgotPassword(
        email: String,
        clientIp: String,
    ) {
        val emailKey = email.trim()
        limiter.check(RESET_EMAILS_PER_IP, clientIp)
        limiter.check(RESET_EMAILS_PER_EMAIL, emailKey)
        limiter.record(RESET_EMAILS_PER_IP, clientIp)
        limiter.record(RESET_EMAILS_PER_EMAIL, emailKey)
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

    companion object {
        /** Provider name in user_identities for Google logins. */
        const val GOOGLE = "google"

        // Brute-force limits (see AttemptLimit). Per IP is looser than per email: several people can share an office IP.
        val LOGIN_FAILURES_PER_EMAIL = AttemptLimit("login-email", max = 5, window = Duration.ofMinutes(15))
        val LOGIN_FAILURES_PER_IP = AttemptLimit("login-ip", max = 20, window = Duration.ofMinutes(15))
        val RESET_EMAILS_PER_EMAIL = AttemptLimit("reset-email", max = 3, window = Duration.ofHours(1))
        val RESET_EMAILS_PER_IP = AttemptLimit("reset-ip", max = 10, window = Duration.ofHours(1))
        val VERIFICATION_EMAILS_PER_USER = AttemptLimit("verification-user", max = 3, window = Duration.ofHours(1))
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

/** Result of a Google sign-in: who logged in, and whether they still need to create their first account. */
data class GoogleLogin(
    val userId: UUID,
    val needsOnboarding: Boolean,
)

/** Only the account id crosses into this module: another module's domain types stay inside it (ArchitectureTest). */
data class Registration(
    val user: User,
    val accountId: UUID,
)
