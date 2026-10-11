package com.amazfy.common.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Signing and checking of access tokens (JWT, HS256 with the JWT_SECRET key).
 * The auth module issues tokens with the encoder; Spring Security checks every request's token with the decoder.
 * Switching to a managed provider later means replacing the decoder with one that uses the provider's public keys.
 */
@Configuration
class JwtConfig(
    @param:Value("\${amazfy.jwt.secret}") secret: String,
) {
    private val key: SecretKey =
        secret.toByteArray().let { bytes ->
            require(bytes.size >= MIN_SECRET_BYTES) { "JWT_SECRET must be at least $MIN_SECRET_BYTES bytes (256 bits)" }
            SecretKeySpec(bytes, "HmacSHA256")
        }

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().apply {
            // Checks expiry (with a small clock-skew allowance) and that we issued the token
            setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER))
        }

    companion object {
        const val ISSUER = "amazfy"
        private const val MIN_SECRET_BYTES = 32
    }
}
