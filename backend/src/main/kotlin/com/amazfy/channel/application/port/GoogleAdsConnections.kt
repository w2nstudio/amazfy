package com.amazfy.channel.application.port

import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.GoogleAdsConnection
import java.time.Instant

/** The stored Google Ads connection of CurrentAccount. The adapter encrypts the token, so nothing here sees it stored. */
interface GoogleAdsConnections {
    fun current(): GoogleAdsConnection?

    /**
     * The token, or null when not connected. Throws GoogleAdsConnectionExpiredException when it can't be decrypted
     * any more (the encryption key changed): the seller has to connect again, as with a token Google refuses.
     */
    fun refreshToken(): String?

    /**
     * Replaces any earlier token: a seller account has one. A chosen account stays: connecting again happens because
     * the token expired, usually with the same Google login.
     */
    fun save(
        refreshToken: String,
        connectedAt: Instant,
    )

    fun chooseAccount(account: GoogleAdsAccount)

    fun delete()
}
