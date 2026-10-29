package com.amazfy.reporting.adapter.outbound.persistence

import com.amazfy.common.tenant.CurrentAccount
import com.amazfy.reporting.application.port.ReportTotalsStore
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Rebuilds the rollup tables with set-based SQL: delete the period's totals, add them up again from the daily rows.
 * Plain SQL doesn't go through @TenantId, so every statement filters on account_id = CurrentAccount (see CLAUDE.md).
 */
@Component
class ReportTotalsSql(
    private val jdbc: JdbcClient,
) : ReportTotalsStore {
    override fun rebuild(days: ClosedRange<LocalDate>?) {
        // Whole calendar months: a month's total is only right with all of its days
        val from = days?.start?.withDayOfMonth(1)
        val to = days?.endInclusive?.let { it.withDayOfMonth(it.lengthOfMonth()) }
        val dates = if (days == null) "" else "AND date BETWEEN :from AND :to"
        val months = if (days == null) "" else "AND month BETWEEN :from AND :to"
        val month = "date_trunc('month', date::timestamp)::date"
        listOf(
            "DELETE FROM ad_spend_monthly WHERE account_id = :account $months",
            """
            INSERT INTO ad_spend_monthly (account_id, ad_id, month, currency, $SPEND_COLUMNS)
            SELECT account_id, ad_id, $month, currency, $SPEND_SUMS
            FROM ad_spend_daily WHERE account_id = :account $dates
            GROUP BY account_id, ad_id, $month, currency
            """,
            "DELETE FROM attribution_monthly WHERE account_id = :account $months",
            """
            INSERT INTO attribution_monthly (account_id, ad_id, month, marketplace, $ATTRIBUTION_COLUMNS)
            SELECT account_id, ad_id, $month, marketplace, $ATTRIBUTION_SUMS
            FROM attribution_daily WHERE account_id = :account $dates
            GROUP BY account_id, ad_id, $month, marketplace
            """,
            "DELETE FROM account_spend_daily WHERE account_id = :account $dates",
            """
            INSERT INTO account_spend_daily (account_id, date, currency, $SPEND_COLUMNS)
            SELECT account_id, date, currency, $SPEND_SUMS
            FROM ad_spend_daily WHERE account_id = :account $dates
            GROUP BY account_id, date, currency
            """,
            "DELETE FROM account_attribution_daily WHERE account_id = :account $dates",
            """
            INSERT INTO account_attribution_daily (account_id, date, marketplace, $ATTRIBUTION_COLUMNS)
            SELECT account_id, date, marketplace, $ATTRIBUTION_SUMS
            FROM attribution_daily WHERE account_id = :account $dates
            GROUP BY account_id, date, marketplace
            """,
        ).forEach { sql ->
            jdbc
                .sql(sql)
                .param("account", CurrentAccount.get())
                .let { if (days == null) it else it.param("from", from).param("to", to) }
                .update()
        }
    }

    private companion object {
        const val SPEND_COLUMNS = "impressions, clicks, cost_micros"
        const val SPEND_SUMS = "sum(impressions), sum(clicks), sum(cost_micros)"
        const val ATTRIBUTION_COLUMNS = "click_throughs, detail_page_views, add_to_carts, purchases, units_sold, sales"
        const val ATTRIBUTION_SUMS =
            "sum(click_throughs), sum(detail_page_views), sum(add_to_carts), sum(purchases), sum(units_sold), sum(sales)"
    }
}
