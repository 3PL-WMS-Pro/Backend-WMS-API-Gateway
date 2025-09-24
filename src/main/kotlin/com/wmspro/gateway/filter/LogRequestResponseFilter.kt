package com.wmspro.gateway.filter

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.*

/**
 * Lightweight logging filter for API Gateway to trace incoming requests and outgoing responses.
 * It adds/propagates a correlation ID (X-Request-Id) and logs basic request/response info.
 */
@Component
class LogRequestResponseFilter : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(LogRequestResponseFilter::class.java)

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val request: ServerHttpRequest = exchange.request
        val path = request.uri.path
        val method = request.method?.name() ?: "UNKNOWN"

        // Correlation ID handling
        val existingId = request.headers.getFirst("X-Request-Id")
        val requestId = existingId ?: UUID.randomUUID().toString()

        val mutatedRequest = if (existingId == null) {
            request.mutate().header("X-Request-Id", requestId).build()
        } else request

        if (log.isInfoEnabled) {
            log.info("[GW][{} {}][reqId={}] Incoming request from={} query={}"
                , method, path, requestId, request.remoteAddress?.address?.hostAddress, request.uri.rawQuery)
        }

        val mutatedExchange = exchange.mutate().request(mutatedRequest).build()

        return chain.filter(mutatedExchange).doOnSuccess {
            val response: ServerHttpResponse = mutatedExchange.response
            val status = response.statusCode?.value() ?: 0
            log.info("[GW][{} {}][reqId={}] Responded with status {}"
                , method, path, requestId, status)
        }.doOnError { ex ->
            log.error("[GW][{} {}][reqId={}] Failed with exception: {}", method, path, requestId, ex.message, ex)
        }
    }

    // Run early but after routing filters
    override fun getOrder(): Int = -1
}
