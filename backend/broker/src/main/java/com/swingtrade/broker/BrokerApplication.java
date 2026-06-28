package com.swingtrade.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the broker module.
 * This class enables Spring Boot auto-configuration for the broker module.
 */
@SpringBootApplication
public class BrokerApplication {
    
    /**
     * Entry point for the broker module application.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BrokerApplication.class, args);
    }
}
