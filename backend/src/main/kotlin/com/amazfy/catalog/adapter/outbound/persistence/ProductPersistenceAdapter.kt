package com.amazfy.catalog.adapter.outbound.persistence

import com.amazfy.catalog.application.port.ProductRepository
import com.amazfy.catalog.domain.Asin
import com.amazfy.catalog.domain.Product
import com.amazfy.catalog.domain.ProductName
import com.amazfy.common.amazon.Marketplace
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.util.UUID

interface ProductJpaRepository : JpaRepository<ProductEntity, UUID> {
    fun findAllByOrderByNameAsc(): List<ProductEntity>

    fun findAllByBrandIdOrderByNameAsc(brandId: UUID): List<ProductEntity>

    fun existsByAsinAndMarketplace(
        asin: String,
        marketplace: String,
    ): Boolean

    /** Pairs of (brand id, product count); filtered to the current account like every query here. */
    @Query("SELECT p.brandId, count(p) FROM ProductEntity p GROUP BY p.brandId")
    fun countPerBrand(): List<Array<Any>>
}

@Component
class ProductPersistenceAdapter(
    private val jpa: ProductJpaRepository,
) : ProductRepository {
    override fun findAll(brandId: UUID?): List<Product> =
        (if (brandId == null) jpa.findAllByOrderByNameAsc() else jpa.findAllByBrandIdOrderByNameAsc(brandId)).map { it.toDomain() }

    override fun findById(id: UUID): Product? = jpa.findById(id).orElse(null)?.toDomain()

    override fun exists(
        asin: Asin,
        marketplace: Marketplace,
    ): Boolean = jpa.existsByAsinAndMarketplace(asin.value, marketplace.name)

    override fun create(
        brandId: UUID,
        asin: Asin,
        marketplace: Marketplace,
        name: ProductName,
    ): Product = jpa.saveAndFlush(ProductEntity(brandId, asin.value, marketplace.name, name.value)).toDomain()

    override fun update(
        id: UUID,
        brandId: UUID?,
        name: ProductName?,
    ): Product {
        val entity = jpa.findById(id).orElseThrow()
        brandId?.let { entity.brandId = it }
        name?.let { entity.name = it.value }
        return jpa.saveAndFlush(entity).toDomain()
    }

    override fun delete(id: UUID) = jpa.deleteById(id)

    override fun countByBrand(): Map<UUID, Int> =
        jpa.countPerBrand().associate { (brandId, count) ->
            brandId as UUID to
                (count as Long).toInt()
        }
}

private fun ProductEntity.toDomain() =
    Product(
        id = id!!,
        brandId = brandId,
        asin = Asin.of(asin),
        marketplace = Marketplace.valueOf(marketplace),
        name = ProductName.of(name),
        createdAt = createdAt!!,
    )
