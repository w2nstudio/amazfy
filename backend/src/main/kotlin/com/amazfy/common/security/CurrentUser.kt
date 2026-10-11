package com.amazfy.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID

/**
 * The logged-in user's id, taken from the access token that Spring Security already checked.
 * Only call this from endpoints that require authentication.
 */
fun currentUserId(): UUID {
    val authentication =
        SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: error("No authenticated user: currentUserId() called from a public endpoint?")
    return UUID.fromString(authentication.token.subject)
}
