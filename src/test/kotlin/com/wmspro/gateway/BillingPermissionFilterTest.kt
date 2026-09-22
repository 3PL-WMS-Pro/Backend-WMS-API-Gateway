package com.wmspro.gateway

import com.wmspro.gateway.filter.BillingPermissionFilter
import com.wmspro.gateway.jwt.JwtService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Date

class BillingPermissionFilterTest {
    private val secret = "warehouse-job-permission-test-key-not-for-production"
    private val filter = BillingPermissionFilter(JwtService(secret))
        .apply(BillingPermissionFilter.Config())

    @Test
    fun `FreighAI order view alone does not satisfy the WMS gateway contract`() {
        val (forwarded, status) = request(listOf("order:view"))
        assertFalse(forwarded)
        assertEquals(HttpStatus.FORBIDDEN, status)
    }

    @Test
    fun `manager with explicit warehouse job viewing permission reaches the tenant service`() {
        val (forwarded, status) = request(listOf("order:view", "billing.warehouse-job.view"))
        assertTrue(forwarded)
        assertNull(status)
    }

    @Test
    fun `viewing permission does not grant generation sync or cancellation`() {
        for (path in listOf(
            "/api/v1/warehouse-jobs",
            "/api/v1/warehouse-jobs/WJ-1/retry",
            "/api/v1/warehouse-jobs/WJ-1/reconcile",
            "/api/v1/warehouse-jobs/WJ-1/cancel"
        )) {
            val (forwarded, status) = request(listOf("billing.warehouse-job.view"), path, post = true)
            assertFalse(forwarded, path)
            assertEquals(HttpStatus.FORBIDDEN, status, path)
        }
    }

    private fun request(
        permissions: List<String>,
        path: String = "/api/v1/warehouse-jobs?billingMonth=2026-08",
        post: Boolean = false
    ): Pair<Boolean, org.springframework.http.HttpStatusCode?> {
        val token = Jwts.builder()
            .setSubject("user_test_manager")
            .claim("role", "MANAGER")
            .claim("permissions", permissions)
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(SignatureAlgorithm.HS256, secret.toByteArray())
            .compact()
        val builder = if (post) MockServerHttpRequest.post(path) else MockServerHttpRequest.get(path)
        val exchange = MockServerWebExchange.from(builder.header("Authorization", "Bearer $token").build())
        var forwarded = false
        filter.filter(exchange, GatewayFilterChain {
            forwarded = true
            Mono.empty()
        }).block(Duration.ofSeconds(5))
        return forwarded to exchange.response.statusCode
    }
}
