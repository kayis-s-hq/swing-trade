package com.swingtrade.data.config;

import com.swingtrade.data.client.UpstoxApiClient;
import com.swingtrade.data.client.YahooFinanceClient;
import com.swingtrade.data.service.MarketDataClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for Market Data Client.
 *
 * Default: Yahoo Finance (free, no authentication required)
 * Production: Upstox (requires API credentials)
 *
 * Usage:
 *   # Yahoo Finance (default)
 *   java -jar target/api-1.0.0.jar
 *
 *   # Upstox (requires credentials)
 *   java -DMARKET_DATA_CLIENT=upstox \
 *        -DUPSTOX_API_KEY=your_api_key \
 *        -DUPSTOX_ACCESS_TOKEN=your_token \
 *        -jar target/api-1.0.0.jar
 */
@Configuration
public class MarketDataClientConfig {

    /**
     * Default bean: Yahoo Finance client (free, no auth required).
     * Used for development, backfill, and basic data ingestion.
     */
    @Bean
    @Primary
    @Profile("!upstox")
    public MarketDataClient yahooFinanceClient() {
        return new YahooFinanceClient();
    }

    /**
     * Upstox API client (requires API credentials).
     * For production paper trading with real market data.
     */
    @Bean
    @Profile("upstox")
    public MarketDataClient upstoxApiClient(UpstoxConfig upstoxConfig) {
        return new UpstoxApiClient(upstoxConfig);
    }
}
