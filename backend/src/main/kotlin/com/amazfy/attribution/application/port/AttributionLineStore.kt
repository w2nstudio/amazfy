package com.amazfy.attribution.application.port

import com.amazfy.attribution.domain.AttributionLine
import com.amazfy.common.amazon.Marketplace
import java.util.UUID

/** Saves Attribution report lines (attribution_daily), always within CurrentAccount. */
interface AttributionLineStore {
    /**
     * Inserts each line with its matched ad (null: unmatched), or updates the numbers and match of a line with the same
     * key from an earlier upload: a report uploaded twice never counts twice.
     */
    fun save(
        marketplace: Marketplace,
        lines: Map<AttributionLine, UUID?>,
    )
}
