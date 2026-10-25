package com.amazfy.attribution

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
 * The database rules of V10 (attribution_daily), with plain SQL.
 * Each test runs in a transaction that is rolled back. In PostgreSQL a failed statement aborts the transaction,
 * so each test provokes at most one failure, last.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class AttributionSchemaTest {
    @Autowired
    lateinit var jdbc: JdbcClient

    private val day = LocalDate.of(2026, 10, 1)

    private fun id(
        sql: String,
        vararg params: Pair<String, Any?>,
    ): UUID {
        var statement = jdbc.sql(sql)
        params.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(UUID::class.java).single()
    }

    private fun account() = id("INSERT INTO accounts (name, region) VALUES ('AquaLife Inc.', 'NA') RETURNING id")

    /** A Google ad (with its campaign and ad group) in [account]. */
    private fun ad(account: UUID): UUID {
        val campaign =
            id(
                "INSERT INTO campaigns (account_id, channel, external_id, name, status) VALUES (:a, 'GOOGLE', :e, 'Filters US', 'ENABLED') RETURNING id",
                "a" to account,
                "e" to UUID.randomUUID().toString(),
            )
        val adGroup =
            id(
                "INSERT INTO ad_groups (account_id, campaign_id, external_id, name, status) VALUES (:a, :c, '7001', 'filter 40l', 'ENABLED') RETURNING id",
                "a" to account,
                "c" to campaign,
            )
        return id(
            "INSERT INTO ads (account_id, ad_group_id, external_id, final_url, status) " +
                "VALUES (:a, :g, '9001', 'https://www.amazon.com/dp/B0AAA11111', 'ENABLED') RETURNING id",
            "a" to account,
            "g" to adGroup,
        )
    }

    /** One reported line: campaign 21345678901, ad group 7001, the given creative; Amazon's numbers for [date]. */
    private fun row(
        account: UUID,
        creative: String? = "9001",
        date: LocalDate = day,
        ad: UUID? = null,
        sales: String = "359.64",
        marketplace: String = "US",
    ): UUID =
        id(
            "INSERT INTO attribution_daily (account_id, source, marketplace, date, publisher, campaign_external_id, " +
                "ad_group_external_id, creative_external_id, ad_id, click_throughs, detail_page_views, add_to_carts, " +
                "purchases, units_sold, sales) " +
                "VALUES (:a, 'CSV', :m, :d, 'Google Ads', '21345678901', '7001', :c, :ad, 120, 95, 20, 12, 14, :s::numeric) " +
                "RETURNING id",
            "a" to account,
            "m" to marketplace,
            "d" to date,
            "c" to creative,
            "ad" to ad,
            "s" to sales,
        )

    @Test
    fun `a reported line has one row per day`() {
        val account = account()
        row(account, date = day)
        row(account, date = day.plusDays(1))
        row(account, creative = "9002", date = day) // another ad in the same ad group

        assertFailsWith<DataIntegrityViolationException> { row(account, date = day) }
    }

    @Test
    fun `an ad-group-level line (no creative) also has one row per day`() {
        val account = account()
        row(account, creative = null)
        assertFailsWith<DataIntegrityViolationException> { row(account, creative = null) }
    }

    @Test
    fun `two seller accounts can report the same ids`() {
        row(account())
        row(account())
    }

    @Test
    fun `a row can't be matched to another seller account's ad`() {
        val othersAd = ad(account())
        assertFailsWith<DataIntegrityViolationException> { row(account(), ad = othersAd) }
    }

    @Test
    fun `removing the matched ad keeps the row, unmatched`() {
        val account = account()
        val ad = ad(account)
        val row = row(account, ad = ad)

        jdbc.sql("DELETE FROM ads WHERE id = :ad").param("ad", ad).update()

        val adId =
            jdbc
                .sql("SELECT ad_id FROM attribution_daily WHERE id = :r")
                .param("r", row)
                .query(UUID::class.java)
                .optional()
        assertNull(adId.orElse(null))
        val sales =
            jdbc
                .sql("SELECT sales::text FROM attribution_daily WHERE id = :r")
                .param("r", row)
                .query(String::class.java)
                .single()
        assertEquals("359.64", sales)
    }

    @Test
    fun `negative sales are rejected`() {
        assertFailsWith<DataIntegrityViolationException> { row(account(), sales = "-1.00") }
    }

    @Test
    fun `an unknown marketplace is rejected`() {
        assertFailsWith<DataIntegrityViolationException> { row(account(), marketplace = "GB") }
    }
}
