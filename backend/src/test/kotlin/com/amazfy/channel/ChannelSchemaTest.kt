package com.amazfy.channel

import com.amazfy.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The database rules of V9 (campaigns, ad groups, ads, daily spend), with plain SQL.
 * Each test runs in a transaction that is rolled back. In PostgreSQL a failed statement aborts the transaction,
 * so each test provokes at most one failure, last.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class ChannelSchemaTest {
    @Autowired
    lateinit var jdbc: JdbcClient

    private fun id(
        sql: String,
        vararg params: Pair<String, Any?>,
    ): UUID {
        var statement = jdbc.sql(sql)
        params.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(UUID::class.java).single()
    }

    private fun account() = id("INSERT INTO accounts (name, region) VALUES ('AquaLife Inc.', 'NA') RETURNING id")

    private fun brand(account: UUID) =
        id("INSERT INTO brands (account_id, name) VALUES (:a, :n) RETURNING id", "a" to account, "n" to "AquaLife ${UUID.randomUUID()}")

    private fun product(
        account: UUID,
        brand: UUID,
    ) = id(
        "INSERT INTO products (account_id, brand_id, asin, marketplace, name) VALUES (:a, :b, :asin, 'US', 'Filter') RETURNING id",
        "a" to account,
        "b" to brand,
        // A different ASIN each time: an ASIN is unique per marketplace within an account
        "asin" to "B0" +
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(8)
                .uppercase(),
    )

    private fun campaign(account: UUID) =
        id(
            "INSERT INTO campaigns (account_id, channel, external_id, name, status) VALUES (:a, 'GOOGLE', :e, 'Filters US', 'ENABLED') RETURNING id",
            "a" to account,
            "e" to UUID.randomUUID().toString(),
        )

    private fun adGroup(
        account: UUID,
        campaign: UUID,
    ) = id(
        "INSERT INTO ad_groups (account_id, campaign_id, external_id, name, status) VALUES (:a, :c, '7001', 'filter 40l', 'ENABLED') RETURNING id",
        "a" to account,
        "c" to campaign,
    )

    private fun ad(
        account: UUID,
        adGroup: UUID,
        product: UUID? = null,
        brand: UUID? = null,
    ) = id(
        "INSERT INTO ads (account_id, ad_group_id, external_id, final_url, status, product_id, brand_id) " +
            "VALUES (:a, :g, :e, 'https://www.amazon.com/dp/B0AAA11111', 'ENABLED', :p, :b) RETURNING id",
        "a" to account,
        "g" to adGroup,
        "e" to UUID.randomUUID().toString(),
        "p" to product,
        "b" to brand,
    )

    private fun spend(
        account: UUID,
        ad: UUID,
        date: LocalDate,
        costMicros: Long = 12_500_000,
    ): Int =
        jdbc
            .sql(
                "INSERT INTO ad_spend_daily (account_id, ad_id, date, impressions, clicks, cost_micros, currency) " +
                    "VALUES (:a, :ad, :d, 1000, 40, :c, 'USD')",
            ).param("a", account)
            .param("ad", ad)
            .param("d", date)
            .param("c", costMicros)
            .update()

    private fun count(
        table: String,
        column: String,
        id: UUID,
    ): Int =
        jdbc
            .sql("SELECT count(*) FROM $table WHERE $column = :id")
            .param("id", id)
            .query(Int::class.java)
            .single()

    @Test
    fun `an ad has one spend row per day`() {
        val account = account()
        val ad = ad(account, adGroup(account, campaign(account)))
        spend(account, ad, LocalDate.of(2026, 10, 1))
        spend(account, ad, LocalDate.of(2026, 10, 2))
        assertEquals(2, count("ad_spend_daily", "ad_id", ad))

        assertFailsWith<DataIntegrityViolationException> { spend(account, ad, LocalDate.of(2026, 10, 1)) }
    }

    @Test
    fun `an ad group can't be put under another seller account's campaign`() {
        val othersCampaign = campaign(account())
        assertFailsWith<DataIntegrityViolationException> { adGroup(account(), othersCampaign) }
    }

    @Test
    fun `an ad can't be linked to another seller account's product`() {
        val other = account()
        val othersProduct = product(other, brand(other))
        val account = account()
        val adGroup = adGroup(account, campaign(account))
        assertFailsWith<DataIntegrityViolationException> { ad(account, adGroup, product = othersProduct) }
    }

    @Test
    fun `an ad links to a product or to a brand, not both`() {
        val account = account()
        val brand = brand(account)
        val adGroup = adGroup(account, campaign(account))
        ad(account, adGroup, product = product(account, brand))
        ad(account, adGroup, brand = brand)
        ad(account, adGroup)
        assertFailsWith<DataIntegrityViolationException> { ad(account, adGroup, product = product(account, brand), brand = brand) }
    }

    @Test
    fun `removing a product makes its ads unmatched and keeps their spend`() {
        val account = account()
        val product = product(account, brand(account))
        val ad = ad(account, adGroup(account, campaign(account)), product = product)
        spend(account, ad, LocalDate.of(2026, 10, 1))

        jdbc.sql("DELETE FROM products WHERE id = :p").param("p", product).update()

        val productId =
            jdbc
                .sql("SELECT product_id FROM ads WHERE id = :ad")
                .param("ad", ad)
                .query(UUID::class.java)
                .optional()
        assertNull(productId.orElse(null))
        assertEquals(1, count("ad_spend_daily", "ad_id", ad))
    }

    @Test
    fun `removing a campaign removes its ad groups, ads and spend`() {
        val account = account()
        val campaign = campaign(account)
        val adGroup = adGroup(account, campaign)
        val ad = ad(account, adGroup)
        spend(account, ad, LocalDate.of(2026, 10, 1))

        jdbc.sql("DELETE FROM campaigns WHERE id = :c").param("c", campaign).update()

        assertEquals(0, count("ad_groups", "id", adGroup))
        assertEquals(0, count("ads", "id", ad))
        assertEquals(0, count("ad_spend_daily", "ad_id", ad))
    }

    @Test
    fun `negative spend is rejected`() {
        val account = account()
        val ad = ad(account, adGroup(account, campaign(account)))
        assertFailsWith<DataIntegrityViolationException> { spend(account, ad, LocalDate.of(2026, 10, 1), costMicros = -1) }
    }
}
