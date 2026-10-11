package com.amazfy.common.error

/** The kind of business error; the web layer maps it to an HTTP status (common/web/GlobalExceptionHandler). */
enum class ErrorKind {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    TOO_MANY_REQUESTS,
}

/**
 * Base class for expected business errors (invalid input, duplicates, wrong credentials, ...).
 * Pure Kotlin, so domain code can throw it. The message is shown to API clients: keep it safe and user-friendly.
 */
open class AppException(
    val kind: ErrorKind,
    message: String,
) : RuntimeException(message)
