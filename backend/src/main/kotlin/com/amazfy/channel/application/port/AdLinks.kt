package com.amazfy.channel.application.port

import com.amazfy.channel.domain.UnlinkedAd
import java.util.UUID

/** The links from ads to products and brands (ads.product_id, ads.brand_id), always within CurrentAccount. */
interface AdLinks {
    /** Ads linked to nothing, sorted by campaign, ad group and final URL. */
    fun unlinked(): List<UnlinkedAd>

    /** Links each ad to its product, unless the ad got a link in the meantime: a seller's choice is never overwritten. */
    fun linkUnlinkedToProducts(productOfAd: Map<UUID, UUID>)

    /** Replaces the ad's link with exactly one of the two; false when the account has no such ad. */
    fun setLink(
        adId: UUID,
        productId: UUID?,
        brandId: UUID?,
    ): Boolean
}
