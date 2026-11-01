package com.amazfy.channel.adapter.outbound.persistence

import com.amazfy.channel.application.port.GoogleAdsStructures
import com.amazfy.channel.domain.GoogleAdsStructure
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset

/**
 * Writes a sync with plain SQL: a large seller has thousands of ads, written in batches. Every statement uses
 * account_id = CurrentAccount (see CLAUDE.md). Children find their parent by Google's ids, within this seller account.
 * A row only counts as updated when something changed, so a daily sync doesn't rewrite every row. product_id and
 * brand_id are never touched: an ad's link stays, also when its final URL changes.
 */
@Component
class GoogleAdsStructuresSql(
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
) : GoogleAdsStructures {
    override fun save(
        customerId: String,
        structure: GoogleAdsStructure,
        syncedAt: Instant,
    ) {
        val account = CurrentAccount.get()
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO campaigns (account_id, channel, external_id, channel_account_id, name, status, daily_budget_micros,
                budget_currency)
            VALUES (?, 'GOOGLE', ?, ?, ?, ?, ?, ?)
            ON CONFLICT (account_id, channel, external_id) DO UPDATE
                SET channel_account_id = excluded.channel_account_id, name = excluded.name, status = excluded.status,
                    daily_budget_micros = excluded.daily_budget_micros, budget_currency = excluded.budget_currency,
                    updated_at = now()
                WHERE (campaigns.channel_account_id, campaigns.name, campaigns.status, campaigns.daily_budget_micros,
                       campaigns.budget_currency)
                    IS DISTINCT FROM (excluded.channel_account_id, excluded.name, excluded.status,
                       excluded.daily_budget_micros, excluded.budget_currency)
            """,
            structure.campaigns.map {
                arrayOf<Any?>(account, it.id, customerId, it.name, it.status, it.dailyBudgetMicros, it.currency)
            },
        )
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ad_groups (account_id, campaign_id, external_id, name, status)
            SELECT c.account_id, c.id, ?, ?, ?
            FROM campaigns c
            WHERE c.account_id = ? AND c.channel = 'GOOGLE' AND c.external_id = ?
            ON CONFLICT (campaign_id, external_id) DO UPDATE
                SET name = excluded.name, status = excluded.status, updated_at = now()
                WHERE (ad_groups.name, ad_groups.status) IS DISTINCT FROM (excluded.name, excluded.status)
            """,
            structure.adGroups.map { arrayOf<Any?>(it.id, it.name, it.status, account, it.campaignId) },
        )
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ads (account_id, ad_group_id, external_id, final_url, status)
            SELECT g.account_id, g.id, ?, ?, ?
            FROM ad_groups g
            JOIN campaigns c ON c.id = g.campaign_id
            WHERE g.account_id = ? AND c.channel = 'GOOGLE' AND c.external_id = ? AND g.external_id = ?
            ON CONFLICT (ad_group_id, external_id) DO UPDATE
                SET final_url = excluded.final_url, status = excluded.status, updated_at = now()
                WHERE (ads.final_url, ads.status) IS DISTINCT FROM (excluded.final_url, excluded.status)
            """,
            structure.ads.map { arrayOf<Any?>(it.id, it.finalUrl, it.status, account, it.campaignId, it.adGroupId) },
        )
        jdbc
            .sql("UPDATE google_ads_connections SET synced_at = :at WHERE account_id = :account AND customer_id = :customerId")
            .param("at", syncedAt.atOffset(ZoneOffset.UTC))
            .param("account", account)
            .param("customerId", customerId)
            .update()
    }
}
