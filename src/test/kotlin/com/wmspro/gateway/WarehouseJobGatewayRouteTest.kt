package com.wmspro.gateway

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.RouteLocator

@SpringBootTest(properties = [
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
])
class WarehouseJobGatewayRouteTest {
    @Autowired lateinit var routeLocator: RouteLocator

    @Test
    fun `warehouse job staff route exists and internal claim routes are not exposed`() {
        val routes = routeLocator.routes.collectList().block().orEmpty()
        assertTrue(routes.any { it.id == "tenant-service-warehouse-jobs" })
        assertFalse(routes.any { it.id.contains("internal", ignoreCase = true) })
    }
}
