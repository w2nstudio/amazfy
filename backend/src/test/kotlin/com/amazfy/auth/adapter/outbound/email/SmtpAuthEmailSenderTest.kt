package com.amazfy.auth.adapter.outbound.email

import com.amazfy.auth.domain.Email
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Sends real emails to a throwaway Mailpit (same image as docker-compose.yml) and reads them back via its API. */
@Testcontainers
class SmtpAuthEmailSenderTest {
    companion object {
        @Container
        @JvmStatic
        val mailpit: GenericContainer<*> =
            GenericContainer<Nothing>(DockerImageName.parse("axllent/mailpit:v1.31")).also { it.addExposedPorts(1025, 8025) }
    }

    private val http = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    private val sender =
        SmtpAuthEmailSender(
            mailSender =
                JavaMailSenderImpl().also {
                    it.host = mailpit.host
                    it.port = mailpit.getMappedPort(1025)
                },
            executor = { it.run() }, // send right away, so the email is in Mailpit when the test looks
            from = "Amazfy <no-reply@amazfy.local>",
            frontendBaseUrl = "http://localhost:5173",
        )

    private fun mailpitApi(
        method: String,
        path: String,
    ): String {
        val uri = URI("http://${mailpit.host}:${mailpit.getMappedPort(8025)}/api/v1$path")
        val request = HttpRequest.newBuilder(uri).method(method, HttpRequest.BodyPublishers.noBody()).build()
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun latestEmail(): JsonNode = json.readTree(mailpitApi("GET", "/message/latest"))

    @BeforeEach
    fun deleteAllEmails() {
        mailpitApi("DELETE", "/messages")
    }

    @Test
    fun `verification email links to the frontend page with the token`() {
        sender.sendEmailVerification(Email.of("john@aqualife.com"), "abc-123_XYZ", Duration.ofHours(24))

        val email = latestEmail()
        assertEquals("Confirm your email address for Amazfy", email["Subject"].asString())
        assertEquals("john@aqualife.com", email["To"][0]["Address"].asString())
        assertEquals("no-reply@amazfy.local", email["From"]["Address"].asString())
        assertEquals("Amazfy", email["From"]["Name"].asString())
        assertContains(email["Text"].asString(), "http://localhost:5173/verify-email?token=abc-123_XYZ")
        assertContains(email["Text"].asString(), "expires in 24 hours")
    }

    @Test
    fun `password reset email links to the reset page`() {
        sender.sendPasswordReset(Email.of("john@aqualife.com"), "reset-token", Duration.ofMinutes(30))

        val email = latestEmail()
        assertEquals("Reset your Amazfy password", email["Subject"].asString())
        assertContains(email["Text"].asString(), "http://localhost:5173/reset-password?token=reset-token")
        assertContains(email["Text"].asString(), "expires in 30 minutes")
    }
}
