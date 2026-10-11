package com.amazfy.common.web

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
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

    /** Expected business errors: status from their kind, their (safe) message as detail. */
    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException): ProblemDetail = ProblemDetail.forStatusAndDetail(ex.kind.toHttpStatus(), ex.message)

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
    }
