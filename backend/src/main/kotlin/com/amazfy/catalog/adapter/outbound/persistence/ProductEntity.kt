package com.amazfy.catalog.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.TenantId
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

// JPA class for the table in V7__products.sql, used only inside ProductPersistenceAdapter.

@Entity
@Table(name = "products")
class ProductEntity(
    var brandId: UUID,
    @Column(updatable = false)
    val asin: String,
    @Column(updatable = false)
    val marketplace: String,
    var name: String,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    /** The owning account, filled and filtered by Hibernate from CurrentAccount (see BrandEntity). Never set it by hand. */
    @TenantId
    @Column(name = "account_id", updatable = false)
    var accountId: UUID? = null

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null

    @UpdateTimestamp
    val updatedAt: Instant? = null
}
