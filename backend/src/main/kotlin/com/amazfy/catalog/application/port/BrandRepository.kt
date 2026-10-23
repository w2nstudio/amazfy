package com.amazfy.catalog.application.port

import com.amazfy.catalog.domain.Brand
import com.amazfy.catalog.domain.BrandName
import java.util.UUID

/**
 * Storage for brands. Always works within CurrentAccount: there is no account parameter, because Hibernate's
 * @TenantId filters every read and fills every insert (common/tenant). Brands of other accounts are invisible.
 */
interface BrandRepository {
    /** The current account's brands, sorted by name. */
    fun findAll(): List<Brand>

    fun findById(id: UUID): Brand?

    /** Whether another brand of the current account already has this name (ignoring case). */
    fun nameTaken(
        name: BrandName,
        exceptId: UUID? = null,
    ): Boolean

    fun create(name: BrandName): Brand

    fun rename(
        id: UUID,
        name: BrandName,
    ): Brand

    fun delete(id: UUID)
}
