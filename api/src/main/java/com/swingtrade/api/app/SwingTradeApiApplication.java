package com.swingtrade.api.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for Swing Trade API module
 * Enables Spring Boot auto-configuration and component scanning
 */
@SpringBootApplication
public class SwingTradeApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwingTradeApiApplication.class, args);
    }
}
