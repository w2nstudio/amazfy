package com.amazfy.attribution.application.port

import com.amazfy.attribution.domain.AttributionLine
import com.amazfy.common.amazon.Marketplace
import java.time.LocalDate
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

    /** Links each unmatched line to the Google ad with its ids, where one is now stored. Null when none matched. */
    fun matchUnmatched(): MatchedLines?
}

/** Lines that [AttributionLineStore.matchUnmatched] linked, and the days they cover. */
data class MatchedLines(
    val lines: Int,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
)
