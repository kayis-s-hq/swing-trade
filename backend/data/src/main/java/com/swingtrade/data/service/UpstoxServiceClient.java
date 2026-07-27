package com.swingtrade.data.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.Optional;

@Profile("upstox")
public class UpstoxServiceClient implements MarketDataClient {

    private static final Logger logger = LoggerFactory.getLogger(UpstoxServiceClient.class);
    private static final String BASE_URL = "https://api.upstox.com";

    private final WebClient webClient;
    private final UpstoxAuthService authService;
    private final NseInstrumentService instrumentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpstoxServiceClient(WebClient.Builder webClientBuilder,
                                UpstoxAuthService authService,
                                NseInstrumentService instrumentService) {
        this(webClientBuilder, authService, instrumentService, BASE_URL);
    }

    // Package-private constructor for testing with a custom base URL (MockWebServer)
    UpstoxServiceClient(WebClient.Builder webClientBuilder,
                        UpstoxAuthService authService,
                        NseInstrumentService instrumentService,
                        String baseUrl) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .build();
        this.authService = authService;
        this.instrumentService = instrumentService;
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

    // Exposed for testing
    List<CandleData> fetchCandlesList(String symbol, LocalDate startDate, LocalDate endDate) {
        Optional<String> encodedKey = instrumentService.getEncodedInstrumentKey(symbol);
        if (encodedKey.isEmpty()) {
            logger.warn("No instrument key found for symbol: {}", symbol);
            return Collections.emptyList();
        }

        String token = authService.getAccessToken();
        if (token == null) {
            logger.error("No access token available. Set UPSTOX_ACCESS_TOKEN or complete OAuth2 flow.");
            return Collections.emptyList();
        }

        String path = String.format("/v2/historical-candle/%s/day/%s/%s",
            encodedKey.get(), endDate, startDate);
        try {
            String responseBody = webClient.get()
                .uri(path)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (responseBody == null) return Collections.emptyList();

            HistoricalCandleResponse response = objectMapper.readValue(
                responseBody, HistoricalCandleResponse.class);

            if (response.getData() == null || response.getData().getCandles() == null) {
                return Collections.emptyList();
            }

            List<CandleData> candles = new ArrayList<>();
            for (List<Object> arr : response.data.candles) {
                candles.add(parseCandleArray(arr, symbol));
            }
            // API returns newest-first; reverse to chronological order
            Collections.reverse(candles);
            return candles;

        } catch (WebClientResponseException e) {
            logger.error("Upstox API error for {}: {} {}", symbol, e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch candles for {}: {}", symbol, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public CandleData fetchLatestCandle(String symbol) {
        List<CandleData> candles = fetchCandlesList(
            symbol, LocalDate.now().minusDays(10), LocalDate.now());
        return candles.isEmpty() ? null : candles.get(candles.size() - 1);
    }

    @Override
    public InstrumentDetails fetchInstrumentDetails(String symbol) {
        Optional<String> key = instrumentService.getEncodedInstrumentKey(symbol);
        if (key.isEmpty()) return null;
        return InstrumentDetails.of(symbol, symbol, "NSE_EQ", "EQ", 1, BigDecimal.valueOf(0.05), null);
    }

    @Override
    public ChartMeta fetchChartMeta(String symbol) {
        // Upstox doesn't provide a chart metadata endpoint.
        // Return null — use YahooFinanceClient for meta data.
        return null;
    }

    @Override
    public Iterable<String> fetchAllStockSymbols() {
        return instrumentService.getAllSymbols();
    }

    @Override
    public QuoteData fetchQuote(String symbol) {
        return null;
    }

    @Override
    public java.util.List<QuoteData> fetchQuotes(java.util.List<String> symbols) {
        return java.util.Collections.emptyList();
    }

    @Override
    public java.util.List<SearchResult> searchSymbols(String query) {
        return java.util.Collections.emptyList();
    }

    @Override
    public boolean isConnected() {
        return authService.validateToken();
    }

    private CandleData parseCandleArray(List<Object> arr, String symbol) {
        // [0]=timestamp, [1]=open, [2]=high, [3]=low, [4]=close, [5]=volume, [6]=oi
        LocalDate date = OffsetDateTime.parse((String) arr.get(0))
            .atZoneSameInstant(ZoneId.of("Asia/Kolkata"))
            .toLocalDate();
        BigDecimal open  = new BigDecimal(arr.get(1).toString());
        BigDecimal high  = new BigDecimal(arr.get(2).toString());
        BigDecimal low   = new BigDecimal(arr.get(3).toString());
        BigDecimal close = new BigDecimal(arr.get(4).toString());
        long volume      = ((Number) arr.get(5)).longValue();
        return CandleData.of(symbol, date, open, high, low, close, volume);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HistoricalCandleResponse {
        @JsonProperty("data") private CandleResponseData data;

        public CandleResponseData getData() { return data; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CandleResponseData {
        @JsonProperty("candles") private List<List<Object>> candles;

        public List<List<Object>> getCandles() { return candles; }
    }
}
