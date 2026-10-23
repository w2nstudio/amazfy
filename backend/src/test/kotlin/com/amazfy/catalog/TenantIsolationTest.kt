package com.amazfy.catalog

import com.amazfy.TestcontainersConfiguration
import com.amazfy.catalog.application.port.BrandRepository
import com.amazfy.catalog.domain.BrandName
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
    lateinit var jdbc: JdbcClient

    private fun newAccount(name: String): UUID =
        jdbc
            .sql("INSERT INTO accounts (name) VALUES (:name) RETURNING id")
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
}
