package com.amazfy.channel.application

import com.amazfy.channel.application.port.GoogleAdsConnections
import com.amazfy.channel.domain.GoogleAdsConnection
import org.springframework.stereotype.Service

/** Use case: whether the current seller account has connected Google Ads. Callers must have checked view access first. */
@Service
class GetGoogleAdsConnection(
    private val connections: GoogleAdsConnections,
) {
    fun current(): GoogleAdsConnection? = connections.current()
}
