package com.amazfy.attribution.adapter.inbound.web

import com.amazfy.api.generated.AttributionApi
import com.amazfy.api.generated.model.AttributionImportResult
import com.amazfy.api.generated.model.Marketplace
import com.amazfy.attribution.application.ImportAttributionCsv
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID
import com.amazfy.common.amazon.Marketplace as DomainMarketplace

/** Implements the Attribution part of api/openapi.yaml. Owners and editors may upload; viewers only see reports. */
@RestController
class AttributionController(
    private val importCsv: ImportAttributionCsv,
) : AttributionApi {
    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun importAttributionCsv(
        accountId: UUID,
        file: MultipartFile,
        marketplace: Marketplace,
        dryRun: Boolean?,
    ): ResponseEntity<AttributionImportResult> {
        val result =
            importCsv.import(file.bytes.toString(Charsets.UTF_8), DomainMarketplace.valueOf(marketplace.value), dryRun == true)
        return ResponseEntity.ok(
            AttributionImportResult(
                fromDate = result.fromDate,
                toDate = result.toDate,
                lines = result.lines,
                matchedLines = result.matchedLines,
                unmatchedLines = result.unmatchedLines,
                otherPublisherLines = result.otherPublisherLines,
                sales = result.sales,
                currency = result.currency,
                saved = result.saved,
            ),
        )
    }
}
