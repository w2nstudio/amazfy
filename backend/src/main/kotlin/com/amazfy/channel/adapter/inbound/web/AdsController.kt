package com.amazfy.channel.adapter.inbound.web

import com.amazfy.api.generated.AdsApi
import com.amazfy.api.generated.model.AdLinkKind
import com.amazfy.api.generated.model.AdLinkRequest
import com.amazfy.api.generated.model.AutoLinkResult
import com.amazfy.api.generated.model.ChannelStatus
import com.amazfy.channel.application.AutoLinkAds
import com.amazfy.channel.application.LinkAd
import com.amazfy.channel.application.ListCampaigns
import com.amazfy.channel.application.ListUnlinkedAds
import com.amazfy.channel.domain.AdLink
import com.amazfy.channel.domain.CampaignAd
import com.amazfy.channel.domain.CampaignAdGroup
import com.amazfy.channel.domain.CampaignSummary
import com.amazfy.channel.domain.UnlinkedAd
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import com.amazfy.api.generated.model.Campaign as ApiCampaign
import com.amazfy.api.generated.model.CampaignAd as ApiCampaignAd
import com.amazfy.api.generated.model.CampaignAdGroup as ApiCampaignAdGroup
import com.amazfy.api.generated.model.Marketplace as ApiMarketplace
import com.amazfy.api.generated.model.UnlinkedAd as ApiUnlinkedAd

/** Implements the Ads part of api/openapi.yaml. Any member may read; owners and editors may change links. */
@RestController
class AdsController(
    private val listUnlinked: ListUnlinkedAds,
    private val autoLink: AutoLinkAds,
    private val linkAd: LinkAd,
    private val campaigns: ListCampaigns,
) : AdsApi {
    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun listUnlinkedAds(accountId: UUID): ResponseEntity<List<ApiUnlinkedAd>> =
        ResponseEntity.ok(listUnlinked.all().map { it.toApi() })

    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun listCampaigns(accountId: UUID): ResponseEntity<List<ApiCampaign>> = ResponseEntity.ok(campaigns.all().map { it.toApi() })

    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun listCampaignAdGroups(
        accountId: UUID,
        campaignId: UUID,
    ): ResponseEntity<List<ApiCampaignAdGroup>> = ResponseEntity.ok(campaigns.adGroupsOf(campaignId).map { it.toApi() })

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun autoLinkAds(accountId: UUID): ResponseEntity<AutoLinkResult> {
        val outcome = autoLink.run()
        return ResponseEntity.ok(AutoLinkResult(linked = outcome.linked, stillUnlinked = outcome.stillUnlinked))
    }

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun linkAd(
        accountId: UUID,
        adId: UUID,
        adLinkRequest: AdLinkRequest,
    ): ResponseEntity<Unit> {
        linkAd.link(adId, adLinkRequest.productId, adLinkRequest.brandId)
        return ResponseEntity.noContent().build()
    }
}

private fun CampaignSummary.toApi() =
    ApiCampaign(
        id = id,
        externalId = externalId,
        channelAccountId = channelAccountId,
        name = name,
        status = ChannelStatus.forValue(status),
        dailyBudget = dailyBudgetMicros?.let { BigDecimal.valueOf(it, 6).setScale(2, RoundingMode.HALF_UP) },
        budgetCurrency = budgetCurrency,
        adGroupCount = adGroupCount,
        adCount = adCount,
    )

private fun CampaignAdGroup.toApi() =
    ApiCampaignAdGroup(
        id = id,
        externalId = externalId,
        name = name,
        status = ChannelStatus.forValue(status),
        ads = ads.map { it.toApi() },
    )

private fun CampaignAd.toApi() =
    ApiCampaignAd(
        id = id,
        externalId = externalId,
        finalUrl = finalUrl,
        status = ChannelStatus.forValue(status),
        productName = productName,
        brandName = brandName,
    )

private fun UnlinkedAd.toApi(): ApiUnlinkedAd {
    val link = link
    return ApiUnlinkedAd(
        id = id,
        externalId = externalId,
        finalUrl = finalUrl,
        campaignName = campaignName,
        adGroupName = adGroupName,
        linkKind =
            when (link) {
                is AdLink.ProductPage -> AdLinkKind.PRODUCT_PAGE
                is AdLink.BrandStore -> AdLinkKind.BRAND_STORE
                AdLink.Other -> AdLinkKind.OTHER
            },
        asin = (link as? AdLink.ProductPage)?.asin,
        marketplace =
            when (link) {
                is AdLink.ProductPage -> link.marketplace
                is AdLink.BrandStore -> link.marketplace
                AdLink.Other -> null
            }?.let { ApiMarketplace.forValue(it.name) },
    )
}
