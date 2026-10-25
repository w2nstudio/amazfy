package com.amazfy.sampledata.adapter.inbound.web

import com.amazfy.api.generated.DevelopmentApi
import com.amazfy.api.generated.model.SampleDataResult
import com.amazfy.sampledata.application.SampleDataService
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Implements the Development part of api/openapi.yaml. Exists only in the "local" profile: on any other server
 * the path has no controller, so it answers 404. Owner only, because it replaces the account's ads and numbers.
 */
@RestController
@Profile("local")
class SampleDataController(
    private val sampleData: SampleDataService,
) : DevelopmentApi {
    @PreAuthorize("@accountAccess.canManage(#accountId)")
    override fun createSampleData(accountId: UUID): ResponseEntity<SampleDataResult> {
        val summary = sampleData.replaceWithSampleData()
        return ResponseEntity.ok(
            SampleDataResult(
                fromDate = summary.fromDate,
                toDate = summary.toDate,
                products = summary.products,
                campaigns = summary.campaigns,
                ads = summary.ads,
                spendRows = summary.spendRows,
                attributionRows = summary.attributionRows,
            ),
        )
    }
}
