package com.amazfy.reporting.application.port

import com.amazfy.common.amazon.Marketplace
import com.amazfy.reporting.domain.DateRange
import com.amazfy.reporting.domain.SpendTotals
import java.math.BigDecimal

/**
 * Reads the daily numbers reports add up, always within CurrentAccount (the current seller account).
 * Implemented with SQL over the channel and attribution tables.
 */
interface ReportNumbers {
    /** Google spend in the range, one entry per currency. */
    fun spendByCurrency(range: DateRange): List<SpendTotals>

    /** Attribution numbers in the range, one entry per marketplace (each has its own currency). */
    fun attributionByMarketplace(range: DateRange): Map<Marketplace, AttributionNumbers>
}

/** Attribution's numbers for one marketplace; the currency follows from the marketplace. */
data class AttributionNumbers(
    val clickThroughs: Long,
    val detailPageViews: Long,
    val addToCarts: Long,
    val purchases: Long,
    val unitsSold: Long,
    val sales: BigDecimal,
)
