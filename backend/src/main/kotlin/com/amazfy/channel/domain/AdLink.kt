package com.amazfy.channel.domain

import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

/**
 * What an ad's final URL points to, which decides how it can be linked (requirements, section 4). A product page has
 * one ASIN, so the ad links to that product automatically. A Brand Store or anything else has no single product: the
 * seller chooses a product or brand.
 */
sealed interface AdLink {
    data class ProductPage(
        val asin: String,
        val marketplace: Marketplace,
    ) : AdLink

    data class BrandStore(
        val marketplace: Marketplace,
    ) : AdLink

    data object Other : AdLink

    companion object {
        /** /dp/<ASIN>, also below a product title (/Filter-40L/dp/<ASIN>), /gp/product/<ASIN> and mobile /gp/aw/d/<ASIN>. */
        private val PRODUCT_PATH = Regex("""/(?:dp|gp/product|gp/aw/d)/([A-Za-z0-9]{10})(?:/|$)""")

        fun of(finalUrl: String): AdLink {
            val url = finalUrl.trim()
            val uri =
                try {
                    URI(if ("://" in url) url else "https://$url")
                } catch (_: URISyntaxException) {
                    return Other
                }
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return Other
            val marketplace = Marketplace.entries.find { it.amazonDomain == host } ?: return Other
            val path = uri.path.orEmpty()
            val asin =
                PRODUCT_PATH
                    .find(path)
                    ?.groupValues
                    ?.get(1)
                    ?.uppercase()
            return when {
                asin != null -> ProductPage(asin, marketplace)
                path.startsWith("/stores/") -> BrandStore(marketplace)
                else -> Other
            }
        }
    }
}

/** An ad linked to no product or brand yet. */
data class UnlinkedAd(
    val id: UUID,
    val externalId: String,
    val finalUrl: String,
    val campaignName: String,
    val adGroupName: String,
) {
    val link: AdLink get() = AdLink.of(finalUrl)
}

class AdNotFoundException : AppException(ErrorKind.NOT_FOUND, "No such ad in this seller account.")

class InvalidAdLinkException(
    message: String,
) : AppException(ErrorKind.BAD_REQUEST, message)
