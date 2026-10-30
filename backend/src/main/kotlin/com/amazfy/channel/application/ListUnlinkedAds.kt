package com.amazfy.channel.application

import com.amazfy.channel.application.port.AdLinks
import com.amazfy.channel.domain.UnlinkedAd
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Use case: the current seller account's ads linked to no product or brand (requirement A7), the ads behind the report
 * row "Not linked to a product or brand". Callers must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional(readOnly = true)
class ListUnlinkedAds(
    private val links: AdLinks,
) {
    fun all(): List<UnlinkedAd> = links.unlinked()
}
