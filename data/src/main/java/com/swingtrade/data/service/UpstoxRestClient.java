package com.swingtrade.data.service;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.data.service.CandleData;
import com.swingtrade.data.service.InstrumentDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST client for fetching market data from Upstox API.
 * Implements MarketDataClient interface with reactive WebFlux client.
 * Handles authentication, rate limiting, and data transformation.
 */
@Component
public class UpstoxRestClient implements MarketDataClient {

    private static final Logger logger = LoggerFactory.getLogger(UpstoxRestClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiSecret;
    private final String accessToken;
    private final String baseUri;

    // Token management
    private volatile String cachedAccessToken;
    private volatile long tokenExpiryTime;

    private static final int TOKEN_REFRESH_THRESHOLD = 60000; // Refresh 1 min before expiry
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    /**
     * Constructs Upstox REST client with injected dependencies.
     *
     * @param webClient the WebClient for HTTP requests
     * @param objectMapper ObjectMapper for JSON processing
     * @param apiKey Upstox API key
     * @param apiSecret Upstox API secret
     * @param accessToken pre-fetched access token (optional)
     */
    public UpstoxRestClient(
            WebClient.Builder webClientBuilder,
            @Value("${upstox.api.key}") String apiKey,
            @Value("${upstox.api.secret}") String apiSecret,
            @Value("${upstox.access.token:}") String accessToken) {

        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.accessToken = accessToken;

        this.webClient = webClientBuilder
                .defaultHeader("Accept", "application/json")
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.setLocale(java.util.Locale.US);

        // Set base URI based on environment (production vs demo)
        this.baseUri = "https://api.upstox.com";

        // If access token is provided, cache it
        if (accessToken != null && !accessToken.isEmpty()) {
            this.cachedAccessToken = accessToken;
            this.tokenExpiryTime = Long.MAX_VALUE;
        }
    }

    /**
     * Authenticates with Upstox API using API key and secret.
     * Returns access token for subsequent requests.
     *
     * @return Mono containing access token
     */
    private Mono<AuthResponse> authenticate() {
        logger.info("Authenticating with Upstox API...");

        return webClient.post()
                .uri("https://api.upstox.com/v2/login/access_token")
                .bodyValue(Map.of(
                        "api_key", apiKey,
                        "secret", apiSecret
                ))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> {
                    logger.error("Authentication failed: {}", response.statusCode());
                    return response.bodyToMono(String.class)
                            .map(body -> new RuntimeException("Authentication failed: " + body));
                })
                .bodyToMono(AuthResponse.class);
    }

    /**
     * Ensures valid cached access token, refreshing if necessary.
     *
     * @return Mono containing access token
     */
    private Mono<String> getAccessToken() {
        if (cachedAccessToken != null &&
                System.currentTimeMillis() + TOKEN_REFRESH_THRESHOLD < tokenExpiryTime) {
            return Mono.just(cachedAccessToken);
        }

        return authenticate()
                .map(authResponse -> {
                    this.cachedAccessToken = authResponse.getData().getAccess_token();
                    // Set expiry to 24 hours from now
                    this.tokenExpiryTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24);
                    return this.cachedAccessToken;
                });
    }

    /**
     * Makes authenticated request with retry logic.
     *
     * @param uri the request URI
     * @param responseType the expected response type
     * @param <T> the response type
     * @return Mono containing response
     */
    private <T> Mono<T> getWithAuth(String uri, Class<T> responseType) {
        return getWithAuth(uri, responseType, 0);
    }

    /**
     * Makes authenticated request with retry logic.
     *
     * @param uri the request URI
     * @param responseType the expected response type
     * @param retryCount current retry count
     * @param <T> the response type
     * @return Mono containing response
     */
    private <T> Mono<T> getWithAuth(String uri, Class<T> responseType, int retryCount) {
        return getAccessToken()
                .flatMap(token ->
                    webClient.get()
                            .uri(uri)
                            .header("Authorization", "Bearer " + token)
                            .retrieve()
                            .bodyToMono(responseType)
                )
                .onErrorResume(th -> {
                    if (retryCount < MAX_RETRIES &&
                            (th instanceof RuntimeException &&
                                    th.getMessage().contains("Authentication failed"))) {
                        logger.warn("Authentication error, retrying... (attempt {})", retryCount + 1);
                        return retryDelay(retryCount + 1)
                                .flatMap(v -> getWithAuth(uri, responseType, retryCount + 1));
                    }
                    logger.error("Request failed: {}", th.getMessage());
                    return Mono.empty();
                })
                .doOnSuccess(response -> logger.debug("Request successful: {}", uri))
                .doOnError(error -> logger.error("Request error: {}", error.getMessage()));
    }

    /**
     * Helper method to delay execution for retries.
     *
     * @param retryCount current retry count
     * @return Mono that completes after delay
     */
    private <T> Mono<T> retryDelay(int retryCount) {
        int delay = RETRY_DELAY_MS * (retryCount + 1);
        return Mono.delay(java.time.Duration.ofMillis(delay))
                .map(defaultValue -> null);
    }

    @Override
    public CandleData fetchCandle(String symbol, LocalDate date) {
        logger.debug("Fetching candle for {} on {}", symbol, date);

        String formattedDate = date.toString();
        String uri = String.format(
                "%s/v2/market-data/ohlc/NQ_EQ/%s/candles/1-day/%s/%s",
                baseUri, symbol, formattedDate, formattedDate);

        return getWithAuth(uri, OhlcResponse.class)
                .map(response -> {
                    if (response != null && response.getData() != null &&
                            !response.getData().getCandles().isEmpty()) {
                        return response.getData().getCandles().stream()
                                .filter(c -> c.getDate().filter(d -> d.equals(date)).isPresent())
                                .findFirst()
                                .map(this::toCandleData)
                                .orElse(null);
                    }
                    return null;
                })
                .block();
    }

    @Override
    public Iterable<CandleData> fetchCandles(String symbol, LocalDate startDate, LocalDate endDate) {
        logger.debug("Fetching candles for {} from {} to {}", symbol, startDate, endDate);

        String formattedStartDate = startDate.toString();
        String formattedEndDate = endDate.toString();
        String uri = String.format(
                "%s/v2/market-data/ohlc/NQ_EQ/%s/candles/1-day/%s/%s",
                baseUri, symbol, formattedStartDate, formattedEndDate);

        return getWithAuth(uri, OhlcResponse.class)
                .map(response -> {
                    if (response == null || response.getData() == null) {
                        return List.<CandleData>of();
                    }
                    return response.getData().getCandles().stream()
                            .map(this::toCandleData)
                            .collect(Collectors.toList());
                })
                .block();
    }

    @Override
    public CandleData fetchLatestCandle(String symbol) {
        logger.debug("Fetching latest candle for {}", symbol);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String uri = String.format(
                "%s/v2/market-data/ohlc/NQ_EQ/%s/candles/1-day/%s/%s",
                baseUri, symbol, today.minusDays(10).toString(), today.toString());

        return getWithAuth(uri, OhlcResponse.class)
                .map(response -> {
                    if (response != null && response.getData() != null) {
                        var candles = response.getData().getCandles();
                        if (!candles.isEmpty()) {
                            return toCandleData(candles.get(candles.size() - 1)); // Last candle
                        }
                    }
                    return null;
                })
                .block();
    }

    @Override
    public InstrumentDetails fetchInstrumentDetails(String symbol) {
        logger.debug("Fetching instrument details for {}", symbol);

        String uri = String.format(
                "%s/v2/instruments/NQ_EQ/%s",
                baseUri, symbol);

        return getWithAuth(uri, InstrumentResponse.class)
                .map(response -> {
                    if (response != null && response.getData() != null &&
                            !response.getData().isEmpty()) {
                        return toInstrumentDetails(response.getData().get(0));
                    }
                    return null;
                })
                .block();
    }

    @Override
    public Iterable<String> fetchAllStockSymbols() {
        logger.debug("Fetching all stock symbols");

        // Fetch all instruments for NQ_EQ segment
        String uri = String.format("%s/v2/instruments/NQ_EQ", baseUri);

        return getWithAuth(uri, InstrumentsResponse.class)
                .map(response -> {
                    if (response != null && response.getData() != null) {
                        return response.getData().stream()
                                .map(this::toInstrumentDetails)
                                .map(InstrumentDetails::symbol)
                                .collect(Collectors.toList());
                    }
                    return List.<String>of();
                })
                .block();
    }

    @Override
    public boolean isConnected() {
        try {
            String uri = String.format("%s/v2/user/profile", baseUri);
            var response = getWithAuth(uri, UserProfileResponse.class).block();
            return response != null && response.isSuccess();
        } catch (Exception e) {
            logger.warn("Connection check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Converts OhlcCandleData to CandleData DTO.
     *
     * @param ohlc the OhlcCandleData
     * @return CandleData DTO
     */
    private CandleData toCandleData(OhlcResponse.OhlcCandleData ohlc) {
        return CandleData.of(
                ohlc.getSymbol(),
                ohlc.getDate().orElse(null),
                ohlc.getOpen(),
                ohlc.getHigh(),
                ohlc.getLow(),
                ohlc.getClose(),
                ohlc.getVolume()
        );
    }

    /**
     * Converts Instrument to InstrumentDetails DTO.
     *
     * @param instrument the Instrument
     * @return InstrumentDetails DTO
     */
    private InstrumentDetails toInstrumentDetails(Instrument instrument) {
        return InstrumentDetails.of(
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getExchange(),
                instrument.getInstrumentType(),
                instrument.getLotSize(),
                instrument.getTickSize(),
                instrument.getIsin()
        );
    }

    // ========== JSON DTO Classes ==========

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AuthResponse {
        @JsonProperty("data")
        private AuthData data;

        public AuthData getData() {
            return data;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class AuthData {
            @JsonProperty("access_token")
            private String access_token;

            public String getAccess_token() {
                return access_token;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OhlcResponse {
        @JsonProperty("data")
        private OhlcData data;

        public OhlcData getData() {
            return data;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class OhlcData {
            @JsonProperty("candles")
            private List<OhlcCandleData> candles;

            public List<OhlcCandleData> getCandles() {
                return candles;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class OhlcCandleData {
            @JsonProperty("symbol")
            private String symbol;

            @JsonProperty("date")
            private Optional<LocalDate> date;

            @JsonProperty("open")
            private BigDecimal open;

            @JsonProperty("high")
            private BigDecimal high;

            @JsonProperty("low")
            private BigDecimal low;

            @JsonProperty("close")
            private BigDecimal close;

            @JsonProperty("volume")
            private long volume;

            // Getters
            public String getSymbol() { return symbol; }
            public Optional<LocalDate> getDate() { return date; }
            public BigDecimal getOpen() { return open; }
            public BigDecimal getHigh() { return high; }
            public BigDecimal getLow() { return low; }
            public BigDecimal getClose() { return close; }
            public long getVolume() { return volume; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class InstrumentResponse {
        @JsonProperty("data")
        private List<Instrument> data;

        public List<Instrument> getData() {
            return data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class InstrumentsResponse {
        @JsonProperty("data")
        private List<Instrument> data;

        public List<Instrument> getData() {
            return data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Instrument {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("name")
        private String name;

        @JsonProperty("exchange")
        private String exchange;

        @JsonProperty("instrument_type")
        private String instrumentType;

        @JsonProperty("lot_size")
        private Integer lotSize;

        @JsonProperty("tick_size")
        private BigDecimal tickSize;

        @JsonProperty("isin")
        private String isin;

        // Getters
        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public String getExchange() { return exchange; }
        public String getInstrumentType() { return instrumentType; }
        public Integer getLotSize() { return lotSize; }
        public BigDecimal getTickSize() { return tickSize; }
        public String getIsin() { return isin; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class UserProfileResponse {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("data")
        private UserProfileData data;

        public boolean isSuccess() {
            return success;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class UserProfileData {
            @JsonProperty("user_id")
            private String userId;

            @JsonProperty("name")
            private String name;

            public String getUserId() { return userId; }
            public String getName() { return name; }
        }
    }
}
