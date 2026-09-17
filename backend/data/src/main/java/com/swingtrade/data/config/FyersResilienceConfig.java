package com.swingtrade.data.config;

import com.swingtrade.data.repository.FyersSymbolRepository;
import com.swingtrade.data.service.FyersAuthService;
import com.swingtrade.data.service.FyersServiceClient;
import com.swingtrade.data.service.FyersSymbolMasterService;
import com.swingtrade.data.service.MarketDataClientProvider;
import com.swingtrade.core.metrics.DataIngestionMetrics;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@Configuration
public class FyersResilienceConfig {

    private final CircuitBreaker fyersAuthCircuitBreaker;
    private final Bulkhead fyersAuthBulkhead;
    private final CircuitBreaker fyersCircuitBreaker;
    private final Bulkhead fyersBulkhead;
    private FyersAuthService fyersAuthService;
    private FyersServiceClient fyersServiceClient;

    public FyersResilienceConfig(@Qualifier("fyersAuth") CircuitBreaker fyersAuthCircuitBreaker,
                                 @Qualifier("fyersAuth") Bulkhead fyersAuthBulkhead,
                                 @Qualifier("fyers") CircuitBreaker fyersCircuitBreaker,
                                 @Qualifier("fyers") Bulkhead fyersBulkhead) {
        this.fyersAuthCircuitBreaker = fyersAuthCircuitBreaker;
        this.fyersAuthBulkhead = fyersAuthBulkhead;
        this.fyersCircuitBreaker = fyersCircuitBreaker;
        this.fyersBulkhead = fyersBulkhead;
    }

    @Bean
    public FyersSymbolMasterService fyersSymbolMasterService(WebClient.Builder webClientBuilder,
                                                             FyersSymbolRepository symbolRepository,
                                                             @Lazy MarketDataClientProvider marketDataClientProvider) {
        return new FyersSymbolMasterService(webClientBuilder, symbolRepository, marketDataClientProvider);
    }

    @Bean
    public FyersAuthService fyersAuthService(FyersConfig fyersConfig, Builder webClientBuilder) {
        FyersAuthService service = new FyersAuthService(fyersConfig, webClientBuilder);
        service.setResilience4j(fyersAuthCircuitBreaker, fyersAuthBulkhead);
        this.fyersAuthService = service;
        return service;
    }

    @Bean
    public FyersServiceClient fyersServiceClient(Builder webClientBuilder,
                                                  FyersAuthService fyersAuthService,
                                                  FyersSymbolMasterService symbolMaster,
                                                  DataIngestionMetrics ingestionMetrics) {
        FyersServiceClient client = new FyersServiceClient(webClientBuilder, fyersAuthService, symbolMaster);
        client.setResilience4j(fyersCircuitBreaker, fyersBulkhead);
        client.setIngestionMetrics(ingestionMetrics);
        this.fyersServiceClient = client;
        return client;
    }

    @PostConstruct
    public void inject() {
        // Beans created above; this is kept for any additional init logic.
    }
}
