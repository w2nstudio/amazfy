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

// JPA class for the table in V6__brands.sql, used only inside BrandPersistenceAdapter.

@Entity
@Table(name = "brands")
class BrandEntity(
    var name: String,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    /**
     * The owning account. Hibernate fills it from CurrentAccount on insert, adds "account_id = <current>" to every
     * query, and refuses to change it afterwards. Never set it by hand.
     */
    @TenantId
    @Column(name = "account_id", updatable = false)
    var accountId: UUID? = null

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null

    @UpdateTimestamp
    val updatedAt: Instant? = null
}
