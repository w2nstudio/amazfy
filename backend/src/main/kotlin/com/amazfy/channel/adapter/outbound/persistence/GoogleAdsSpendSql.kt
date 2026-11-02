package com.amazfy.channel.adapter.outbound.persistence

import com.amazfy.channel.application.port.GoogleAdsSpendStore
import com.amazfy.channel.domain.GoogleAdSpend
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

/** Writes spend with plain SQL: a large seller has hundreds of thousands of rows. Every statement uses account_id = CurrentAccount (see CLAUDE.md). */
@Component
class GoogleAdsSpendSql(
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
) : GoogleAdsSpendStore {
    override fun replace(
        customerId: String,
        days: ClosedRange<LocalDate>,
        currency: String,
        spend: List<GoogleAdSpend>,
    ): Long {
        val account = CurrentAccount.get()
        val adIds = adIds(account, customerId)
        jdbc
            .sql(
                """
                DELETE FROM ad_spend_daily s
                USING ads ad, ad_groups g, campaigns c
                WHERE s.ad_id = ad.id AND ad.ad_group_id = g.id AND g.campaign_id = c.id
                    AND s.account_id = :account AND c.account_id = :account AND c.channel_account_id = :customerId
                    AND s.date BETWEEN :from AND :to
                """,
            ).param("account", account)
            .param("customerId", customerId)
            .param("from", days.start)
            .param("to", days.endInclusive)
            .update()

        val stored = spend.mapNotNull { row -> adIds[Triple(row.campaignId, row.adGroupId, row.adId)]?.let { it to row } }
        // One statement per chunk, with arrays: many times faster than a batch of single rows, and this holds a transaction
        stored.chunked(CHUNK_SIZE).forEach { chunk ->
            jdbcTemplate.update(
                """
                INSERT INTO ad_spend_daily (account_id, currency, ad_id, date, impressions, clicks, cost_micros)
                SELECT ?, ?, * FROM unnest(?::uuid[], ?::date[], ?::bigint[], ?::bigint[], ?::bigint[])
                """,
            ) { statement ->
                val connection = statement.connection

                fun array(
                    type: String,
                    values: List<Any>,
                ) = connection.createArrayOf(type, values.toTypedArray())
                statement.setObject(1, account)
                statement.setString(2, currency)
                statement.setArray(3, array("uuid", chunk.map { (adId, _) -> adId }))
                statement.setArray(4, array("date", chunk.map { (_, row) -> java.sql.Date.valueOf(row.date) }))
                statement.setArray(5, array("bigint", chunk.map { (_, row) -> row.impressions }))
                statement.setArray(6, array("bigint", chunk.map { (_, row) -> row.clicks }))
                statement.setArray(7, array("bigint", chunk.map { (_, row) -> row.costMicros }))
            }
        }
        return stored.sumOf { (_, row) -> row.costMicros }
    }

    /** Amazfy's id of each ad from this Google Ads account, by Google's ids. */
    private fun adIds(
        account: UUID,
        customerId: String,
    ): Map<Triple<String, String, String>, UUID> =
        jdbc
            .sql(
                """
                SELECT ad.id, c.external_id AS campaign, g.external_id AS ad_group, ad.external_id AS ad
                FROM ads ad
                JOIN ad_groups g ON g.id = ad.ad_group_id
                JOIN campaigns c ON c.id = g.campaign_id
                WHERE ad.account_id = :account AND c.channel = 'GOOGLE' AND c.channel_account_id = :customerId
                """,
            ).param("account", account)
            .param("customerId", customerId)
            .query { rs, _ ->
                Triple(rs.getString("campaign"), rs.getString("ad_group"), rs.getString("ad")) to rs.getObject("id", UUID::class.java)
            }.list()
            .toMap()

    private companion object {
        const val CHUNK_SIZE = 10_000
    }
}
