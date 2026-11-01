package com.amazfy.channel.application

import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.UnknownGoogleAdsAccountException
import org.springframework.stereotype.Service

/**
 * Use case: which Google Ads account the current seller account reads (requirement A4). Asks Google again rather than
 * trusting the request, so only an account this connection really reaches can be chosen. Callers must have checked
 * access first (@PreAuthorize with accountAccess).
 */
@Service
class ChooseGoogleAdsAccount(
    private val accounts: ListGoogleAdsAccounts,
    private val connections: GoogleAdsConnections,
) {
    fun choose(customerId: String): GoogleAdsAccount {
        val account = accounts.all().find { it.customerId == customerId } ?: throw UnknownGoogleAdsAccountException()
        connections.chooseAccount(account)
        return account
    }
}
