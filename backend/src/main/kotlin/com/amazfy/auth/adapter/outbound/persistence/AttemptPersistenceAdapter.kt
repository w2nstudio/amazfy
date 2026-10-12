package com.amazfy.auth.adapter.outbound.persistence

import com.amazfy.auth.application.port.AttemptRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/**
 * Plain SQL instead of JPA: rows are never loaded as objects, only inserted, counted and deleted in bulk.
 * Table: V5__auth_attempts.sql.
 */
@Component
class AttemptPersistenceAdapter(
    private val jdbc: JdbcClient,
) : AttemptRepository {
    override fun record(
        keyHash: String,
        at: Instant,
    ) {
        jdbc
            .sql("INSERT INTO auth_attempts (key_hash, attempted_at) VALUES (:key, :at)")
            .param("key", keyHash)
            .param("at", Timestamp.from(at))
            .update()
    }

    override fun nthLatestSince(
        keyHash: String,
        n: Int,
        since: Instant,
    ): Instant? =
        jdbc
            .sql(
                "SELECT attempted_at FROM auth_attempts WHERE key_hash = :key AND attempted_at > :since " +
                    "ORDER BY attempted_at DESC OFFSET :skip LIMIT 1",
            ).param("key", keyHash)
            .param("since", Timestamp.from(since))
            .param("skip", n - 1)
            .query(Timestamp::class.java)
            .optional()
            .map { it.toInstant() }
            .orElse(null)

    override fun deleteAll(keyHash: String) {
        jdbc.sql("DELETE FROM auth_attempts WHERE key_hash = :key").param("key", keyHash).update()
    }

    override fun deleteOlderThan(cutoff: Instant): Int =
        jdbc.sql("DELETE FROM auth_attempts WHERE attempted_at < :cutoff").param("cutoff", Timestamp.from(cutoff)).update()
}
