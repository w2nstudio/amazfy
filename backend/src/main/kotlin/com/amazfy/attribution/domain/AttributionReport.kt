package com.amazfy.attribution.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A column Amazfy needs from an Amazon Attribution report, and the header names it may have. Names are compared
 * without case, spaces or punctuation ("Click-throughs" = "clickthroughs"). Not yet checked against a real export
 * (requirements, open risk): when one arrives, its headers are added here. Brand halo columns ("Total …") are
 * deliberately not listed: Amazfy counts the promoted product's numbers.
 */
enum class ReportColumn(
    val label: String,
    val required: Boolean,
    vararg names: String,
) {
    DATE("Date", true, "date", "day", "reportdate"),
    PUBLISHER("Publisher", false, "publisher", "publishername"),
    CAMPAIGN("Campaign ID", true, "campaignid", "campaign"),
    AD_GROUP("Ad group ID", true, "adgroupid", "adgroup"),
    CREATIVE("Creative ID", false, "creativeid", "creative", "adid"),
    CLICK_THROUGHS("Click-throughs", true, "clickthroughs", "clicks"),
    DETAIL_PAGE_VIEWS("Detail page views", true, "detailpageviews", "dpv", "attributeddetailpageviewsclicks14d"),
    ADD_TO_CARTS("Add to carts", true, "addtocarts", "addtocart", "atc", "attributedaddtocartclicks14d"),
    PURCHASES("Purchases", true, "purchases", "orders", "attributedpurchases14d"),
    UNITS_SOLD("Units sold", true, "unitssold", "units", "unitssold14d"),
    SALES("Sales", true, "sales", "productsales", "attributedsales14d"),
    ;

    val names: Set<String> = names.toSet()
}

/** One report line: an ad's (or ad group's) numbers on one day, as Amazon reported them. */
data class AttributionLine(
    val date: LocalDate,
    val publisher: String,
    val campaignId: String,
    val adGroupId: String,
    /** Empty when the report only goes down to ad groups (requirements, open risk). */
    val creativeId: String?,
    val clickThroughs: Long,
    val detailPageViews: Long,
    val addToCarts: Long,
    val purchases: Long,
    val unitsSold: Long,
    val sales: BigDecimal,
) {
    /** What identifies the line: the same key in a later upload replaces it. */
    val key: LineKey get() = LineKey(date, publisher, campaignId, adGroupId, creativeId)

    /** Google's ids of the ad the line is about; null without a creative. */
    val adKey: AdKey? get() = creativeId?.let { AdKey(campaignId, adGroupId, it) }

    operator fun plus(other: AttributionLine) =
        copy(
            clickThroughs = clickThroughs + other.clickThroughs,
            detailPageViews = detailPageViews + other.detailPageViews,
            addToCarts = addToCarts + other.addToCarts,
            purchases = purchases + other.purchases,
            unitsSold = unitsSold + other.unitsSold,
            sales = sales + other.sales,
        )
}

data class LineKey(
    val date: LocalDate,
    val publisher: String,
    val campaignId: String,
    val adGroupId: String,
    val creativeId: String?,
)

/** Google's campaign, ad group and ad ids: the same ids Amazfy keeps as external_id (Attribution calls the ad a creative). */
data class AdKey(
    val campaignId: String,
    val adGroupId: String,
    val creativeId: String,
)

/** The report's Google lines (one per key) and how many lines of other publishers were left out. */
data class ParsedReport(
    val lines: List<AttributionLine>,
    val otherPublisherLines: Int,
)

class InvalidAttributionReportException(
    message: String,
) : AppException(ErrorKind.BAD_REQUEST, message)

/**
 * Reads an Amazon Attribution report from its rows. All or nothing: any invalid line rejects the whole file, naming the
 * lines to fix, since half an import would show wrong totals without anyone noticing.
 */
object AttributionReport {
    /** Exports may start with a few lines about the report; the header is looked for in the first rows. */
    private const val HEADER_SEARCH_ROWS = 20
    private const val ERRORS_SHOWN = 5
    private const val GOOGLE_ADS = "Google Ads"
    private val US_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy")
    private val COMPACT_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun parse(rows: List<CsvRow>): ParsedReport {
        val (headerIndex, columns) = findHeader(rows)
        val lines = mutableListOf<AttributionLine>()
        val errors = mutableListOf<String>()
        rows.drop(headerIndex + 1).forEach { row ->
            try {
                lines += readLine(row, columns)
            } catch (e: InvalidValue) {
                errors += "line ${row.line}: ${e.message}"
            }
        }
        if (errors.isNotEmpty()) {
            val more = if (errors.size > ERRORS_SHOWN) " (and ${errors.size - ERRORS_SHOWN} more)" else ""
            throw InvalidAttributionReportException(
                "Nothing was imported. Please fix ${errors.take(ERRORS_SHOWN).joinToString("; ")}$more.",
            )
        }
        if (lines.isEmpty()) throw InvalidAttributionReportException("The file has a header but no report lines.")

        // Only Google is measured in Phase 1: another publisher's sales would otherwise count as unmatched Google sales
        val (google, others) = lines.partition { it.publisher.lowercase().contains("google") }
        // The same line twice (e.g. a report split by another column Amazfy doesn't keep) is added up into one
        val merged = google.groupBy { it.key }.map { (_, same) -> same.reduce(AttributionLine::plus) }
        return ParsedReport(merged, others.size)
    }

    private fun findHeader(rows: List<CsvRow>): Pair<Int, Map<ReportColumn, Int>> {
        val candidates =
            rows.take(HEADER_SEARCH_ROWS).mapIndexed { index, row ->
                val found =
                    ReportColumn.entries
                        .mapNotNull { column ->
                            row.cells
                                .indexOfFirst { normalize(it) in column.names }
                                .takeIf { it >= 0 }
                                ?.let { column to it }
                        }.toMap()
                Triple(index, row, found)
            }
        val header =
            candidates.firstOrNull { (_, _, found) -> ReportColumn.entries.filter { it.required }.all { it in found } }
        if (header != null) return header.first to header.third

        val best = candidates.maxByOrNull { it.third.size }
        if (best == null) throw InvalidAttributionReportException("The file is empty.")
        val missing = ReportColumn.entries.filter { it.required && it !in best.third }.joinToString { it.label }
        val headers =
            best.second.cells
                .filter { it.isNotBlank() }
                .joinToString { "\"$it\"" }
        throw InvalidAttributionReportException(
            "This doesn't look like an Amazon Attribution report: no column found for $missing. The file's columns are $headers.",
        )
    }

    private fun readLine(
        row: CsvRow,
        columns: Map<ReportColumn, Int>,
    ): AttributionLine {
        fun cell(column: ReportColumn): String =
            columns[column]
                ?.let { row.cells.getOrNull(it) }
                ?.trim()
                .orEmpty()
        return AttributionLine(
            date = date(cell(ReportColumn.DATE)),
            publisher = cell(ReportColumn.PUBLISHER).ifEmpty { GOOGLE_ADS }, // no publisher column: a Google-only report
            campaignId = id(cell(ReportColumn.CAMPAIGN), ReportColumn.CAMPAIGN, required = true)!!,
            adGroupId = id(cell(ReportColumn.AD_GROUP), ReportColumn.AD_GROUP, required = true)!!,
            creativeId = id(cell(ReportColumn.CREATIVE), ReportColumn.CREATIVE, required = false),
            clickThroughs = count(cell(ReportColumn.CLICK_THROUGHS), ReportColumn.CLICK_THROUGHS),
            detailPageViews = count(cell(ReportColumn.DETAIL_PAGE_VIEWS), ReportColumn.DETAIL_PAGE_VIEWS),
            addToCarts = count(cell(ReportColumn.ADD_TO_CARTS), ReportColumn.ADD_TO_CARTS),
            purchases = count(cell(ReportColumn.PURCHASES), ReportColumn.PURCHASES),
            unitsSold = count(cell(ReportColumn.UNITS_SOLD), ReportColumn.UNITS_SOLD),
            sales = money(cell(ReportColumn.SALES)),
        )
    }

    /** 2026-10-27, 10/27/2026 (US exports) or 20261027 (Amazon's API). */
    private fun date(text: String): LocalDate =
        listOf(DateTimeFormatter.ISO_LOCAL_DATE, US_DATE, COMPACT_DATE).firstNotNullOfOrNull { format ->
            try {
                LocalDate.parse(text, format)
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: throw InvalidValue("Date \"$text\" is not a date like 2026-10-27 or 10/27/2026")

    private fun id(
        text: String,
        column: ReportColumn,
        required: Boolean,
    ): String? {
        val id = text.removePrefix("'") // a leading apostrophe keeps spreadsheets from turning ids into numbers
        if (id.isEmpty()) return if (required) throw InvalidValue("${column.label} is empty") else null
        if (Regex("""\d(\.\d+)?[eE]\+\d+""").matches(id)) {
            throw InvalidValue("${column.label} \"$id\" was changed by a spreadsheet program; please export the report again")
        }
        return id
    }

    private fun count(
        text: String,
        column: ReportColumn,
    ): Long {
        val digits = text.replace(",", "").replace(" ", "")
        if (digits.isEmpty()) return 0
        return digits.toLongOrNull()?.takeIf { it >= 0 }
            ?: throw InvalidValue("${column.label} \"$text\" is not a whole number")
    }

    /** "$1,234.50", "1234.5" or "£12.00": the marketplace decides the currency, so the symbol is dropped. */
    private fun money(text: String): BigDecimal {
        val amount = text.replace(Regex("""[,\s$£€]"""), "")
        if (amount.isEmpty()) return BigDecimal.ZERO.setScale(2)
        return amount.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.setScale(2, RoundingMode.HALF_UP)
            ?: throw InvalidValue("Sales \"$text\" is not an amount")
    }

    private fun normalize(header: String) = header.lowercase().filter { it.isLetterOrDigit() }

    private class InvalidValue(
        message: String,
    ) : RuntimeException(message)
}
