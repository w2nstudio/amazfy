package com.amazfy

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Checks the rules enforced by the database itself (constraints in the Flyway migrations).
 * Each test runs in a transaction that is rolled back, so tests don't affect each other.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class DatabaseSchemaTest {
    @Autowired
    lateinit var jdbc: JdbcClient

    private fun insertUser(email: String): UUID =
        jdbc
            .sql("INSERT INTO users (email) VALUES (:email) RETURNING id")
            .param("email", email)
            .query(UUID::class.java)
            .single()

    private fun insertAccount(name: String): UUID =
        jdbc
            .sql("INSERT INTO accounts (name) VALUES (:name) RETURNING id")
            .param("name", name)
            .query(UUID::class.java)
            .single()

    private fun count(table: String): Int =
        jdbc
            .sql("SELECT count(*) FROM $table")
            .query(Int::class.java)
            .single()

    @Test
    fun `email is unique regardless of upper or lower case`() {
        insertUser("john@aqualife.com")
        assertFailsWith<DataIntegrityViolationException> { insertUser("John@AquaLife.com") }
    }

    // Note: in PostgreSQL a failed statement aborts the whole transaction, so each test provokes at most one failure, last.
    private fun insertIdentity(
        userId: UUID,
        provider: String,
        hash: String?,
    ): Int =
        jdbc
            .sql("INSERT INTO user_identities (user_id, provider, subject, password_hash) VALUES (:u, :p, :s, :h)")
            .param("u", userId)
            .param("p", provider)
            .param("s", "subject-$provider")
            .param("h", hash)
            .update()

    @Test
    fun `password identity with a hash is accepted`() {
        assertEquals(1, insertIdentity(insertUser("ok@example.com"), "password", "\$2a\$10\$example"))
    }

    @Test
    fun `password identity without a hash is rejected`() {
        val userId = insertUser("nohash@example.com")
        assertFailsWith<DataIntegrityViolationException> { insertIdentity(userId, "password", null) }
    }

    @Test
    fun `google identity with a password hash is rejected`() {
        val userId = insertUser("google@example.com")
        assertFailsWith<DataIntegrityViolationException> { insertIdentity(userId, "google", "a-hash") }
    }

    @Test
    fun `membership role must be OWNER, EDITOR or VIEWER`() {
        val userId = insertUser("owner@example.com")
        val accountId = insertAccount("AquaLife US")
        assertFailsWith<DataIntegrityViolationException> {
            jdbc
                .sql("INSERT INTO memberships (user_id, account_id, role) VALUES (:u, :a, 'ADMIN')")
                .param("u", userId)
                .param("a", accountId)
                .update()
        }
    }

    @Test
    fun `deleting a user removes their identities and memberships but keeps the account`() {
        val userId = insertUser("leaving@example.com")
        val accountId = insertAccount("Leaving Co")
        jdbc
            .sql("INSERT INTO user_identities (user_id, provider, subject, password_hash) VALUES (:u, 'password', :u::text, 'h')")
            .param("u", userId)
            .update()
        jdbc
            .sql("INSERT INTO memberships (user_id, account_id, role) VALUES (:u, :a, 'OWNER')")
            .param("u", userId)
            .param("a", accountId)
            .update()

        jdbc.sql("DELETE FROM users WHERE id = :u").param("u", userId).update()

        assertEquals(0, count("user_identities"))
        assertEquals(0, count("memberships"))
        assertEquals(1, count("accounts"))
    }

    private fun insertOneTimeToken(
        userId: UUID,
        purpose: String,
    ): Int =
        jdbc
            .sql(
                "INSERT INTO one_time_tokens (user_id, purpose, token_hash, expires_at) " +
                    "VALUES (:u, :p, gen_random_uuid()::text, now() + interval '1 day')",
            ).param("u", userId)
            .param("p", purpose)
            .update()

    @Test
    fun `one-time token purpose must be EMAIL_VERIFICATION or PASSWORD_RESET`() {
        val userId = insertUser("tokens@example.com")
        assertEquals(1, insertOneTimeToken(userId, "EMAIL_VERIFICATION"))
        assertEquals(1, insertOneTimeToken(userId, "PASSWORD_RESET"))
        assertFailsWith<DataIntegrityViolationException> { insertOneTimeToken(userId, "LOGIN") }
    }

    @Test
    fun `deleting a user removes their one-time tokens`() {
        val userId = insertUser("gone@example.com")
        insertOneTimeToken(userId, "PASSWORD_RESET")

        jdbc.sql("DELETE FROM users WHERE id = :u").param("u", userId).update()

        assertEquals(0, count("one_time_tokens"))
    }

    @Test
    fun `ids are generated as time-ordered UUID version 7`() {
        assertEquals(7, insertUser("v7@example.com").version())
    }
}
