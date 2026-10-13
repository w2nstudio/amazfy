package com.amazfy.auth.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind

/**
 * Who Google says signed in, taken from a verified ID token.
 * [subject] is Google's permanent id for the person ("sub"): it never changes, while their email can.
 * That's why logins are matched by subject (user_identities.subject), not by email.
 */
data class GoogleIdentity(
    val subject: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
)

/** Anything wrong during the round trip through Google (tampered state, invalid token, Google unreachable). */
class GoogleLoginFailedException(
    reason: String,
) : AppException(ErrorKind.UNAUTHORIZED, "Google sign-in failed: $reason")
