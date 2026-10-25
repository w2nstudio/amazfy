package com.amazfy.sampledata.application

import com.amazfy.account.application.AccountService
import com.amazfy.catalog.application.CatalogService
import com.amazfy.catalog.application.ProductSummary
import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.amazon.Region
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Date
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Made-up but realistic Google ads, spend and Attribution numbers for one seller account, so reports can be built
 * and tried before the Google (M4) and Amazon connections exist. Local development only (profile "local"): production
 * has no such bean. Writes the channel and attribution tables with plain SQL, as a sync or CSV import later will
 * through their modules; account_id is set explicitly here (SQL doesn't go through @TenantId).
 *
 * Deterministic: the same account and day give the same numbers, so results can be compared between runs.
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
        val region = accounts.findAccount(accountId)!!.region
        val products = catalog.listProducts().ifEmpty { addSampleCatalog(region) }
        val brandNames = catalog.listBrands().associate { it.id to it.name }
        val random = Random(accountId.hashCode())
        val toDate = LocalDate.now(clock).minusDays(1) // Attribution has no numbers for today yet
        val fromDate = toDate.minusDays(DAYS - 1)

        jdbc.sql("DELETE FROM attribution_daily WHERE account_id = :a").param("a", accountId).update()
        jdbc.sql("DELETE FROM campaigns WHERE account_id = :a").param("a", accountId).update() // cascades to ads, spend

        val ads = mutableListOf<SampleAd>()
        products.forEach { product ->
            val brandName = brandNames.getValue(product.brandId)
            val campaign = insertCampaign(accountId, random, "$brandName – ${product.name} (${product.marketplace})")
            listOf("exact", "broad").forEach { match ->
                val adGroup = insertAdGroup(accountId, campaign, random, "${product.name.lowercase()} – $match")
                repeat(2) {
                    ads += insertAd(accountId, adGroup, random, product.amazonUrl, product.marketplace, productId = product.id)
                }
            }
        }
        // Brand Store ads: link to the brand's store, no single product (brand-level spend). The second one uses a short
        // link Amazfy can't read, so it stays unmatched (no product, no brand).
        products.groupBy { it.brandId }.forEach { (brandId, brandProducts) ->
            val marketplace = brandProducts.first().marketplace
            val brandName = brandNames.getValue(brandId)
            val campaign = insertCampaign(accountId, random, "$brandName – Brand Store (${marketplace.name})")
            val adGroup = insertAdGroup(accountId, campaign, random, "${brandName.lowercase()} brand")
            val store = "https://www.${marketplace.amazonDomain}/stores/${brandName.replace(
                " ",
                "",
            )}/page/${UUID(random.nextLong(), random.nextLong())}"
            ads += insertAd(accountId, adGroup, random, store, marketplace, brandId = brandId)
            ads += insertAd(accountId, adGroup, random, "https://amzn.to/${random.nextInt(100_000, 999_999)}", marketplace)
        }

        val days = (0 until DAYS).map { fromDate.plusDays(it) }
        val spendRows = mutableListOf<Array<Any>>()
        val attributionRows = mutableListOf<Array<Any?>>()
        ads.forEach { ad ->
            val price = random.nextDouble(19.99, 59.99)
            val base = random.nextInt(300, 1_500)
            days.forEach { day ->
                val numbers = SampleDay.of(random, base, day, toDate)
                spendRows +=
                    arrayOf(
                        accountId,
                        ad.id,
                        Date.valueOf(day),
                        numbers.impressions,
                        numbers.clicks,
                        numbers.costMicros,
                        ad.marketplace.currency,
                    )
                attributionRows += attributionRow(accountId, ad, day, numbers, price, adId = ad.id)
            }
        }
        // An Attribution line for an ad Amazfy doesn't know (e.g. removed on Google before the first sync): kept, unmatched
        val firstAd = ads.first()
        days.takeLast(14).forEach { day ->
            val numbers = SampleDay.of(random, 200, day, toDate)
            attributionRows +=
                attributionRow(
                    accountId,
                    firstAd.copy(externalId = "${random.nextLong(100_000_000_000, 999_999_999_999)}"),
                    day,
                    numbers,
                    24.99,
                    adId = null,
                )
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
            fromDate = fromDate,
            toDate = toDate,
            products = products.size,
            campaigns = ads.map { it.campaignId }.distinct().size,
            ads = ads.size,
            spendRows = spendRows.size,
            attributionRows = attributionRows.size,
        )
    }

    /** For an account without products: two brands with products on the region's first marketplace. */
    private fun addSampleCatalog(region: Region): List<ProductSummary> {
        val marketplace = region.marketplaces.first()
        val taken = catalog.listBrands().associateBy { it.name.lowercase() }

        fun brand(name: String) = taken[name.lowercase()]?.id ?: catalog.createBrand(name).id
        val aquaLife = brand("AquaLife")
        val clearTank = brand("ClearTank")
        catalog.createProduct(aquaLife, "B0AQL00001", marketplace, "Aquarium filter 40L")
        catalog.createProduct(aquaLife, "B0AQL00002", marketplace, "Aquarium heater 100W")
        catalog.createProduct(clearTank, "B0CLT00001", marketplace, "Glass cleaner 500ml")
        return catalog.listProducts()
    }

    private fun insertCampaign(
        accountId: UUID,
        random: Random,
        name: String,
    ): UUID =
        jdbc
            .sql(
                "INSERT INTO campaigns (account_id, channel, external_id, name, status) VALUES (:a, 'GOOGLE', :e, :n, 'ENABLED') RETURNING id",
            ).param("a", accountId)
            .param("e", "${random.nextLong(10_000_000_000, 99_999_999_999)}") // Google campaign ids: 11 digits
            .param("n", name)
            .query(UUID::class.java)
            .single()

    private fun insertAdGroup(
        accountId: UUID,
        campaignId: UUID,
        random: Random,
        name: String,
    ): SampleAdGroup {
        val externalId = "${random.nextLong(100_000_000_000, 999_999_999_999)}"
        val id =
            jdbc
                .sql(
                    "INSERT INTO ad_groups (account_id, campaign_id, external_id, name, status) VALUES (:a, :c, :e, :n, 'ENABLED') RETURNING id",
                ).param("a", accountId)
                .param("c", campaignId)
                .param("e", externalId)
                .param("n", name)
                .query(UUID::class.java)
                .single()
        return SampleAdGroup(id, campaignId, externalId, campaignExternalId(campaignId))
    }

    private fun campaignExternalId(campaignId: UUID): String =
        jdbc
            .sql("SELECT external_id FROM campaigns WHERE id = :c")
            .param("c", campaignId)
            .query(String::class.java)
            .single()

    private fun insertAd(
        accountId: UUID,
        adGroup: SampleAdGroup,
        random: Random,
        finalUrl: String,
        marketplace: Marketplace,
        productId: UUID? = null,
        brandId: UUID? = null,
    ): SampleAd {
        val externalId = "${random.nextLong(100_000_000_000, 999_999_999_999)}"
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
        return SampleAd(id, adGroup.campaignId, adGroup.campaignExternalId, adGroup.externalId, externalId, marketplace)
    }

    /** Amazon's side of the same clicks: some get lost on the way, a share view, add to cart and buy. */
    private fun attributionRow(
        accountId: UUID,
        ad: SampleAd,
        day: LocalDate,
        numbers: SampleDay,
        price: Double,
        adId: UUID?,
    ): Array<Any?> {
        val random = Random(ad.externalId.hashCode() * 31 + day.hashCode())
        val clickThroughs = (numbers.clicks * random.nextDouble(0.85, 0.95)).toLong()
        val detailPageViews = (clickThroughs * random.nextDouble(0.80, 0.95)).toLong()
        val addToCarts = (detailPageViews * random.nextDouble(0.12, 0.25)).toLong()
        val purchases = minOf(addToCarts, (clickThroughs * random.nextDouble(0.05, 0.14)).toLong())
        val units = purchases + (purchases * random.nextDouble(0.0, 0.3)).toLong()
        val sales = BigDecimal(units * price).setScale(2, RoundingMode.HALF_UP)
        return arrayOf(
            accountId,
            ad.marketplace.name,
            Date.valueOf(day),
            ad.campaignExternalId,
            ad.adGroupExternalId,
            ad.externalId,
            adId,
            clickThroughs,
            detailPageViews,
            addToCarts,
            purchases,
            units,
            sales,
        )
    }

    private companion object {
        const val DAYS = 90L
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

private data class SampleAdGroup(
    val id: UUID,
    val campaignId: UUID,
    val externalId: String,
    val campaignExternalId: String,
)

private data class SampleAd(
    val id: UUID,
    val campaignId: UUID,
    val campaignExternalId: String,
    val adGroupExternalId: String,
    val externalId: String,
    val marketplace: Marketplace,
)

/** One ad's Google numbers for one day: fewer on weekends, slowly growing over the period, with daily noise. */
private data class SampleDay(
    val impressions: Long,
    val clicks: Long,
    val costMicros: Long,
) {
    companion object {
        fun of(
            random: Random,
            base: Int,
            day: LocalDate,
            lastDay: LocalDate,
        ): SampleDay {
            val weekend = if (day.dayOfWeek.value >= 6) 0.75 else 1.0
            val growth = 1.0 - (lastDay.toEpochDay() - day.toEpochDay()) / 300.0 // ~30% lower 90 days ago
            val impressions = (base * weekend * growth * random.nextDouble(0.8, 1.2)).toLong()
            val clicks = (impressions * random.nextDouble(0.02, 0.06)).toLong()
            val costPerClick = random.nextDouble(0.35, 1.40) // in the currency, e.g. dollars
            return SampleDay(impressions, clicks, (clicks * costPerClick * 1_000_000).toLong())
        }
    }
}
