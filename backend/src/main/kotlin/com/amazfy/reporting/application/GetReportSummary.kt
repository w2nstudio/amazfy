package com.amazfy.reporting.application

import com.amazfy.reporting.application.port.ReportNumbers
import com.amazfy.reporting.domain.AttributionTotals
import com.amazfy.reporting.domain.CurrencyTotals
import com.amazfy.reporting.domain.DateRange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * Use case: the current seller account's totals for a date range, per currency (requirement A11).
 * Covers all its ads, including Brand Store and unmatched ones; the drill-down splits them up.
 * Callers must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional(readOnly = true)
class GetReportSummary(
    private val numbers: ReportNumbers,
    private val clock: Clock,
) {
    fun forRange(
        from: LocalDate?,
        to: LocalDate?,
    ): ReportSummary {
        val range = DateRange.of(from, to, today = LocalDate.now(clock))
        val attribution =
            numbers.attributionByMarketplace(range).map { (marketplace, n) ->
                AttributionTotals(marketplace.currency, n.clickThroughs, n.detailPageViews, n.addToCarts, n.purchases, n.unitsSold, n.sales)
            }
        return ReportSummary(range, CurrencyTotals.combine(numbers.spendByCurrency(range), attribution))
    }
}

data class ReportSummary(
    val range: DateRange,
    val currencies: List<CurrencyTotals>,
)
