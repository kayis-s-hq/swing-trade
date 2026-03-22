package com.swingtrade.api.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Swing Trade API module
 * Enables Spring Boot auto-configuration and component scanning
 * Enables scheduled tasks for weekly sector digest and other periodic jobs
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class SwingTradeApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwingTradeApiApplication.class, args);
    }
}
