package com.amazfy.common.web

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import com.amazfy.common.error.TooManyAttemptsException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Turns every error into an RFC 7807 Problem Details response (application/problem+json).
 *
 * The base class already handles Spring's own errors (404 unknown path, 400 invalid input,
 * 405 wrong method, ...). The handler below catches everything else, so unexpected errors
 * never leak stack traces or internal messages to clients.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Invalid request body (bean validation from the contract): 400 with one entry per field in `errors`. */
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem =
            ex.body.apply {
                detail = "Some fields are invalid."
                setProperty(
                    "errors",
                    ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "message" to (it.defaultMessage ?: "is invalid")) },
                )
            }
        return handleExceptionInternal(ex, problem, headers, status, request)
    }

    /** Expected business errors: status from their kind, their (safe) message as detail. */
    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException): ProblemDetail = ProblemDetail.forStatusAndDetail(ex.kind.toHttpStatus(), ex.message)

    /** 429 plus Retry-After (whole seconds, rounded up), so clients know when to try again. */
    @ExceptionHandler(TooManyAttemptsException::class)
    fun handleTooManyAttempts(ex: TooManyAttemptsException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, maxOf(1, ex.retryAfter.toMillis().plus(999) / 1000).toString())
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message))

    /** A @PreAuthorize rule said no (e.g. not a member of the account): 403, without saying which rule. */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You don't have access to this.")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unexpected error", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.")
    }
}

private fun ErrorKind.toHttpStatus(): HttpStatus =
    when (this) {
        ErrorKind.BAD_REQUEST -> HttpStatus.BAD_REQUEST
        ErrorKind.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        ErrorKind.FORBIDDEN -> HttpStatus.FORBIDDEN
        ErrorKind.NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorKind.CONFLICT -> HttpStatus.CONFLICT
        ErrorKind.TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS
        ErrorKind.NOT_IMPLEMENTED -> HttpStatus.NOT_IMPLEMENTED
    }
