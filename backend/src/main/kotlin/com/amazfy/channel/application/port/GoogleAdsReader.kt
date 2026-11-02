package com.amazfy.channel.application.port

import com.amazfy.channel.domain.GoogleAdSpend
import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.GoogleAdsClient
import com.amazfy.channel.domain.GoogleAdsCustomer
import com.amazfy.channel.domain.GoogleAdsStructure
import java.time.LocalDate

/** Reading from the Google Ads API. Throws GoogleAdsReadFailedException when Google fails or refuses a call. */
interface GoogleAdsReader {
    /** A short-lived key for the calls below. Throws GoogleAdsConnectionExpiredException when Google refuses the token. */
    fun accessToken(refreshToken: String): String

    /** The accounts this Google login has been invited to directly (not those below a manager account). */
    fun accessibleCustomerIds(accessToken: String): List<String>

    /** The account itself (level 0) and every account below it. */
    fun hierarchyOf(
        customerId: String,
        accessToken: String,
    ): List<GoogleAdsClient>

    /** The account's Search campaigns with their ad groups and ads, removed ones included. */
    fun structureOf(
        account: GoogleAdsAccount,
        accessToken: String,
    ): GoogleAdsStructure

    /** The account's currency and time zone. */
    fun customerOf(
        account: GoogleAdsAccount,
        accessToken: String,
    ): GoogleAdsCustomer

    /** What each ad of its Search campaigns cost on these days, removed ads included. Days without any numbers are left out. */
    fun spendOf(
        account: GoogleAdsAccount,
        days: ClosedRange<LocalDate>,
        accessToken: String,
    ): List<GoogleAdSpend>
}
