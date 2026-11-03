package com.amazfy.attribution.application

import com.amazfy.attribution.application.port.AttributionLineStore
import com.amazfy.reporting.application.RefreshReportTotals
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Use case: links the current seller account's unmatched Attribution lines to the Google ads that now have their ids,
 * and refreshes the report totals of their days. A report can be uploaded before a sync brings in its ads; this moves
 * those sales from "Not matched to an ad" to their ads without uploading again. Called by the Google Ads sync after it
 * stored the ads, in the sync's transaction.
 */
@Service
@Transactional
class MatchAttributionLines(
    private val store: AttributionLineStore,
    private val reportTotals: RefreshReportTotals,
) {
    /** How many lines were matched just now. */
    fun run(): Int {
        val matched = store.matchUnmatched() ?: return 0
        reportTotals.forDays(matched.firstDay, matched.lastDay)
        return matched.lines
    }
}
