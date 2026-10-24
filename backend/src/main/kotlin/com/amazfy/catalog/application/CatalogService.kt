package com.amazfy.catalog.application

import com.amazfy.catalog.application.port.BrandRepository
import com.amazfy.catalog.domain.Brand
import com.amazfy.catalog.domain.BrandName
import com.amazfy.catalog.domain.BrandNotFoundException
import com.amazfy.catalog.domain.DuplicateBrandNameException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * The catalog module's API: brands (and products, next step) of the current account.
 * Light module: one service. No method takes an account: everything runs within CurrentAccount (common/tenant),
 * set from the request path; callers must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional(readOnly = true)
class CatalogService(
    private val brands: BrandRepository,
) {
    /** The account's brands, sorted by name. */
    fun listBrands(): List<BrandSummary> = brands.findAll().map { it.toSummary() }

    @Transactional
    fun createBrand(name: String): BrandSummary {
        val brandName = BrandName.of(name)
        if (brands.nameTaken(brandName)) throw DuplicateBrandNameException()
        return uniqueName { brands.create(brandName) }.toSummary()
    }

    /** Renaming to a different capitalisation of its own name is fine; another brand's name is not. */
    @Transactional
    fun renameBrand(
        id: UUID,
        name: String,
    ): BrandSummary {
        val brandName = BrandName.of(name)
        if (brands.findById(id) == null) throw BrandNotFoundException()
        if (brands.nameTaken(brandName, exceptId = id)) throw DuplicateBrandNameException()
        return uniqueName { brands.rename(id, brandName) }.toSummary()
    }

    /** Products arrive in the next step; then a brand that still has products can't be removed (409). */
    @Transactional
    fun deleteBrand(id: UUID) {
        if (brands.findById(id) == null) throw BrandNotFoundException()
        brands.delete(id)
    }

    /** Two requests adding the same name at once both pass the check; the database's unique index stops the second. */
    private fun <T> uniqueName(write: () -> T): T =
        try {
            write()
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateBrandNameException()
        }
}

/** A brand as the catalog module's API returns it. productCount stays 0 until products exist (next M2 step). */
data class BrandSummary(
    val id: UUID,
    val name: String,
    val productCount: Int,
    val createdAt: Instant,
)

private fun Brand.toSummary() = BrandSummary(id = id, name = name.value, productCount = 0, createdAt = createdAt)
