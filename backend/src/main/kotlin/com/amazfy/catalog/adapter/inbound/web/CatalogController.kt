package com.amazfy.catalog.adapter.inbound.web

import com.amazfy.api.generated.BrandsApi
import com.amazfy.api.generated.ProductsApi
import com.amazfy.api.generated.model.Brand
import com.amazfy.api.generated.model.BrandRequest
import com.amazfy.api.generated.model.Product
import com.amazfy.api.generated.model.ProductCreateRequest
import com.amazfy.api.generated.model.ProductUpdateRequest
import com.amazfy.catalog.application.BrandSummary
import com.amazfy.catalog.application.CatalogService
import com.amazfy.common.error.NotImplementedYetException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset
import java.util.UUID

/**
 * Implements the Brands and Products parts of api/openapi.yaml. Any member may read; owners and editors may change.
 * Data is filtered to the account in the path automatically (@TenantId, common/tenant). Products: next step (501).
 */
@RestController
class CatalogController(
    private val catalog: CatalogService,
) : BrandsApi,
    ProductsApi {
    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun listBrands(accountId: UUID): ResponseEntity<List<Brand>> = ResponseEntity.ok(catalog.listBrands().map { it.toApi() })

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun createBrand(
        accountId: UUID,
        brandRequest: BrandRequest,
    ): ResponseEntity<Brand> = ResponseEntity.status(HttpStatus.CREATED).body(catalog.createBrand(brandRequest.name).toApi())

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun renameBrand(
        accountId: UUID,
        brandId: UUID,
        brandRequest: BrandRequest,
    ): ResponseEntity<Brand> = ResponseEntity.ok(catalog.renameBrand(brandId, brandRequest.name).toApi())

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun deleteBrand(
        accountId: UUID,
        brandId: UUID,
    ): ResponseEntity<Unit> {
        catalog.deleteBrand(brandId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun listProducts(
        accountId: UUID,
        brandId: UUID?,
    ): ResponseEntity<List<Product>> = throw NotImplementedYetException("Products")

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun createProduct(
        accountId: UUID,
        productCreateRequest: ProductCreateRequest,
    ): ResponseEntity<Product> = throw NotImplementedYetException("Products")

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun updateProduct(
        accountId: UUID,
        productId: UUID,
        productUpdateRequest: ProductUpdateRequest,
    ): ResponseEntity<Product> = throw NotImplementedYetException("Products")

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun deleteProduct(
        accountId: UUID,
        productId: UUID,
    ): ResponseEntity<Unit> = throw NotImplementedYetException("Products")
}

private fun BrandSummary.toApi() = Brand(id = id, name = name, productCount = productCount, createdAt = createdAt.atOffset(ZoneOffset.UTC))
