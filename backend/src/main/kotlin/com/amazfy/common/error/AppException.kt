package com.amazfy.common.error

/** The kind of business error; the web layer maps it to an HTTP status (common/web/GlobalExceptionHandler). */
enum class ErrorKind {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    TOO_MANY_REQUESTS,
    NOT_IMPLEMENTED,
}

/**
 * Base class for expected business errors (invalid input, duplicates, wrong credentials, ...).
 * Pure Kotlin, so domain code can throw it. The message is shown to API clients: keep it safe and user-friendly.
 */
open class AppException(
    val kind: ErrorKind,
    message: String,
) : RuntimeException(message)

/** For endpoints that exist in the contract but are built in a later step. */
class NotImplementedYetException(
    feature: String,
) : AppException(ErrorKind.NOT_IMPLEMENTED, "$feature is not available yet.")
