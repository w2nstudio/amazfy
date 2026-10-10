package com.amazfy

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AmazfyBackendApplicationTests {
	@Autowired
	lateinit var flyway: Flyway

	@Test
	fun `app starts and Flyway applies the migrations`() {
		assertEquals("1", flyway.info().current()?.version?.version)
	}
}
