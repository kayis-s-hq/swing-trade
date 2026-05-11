package com.swingtrade.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.data.config.UpstoxConfig;
import com.swingtrade.data.service.CandleData;
import com.swingtrade.data.service.InstrumentDetails;
import com.swingtrade.data.service.MarketDataClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UpstoxApiClient implements MarketDataClient {

    private final WebClient webClient;
    private final UpstoxConfig upstoxConfig;
    private final ObjectMapper objectMapper;

    public UpstoxApiClient(UpstoxConfig upstoxConfig) {
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

    @Override
    public CandleData fetchCandle(String symbol, LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = date.format(formatter);

        try {
            JsonNode response = getCandles(symbol, "day", date, date).block();
            if (response == null || !response.has("data")) {
                return null;
            }

            JsonNode dataNode = response.get("data");
            if (dataNode.isArray() && dataNode.size() > 0) {
                JsonNode candle = dataNode.get(0);
                return parseCandle(candle, symbol);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Iterable<CandleData> fetchCandles(String symbol, LocalDate startDate, LocalDate endDate) {
        List<CandleData> candles = new ArrayList<>();
        try {
            JsonNode response = getCandles(symbol, "day", startDate, endDate).block();
            if (response == null || !response.has("data")) {
                return candles;
            }

            JsonNode dataNode = response.get("data");
            if (dataNode.isArray()) {
                for (JsonNode candleNode : dataNode) {
                    CandleData candle = parseCandle(candleNode, symbol);
                    if (candle != null) {
                        candles.add(candle);
                    }
                }
            }
        } catch (Exception e) {
            // Return empty list on error
        }
        return candles;
    }

    @Override
    public CandleData fetchLatestCandle(String symbol) {
        // Fallback to API - fetch last 30 days
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);
        JsonNode response = getCandles(symbol, "day", lastMonth, today).block();

        if (response != null && response.has("data")) {
            JsonNode dataNode = response.get("data");
            if (dataNode.isArray() && dataNode.size() > 0) {
                return parseCandle(dataNode.get(dataNode.size() - 1), symbol);
            }
        }
        return null;
    }

    @Override
    public InstrumentDetails fetchInstrumentDetails(String symbol) {
        try {
            JsonNode response = getInstrumentList().block();
            if (response == null || !response.has("data")) {
                return null;
            }

            JsonNode dataNode = response.get("data");
            if (dataNode.isArray()) {
                for (JsonNode instrument : dataNode) {
                    String instSymbol = instrument.get("symbol").asText();
                    if (instSymbol.equals(symbol)) {
                        return parseInstrumentDetails(instrument, symbol);
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    @Override
    public Iterable<String> fetchAllStockSymbols() {
        Set<String> symbols = new HashSet<>();
        try {
            JsonNode response = getInstrumentList().block();
            if (response != null && response.has("data")) {
                JsonNode dataNode = response.get("data");
                if (dataNode.isArray()) {
                    for (JsonNode instrument : dataNode) {
                        String symbol = instrument.get("symbol").asText();
                        if (!symbol.isEmpty()) {
                            symbols.add(symbol);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Return empty set on error
        }
        return symbols;
    }

    @Override
    public boolean isConnected() {
        try {
            // Try a simple API call to check connection
            JsonNode response = getInstrumentList().block(java.time.Duration.ofSeconds(5));
            return response != null && response.has("data");
        } catch (Exception e) {
            return false;
        }
    }

    private CandleData parseCandle(JsonNode node, String symbol) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            LocalDate date = LocalDate.parse(node.get("timestamp").asText(), formatter);

            return CandleData.of(
                symbol,
                date,
                new BigDecimal(node.get("o").asText()),
                new BigDecimal(node.get("h").asText()),
                new BigDecimal(node.get("l").asText()),
                new BigDecimal(node.get("c").asText()),
                node.get("v").asLong()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private InstrumentDetails parseInstrumentDetails(JsonNode node, String symbol) {
        try {
            return InstrumentDetails.of(
                symbol,
                node.get("name").asText(),
                node.get("exchangeSegment").asText(),
                node.get("instrumentType").asText(),
                node.has("lotSize") ? node.get("lotSize").asInt() : null,
                node.has("tickSize") ? new BigDecimal(node.get("tickSize").asText()) : null,
                node.has("isin") ? node.get("isin").asText() : null
            );
        } catch (Exception e) {
            return null;
        }
    }
}
