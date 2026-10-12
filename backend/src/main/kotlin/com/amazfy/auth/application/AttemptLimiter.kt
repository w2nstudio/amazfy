package com.amazfy.auth.application

import com.amazfy.auth.application.port.AttemptRepository
import com.amazfy.auth.domain.AttemptLimit
import com.amazfy.auth.domain.SecretTokenCodec
import com.amazfy.common.error.TooManyAttemptsException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/**
 * Brute-force protection: counts attempts per limit and subject, and refuses once a limit is reached.
 *
 * Each method runs in its own transaction (REQUIRES_NEW), so a recorded attempt is kept even when the caller's
 * transaction is rolled back, e.g. because the password was wrong.
 * Two requests at the same instant can both pass the check; at most a few extra attempts, which is acceptable.
 */
@Component
class AttemptLimiter(
    private val attempts: AttemptRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Throws [TooManyAttemptsException] if [subject] already used up [limit]; says when the next attempt is allowed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun check(
        limit: AttemptLimit,
        subject: String,
    ) {
        val now = clock.instant()
        val nthLatest = attempts.nthLatestSince(key(limit, subject), limit.max, now.minus(limit.window)) ?: return
        throw TooManyAttemptsException(retryAfter = Duration.between(now, nthLatest.plus(limit.window)))
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        limit: AttemptLimit,
        subject: String,
    ) = attempts.record(key(limit, subject), clock.instant())

    /** Forgets the subject's attempts, e.g. failed logins after a successful one. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reset(
        limit: AttemptLimit,
        subject: String,
    ) = attempts.deleteAll(key(limit, subject))

    /** Hourly: attempts older than the longest possible window can't affect any limit any more. */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    fun deleteExpired() {
        val deleted = attempts.deleteOlderThan(clock.instant().minus(AttemptLimit.MAX_WINDOW))
        if (deleted > 0) log.debug("Deleted {} expired attempts", deleted)
    }

    /** Hashed, so stored keys don't reveal emails or IP addresses. Lower-cased: emails differ only in case. */
    private fun key(
        limit: AttemptLimit,
        subject: String,
    ) = SecretTokenCodec.hash("${limit.name}:${subject.lowercase()}")
}
