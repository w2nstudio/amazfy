package com.amazfy.channel.domain

import com.amazfy.common.amazon.Marketplace
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AdLinkTest {
    private val filterUs = AdLink.ProductPage("B0AAA11111", Marketplace.US)

    @Test
    fun `product pages give their ASIN and marketplace, however the link is written`() {
        listOf(
            "https://www.amazon.com/dp/B0AAA11111",
            "https://www.amazon.com/dp/B0AAA11111/",
            "https://amazon.com/dp/B0AAA11111?th=1&maas=maas_adg_api_123",
            "https://www.amazon.com/Aquarium-Filter-40L/dp/B0AAA11111/ref=sr_1_1",
            "https://www.amazon.com/gp/product/B0AAA11111",
            "https://www.amazon.com/gp/aw/d/B0AAA11111",
            "https://www.amazon.com/dp/b0aaa11111",
            "www.amazon.com/dp/B0AAA11111",
            "  https://www.amazon.com/dp/B0AAA11111  ",
        ).forEach { assertEquals(filterUs, AdLink.of(it), it) }

        assertEquals(AdLink.ProductPage("B0AAA11111", Marketplace.UK), AdLink.of("https://www.amazon.co.uk/dp/B0AAA11111"))
    }

    @Test
    fun `a Brand Store page has a marketplace but no product`() {
        assertEquals(AdLink.BrandStore(Marketplace.DE), AdLink.of("https://www.amazon.de/stores/AquaLife/page/1234"))
    }

    @Test
    fun `anything else can't be read`() {
        listOf(
            "https://amzn.to/123456", // short link
            "https://www.aqualife.com/dp/B0AAA11111", // not Amazon
            "https://www.amazon.co.jp/dp/B0AAA11111", // a marketplace Amazfy doesn't support
            "https://www.amazon.com/dp/B0AAA", // too short for an ASIN
            "https://www.amazon.com/s?k=aquarium+filter", // a search page
            "not a url at all",
            "",
        ).forEach { assertEquals(AdLink.Other, AdLink.of(it), it) }
    }
}
