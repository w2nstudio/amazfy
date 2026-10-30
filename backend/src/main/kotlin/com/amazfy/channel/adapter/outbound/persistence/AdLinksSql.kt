package com.amazfy.channel.adapter.outbound.persistence

import com.amazfy.channel.application.port.AdLinks
import com.amazfy.channel.domain.UnlinkedAd
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The link columns of the ads table, with plain SQL (the Google sync adds the rest of this module's persistence).
 * Plain SQL doesn't go through @TenantId, so every statement filters on account_id = CurrentAccount (see CLAUDE.md).
 */
@Component
class AdLinksSql(
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
) : AdLinks {
    override fun unlinked(): List<UnlinkedAd> =
        jdbc
            .sql(
                """
                SELECT ad.id, ad.external_id, ad.final_url, c.name AS campaign_name, g.name AS ad_group_name
                FROM ads ad
                JOIN ad_groups g ON g.id = ad.ad_group_id
                JOIN campaigns c ON c.id = g.campaign_id
                WHERE ad.account_id = :account AND ad.product_id IS NULL AND ad.brand_id IS NULL
                ORDER BY c.name, g.name, ad.final_url
                """,
            ).param("account", CurrentAccount.get())
            .query { rs, _ ->
                UnlinkedAd(
                    id = rs.getObject("id", UUID::class.java),
                    externalId = rs.getString("external_id"),
                    finalUrl = rs.getString("final_url"),
                    campaignName = rs.getString("campaign_name"),
                    adGroupName = rs.getString("ad_group_name"),
                )
            }.list()

    override fun linkUnlinkedToProducts(productOfAd: Map<UUID, UUID>) {
        val account = CurrentAccount.get()
        jdbcTemplate.batchUpdate(
            "UPDATE ads SET product_id = ?, updated_at = now() " +
                "WHERE id = ? AND account_id = ? AND product_id IS NULL AND brand_id IS NULL",
            productOfAd.map { (adId, productId) -> arrayOf<Any>(productId, adId, account) },
        )
    }

    override fun setLink(
        adId: UUID,
        productId: UUID?,
        brandId: UUID?,
    ): Boolean =
        jdbc
            .sql("UPDATE ads SET product_id = :product, brand_id = :brand, updated_at = now() WHERE id = :ad AND account_id = :account")
            .param("product", productId, java.sql.Types.OTHER)
            .param("brand", brandId, java.sql.Types.OTHER)
            .param("ad", adId)
            .param("account", CurrentAccount.get())
            .update() > 0
}
