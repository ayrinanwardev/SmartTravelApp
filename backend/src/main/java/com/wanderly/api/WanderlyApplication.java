package com.wanderly.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WanderlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(WanderlyApplication.class, args);
    }
}
