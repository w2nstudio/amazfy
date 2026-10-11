package com.amazfy.auth.application.port

import com.amazfy.auth.domain.Email
import com.amazfy.auth.domain.User

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
