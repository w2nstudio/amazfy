package com.amazfy.reporting.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReportTotalsTest {
    private val today = LocalDate.of(2026, 10, 27)

    @Test
    fun `without dates a report covers the last 30 days up to yesterday`() {
        assertEquals(DateRange(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 26)), DateRange.of(null, null, today))
        // Only an end date: the 30 days up to it
        assertEquals(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), DateRange.of(null, LocalDate.of(2026, 9, 30), today))
    }

    @Test
    fun `a range must run forwards and cover at most 366 days`() {
        DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)) // one day is fine
        DateRange(LocalDate.of(2025, 10, 27), LocalDate.of(2026, 10, 27)) // 366 days, the most
        assertFailsWith<InvalidDateRangeException> { DateRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)) }
        assertFailsWith<InvalidDateRangeException> { DateRange(LocalDate.of(2025, 10, 26), LocalDate.of(2026, 10, 27)) }
    }

    private fun totals(
        spend: String = "100.00",
        sales: String = "391.00",
        purchases: Long = 12,
        clickThroughs: Long = 150,
    ) = CurrencyTotals("USD", BigDecimal(spend), 5_000, 160, clickThroughs, 130, 30, purchases, 14, BigDecimal(sales))

    @Test
    fun `ROAS, CPA and conversion rate follow the requirements' definitions`() {
        val t = totals()
        assertEquals(BigDecimal("3.91"), t.roas) // 391.00 sales / 100.00 spend
        assertEquals(BigDecimal("8.33"), t.cpa) // 100.00 spend / 12 purchases
        assertEquals(BigDecimal("8.00"), t.conversionRate) // 12 purchases / 150 click-throughs x 100
    }

    @Test
    fun `a metric that can't be calculated is left out`() {
        assertNull(totals(spend = "0").roas)
        assertNull(totals(purchases = 0).cpa)
        assertNull(totals(clickThroughs = 0).conversionRate)
    }

    @Test
    fun `currencies are kept apart, marketplaces of one currency are added up`() {
        val combined =
            CurrencyTotals.combine(
                spend = listOf(SpendTotals("EUR", 50_000_000, 1_000, 40), SpendTotals("GBP", 20_500_000, 400, 15)),
                attribution =
                    listOf(
                        AttributionTotals("EUR", 30, 25, 6, 3, 3, BigDecimal("90.00")), // DE
                        AttributionTotals("EUR", 10, 8, 2, 1, 1, BigDecimal("30.00")), // FR
                    ),
            )

        assertEquals(listOf("EUR", "GBP"), combined.map { it.currency })
        val eur = combined[0]
        assertEquals(BigDecimal("50.000000"), eur.spend) // micros, exact
        assertEquals(4, eur.purchases)
        assertEquals(BigDecimal("120.00"), eur.sales)
        assertEquals(BigDecimal("2.40"), eur.roas)
        // GBP has spend but no Amazon numbers in this range: sales 0, so ROAS 0 (money spent, nothing sold)
        assertEquals(BigDecimal("0.00"), combined[1].sales)
        assertEquals(BigDecimal("0.00"), combined[1].roas)
    }
}
