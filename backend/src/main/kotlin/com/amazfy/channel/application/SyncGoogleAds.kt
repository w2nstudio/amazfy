package com.amazfy.channel.application

import com.amazfy.attribution.application.MatchAttributionLines
import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.application.port.GoogleAdsReader
import com.amazfy.channel.application.port.GoogleAdsSpendStore
import com.amazfy.channel.application.port.GoogleAdsStructures
import com.amazfy.channel.domain.GoogleAdsAccountNotChosenException
import com.amazfy.channel.domain.GoogleAdsConnectionExpiredException
import com.amazfy.channel.domain.GoogleAdsNotConnectedException
import com.amazfy.channel.domain.GoogleAdsReadFailedException
import com.amazfy.channel.domain.GoogleAdsSyncProblem
import com.amazfy.channel.domain.spendDaysToRead
import com.amazfy.reporting.application.RefreshReportTotals
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate

/**
 * Use case: brings the chosen Google Ads account's campaigns, ad groups, ads and daily spend into the current seller
 * account, then links the new ads it can and matches earlier Attribution lines to them (requirements A6, A7, A9, A10).
 * Reads Google before opening a transaction, so a slow Google never holds a database connection, and a failed read
 * changes nothing. Campaigns before spend and Attribution: both need the ads stored. Callers must have checked edit
 * access first (@PreAuthorize with accountAccess).
 */
@Service
class SyncGoogleAds(
    private val connections: GoogleAdsConnections,
    private val google: GoogleAdsReader,
    private val structures: GoogleAdsStructures,
    private val spendStore: GoogleAdsSpendStore,
    private val reportTotals: RefreshReportTotals,
    private val autoLink: AutoLinkAds,
    private val matchAttribution: MatchAttributionLines,
    private val transaction: TransactionTemplate,
    private val clock: Clock,
) {
    /** A failure stays on the connection until a sync works, so the seller sees it also when the daily job hit it. */
    fun run(): GoogleAdsSyncOutcome =
        try {
            readAndSave()
        } catch (e: GoogleAdsConnectionExpiredException) {
            connections.recordSyncProblem(GoogleAdsSyncProblem.CONNECTION_EXPIRED)
            throw e
        } catch (e: GoogleAdsReadFailedException) {
            connections.recordSyncProblem(GoogleAdsSyncProblem.READ_FAILED)
            throw e
        }

    private fun readAndSave(): GoogleAdsSyncOutcome {
        val connection = connections.current() ?: throw GoogleAdsNotConnectedException()
        val account = connection.account ?: throw GoogleAdsAccountNotChosenException()
        val refreshToken = connections.refreshToken() ?: throw GoogleAdsNotConnectedException()
        val accessToken = google.accessToken(refreshToken)
        val structure = google.structureOf(account, accessToken).storable()
        val customer = google.customerOf(account, accessToken)
        val now = clock.instant()
        val days =
            spendDaysToRead(
                today = now.atZone(customer.timeZone).toLocalDate(),
                lastSynced = connection.syncedAt?.atZone(customer.timeZone)?.toLocalDate(),
            )
        val spend = google.spendOf(account, days, accessToken)
        val saved =
            transaction.execute {
                structures.save(account.customerId, structure, now)
                val spendMicros = spendStore.replace(account.customerId, days, customer.currency, spend)
                reportTotals.forDays(days.start, days.endInclusive)
                Saved(spendMicros, autoLink.run(), matchAttribution.run())
            }
        val (spendMicros, linking, attributionLinesMatched) = saved
        return GoogleAdsSyncOutcome(
            campaigns = structure.campaigns.size,
            adGroups = structure.adGroups.size,
            ads = structure.ads.size,
            linked = linking.linked,
            stillUnlinked = linking.stillUnlinked,
            attributionLinesMatched = attributionLinesMatched,
            spendFrom = days.start,
            spendTo = days.endInclusive,
            spendMicros = spendMicros,
            currency = customer.currency,
        )
    }

    private data class Saved(
        val spendMicros: Long,
        val linking: AutoLinkOutcome,
        val attributionLinesMatched: Int,
    )
}

data class GoogleAdsSyncOutcome(
    val campaigns: Int,
    val adGroups: Int,
    val ads: Int,
    val linked: Int,
    val stillUnlinked: Int,
    val attributionLinesMatched: Int,
    val spendFrom: LocalDate,
    val spendTo: LocalDate,
    val spendMicros: Long,
    val currency: String,
)
