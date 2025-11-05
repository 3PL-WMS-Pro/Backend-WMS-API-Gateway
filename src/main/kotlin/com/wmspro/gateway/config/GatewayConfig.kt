package com.wmspro.gateway.config

import com.wmspro.gateway.filter.JwtAuthenticationFilter
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class GatewayConfig(private val jwtFilter: JwtAuthenticationFilter) {

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val corsConfig = CorsConfiguration()
        corsConfig.allowedOrigins = listOf(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:3001",
            "https://wms.leadtorev.com"
        )
        corsConfig.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        corsConfig.allowedHeaders = listOf("*")
        corsConfig.allowCredentials = true
        corsConfig.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", corsConfig)

        return CorsWebFilter(source)
    }
    
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
            .route("warehouse-service-warehouses") { r ->
                r.path("/api/v1/warehouses/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-WAREHOUSE-SERVICE")
            }
            .route("warehouse-service-floors") { r ->
                r.path("/api/v1/floors/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-WAREHOUSE-SERVICE")
            }
            .route("warehouse-service-zones") { r ->
                r.path("/api/v1/zones/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-WAREHOUSE-SERVICE")
            }
            .route("warehouse-service-aisles") { r ->
                r.path("/api/v1/aisles/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-WAREHOUSE-SERVICE")
            }
            .route("warehouse-service-racks") { r ->
                r.path("/api/v1/racks/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-WAREHOUSE-SERVICE")
            }
            .route("warehouse-service-shelves") { r ->
                r.path("/api/v1/shelves/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-WAREHOUSE-SERVICE")
            }
            .route("warehouse-service-bins") { r ->
                r.path("/api/v1/bins/**")
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
            .route("inventory-service-barcode-requests") { r ->
                r.path("/api/v1/barcode-requests/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-INVENTORY-SERVICE")
            }
            .route("inventory-service-storage-items") { r ->
                r.path("/api/v1/storage-items/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-INVENTORY-SERVICE")
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
            .route("file-service") { r ->
                r.path("/api/v1/files/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-FILE-SERVICE")
            }
            .route("qc-service-stock-verification") { r ->
                r.path("/api/v1/stock-verification-requests/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-QC-SERVICE")
            }
            .route("task-service-receiving-tasks") { r ->
                r.path("/api/v1/receiving-tasks/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TASK-SERVICE")
            }
            .route("task-service") { r ->
                r.path("/api/v1/tasks/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TASK-SERVICE")
            }
            .route("inbound-service-asns") { r ->
                r.path("/api/v1/asns/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-INBOUND-SERVICE")
            }
            .route("inbound-service-receiving-records") { r ->
                r.path("/api/v1/receiving-records/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-INBOUND-SERVICE")
            }
            .route("health-check") { r ->
                r.path("/actuator/health/**")
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .build()
    }
}