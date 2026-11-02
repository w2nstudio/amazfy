package com.amazfy.channel.application

import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.application.port.GoogleAdsSyncQueue
import com.amazfy.channel.domain.GoogleAdsSyncProblem
import com.amazfy.common.error.AppException
import com.amazfy.common.tenant.CurrentAccount
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Job: keeps every seller account's Google Ads data up to date without anyone doing anything (requirement A9). It looks
 * often for accounts that are due instead of running once a day, so a server that was down catches up, and sellers don't
 * all come due at the same moment. One account's failure never stops the others.
 */
@Service
class DailyGoogleAdsSync(
    private val queue: GoogleAdsSyncQueue,
    private val sync: SyncGoogleAds,
    private val connections: GoogleAdsConnections,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${amazfy.google-ads.sync-cron}")
    fun runDue() {
        while (true) {
            val now = clock.instant()
            val accountId = queue.takeNextDue(now.minus(SYNC_EVERY), now.minus(RETRY_AFTER), now) ?: return
            syncOne(accountId)
        }
    }

    private fun syncOne(accountId: UUID) =
        CurrentAccount.runAs(accountId) {
            try {
                sync.run()
            } catch (e: AppException) {
                log.info("Daily Google Ads sync of account {} didn't work: {}", accountId, e.message)
            } catch (e: RuntimeException) {
                log.error("Daily Google Ads sync of account {} failed", accountId, e)
                connections.recordSyncProblem(GoogleAdsSyncProblem.READ_FAILED)
            }
        }

    private companion object {
        /** Just under a day: with exactly a day, each sync would start a little later than the one before. */
        val SYNC_EVERY: Duration = Duration.ofHours(23)
        val RETRY_AFTER: Duration = Duration.ofHours(1)
    }
}
