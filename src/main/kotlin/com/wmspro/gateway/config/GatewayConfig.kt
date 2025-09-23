package com.wmspro.gateway.config

import com.wmspro.gateway.filter.JwtAuthenticationFilter
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GatewayConfig(private val jwtFilter: JwtAuthenticationFilter) {
    
    @Bean
    fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator {
        return builder.routes()
            .route("tenant-service") { r ->
                r.path("/api/tenants/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://TENANT-SERVICE")
            }
            .route("warehouse-service") { r ->
                r.path("/api/warehouses/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WAREHOUSE-SERVICE")
            }
            .route("product-service") { r ->
                r.path("/api/products/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://PRODUCT-SERVICE")
            }
            .route("inventory-service") { r ->
                r.path("/api/inventory/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://INVENTORY-SERVICE")
            }
            .route("order-service") { r ->
                r.path("/api/orders/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://ORDER-SERVICE")
            }
            .route("health-check") { r ->
                r.path("/health/**")
                    .uri("lb://HEALTH-SERVICE")
            }
            .build()
    }
}