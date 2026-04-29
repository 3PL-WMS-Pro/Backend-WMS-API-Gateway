package com.wmspro.gateway.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

/**
 * Validates JWTs presented to the WMS gateway.
 *
 * Phase 5 of the leadtorev → FreighAi migration: the signing secret is now the
 * FreighAi HMAC key (was the leadtorev secret). All tokens issued post-cutover
 * come from FreighAi's auth service via the WMS auth proxy (AuthProxyService),
 * so this is the only secret the gateway needs to know about.
 *
 * Note: jjwt 0.9.1's `setSigningKey(String)` base64-decodes the input — that
 * was acceptable with the old leadtorev secret because Apache Commons' Base64
 * decoder silently skipped the `#`/`@` characters. FreighAi's secret contains
 * `-` (URL-safe base64 character) so a String overload would behave
 * unexpectedly. We use the raw `byte[]` overload to keep things deterministic.
 */
@Component
class JwtService(
    @Value("\${jwt.secret:freighai-dev-secret-key-256-bits-minimum-for-hs256-algorithm}")
    private val jwtSecret: String
) {

    private val signingKey: ByteArray by lazy { jwtSecret.toByteArray(Charsets.UTF_8) }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = extractAllClaims(token)
            val expiration = claims.expiration
            expiration == null || expiration.after(Date())
        } catch (e: Exception) {
            false
        }
    }

    fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .setSigningKey(signingKey)
            .parseClaimsJws(token)
            .body
    }

    fun extractUsername(token: String): String? {
        return try {
            extractAllClaims(token).subject
        } catch (e: Exception) {
            null
        }
    }

    fun extractClaim(token: String, claimKey: String): Any? {
        return try {
            extractAllClaims(token)[claimKey]
        } catch (e: Exception) {
            null
        }
    }
}
