package com.amazfy.channel.application

import com.amazfy.channel.application.port.GoogleAdsConnections
import org.springframework.stereotype.Service

/**
 * Use case: Amazfy stops being able to read the current seller account's Google Ads (requirement A4): the token is
 * deleted, so Amazfy has nothing left to read with. Numbers already brought in stay. Callers must have checked access
 * first (@PreAuthorize with accountAccess).
 *
 * Deliberately doesn't revoke the token at Google. Revoking ends everything that Google login granted Amazfy, so it
 * would also disconnect the person's other seller accounts, as is common for an agency with one Google login. The
 * seller can remove Amazfy in their Google Account, and the page says where.
 */
@Service
class DisconnectGoogleAds(
    private val connections: GoogleAdsConnections,
) {
    fun run() = connections.delete()
}
