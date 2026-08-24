package com.wmspro.gateway.filter

import com.wmspro.gateway.jwt.JwtService
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/** Blocking authorization for the new Warehouse Job surface. */
@Component
class BillingPermissionFilter(
    private val jwtService: JwtService
) : AbstractGatewayFilterFactory<BillingPermissionFilter.Config>(Config::class.java) {
    override fun apply(config: Config): GatewayFilter = GatewayFilter { exchange, chain ->
        val token = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?.removePrefix("Bearer ")
        if (token.isNullOrBlank() || !jwtService.validateToken(token)) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return@GatewayFilter exchange.response.setComplete()
        }
        val required = requiredPermission(exchange.request.method?.name(), exchange.request.uri.path)
        if (!hasPermission(token, required)) {
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            return@GatewayFilter exchange.response.setComplete()
        }
        chain.filter(exchange)
    }

    private fun requiredPermission(method: String?, path: String): String = when {
        path.endsWith("/retry") || path.endsWith("/reconcile") -> "billing.warehouse-job.sync"
        path.endsWith("/cancel") -> "billing.warehouse-job.cancel"
        method == "POST" -> "billing.warehouse-job.generate"
        else -> "billing.warehouse-job.view"
    }

    private fun hasPermission(token: String, required: String): Boolean {
        val role = jwtService.extractClaim(token, "role")?.toString()?.uppercase()
        if (role in setOf("ADMIN", "SUPER_ADMIN", "ROLE_ADMIN")) return true
        val claims = jwtService.extractAllClaims(token)
        val permissions = claims["permissions"]
        val aliases = permissionAliases(required)
        return when (permissions) {
            is Collection<*> -> permissions.mapNotNull { it?.toString() }.any(aliases::contains)
            is Map<*, *> -> permissions.entries.any { (key, value) -> key?.toString() in aliases && value == true }
            else -> aliases.any { claims[it] == true }
        }
    }

    private fun permissionAliases(permission: String): Set<String> = when (permission) {
        "billing.warehouse-job.view" -> setOf(permission, "canViewWarehouseJobs", "canViewBilling")
        "billing.warehouse-job.generate" -> setOf(permission, "canGenerateWarehouseJobs")
        "billing.warehouse-job.sync" -> setOf(permission, "canSyncWarehouseJobs")
        "billing.warehouse-job.cancel" -> setOf(permission, "canCancelWarehouseJobs")
        else -> setOf(permission)
    }

    class Config
}
