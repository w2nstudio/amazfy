package com.amazfy.common.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Every endpoint requires a valid access token unless listed in PUBLIC_ENDPOINTS.
 * Keep PUBLIC_ENDPOINTS in sync with the operations marked `security: []` in api/openapi.yaml.
 */
@Configuration
class SecurityConfig(
    @param:Value("\${amazfy.cors.allowed-origins}") private val allowedOrigins: List<String>,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                it
                    .requestMatchers(*PUBLIC_ENDPOINTS)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }
            // Stateless API: no server sessions; the client sends a token on every request (M1)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // CSRF protects cookie-based sessions; a token-based API has none
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            // Every other request needs a valid access token: "Authorization: Bearer <JWT>", checked by JwtConfig's decoder
            .oauth2ResourceServer {
                it.jwt(Customizer.withDefaults()).authenticationEntryPoint {
                    _,
                    response,
                    _,
                    ->
                    writeUnauthorized(response)
                }
            }
            // Missing or invalid credentials → 401 in the same RFC 7807 format as other errors
            .exceptionHandling { it.authenticationEntryPoint { _, response, _ -> writeUnauthorized(response) } }
            .build()

    /** Browsers only let the frontend call the API from these origins (e.g. the Vite dev server). */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = this@SecurityConfig.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type")
                allowCredentials = true // lets the browser send the refresh cookie to /api/v1/auth/*
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/api/**", config) }
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write(
            """{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication is required."}""",
        )
    }

    companion object {
        /** Reachable without a token (matches `security: []` in api/openapi.yaml). */
        val PUBLIC_ENDPOINTS =
            arrayOf(
                "/actuator/health/**",
                "/api/v1/system/info",
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/verify-email",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password",
                "/api/v1/auth/google",
                "/api/v1/auth/google/callback",
            )
    }
}
