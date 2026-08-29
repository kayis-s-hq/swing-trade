package com.swingtrade.api.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class Resilience4jConfig {

    // Circuit breaker config — shared defaults
    private CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreException(throwable -> throwable instanceof InterruptedException)
                .build();
    }

    // Circuit breakers — per-client configs (kept for backward compat; individual beans are preferred)
    @Bean
    public Map<String, CircuitBreaker> circuitBreakers() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultCircuitBreakerConfig());

        Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();
        breakers.put("yahoo", registry.circuitBreaker("yahoo",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(40)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build()));
        breakers.put("fyers", registry.circuitBreaker("fyers",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(20)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build()));
        breakers.put("fyersAuth", registry.circuitBreaker("fyersAuth",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(30)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build()));
        breakers.put("llm", registry.circuitBreaker("llm",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(40)
                        .slidingWindowSize(20)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .slowCallRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(5))
                        .build()));
        breakers.put("news", registry.circuitBreaker("news",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(60)
                        .slidingWindowSize(20)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build()));
        breakers.put("discord", registry.circuitBreaker("discord",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(70)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build()));
        return breakers;
    }

    // Individual circuit breaker beans with qualifiers
    @Bean
    @Qualifier("yahoo")
    public CircuitBreaker yahooCircuitBreaker() {
        return CircuitBreaker.of("yahoo",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(40)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build());
    }

    @Bean
    @Qualifier("fyers")
    public CircuitBreaker fyersCircuitBreaker() {
        return CircuitBreaker.of("fyers",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(20)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build());
    }

    @Bean
    @Qualifier("fyersAuth")
    public CircuitBreaker fyersAuthCircuitBreaker() {
        return CircuitBreaker.of("fyersAuth",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(30)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build());
    }

    @Bean
    @Qualifier("llm")
    public CircuitBreaker llmCircuitBreaker() {
        return CircuitBreaker.of("llm",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(40)
                        .slidingWindowSize(20)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .slowCallRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(5))
                        .build());
    }

    @Bean
    @Qualifier("news")
    public CircuitBreaker newsCircuitBreaker() {
        return CircuitBreaker.of("news",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(60)
                        .slidingWindowSize(20)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build());
    }

    @Bean
    @Qualifier("discord")
    public CircuitBreaker discordCircuitBreaker() {
        return CircuitBreaker.of("discord",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(70)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build());
    }

    // Bulkheads — per-client concurrency limits (kept for backward compat; individual beans are preferred)
    @Bean
    public Map<String, Bulkhead> bulkheads() {
        BulkheadConfig defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ZERO)
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(defaultConfig);

        Map<String, Bulkhead> bulkheads = new LinkedHashMap<>();
        bulkheads.put("yahoo", registry.bulkhead("yahoo",
                BulkheadConfig.custom().maxConcurrentCalls(5).build()));
        bulkheads.put("fyers", registry.bulkhead("fyers",
                BulkheadConfig.custom().maxConcurrentCalls(5).build()));
        bulkheads.put("fyersAuth", registry.bulkhead("fyersAuth",
                BulkheadConfig.custom().maxConcurrentCalls(3).build()));
        bulkheads.put("llm", registry.bulkhead("llm",
                BulkheadConfig.custom().maxConcurrentCalls(3).build()));
        bulkheads.put("news", registry.bulkhead("news",
                BulkheadConfig.custom().maxConcurrentCalls(7).build()));
        bulkheads.put("discord", registry.bulkhead("discord",
                BulkheadConfig.custom().maxConcurrentCalls(2).build()));
        return bulkheads;
    }

    // Individual bulkhead beans with qualifiers
    @Bean
    @Qualifier("yahoo")
    public Bulkhead yahooBulkhead() {
        return Bulkhead.of("yahoo", BulkheadConfig.custom().maxConcurrentCalls(5).build());
    }

    @Bean
    @Qualifier("fyers")
    public Bulkhead fyersBulkhead() {
        return Bulkhead.of("fyers", BulkheadConfig.custom().maxConcurrentCalls(5).build());
    }

    @Bean
    @Qualifier("fyersAuth")
    public Bulkhead fyersAuthBulkhead() {
        return Bulkhead.of("fyersAuth", BulkheadConfig.custom().maxConcurrentCalls(3).build());
    }

    @Bean
    @Qualifier("llm")
    public Bulkhead llmBulkhead() {
        return Bulkhead.of("llm", BulkheadConfig.custom().maxConcurrentCalls(3).build());
    }

    @Bean
    @Qualifier("news")
    public Bulkhead newsBulkhead() {
        return Bulkhead.of("news", BulkheadConfig.custom().maxConcurrentCalls(7).build());
    }

    @Bean
    @Qualifier("discord")
    public Bulkhead discordBulkhead() {
        return Bulkhead.of("discord", BulkheadConfig.custom().maxConcurrentCalls(2).build());
    }

    // Time limiters — per-client timeouts (kept for backward compat; individual beans are preferred)
    @Bean
    public Map<String, TimeLimiter> timeLimiters() {
        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(defaultConfig);

        Map<String, TimeLimiter> limiters = new LinkedHashMap<>();
        limiters.put("yahoo", registry.timeLimiter("yahoo",
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build()));
        limiters.put("fyers", registry.timeLimiter("fyers",
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build()));
        limiters.put("fyersAuth", registry.timeLimiter("fyersAuth",
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10)).build()));
        limiters.put("llm", registry.timeLimiter("llm",
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(60)).build()));
        limiters.put("news", registry.timeLimiter("news",
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build()));
        limiters.put("discord", registry.timeLimiter("discord",
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build()));
        return limiters;
    }

    // Individual time limiter beans with qualifiers
    @Bean
    @Qualifier("yahoo")
    public TimeLimiter yahooTimeLimiter() {
        return TimeLimiter.of("yahoo", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build());
    }

    @Bean
    @Qualifier("fyers")
    public TimeLimiter fyersTimeLimiter() {
        return TimeLimiter.of("fyers", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build());
    }

    @Bean
    @Qualifier("fyersAuth")
    public TimeLimiter fyersAuthTimeLimiter() {
        return TimeLimiter.of("fyersAuth", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10)).build());
    }

    @Bean
    @Qualifier("llm")
    public TimeLimiter llmTimeLimiter() {
        return TimeLimiter.of("llm", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(60)).build());
    }

    @Bean
    @Qualifier("news")
    public TimeLimiter newsTimeLimiter() {
        return TimeLimiter.of("news", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build());
    }

    @Bean
    @Qualifier("discord")
    public TimeLimiter discordTimeLimiter() {
        return TimeLimiter.of("discord", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build());
    }
}