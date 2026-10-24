package com.amazfy.auth.adapter.inbound.web

import com.amazfy.api.generated.AuthApi
import com.amazfy.api.generated.model.AuthTokens
import com.amazfy.api.generated.model.ForgotPasswordRequest
import com.amazfy.api.generated.model.LoginRequest
import com.amazfy.api.generated.model.RegisterRequest
import com.amazfy.api.generated.model.ResetPasswordRequest
import com.amazfy.api.generated.model.TokenRequest
import com.amazfy.auth.application.AuthService
import com.amazfy.auth.application.GoogleLoginService
import com.amazfy.auth.application.GoogleRoundTrip
import com.amazfy.auth.application.GoogleSignInCancelledException
import com.amazfy.auth.application.TokenService
import com.amazfy.auth.application.Tokens
import com.amazfy.auth.domain.GoogleLoginFailedException
import com.amazfy.common.amazon.Region
import com.amazfy.common.security.currentUserId
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Duration

/** Implements the Auth part of api/openapi.yaml. The refresh token travels only in an HttpOnly cookie. */
@RestController
class AuthController(
    private val authService: AuthService,
    private val tokenService: TokenService,
    private val googleLoginService: GoogleLoginService,
    private val request: HttpServletRequest, // a proxy to the current request
    @param:Value("\${amazfy.auth.cookie-secure}") private val cookieSecure: Boolean,
    @param:Value("\${amazfy.frontend-base-url}") private val frontendBaseUrl: String,
) : AuthApi {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun register(registerRequest: RegisterRequest): ResponseEntity<AuthTokens> {
        val registration = with(registerRequest) { authService.register(email, password, accountName, Region.valueOf(region.value)) }
        return withTokens(HttpStatus.CREATED, tokenService.issueFor(registration.user.id))
    }

    override fun login(loginRequest: LoginRequest): ResponseEntity<AuthTokens> {
        val user = authService.login(loginRequest.email, loginRequest.password, clientIp())
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

    override fun verifyEmail(tokenRequest: TokenRequest): ResponseEntity<Unit> {
        authService.verifyEmail(tokenRequest.token)
        return ResponseEntity.noContent().build()
    }

    override fun resendVerification(): ResponseEntity<Unit> {
        authService.resendVerification(currentUserId())
        return ResponseEntity.noContent().build()
    }

    override fun forgotPassword(forgotPasswordRequest: ForgotPasswordRequest): ResponseEntity<Unit> {
        authService.forgotPassword(forgotPasswordRequest.email, clientIp())
        return ResponseEntity.accepted().build()
    }

    override fun resetPassword(resetPasswordRequest: ResetPasswordRequest): ResponseEntity<Unit> {
        authService.resetPassword(resetPasswordRequest.token, resetPasswordRequest.newPassword)
        return ResponseEntity.noContent().build()
    }

    /** Sends the browser to Google's sign-in page; the values to check on the way back wait in a short-lived cookie. */
    override fun startGoogleLogin(): ResponseEntity<Unit> {
        val start = googleLoginService.start()
        val cookie =
            ResponseCookie
                .from(GOOGLE_LOGIN_COOKIE, with(start.roundTrip) { listOf(state, codeVerifier, nonce) }.joinToString("."))
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax") // Lax, not Strict: the browser must send it when Google redirects back to us
                .path(GOOGLE_LOGIN_PATH)
                .maxAge(Duration.ofMinutes(10))
                .build()
        return redirect(start.redirectUrl, listOf(cookie.toString()))
    }

    /**
     * Google sends the browser back here. Checks the round trip and Google's ID token, then redirects to the frontend.
     * The round-trip cookie is always deleted: each sign-in attempt can be completed only once.
     */
    override fun completeGoogleLogin(
        state: String,
        code: String?,
        error: String?,
    ): ResponseEntity<Unit> {
        val roundTrip =
            request.cookies
                ?.firstOrNull { it.name == GOOGLE_LOGIN_COOKIE }
                ?.value
                ?.split(".")
                ?.takeIf { it.size == 3 }
                ?.let { GoogleRoundTrip(state = it[0], codeVerifier = it[1], nonce = it[2]) }
        val deleteCookie =
            ResponseCookie
                .from(GOOGLE_LOGIN_COOKIE, "")
                .path(GOOGLE_LOGIN_PATH)
                .maxAge(Duration.ZERO)
                .build()

        val cookies = mutableListOf(deleteCookie.toString())
        val frontendPath =
            try {
                val login = authService.loginWithGoogle(googleLoginService.complete(code, error, state, roundTrip))
                // Logged in: the refresh cookie is set here; the frontend then calls /auth/refresh for an access token
                val tokens = tokenService.issueFor(login.userId)
                cookies += refreshCookie(tokens.refreshToken, tokens.refreshTokenExpiresIn)
                if (login.needsOnboarding) "/onboarding" else "/"
            } catch (e: GoogleSignInCancelledException) {
                "/login" // the user's own choice: no error message
            } catch (e: GoogleLoginFailedException) {
                log.info("Google sign-in failed: {}", e.message)
                "/login?error=google"
            }
        return redirect(frontendBaseUrl + frontendPath, cookies)
    }

    private fun redirect(
        url: String,
        cookies: List<String>,
    ): ResponseEntity<Unit> =
        ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(url))
            .header(HttpHeaders.SET_COOKIE, *cookies.toTypedArray())
            .build()

    private fun withTokens(
        status: HttpStatus,
        tokens: Tokens,
    ): ResponseEntity<AuthTokens> =
        ResponseEntity
            .status(status)
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken, tokens.refreshTokenExpiresIn))
            .body(AuthTokens(tokens.accessToken, AuthTokens.TokenType.Bearer, tokens.accessTokenExpiresIn.toSeconds().toInt()))

    /**
     * The caller's IP address. Behind the hosting platform's proxy, the prod profile makes Tomcat take it from the
     * proxy's X-Forwarded-For header (server.forward-headers-strategy); locally it is the direct caller.
     */
    private fun clientIp(): String = request.remoteAddr

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

        /** Holds state, PKCE code verifier and nonce during the round trip through Google (10 minutes). */
        const val GOOGLE_LOGIN_COOKIE = "amazfy_google_login"
        private const val GOOGLE_LOGIN_PATH = "/api/v1/auth/google"
    }
}
