package com.amazfy.auth.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.time.Instant
import java.util.UUID

/** A person who logs in (never called "account"; see CLAUDE.md, Domain naming). */
data class User(
    val id: UUID,
    val email: Email,
    val emailVerifiedAt: Instant?,
) {
    val emailVerified: Boolean get() = emailVerifiedAt != null
}

/** An email address, trimmed and lower-cased so "John@X.com" and "john@x.com" are the same user. */
@JvmInline
value class Email private constructor(
    val value: String,
) {
    companion object {
        private const val MAX_LENGTH = 254

        // Deliberately simple: one @, something before it, a dot in the domain. The verification email is the real test.
        private val shape = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            if (normalized.length > MAX_LENGTH || !shape.matches(normalized)) {
                throw InvalidEmailException()
            }
            return Email(normalized)
        }
    }
}

/**
 * A password as typed by the user, checked against the rules. Never stored or logged; only its hash is kept.
 * Rules follow current guidance: length matters, no forced symbol/digit mix.
 */
class RawPassword private constructor(
    val value: String,
) {
    override fun toString() = "RawPassword(****)" // never leak it into logs

    companion object {
        const val MIN_LENGTH = 10
        const val MAX_LENGTH = 128

        fun of(raw: String): RawPassword {
            if (raw.length !in MIN_LENGTH..MAX_LENGTH) throw WeakPasswordException()
            if (raw.isBlank()) throw WeakPasswordException()
            return RawPassword(raw)
        }
    }
}

class InvalidEmailException : AppException(ErrorKind.BAD_REQUEST, "Please enter a valid email address.")

class WeakPasswordException :
    AppException(ErrorKind.BAD_REQUEST, "Password must be ${RawPassword.MIN_LENGTH}–${RawPassword.MAX_LENGTH} characters.")

class EmailAlreadyRegisteredException : AppException(ErrorKind.CONFLICT, "An account with this email already exists.")

/** Same message whether the email or the password was wrong, so attackers can't tell which emails exist. */
class InvalidCredentialsException : AppException(ErrorKind.UNAUTHORIZED, "Wrong email or password.")
