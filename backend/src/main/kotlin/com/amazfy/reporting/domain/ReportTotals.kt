package com.amazfy.reporting.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** The days a report covers, both included. At most [MAX_DAYS]; never empty. */
data class DateRange(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        if (from.isAfter(to)) throw InvalidDateRangeException("The start date must not be after the end date.")
        if (to.toEpochDay() - from.toEpochDay() + 1 > MAX_DAYS) throw InvalidDateRangeException("A report covers at most $MAX_DAYS days.")
    }

    /** Every day from [from] to [to], in order. */
    val days: List<LocalDate> get() = from.datesUntil(to.plusDays(1)).toList()

    companion object {
        const val MAX_DAYS = 366
        const val DEFAULT_DAYS = 30L

        /**
         * The range asked for, filling what's missing: up to yesterday (Amazon has no numbers for today yet), and
         * [DEFAULT_DAYS] days long.
         */
        fun of(
            from: LocalDate?,
            to: LocalDate?,
            today: LocalDate,
        ): DateRange {
            val last = to ?: today.minusDays(1)
            return DateRange(from ?: last.minusDays(DEFAULT_DAYS - 1), last)
        }
    }
}

class InvalidDateRangeException(
    message: String,
) : AppException(ErrorKind.BAD_REQUEST, message)

/** Google's numbers for a range in one currency (the Google Ads account's). */
data class SpendTotals(
    val currency: String,
    val costMicros: Long,
    val impressions: Long,
    val clicks: Long,
)

/** Amazon Attribution's numbers for a range in one currency (the marketplace's). */
data class AttributionTotals(
    val currency: String,
    val clickThroughs: Long,
    val detailPageViews: Long,
    val addToCarts: Long,
    val purchases: Long,
    val unitsSold: Long,
    val sales: BigDecimal,
) {
    operator fun plus(other: AttributionTotals): AttributionTotals {
        require(currency == other.currency) { "Amounts in different currencies are never added up" }
        return AttributionTotals(
            currency,
            clickThroughs + other.clickThroughs,
            detailPageViews + other.detailPageViews,
            addToCarts + other.addToCarts,
            purchases + other.purchases,
            unitsSold + other.unitsSold,
            sales + other.sales,
        )
    }
}

/**
 * Spend and sales in one currency, with the metrics sellers judge ads by (requirements, "Metric definitions").
 * A metric is null when it can't be calculated: no spend (ROAS), no purchases (CPA), no click-throughs (conversion rate).
 * [spend] is exact (micros, 6 decimals), so metrics use exact amounts and are rounded to 2 decimals only at the end;
 * round spend to cents only for display.
 */
data class CurrencyTotals(
    val currency: String,
    val spend: BigDecimal,
    val impressions: Long,
    val clicks: Long,
    val clickThroughs: Long,
    val detailPageViews: Long,
    val addToCarts: Long,
    val purchases: Long,
    val unitsSold: Long,
    val sales: BigDecimal,
) {
    /** Return on ad spend: sales ÷ spend. 3.91 = each dollar spent brought $3.91 in Amazon sales. */
    val roas: BigDecimal? get() = if (spend.signum() == 0) null else sales.divide(spend, 2, RoundingMode.HALF_UP)

    /** Cost per acquisition: spend ÷ purchases, in the currency. */
    val cpa: BigDecimal? get() = if (purchases == 0L) null else spend.divide(BigDecimal(purchases), 2, RoundingMode.HALF_UP)

    /** Purchases ÷ click-throughs × 100, in percent. Amazon's click count, since purchases are Amazon's too. */
    val conversionRate: BigDecimal? get() =
        if (clickThroughs == 0L) null else BigDecimal(purchases * 100).divide(BigDecimal(clickThroughs), 2, RoundingMode.HALF_UP)

    companion object {
        /**
         * One entry per currency that has spend or sales, sorted by code. A currency with only one side (e.g. EUR spend
         * for a UK listing, whose sales are GBP) gets zeros on the other side, so it shows up without a misleading ROAS.
         */
        fun combine(
            spend: List<SpendTotals>,
            attribution: List<AttributionTotals>,
        ): List<CurrencyTotals> {
            val spendBy = spend.groupBy { it.currency }
            val attributionBy = attribution.groupBy { it.currency }.mapValues { (_, totals) -> totals.reduce(AttributionTotals::plus) }
            return (spendBy.keys + attributionBy.keys).sorted().map { currency ->
                val google = spendBy[currency].orEmpty()
                val amazon = attributionBy[currency]
                CurrencyTotals(
                    currency = currency,
                    spend = BigDecimal.valueOf(google.sumOf { it.costMicros }, 6),
                    impressions = google.sumOf { it.impressions },
                    clicks = google.sumOf { it.clicks },
                    clickThroughs = amazon?.clickThroughs ?: 0,
                    detailPageViews = amazon?.detailPageViews ?: 0,
                    addToCarts = amazon?.addToCarts ?: 0,
                    purchases = amazon?.purchases ?: 0,
                    unitsSold = amazon?.unitsSold ?: 0,
                    sales = (amazon?.sales ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                )
            }
        }
    }
}
