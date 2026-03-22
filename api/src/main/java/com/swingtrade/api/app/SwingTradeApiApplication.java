package com.swingtrade.api.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main application class for Swing Trade API module
 * Enables Spring Boot auto-configuration and component scanning
 */
@SpringBootApplication
@EnableCaching
public class SwingTradeApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwingTradeApiApplication.class, args);
    }
}
