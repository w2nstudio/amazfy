package com.amazfy.channel.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GoogleAdsAccountTest {
    private fun client(
        id: String,
        name: String? = "Account $id",
        manager: Boolean = false,
        status: String = "ENABLED",
        test: Boolean = false,
        level: Int = 1,
    ) = GoogleAdsClient(id, name, manager, status, test, level)

    private fun ids(hierarchies: Map<String, List<GoogleAdsClient>>) = usableGoogleAdsAccounts(hierarchies).map { it.customerId }

    @Test
    fun `manager accounts aren't offered, since they hold no campaigns`() {
        val hierarchy = listOf(client("1", manager = true, level = 0), client("2"))
        assertEquals(listOf("2"), ids(mapOf("1" to hierarchy)))
    }

    @Test
    fun `only accounts that can run are offered`() {
        val hierarchy =
            listOf("ENABLED", "CANCELED", "CLOSED", "SUSPENDED", "UNKNOWN").mapIndexed { i, status -> client("$i", status = status) }
        assertEquals(listOf("0"), ids(mapOf("9" to hierarchy)))
    }

    @Test
    fun `a test account is offered although Google shows it as canceled`() {
        assertEquals(listOf("2"), ids(mapOf("1" to listOf(client("2", status = "CANCELED", test = true)))))
    }

    @Test
    fun `an account remembers what it was reached through`() {
        val account = usableGoogleAdsAccounts(mapOf("1" to listOf(client("2")))).single()
        assertEquals("1", account.loginCustomerId)
    }

    @Test
    fun `direct access wins over access through a manager`() {
        val hierarchies =
            mapOf(
                "1" to listOf(client("1", manager = true, level = 0), client("2", level = 1)),
                "2" to listOf(client("2", level = 0)),
            )
        assertEquals("2", usableGoogleAdsAccounts(hierarchies).single().loginCustomerId)
    }

    @Test
    fun `accounts come sorted by name, those without one last`() {
        val hierarchy = listOf(client("1", name = null), client("2", name = "zebra"), client("3", name = "AquaLife"))
        assertEquals(listOf("3", "2", "1"), ids(mapOf("9" to hierarchy)))
    }
}
