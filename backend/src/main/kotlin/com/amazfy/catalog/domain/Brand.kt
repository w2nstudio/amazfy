package com.amazfy.catalog.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.time.Instant
import java.util.UUID

/** A seller's brand (registered in Amazon Brand Registry), e.g. "AquaLife". Belongs to one account; has products. */
data class Brand(
    val id: UUID,
    val name: BrandName,
    val createdAt: Instant,
)

/** A brand name: trimmed, 1–100 characters. Unique within the account, ignoring upper/lower case. */
@JvmInline
value class BrandName private constructor(
    val value: String,
) {
    companion object {
        const val MAX_LENGTH = 100

        fun of(raw: String): BrandName {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) throw InvalidBrandNameException()
            return BrandName(trimmed)
        }
    }
}

class InvalidBrandNameException : AppException(ErrorKind.BAD_REQUEST, "Brand name must be 1–${BrandName.MAX_LENGTH} characters.")

class DuplicateBrandNameException : AppException(ErrorKind.CONFLICT, "You already have a brand with this name.")

/** Also for brands of other accounts: they simply don't exist from this account's point of view. */
class BrandNotFoundException : AppException(ErrorKind.NOT_FOUND, "This brand doesn't exist.")

/** No product is ever left without a brand: the seller moves or removes the products first. */
class BrandHasProductsException :
    AppException(ErrorKind.CONFLICT, "This brand still has products. Move them to another brand or remove them first.")
