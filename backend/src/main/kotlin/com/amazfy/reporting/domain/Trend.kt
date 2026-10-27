package com.amazfy.reporting.domain

import java.time.LocalDate

/** Google spend on one day, in one currency. */
data class DailySpend(
    val date: LocalDate,
    val spend: SpendTotals,
)

/** Amazon Attribution numbers on one day, in one currency. */
data class DailyAttribution(
    val date: LocalDate,
    val attribution: AttributionTotals,
)

data class TrendDay(
    val date: LocalDate,
    val totals: CurrencyTotals,
)

/** The totals of each day in one currency (requirement A13, the trend chart). */
data class TrendSeries(
    val currency: String,
    val days: List<TrendDay>,
)

object Trend {
    /**
     * One series per currency that has spend or sales in [range], sorted by code. Each has every day of the range,
     * with zeros on days without numbers, so a chart shows the gaps instead of skipping them.
     */
    fun of(
        range: DateRange,
        spend: List<DailySpend>,
        attribution: List<DailyAttribution>,
    ): List<TrendSeries> {
        val spendOn = spend.groupBy({ it.date }, { it.spend })
        val attributionOn = attribution.groupBy({ it.date }, { it.attribution })
        val currencies = (spend.map { it.spend.currency } + attribution.map { it.attribution.currency }).toSortedSet()
        return currencies.map { currency ->
            TrendSeries(
                currency,
                range.days.map { date ->
                    // The zero spend makes combine return exactly one entry for the currency, also on empty days
                    val totals =
                        CurrencyTotals.combine(
                            spend = spendOn[date].orEmpty().filter { it.currency == currency } + SpendTotals(currency, 0, 0, 0),
                            attribution = attributionOn[date].orEmpty().filter { it.currency == currency },
                        )
                    TrendDay(date, totals.single())
                },
            )
        }
    }
}
