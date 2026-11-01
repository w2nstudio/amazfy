package com.amazfy.channel.domain

/**
 * A Google Ads account a seller can choose: one that holds campaigns. [loginCustomerId] is the account the access comes
 * through (Google's login-customer-id header): the account itself, or a manager account above it. Ids are digits only.
 */
data class GoogleAdsAccount(
    val customerId: String,
    val name: String?,
    val loginCustomerId: String,
)

/** One line of the account hierarchy below an account the Google login can reach; level 0 is that account itself. */
data class GoogleAdsClient(
    val customerId: String,
    val name: String?,
    val isManager: Boolean,
    val status: String,
    val isTestAccount: Boolean,
    val level: Int,
)

/**
 * The accounts worth offering, from the hierarchy below each account the login reaches. Left out:
 * - manager accounts, since they only manage others and hold no campaigns of their own
 * - canceled, closed or suspended accounts, since nothing runs there. Except test accounts: Google shows them as
 *   canceled, yet they are readable, and they are all a developer has
 *
 * An account can be reached several ways. The shortest wins: direct access keeps working if a manager link is removed.
 */
fun usableGoogleAdsAccounts(hierarchies: Map<String, List<GoogleAdsClient>>): List<GoogleAdsAccount> =
    hierarchies
        .flatMap { (reachedThrough, clients) -> clients.map { reachedThrough to it } }
        .filter { (_, client) -> !client.isManager && (client.status == "ENABLED" || client.isTestAccount) }
        .groupBy { (_, client) -> client.customerId }
        .values
        .map { ways ->
            val (reachedThrough, client) = ways.minBy { (_, client) -> client.level }
            GoogleAdsAccount(client.customerId, client.name, loginCustomerId = reachedThrough)
        }.sortedWith(compareBy({ it.name == null }, { it.name?.lowercase() }, { it.customerId }))
