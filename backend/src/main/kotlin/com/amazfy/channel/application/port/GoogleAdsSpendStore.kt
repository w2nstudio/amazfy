package com.amazfy.channel.application.port

import com.amazfy.channel.domain.GoogleAdSpend
import java.time.LocalDate

/** The stored daily spend of CurrentAccount's ads from Google. Callers refresh the report totals in the same transaction. */
interface GoogleAdsSpendStore {
    /**
     * Replaces these [days] of the ads that came from Google Ads account [customerId]. Google doesn't send days without
     * any numbers, so replacing is what removes a day it has corrected to nothing. Other days, and ads from elsewhere,
     * stay. Rows of ads not stored are left out. Returns the micros stored.
     */
    fun replace(
        customerId: String,
        days: ClosedRange<LocalDate>,
        currency: String,
        spend: List<GoogleAdSpend>,
    ): Long
}
