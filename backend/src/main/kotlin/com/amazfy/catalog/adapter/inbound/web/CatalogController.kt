package com.amazfy.catalog.adapter.inbound.web

import com.amazfy.api.generated.BrandsApi
import com.amazfy.api.generated.ProductsApi
import com.amazfy.api.generated.model.Brand
import com.amazfy.api.generated.model.BrandRequest
import com.amazfy.api.generated.model.Product
import com.amazfy.api.generated.model.ProductCreateRequest
import com.amazfy.api.generated.model.ProductUpdateRequest
import com.amazfy.common.error.NotImplementedYetException
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Implements the Brands and Products parts of api/openapi.yaml. Any member may read; owners and editors may change.
 * The access rules are already in place; the endpoints themselves are built in the next M2 steps (501 until then).
 */
@RestController
class CatalogController :
    BrandsApi,
    ProductsApi {
    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun listBrands(accountId: UUID): ResponseEntity<List<Brand>> = throw NotImplementedYetException("Brands")

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun createBrand(
        accountId: UUID,
        brandRequest: BrandRequest,
    ): ResponseEntity<Brand> = throw NotImplementedYetException("Brands")

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun renameBrand(
        accountId: UUID,
        brandId: UUID,
        brandRequest: BrandRequest,
    ): ResponseEntity<Brand> = throw NotImplementedYetException("Brands")

    @PreAuthorize("@accountAccess.canEdit(#accountId)")
    override fun deleteBrand(
        accountId: UUID,
        brandId: UUID,
    ): ResponseEntity<Unit> = throw NotImplementedYetException("Brands")

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
