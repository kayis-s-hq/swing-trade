package com.swingtrade.data.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Profile("fyers")
public class FyersServiceClient implements MarketDataClient {

    private static final Logger logger = LoggerFactory.getLogger(FyersServiceClient.class);
    private static final String BASE_URL = "https://api-t1.fyers.in";

    private final WebClient webClient;
    private final FyersAuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FyersServiceClient(WebClient.Builder webClientBuilder, FyersAuthService authService) {
        this.webClient = webClientBuilder
            .baseUrl(BASE_URL)
            .defaultHeader("Accept", "application/json")
            .build();
        this.authService = authService;
    }

    @Override
    public CandleData fetchCandle(String symbol, LocalDate date) {
        List<CandleData> candles = fetchCandlesList(symbol, date, date);
        return candles.isEmpty() ? null : candles.get(0);
    }

    @Override
    public Iterable<CandleData> fetchCandles(String symbol, LocalDate startDate, LocalDate endDate) {
        return fetchCandlesList(symbol, startDate, endDate);
    }

    List<CandleData> fetchCandlesList(String symbol, LocalDate startDate, LocalDate endDate) {
        String token = authService.getAccessToken();
        if (token == null) {
            logger.error("Fyers access token is missing. Please complete authentication.");
            return Collections.emptyList();
        }

        String fyersSymbol = "NSE:" + symbol + "-EQ";
        String path = "/data/history?symbol=" + fyersSymbol + "&resolution=D&date%5Bfrom%5D=" + startDate + "&date%5Bto%5D=" + endDate;

        try {
            return executeWithRetry(symbol, path, token);
        } catch (Exception e) {
            logger.error("Failed to fetch candles from Fyers for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Executes the request and retries once with a refreshed token if a 401 is received.
     */
    private List<CandleData> executeWithRetry(String symbol, String path, String token) {
        try {
            String responseBody = webClient.get()
                .uri(path)
                .header("Authorization", "token " + token)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseCandles(symbol, responseBody);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                logger.warn("Token expired for {}. Refreshing and retrying...", symbol);
                authService.refreshToken();
                String newToken = authService.getAccessToken();
                if (newToken != null) {
                    try {
                        String responseBody = webClient.get()
                            .uri(path)
                            .header("Authorization", "token " + newToken)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();
                        return parseCandles(symbol, responseBody);
                    } catch (Exception ex) {
                        logger.error("Retry failed for {}: {}", symbol, ex.getMessage());
                        return Collections.emptyList();
                    }
                }
            }
            throw e;
        } catch (Exception e) {
            logger.error("Request failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<CandleData> parseCandles(String symbol, String responseBody) throws Exception {
        if (responseBody == null) return Collections.emptyList();

        JsonNode root = objectMapper.readTree(responseBody);
        if (!root.has("candles") || "error".equals(root.get("s").asText())) {
            logger.warn("Fyers API error for {}: {}", symbol, root.get("message"));
            return Collections.emptyList();
        }

        JsonNode candlesNode = root.get("candles");
        List<CandleData> candles = new ArrayList<>();
        
        for (JsonNode node : candlesNode) {
            long epochSeconds = node.get(0).asLong();
            LocalDate date = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(epochSeconds), 
                ZoneId.of("Asia/Kolkata")
            ).toLocalDate();
            
            BigDecimal open = new BigDecimal(node.get(1).asText());
            BigDecimal high = new BigDecimal(node.get(2).asText());
            BigDecimal low = new BigDecimal(node.get(3).asText());
            BigDecimal close = new BigDecimal(node.get(4).asText());
            long volume = node.get(5).asLong();
            
            candles.add(CandleData.of(symbol, date, open, high, low, close, volume));
        }
        
        return candles;
    }

    @Override
    public CandleData fetchLatestCandle(String symbol) {
        List<CandleData> candles = fetchCandlesList(symbol, LocalDate.now().minusDays(10), LocalDate.now());
        return candles.isEmpty() ? null : candles.get(candles.size() - 1);
    }

    @Override
    public InstrumentDetails fetchInstrumentDetails(String symbol) {
        return InstrumentDetails.of(symbol, symbol, "NSE_EQ", "EQ", 1, BigDecimal.valueOf(0.05), null);
    }

    @Override
    public ChartMeta fetchChartMeta(String symbol) {
        // Fyers doesn't provide a chart metadata endpoint.
        // Return null — use YahooFinanceClient for meta data.
        return null;
    }

    @Override
    public Iterable<String> fetchAllStockSymbols() {
        return Collections.emptyList();
    }

    @Override
    public boolean isConnected() {
        return authService.validateToken();
    }
}
