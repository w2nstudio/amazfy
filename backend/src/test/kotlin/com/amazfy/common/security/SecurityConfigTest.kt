package com.amazfy.common.security

import com.amazfy.common.security.JwtConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(SecurityConfigTest.ProtectedController::class)
@Import(SecurityConfig::class, JwtConfig::class, SecurityConfigTest.ProtectedController::class)
class SecurityConfigTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `protected endpoint without credentials returns 401 problem`() {
        mockMvc
            .get("/api/v1/protected")
            .andExpect {
                status { isUnauthorized() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.status") { value(401) }
            }
    }

    @Test
    fun `CORS preflight from the frontend origin is allowed`() {
        mockMvc
            .options("/api/v1/protected") {
                header(HttpHeaders.ORIGIN, "http://localhost:5173")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            }.andExpect {
                status { isOk() }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173") }
            }
    }

    @Test
    fun `CORS preflight from an unknown origin is rejected`() {
        mockMvc
            .options("/api/v1/protected") {
                header(HttpHeaders.ORIGIN, "https://evil.example")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            }.andExpect {
                status { isForbidden() }
            }
    }

    @RestController
    class ProtectedController {
        @GetMapping("/api/v1/protected")
        fun protectedEndpoint(): String = "secret"
    }
}
