package com.amazfy.reporting.adapter.outbound.persistence

import com.amazfy.TestcontainersConfiguration
import com.amazfy.common.tenant.CurrentAccount
import com.amazfy.reporting.domain.DateRange
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/** The report SQL with inputs the endpoint tests don't reach. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class ReportNumbersQueriesTest {
    @Autowired
    lateinit var queries: ReportNumbersQueries

    private val range = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28))

    @Test
    fun `a drill-down filter selecting more ads than PostgreSQL allows parameters still works`() {
        // PostgreSQL takes at most 65,535 parameters per statement, so the ads can't each be one
        val manyAds = List(70_000) { UUID.randomUUID() }.toSet()

        CurrentAccount.runAs(UUID.randomUUID()) {
            assertEquals(emptyList(), queries.dailySpend(range, manyAds))
            assertEquals(emptyList(), queries.dailyAttribution(range, manyAds))
        }
    }
}
