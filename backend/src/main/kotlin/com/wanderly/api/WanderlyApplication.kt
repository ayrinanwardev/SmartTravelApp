package com.wanderly.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class WanderlyApplication

fun main(args: Array<String>) {
    runApplication<WanderlyApplication>(*args)
}
