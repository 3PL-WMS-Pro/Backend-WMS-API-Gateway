package com.wmspro.gateway.filter

import com.wmspro.gateway.jwt.PortalJwtValidator
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Gateway filter for authenticated **customer-portal** routes.
 *
 * ## What makes this different from [JwtAuthenticationFilter]
 *
 * The staff filter *adds* identity headers (`X-User-Id`, `X-Tenant-Id`) from token claims and lets
 * a client-supplied `X-Tenant-Id` pass through untouched when the token has none. That is the
 * root of the cross-tenant exposure in the current system.
 *
 * This filter does the reverse: it **strips** every identity and tenant header from the inbound
 * request before forwarding. Nothing a customer's browser sends can influence which tenant or
 * account the portal service resolves — that comes exclusively from the signed token, re-verified
 * downstream. Stripping matters because a customer is an external party who can craft any header
 * they like; without it, `X-Tenant-Id: 205` would be forwarded verbatim and trusted by any service
 * that happened to read it.
 */
@Component
class PortalJwtAuthenticationFilter(
    private val portalJwtValidator: PortalJwtValidator
) : AbstractGatewayFilterFactory<PortalJwtAuthenticationFilter.Config>(Config::class.java) {

    private val log = LoggerFactory.getLogger(PortalJwtAuthenticationFilter::class.java)

    companion object {
        /**
         * Headers removed from every inbound portal request.
         *
         * These are the exact headers the downstream services read for tenant and identity. A
         * customer must not be able to set any of them.
         */
        private val STRIPPED_HEADERS = listOf(
            "X-Tenant-Id", "X-Tenant-ID",
            "X-Client-Id", "X-Client-ID", "X-Client",
            "X-User-Id", "X-User-ID",
            "X-User-Email", "X-User-Type",
            "X-Department-Id", "X-Account-Id"
        )
    }

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            val path = request.uri.path
            val method = request.method?.name() ?: "UNKNOWN"

            val authHeader = request.headers[HttpHeaders.AUTHORIZATION]?.firstOrNull()
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[GW-PORTAL][{} {}] Missing or malformed Authorization header", method, path)
                return@GatewayFilter unauthorized(exchange)
            }

            val claims = portalJwtValidator.validate(authHeader.substring(7))
            if (claims == null) {
                log.warn("[GW-PORTAL][{} {}] Portal token rejected", method, path)
                return@GatewayFilter unauthorized(exchange)
            }

            log.debug(
                "[GW-PORTAL][{} {}] Authenticated portal user {} (tenant {})",
                method, path, claims.email ?: claims.portalUserId, claims.tenantId
            )

            // Strip, then forward only the token. The portal service re-verifies it and derives
            // tenant + accounts from the claims — never from anything on the wire.
            val mutated = request.mutate()
                .headers { headers -> STRIPPED_HEADERS.forEach { headers.remove(it) } }
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .build()

            chain.filter(exchange.mutate().request(mutated).build())
        }
    }

    /**
     * Public portal routes (login, refresh, workspace lookup) still need the identity headers
     * stripped — an unauthenticated caller must not be able to inject a tenant either.
     */
    fun stripOnly(): GatewayFilter = GatewayFilter { exchange, chain ->
        val mutated = exchange.request.mutate()
            .headers { headers -> STRIPPED_HEADERS.forEach { headers.remove(it) } }
            .build()
        chain.filter(exchange.mutate().request(mutated).build())
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    class Config
}
