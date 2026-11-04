package com.amazfy.sampledata.application

import com.amazfy.account.application.AccountService
import com.amazfy.catalog.application.CatalogService
import com.amazfy.catalog.application.ProductSummary
import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.tenant.CurrentAccount
import com.amazfy.reporting.application.RefreshReportTotals
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Date
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Made-up Google ads, spend and Attribution numbers for one seller account, so reports can be built and checked before
 * the Google (M4) and Amazon connections exist. Local development only (profile "local"): production has no such bean.
 * Writes the channel and attribution tables with plain SQL, as a sync or CSV import later will through their modules;
 * account_id is set explicitly here (SQL doesn't go through @TenantId).
 *
 * Two sets, both listed with their expected results in docs/guides/sample-data.md:
 * - [SampleSet.FULL]: 90 days, round numbers that are the same every day, so every report can be checked with a calculator.
 * - [SampleSet.SMALL_DEMO]: 4 days for one gardening brand, uneven like a real shop, small enough to explain in a demo.
 *
 * Ads are made only for the sample products (added if missing), never for the seller's own products, so sample numbers
 * don't end up on real listings.
 */
@Service
@Profile("local")
class SampleDataService(
    private val catalog: CatalogService,
    private val accounts: AccountService,
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
    private val reportTotals: RefreshReportTotals,
) {
    /** Replaces the current account's campaigns (with ad groups, ads, spend) and Attribution numbers. */
    @Transactional
    fun replaceWithSampleData(set: SampleSet = SampleSet.FULL): SampleDataSummary {
        val accountId = CurrentAccount.get()
        val marketplace =
            accounts
                .findAccount(accountId)!!
                .region.marketplaces
                .first()
        jdbc.sql("DELETE FROM attribution_daily WHERE account_id = :a").param("a", accountId).update()
        jdbc.sql("DELETE FROM campaigns WHERE account_id = :a").param("a", accountId).update() // cascades to ads, spend
        val summary =
            when (set) {
                SampleSet.FULL -> fullSet(accountId, marketplace)
                SampleSet.SMALL_DEMO -> smallDemo(accountId, marketplace)
            }
        // Everything was replaced, including days outside the new ones (an earlier fill's), so rebuild all totals
        reportTotals.forAllDays()
        return summary
    }

    private fun fullSet(
        accountId: UUID,
        marketplace: Marketplace,
    ): SampleDataSummary {
        val products = sampleProducts(marketplace, SAMPLE_PRODUCTS)
        val toDate = LocalDate.now(clock).minusDays(1) // Attribution has no numbers for today yet
        val days = (0 until DAYS).map { toDate.minusDays(DAYS - 1 - it) }
        val ids = ExternalIds()

        val ads = mutableListOf<SampleAd>()
        products.forEach { (plan, product) ->
            val campaign = insertCampaign(accountId, ids, "${plan.brand} – ${product.name} (${marketplace.name})")
            listOf("exact", "broad").forEach { match ->
                val adGroup = insertAdGroup(accountId, campaign, ids, "${product.name.lowercase()} – $match")
                // In each ad group ad A sells twice a day and ad B once, at the same spend: the ad level shows a difference
                ads += insertAd(accountId, adGroup, ids, product.amazonUrl, marketplace, plan.day(purchases = 2), productId = product.id)
                ads += insertAd(accountId, adGroup, ids, product.amazonUrl, marketplace, plan.day(purchases = 1), productId = product.id)
            }
        }
        // Per brand, a Brand Store campaign with two ads: one links to the brand's store (brand-level spend, no single
        // product), the other uses a short link Amazfy can't read, so it stays linked to nothing
        products.groupBy({ it.second.brandId }, { it.first }).forEach { (brandId, plans) ->
            val brand = plans.first()
            val campaign = insertCampaign(accountId, ids, "${brand.brand} – Brand Store (${marketplace.name})")
            val adGroup = insertAdGroup(accountId, campaign, ids, "${brand.brand.lowercase()} brand")
            val store = "https://www.${marketplace.amazonDomain}/stores/${brand.brand}/page/${UUID.nameUUIDFromBytes(
                brand.brand.toByteArray(),
            )}"
            ads +=
                insertAd(accountId, adGroup, ids, store, marketplace, SIDE_AD.copy(purchases = 1, price = brand.price), brandId = brandId)
            ads += insertAd(accountId, adGroup, ids, "https://amzn.to/${ids.next(6)}", marketplace, SIDE_AD.copy(purchases = 0))
        }

        val spendRows = mutableListOf<Array<Any>>()
        val attributionRows = mutableListOf<Array<Any?>>()
        days.forEach { day ->
            ads.forEach { ad ->
                spendRows += spendRow(accountId, ad, day, ad.day)
                attributionRows += attributionRow(accountId, ad, ad.creativeId, day, adId = ad.id, ad.day)
            }
            // Attribution numbers for an ad Amazfy doesn't know (e.g. removed on Google before the first sync): kept, unmatched
            attributionRows += attributionRow(accountId, ads.first(), UNMATCHED_CREATIVE, day, adId = null, UNMATCHED)
        }
        insertRows(spendRows, attributionRows)

        return SampleDataSummary(
            fromDate = days.first(),
            toDate = toDate,
            products = products.size,
            campaigns = ads.map { it.campaignId }.distinct().size,
            ads = ads.size,
            spendRows = spendRows.size,
            attributionRows = attributionRows.size,
        )
    }

    /**
     * The small demo: Leafwell's pruning shears and gardening gloves over 4 days, 18 to 15 days ago. Amazon's 14-day
     * window has closed for those days, so the dashboard doesn't mark them as still being counted.
     */
    private fun smallDemo(
        accountId: UUID,
        marketplace: Marketplace,
    ): SampleDataSummary {
        val products = sampleProducts(marketplace, listOf(SmallDemo.SHEARS, SmallDemo.GLOVES)).toMap()
        val toDate = LocalDate.now(clock).minusDays(SmallDemo.DAYS_AGO_LAST)
        val days = (SmallDemo.DAYS - 1 downTo 0).map { toDate.minusDays(it.toLong()) }
        val ids = ExternalIds()

        val campaigns = mutableMapOf<String, SampleAdGroup>()
        val ads =
            SmallDemo.ADS.map { plan ->
                // One ad group per campaign, made when its first ad comes
                val adGroup =
                    campaigns.getOrPut(plan.campaign) {
                        val campaign = insertCampaign(accountId, ids, "${SmallDemo.BRAND} – ${plan.campaign} (${marketplace.name})")
                        insertAdGroup(accountId, campaign, ids, plan.adGroup)
                    }
                val product = plan.product?.let { products.getValue(it) }
                val finalUrl = product?.amazonUrl ?: "https://amzn.to/${ids.next(6)}" // a short link Amazfy can't read
                plan to insertAd(accountId, adGroup, ids, finalUrl, marketplace, plan.on(0), productId = product?.id)
            }

        val spendRows = mutableListOf<Array<Any>>()
        val attributionRows = mutableListOf<Array<Any?>>()
        days.forEachIndexed { i, day ->
            ads.forEach { (plan, ad) ->
                spendRows += spendRow(accountId, ad, day, plan.on(i))
                attributionRows += attributionRow(accountId, ad, ad.creativeId, day, adId = ad.id, plan.on(i))
            }
            attributionRows +=
                attributionRow(accountId, ads.first().second, UNMATCHED_CREATIVE, day, adId = null, SmallDemo.unmatchedOn(i))
        }
        insertRows(spendRows, attributionRows)

        return SampleDataSummary(
            fromDate = days.first(),
            toDate = toDate,
            products = products.size,
            campaigns = campaigns.size,
            ads = ads.size,
            spendRows = spendRows.size,
            attributionRows = attributionRows.size,
        )
    }

    private fun spendRow(
        accountId: UUID,
        ad: SampleAd,
        day: LocalDate,
        numbers: AdDay,
    ): Array<Any> =
        arrayOf(accountId, ad.id, Date.valueOf(day), numbers.impressions, numbers.clicks, numbers.costMicros, ad.marketplace.currency)

    private fun insertRows(
        spendRows: List<Array<Any>>,
        attributionRows: List<Array<Any?>>,
    ) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO ad_spend_daily (account_id, ad_id, date, impressions, clicks, cost_micros, currency) VALUES (?, ?, ?, ?, ?, ?, ?)",
            spendRows,
        )
        jdbcTemplate.batchUpdate(
            "INSERT INTO attribution_daily (account_id, source, marketplace, date, publisher, campaign_external_id, " +
                "ad_group_external_id, creative_external_id, ad_id, click_throughs, detail_page_views, add_to_carts, " +
                "purchases, units_sold, sales) VALUES (?, 'CSV', ?, ?, 'Google Ads', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            attributionRows,
        )
    }

    /** The sample products on the region's first marketplace, created (with their brands) where missing. */
    private fun sampleProducts(
        marketplace: Marketplace,
        plans: List<SampleProduct>,
    ): List<Pair<SampleProduct, ProductSummary>> {
        val existing = catalog.listProducts()
        val brandIds = catalog.listBrands().associate { it.name.lowercase() to it.id }.toMutableMap()
        return plans.map { plan ->
            val product =
                existing.find { it.asin == plan.asin && it.marketplace == marketplace }
                    ?: catalog.createProduct(
                        brandIds.getOrPut(plan.brand.lowercase()) { catalog.createBrand(plan.brand).id },
                        plan.asin,
                        marketplace,
                        plan.name,
                    )
            plan to product
        }
    }

    private fun insertCampaign(
        accountId: UUID,
        ids: ExternalIds,
        name: String,
    ): SampleCampaign {
        val externalId = ids.next(11) // Google campaign ids: 11 digits
        val id =
            jdbc
                .sql(
                    "INSERT INTO campaigns (account_id, channel, external_id, name, status) VALUES (:a, 'GOOGLE', :e, :n, 'ENABLED') RETURNING id",
                ).param("a", accountId)
                .param("e", externalId)
                .param("n", name)
                .query(UUID::class.java)
                .single()
        return SampleCampaign(id, externalId)
    }

    private fun insertAdGroup(
        accountId: UUID,
        campaign: SampleCampaign,
        ids: ExternalIds,
        name: String,
    ): SampleAdGroup {
        val externalId = ids.next(12)
        val id =
            jdbc
                .sql(
                    "INSERT INTO ad_groups (account_id, campaign_id, external_id, name, status) VALUES (:a, :c, :e, :n, 'ENABLED') RETURNING id",
                ).param("a", accountId)
                .param("c", campaign.id)
                .param("e", externalId)
                .param("n", name)
                .query(UUID::class.java)
                .single()
        return SampleAdGroup(id, externalId, campaign)
    }

    private fun insertAd(
        accountId: UUID,
        adGroup: SampleAdGroup,
        ids: ExternalIds,
        finalUrl: String,
        marketplace: Marketplace,
        day: AdDay,
        productId: UUID? = null,
        brandId: UUID? = null,
    ): SampleAd {
        val externalId = ids.next(12)
        val id =
            jdbc
                .sql(
                    "INSERT INTO ads (account_id, ad_group_id, external_id, final_url, status, product_id, brand_id) " +
                        "VALUES (:a, :g, :e, :u, 'ENABLED', :p, :b) RETURNING id",
                ).param("a", accountId)
                .param("g", adGroup.id)
                .param("e", externalId)
                .param("u", finalUrl)
                .param("p", productId)
                .param("b", brandId)
                .query(UUID::class.java)
                .single()
        return SampleAd(id, adGroup.campaign.id, adGroup.campaign.externalId, adGroup.externalId, externalId, marketplace, day)
    }

    /** Amazon's side of the ad's clicks on one day, as an Attribution report line. */
    private fun attributionRow(
        accountId: UUID,
        ad: SampleAd,
        creativeId: String,
        day: LocalDate,
        adId: UUID?,
        numbers: AdDay,
    ): Array<Any?> =
        arrayOf(
            accountId,
            ad.marketplace.name,
            Date.valueOf(day),
            ad.campaignExternalId,
            ad.adGroupExternalId,
            creativeId,
            adId,
            numbers.clickThroughs,
            numbers.clickThroughs, // detail page views: every visit reaches the product page
            numbers.purchases * 2, // add to carts: half of them buy
            numbers.purchases,
            numbers.purchases, // units: one per purchase
            numbers.sales,
        )

    private companion object {
        const val DAYS = 90L
        const val UNMATCHED_CREATIVE = "999999999999"

        /** Brand Store and short-link ads: $5 a day. */
        val SIDE_AD = AdDay(spend = 5, purchases = 0, price = 0)

        /** The unmatched Attribution line: one $40 purchase a day from 9 visits, no Google spend known. */
        val UNMATCHED = AdDay(spend = 0, purchases = 1, price = 40, clickThroughsWithoutSpend = 9)

        val SAMPLE_PRODUCTS =
            listOf(
                SampleProduct("AquaLife", "B0AQL00001", "Aquarium filter 40L", price = 40, spendPerAd = 20),
                SampleProduct("AquaLife", "B0AQL00002", "Aquarium heater 100W", price = 30, spendPerAd = 10),
                SampleProduct("ClearTank", "B0CLT00001", "Glass cleaner 500ml", price = 12, spendPerAd = 5),
            )
    }
}

/** Which sample data to fill in (see SampleDataService). */
enum class SampleSet { FULL, SMALL_DEMO }

/**
 * The small demo set: one gardening brand over 4 days, uneven like a real shop. Ad A and ad B advertise the same shears
 * at about the same cost, but A sells far more; day 3 is a very good day and day 4 a weak one. Expected results:
 * docs/guides/sample-data.md. Google charges $1 a click here, so clicks equal spend in dollars.
 */
private object SmallDemo {
    const val BRAND = "Leafwell"
    const val DAYS = 4
    const val DAYS_AGO_LAST = 15L
    val SHEARS = SampleProduct(BRAND, "B0LFW00001", "Bypass pruning shears", price = 25)
    val GLOVES = SampleProduct(BRAND, "B0LFW00002", "Gardening gloves", price = 18)

    /** One ad's spend and purchases on each day, in order; ads of the same campaign share its one ad group. */
    data class Ad(
        val campaign: String,
        val adGroup: String,
        val product: SampleProduct?,
        val spend: List<Int>,
        val purchases: List<Int>,
    ) {
        fun on(day: Int) = AdDay(spend[day], purchases[day], product?.price ?: 0, clicksPerDollar = 1)
    }

    val ADS =
        listOf(
            Ad("Pruning shears", "pruning shears", SHEARS, spend = listOf(18, 22, 30, 25), purchases = listOf(2, 3, 6, 2)), // ad A
            Ad("Pruning shears", "pruning shears", SHEARS, spend = listOf(20, 19, 24, 22), purchases = listOf(1, 0, 0, 0)), // ad B
            Ad("Gardening gloves", "gardening gloves", GLOVES, spend = listOf(12, 14, 18, 13), purchases = listOf(2, 2, 5, 1)),
            Ad("Brand", "leafwell", null, spend = listOf(5, 4, 6, 5), purchases = listOf(0, 0, 0, 0)), // short link: not linked
        )

    /** Shears bought through an ad Amazfy doesn't know: no spend, so its visits are given directly. */
    private val UNMATCHED_PURCHASES = listOf(0, 1, 1, 0)
    private val UNMATCHED_CLICK_THROUGHS = listOf(6, 8, 9, 5)

    fun unmatchedOn(day: Int) = AdDay(0, UNMATCHED_PURCHASES[day], SHEARS.price, clickThroughsWithoutSpend = UNMATCHED_CLICK_THROUGHS[day])
}

/** What replaceWithSampleData created. */
data class SampleDataSummary(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val products: Int,
    val campaigns: Int,
    val ads: Int,
    val spendRows: Int,
    val attributionRows: Int,
)

/**
 * A sample product and, in the full set, the daily spend of each of its four ads (in the marketplace's currency,
 * whole units).
 */
private data class SampleProduct(
    val brand: String,
    val asin: String,
    val name: String,
    val price: Int,
    val spendPerAd: Int = 0,
) {
    fun day(purchases: Int) = AdDay(spendPerAd, purchases, price)
}

/**
 * One ad's numbers on a day (in the full set, the same every day). Google charges 50 cents a click in the full set and
 * $1 in the small demo, shows the ad 20 times per click, and 9 of every 10 clicks reach Amazon (click-throughs,
 * rounded to whole visits).
 */
private data class AdDay(
    val spend: Int,
    val purchases: Int,
    val price: Int,
    val clickThroughsWithoutSpend: Int = 0,
    val clicksPerDollar: Int = 2,
) {
    val clicks: Long get() = spend.toLong() * clicksPerDollar
    val impressions: Long get() = clicks * 20
    val costMicros: Long get() = spend * 1_000_000L
    val clickThroughs: Long get() = if (spend > 0) (clicks * 9 + 5) / 10 else clickThroughsWithoutSpend.toLong()
    val sales: BigDecimal get() = BigDecimal(purchases * price).setScale(2)
}

/** Google-style numeric ids, counting up so a run always produces the same ones. */
private class ExternalIds {
    private var counter = 0L

    fun next(digits: Int): String = "${10L.pow(digits - 1) + ++counter}"

    private fun Long.pow(n: Int): Long = (1..n).fold(1L) { acc, _ -> acc * this }
}

private data class SampleCampaign(
    val id: UUID,
    val externalId: String,
)

private data class SampleAdGroup(
    val id: UUID,
    val externalId: String,
    val campaign: SampleCampaign,
)

private data class SampleAd(
    val id: UUID,
    val campaignId: UUID,
    val campaignExternalId: String,
    val adGroupExternalId: String,
    val creativeId: String,
    val marketplace: Marketplace,
    val day: AdDay,
)
