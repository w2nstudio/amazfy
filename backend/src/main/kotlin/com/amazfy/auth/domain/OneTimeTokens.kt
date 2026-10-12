package com.amazfy.auth.domain

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind

/** What an emailed one-time link is for. Matches the CHECK constraint in V4__one_time_tokens.sql. */
enum class OneTimeTokenPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
}

/** Unknown, expired, already used, or meant for something else: the user asks for a new link. */
class InvalidOneTimeTokenException :
    AppException(ErrorKind.BAD_REQUEST, "This link is invalid, has expired or was already used. Please request a new one.")
