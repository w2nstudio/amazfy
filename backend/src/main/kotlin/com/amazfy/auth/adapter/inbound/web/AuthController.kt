package com.amazfy.auth.adapter.inbound.web

import com.amazfy.api.generated.AuthApi
import com.amazfy.api.generated.model.AuthTokens
import com.amazfy.api.generated.model.ForgotPasswordRequest
import com.amazfy.api.generated.model.LoginRequest
import com.amazfy.api.generated.model.RegisterRequest
import com.amazfy.api.generated.model.ResetPasswordRequest
import com.amazfy.api.generated.model.TokenRequest
import com.amazfy.auth.application.AuthService
import com.amazfy.auth.application.TokenService
import com.amazfy.auth.application.Tokens
import com.amazfy.common.error.NotImplementedYetException
import com.amazfy.common.security.currentUserId
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** Implements the Auth part of api/openapi.yaml. The refresh token travels only in an HttpOnly cookie. */
@RestController
class AuthController(
    private val authService: AuthService,
    private val tokenService: TokenService,
    @param:Value("\${amazfy.auth.cookie-secure}") private val cookieSecure: Boolean,
) : AuthApi {
    override fun register(registerRequest: RegisterRequest): ResponseEntity<AuthTokens> {
        val registration = authService.register(registerRequest.email, registerRequest.password, registerRequest.accountName)
        return withTokens(HttpStatus.CREATED, tokenService.issueFor(registration.user.id))
    }

    override fun login(loginRequest: LoginRequest): ResponseEntity<AuthTokens> {
        val user = authService.login(loginRequest.email, loginRequest.password)
        return withTokens(HttpStatus.OK, tokenService.issueFor(user.id))
    }

    override fun refreshTokens(amazfyRefresh: String?): ResponseEntity<AuthTokens> =
        withTokens(HttpStatus.OK, tokenService.refresh(amazfyRefresh))

    override fun logout(amazfyRefresh: String?): ResponseEntity<Unit> {
        tokenService.logout(amazfyRefresh)
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO)).build()
    }

    override fun logoutAll(): ResponseEntity<Unit> {
        tokenService.logoutAll(currentUserId())
        return ResponseEntity.noContent().build()
    }

    // Built in later M1 steps (see docs/plan/phase-1-plan.md)
    override fun verifyEmail(tokenRequest: TokenRequest): ResponseEntity<Unit> = throw NotImplementedYetException("Email verification")

    override fun resendVerification(): ResponseEntity<Unit> = throw NotImplementedYetException("Email verification")

    override fun forgotPassword(forgotPasswordRequest: ForgotPasswordRequest): ResponseEntity<Unit> =
        throw NotImplementedYetException("Password reset")

    override fun resetPassword(resetPasswordRequest: ResetPasswordRequest): ResponseEntity<Unit> =
        throw NotImplementedYetException("Password reset")

    override fun startGoogleLogin(): ResponseEntity<Unit> = throw NotImplementedYetException("Google login")

    override fun completeGoogleLogin(
        code: String,
        state: String,
    ): ResponseEntity<Unit> = throw NotImplementedYetException("Google login")

    private fun withTokens(
        status: HttpStatus,
        tokens: Tokens,
    ): ResponseEntity<AuthTokens> =
        ResponseEntity
            .status(status)
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken, tokens.refreshTokenExpiresIn))
            .body(AuthTokens(tokens.accessToken, AuthTokens.TokenType.Bearer, tokens.accessTokenExpiresIn.toSeconds().toInt()))

    /** HttpOnly: page scripts can't read it. SameSite=Strict: never sent by other sites. Path: only auth endpoints get it. */
    private fun refreshCookie(
        value: String,
        maxAge: Duration,
    ): String =
        ResponseCookie
            .from(REFRESH_COOKIE, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(maxAge)
            .build()
            .toString()

    companion object {
        const val REFRESH_COOKIE = "amazfy_refresh"
    }
}
