package com.amazfy.catalog.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import java.time.Instant
import java.util.UUID

/**
 * One Amazon listing the seller advertises: ASIN + marketplace, under one brand. Example: B0AAA11111 on amazon.com,
 * "Aquarium filter 40L", brand AquaLife. ASIN and marketplace identify the listing and never change after creation.
 */
data class Product(
    val id: UUID,
    val brandId: UUID,
    val asin: Asin,
    val marketplace: Marketplace,
    val name: ProductName,
    val createdAt: Instant,
) {
    /** The listing on Amazon, e.g. https://www.amazon.com/dp/B0AAA11111 */
    val amazonUrl: String get() = "https://www.${marketplace.amazonDomain}/dp/${asin.value}"
}

/**
 * Amazon's 10-character product id ("Amazon Standard Identification Number"), e.g. B0AAA11111, as seen in a listing's
 * address (amazon.com/dp/B0AAA11111). Spaces are trimmed and letters upper-cased, then it must be "B0" + 8 letters or
 * digits. (Books use their ISBN as ASIN, which this rule doesn't accept; not expected for Phase 1 sellers.)
 */
@JvmInline
value class Asin private constructor(
    val value: String,
) {
    companion object {
        private val shape = Regex("^B0[0-9A-Z]{8}$")

        fun of(raw: String): Asin {
            val normalized = raw.trim().uppercase()
            if (!shape.matches(normalized)) throw InvalidAsinException()
            return Asin(normalized)
        }
    }
}

/**
 * An Amazon country site. Phase 1: the marketplaces where Amazon Attribution is available.
 * The code is Amazon's own (UK, not the ISO code GB). Sales are in the marketplace's currency and never summed across.
 */
enum class Marketplace(
    val amazonDomain: String,
    val currency: String,
) {
    US("amazon.com", "USD"),
    CA("amazon.ca", "CAD"),
    UK("amazon.co.uk", "GBP"),
    DE("amazon.de", "EUR"),
    FR("amazon.fr", "EUR"),
    IT("amazon.it", "EUR"),
    ES("amazon.es", "EUR"),
}

/** A short product name the seller recognises: trimmed, 1–200 characters. */
@JvmInline
value class ProductName private constructor(
    val value: String,
) {
    companion object {
        const val MAX_LENGTH = 200

        fun of(raw: String): ProductName {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) throw InvalidProductNameException()
            return ProductName(trimmed)
        }
    }
}

class InvalidAsinException : AppException(ErrorKind.BAD_REQUEST, "An ASIN has 10 characters and starts with B0, e.g. B0AAA11111.")

class InvalidProductNameException : AppException(ErrorKind.BAD_REQUEST, "Product name must be 1–${ProductName.MAX_LENGTH} characters.")

class DuplicateProductException : AppException(ErrorKind.CONFLICT, "This ASIN is already in your catalog for this marketplace.")

/** Also for products of other accounts: they don't exist from this account's point of view. */
class ProductNotFoundException : AppException(ErrorKind.NOT_FOUND, "This product doesn't exist.")

/** The brand sent with a product isn't one of this account's brands (400: the request is wrong, not the address). */
class UnknownBrandException : AppException(ErrorKind.BAD_REQUEST, "Choose one of your brands.")
