package com.amazfy.channel.application.port

import com.amazfy.channel.domain.GoogleAdsStructure
import java.time.Instant

/** The stored Google campaigns, ad groups and ads of CurrentAccount. */
interface GoogleAdsStructures {
    /**
     * Adds what's new and updates what's known, found again by Google's ids. Nothing is deleted, and links to products
     * and brands stay. Records [syncedAt] on the connection, if [customerId] is still the chosen account.
     */
    fun save(
        customerId: String,
        structure: GoogleAdsStructure,
        syncedAt: Instant,
    )
}
