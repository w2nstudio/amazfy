package com.amazfy.auth.adapter.outbound.email

import com.amazfy.auth.application.port.AuthEmailSender
import com.amazfy.auth.domain.Email
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.util.concurrent.Executor

/**
 * Plain-text emails over SMTP (Mailpit locally, an email-sending service in production).
 * Links point to frontend pages, which call the API with the token.
 * Sending happens in the background: the request doesn't wait for the email server, so response times don't reveal
 * whether an email was sent (e.g. for which addresses "forgot password" found a user).
 */
@Component
class SmtpAuthEmailSender(
    private val mailSender: JavaMailSender,
    @param:Qualifier("applicationTaskExecutor") private val executor: Executor,
    @param:Value("\${amazfy.mail.from}") private val from: String,
    @param:Value("\${amazfy.mail.frontend-base-url}") private val frontendBaseUrl: String,
) : AuthEmailSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendEmailVerification(
        to: Email,
        token: String,
        validFor: Duration,
    ) = send(
        to = to,
        subject = "Confirm your email address for Amazfy",
        text =
            """
            Welcome to Amazfy!

            Please confirm your email address by opening this link:
            ${link("/verify-email", token)}

            The link works once and expires in ${describe(validFor)}.
            If you didn't sign up for Amazfy, you can ignore this email.
            """.trimIndent(),
    )

    override fun sendPasswordReset(
        to: Email,
        token: String,
        validFor: Duration,
    ) = send(
        to = to,
        subject = "Reset your Amazfy password",
        text =
            """
            Someone asked to reset the password for your Amazfy login.

            To choose a new password, open this link:
            ${link("/reset-password", token)}

            The link works once and expires in ${describe(validFor)}.
            If you didn't ask for this, you can ignore this email; your password stays the same.
            """.trimIndent(),
    )

    private fun send(
        to: Email,
        subject: String,
        text: String,
    ) {
        val message = SimpleMailMessage()
        message.from = from
        message.setTo(to.value)
        message.subject = subject
        message.text = text
        executor.execute {
            // Logged here: the request has already been answered. The address stays out of the logs (personal data).
            runCatching { mailSender.send(message) }.onFailure { log.warn("Email \"{}\" could not be sent", subject, it) }
        }
    }

    private fun link(
        path: String,
        token: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(frontendBaseUrl)
            .path(path)
            .queryParam("token", token)
            .encode()
            .build()
            .toUriString()

    /** "24 hours", "1 hour" or "30 minutes". */
    private fun describe(duration: Duration): String {
        fun plural(
            n: Long,
            unit: String,
        ) = if (n == 1L) "1 $unit" else "$n ${unit}s"
        return if (duration.toMinutes() % 60 == 0L) plural(duration.toHours(), "hour") else plural(duration.toMinutes(), "minute")
    }
}
