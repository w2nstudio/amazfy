package com.amazfy

import com.amazfy.auth.application.port.AuthEmailSender
import com.amazfy.auth.domain.Email
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Replaces real email sending in Spring tests (see TestcontainersConfiguration). Tests read the links' tokens from here.
 * SMTP itself is tested in SmtpAuthEmailSenderTest.
 */
class RecordingAuthEmailSender : AuthEmailSender {
    data class SentEmail(
        val to: String,
        val kind: Kind,
        val token: String,
    )

    enum class Kind { VERIFICATION, PASSWORD_RESET }

    private val sent = CopyOnWriteArrayList<SentEmail>()

    override fun sendEmailVerification(
        to: Email,
        token: String,
        validFor: Duration,
    ) {
        sent += SentEmail(to.value, Kind.VERIFICATION, token)
    }

    override fun sendPasswordReset(
        to: Email,
        token: String,
        validFor: Duration,
    ) {
        sent += SentEmail(to.value, Kind.PASSWORD_RESET, token)
    }

    fun sentTo(
        email: String,
        kind: Kind,
    ): List<SentEmail> = sent.filter { it.to == email && it.kind == kind }
}
