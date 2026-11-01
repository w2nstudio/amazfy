package com.amazfy.channel.adapter.outbound.persistence

import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.GoogleAdsConnection
import com.amazfy.channel.domain.GoogleAdsConnectionExpiredException
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
 * account_id = CurrentAccount (see CLAUDE.md). Tokens are encrypted here: they are never stored as plain text.
 */
@Component
class GoogleAdsConnectionsSql(
    private val jdbc: JdbcClient,
    private val cipher: SecretCipher,
) : GoogleAdsConnections {
    override fun current(): GoogleAdsConnection? =
        jdbc
            .sql(
                """
                SELECT connected_at, customer_id, login_customer_id, account_name
                FROM google_ads_connections WHERE account_id = :account
                """,
            ).param("account", CurrentAccount.get())
            .query { rs, _ ->
                val customerId = rs.getString("customer_id")
                GoogleAdsConnection(
                    connectedAt = rs.getObject("connected_at", OffsetDateTime::class.java).toInstant(),
                    account =
                        customerId?.let {
                            GoogleAdsAccount(it, rs.getString("account_name"), rs.getString("login_customer_id"))
                        },
                )
            }.optional()
            .orElse(null)

    override fun refreshToken(): String? {
        val account = CurrentAccount.get()
        val encrypted =
            jdbc
                .sql("SELECT encrypted_refresh_token FROM google_ads_connections WHERE account_id = :account")
                .param("account", account)
                .query(String::class.java)
                .optional()
                .orElse(null) ?: return null
        return cipher.decrypt(encrypted, tokenContext(account)) ?: throw GoogleAdsConnectionExpiredException()
    }

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

    override fun chooseAccount(account: GoogleAdsAccount) {
        jdbc
            .sql(
                """
                UPDATE google_ads_connections
                SET customer_id = :customerId, login_customer_id = :loginCustomerId, account_name = :name
                WHERE account_id = :account
                """,
            ).param("customerId", account.customerId)
            .param("loginCustomerId", account.loginCustomerId)
            .param("name", account.name)
            .param("account", CurrentAccount.get())
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
