package com.amazfy.auth.application

import com.amazfy.account.application.AccountService
import com.amazfy.auth.application.port.PasswordHasher
import com.amazfy.auth.application.port.UserRepository
import com.amazfy.auth.domain.Email
import com.amazfy.auth.domain.EmailAlreadyRegisteredException
import com.amazfy.auth.domain.InvalidCredentialsException
import com.amazfy.auth.domain.RawPassword
import com.amazfy.auth.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Sign-up and email/password login. Token issuing is added on top of this (next step). */
@Service
@Transactional(readOnly = true)
class AuthService(
    private val users: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val accountService: AccountService,
) {
    /** Hash of a throwaway password, compared when an email is unknown so both cases take the same time. */
    private val timingDummyHash: String by lazy { passwordHasher.hash("timing-equalizer-not-a-real-password") }

    /**
     * Creates the user, their password login, their first account and an owner membership, all in one transaction.
     * Input is validated before anything is stored.
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
}

/** Only the account id crosses into this module: another module's domain types stay inside it (ArchitectureTest). */
data class Registration(
    val user: User,
    val accountId: UUID,
)
