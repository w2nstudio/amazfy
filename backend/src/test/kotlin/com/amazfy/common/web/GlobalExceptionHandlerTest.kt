package com.amazfy.common.web

import com.amazfy.common.error.AppException
import com.amazfy.common.error.ErrorKind
import com.amazfy.common.security.JwtConfig
import com.amazfy.common.security.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(GlobalExceptionHandlerTest.FailingController::class)
@Import(GlobalExceptionHandler::class, SecurityConfig::class, JwtConfig::class, GlobalExceptionHandlerTest.FailingController::class)
@WithMockUser // the test endpoints are protected like every non-public endpoint
class GlobalExceptionHandlerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `unexpected error returns 500 problem without internal details`() {
        mockMvc
            .get("/test/boom")
            .andExpect {
                status { isInternalServerError() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.status") { value(500) }
                jsonPath("$.detail") { value("An unexpected error occurred.") }
            }
    }

    @Test
    fun `unknown path returns 404 problem`() {
        mockMvc
            .get("/test/does-not-exist")
            .andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.status") { value(404) }
            }
    }

    @Test
    fun `business error returns its status and safe message`() {
        mockMvc
            .get("/test/conflict")
            .andExpect {
                status { isConflict() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.detail") { value("This email is already registered.") }
            }
    }

    @RestController
    class FailingController {
        @GetMapping("/test/conflict")
        fun conflict(): String = throw AppException(ErrorKind.CONFLICT, "This email is already registered.")

        @GetMapping("/test/boom")
        fun boom(): String = error("secret internal message")
    }
}
