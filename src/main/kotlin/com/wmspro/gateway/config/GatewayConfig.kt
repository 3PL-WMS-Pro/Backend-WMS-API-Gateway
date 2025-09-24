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
            .route("tenant-service-tenants") { r ->
                r.path("/api/v1/tenants/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-user-roles") { r ->
                r.path("/api/v1/user-role-mappings/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-settings") { r ->
                r.path("/api/v1/tenant-settings/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("warehouse-service") { r ->
                r.path("/api/v1/warehouses/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-WAREHOUSE-SERVICE")
            }
            .route("product-service") { r ->
                r.path("/api/v1/products/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-PRODUCT-SERVICE")
            }
            .route("inventory-service") { r ->
                r.path("/api/v1/inventory/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-INVENTORY-SERVICE")
            }
            .route("order-service") { r ->
                r.path("/api/v1/orders/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-ORDER-SERVICE")
            }
            .route("health-check") { r ->
                r.path("/actuator/health/**")
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .build()
    }
}