package com.amazfy.catalog.adapter.outbound.persistence

import com.amazfy.catalog.application.port.BrandRepository
import com.amazfy.catalog.domain.Brand
import com.amazfy.catalog.domain.BrandName
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.util.UUID

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
