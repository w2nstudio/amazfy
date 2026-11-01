package com.amazfy.channel.adapter.outbound.persistence

import com.amazfy.channel.application.port.Campaigns
import com.amazfy.channel.domain.CampaignAd
import com.amazfy.channel.domain.CampaignAdGroup
import com.amazfy.channel.domain.CampaignSummary
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.util.UUID

/** Reads campaigns, ad groups and ads with plain SQL, so every statement filters on account_id = CurrentAccount (see CLAUDE.md). */
@Component
class CampaignsSql(
    private val jdbc: JdbcClient,
) : Campaigns {
    override fun all(): List<CampaignSummary> =
        jdbc
            .sql(
                """
                SELECT c.id, c.external_id, c.channel_account_id, c.name, c.status, c.daily_budget_micros, c.budget_currency,
                    (SELECT count(*) FROM ad_groups g WHERE g.campaign_id = c.id AND g.account_id = c.account_id) AS ad_group_count,
                    (SELECT count(*) FROM ads ad JOIN ad_groups g ON g.id = ad.ad_group_id
                     WHERE g.campaign_id = c.id AND ad.account_id = c.account_id) AS ad_count
                FROM campaigns c
                WHERE c.account_id = :account
                ORDER BY c.status = 'REMOVED', c.name, c.external_id
                """,
            ).param("account", CurrentAccount.get())
            .query { rs, _ ->
                CampaignSummary(
                    id = rs.getObject("id", UUID::class.java),
                    externalId = rs.getString("external_id"),
                    channelAccountId = rs.getString("channel_account_id"),
                    name = rs.getString("name"),
                    status = rs.getString("status"),
                    dailyBudgetMicros = rs.getObject("daily_budget_micros") as Long?,
                    budgetCurrency = rs.getString("budget_currency"),
                    adGroupCount = rs.getInt("ad_group_count"),
                    adCount = rs.getInt("ad_count"),
                )
            }.list()

    override fun adGroupsOf(campaignId: UUID): List<CampaignAdGroup>? {
        val account = CurrentAccount.get()
        val exists =
            jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM campaigns WHERE id = :campaign AND account_id = :account)")
                .param("campaign", campaignId)
                .param("account", account)
                .query(Boolean::class.java)
                .single()
        if (!exists) return null

        val adsOfGroup =
            jdbc
                .sql(
                    """
                    SELECT ad.ad_group_id, ad.id, ad.external_id, ad.final_url, ad.status, p.name AS product_name, b.name AS brand_name
                    FROM ads ad
                    JOIN ad_groups g ON g.id = ad.ad_group_id
                    LEFT JOIN products p ON p.id = ad.product_id
                    LEFT JOIN brands b ON b.id = ad.brand_id
                    WHERE g.campaign_id = :campaign AND ad.account_id = :account
                    ORDER BY ad.status = 'REMOVED', ad.final_url, ad.external_id
                    """,
                ).param("campaign", campaignId)
                .param("account", account)
                .query { rs, _ ->
                    rs.getObject("ad_group_id", UUID::class.java) to
                        CampaignAd(
                            id = rs.getObject("id", UUID::class.java),
                            externalId = rs.getString("external_id"),
                            finalUrl = rs.getString("final_url"),
                            status = rs.getString("status"),
                            productName = rs.getString("product_name"),
                            brandName = rs.getString("brand_name"),
                        )
                }.list()
                .groupBy({ it.first }, { it.second })

        return jdbc
            .sql(
                """
                SELECT id, external_id, name, status
                FROM ad_groups
                WHERE campaign_id = :campaign AND account_id = :account
                ORDER BY status = 'REMOVED', name, external_id
                """,
            ).param("campaign", campaignId)
            .param("account", account)
            .query { rs, _ ->
                val id = rs.getObject("id", UUID::class.java)
                CampaignAdGroup(
                    id = id,
                    externalId = rs.getString("external_id"),
                    name = rs.getString("name"),
                    status = rs.getString("status"),
                    ads = adsOfGroup[id].orEmpty(),
                )
            }.list()
    }
}
