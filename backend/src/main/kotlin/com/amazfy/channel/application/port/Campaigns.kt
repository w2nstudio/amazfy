package com.amazfy.channel.application.port

import com.amazfy.channel.domain.CampaignAdGroup
import com.amazfy.channel.domain.CampaignSummary
import java.util.UUID

/** Reads campaigns, ad groups and ads, always within CurrentAccount. */
interface Campaigns {
    /** All campaigns, by name, removed ones last. */
    fun all(): List<CampaignSummary>

    /** The campaign's ad groups with their ads, by name and final URL, removed ones last; null when the account has no such campaign. */
    fun adGroupsOf(campaignId: UUID): List<CampaignAdGroup>?
}
