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

    /**
     * A token is acceptable for API access when it verifies, has not expired, and
     * is not a REFRESH token.
     *
     * The refresh-token check matters because FreighAi signs access and refresh
     * tokens with the SAME key (users-service `JwtService.generateRefreshToken`),
     * so a refresh token verifies here perfectly well. It is distinguishable only
     * by its `type: "refresh"` claim, and it lives for 7 days against the access
     * token's 24 hours. FreighAi's own gateway rejects it for exactly this reason
     * (`JwtValidationService`: `if (claims["type"] == "refresh") return null`);
     * this mirrors that guard.
     *
     * This became reachable when the mobile migration started returning
     * `refreshToken` to clients from `POST /users/login`. Before that the refresh
     * token never left Tenant-Service, so the gap was latent. Without this check a
     * refresh token lifted from AsyncStorage would work as a 7-day bearer credential
     * that survives both rotation and an explicit `/users/logout` revocation — and,
     * carrying no `email` claim, would stamp `X-User-Id`/`X-User-Email` with the raw
     * `sub`, re-creating the very contamination the M1 fix and M9 backfill remove.
     */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = extractAllClaims(token)
            if (claims["type"] == "refresh") return false
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
