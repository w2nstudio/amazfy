package com.amazfy.reporting.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

class BreakdownTest {
    private val aquaLife = UUID.randomUUID()
    private val filter = UUID.randomUUID()
    private val heater = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val adGroup = UUID.randomUUID()

    private fun ad(
        product: UUID? = null,
        productName: String? = null,
        brand: UUID? = aquaLife,
    ) = AdFacts(
        adId = UUID.randomUUID(),
        adExternalId = "9001",
        finalUrl = "https://www.amazon.com/dp/B0AAA11111",
        adGroupId = adGroup,
        adGroupName = "filter 40l",
        campaignId = campaign,
        campaignName = "AquaLife US",
        productId = product,
        productName = productName,
        productDetail = product?.let { "B0AAA11111 · US" },
        brandId = brand,
        brandName = brand?.let { "AquaLife" },
    )

    private val filterAd = ad(filter, "Filter 40L")
    private val heaterAd = ad(heater, "Heater 100W")
    private val storeAd = ad() // Brand Store ad: brand, no product
    private val shortLinkAd = ad(brand = null) // not linked
    private val ads = listOf(filterAd, heaterAd, storeAd, shortLinkAd)

    /** Each ad spent $10 and sold $40 in the range. */
    private val spend = ads.associate { it.adId to listOf(SpendTotals("USD", 10_000_000, 100, 5)) }
    private val attribution = ads.associate { it.adId to listOf(AttributionTotals("USD", 5, 4, 2, 1, 1, BigDecimal("40.00"))) }
    private val unmatched = listOf(AttributionTotals("USD", 3, 3, 1, 1, 1, BigDecimal("25.00")))

    private fun rows(
        level: BreakdownLevel,
        f: BreakdownFilter = BreakdownFilter(),
    ) = Breakdown.of(level, f, ads, spend, attribution, unmatched)

    @Test
    fun `at product level Brand Store and unlinked ads get their own rows, unmatched numbers come last`() {
        val rows = rows(BreakdownLevel.PRODUCT)
        assertEquals(
            listOf(
                RowKind.PRODUCT to "Filter 40L",
                RowKind.PRODUCT to "Heater 100W",
                RowKind.BRAND_LEVEL to "AquaLife – brand-level",
                RowKind.NOT_LINKED to "Not linked to a product or brand",
                RowKind.UNMATCHED to "Not matched to an ad",
            ),
            rows.map { it.kind to it.name },
        )
        assertEquals(aquaLife, rows[2].id) // the brand-level row drills down by its brand
    }

    @Test
    fun `every level adds up to the same totals`() {
        BreakdownLevel.entries.forEach { level ->
            val rows = rows(level)
            assertEquals(BigDecimal("40.000000"), rows.sumOf { it.totals.spend }, "spend at $level")
            assertEquals(BigDecimal("185.00"), rows.sumOf { it.totals.sales }, "sales at $level") // 4 x 40 + 25 unmatched
        }
    }

    @Test
    fun `a brand row counts its products' ads and its Brand Store ads`() {
        val brandRow = rows(BreakdownLevel.BRAND).single { it.kind == RowKind.BRAND }
        assertEquals(BigDecimal("30.000000"), brandRow.totals.spend)
        assertEquals(BigDecimal("4.00"), brandRow.totals.roas) // 120 sales / 30 spend
    }

    @Test
    fun `filters narrow the ads, and filtered views have no unmatched row`() {
        val oneProduct = rows(BreakdownLevel.CAMPAIGN, BreakdownFilter(productId = filter))
        assertEquals(listOf(RowKind.CAMPAIGN), oneProduct.map { it.kind })
        assertEquals(BigDecimal("10.000000"), oneProduct.single().totals.spend)

        val brandStore = rows(BreakdownLevel.AD, BreakdownFilter(brandId = aquaLife, brandLevel = true))
        assertEquals(listOf(storeAd.adId), brandStore.map { it.id })

        assertEquals(listOf(shortLinkAd.adId), rows(BreakdownLevel.AD, BreakdownFilter(notLinked = true)).map { it.id })
    }

    @Test
    fun `items without numbers in the range get no row`() {
        val quiet = Breakdown.of(BreakdownLevel.PRODUCT, BreakdownFilter(), ads, emptyMap(), emptyMap(), emptyList())
        assertEquals(emptyList(), quiet)
    }
}
