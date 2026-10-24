package com.amazfy.catalog.application.port

import com.amazfy.catalog.domain.Asin
import com.amazfy.catalog.domain.Marketplace
import com.amazfy.catalog.domain.Product
import com.amazfy.catalog.domain.ProductName
import java.util.UUID

/** Storage for products. Like BrandRepository, always within CurrentAccount (@TenantId): no account parameter. */
interface ProductRepository {
    /** The current account's products (optionally of one brand), sorted by name. */
    fun findAll(brandId: UUID? = null): List<Product>

    fun findById(id: UUID): Product?

    fun exists(
        asin: Asin,
        marketplace: Marketplace,
    ): Boolean

    /** [brandId] must be a brand of the current account; the database refuses any other. */
    fun create(
        brandId: UUID,
        asin: Asin,
        marketplace: Marketplace,
        name: ProductName,
    ): Product

    /** Changes only what is given. ASIN and marketplace can't change. */
    fun update(
        id: UUID,
        brandId: UUID? = null,
        name: ProductName? = null,
    ): Product

    fun delete(id: UUID)

    /** How many products each brand of the current account has; brands without products are absent. */
    fun countByBrand(): Map<UUID, Int>
}
