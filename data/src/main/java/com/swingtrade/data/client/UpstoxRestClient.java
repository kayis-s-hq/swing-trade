package com.swingtrade.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.data.config.UpstoxConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UpstoxRestClient {
    
    private final WebClient webClient;
    private final UpstoxConfig upstoxConfig;
    private final ObjectMapper objectMapper;
    
    public UpstoxRestClient(UpstoxConfig upstoxConfig) {
        this.upstoxConfig = upstoxConfig;
        this.objectMapper = new ObjectMapper();
        
        this.webClient = WebClient.builder()
                .baseUrl(upstoxConfig.getApiUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + upstoxConfig.getAccessToken())
                .build();
    }
    
    public Mono<JsonNode> getCandles(String symbol, String interval, LocalDate fromDate, LocalDate toDate) {
        // Format dates according to Upstox API requirements
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedFromDate = fromDate.format(formatter);
        String formattedToDate = toDate.format(formatter);
        
        // Construct the URL path based on Upstox API specification
        String urlPath = String.format("/instruments/candles/%s/%s/%s/%s", 
            symbol, interval, formattedFromDate, formattedToDate);
        
        return webClient.get()
                .uri(urlPath)
                .retrieve()
                .onStatus(status -> status.value() == 401, response -> {
                    // Handle authentication errors
                    return Mono.error(new RuntimeException("Authentication failed"));
                })
                .onStatus(status -> status.value() >= 400, response -> {
                    // Handle other HTTP errors
                    return Mono.error(new RuntimeException("HTTP Error: " + response.statusCode()));
                })
                .bodyToMono(String.class)
                .map(responseBody -> {
                    try {
                        return objectMapper.readTree(responseBody);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse JSON response", e);
                    }
                })
                .retryWhen(
                    reactor.util.retry.Retry.backoff(
                        upstoxConfig.getMaxRetries(), 
                        java.time.Duration.ofMillis(upstoxConfig.getRetryDelayMillis())
                    )
                );
    }
    
    public Mono<JsonNode> getInstrumentList() {
        return webClient.get()
                .uri("/instruments")
                .retrieve()
                .onStatus(status -> status.value() == 401, response -> {
                    return Mono.error(new RuntimeException("Authentication failed"));
                })
                .onStatus(status -> status.value() >= 400, response -> {
                    return Mono.error(new RuntimeException("HTTP Error: " + response.statusCode()));
                })
                .bodyToMono(String.class)
                .map(responseBody -> {
                    try {
                        return objectMapper.readTree(responseBody);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse JSON response", e);
                    }
                })
                .retryWhen(
                    reactor.util.retry.Retry.backoff(
                        upstoxConfig.getMaxRetries(), 
                        java.time.Duration.ofMillis(upstoxConfig.getRetryDelayMillis())
                    )
                );
    }
}
