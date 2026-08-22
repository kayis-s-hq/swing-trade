package com.swingtrade.data.config;

import com.swingtrade.data.service.FyersAuthService;
import com.swingtrade.data.service.FyersServiceClient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class FyersResilienceConfig {

    private final FyersAuthService fyersAuthService;
    private final FyersServiceClient fyersServiceClient;
    private final CircuitBreaker fyersAuthCircuitBreaker;
    private final Bulkhead fyersAuthBulkhead;
    private final CircuitBreaker fyersCircuitBreaker;
    private final Bulkhead fyersBulkhead;

    public FyersResilienceConfig(FyersAuthService fyersAuthService,
                                 FyersServiceClient fyersServiceClient,
                                 @Qualifier("fyersAuth") CircuitBreaker fyersAuthCircuitBreaker,
                                 @Qualifier("fyersAuth") Bulkhead fyersAuthBulkhead,
                                 @Qualifier("fyers") CircuitBreaker fyersCircuitBreaker,
                                 @Qualifier("fyers") Bulkhead fyersBulkhead) {
        this.fyersAuthService = fyersAuthService;
        this.fyersServiceClient = fyersServiceClient;
        this.fyersAuthCircuitBreaker = fyersAuthCircuitBreaker;
        this.fyersAuthBulkhead = fyersAuthBulkhead;
        this.fyersCircuitBreaker = fyersCircuitBreaker;
        this.fyersBulkhead = fyersBulkhead;
    }

    @PostConstruct
    public void inject() {
        fyersAuthService.setResilience4j(fyersAuthCircuitBreaker, fyersAuthBulkhead);
        fyersServiceClient.setResilience4j(fyersCircuitBreaker, fyersBulkhead);
    }
}