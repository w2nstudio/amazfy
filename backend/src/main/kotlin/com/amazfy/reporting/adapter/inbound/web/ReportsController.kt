package com.amazfy.reporting.adapter.inbound.web

import com.amazfy.api.generated.ReportsApi
import com.amazfy.api.generated.model.BreakdownLevel
import com.amazfy.api.generated.model.BreakdownRowKind
import com.amazfy.api.generated.model.ReportBreakdown
import com.amazfy.api.generated.model.ReportSummary
import com.amazfy.reporting.application.GetReportBreakdown
import com.amazfy.reporting.application.GetReportSummary
import com.amazfy.reporting.domain.BreakdownFilter
import com.amazfy.reporting.domain.BreakdownRow
import com.amazfy.reporting.domain.CurrencyTotals
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import com.amazfy.api.generated.model.BreakdownRow as ApiBreakdownRow
import com.amazfy.api.generated.model.CurrencyTotals as ApiCurrencyTotals
import com.amazfy.reporting.domain.BreakdownLevel as Level

/** Implements the Reports part of api/openapi.yaml. Any member of the seller account may read its reports. */
@RestController
class ReportsController(
    private val summary: GetReportSummary,
    private val breakdown: GetReportBreakdown,
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

    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun getReportBreakdown(
        by: BreakdownLevel,
        accountId: UUID,
        brandId: UUID?,
        productId: UUID?,
        brandLevel: Boolean?,
        notLinked: Boolean?,
        campaignId: UUID?,
        adGroupId: UUID?,
        from: LocalDate?,
        to: LocalDate?,
    ): ResponseEntity<ReportBreakdown> {
        val filter = BreakdownFilter(brandId, productId, brandLevel == true, notLinked == true, campaignId, adGroupId)
        val report = breakdown.forRange(Level.valueOf(by.value), filter, from, to)
        return ResponseEntity.ok(ReportBreakdown(report.range.from, report.range.to, by, report.rows.map { it.toApi() }))
    }
}

private fun BreakdownRow.toApi() =
    ApiBreakdownRow(kind = BreakdownRowKind.forValue(kind.name), name = name, totals = totals.toApi(), id = id, detail = detail)

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
