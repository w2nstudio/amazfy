package com.amazfy.channel.application.port

import com.amazfy.channel.domain.GoogleAdsClient

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
}
