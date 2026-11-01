package com.amazfy.channel.adapter.outbound.google

import com.amazfy.channel.domain.GoogleAd
import com.amazfy.channel.domain.GoogleAdGroup
import com.amazfy.channel.domain.GoogleAdsAccount
import com.amazfy.channel.domain.GoogleAdsConnectionExpiredException
import com.amazfy.channel.domain.GoogleAdsReadFailedException
import com.amazfy.channel.domain.GoogleCampaign
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The real adapter against a fake Google: a local server answering like Google's token endpoint and Ads API. */
class GoogleAdsRestReaderTest {
    private var answer: Pair<Int, String> = 500 to "{}"
    private val pages = ArrayDeque<String>()
    private val requests = mutableListOf<Recorded>()

    private class Recorded(
        val method: String,
        val path: String,
        val authorization: String?,
        val loginCustomerId: String?,
        val developerToken: String?,
        val body: String,
    )

    private val fakeGoogle =
        HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                requests +=
                    Recorded(
                        exchange.requestMethod,
                        exchange.requestURI.path,
                        exchange.requestHeaders.getFirst("Authorization"),
                        exchange.requestHeaders.getFirst("login-customer-id"),
                        exchange.requestHeaders.getFirst("developer-token"),
                        exchange.requestBody.readAllBytes().decodeToString(),
                    )
                val (status, body) = pages.removeFirstOrNull()?.let { 200 to it } ?: answer
                respond(exchange, status, body)
            }
            start()
        }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String,
    ) {
        exchange.responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @AfterEach
    fun stopFakeGoogle() = fakeGoogle.stop(0)

    private val reader by lazy {
        val base = "http://localhost:${fakeGoogle.address.port}"
        GoogleAdsRestReader("test-client-id", "test-secret", "$base/token", "$base/v26")
    }

    @Test
    fun `a refresh token becomes an access token`() {
        answer = 200 to """{"access_token":"short-lived","expires_in":3599}"""

        assertEquals("short-lived", reader.accessToken("1//long"))

        val form =
            requests.single().body.split("&").associate { pair ->
                pair.split("=").let { it[0] to URLDecoder.decode(it[1], Charsets.UTF_8) }
            }
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("1//long", form["refresh_token"])
        assertEquals("test-secret", form["client_secret"])
    }

    @Test
    fun `a token Google refuses means the seller has to connect again`() {
        answer = 400 to """{"error":"invalid_grant","error_description":"Token has been expired or revoked."}"""
        assertFailsWith<GoogleAdsConnectionExpiredException> { reader.accessToken("1//old") }
    }

    @Test
    fun `other token failures are read failures`() {
        answer = 500 to """{"error":"internal_failure"}"""
        assertFailsWith<GoogleAdsReadFailedException> { reader.accessToken("1//long") }
    }

    @Test
    fun `lists accessible accounts as plain ids, without a developer token`() {
        answer = 200 to """{"resourceNames":["customers/9998887776","customers/1234567890"]}"""

        assertEquals(listOf("9998887776", "1234567890"), reader.accessibleCustomerIds("short-lived"))

        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/v26/customers:listAccessibleCustomers", request.path)
        assertEquals("Bearer short-lived", request.authorization)
        assertNull(request.developerToken)
    }

    @Test
    fun `a login that reaches nothing gives an empty list`() {
        answer = 200 to "{}"
        assertTrue(reader.accessibleCustomerIds("short-lived").isEmpty())
    }

    @Test
    fun `reads the hierarchy through the account itself, in Google's JSON`() {
        answer = 200 to
            """
            {"results":[
              {"customerClient":{"id":"9998887776","descriptiveName":"Amazfy test manager","manager":true,"status":"ENABLED","testAccount":true}},
              {"customerClient":{"id":"1234567890","descriptiveName":"AquaLife test","status":"CANCELED","testAccount":true,"level":"1"}},
              {"customerClient":{"id":"1112223334","status":"ENABLED","level":"1"}}
            ]}
            """.trimIndent()

        val clients = reader.hierarchyOf("9998887776", "short-lived")

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/v26/customers/9998887776/googleAds:search", request.path)
        assertEquals("9998887776", request.loginCustomerId)
        assertEquals("Bearer short-lived", request.authorization)
        assertNull(request.developerToken)
        assertTrue("customer_client.test_account" in request.body)
        assertFalse("status =" in request.body, "test accounts can be canceled, so the query mustn't filter on status")

        val (manager, test, unnamed) = clients
        assertTrue(manager.isManager)
        assertEquals(0, manager.level)
        assertEquals("AquaLife test", test.name)
        assertEquals("CANCELED", test.status)
        assertTrue(test.isTestAccount)
        assertEquals(1, test.level)
        assertFalse(test.isManager)
        assertNull(unnamed.name)
        assertFalse(unnamed.isTestAccount)
    }

    @Test
    fun `follows every page of a large hierarchy`() {
        pages += """{"results":[{"customerClient":{"id":"1","status":"ENABLED"}}],"nextPageToken":"page-2"}"""
        pages += """{"results":[{"customerClient":{"id":"2","status":"ENABLED"}}]}"""

        assertEquals(listOf("1", "2"), reader.hierarchyOf("9", "short-lived").map { it.customerId })
        assertTrue("page-2" in requests[1].body)
    }

    @Test
    fun `a refused call is a read failure`() {
        answer = 403 to """{"error":{"status":"PERMISSION_DENIED"}}"""
        assertFailsWith<GoogleAdsReadFailedException> { reader.hierarchyOf("9", "short-lived") }
        assertFailsWith<GoogleAdsReadFailedException> { reader.accessibleCustomerIds("short-lived") }
        assertFailsWith<GoogleAdsReadFailedException> { reader.structureOf(ACCOUNT, "short-lived") }
    }

    @Test
    fun `reads campaigns, ad groups and ads through the manager account, in Google's JSON`() {
        pages +=
            """
            {"results":[
              {"campaign":{"id":"21","name":"Filters","status":"ENABLED"},"campaignBudget":{"amountMicros":"15000000"},"customer":{"currencyCode":"USD"}},
              {"campaign":{"id":"22","name":"Custom period","status":"PAUSED"},"campaignBudget":{},"customer":{"currencyCode":"USD"}}
            ]}
            """.trimIndent()
        pages += """{"results":[{"adGroup":{"id":"31","name":"Filter 40L","status":"ENABLED"},"campaign":{"id":"21"}}]}"""
        pages +=
            """
            {"results":[
              {"adGroupAd":{"ad":{"id":"41","finalUrls":["https://www.amazon.com/dp/B0AAA11111","https://other"]},"status":"REMOVED"},"adGroup":{"id":"31"},"campaign":{"id":"21"}},
              {"adGroupAd":{"ad":{"id":"42"},"status":"ENABLED"},"adGroup":{"id":"31"},"campaign":{"id":"21"}}
            ]}
            """.trimIndent()

        val structure = reader.structureOf(ACCOUNT, "short-lived")

        assertEquals(3, requests.size)
        requests.forEach {
            assertEquals("/v26/customers/1234567890/googleAds:search", it.path)
            assertEquals("9998887776", it.loginCustomerId)
            assertEquals("Bearer short-lived", it.authorization)
            assertNull(it.developerToken)
            assertTrue("advertising_channel_type = 'SEARCH'" in it.body)
            assertFalse("status =" in it.body, "removed ones must come too, or they'd look alive forever")
        }
        assertEquals(GoogleCampaign("21", "Filters", "ENABLED", 15_000_000, "USD"), structure.campaigns[0])
        assertNull(structure.campaigns[1].dailyBudgetMicros)
        assertEquals(GoogleAdGroup("31", "21", "Filter 40L", "ENABLED"), structure.adGroups.single())
        assertEquals(GoogleAd("41", "31", "21", "https://www.amazon.com/dp/B0AAA11111", "REMOVED"), structure.ads[0])
        assertEquals("", structure.ads[1].finalUrl)
    }

    private companion object {
        val ACCOUNT = GoogleAdsAccount("1234567890", "AquaLife", loginCustomerId = "9998887776")
    }
}
