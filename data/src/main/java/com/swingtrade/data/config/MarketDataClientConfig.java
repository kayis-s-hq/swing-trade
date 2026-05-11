package com.swingtrade.data.config;

import com.swingtrade.data.client.YahooFinanceClient;
import com.swingtrade.data.service.MarketDataClient;
import com.swingtrade.data.service.NseInstrumentService;
import com.swingtrade.data.service.UpstoxAuthService;
import com.swingtrade.data.service.UpstoxServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class MarketDataClientConfig {

    @Bean
    @Primary
    @Profile("!upstox")
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
}
