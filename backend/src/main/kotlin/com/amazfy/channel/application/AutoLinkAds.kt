package com.amazfy.channel.application

import com.amazfy.catalog.application.CatalogService
import com.amazfy.channel.application.port.AdLinks
import com.amazfy.channel.domain.AdLink
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Use case: links the current seller account's unlinked ads whose final URL is a product page of a known product
 * (requirement A7). Reports look up each ad's link when they run, so the report totals need no refresh.
 * Callers must have checked edit access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional
class AutoLinkAds(
    private val links: AdLinks,
    private val catalog: CatalogService,
) {
    fun run(): AutoLinkOutcome {
        val unlinked = links.unlinked()
        val productIds = catalog.listProducts().associate { (it.asin to it.marketplace) to it.id }
        val found =
            unlinked
                .mapNotNull { ad ->
                    val page = ad.link as? AdLink.ProductPage ?: return@mapNotNull null
                    productIds[page.asin to page.marketplace]?.let { ad.id to it }
                }.toMap()
        links.linkUnlinkedToProducts(found)
        return AutoLinkOutcome(linked = found.size, stillUnlinked = unlinked.size - found.size)
    }
}

data class AutoLinkOutcome(
    val linked: Int,
    val stillUnlinked: Int,
)
