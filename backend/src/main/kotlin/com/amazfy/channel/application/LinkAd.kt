package com.amazfy.channel.application

import com.amazfy.catalog.application.CatalogService
import com.amazfy.channel.application.port.AdLinks
import com.amazfy.channel.domain.AdNotFoundException
import com.amazfy.channel.domain.InvalidAdLinkException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Use case: the seller links an ad to a product, or to a brand for brand-level spend (requirement A7). Reports look up
 * each ad's link when they run, so the report totals need no refresh.
 * Callers must have checked edit access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional
class LinkAd(
    private val links: AdLinks,
    private val catalog: CatalogService,
) {
    fun link(
        adId: UUID,
        productId: UUID?,
        brandId: UUID?,
    ) {
        if ((productId == null) == (brandId == null)) throw InvalidAdLinkException("Choose a product or a brand.")
        // The database would refuse another account's too; this gives the seller a clear answer
        if (productId != null && catalog.listProducts().none { it.id == productId }) {
            throw InvalidAdLinkException("Choose one of your products.")
        }
        if (brandId != null && catalog.listBrands().none { it.id == brandId }) {
            throw InvalidAdLinkException("Choose one of your brands.")
        }
        if (!links.setLink(adId, productId, brandId)) throw AdNotFoundException()
    }
}
