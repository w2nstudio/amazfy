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
import com.amazfy.catalog.application.ProductSummary
import com.amazfy.catalog.domain.Marketplace
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.ZoneOffset
import java.util.UUID
import com.amazfy.api.generated.model.Marketplace as ApiMarketplace

/**
 * Implements the Brands and Products parts of api/openapi.yaml. Any member may read; owners and editors may change.
 * Data is filtered to the account in the path automatically (@TenantId, common/tenant).
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
    ): ResponseEntity<List<Product>> = ResponseEntity.ok(catalog.listProducts(brandId).map { it.toApi() })

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun createProduct(
        accountId: UUID,
        productCreateRequest: ProductCreateRequest,
    ): ResponseEntity<Product> {
        val created =
            with(productCreateRequest) {
                catalog.createProduct(brandId, asin, Marketplace.valueOf(marketplace.value), name)
            }
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toApi())
    }

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun updateProduct(
        accountId: UUID,
        productId: UUID,
        productUpdateRequest: ProductUpdateRequest,
    ): ResponseEntity<Product> =
        ResponseEntity.ok(catalog.updateProduct(productId, productUpdateRequest.brandId, productUpdateRequest.name).toApi())

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun deleteProduct(
        accountId: UUID,
        productId: UUID,
    ): ResponseEntity<Unit> {
        catalog.deleteProduct(productId)
        return ResponseEntity.noContent().build()
    }
}

private fun BrandSummary.toApi() = Brand(id = id, name = name, productCount = productCount, createdAt = createdAt.atOffset(ZoneOffset.UTC))

private fun ProductSummary.toApi() =
    Product(
        id = id,
        brandId = brandId,
        asin = asin,
        marketplace = ApiMarketplace.forValue(marketplace.name),
        name = name,
        amazonUrl = URI.create(amazonUrl),
        createdAt = createdAt.atOffset(ZoneOffset.UTC),
    )
