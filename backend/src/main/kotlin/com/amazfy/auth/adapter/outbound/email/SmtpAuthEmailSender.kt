package com.amazfy.auth.adapter.outbound.email

import com.amazfy.auth.application.port.AuthEmailSender
import com.amazfy.auth.domain.Email
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration

/**
 * Plain-text emails over SMTP (Mailpit locally, an email-sending service in production).
 * Links point to frontend pages, which call the API with the token.
 */
@Component
class SmtpAuthEmailSender(
    private val mailSender: JavaMailSender,
    @param:Value("\${amazfy.mail.from}") private val from: String,
    @param:Value("\${amazfy.mail.frontend-base-url}") private val frontendBaseUrl: String,
) : AuthEmailSender {
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
        mailSender.send(message)
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
