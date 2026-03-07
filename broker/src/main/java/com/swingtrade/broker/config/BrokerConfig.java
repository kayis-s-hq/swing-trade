package com.swingtrade.broker.config;

import com.swingtrade.broker.engine.PaperTradeEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Configuration class for the broker module.
 * Sets up the necessary beans for paper trading functionality.
 */
@Configuration
public class BrokerConfig {
    
    /**
     * Creates and configures the paper trade engine.
     * 
     * @return configured paper trade engine
     */
    @Bean
    public PaperTradeEngine paperTradeEngine() {
        // Default initial capital - this could be injected from properties
        return new PaperTradeEngine(new BigDecimal("100000.00"));
    }
}
