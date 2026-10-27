package com.amazfy.reporting.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendTest {
    private val first = LocalDate.of(2026, 10, 1)
    private val range = DateRange(first, first.plusDays(2)) // 1-3 October

    private fun spend(
        day: LocalDate,
        dollars: Long,
        currency: String = "USD",
    ) = DailySpend(day, SpendTotals(currency, dollars * 1_000_000, 100, 10))

    private fun sales(
        day: LocalDate,
        amount: String,
        currency: String = "USD",
    ) = DailyAttribution(day, AttributionTotals(currency, 9, 8, 3, 2, 2, BigDecimal(amount)))

    @Test
    fun `every day of the range is in the series, with zeros on days without numbers`() {
        val series = Trend.of(range, listOf(spend(first, 10)), listOf(sales(first.plusDays(2), "40.00"))).single()

        assertEquals(listOf(first, first.plusDays(1), first.plusDays(2)), series.days.map { it.date })
        val (oct1, oct2, oct3) = series.days.map { it.totals }
        assertEquals(0, BigDecimal("10").compareTo(oct1.spend))
        assertEquals(BigDecimal("0.00"), oct1.sales)
        assertEquals(0, oct2.spend.signum())
        assertNull(oct2.roas) // no spend that day
        assertEquals(BigDecimal("40.00"), oct3.sales)
    }

    @Test
    fun `numbers on the same day are added up`() {
        val series = Trend.of(range, listOf(spend(first, 10), spend(first, 5)), listOf(sales(first, "40.00"), sales(first, "20.00")))
        val totals = series.single().days[0].totals
        assertEquals(0, BigDecimal("15").compareTo(totals.spend))
        assertEquals(BigDecimal("60.00"), totals.sales)
        assertEquals(BigDecimal("4.00"), totals.roas)
    }

    @Test
    fun `one series per currency, sorted by code`() {
        val series = Trend.of(range, listOf(spend(first, 10, "GBP")), listOf(sales(first, "30.00", "EUR")))
        assertEquals(listOf("EUR", "GBP"), series.map { it.currency })
        assertTrue(series.all { it.days.size == 3 })
        assertEquals(BigDecimal("0.00"), series[1].days[0].totals.sales) // GBP spend, sales are EUR: never mixed
    }

    @Test
    fun `without numbers there are no series`() {
        assertEquals(emptyList(), Trend.of(range, emptyList(), emptyList()))
    }
}
