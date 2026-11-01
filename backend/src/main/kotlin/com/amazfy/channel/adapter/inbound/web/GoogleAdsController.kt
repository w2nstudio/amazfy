package com.amazfy.channel.adapter.inbound.web

import com.amazfy.api.generated.GoogleAdsApi
import com.amazfy.api.generated.model.GoogleAdsAccountChoice
import com.amazfy.api.generated.model.GoogleAdsConnectStart
import com.amazfy.channel.application.ChooseGoogleAdsAccount
import com.amazfy.channel.application.ConnectGoogleAds
import com.amazfy.channel.application.DisconnectGoogleAds
import com.amazfy.channel.application.GetGoogleAdsConnection
import com.amazfy.channel.application.ListGoogleAdsAccounts
import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.GoogleAdsConnectCancelledException
import com.amazfy.channel.domain.GoogleAdsConnectFailedException
import com.amazfy.common.security.currentUserId
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.ZoneOffset
import java.util.UUID
import com.amazfy.api.generated.model.GoogleAdsAccount as ApiGoogleAdsAccount
import com.amazfy.api.generated.model.GoogleAdsConnection as ApiGoogleAdsConnection

/** Implements the Google Ads part of api/openapi.yaml. Any member may see the connection; only owners may change it. */
@RestController
class GoogleAdsController(
    private val getConnection: GetGoogleAdsConnection,
    private val connect: ConnectGoogleAds,
    private val disconnect: DisconnectGoogleAds,
    private val listAccounts: ListGoogleAdsAccounts,
    private val chooseAccount: ChooseGoogleAdsAccount,
    private val attemptCookie: GoogleAdsConnectCookie,
    private val request: HttpServletRequest, // a proxy to the current request
    @param:Value("\${amazfy.frontend-base-url}") private val frontendBaseUrl: String,
) : GoogleAdsApi {
    private val log = LoggerFactory.getLogger(javaClass)

    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun getGoogleAdsConnection(accountId: UUID): ResponseEntity<ApiGoogleAdsConnection> {
        val connection = getConnection.current()
        return ResponseEntity.ok(
            ApiGoogleAdsConnection(
                connected = connection != null,
                connectedAt = connection?.connectedAt?.atOffset(ZoneOffset.UTC),
                account = connection?.account?.toApi(),
            ),
        )
    }

    /** A fetch call, since only it can carry the access token: the page then sends the browser to the returned address. */
    @PreAuthorize("@accountAccess.canManage(#accountId)")
    override fun startGoogleAdsConnect(accountId: UUID): ResponseEntity<GoogleAdsConnectStart> {
        val start = connect.start(accountId, currentUserId())
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, attemptCookie.of(start.attempt))
            .body(GoogleAdsConnectStart(authorizationUrl = URI.create(start.redirectUrl)))
    }

    /** Google sends the browser back here, without a login: the encrypted attempt says who is connecting what. */
    override fun completeGoogleAdsConnect(
        state: String,
        code: String?,
        error: String?,
    ): ResponseEntity<Unit> {
        val attempt = attemptCookie.read(request.cookies?.firstOrNull { it.name == GoogleAdsConnectCookie.NAME }?.value)
        val outcome =
            try {
                connect.complete(code, error, state, attempt)
                "?googleAds=connected"
            } catch (_: GoogleAdsConnectCancelledException) {
                "" // the seller's own choice: no message
            } catch (e: GoogleAdsConnectFailedException) {
                log.info("{}", e.message)
                "?googleAds=failed"
            }
        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create("$frontendBaseUrl/ads$outcome"))
            .header(HttpHeaders.SET_COOKIE, attemptCookie.deleted())
            .build()
    }

    @PreAuthorize("@accountAccess.canManage(#accountId)")
    override fun disconnectGoogleAds(accountId: UUID): ResponseEntity<Unit> {
        disconnect.run()
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@accountAccess.canManage(#accountId)")
    override fun listGoogleAdsAccounts(accountId: UUID): ResponseEntity<List<ApiGoogleAdsAccount>> =
        ResponseEntity.ok(listAccounts.all().map { it.toApi() })

    @PreAuthorize("@accountAccess.canManage(#accountId)")
    override fun chooseGoogleAdsAccount(
        accountId: UUID,
        googleAdsAccountChoice: GoogleAdsAccountChoice,
    ): ResponseEntity<ApiGoogleAdsAccount> = ResponseEntity.ok(chooseAccount.choose(googleAdsAccountChoice.customerId).toApi())
}

/** Without the login customer id: that's for calling Google, and clients have no use for it. */
private fun GoogleAdsAccount.toApi() = ApiGoogleAdsAccount(customerId = customerId, name = name)
