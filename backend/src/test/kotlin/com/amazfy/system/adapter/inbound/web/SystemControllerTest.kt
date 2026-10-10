package com.amazfy.system.adapter.inbound.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Properties

@WebMvcTest(SystemController::class)
@Import(SystemControllerTest.FixedBuildInfo::class)
class SystemControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET system info returns name and version`() {
        mockMvc
            .get("/api/v1/system/info")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("amazfy-backend") }
                jsonPath("$.version") { value("1.2.3") }
            }
    }

    @TestConfiguration
    class FixedBuildInfo {
        @Bean
        fun buildProperties() =
            BuildProperties(
                Properties().apply {
                    setProperty("name", "amazfy-backend")
                    setProperty("version", "1.2.3")
                },
            )
    }
}
