package com.amazfy.channel.application

import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.application.port.GoogleAdsReader
import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.GoogleAdsClient
import com.amazfy.channel.domain.GoogleAdsNotConnectedException
import com.amazfy.channel.domain.GoogleAdsReadFailedException
import com.amazfy.channel.domain.usableGoogleAdsAccounts
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Use case: the Google Ads accounts the current seller account's connection can reach, to choose from (requirement A4).
 * Callers must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
class ListGoogleAdsAccounts(
    private val connections: GoogleAdsConnections,
    private val google: GoogleAdsReader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun all(): List<GoogleAdsAccount> {
        val refreshToken = connections.refreshToken() ?: throw GoogleAdsNotConnectedException()
        val accessToken = google.accessToken(refreshToken)
        val reachable = google.accessibleCustomerIds(accessToken)
        val hierarchies = reachable.mapNotNull { id -> hierarchyOrNull(id, accessToken)?.let { id to it } }.toMap()
        if (reachable.isNotEmpty() && hierarchies.isEmpty()) throw GoogleAdsReadFailedException("no account could be read")
        return usableGoogleAdsAccounts(hierarchies)
    }

    /** One unreadable account, e.g. a canceled one, must not hide the others. */
    private fun hierarchyOrNull(
        customerId: String,
        accessToken: String,
    ): List<GoogleAdsClient>? =
        try {
            google.hierarchyOf(customerId, accessToken)
        } catch (e: GoogleAdsReadFailedException) {
            log.info("Skipping Google Ads account {}: {}", customerId, e.reason)
            null
        }
}
