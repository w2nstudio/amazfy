package com.amazfy.reporting.application.port

import com.amazfy.common.amazon.Marketplace
import com.amazfy.reporting.domain.AdFacts
import com.amazfy.reporting.domain.DailySpend
import com.amazfy.reporting.domain.DateRange
import com.amazfy.reporting.domain.SpendTotals
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Reads the daily numbers reports add up, always within CurrentAccount (the current seller account).
 * Implemented with SQL over the channel and attribution tables.
 */
interface ReportNumbers {
    /** Google spend in the range, one entry per currency. */
    fun spendByCurrency(range: DateRange): List<SpendTotals>

    /** Attribution numbers in the range, one entry per marketplace (each has its own currency). */
    fun attributionByMarketplace(range: DateRange): Map<Marketplace, AttributionNumbers>

    /** All Google ads of the account with their ad group, campaign, product and brand. */
    fun adFacts(): List<AdFacts>

    /** Google spend in the range per ad and currency. */
    fun spendByAd(range: DateRange): Map<UUID, List<SpendTotals>>

    /**
     * Attribution numbers in the range per ad and marketplace. Key null: the lines that match no known ad, read in
     * the same pass (the drill-down needs both, and Attribution rows are the most to read).
     */
    fun attributionByAd(range: DateRange): Map<UUID?, Map<Marketplace, AttributionNumbers>>

    /** Google spend in the range per day and currency: of [adIds] only, or of all ads when null. */
    fun dailySpend(
        range: DateRange,
        adIds: Set<UUID>?,
    ): List<DailySpend>

    /**
     * Attribution numbers in the range per day and marketplace: of the lines matched to [adIds] only, or of all lines
     * (unmatched ones included) when null.
     */
    fun dailyAttribution(
        range: DateRange,
        adIds: Set<UUID>?,
    ): List<DailyAttributionNumbers>
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

data class DailyAttributionNumbers(
    val date: LocalDate,
    val marketplace: Marketplace,
    val numbers: AttributionNumbers,
)
