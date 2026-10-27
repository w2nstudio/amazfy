package com.amazfy.reporting.application

import com.amazfy.reporting.application.port.ReportNumbers
import com.amazfy.reporting.domain.Breakdown
import com.amazfy.reporting.domain.BreakdownFilter
import com.amazfy.reporting.domain.BreakdownLevel
import com.amazfy.reporting.domain.BreakdownRow
import com.amazfy.reporting.domain.DateRange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * Use case: the current seller account's numbers split into rows by brand, product, campaign, ad group or ad
 * (requirement A12, the drill-down). With no filter, the rows of any level add up to the summary.
 * Callers must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional(readOnly = true)
class GetReportBreakdown(
    private val numbers: ReportNumbers,
    private val clock: Clock,
) {
    fun forRange(
        level: BreakdownLevel,
        filter: BreakdownFilter,
        from: LocalDate?,
        to: LocalDate?,
    ): ReportBreakdown {
        val range = DateRange.of(from, to, today = LocalDate.now(clock))
        val rows =
            Breakdown.of(
                level = level,
                filter = filter,
                ads = numbers.adFacts(),
                spendByAd = numbers.spendByAd(range),
                attributionByAd = numbers.attributionByAd(range).mapValues { (_, byMarketplace) -> byMarketplace.toTotals() },
                unmatched = numbers.unmatchedAttribution(range).toTotals(),
            )
        return ReportBreakdown(range, level, rows)
    }
}

data class ReportBreakdown(
    val range: DateRange,
    val level: BreakdownLevel,
    val rows: List<BreakdownRow>,
)
