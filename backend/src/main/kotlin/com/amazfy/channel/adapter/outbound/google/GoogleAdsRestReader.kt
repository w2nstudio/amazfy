package com.amazfy.channel.adapter.outbound.google

import com.amazfy.channel.application.port.GoogleAdsReader
import com.amazfy.channel.domain.GoogleAd
import com.amazfy.channel.domain.GoogleAdGroup
import com.amazfy.channel.domain.GoogleAdSpend
import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.GoogleAdsClient
import com.amazfy.channel.domain.GoogleAdsConnectionExpiredException
import com.amazfy.channel.domain.GoogleAdsCustomer
import com.amazfy.channel.domain.GoogleAdsReadFailedException
import com.amazfy.channel.domain.GoogleAdsStructure
import com.amazfy.channel.domain.GoogleCampaign
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
    ): List<GoogleAdsClient> = search("the hierarchy of $customerId", customerId, customerId, HIERARCHY_QUERY, accessToken, ::toClient)

    override fun structureOf(
        account: GoogleAdsAccount,
        accessToken: String,
    ): GoogleAdsStructure =
        GoogleAdsStructure(
            campaigns = read(account, "campaigns", CAMPAIGNS_QUERY, accessToken, ::toCampaign),
            adGroups = read(account, "ad groups", AD_GROUPS_QUERY, accessToken, ::toAdGroup),
            ads = read(account, "ads", ADS_QUERY, accessToken, ::toAd),
        )

    override fun customerOf(
        account: GoogleAdsAccount,
        accessToken: String,
    ): GoogleAdsCustomer =
        read(account, "settings", CUSTOMER_QUERY, accessToken, ::toCustomer).firstOrNull()
            ?: throw GoogleAdsReadFailedException("Google sent no settings for ${account.customerId}")

    override fun spendOf(
        account: GoogleAdsAccount,
        days: ClosedRange<LocalDate>,
        accessToken: String,
    ): List<GoogleAdSpend> {
        val query = "$SPEND_QUERY AND segments.date BETWEEN '${days.start}' AND '${days.endInclusive}'"
        return read(account, "spend", query, accessToken, ::toSpend)
    }

    private fun <T> read(
        account: GoogleAdsAccount,
        what: String,
        query: String,
        accessToken: String,
        convert: (Map<*, *>) -> T,
    ) = search("the $what of ${account.customerId}", account.customerId, account.loginCustomerId, query, accessToken, convert)

    /**
     * All pages of one query, converted page by page: a year of spend is too much to hold as raw JSON.
     * Google's JSON leaves out values that are false or 0, and sends 64-bit numbers as text.
     */
    private fun <T> search(
        what: String,
        customerId: String,
        loginCustomerId: String,
        query: String,
        accessToken: String,
        convert: (Map<*, *>) -> T,
    ): List<T> {
        val results = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val body = mapOf("query" to query) + listOfNotNull(pageToken?.let { "pageToken" to it })
            val page =
                call(what) {
                    http
                        .post()
                        .uri("$apiUrl/customers/{id}/googleAds:search", customerId)
                        .header("Authorization", "Bearer $accessToken")
                        .header("login-customer-id", loginCustomerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(Map::class.java)
                }
            (page?.get("results") as? List<*>).orEmpty().mapTo(results) { convert(it as Map<*, *>) }
            pageToken = page?.get("nextPageToken") as? String
        } while (pageToken != null)
        return results
    }

    private fun toCampaign(result: Map<*, *>): GoogleCampaign {
        val campaign = result["campaign"] as Map<*, *>
        return GoogleCampaign(
            id = campaign["id"].toString(),
            name = campaign["name"] as? String ?: "",
            status = campaign["status"].toString(),
            dailyBudgetMicros = (result["campaignBudget"] as? Map<*, *>)?.get("amountMicros")?.toString()?.toLong(),
            currency = (result["customer"] as? Map<*, *>)?.get("currencyCode") as? String,
        )
    }

    private fun toAdGroup(result: Map<*, *>): GoogleAdGroup {
        val adGroup = result["adGroup"] as Map<*, *>
        return GoogleAdGroup(
            id = adGroup["id"].toString(),
            campaignId = (result["campaign"] as Map<*, *>)["id"].toString(),
            name = adGroup["name"] as? String ?: "",
            status = adGroup["status"].toString(),
        )
    }

    private fun toAd(result: Map<*, *>): GoogleAd {
        val adGroupAd = result["adGroupAd"] as Map<*, *>
        val ad = adGroupAd["ad"] as Map<*, *>
        return GoogleAd(
            id = ad["id"].toString(),
            adGroupId = (result["adGroup"] as Map<*, *>)["id"].toString(),
            campaignId = (result["campaign"] as Map<*, *>)["id"].toString(),
            finalUrl = (ad["finalUrls"] as? List<*>)?.firstOrNull() as? String ?: "",
            status = adGroupAd["status"].toString(),
        )
    }

    private fun toCustomer(result: Map<*, *>): GoogleAdsCustomer {
        val customer = result["customer"] as Map<*, *>
        return GoogleAdsCustomer(
            currency = customer["currencyCode"].toString(),
            // Only decides which day counts as today, and each read covers earlier days again, so a wrong zone does no harm
            timeZone = runCatching { ZoneId.of(customer["timeZone"].toString()) }.getOrDefault(ZoneOffset.UTC),
        )
    }

    private fun toSpend(result: Map<*, *>): GoogleAdSpend {
        val metrics = result["metrics"] as? Map<*, *>

        fun metric(name: String) = metrics?.get(name)?.toString()?.toLong() ?: 0
        return GoogleAdSpend(
            campaignId = (result["campaign"] as Map<*, *>)["id"].toString(),
            adGroupId = (result["adGroup"] as Map<*, *>)["id"].toString(),
            adId = ((result["adGroupAd"] as Map<*, *>)["ad"] as Map<*, *>)["id"].toString(),
            date = LocalDate.parse((result["segments"] as Map<*, *>)["date"].toString()),
            impressions = metric("impressions"),
            clicks = metric("clicks"),
            costMicros = metric("costMicros"),
        )
    }

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

        /** Phase 1 covers Search campaigns. No status filter: removed ones keep their past spend, and must show as removed. */
        const val SEARCH_ONLY = "WHERE campaign.advertising_channel_type = 'SEARCH'"
        const val CAMPAIGNS_QUERY =
            "SELECT campaign.id, campaign.name, campaign.status, campaign_budget.amount_micros, customer.currency_code " +
                "FROM campaign $SEARCH_ONLY"
        const val AD_GROUPS_QUERY = "SELECT ad_group.id, ad_group.name, ad_group.status, campaign.id FROM ad_group $SEARCH_ONLY"
        const val ADS_QUERY =
            "SELECT ad_group_ad.ad.id, ad_group_ad.ad.final_urls, ad_group_ad.status, ad_group.id, campaign.id " +
                "FROM ad_group_ad $SEARCH_ONLY"
        const val CUSTOMER_QUERY = "SELECT customer.currency_code, customer.time_zone FROM customer"
        const val SPEND_QUERY =
            "SELECT campaign.id, ad_group.id, ad_group_ad.ad.id, segments.date, metrics.impressions, metrics.clicks, " +
                "metrics.cost_micros FROM ad_group_ad $SEARCH_ONLY"
    }
}
