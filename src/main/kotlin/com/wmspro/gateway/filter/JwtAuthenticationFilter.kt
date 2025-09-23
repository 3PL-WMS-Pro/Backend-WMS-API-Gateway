package com.wmspro.gateway.filter

import com.wmspro.gateway.jwt.JwtService
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
    
    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            
            if (!request.headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                return@GatewayFilter onError(exchange, "No authorization header", HttpStatus.UNAUTHORIZED)
            }
            
            val authHeader = request.headers[HttpHeaders.AUTHORIZATION]?.get(0)
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return@GatewayFilter onError(exchange, "Invalid authorization header", HttpStatus.UNAUTHORIZED)
            }
            
            try {
                val token = authHeader.substring(7)
                
                if (!jwtService.validateToken(token)) {
                    return@GatewayFilter onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED)
                }
                
                // Extract claims
                val username = jwtService.extractUsername(token)
                val userType = jwtService.extractClaim(token, "userTypeId")?.toString()
                val departmentId = jwtService.extractClaim(token, "departmentId")?.toString()
                val tenantId = jwtService.extractClaim(token, "clientId")?.toString()
                
                val mutatedRequest = exchange.request.mutate()
                    .header("X-User-Id", username ?: "")
                    .header("X-User-Type", userType ?: "")
                    .header("X-Department-Id", departmentId ?: "")
                    .header("X-Tenant-Id", tenantId ?: "")
                    .header("Authorization", authHeader) // Pass the token to downstream services
                    .build()
                
                chain.filter(exchange.mutate().request(mutatedRequest).build())
            } catch (e: Exception) {
                onError(exchange, "Token validation failed: ${e.message}", HttpStatus.UNAUTHORIZED)
            }
        }
    }
    
    private fun onError(exchange: ServerWebExchange, error: String, httpStatus: HttpStatus): Mono<Void> {
        val response: ServerHttpResponse = exchange.response
        response.statusCode = httpStatus
        return response.setComplete()
    }
    
    class Config
}