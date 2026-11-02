package com.amazfy.channel.adapter.outbound.persistence

import com.amazfy.channel.application.port.GoogleAdsSyncQueue
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * The one statement here that isn't filtered on CurrentAccount (see CLAUDE.md): it decides which account a job works for
 * next, and returns only that account's id. SKIP LOCKED lets a second server move on instead of waiting for this row.
 */
@Component
class GoogleAdsSyncQueueSql(
    private val jdbc: JdbcClient,
) : GoogleAdsSyncQueue {
    override fun takeNextDue(
        syncedBefore: Instant,
        attemptedBefore: Instant,
        now: Instant,
    ): UUID? =
        jdbc
            .sql(
                """
                UPDATE google_ads_connections SET sync_attempted_at = :now
                WHERE account_id = (
                    SELECT account_id FROM google_ads_connections
                    WHERE customer_id IS NOT NULL
                        AND sync_problem IS DISTINCT FROM 'CONNECTION_EXPIRED'
                        AND (synced_at IS NULL OR synced_at < :syncedBefore)
                        AND (sync_attempted_at IS NULL OR sync_attempted_at < :attemptedBefore)
                    ORDER BY sync_attempted_at NULLS FIRST
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING account_id
                """,
            ).param("now", now.atOffset(ZoneOffset.UTC))
            .param("syncedBefore", syncedBefore.atOffset(ZoneOffset.UTC))
            .param("attemptedBefore", attemptedBefore.atOffset(ZoneOffset.UTC))
            .query(UUID::class.java)
            .optional()
            .orElse(null)
}
