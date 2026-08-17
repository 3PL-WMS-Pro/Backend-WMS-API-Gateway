package com.wmspro.gateway.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Validates **customer-portal** JWTs at the gateway.
 *
 * ## Why this is separate from [JwtService]
 *
 * [JwtService] validates staff tokens against `jwt.secret` and checks only expiry and
 * `type != refresh`. It performs no issuer, audience or role check, and
 * `JwtAuthenticationFilter` forwards `X-User-Id` and `X-Tenant-Id` straight from the claims.
 *
 * If portal tokens shared that secret, a customer's token would therefore authenticate against
 * every internal staff route — task lists, inventory writes, billing. Two independent controls
 * prevent that:
 *
 *  1. A **different signing secret** (`portal.jwt.secret`), so a portal token simply fails
 *     signature verification on staff routes and vice versa.
 *  2. Pinned `iss` and `aud`, so even a same-secret misconfiguration would still be caught.
 *
 * Uses the JDK's own [Mac] rather than jjwt: the gateway's jjwt is 0.9.1 (2018, known CVE
 * exposure), and the portal service mints with the same primitive, so the two stay symmetrical.
 */
@Component
class PortalJwtValidator(
    @Value("\${portal.jwt.secret:portal-dev-only-secret-key-change-me-at-least-32-bytes}")
    private val portalSecret: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    private val keySpec by lazy {
        SecretKeySpec(portalSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    }

    companion object {
        private const val ISSUER = "wms-customer-portal"
        private const val AUDIENCE_API = "wms-customer-portal-api"
        private const val TYPE_ACCESS = "access"
    }

    /**
     * Verifies signature, issuer, audience, type and expiry.
     *
     * Returns the claims on success so the filter can log attribution — but note the filter
     * deliberately does NOT forward tenant or account headers downstream. The portal service
     * re-derives both from the token itself, so a compromised or buggy gateway cannot widen a
     * customer's entitlement.
     */
    fun validate(token: String): PortalTokenClaims? = try {
        val parts = token.split('.')
        if (parts.size != 3) {
            null
        } else {
            val expected = hmac("${parts[0]}.${parts[1]}")
            if (!MessageDigest.isEqual(decoder.decode(parts[2]), expected)) {
                null
            } else {
                @Suppress("UNCHECKED_CAST")
                val claims = mapper.readValue(decoder.decode(parts[1]), Map::class.java) as Map<String, Any?>

                when {
                    claims["iss"] != ISSUER -> null
                    claims["aud"] != AUDIENCE_API -> null
                    claims["typ"] != TYPE_ACCESS -> null
                    else -> {
                        val exp = (claims["exp"] as? Number)?.toLong()
                        if (exp == null || Instant.now().epochSecond >= exp) {
                            null
                        } else {
                            PortalTokenClaims(
                                portalUserId = claims["sub"] as? String ?: "",
                                tenantId = (claims["tid"] as? Number)?.toInt(),
                                email = claims["eml"] as? String
                            )
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        logger.debug("Portal token rejected at gateway: {}", e.message)
        null
    }

    private fun hmac(signingInput: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8))
    }
}

data class PortalTokenClaims(
    val portalUserId: String,
    val tenantId: Int?,
    val email: String?
)
