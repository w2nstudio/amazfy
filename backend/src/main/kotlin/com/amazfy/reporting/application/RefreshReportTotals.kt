package com.amazfy.reporting.application

import com.amazfy.reporting.application.port.ReportTotalsStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Use case: brings the current seller account's report totals up to date after its daily numbers changed. Reports
 * read these totals instead of millions of daily rows (V11), so whatever writes ad_spend_daily or attribution_daily,
 * or deletes ads, calls this in the same transaction: reports then never see new daily rows without their totals.
 */
@Service
@Transactional
class RefreshReportTotals(
    private val store: ReportTotalsStore,
) {
    /** After daily numbers from [from] to [to] were written or changed, e.g. by an import or a sync. */
    fun forDays(
        from: LocalDate,
        to: LocalDate,
    ) = store.rebuild(from..to)

    /** After the account's numbers were replaced as a whole, or ads were deleted. */
    fun forAllDays() = store.rebuild(null)
}
