package com.wmspro.gateway.filter

import com.wmspro.common.jwt.JwtTokenExtractor
import com.wmspro.common.jwt.JwtValidator
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
    private val jwtTokenExtractor: JwtTokenExtractor,
    private val jwtValidator: JwtValidator
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
                // Use common library's JWT utilities
                val token = authHeader.substring(7)
                
                if (!jwtValidator.isTokenValid(token)) {
                    return@GatewayFilter onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED)
                }
                
                // Extract claims using common library
                val username = jwtTokenExtractor.extractUsername(token)
                val userType = jwtTokenExtractor.extractUserType(token)
                val departmentId = jwtTokenExtractor.extractDepartment(token)
                val tenantId = jwtTokenExtractor.extractTenantId(token)
                
                val mutatedRequest = exchange.request.mutate()
                    .header("X-User-Id", username ?: "")
                    .header("X-User-Type", userType?.toString() ?: "")
                    .header("X-Department-Id", departmentId?.toString() ?: "")
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