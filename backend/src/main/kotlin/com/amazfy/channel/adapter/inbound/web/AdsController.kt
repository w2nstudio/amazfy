package com.amazfy.channel.adapter.inbound.web

import com.amazfy.api.generated.AdsApi
import com.amazfy.api.generated.model.AdLinkKind
import com.amazfy.api.generated.model.AdLinkRequest
import com.amazfy.api.generated.model.AutoLinkResult
import com.amazfy.channel.application.AutoLinkAds
import com.amazfy.channel.application.LinkAd
import com.amazfy.channel.application.ListUnlinkedAds
import com.amazfy.channel.domain.AdLink
import com.amazfy.channel.domain.UnlinkedAd
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import com.amazfy.api.generated.model.Marketplace as ApiMarketplace
import com.amazfy.api.generated.model.UnlinkedAd as ApiUnlinkedAd

/** Implements the Ads part of api/openapi.yaml. Any member may read; owners and editors may change links. */
@RestController
class AdsController(
    private val listUnlinked: ListUnlinkedAds,
    private val autoLink: AutoLinkAds,
    private val linkAd: LinkAd,
) : AdsApi {
    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun listUnlinkedAds(accountId: UUID): ResponseEntity<List<ApiUnlinkedAd>> =
        ResponseEntity.ok(listUnlinked.all().map { it.toApi() })

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
