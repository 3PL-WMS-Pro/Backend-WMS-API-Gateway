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
            // ─────────────────────────────────────────────────────────────────
            // Phase 5 wrapper routes — login is public; everything else under
            // /users/** and /clients/** requires a valid (FreighAi) JWT.
            // The /users/login route MUST be declared BEFORE /users/** so its
            // more specific match wins.
            // ─────────────────────────────────────────────────────────────────
            .route("tenant-service-auth-login") { r ->
                r.path("/users/login")
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-users") { r ->
                r.path("/users/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-clients") { r ->
                r.path("/clients/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
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
            .route("tenant-service-document-templates") { r ->
                r.path("/api/v1/document-templates/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            // ─────────────────────────────────────────────────────────────────
            // WMS Billing routes (Phases 1-10) — all hosted by Tenant Service
            // under com.wmspro.tenant.billing.* package. JWT-required like the
            // other authenticated tenant-service paths.
            // ─────────────────────────────────────────────────────────────────
            .route("tenant-service-service-catalog") { r ->
                r.path("/api/v1/service-catalog/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-billing-profiles") { r ->
                r.path("/api/v1/billing-profiles/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-service-logs") { r ->
                r.path("/api/v1/service-logs/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-billing-runs") { r ->
                r.path("/api/v1/billing-runs/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-wms-invoices") { r ->
                r.path("/api/v1/wms-invoices/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-freighai-proxy") { r ->
                r.path("/api/v1/freighai/**")
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
            .route("inventory-service-barcode-reservations") { r ->
                r.path("/api/v1/barcode-reservations/**")
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
            .route("inventory-service-quantity-inventory") { r ->
                r.path("/api/v1/quantity-inventory/**")
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