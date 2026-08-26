package com.swingtrade.data.config;

import com.swingtrade.data.client.YahooFinanceClient;
import com.swingtrade.data.service.MarketDataClient;
// Upstox is unconfigured — imports commented out along with the upstox bean below.
// import com.swingtrade.data.service.NseInstrumentService;
// import com.swingtrade.data.service.UpstoxAuthService;
// import com.swingtrade.data.service.UpstoxServiceClient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// import org.springframework.context.annotation.Profile;
// import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class MarketDataClientConfig {

    @Bean(name = "yahoo")
    public MarketDataClient yahooFinanceClient(
            @Value("${yahoo.api.base-url:https://query1.finance.yahoo.com}") String baseUrl,
            @Qualifier("yahoo") CircuitBreaker circuitBreaker,
            @Qualifier("yahoo") Bulkhead bulkhead,
            @Qualifier("yahoo") TimeLimiter timeLimiter) {
        return new YahooFinanceClient(baseUrl, new tools.jackson.databind.ObjectMapper(),
            java.time.Clock.systemUTC(), circuitBreaker, bulkhead, timeLimiter);
    }

    // Upstox is unconfigured — bean commented out (kept for future re-enablement).
    // @Bean(name = "upstox")
    // @Profile("upstox")
    // public MarketDataClient upstoxServiceClient(
    //         WebClient.Builder webClientBuilder,
    //         UpstoxAuthService authService,
    //         NseInstrumentService instrumentService) {
    //     return new UpstoxServiceClient(webClientBuilder, authService, instrumentService);
    // }
}