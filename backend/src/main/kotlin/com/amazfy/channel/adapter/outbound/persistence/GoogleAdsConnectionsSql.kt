package com.amazfy.channel.adapter.outbound.persistence

import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.domain.GoogleAdsConnection
import com.amazfy.common.crypto.SecretCipher
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * The google_ads_connections table, with plain SQL like the rest of this module, so every statement filters on
 * account_id = CurrentAccount (see CLAUDE.md). Tokens are encrypted here: they are never stored or passed on as plain text.
 */
@Component
class GoogleAdsConnectionsSql(
    private val jdbc: JdbcClient,
    private val cipher: SecretCipher,
) : GoogleAdsConnections {
    override fun current(): GoogleAdsConnection? =
        jdbc
            .sql("SELECT connected_at FROM google_ads_connections WHERE account_id = :account")
            .param("account", CurrentAccount.get())
            .query { rs, _ -> GoogleAdsConnection(rs.getObject("connected_at", OffsetDateTime::class.java).toInstant()) }
            .optional()
            .orElse(null)

    override fun save(
        refreshToken: String,
        connectedAt: Instant,
    ) {
        val account = CurrentAccount.get()
        jdbc
            .sql(
                """
                INSERT INTO google_ads_connections (account_id, encrypted_refresh_token, connected_at)
                VALUES (:account, :token, :connectedAt)
                ON CONFLICT (account_id) DO UPDATE
                    SET encrypted_refresh_token = excluded.encrypted_refresh_token, connected_at = excluded.connected_at
                """,
            ).param("account", account)
            .param("token", cipher.encrypt(refreshToken, tokenContext(account)))
            .param("connectedAt", connectedAt.atOffset(ZoneOffset.UTC))
            .update()
    }

    override fun delete() {
        jdbc
            .sql("DELETE FROM google_ads_connections WHERE account_id = :account")
            .param("account", CurrentAccount.get())
            .update()
    }

    companion object {
        /** A token can only be decrypted for its own seller account. */
        fun tokenContext(accountId: UUID) = "google-ads-refresh-token:$accountId"
    }
}
