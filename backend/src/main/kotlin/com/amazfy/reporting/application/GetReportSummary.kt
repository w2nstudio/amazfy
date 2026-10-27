package com.amazfy.reporting.application

import com.amazfy.common.amazon.Marketplace
import com.amazfy.reporting.application.port.AttributionNumbers
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
        return ReportSummary(
            range,
            CurrencyTotals.combine(numbers.spendByCurrency(range), numbers.attributionByMarketplace(range).toTotals()),
        )
    }
}

data class ReportSummary(
    val range: DateRange,
    val currencies: List<CurrencyTotals>,
)

/** Attribution numbers per marketplace as totals in each marketplace's currency (shared by the reporting use cases). */
internal fun Map<Marketplace, AttributionNumbers>.toTotals(): List<AttributionTotals> = map { (marketplace, n) -> n.toTotals(marketplace) }

internal fun AttributionNumbers.toTotals(marketplace: Marketplace) =
    AttributionTotals(marketplace.currency, clickThroughs, detailPageViews, addToCarts, purchases, unitsSold, sales)
