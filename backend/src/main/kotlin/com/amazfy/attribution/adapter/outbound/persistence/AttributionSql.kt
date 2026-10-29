package com.amazfy.attribution.adapter.outbound.persistence

import com.amazfy.attribution.application.port.AttributionLineStore
import com.amazfy.attribution.application.port.GoogleAdLookup
import com.amazfy.attribution.domain.AdKey
import com.amazfy.attribution.domain.AttributionLine
import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.Date
import java.util.UUID

/**
 * Attribution lines and the Google ads they match, with plain SQL: an upload writes thousands of lines in one batch.
 * Plain SQL doesn't go through @TenantId, so every statement uses account_id = CurrentAccount (see CLAUDE.md).
 */
@Component
class AttributionSql(
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
) : GoogleAdLookup,
    AttributionLineStore {
    override fun idsOf(keys: Set<AdKey>): Map<AdKey, UUID> {
        if (keys.isEmpty()) return emptyMap()
        // All the account's Google ads (thousands at most), matched in memory: simpler than one lookup per line
        return jdbc
            .sql(
                """
                SELECT c.external_id AS campaign, g.external_id AS ad_group, ad.external_id AS ad, ad.id
                FROM ads ad
                JOIN ad_groups g ON g.id = ad.ad_group_id
                JOIN campaigns c ON c.id = g.campaign_id
                WHERE ad.account_id = :account AND c.channel = 'GOOGLE'
                """,
            ).param("account", CurrentAccount.get())
            .query { rs, _ ->
                AdKey(rs.getString("campaign"), rs.getString("ad_group"), rs.getString("ad")) to rs.getObject("id", UUID::class.java)
            }.list()
            .filter { it.first in keys }
            .toMap()
    }

    override fun save(
        marketplace: Marketplace,
        lines: Map<AttributionLine, UUID?>,
    ) {
        val account = CurrentAccount.get()
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO attribution_daily (account_id, source, marketplace, date, publisher, campaign_external_id,
                ad_group_external_id, creative_external_id, ad_id, click_throughs, detail_page_views, add_to_carts,
                purchases, units_sold, sales)
            VALUES (?, 'CSV', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (account_id, marketplace, publisher, campaign_external_id, ad_group_external_id, creative_external_id, date)
            DO UPDATE SET source = 'CSV', ad_id = EXCLUDED.ad_id, click_throughs = EXCLUDED.click_throughs,
                detail_page_views = EXCLUDED.detail_page_views, add_to_carts = EXCLUDED.add_to_carts,
                purchases = EXCLUDED.purchases, units_sold = EXCLUDED.units_sold, sales = EXCLUDED.sales, updated_at = now()
            """,
            lines.map { (line, adId) ->
                arrayOf<Any?>(
                    account,
                    marketplace.name,
                    Date.valueOf(line.date),
                    line.publisher,
                    line.campaignId,
                    line.adGroupId,
                    line.creativeId,
                    adId,
                    line.clickThroughs,
                    line.detailPageViews,
                    line.addToCarts,
                    line.purchases,
                    line.unitsSold,
                    line.sales,
                )
            },
        )
    }
}
