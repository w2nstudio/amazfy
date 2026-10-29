package com.amazfy.attribution.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AttributionReportTest {
    private fun parse(csv: String) = AttributionReport.parse(CsvText.rows(csv.trimIndent()))

    private val header =
        "Date,Publisher,Campaign ID,Ad group ID,Creative ID," +
            "Click-throughs,Detail page views,Add to carts,Purchases,Units sold,Sales"

    @Test
    fun `reads a report below lines of report information`() {
        val report =
            parse(
                """
                Advertiser,AquaLife
                Report range,2026-10-01 to 2026-10-27

                $header
                2026-10-27,Google Ads,21345678901,152340987612,689012345678,36,34,4,2,2,"$80.00"
                """,
            )
        val line = report.lines.single()
        assertEquals(LocalDate.of(2026, 10, 27), line.date)
        assertEquals(AdKey("21345678901", "152340987612", "689012345678"), line.adKey)
        assertEquals(36, line.clickThroughs)
        assertEquals(2, line.purchases)
        assertEquals(BigDecimal("80.00"), line.sales)
    }

    @Test
    fun `knows the names Amazon's API uses, US dates, thousands separators and semicolons`() {
        val report =
            parse(
                """
                date;publisher;campaignId;adGroupId;creativeId;clickThroughs;attributedDetailPageViewsClicks14d;attributedAddToCartClicks14d;attributedPurchases14d;unitsSold14d;attributedSales14d
                10/27/2026;Google Ads;1;2;3;"1,200";1000;100;50;60;"2,400.5"
                """,
            )
        val line = report.lines.single()
        assertEquals(LocalDate.of(2026, 10, 27), line.date)
        assertEquals(1200, line.clickThroughs)
        assertEquals(BigDecimal("2400.50"), line.sales)
    }

    @Test
    fun `keeps Google lines only, adds up repeated lines, and allows lines without a creative`() {
        val report =
            parse(
                """
                $header
                2026-10-27,Google Ads,1,2,3,10,10,2,1,1,40
                2026-10-27,Google Ads,1,2,3,5,5,1,1,1,40
                2026-10-27,Meta,9,9,9,10,10,2,1,1,40
                2026-10-27,Google Ads,1,2,,7,7,1,0,0,0
                """,
            )
        assertEquals(1, report.otherPublisherLines)
        assertEquals(2, report.lines.size)
        assertEquals(15, report.lines.first().clickThroughs)
        assertEquals(BigDecimal("80.00"), report.lines.first().sales)
        assertNull(report.lines.last().adKey) // ad group level only: can't be linked to one ad
    }

    @Test
    fun `a file that isn't an Attribution report names the missing columns and the ones it has`() {
        val error = assertFailsWith<InvalidAttributionReportException> { parse("Date,Campaign ID,Clicks,Cost\n2026-10-27,1,10,5.00") }
        assertContains(error.message!!, "Ad group ID")
        assertContains(error.message!!, "\"Cost\"")
    }

    @Test
    fun `invalid lines reject the whole file and are named`() {
        val error =
            assertFailsWith<InvalidAttributionReportException> {
                parse(
                    """
                    $header
                    2026-10-27,Google Ads,1,2,3,10,10,2,1,1,40
                    27.10.2026,Google Ads,1,2,3,10,10,2,1,1,40
                    2026-10-27,Google Ads,2.13457E+10,2,3,10,10,2,one,1,40
                    """,
                )
            }
        assertContains(error.message!!, "Nothing was imported")
        assertContains(error.message!!, "line 3: Date \"27.10.2026\"")
        assertContains(error.message!!, "line 4: Campaign ID \"2.13457E+10\" was changed by a spreadsheet program")
    }
}
