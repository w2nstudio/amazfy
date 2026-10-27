package com.amazfy.sampledata.application

import com.amazfy.account.application.AccountService
import com.amazfy.catalog.application.CatalogService
import com.amazfy.catalog.application.ProductSummary
import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.tenant.CurrentAccount
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
 * The numbers are round and the same every day, so every report can be checked with a calculator:
 * docs/guides/sample-data.md lists the expected results. Ads are made only for the three sample products (added if
 * missing), never for the seller's own products, so sample numbers don't end up on real listings.
 */
@Service
@Profile("local")
class SampleDataService(
    private val catalog: CatalogService,
    private val accounts: AccountService,
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) {
    /** Replaces the current account's campaigns (with ad groups, ads, spend) and Attribution numbers. */
    @Transactional
    fun replaceWithSampleData(): SampleDataSummary {
        val accountId = CurrentAccount.get()
        val marketplace =
            accounts
                .findAccount(accountId)!!
                .region.marketplaces
                .first()
        val products = sampleProducts(marketplace)
        val toDate = LocalDate.now(clock).minusDays(1) // Attribution has no numbers for today yet
        val days = (0 until DAYS).map { toDate.minusDays(DAYS - 1 - it) }
        val ids = ExternalIds()

        jdbc.sql("DELETE FROM attribution_daily WHERE account_id = :a").param("a", accountId).update()
        jdbc.sql("DELETE FROM campaigns WHERE account_id = :a").param("a", accountId).update() // cascades to ads, spend

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
                spendRows +=
                    arrayOf(accountId, ad.id, Date.valueOf(day), ad.day.impressions, ad.day.clicks, ad.day.costMicros, marketplace.currency)
                attributionRows += attributionRow(accountId, ad, ad.creativeId, day, adId = ad.id)
            }
            // Attribution numbers for an ad Amazfy doesn't know (e.g. removed on Google before the first sync): kept, unmatched
            attributionRows += attributionRow(accountId, ads.first().copy(day = UNMATCHED), UNMATCHED_CREATIVE, day, adId = null)
        }
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

    /** The sample products on the region's first marketplace, created (with their brands) where missing. */
    private fun sampleProducts(marketplace: Marketplace): List<Pair<SampleProduct, ProductSummary>> {
        val existing = catalog.listProducts()
        val brandIds = catalog.listBrands().associate { it.name.lowercase() to it.id }.toMutableMap()
        return SAMPLE_PRODUCTS.map { plan ->
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
    ): Array<Any?> =
        arrayOf(
            accountId,
            ad.marketplace.name,
            Date.valueOf(day),
            ad.campaignExternalId,
            ad.adGroupExternalId,
            creativeId,
            adId,
            ad.day.clickThroughs,
            ad.day.clickThroughs, // detail page views: every visit reaches the product page
            ad.day.purchases * 2, // add to carts: half of them buy
            ad.day.purchases,
            ad.day.purchases, // units: one per purchase
            ad.day.sales,
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

/** A sample product and the daily spend of each of its four ads (in the marketplace's currency, whole units). */
private data class SampleProduct(
    val brand: String,
    val asin: String,
    val name: String,
    val price: Int,
    val spendPerAd: Int,
) {
    fun day(purchases: Int) = AdDay(spendPerAd, purchases, price)
}

/**
 * One ad's numbers, the same every day. Google charges 50 cents a click, shows the ad 20 times per click, and 9 of
 * every 10 clicks reach Amazon (click-throughs).
 */
private data class AdDay(
    val spend: Int,
    val purchases: Int,
    val price: Int,
    val clickThroughsWithoutSpend: Int = 0,
) {
    val clicks: Long get() = spend * 2L
    val impressions: Long get() = clicks * 20
    val costMicros: Long get() = spend * 1_000_000L
    val clickThroughs: Long get() = if (spend > 0) clicks * 9 / 10 else clickThroughsWithoutSpend.toLong()
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
