package com.amazfy.reporting.adapter.inbound.web

import com.amazfy.api.generated.ReportsApi
import com.amazfy.api.generated.model.ReportSummary
import com.amazfy.reporting.application.GetReportSummary
import com.amazfy.reporting.domain.CurrencyTotals
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import com.amazfy.api.generated.model.CurrencyTotals as ApiCurrencyTotals

/** Implements the Reports part of api/openapi.yaml. Any member of the seller account may read its reports. */
@RestController
class ReportsController(
    private val summary: GetReportSummary,
) : ReportsApi {
    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun getReportSummary(
        accountId: UUID,
        from: LocalDate?,
        to: LocalDate?,
    ): ResponseEntity<ReportSummary> {
        val report = summary.forRange(from, to)
        return ResponseEntity.ok(ReportSummary(report.range.from, report.range.to, report.currencies.map { it.toApi() }))
    }
}

private fun CurrencyTotals.toApi() =
    ApiCurrencyTotals(
        currency = currency,
        spend = spend.setScale(2, RoundingMode.HALF_UP), // exact internally, cents for display
        impressions = impressions,
        clicks = clicks,
        clickThroughs = clickThroughs,
        detailPageViews = detailPageViews,
        addToCarts = addToCarts,
        purchases = purchases,
        unitsSold = unitsSold,
        sales = sales,
        roas = roas,
        cpa = cpa,
        conversionRate = conversionRate,
    )
