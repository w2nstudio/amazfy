package com.amazfy.reporting.application

import com.amazfy.reporting.application.port.ReportNumbers
import com.amazfy.reporting.domain.BreakdownFilter
import com.amazfy.reporting.domain.DailyAttribution
import com.amazfy.reporting.domain.DateRange
import com.amazfy.reporting.domain.Trend
import com.amazfy.reporting.domain.TrendSeries
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Use case: the current seller account's totals per day (requirement A13, the trend chart), for the ads a drill-down
 * filter selects. Without a filter it covers the whole account, unmatched numbers included, and adds up to the summary.
 * Callers must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional(readOnly = true)
class GetReportTrend(
    private val numbers: ReportNumbers,
    private val clock: Clock,
) {
    fun forRange(
        filter: BreakdownFilter,
        from: LocalDate?,
        to: LocalDate?,
    ): ReportTrend {
        val range = DateRange.of(from, to, today = LocalDate.now(clock))
        // The database adds up per day; only which ads count is decided here, with the drill-down's own rule
        val adIds = if (filter.isEmpty) null else selectedAds(filter)
        val series =
            Trend.of(
                range = range,
                spend = numbers.dailySpend(range, adIds),
                attribution = numbers.dailyAttribution(range, adIds).map { DailyAttribution(it.date, it.numbers.toTotals(it.marketplace)) },
            )
        return ReportTrend(range, series)
    }

    private fun selectedAds(filter: BreakdownFilter): Set<UUID> = numbers.adFacts().filter(filter::matches).mapTo(HashSet()) { it.adId }
}

data class ReportTrend(
    val range: DateRange,
    val series: List<TrendSeries>,
)
