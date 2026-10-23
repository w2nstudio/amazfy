package com.amazfy.catalog.adapter.outbound.persistence

import com.amazfy.catalog.application.port.BrandRepository
import com.amazfy.catalog.domain.Brand
import com.amazfy.catalog.domain.BrandName
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.TenantId
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

// JPA class for the table in V6__brands.sql, used only inside this adapter.

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

interface BrandJpaRepository : JpaRepository<BrandEntity, UUID> {
    fun findAllByOrderByNameAsc(): List<BrandEntity>

    @Query("SELECT count(b) > 0 FROM BrandEntity b WHERE lower(b.name) = lower(:name) AND (:exceptId IS NULL OR b.id <> :exceptId)")
    fun nameTaken(
        name: String,
        exceptId: UUID?,
    ): Boolean
}

@Component
class BrandPersistenceAdapter(
    private val jpa: BrandJpaRepository,
) : BrandRepository {
    override fun findAll(): List<Brand> = jpa.findAllByOrderByNameAsc().map { it.toDomain() }

    override fun findById(id: UUID): Brand? = jpa.findById(id).orElse(null)?.toDomain()

    override fun nameTaken(
        name: BrandName,
        exceptId: UUID?,
    ): Boolean = jpa.nameTaken(name.value, exceptId)

    override fun create(name: BrandName): Brand = jpa.saveAndFlush(BrandEntity(name.value)).toDomain()

    override fun rename(
        id: UUID,
        name: BrandName,
    ): Brand {
        val entity = jpa.findById(id).orElseThrow()
        entity.name = name.value
        return jpa.saveAndFlush(entity).toDomain()
    }

    override fun delete(id: UUID) = jpa.deleteById(id)
}

private fun BrandEntity.toDomain() = Brand(id = id!!, name = BrandName.of(name), createdAt = createdAt!!)
