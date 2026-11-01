package com.amazfy.channel.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GoogleAdsStructureTest {
    private val campaign = GoogleCampaign("21", "Filters", "ENABLED", 15_000_000, "USD")
    private val adGroup = GoogleAdGroup("31", "21", "Filter 40L", "ENABLED")
    private val ad = GoogleAd("41", "31", "21", "https://www.amazon.com/dp/B0AAA11111", "ENABLED")

    @Test
    fun `all three known statuses are kept`() {
        val structure =
            GoogleAdsStructure(
                listOf(campaign, campaign.copy(id = "22", status = "PAUSED"), campaign.copy(id = "23", status = "REMOVED")),
                listOf(adGroup),
                listOf(ad),
            )
        assertEquals(structure, structure.storable())
    }

    @Test
    fun `an unknown status leaves out the row and everything below it`() {
        val structure =
            GoogleAdsStructure(
                listOf(campaign.copy(status = "UNKNOWN")),
                listOf(adGroup),
                listOf(ad),
            ).storable()

        assertEquals(GoogleAdsStructure(emptyList(), emptyList(), emptyList()), structure)
    }

    @Test
    fun `an ad without its ad group is left out, as is an ad group without its campaign`() {
        val structure =
            GoogleAdsStructure(
                listOf(campaign),
                listOf(adGroup, adGroup.copy(id = "32", campaignId = "29")),
                listOf(ad, ad.copy(id = "42", adGroupId = "32", campaignId = "29"), ad.copy(id = "43", adGroupId = "39")),
            ).storable()

        assertEquals(listOf("31"), structure.adGroups.map { it.id })
        assertEquals(listOf("41"), structure.ads.map { it.id })
    }
}
