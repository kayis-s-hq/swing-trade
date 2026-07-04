package com.swingtrade.data.config;

import com.swingtrade.data.client.YahooFinanceClient;
import com.swingtrade.data.service.FyersAuthService;
import com.swingtrade.data.service.FyersServiceClient;
import com.swingtrade.data.service.MarketDataClient;
import com.swingtrade.data.service.NseInstrumentService;
import com.swingtrade.data.service.UpstoxAuthService;
import com.swingtrade.data.service.UpstoxServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class MarketDataClientConfig {

    @Bean
    public MarketDataClient yahooFinanceClient() {
        return new YahooFinanceClient();
    }

    @Bean
    @Profile("upstox")
    public MarketDataClient upstoxServiceClient(
            WebClient.Builder webClientBuilder,
            UpstoxAuthService authService,
            NseInstrumentService instrumentService) {
        return new UpstoxServiceClient(webClientBuilder, authService, instrumentService);
    }

    @Bean
    public MarketDataClient fyersServiceClient(
            WebClient.Builder webClientBuilder,
            FyersAuthService authService) {
        return new FyersServiceClient(webClientBuilder, authService);
    }
}
