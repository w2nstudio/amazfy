package com.amazfy

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts a throwaway PostgreSQL container for tests. @ServiceConnection points the
 * app's datasource at it automatically. Same image as docker-compose.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))

    /** Tests never send real emails; they read what would have been sent from this bean. */
    @Bean
    @Primary
    fun recordingAuthEmailSender(): RecordingAuthEmailSender = RecordingAuthEmailSender()
}
