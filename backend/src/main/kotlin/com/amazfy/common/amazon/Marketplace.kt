package com.amazfy.common.amazon

// Amazon's geography, shared by the modules: accounts have a region, products a marketplace, reports (M3) both.
// Plain Kotlin, so every module's domain may use it. Codes are Amazon's own (NA/EU, and UK rather than ISO's GB).

/**
 * An Amazon region. Amazon gives a seller one seller account per region (a "unified account"), and that account sells
 * in the region's marketplaces only. Phase 1: the regions with marketplaces where Amazon Attribution is available.
 */
enum class Region(
    val displayName: String,
) {
    NA("North America"),
    EU("Europe"),
    ;

    val marketplaces: List<Marketplace> get() = Marketplace.entries.filter { it.region == this }
}

/** An Amazon country site. Sales are in the marketplace's currency and never summed across currencies. */
enum class Marketplace(
    val region: Region,
    val amazonDomain: String,
    val currency: String,
) {
    US(Region.NA, "amazon.com", "USD"),
    CA(Region.NA, "amazon.ca", "CAD"),
    UK(Region.EU, "amazon.co.uk", "GBP"),
    DE(Region.EU, "amazon.de", "EUR"),
    FR(Region.EU, "amazon.fr", "EUR"),
    IT(Region.EU, "amazon.it", "EUR"),
    ES(Region.EU, "amazon.es", "EUR"),
}
