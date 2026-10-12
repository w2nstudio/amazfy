package com.amazfy.auth.domain

import java.time.Duration

/**
 * "At most [max] attempts per [window]", counted separately per subject (an email, an IP address or a user).
 * Example: AttemptLimit("login-email", 5, 15 min) locks an email for up to 15 minutes after 5 failed logins.
 * The window slides: each attempt stops counting [window] after it happened.
 */
data class AttemptLimit(
    val name: String,
    val max: Int,
    val window: Duration,
) {
    init {
        require(max >= 1) { "max must be at least 1" }
        require(window > Duration.ZERO && window <= MAX_WINDOW) { "window must be between 0 and $MAX_WINDOW" }
    }

    companion object {
        /** Attempts older than this are deleted, so no limit can look further back. */
        val MAX_WINDOW: Duration = Duration.ofDays(1)
    }
}
