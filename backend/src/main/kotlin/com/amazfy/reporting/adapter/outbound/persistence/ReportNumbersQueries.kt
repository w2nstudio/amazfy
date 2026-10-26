package com.amazfy.reporting.adapter.outbound.persistence

import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.tenant.CurrentAccount
import com.amazfy.reporting.application.port.AttributionNumbers
import com.amazfy.reporting.application.port.ReportNumbers
import com.amazfy.reporting.domain.DateRange
import com.amazfy.reporting.domain.SpendTotals
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.ResultSet

/**
 * Adds up the daily tables with SQL: reports read many rows and return few, which the database does best.
 * Plain SQL doesn't go through @TenantId, so every query filters on account_id = CurrentAccount (see CLAUDE.md).
 */
@Component
class ReportNumbersQueries(
    private val jdbc: JdbcClient,
) : ReportNumbers {
    override fun spendByCurrency(range: DateRange): List<SpendTotals> =
        jdbc
            .sql(
                """
                SELECT currency, sum(cost_micros) AS cost_micros, sum(impressions) AS impressions, sum(clicks) AS clicks
                FROM ad_spend_daily
                WHERE account_id = :account AND date BETWEEN :from AND :to
                GROUP BY currency
                """,
            ).param("account", CurrentAccount.get())
            .param("from", range.from)
            .param("to", range.to)
            .query { rs, _ -> rs.toSpendTotals() }
            .list()

    override fun attributionByMarketplace(range: DateRange): Map<Marketplace, AttributionNumbers> =
        jdbc
            .sql(
                """
                SELECT marketplace, sum(click_throughs) AS click_throughs, sum(detail_page_views) AS detail_page_views,
                       sum(add_to_carts) AS add_to_carts, sum(purchases) AS purchases, sum(units_sold) AS units_sold,
                       sum(sales) AS sales
                FROM attribution_daily
                WHERE account_id = :account AND date BETWEEN :from AND :to
                GROUP BY marketplace
                """,
            ).param("account", CurrentAccount.get())
            .param("from", range.from)
            .param("to", range.to)
            .query { rs, _ ->
                Marketplace.valueOf(rs.getString("marketplace")) to
                    AttributionNumbers(
                        clickThroughs = rs.getLong("click_throughs"),
                        detailPageViews = rs.getLong("detail_page_views"),
                        addToCarts = rs.getLong("add_to_carts"),
                        purchases = rs.getLong("purchases"),
                        unitsSold = rs.getLong("units_sold"),
                        sales = rs.getBigDecimal("sales"),
                    )
            }.list()
            .toMap()
}

private fun ResultSet.toSpendTotals() =
    SpendTotals(getString("currency"), getLong("cost_micros"), getLong("impressions"), getLong("clicks"))
