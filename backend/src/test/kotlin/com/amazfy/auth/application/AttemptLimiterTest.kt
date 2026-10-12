package com.amazfy.auth.application

import com.amazfy.TestcontainersConfiguration
import com.amazfy.auth.application.port.AttemptRepository
import com.amazfy.auth.domain.AttemptLimit
import com.amazfy.common.error.TooManyAttemptsException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The limiter against a real PostgreSQL, with a clock the test moves forward. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AttemptLimiterTest {
    @Autowired
    lateinit var attempts: AttemptRepository

    @Autowired
    lateinit var jdbc: JdbcClient

    /** A clock that stands still until the test moves it. */
    private class TestClock(
        var now: Instant,
    ) : Clock() {
        override fun instant() = now

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId) = this
    }

    private val clock = TestClock(Instant.now().truncatedTo(ChronoUnit.MILLIS))
    private val limiter by lazy { AttemptLimiter(attempts, clock) }
    private val limit = AttemptLimit("test-login", max = 3, window = Duration.ofMinutes(15))

    private fun uniqueSubject() = "user-${UUID.randomUUID()}@aqualife.com"

    private fun fail(subject: String) {
        limiter.check(limit, subject)
        limiter.record(limit, subject)
    }

    @Test
    fun `allows up to max attempts, then refuses and says when to retry`() {
        val subject = uniqueSubject()
        repeat(3) {
            fail(subject)
            clock.now = clock.now.plus(Duration.ofMinutes(1))
        }

        val refused = assertFailsWith<TooManyAttemptsException> { limiter.check(limit, subject) }
        // The first attempt was 3 minutes ago; it stops counting after 15, so 12 minutes to wait
        assertEquals(Duration.ofMinutes(12), refused.retryAfter)
        assertEquals("Too many attempts. Please try again in 12 minutes.", refused.message)
    }

    @Test
    fun `attempts stop counting once the window has passed`() {
        val subject = uniqueSubject()
        repeat(3) { fail(subject) }
        assertFailsWith<TooManyAttemptsException> { limiter.check(limit, subject) }

        clock.now = clock.now.plus(Duration.ofMinutes(15))

        limiter.check(limit, subject) // allowed again
    }

    @Test
    fun `subjects and limits are counted separately, emails ignoring case`() {
        val subject = uniqueSubject()
        repeat(3) { fail(subject) }

        assertFailsWith<TooManyAttemptsException> { limiter.check(limit, subject.uppercase()) }
        limiter.check(limit, uniqueSubject())
        limiter.check(limit.copy(name = "test-other"), subject)
    }

    @Test
    fun `reset forgets the attempts`() {
        val subject = uniqueSubject()
        repeat(3) { fail(subject) }

        limiter.reset(limit, subject)

        limiter.check(limit, subject)
    }

    @Test
    fun `clean-up deletes attempts older than a day and keeps newer ones`() {
        val old = clock.now.minus(Duration.ofDays(1)).minusSeconds(1)
        val recent = clock.now.minus(Duration.ofHours(23))
        attempts.record("cleanup-test", old)
        attempts.record("cleanup-test", recent)

        limiter.deleteExpired()

        val left =
            jdbc
                .sql("SELECT attempted_at FROM auth_attempts WHERE key_hash = 'cleanup-test'")
                .query(Timestamp::class.java)
                .list()
                .map { it!!.toInstant() }
        assertEquals(listOf(recent), left)
    }

    @Test
    fun `a limit can't look back further than the clean-up keeps`() {
        assertFailsWith<IllegalArgumentException> { AttemptLimit("too-long", 3, Duration.ofDays(2)) }
    }
}
