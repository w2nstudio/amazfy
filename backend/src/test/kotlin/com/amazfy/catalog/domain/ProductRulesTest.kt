package com.amazfy.catalog.domain

import com.amazfy.common.amazon.Marketplace
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductRulesTest {
    @Test
    fun `an ASIN is trimmed and upper-cased`() {
        assertEquals("B0AAA11111", Asin.of("  b0aaa11111 ").value)
    }

    @Test
    fun `an ASIN must be B0 plus 8 letters or digits`() {
        listOf("", "B0AAA1111", "B0AAA111111", "A0AAA11111", "B0AAA-1111", "0123456789").forEach {
            assertFailsWith<InvalidAsinException>("should reject '$it'") { Asin.of(it) }
        }
    }

    @Test
    fun `the Amazon link uses the marketplace's own site`() {
        fun product(marketplace: Marketplace) =
            Product(UUID.randomUUID(), UUID.randomUUID(), Asin.of("B0AAA11111"), marketplace, ProductName.of("Filter"), Instant.now())

        assertEquals("https://www.amazon.com/dp/B0AAA11111", product(Marketplace.US).amazonUrl)
        assertEquals("https://www.amazon.co.uk/dp/B0AAA11111", product(Marketplace.UK).amazonUrl)
        assertEquals("https://www.amazon.de/dp/B0AAA11111", product(Marketplace.DE).amazonUrl)
    }

    @Test
    fun `a product name is trimmed and 1 to 200 characters`() {
        assertEquals("Aquarium filter 40L", ProductName.of(" Aquarium filter 40L ").value)
        assertFailsWith<InvalidProductNameException> { ProductName.of("   ") }
        assertFailsWith<InvalidProductNameException> { ProductName.of("x".repeat(201)) }
    }
}
