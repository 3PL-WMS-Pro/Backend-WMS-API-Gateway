package com.wmspro.gateway.config

import com.wmspro.gateway.filter.JwtAuthenticationFilter
import com.wmspro.gateway.filter.PortalJwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class GatewayConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    private val portalJwtFilter: PortalJwtAuthenticationFilter,
    /**
     * Customer-portal origin(s). Env-driven because the final hostname (leadtorev vs freighai) is
     * still undecided; the local default covers `bun run dev`.
     */
    @Value("\${portal.cors.allowed-origins:http://localhost:3100}")
    private val portalAllowedOrigins: String
) {

    /**
     * CORS, with the customer portal scoped to its own paths.
     *
     * ## Why there are two configurations and not one
     *
     * The portal origins were originally appended to the single `CorsConfiguration` registered at
     * the catch-all path pattern. That granted the **internet-facing** portal origin credentialed
     * cross-origin access to
     * every internal route the gateway fronts — `/api/v1/tenants`, `/users`, `/clients`, billing,
     * warehouses — with `allowCredentials = true` and `allowedHeaders = ["*"]`. Verified: a
     * preflight for `/api/v1/warehouses` from `http://localhost:3100` returned 200 with
     * `Access-Control-Allow-Origin` echoed, while a control origin got 403.
     *
     * That is a much wider grant than the portal needs. Registration order matters here:
     * `UrlBasedCorsConfigurationSource` returns the FIRST pattern that matches, so the specific
     * portal path must be registered before the catch-all.
     */
    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val internalOrigins = listOf(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:3001",
            "https://wms.leadtorev.com"
        )
        val portalOrigins = portalAllowedOrigins.split(',').map { it.trim() }.filter { it.isNotBlank() }

        val internalConfig = CorsConfiguration().apply {
            allowedOrigins = internalOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        // The portal surface: portal origins, plus the internal ones so the staff app can still
        // reach the /admin endpoints under this prefix.
        val portalConfig = CorsConfiguration().apply {
            allowedOrigins = portalOrigins + internalOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/v1/customer-portal/**", portalConfig)
        source.registerCorsConfiguration("/**", internalConfig)

        return CorsWebFilter(source)
    }
    
    @Bean
    fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator {
        return builder.routes()
            // ─────────────────────────────────────────────────────────────────
            // CUSTOMER PORTAL
            //
            // Declared FIRST so the specific portal paths win over the generic
            // ones below. Three distinct classes, each with different auth:
            //
            //   1. auth + workspace  → public. Headers stripped, no token needed.
            //   2. admin             → STAFF token (FreighAi), for the internal
            //                          WMS "Portal Users" screen.
            //   3. everything else   → PORTAL token, validated against a
            //                          SEPARATE secret with pinned iss/aud.
            //
            // The portal filter STRIPS every inbound identity and tenant header.
            // Customers are external parties who can craft any header they like;
            // the portal service derives tenant and accounts from the signed
            // token alone. This is the one namespace in the whole gateway that
            // does not trust X-Tenant-Id.
            // ─────────────────────────────────────────────────────────────────
            .route("customer-portal-auth-public") { r ->
                r.path(
                    "/api/v1/customer-portal/auth/login",
                    "/api/v1/customer-portal/auth/refresh",
                    "/api/v1/customer-portal/auth/logout",
                    "/api/v1/customer-portal/auth/forgot-password",
                    "/api/v1/customer-portal/auth/reset-password",
                    "/api/v1/customer-portal/auth/accept-invitation"
                )
                    .filters { f -> f.filter(portalJwtFilter.stripOnly()) }
                    .uri("lb://WMS-CUSTOMER-PORTAL-SERVICE")
            }
            .route("customer-portal-workspace-public") { r ->
                // Pre-auth tenant branding for the login screen.
                r.path("/api/v1/customer-portal/workspace/**")
                    .filters { f -> f.filter(portalJwtFilter.stripOnly()) }
                    .uri("lb://WMS-CUSTOMER-PORTAL-SERVICE")
            }
            .route("customer-portal-admin-staff") { r ->
                // Staff-facing. Uses the STAFF filter — these callers hold FreighAi tokens.
                r.path("/api/v1/customer-portal/admin/**")
                    .filters { f -> f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri("lb://WMS-CUSTOMER-PORTAL-SERVICE")
            }
            .route("customer-portal") { r ->
                r.path("/api/v1/customer-portal/**")
                    .filters { f -> f.filter(portalJwtFilter.apply(PortalJwtAuthenticationFilter.Config())) }
                    .uri("lb://WMS-CUSTOMER-PORTAL-SERVICE")
            }
            // ─────────────────────────────────────────────────────────────────
            // Phase 5 wrapper routes — the three auth endpoints are public;
            // everything else under /users/** and /clients/** requires a valid
            // (FreighAi) JWT.
            //
            // These three MUST be declared BEFORE /users/** so their more
            // specific match wins.
            //
            // /refresh and /logout are public by necessity, not convenience:
            // both key off the REFRESH token, and the caller's access token has
            // by definition already expired by the time refresh is called.
            // Requiring a valid access token would make them unusable. FreighAi
            // validates the refresh token itself, so possession of it is the
            // authorisation.
            //
            // Any new top-level path added here must ALSO be added to
            // TenantInterceptor.CENTRAL_DB_PATHS in wms-tenant-service — see
            // MIGRATION.md Incident 1, where a missed interceptor registration
            // silently wrote tenant data to the central database.
            // ─────────────────────────────────────────────────────────────────
            .route("tenant-service-auth-login") { r ->
                r.path("/users/login")
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-auth-refresh") { r ->
                r.path("/users/refresh")
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            .route("tenant-service-auth-logout") { r ->
                r.path("/users/logout")
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
            // Phase A: tenant-wide revenue defaults singleton.
            .route("tenant-service-billing-defaults") { r ->
                r.path("/api/v1/billing-defaults/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            // Phase B: tenant-wide internal cost defaults singleton.
            .route("tenant-service-operational-costs") { r ->
                r.path("/api/v1/operational-costs/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            // Phase C: per-shipment internal cost adjustments.
            .route("tenant-service-movement-cost-adjustments") { r ->
                r.path("/api/v1/movement-cost-adjustments/**")
                    .filters { f ->
                        f.filter(jwtFilter.apply(JwtAuthenticationFilter.Config()))
                    }
                    .uri("lb://WMS-TENANT-SERVICE")
            }
            // Phase D: reconciliation report endpoints.
            .route("tenant-service-reconciliation") { r ->
                r.path("/api/v1/reconciliation/**")
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