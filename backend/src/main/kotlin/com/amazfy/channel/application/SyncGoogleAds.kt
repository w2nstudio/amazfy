package com.amazfy.channel.application

import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.application.port.GoogleAdsReader
import com.amazfy.channel.application.port.GoogleAdsStructures
import com.amazfy.channel.domain.GoogleAdsAccountNotChosenException
import com.amazfy.channel.domain.GoogleAdsNotConnectedException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Use case: brings the chosen Google Ads account's campaigns, ad groups and ads into the current seller account, then
 * links the new ads it can (requirements A6, A7). Reads Google before opening a transaction, so a slow Google never
 * holds a database connection, and a failed read changes nothing. No report totals to refresh: spend doesn't change here.
 * Callers must have checked edit access first (@PreAuthorize with accountAccess).
 */
@Service
class SyncGoogleAds(
    private val connections: GoogleAdsConnections,
    private val google: GoogleAdsReader,
    private val structures: GoogleAdsStructures,
    private val autoLink: AutoLinkAds,
    private val transaction: TransactionTemplate,
    private val clock: Clock,
) {
    fun run(): GoogleAdsSyncOutcome {
        val connection = connections.current() ?: throw GoogleAdsNotConnectedException()
        val account = connection.account ?: throw GoogleAdsAccountNotChosenException()
        val refreshToken = connections.refreshToken() ?: throw GoogleAdsNotConnectedException()
        val structure = google.structureOf(account, google.accessToken(refreshToken)).storable()
        val linking =
            transaction.execute {
                structures.save(account.customerId, structure, clock.instant())
                autoLink.run()
            }
        return GoogleAdsSyncOutcome(
            campaigns = structure.campaigns.size,
            adGroups = structure.adGroups.size,
            ads = structure.ads.size,
            linked = linking.linked,
            stillUnlinked = linking.stillUnlinked,
        )
    }
}

data class GoogleAdsSyncOutcome(
    val campaigns: Int,
    val adGroups: Int,
    val ads: Int,
    val linked: Int,
    val stillUnlinked: Int,
)
