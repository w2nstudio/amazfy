package com.amazfy.channel.application

import com.amazfy.channel.application.port.Campaigns
import com.amazfy.channel.domain.CampaignAdGroup
import com.amazfy.channel.domain.CampaignNotFoundException
import com.amazfy.channel.domain.CampaignSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Use case: shows the current seller account's campaigns, ad groups and ads (requirement A6).
 * Callers must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional(readOnly = true)
class ListCampaigns(
    private val campaigns: Campaigns,
) {
    fun all(): List<CampaignSummary> = campaigns.all()

    fun adGroupsOf(campaignId: UUID): List<CampaignAdGroup> = campaigns.adGroupsOf(campaignId) ?: throw CampaignNotFoundException()
}
