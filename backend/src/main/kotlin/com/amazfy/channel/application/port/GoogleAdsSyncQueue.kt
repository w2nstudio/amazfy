package com.amazfy.channel.application.port

import java.time.Instant
import java.util.UUID

/** Which seller accounts are due for a sync. A job's view across all seller accounts, so it holds no account's data. */
interface GoogleAdsSyncQueue {
    /**
     * Takes one seller account with a chosen Google Ads account that last synced before [syncedBefore] and was last
     * taken before [attemptedBefore], marking it taken at [now]; null when none is left. Connections waiting to be
     * connected again are skipped. Taking is atomic: two servers never get the same one.
     */
    fun takeNextDue(
        syncedBefore: Instant,
        attemptedBefore: Instant,
        now: Instant,
    ): UUID?
}
