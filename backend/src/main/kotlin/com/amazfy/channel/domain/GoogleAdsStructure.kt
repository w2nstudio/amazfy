package com.amazfy.channel.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind

/** A campaign as on Google. The budget is empty when it isn't per day (a budget over a custom period). */
data class GoogleCampaign(
    val id: String,
    val name: String,
    val status: String,
    val dailyBudgetMicros: Long?,
    val currency: String?,
)

data class GoogleAdGroup(
    val id: String,
    val campaignId: String,
    val name: String,
    val status: String,
)

/** Empty [finalUrl]: an ad type without one, such as a call ad. It's kept, since it can still cost money. */
data class GoogleAd(
    val id: String,
    val adGroupId: String,
    val campaignId: String,
    val finalUrl: String,
    val status: String,
)

/** The campaigns, ad groups and ads of one Google Ads account. */
data class GoogleAdsStructure(
    val campaigns: List<GoogleCampaign>,
    val adGroups: List<GoogleAdGroup>,
    val ads: List<GoogleAd>,
) {
    /**
     * Without what can't be stored: a status this version doesn't know (Google reserves UNKNOWN for values newer than
     * the API version), or a parent that isn't there, because Google reads each level separately and an ad group can
     * appear between two reads. The next sync picks those up.
     */
    fun storable(): GoogleAdsStructure {
        val keptCampaigns = campaigns.filter { it.status in KNOWN_STATUSES }
        val campaignIds = keptCampaigns.mapTo(HashSet()) { it.id }
        val keptAdGroups = adGroups.filter { it.status in KNOWN_STATUSES && it.campaignId in campaignIds }
        val adGroupIds = keptAdGroups.mapTo(HashSet()) { it.campaignId to it.id }
        val keptAds = ads.filter { it.status in KNOWN_STATUSES && (it.campaignId to it.adGroupId) in adGroupIds }
        return GoogleAdsStructure(keptCampaigns, keptAdGroups, keptAds)
    }

    private companion object {
        val KNOWN_STATUSES = setOf("ENABLED", "PAUSED", "REMOVED")
    }
}

class GoogleAdsAccountNotChosenException : AppException(ErrorKind.CONFLICT, "Choose a Google Ads account first.")
