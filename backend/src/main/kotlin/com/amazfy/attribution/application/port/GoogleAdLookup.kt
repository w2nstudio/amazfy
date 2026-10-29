package com.amazfy.attribution.application.port

import com.amazfy.attribution.domain.AdKey
import java.util.UUID

/** Finds the seller account's Google ads by Google's ids, always within CurrentAccount. */
interface GoogleAdLookup {
    /** Amazfy's ad id for each of [keys] that names a known ad; unknown keys are left out. */
    fun idsOf(keys: Set<AdKey>): Map<AdKey, UUID>
}
