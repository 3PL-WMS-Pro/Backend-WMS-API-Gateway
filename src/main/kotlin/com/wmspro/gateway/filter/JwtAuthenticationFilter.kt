package com.wmspro.gateway.filter

import com.wmspro.gateway.jwt.JwtService
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config>(Config::class.java) {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            val path = request.uri.path
            val method = request.method?.name() ?: "UNKNOWN"

            if (!request.headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                log.warn("[GW][{} {}] Missing Authorization header", method, path)
                return@GatewayFilter onError(exchange, "No authorization header", HttpStatus.UNAUTHORIZED)
            }

            val authHeader = request.headers[HttpHeaders.AUTHORIZATION]?.get(0)

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[GW][{} {}] Invalid Authorization header format", method, path)
                return@GatewayFilter onError(exchange, "Invalid authorization header", HttpStatus.UNAUTHORIZED)
            }

            try {
                val token = authHeader.substring(7)

                if (!jwtService.validateToken(token)) {
                    log.warn("[GW][{} {}] Token validation failed (invalid/expired)", method, path)
                    return@GatewayFilter onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED)
                }

                // Extract claims. Legacy leadtorev JWTs had `clientId`/`userTypeId`/
                // `departmentId`; FreighAi JWTs have only `sub`/`email`/`role` plus a
                // `tenant_id` STRING (e.g. "tenant_c9d375a64417") that's incompatible with
                // the WMS backend's Long-typed X-Tenant-Id contract. We deliberately do
                // NOT fall back to FreighAi's `tenant_id` — instead we let the frontend's
                // own X-Tenant-Id / X-Client header (Long, e.g. "199") pass through
                // untouched (see the no-clobber `if` blocks below).
                // Identity: prefer the `email` claim over `sub`.
                //
                // FreighAi sets `sub` to the opaque user id ("user_41a4d54161b6"), but every
                // WMS document that records a user stores an EMAIL — Task.assignedTo,
                // StatusHistory.changedBy, receivingStaff, pickedBy, createdBy — and
                // UserService resolves those against FreighAi's /users/batch-by-emails to
                // render display names.
                //
                // Forwarding `sub` as X-User-Id therefore broke two things: TaskService's
                // findByAssignedTo(userId) matched nothing (empty task list + zero dashboard
                // stats for every mobile worker), and 167 production documents were written
                // with `user_*` values in email-typed fields between 2026-05-05 and this fix.
                //
                // Falling back to `sub` keeps legacy leadtorev tokens working — their subject
                // WAS the email.
                val userEmail = jwtService.extractClaim(token, "email")?.toString()?.takeIf { it.isNotBlank() }
                val username = userEmail ?: jwtService.extractUsername(token)
                val userType = jwtService.extractClaim(token, "userTypeId")?.toString()
                val departmentId = jwtService.extractClaim(token, "departmentId")?.toString()
                val tenantId = jwtService.extractClaim(token, "clientId")?.toString()

                log.debug(
                    "[GW][{} {}] Authenticated user. userId={}, userType={}, departmentId={}, tenantId={}",
                    method, path, username, userType, departmentId, tenantId
                )

                val mutatedRequest = exchange.request.mutate().apply {
                    if (!username.isNullOrBlank()) header("X-User-Id", username)
                    // Parity with the web client, which sends X-User-Email from localStorage.
                    // Mobile sends no identity headers at all, so without this the billing /
                    // admin controllers that read X-User-Email would record "unknown".
                    if (!username.isNullOrBlank()) header("X-User-Email", username)
                    if (!userType.isNullOrBlank()) header("X-User-Type", userType)
                    if (!departmentId.isNullOrBlank()) header("X-Department-Id", departmentId)
                    if (!tenantId.isNullOrBlank()) header("X-Tenant-Id", tenantId)
                    header("Authorization", authHeader) // Pass the token to downstream services
                }.build()

                chain.filter(exchange.mutate().request(mutatedRequest).build())
            } catch (e: Exception) {
                log.error("[GW][{} {}] Exception during token processing: {}", method, path, e.message, e)
                onError(exchange, "Token validation failed: ${e.message}", HttpStatus.UNAUTHORIZED)
            }
        }
    }

    private fun onError(exchange: ServerWebExchange, error: String, httpStatus: HttpStatus): Mono<Void> {
        val response: ServerHttpResponse = exchange.response
        response.statusCode = httpStatus
        // We avoid writing the body to keep gateway lean, but we log the reason here
        log.debug("[GW][{}] Responding with status {}: {}", exchange.request.id, httpStatus.value(), error)
        return response.setComplete()
    }

    class Config
}