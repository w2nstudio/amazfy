package com.amazfy.auth.adapter.outbound.jwt

import com.amazfy.auth.application.port.AccessTokenIssuer
import com.amazfy.common.security.JwtConfig
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Signs access tokens: issuer "amazfy", subject = user id, issued-at and expiry. No personal data in the token. */
@Component
class JwtAccessTokenIssuer(
    private val encoder: JwtEncoder,
    private val clock: Clock,
) : AccessTokenIssuer {
    override fun issue(
        userId: UUID,
        expiresAt: Instant,
    ): String {
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(JwtConfig.ISSUER)
                .subject(userId.toString())
                .issuedAt(clock.instant())
                .expiresAt(expiresAt)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}
