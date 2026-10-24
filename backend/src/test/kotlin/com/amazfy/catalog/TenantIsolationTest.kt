package com.amazfy.catalog

import com.amazfy.TestcontainersConfiguration
import com.amazfy.catalog.application.port.BrandRepository
import com.amazfy.catalog.application.port.ProductRepository
import com.amazfy.catalog.domain.Asin
import com.amazfy.catalog.domain.BrandName
import com.amazfy.catalog.domain.ProductName
import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.tenant.CurrentAccount
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves one account can never reach another account's data through @TenantId (requirements section 3, "Data
 * isolation"). Deliberately not @Transactional: Hibernate picks the account when a transaction starts, so each
 * repository call runs in its own transaction inside CurrentAccount.runAs, like a real request.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class TenantIsolationTest {
    @Autowired
    lateinit var brands: BrandRepository

    @Autowired
    lateinit var products: ProductRepository

    @Autowired
    lateinit var jdbc: JdbcClient

    private fun newAccount(name: String): UUID =
        jdbc
            .sql("INSERT INTO accounts (name, region) VALUES (:name, 'NA') RETURNING id")
            .param("name", name)
            .query(UUID::class.java)
            .single()

    private fun brandCount(accountId: UUID): Int =
        jdbc
            .sql("SELECT count(*) FROM brands WHERE account_id = :a")
            .param("a", accountId)
            .query(Int::class.java)
            .single()

    private val aquaLife by lazy { newAccount("AquaLife Inc. – North America") }
    private val otherSeller by lazy { newAccount("Other Seller Ltd.") }

    @Test
    fun `a new brand belongs to the current account without setting it`() {
        val brand = CurrentAccount.runAs(aquaLife) { brands.create(BrandName.of("AquaLife")) }

        val owner =
            jdbc
                .sql("SELECT account_id FROM brands WHERE id = :id")
                .param("id", brand.id)
                .query(UUID::class.java)
                .single()
        assertEquals(aquaLife, owner)
    }

    @Test
    fun `another account can't list or read the brand`() {
        val brand = CurrentAccount.runAs(aquaLife) { brands.create(BrandName.of("AquaLife")) }

        CurrentAccount.runAs(otherSeller) {
            assertTrue(brands.findAll().isEmpty())
            assertNull(brands.findById(brand.id))
        }
        CurrentAccount.runAs(aquaLife) { assertEquals(listOf("AquaLife"), brands.findAll().map { it.name.value }) }
    }

    @Test
    fun `another account can't rename or delete the brand`() {
        val brand = CurrentAccount.runAs(aquaLife) { brands.create(BrandName.of("AquaLife")) }

        CurrentAccount.runAs(otherSeller) {
            assertFailsWith<NoSuchElementException> { brands.rename(brand.id, BrandName.of("Hijacked")) }
            brands.delete(brand.id) // finds nothing to delete
        }

        assertEquals(1, brandCount(aquaLife))
        CurrentAccount.runAs(aquaLife) { assertEquals("AquaLife", brands.findById(brand.id)!!.name.value) }
    }

    @Test
    fun `without a current account nothing is visible`() {
        CurrentAccount.runAs(aquaLife) { brands.create(BrandName.of("AquaLife")) }

        assertEquals(CurrentAccount.NONE, CurrentAccount.get())
        assertTrue(brands.findAll().isEmpty())
    }

    @Test
    fun `brand names are unique per account, ignoring case, but two accounts may use the same name`() {
        CurrentAccount.runAs(aquaLife) { brands.create(BrandName.of("AquaLife")) }

        CurrentAccount.runAs(otherSeller) {
            assertFalse(brands.nameTaken(BrandName.of("AquaLife")))
            brands.create(BrandName.of("AquaLife")) // allowed: a different seller
        }
        CurrentAccount.runAs(aquaLife) {
            assertTrue(brands.nameTaken(BrandName.of("AQUALIFE")))
            // The database refuses it too, even if the check above were skipped
            assertFailsWith<DataIntegrityViolationException> { brands.create(BrandName.of("aqualife")) }
        }
    }

    // ---------- Products ----------

    private val filter = Asin.of("B0AAA11111")

    /** A brand and one product of it (B0AAA11111 on amazon.com) in [accountId]. */
    private fun brandWithProduct(accountId: UUID) =
        CurrentAccount.runAs(accountId) {
            val brand = brands.create(BrandName.of("AquaLife"))
            brand to products.create(brand.id, filter, Marketplace.US, ProductName.of("Aquarium filter 40L"))
        }

    @Test
    fun `another account can't list, read, change or delete a product`() {
        val (_, product) = brandWithProduct(aquaLife)
        val otherBrand = CurrentAccount.runAs(otherSeller) { brands.create(BrandName.of("Other")) }

        CurrentAccount.runAs(otherSeller) {
            assertTrue(products.findAll().isEmpty())
            assertNull(products.findById(product.id))
            assertTrue(products.countByBrand().isEmpty())
            assertFailsWith<NoSuchElementException> { products.update(product.id, brandId = otherBrand.id) }
            products.delete(product.id) // finds nothing to delete
        }
        CurrentAccount.runAs(aquaLife) { assertEquals(product, products.findById(product.id)) }
    }

    @Test
    fun `a product can't be put under another account's brand, even by mistake`() {
        val othersBrand = CurrentAccount.runAs(otherSeller) { brands.create(BrandName.of("Other")) }

        CurrentAccount.runAs(aquaLife) {
            // The database checks the brand belongs to the same account (composite foreign key)
            assertFailsWith<DataIntegrityViolationException> {
                products.create(othersBrand.id, filter, Marketplace.US, ProductName.of("Sneaky"))
            }
        }
    }

    @Test
    fun `an ASIN is unique per marketplace within an account, not across accounts`() {
        val (brand, _) = brandWithProduct(aquaLife)
        brandWithProduct(otherSeller) // the same ASIN for another seller is fine

        CurrentAccount.runAs(aquaLife) {
            assertTrue(products.exists(filter, Marketplace.US))
            assertFalse(products.exists(filter, Marketplace.UK))
            products.create(brand.id, filter, Marketplace.UK, ProductName.of("Aquarium filter 40L (UK)")) // another marketplace
            assertFailsWith<DataIntegrityViolationException> {
                products.create(brand.id, filter, Marketplace.US, ProductName.of("Twice"))
            }
        }
    }

    @Test
    fun `products are counted per brand of the current account`() {
        val (brand, _) = brandWithProduct(aquaLife)
        brandWithProduct(otherSeller)

        CurrentAccount.runAs(aquaLife) {
            products.create(brand.id, Asin.of("B0BBB22222"), Marketplace.US, ProductName.of("Aquarium heater"))
            assertEquals(mapOf(brand.id to 2), products.countByBrand())
        }
    }

    @Test
    fun `the database refuses to delete a brand that still has products`() {
        val (brand, _) = brandWithProduct(aquaLife)

        CurrentAccount.runAs(aquaLife) {
            assertFailsWith<DataIntegrityViolationException> {
                brands.delete(brand.id)
                brands.findAll() // the delete reaches the database at the latest when the next query flushes it
            }
        }
    }
}
