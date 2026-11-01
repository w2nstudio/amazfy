package com.amazfy.channel.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.util.UUID

/** A campaign as the seller sees it (requirement A6). Its ad groups and ads are read separately, when opened. */
data class CampaignSummary(
    val id: UUID,
    val externalId: String,
    val channelAccountId: String?,
    val name: String,
    val status: String,
    val dailyBudgetMicros: Long?,
    val budgetCurrency: String?,
    val adGroupCount: Int,
    val adCount: Int,
)

data class CampaignAdGroup(
    val id: UUID,
    val externalId: String,
    val name: String,
    val status: String,
    val ads: List<CampaignAd>,
)

/** An ad, known by its final URL and id: Google's ads have no name. */
data class CampaignAd(
    val id: UUID,
    val externalId: String,
    val finalUrl: String,
    val status: String,
    val productName: String?,
    val brandName: String?,
)

class CampaignNotFoundException : AppException(ErrorKind.NOT_FOUND, "No such campaign in this seller account.")
