package com.amazfy.channel.application.port

import com.amazfy.channel.domain.GoogleAdsConnection
import java.time.Instant

/** The stored Google Ads connection of CurrentAccount. The adapter encrypts the token, so nothing here sees it stored. */
interface GoogleAdsConnections {
    fun current(): GoogleAdsConnection?

    /** Replaces any earlier connection: a seller account has one. */
    fun save(
        refreshToken: String,
        connectedAt: Instant,
    )

    fun delete()
}
