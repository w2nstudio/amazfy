package com.amazfy.auth.adapter.outbound.password

import com.amazfy.auth.application.port.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/**
 * bcrypt with cost 12 (about 4,000 rounds): slow enough to make guessing expensive, fast enough for logins.
 * bcrypt hashes can be imported by managed providers such as Auth0, so users keep their passwords after a migration.
 */
@Component
class BcryptPasswordHasher : PasswordHasher {
    private val encoder = BCryptPasswordEncoder(12)

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)!!

    override fun matches(
        rawPassword: String,
        hash: String,
    ): Boolean = encoder.matches(rawPassword, hash)
}
