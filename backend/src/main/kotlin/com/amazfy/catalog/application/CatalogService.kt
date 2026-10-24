package com.amazfy.catalog.application

import com.amazfy.account.application.AccountService
import com.amazfy.catalog.application.port.BrandRepository
import com.amazfy.catalog.application.port.ProductRepository
import com.amazfy.catalog.domain.Asin
import com.amazfy.catalog.domain.Brand
import com.amazfy.catalog.domain.BrandHasProductsException
import com.amazfy.catalog.domain.BrandName
import com.amazfy.catalog.domain.BrandNotFoundException
import com.amazfy.catalog.domain.DuplicateBrandNameException
import com.amazfy.catalog.domain.DuplicateProductException
import com.amazfy.catalog.domain.MarketplaceOutsideRegionException
import com.amazfy.catalog.domain.Product
import com.amazfy.catalog.domain.ProductName
import com.amazfy.catalog.domain.ProductNotFoundException
import com.amazfy.catalog.domain.UnknownBrandException
import com.amazfy.common.amazon.Marketplace
import com.amazfy.common.tenant.CurrentAccount
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * The catalog module's API: brands and products of the current account.
 * Light module: one service. No method takes an account: everything runs within CurrentAccount (common/tenant),
 * set from the request path; callers must have checked access first (@PreAuthorize with accountAccess).
 */
@Service
@Transactional(readOnly = true)
class CatalogService(
    private val brands: BrandRepository,
    private val products: ProductRepository,
    private val accounts: AccountService,
) {
    /** The account's brands, sorted by name, each with its number of products. */
    fun listBrands(): List<BrandSummary> {
        val counts = products.countByBrand()
        return brands.findAll().map { it.toSummary(counts[it.id] ?: 0) }
    }

    @Transactional
    fun createBrand(name: String): BrandSummary {
        val brandName = BrandName.of(name)
        if (brands.nameTaken(brandName)) throw DuplicateBrandNameException()
        return uniqueName { brands.create(brandName) }.toSummary(productCount = 0)
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
        return uniqueName { brands.rename(id, brandName) }.toSummary(products.countByBrand()[id] ?: 0)
    }

    /** A brand that still has products can't be removed (409), so no product is ever left without a brand. */
    @Transactional
    fun deleteBrand(id: UUID) {
        if (brands.findById(id) == null) throw BrandNotFoundException()
        if ((products.countByBrand()[id] ?: 0) > 0) throw BrandHasProductsException()
        // A product added at the same moment passes the check; the database's RESTRICT reference stops the delete
        try {
            brands.delete(id)
        } catch (e: DataIntegrityViolationException) {
            throw BrandHasProductsException()
        }
    }

    /** The account's products (optionally of one brand), sorted by brand name, then product name. */
    fun listProducts(brandId: UUID? = null): List<ProductSummary> {
        val brandNames = brands.findAll().associate { it.id to it.name.value }
        return products
            .findAll(brandId)
            .sortedWith(compareBy({ brandNames[it.brandId]?.lowercase() }, { it.name.value.lowercase() }))
            .map { it.toSummary() }
    }

    @Transactional
    fun createProduct(
        brandId: UUID,
        asin: String,
        marketplace: Marketplace,
        name: String,
    ): ProductSummary {
        val validAsin = Asin.of(asin)
        val productName = ProductName.of(name)
        if (brands.findById(brandId) == null) throw UnknownBrandException()
        val region = accounts.findAccount(CurrentAccount.get())!!.region
        if (marketplace.region != region) throw MarketplaceOutsideRegionException(region)
        if (products.exists(validAsin, marketplace)) throw DuplicateProductException()
        // Same race as brand names: the unique index on (account, ASIN, marketplace) stops the second request
        return try {
            products.create(brandId, validAsin, marketplace, productName).toSummary()
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateProductException()
        }
    }

    /** Renames and/or moves to another brand; only what is given changes. ASIN and marketplace never change. */
    @Transactional
    fun updateProduct(
        id: UUID,
        brandId: UUID?,
        name: String?,
    ): ProductSummary {
        val productName = name?.let { ProductName.of(it) }
        if (products.findById(id) == null) throw ProductNotFoundException()
        if (brandId != null && brands.findById(brandId) == null) throw UnknownBrandException()
        return products.update(id, brandId, productName).toSummary()
    }

    @Transactional
    fun deleteProduct(id: UUID) {
        if (products.findById(id) == null) throw ProductNotFoundException()
        products.delete(id)
    }

    /** Two requests adding the same name at once both pass the check; the database's unique index stops the second. */
    private fun <T> uniqueName(write: () -> T): T =
        try {
            write()
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateBrandNameException()
        }
}

/** A brand as the catalog module's API returns it. */
data class BrandSummary(
    val id: UUID,
    val name: String,
    val productCount: Int,
    val createdAt: Instant,
)

/** A product as the catalog module's API returns it. */
data class ProductSummary(
    val id: UUID,
    val brandId: UUID,
    val asin: String,
    val marketplace: Marketplace,
    val name: String,
    val amazonUrl: String,
    val createdAt: Instant,
)

private fun Brand.toSummary(productCount: Int) =
    BrandSummary(id = id, name = name.value, productCount = productCount, createdAt = createdAt)

private fun Product.toSummary() =
    ProductSummary(
        id = id,
        brandId = brandId,
        asin = asin.value,
        marketplace = marketplace,
        name = name.value,
        amazonUrl = amazonUrl,
        createdAt = createdAt,
    )
