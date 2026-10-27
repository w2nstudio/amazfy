package com.amazfy.reporting.domain

import java.util.UUID

/** What each row of a drill-down stands for (requirement A12: brand → product → campaign → ad group → ad). */
enum class BreakdownLevel { BRAND, PRODUCT, CAMPAIGN, AD_GROUP, AD }

/**
 * The kind of a row. Besides the items themselves, three special rows keep a level's totals complete:
 * BRAND_LEVEL (a brand's Brand Store ads, at product level), NOT_LINKED (ads without product or brand) and
 * UNMATCHED (Attribution numbers matching no known ad).
 */
enum class RowKind { BRAND, PRODUCT, BRAND_LEVEL, NOT_LINKED, CAMPAIGN, AD_GROUP, AD, UNMATCHED }

/**
 * One Google ad with everything reports group by. [brandId] is the brand of its product, or for a Brand Store ad the
 * brand it is linked to directly; both product and brand are null for an ad not linked yet.
 */
data class AdFacts(
    val adId: UUID,
    val adExternalId: String,
    val finalUrl: String,
    val adGroupId: UUID,
    val adGroupName: String,
    val campaignId: UUID,
    val campaignName: String,
    val productId: UUID?,
    val productName: String?,
    /** E.g. "B0AAA11111 · US" */
    val productDetail: String?,
    val brandId: UUID?,
    val brandName: String?,
)

/** Which ads count. Every given condition must hold; none given = all ads (and the UNMATCHED row). */
data class BreakdownFilter(
    val brandId: UUID? = null,
    val productId: UUID? = null,
    /** Only Brand Store ads (linked to a brand, not to a product). */
    val brandLevel: Boolean = false,
    /** Only ads linked to neither a product nor a brand. */
    val notLinked: Boolean = false,
    val campaignId: UUID? = null,
    val adGroupId: UUID? = null,
    val adId: UUID? = null,
) {
    val isEmpty: Boolean get() = this == BreakdownFilter()

    fun matches(ad: AdFacts): Boolean =
        (brandId == null || ad.brandId == brandId) &&
            (productId == null || ad.productId == productId) &&
            (!brandLevel || (ad.productId == null && ad.brandId != null)) &&
            (!notLinked || ad.brandId == null) &&
            (campaignId == null || ad.campaignId == campaignId) &&
            (adGroupId == null || ad.adGroupId == adGroupId) &&
            (adId == null || ad.adId == adId)
}

/** One row: an item (or special row) and its totals in one currency. */
data class BreakdownRow(
    val kind: RowKind,
    val id: UUID?,
    val name: String,
    val detail: String?,
    val totals: CurrencyTotals,
)

object Breakdown {
    /**
     * Groups the ads matching [filter] by [level] and adds up their numbers, one row per item and currency. Items
     * without numbers in the range get no row. Sorted by name; BRAND_LEVEL and NOT_LINKED after, UNMATCHED last.
     * [unmatched]: Attribution numbers of no known ad, shown only without a filter (they belong to no item).
     */
    fun of(
        level: BreakdownLevel,
        filter: BreakdownFilter,
        ads: List<AdFacts>,
        spendByAd: Map<UUID, List<SpendTotals>>,
        attributionByAd: Map<UUID, List<AttributionTotals>>,
        unmatched: List<AttributionTotals>,
    ): List<BreakdownRow> {
        val rows =
            ads
                .filter(filter::matches)
                .groupBy { it.itemAt(level) }
                .flatMap { (item, itemAds) ->
                    CurrencyTotals
                        .combine(
                            spend = itemAds.flatMap { spendByAd[it.adId].orEmpty() },
                            attribution = itemAds.flatMap { attributionByAd[it.adId].orEmpty() },
                        ).map { BreakdownRow(item.kind, item.id, item.name, item.detail, it) }
                }.sortedWith(compareBy({ it.kind.isSpecial }, { it.name.lowercase() }, { it.totals.currency }))
        val unmatchedRows =
            if (!filter.isEmpty) {
                emptyList()
            } else {
                CurrencyTotals.combine(emptyList(), unmatched).map {
                    BreakdownRow(RowKind.UNMATCHED, null, "Not matched to an ad", "Amazon numbers for ads Amazfy doesn't know", it)
                }
            }
        return rows + unmatchedRows
    }

    private data class Item(
        val kind: RowKind,
        val id: UUID?,
        val name: String,
        val detail: String? = null,
    )

    private val NOT_LINKED = Item(RowKind.NOT_LINKED, null, "Not linked to a product or brand", "Ads whose link Amazfy can't read yet")

    private val RowKind.isSpecial get() = this == RowKind.BRAND_LEVEL || this == RowKind.NOT_LINKED

    private fun AdFacts.itemAt(level: BreakdownLevel): Item =
        when (level) {
            BreakdownLevel.BRAND -> if (brandId != null) Item(RowKind.BRAND, brandId, brandName!!) else NOT_LINKED
            BreakdownLevel.PRODUCT ->
                when {
                    productId != null -> Item(RowKind.PRODUCT, productId, productName!!, productDetail)
                    brandId != null -> Item(RowKind.BRAND_LEVEL, brandId, "$brandName – brand-level", "Brand Store ads, no single product")
                    else -> NOT_LINKED
                }
            BreakdownLevel.CAMPAIGN -> Item(RowKind.CAMPAIGN, campaignId, campaignName)
            BreakdownLevel.AD_GROUP -> Item(RowKind.AD_GROUP, adGroupId, adGroupName, campaignName)
            BreakdownLevel.AD -> Item(RowKind.AD, adId, "Ad $adExternalId", finalUrl)
        }
}
