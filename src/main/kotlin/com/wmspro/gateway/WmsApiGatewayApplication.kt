package com.wmspro.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WmsApiGatewayApplication

fun main(args: Array<String>) {
	runApplication<WmsApiGatewayApplication>(*args)
}
