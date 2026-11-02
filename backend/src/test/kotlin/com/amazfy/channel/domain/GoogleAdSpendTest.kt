package com.amazfy.channel.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class GoogleAdSpendTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun `the first read goes back 90 days, today included`() {
        assertEquals(LocalDate.of(2026, 8, 5)..today, spendDaysToRead(today, lastSynced = null))
    }

    @Test
    fun `after a sync today, two weeks are read again`() {
        assertEquals(LocalDate.of(2026, 10, 20)..today, spendDaysToRead(today, lastSynced = today))
    }

    @Test
    fun `after a sync yesterday, a day more`() {
        assertEquals(LocalDate.of(2026, 10, 19)..today, spendDaysToRead(today, lastSynced = today.minusDays(1)))
    }

    @Test
    fun `after a month of failed syncs, the whole gap is read`() {
        assertEquals(LocalDate.of(2026, 9, 19)..today, spendDaysToRead(today, lastSynced = LocalDate.of(2026, 10, 2)))
    }
}
