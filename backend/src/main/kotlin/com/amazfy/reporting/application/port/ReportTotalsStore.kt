package com.amazfy.reporting.application.port

import java.time.LocalDate

/** The report totals added up in advance (rollup tables, migration V11), always within CurrentAccount. */
interface ReportTotalsStore {
    /**
     * Rebuilds the totals from the daily tables: for the calendar months that [days] touches (a month's total needs
     * all its days), or for all of the account's days when null.
     */
    fun rebuild(days: ClosedRange<LocalDate>?)
}
