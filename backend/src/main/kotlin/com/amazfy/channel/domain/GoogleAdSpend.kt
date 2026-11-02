package com.amazfy.channel.domain

import java.time.LocalDate
import java.time.ZoneId

/** What one ad cost on one day, in the Google Ads account's time zone. An ad's id is unique only inside its ad group. */
data class GoogleAdSpend(
    val campaignId: String,
    val adGroupId: String,
    val adId: String,
    val date: LocalDate,
    val impressions: Long,
    val clicks: Long,
    val costMicros: Long,
)

/** A Google Ads account's own settings: all its money is in this currency, and its days follow this time zone. */
data class GoogleAdsCustomer(
    val currency: String,
    val timeZone: ZoneId,
)

/** The first read of an account goes back as far as the dashboard's longest preset. */
private const val FIRST_READ_DAYS = 90L

/**
 * Google corrects past days, for example after invalid clicks, and doesn't say when a day is final. Forum reports show
 * changes after several days, so each read covers two weeks again, the same as Amazon's attribution window.
 */
private const val DAYS_READ_AGAIN = 14L

/**
 * The days to read from Google, up to [today]. Counting back from the last sync, not from today, means syncs that failed
 * for a while, such as after an expired connection, leave no gap.
 */
fun spendDaysToRead(
    today: LocalDate,
    lastSynced: LocalDate?,
): ClosedRange<LocalDate> {
    val from = lastSynced?.minusDays(DAYS_READ_AGAIN - 1) ?: today.minusDays(FIRST_READ_DAYS - 1)
    return from..today
}
