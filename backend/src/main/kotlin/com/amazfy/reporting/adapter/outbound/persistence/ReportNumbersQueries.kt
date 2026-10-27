package com.amazfy.reporting.adapter.outbound.persistence

import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.tenant.CurrentAccount
import com.amazfy.reporting.application.port.AttributionNumbers
import com.amazfy.reporting.application.port.DailyAttributionNumbers
import com.amazfy.reporting.application.port.ReportNumbers
import com.amazfy.reporting.domain.AdFacts
import com.amazfy.reporting.domain.DailySpend
import com.amazfy.reporting.domain.DateRange
import com.amazfy.reporting.domain.SpendTotals
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

/**
 * Adds up the daily tables with SQL: reports read many rows and return few, which the database does best.
 * Plain SQL doesn't go through @TenantId, so every query filters on account_id = CurrentAccount (see CLAUDE.md).
 */
@Component
class ReportNumbersQueries(
    private val jdbc: JdbcClient,
) : ReportNumbers {
    override fun spendByCurrency(range: DateRange): List<SpendTotals> =
        inRange(
            """
            SELECT currency, $SPEND_SUMS
            FROM ad_spend_daily
            WHERE account_id = :account AND date BETWEEN :from AND :to
            GROUP BY currency
            """,
            range,
        ).query { rs, _ -> rs.toSpendTotals() }.list()

    override fun attributionByMarketplace(range: DateRange): Map<Marketplace, AttributionNumbers> =
        inRange(
            """
            SELECT marketplace, $ATTRIBUTION_SUMS
            FROM attribution_daily
            WHERE account_id = :account AND date BETWEEN :from AND :to
            GROUP BY marketplace
            """,
            range,
        ).query { rs, _ -> rs.marketplace() to rs.toAttributionNumbers() }.list().toMap()

    override fun adFacts(): List<AdFacts> =
        jdbc
            .sql(
                """
                SELECT ad.id, ad.external_id, ad.final_url, g.id AS ad_group_id, g.name AS ad_group_name,
                       c.id AS campaign_id, c.name AS campaign_name,
                       p.id AS product_id, p.name AS product_name, p.asin, p.marketplace,
                       b.id AS brand_id, b.name AS brand_name
                FROM ads ad
                JOIN ad_groups g ON g.id = ad.ad_group_id
                JOIN campaigns c ON c.id = g.campaign_id
                LEFT JOIN products p ON p.id = ad.product_id
                LEFT JOIN brands b ON b.id = coalesce(p.brand_id, ad.brand_id)
                WHERE ad.account_id = :account
                """,
            ).param("account", CurrentAccount.get())
            .query { rs, _ ->
                AdFacts(
                    adId = rs.uuid("id")!!,
                    adExternalId = rs.getString("external_id"),
                    finalUrl = rs.getString("final_url"),
                    adGroupId = rs.uuid("ad_group_id")!!,
                    adGroupName = rs.getString("ad_group_name"),
                    campaignId = rs.uuid("campaign_id")!!,
                    campaignName = rs.getString("campaign_name"),
                    productId = rs.uuid("product_id"),
                    productName = rs.getString("product_name"),
                    productDetail = rs.getString("asin")?.let { "$it · ${rs.getString("marketplace")}" },
                    brandId = rs.uuid("brand_id"),
                    brandName = rs.getString("brand_name"),
                )
            }.list()

    override fun spendByAd(range: DateRange): Map<UUID, List<SpendTotals>> =
        inRange(
            """
            SELECT ad_id, currency, $SPEND_SUMS
            FROM ad_spend_daily
            WHERE account_id = :account AND date BETWEEN :from AND :to
            GROUP BY ad_id, currency
            """,
            range,
        ).query { rs, _ -> rs.uuid("ad_id")!! to rs.toSpendTotals() }
            .list()
            .groupBy({ it.first }, { it.second })

    override fun attributionByAd(range: DateRange): Map<UUID, Map<Marketplace, AttributionNumbers>> =
        inRange(
            """
            SELECT ad_id, marketplace, $ATTRIBUTION_SUMS
            FROM attribution_daily
            WHERE account_id = :account AND date BETWEEN :from AND :to AND ad_id IS NOT NULL
            GROUP BY ad_id, marketplace
            """,
            range,
        ).query { rs, _ -> Triple(rs.uuid("ad_id")!!, rs.marketplace(), rs.toAttributionNumbers()) }
            .list()
            .groupBy { it.first }
            .mapValues { (_, rows) -> rows.associate { it.second to it.third } }

    override fun unmatchedAttribution(range: DateRange): Map<Marketplace, AttributionNumbers> =
        inRange(
            """
            SELECT marketplace, $ATTRIBUTION_SUMS
            FROM attribution_daily
            WHERE account_id = :account AND date BETWEEN :from AND :to AND ad_id IS NULL
            GROUP BY marketplace
            """,
            range,
        ).query { rs, _ -> rs.marketplace() to rs.toAttributionNumbers() }.list().toMap()

    override fun dailySpend(
        range: DateRange,
        adIds: Set<UUID>?,
    ): List<DailySpend> {
        if (adIds?.isEmpty() == true) return emptyList() // "IN ()" isn't valid SQL, and no ads means no numbers
        return inRange(
            """
            SELECT date, currency, $SPEND_SUMS
            FROM ad_spend_daily
            WHERE account_id = :account AND date BETWEEN :from AND :to ${onlyAds(adIds)}
            GROUP BY date, currency
            """,
            range,
            adIds,
        ).query { rs, _ -> DailySpend(rs.date(), rs.toSpendTotals()) }.list()
    }

    override fun dailyAttribution(
        range: DateRange,
        adIds: Set<UUID>?,
    ): List<DailyAttributionNumbers> {
        if (adIds?.isEmpty() == true) return emptyList()
        return inRange(
            """
            SELECT date, marketplace, $ATTRIBUTION_SUMS
            FROM attribution_daily
            WHERE account_id = :account AND date BETWEEN :from AND :to ${onlyAds(adIds)}
            GROUP BY date, marketplace
            """,
            range,
            adIds,
        ).query { rs, _ -> DailyAttributionNumbers(rs.date(), rs.marketplace(), rs.toAttributionNumbers()) }.list()
    }

    /** [adIds] null: no condition. Otherwise the ads' ids are bound as :ads, which JdbcClient expands into a list. */
    private fun onlyAds(adIds: Set<UUID>?) = if (adIds == null) "" else "AND ad_id IN (:ads)"

    private fun inRange(
        sql: String,
        range: DateRange,
        adIds: Set<UUID>? = null,
    ) = jdbc
        .sql(sql)
        .param("account", CurrentAccount.get())
        .param("from", range.from)
        .param("to", range.to)
        .let { if (adIds == null) it else it.param("ads", adIds) }

    private companion object {
        const val SPEND_SUMS = "sum(cost_micros) AS cost_micros, sum(impressions) AS impressions, sum(clicks) AS clicks"
        const val ATTRIBUTION_SUMS =
            "sum(click_throughs) AS click_throughs, sum(detail_page_views) AS detail_page_views, " +
                "sum(add_to_carts) AS add_to_carts, sum(purchases) AS purchases, sum(units_sold) AS units_sold, sum(sales) AS sales"
    }
}

private fun ResultSet.uuid(column: String): UUID? = getObject(column, UUID::class.java)

private fun ResultSet.date(): LocalDate = getObject("date", LocalDate::class.java)

private fun ResultSet.marketplace() = Marketplace.valueOf(getString("marketplace"))

private fun ResultSet.toSpendTotals() =
    SpendTotals(getString("currency"), getLong("cost_micros"), getLong("impressions"), getLong("clicks"))

private fun ResultSet.toAttributionNumbers() =
    AttributionNumbers(
        clickThroughs = getLong("click_throughs"),
        detailPageViews = getLong("detail_page_views"),
        addToCarts = getLong("add_to_carts"),
        purchases = getLong("purchases"),
        unitsSold = getLong("units_sold"),
        sales = getBigDecimal("sales"),
    )
