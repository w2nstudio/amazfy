package com.amazfy.attribution.application

import com.amazfy.account.application.AccountService
import com.amazfy.attribution.application.port.AttributionLineStore
import com.amazfy.attribution.application.port.GoogleAdLookup
import com.amazfy.attribution.domain.AttributionReport
import com.amazfy.attribution.domain.CsvText
import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.amazon.Region
import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import com.amazfy.common.tenant.CurrentAccount
import com.amazfy.reporting.application.RefreshReportTotals
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Use case: an Amazon Attribution report uploaded as CSV (requirement A10), for the current seller account and one of
 * its marketplaces. Reads the lines, links each to the Google ad with the same ids, saves them (a re-upload updates)
 * and refreshes the report totals of the imported days, all in one transaction: a failure leaves nothing behind.
 * Callers must have checked edit access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional
class ImportAttributionCsv(
    private val accounts: AccountService,
    private val googleAds: GoogleAdLookup,
    private val store: AttributionLineStore,
    private val reportTotals: RefreshReportTotals,
) {
    fun import(
        csv: String,
        marketplace: Marketplace,
    ): AttributionImport {
        val region = accounts.findAccount(CurrentAccount.get())!!.region
        if (marketplace.region != region) throw ReportOutsideRegionException(region)

        val report = AttributionReport.parse(CsvText.rows(csv))
        val adIds = googleAds.idsOf(report.lines.mapNotNull { it.adKey }.toSet())
        val matched = report.lines.associateWith { line -> line.adKey?.let { adIds[it] } }
        store.save(marketplace, matched)

        val days = report.lines.map { it.date }
        reportTotals.forDays(days.min(), days.max())
        return AttributionImport(
            fromDate = days.min(),
            toDate = days.max(),
            lines = matched.size,
            matchedLines = matched.values.count { it != null },
            otherPublisherLines = report.otherPublisherLines,
        )
    }
}

data class AttributionImport(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val lines: Int,
    val matchedLines: Int,
    val otherPublisherLines: Int,
) {
    val unmatchedLines: Int get() = lines - matchedLines
}

class ReportOutsideRegionException(
    region: Region,
) : AppException(
        ErrorKind.BAD_REQUEST,
        "This seller account is in ${region.displayName}: choose one of its marketplaces " +
            "(${region.marketplaces.joinToString { it.name }}).",
    )
