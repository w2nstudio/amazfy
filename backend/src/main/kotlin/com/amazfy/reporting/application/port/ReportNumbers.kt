package com.amazfy.reporting.application.port

import com.amazfy.common.amazon.Marketplace
import com.amazfy.reporting.domain.AdFacts
import com.amazfy.reporting.domain.DateRange
import com.amazfy.reporting.domain.SpendTotals
import java.math.BigDecimal
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

    /** Attribution numbers in the range per matched ad and marketplace. */
    fun attributionByAd(range: DateRange): Map<UUID, Map<Marketplace, AttributionNumbers>>

    /** Attribution numbers in the range that match no known ad, per marketplace. */
    fun unmatchedAttribution(range: DateRange): Map<Marketplace, AttributionNumbers>
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
