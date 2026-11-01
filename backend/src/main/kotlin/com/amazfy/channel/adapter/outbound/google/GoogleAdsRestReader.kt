package com.amazfy.channel.adapter.outbound.google

import com.amazfy.channel.application.port.GoogleAdsReader
import com.amazfy.channel.domain.GoogleAdsClient
import com.amazfy.channel.domain.GoogleAdsConnectionExpiredException
import com.amazfy.channel.domain.GoogleAdsReadFailedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * The Google Ads API over REST. Sends no developer token: Google retired them in September 2026, and access now belongs
 * to the Google Cloud project that owns the OAuth client. Never log tokens: only Google's error answers are logged.
 */
@Component
class GoogleAdsRestReader(
    @param:Value("\${amazfy.google.client-id}") private val clientId: String,
    @param:Value("\${amazfy.google.client-secret}") private val clientSecret: String,
    @param:Value("\${amazfy.google.token-uri}") private val tokenUri: String,
    @param:Value("\${amazfy.google-ads.api-url}") private val apiUrl: String,
) : GoogleAdsReader {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = RestClient.create()

    override fun accessToken(refreshToken: String): String {
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
                add("client_id", clientId)
                add("client_secret", clientSecret)
            }
        val response =
            try {
                http
                    .post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map::class.java)
            } catch (e: RestClientResponseException) {
                if ("invalid_grant" in e.responseBodyAsString) throw GoogleAdsConnectionExpiredException()
                throw failed("the token request", e)
            } catch (e: RestClientException) {
                throw failed("the token request", e)
            }
        return response?.get("access_token") as? String ?: throw GoogleAdsReadFailedException("Google sent no access token")
    }

    override fun accessibleCustomerIds(accessToken: String): List<String> {
        val response =
            call("the list of accessible accounts") {
                http
                    .get()
                    .uri("$apiUrl/customers:listAccessibleCustomers")
                    .header("Authorization", "Bearer $accessToken")
                    .retrieve()
                    .body(Map::class.java)
            }
        return (response?.get("resourceNames") as? List<*>).orEmpty().map { it.toString().removePrefix("customers/") }
    }

    override fun hierarchyOf(
        customerId: String,
        accessToken: String,
    ): List<GoogleAdsClient> {
        val clients = mutableListOf<GoogleAdsClient>()
        var pageToken: String? = null
        do {
            val body = mapOf("query" to HIERARCHY_QUERY) + listOfNotNull(pageToken?.let { "pageToken" to it })
            val page =
                call("the hierarchy of $customerId") {
                    http
                        .post()
                        .uri("$apiUrl/customers/{id}/googleAds:search", customerId)
                        .header("Authorization", "Bearer $accessToken")
                        .header("login-customer-id", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(Map::class.java)
                }
            (page?.get("results") as? List<*>).orEmpty().mapTo(clients) { toClient(it as Map<*, *>) }
            pageToken = page?.get("nextPageToken") as? String
        } while (pageToken != null)
        return clients
    }

    /** Google's JSON leaves out values that are false or 0, and sends 64-bit numbers as text. */
    private fun toClient(result: Map<*, *>): GoogleAdsClient {
        val client = result["customerClient"] as Map<*, *>
        return GoogleAdsClient(
            customerId = client["id"].toString(),
            name = (client["descriptiveName"] as? String)?.takeIf { it.isNotBlank() },
            isManager = client["manager"] == true,
            status = client["status"].toString(),
            isTestAccount = client["testAccount"] == true,
            level = client["level"]?.toString()?.toInt() ?: 0,
        )
    }

    private fun <T> call(
        what: String,
        request: () -> T,
    ): T =
        try {
            request()
        } catch (e: RestClientException) {
            throw failed(what, e)
        }

    private fun failed(
        what: String,
        e: RestClientException,
    ): GoogleAdsReadFailedException {
        log.warn("Google Ads refused {}: {}", what, e.message) // Google's answer, which holds no token of ours
        return GoogleAdsReadFailedException(e.message)
    }

    private companion object {
        /** No status filter: test accounts can be canceled, and usableGoogleAdsAccounts decides what's offered. */
        const val HIERARCHY_QUERY =
            "SELECT customer_client.id, customer_client.descriptive_name, customer_client.manager, " +
                "customer_client.status, customer_client.test_account, customer_client.level FROM customer_client"
    }
}
