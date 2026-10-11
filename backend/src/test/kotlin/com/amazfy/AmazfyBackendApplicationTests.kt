package com.amazfy

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AmazfyBackendApplicationTests {
    @Autowired
    lateinit var flyway: Flyway

    @Test
    fun `app starts and Flyway applies all migrations`() {
        val info = flyway.info()
        assertNotNull(info.current(), "at least one migration applied")
        assertTrue(info.pending().isEmpty(), "no pending migrations")
    }
}
