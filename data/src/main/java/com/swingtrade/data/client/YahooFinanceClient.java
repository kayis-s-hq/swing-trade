package com.swingtrade.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.data.service.CandleData;
import com.swingtrade.data.service.InstrumentDetails;
import com.swingtrade.data.service.MarketDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Yahoo Finance API client for fetching OHLCV market data.
 * Uses Yahoo Finance's public API to retrieve historical stock data.
 *
 * Note: This uses the public yfinance API which is free and doesn't require
 * authentication. It's suitable for development and backfill scenarios.
 */
@Service
public class YahooFinanceClient implements MarketDataClient {

    private static final Logger logger = LoggerFactory.getLogger(YahooFinanceClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Yahoo Finance API endpoints
    private static final String YAHOO_FINANCE_API = "https://query1.finance.yahoo.com";
    private static final String V8_API_PATH = "/v8/finance/chart";

    public YahooFinanceClient() {
        this.objectMapper = new ObjectMapper();

        this.webClient = WebClient.builder()
                .baseUrl(YAHOO_FINANCE_API)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
    }

    /**
     * Fetches a single day's OHLCV data for a stock from Yahoo Finance.
     *
     * @param symbol the stock symbol (e.g., "RELIANCE.NS" for NSE)
     * @param date the trading date
     * @return candle data or null if not found
     */
    @Override
    public CandleData fetchCandle(String symbol, LocalDate date) {
        try {
            // Yahoo Finance uses symbols like "RELIANCE.NS" for NSE
            String yfinanceSymbol = formatSymbolForYahoo(symbol);

            long timestamp = date.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
            long nextDay = date.plusDays(1).atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);

            String url = String.format("%s%s/%s?period1=%d&period2=%d&interval=1d&events=history",
                    YAHOO_FINANCE_API, V8_API_PATH, yfinanceSymbol, timestamp, nextDay);

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, r -> Mono.empty())
                    .onStatus(status -> status.value() >= 400, r -> Mono.empty())
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isEmpty()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result");

            if (!result.isArray() || result.size() == 0) {
                return null;
            }

            JsonNode quotes = result.get(0).path("indicators").path("quote").get(0);
            JsonNode timestamps = result.get(0).path("timestamp");

            if (!quotes.isArray() || quotes.size() == 0) {
                return null;
            }

            JsonNode quote = quotes.get(0);

            // Check if data is valid (not null/empty)
            JsonNode closeNode = quote.get("close");
            if (closeNode == null || closeNode.isNull() || closeNode.asDouble() == 0) {
                return null;
            }

            BigDecimal open = parseBigDecimal(quote.get("open"));
            BigDecimal high = parseBigDecimal(quote.get("high"));
            BigDecimal low = parseBigDecimal(quote.get("low"));
            BigDecimal close = parseBigDecimal(closeNode);
            long volume = quote.get("volume").asLong(0);

            return CandleData.of(
                symbol,
                date,
                open,
                high,
                low,
                close,
                volume
            );

        } catch (Exception e) {
            logger.warn("Failed to fetch candle for {} on {}: {}", symbol, date, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches multiple days of OHLCV data for a stock.
     *
     * @param symbol the stock symbol
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return list of candle data
     */
    @Override
    public Iterable<CandleData> fetchCandles(String symbol, LocalDate startDate, LocalDate endDate) {
        List<CandleData> candles = new ArrayList<>();

        try {
            String yfinanceSymbol = formatSymbolForYahoo(symbol);

            long period1 = startDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
            long period2 = endDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) + 86400;

            String url = String.format("%s%s/%s?period1=%d&period2=%d&interval=1d&events=history",
                    YAHOO_FINANCE_API, V8_API_PATH, yfinanceSymbol, period1, period2);

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(status -> status.value() >= 400, r -> Mono.empty())
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isEmpty()) {
                return candles;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result");

            if (!result.isArray() || result.size() == 0) {
                return candles;
            }

            JsonNode quotes = result.get(0).path("indicators").path("quote").get(0);

            if (!quotes.isArray() || quotes.size() == 0) {
                return candles;
            }

            for (int i = 0; i < quotes.size(); i++) {
                JsonNode quote = quotes.get(i);

                // Skip rows with missing close price
                JsonNode closeNode = quote.get("close");
                if (closeNode == null || closeNode.isNull() || closeNode.asDouble() == 0) {
                    continue;
                }

                BigDecimal open = parseBigDecimal(quote.get("open"));
                BigDecimal high = parseBigDecimal(quote.get("high"));
                BigDecimal low = parseBigDecimal(quote.get("low"));
                BigDecimal close = parseBigDecimal(closeNode);
                long volume = quote.get("volume").asLong(0);

                // Get date from timestamp
                JsonNode timestamps = result.get(0).path("timestamp");
                if (i < timestamps.size()) {
                    long timestamp = timestamps.get(i).asLong();
                    LocalDate date = LocalDate.ofEpochDay(timestamp / 86400);

                    candles.add(CandleData.of(symbol, date, open, high, low, close, volume));
                }
            }

            logger.info("Fetched {} candles for {} from {} to {}", candles.size(), symbol, startDate, endDate);

        } catch (Exception e) {
            logger.error("Error fetching candles for {}: {}", symbol, e.getMessage());
        }

        return candles;
    }

    /**
     * Fetches the most recent candle for a stock.
     *
     * @param symbol the stock symbol
     * @return the latest candle data
     */
    @Override
    public CandleData fetchLatestCandle(String symbol) {
        // Fetch last 30 days to get the most recent candle
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        Iterable<CandleData> candles = fetchCandles(symbol, startDate, endDate);

        CandleData latest = null;
        for (CandleData candle : candles) {
            if (latest == null || candle.date().isAfter(latest.date())) {
                latest = candle;
            }
        }

        return latest;
    }

    /**
     * Fetches instrument details for a stock.
     * Note: Yahoo Finance doesn't provide detailed instrument info like lot size.
     * This returns basic symbol mapping info.
     *
     * @param symbol the stock symbol
     * @return instrument details or null if not found
     */
    @Override
    public InstrumentDetails fetchInstrumentDetails(String symbol) {
        try {
            String yfinanceSymbol = formatSymbolForYahoo(symbol);

            String url = String.format("%s%s/%s?modules=assetProfile%2CsummaryDetail",
                    YAHOO_FINANCE_API, "/v10/finance.quote-summary", yfinanceSymbol);

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(status -> status.value() >= 400, r -> Mono.empty())
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isEmpty()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode summaryDetail = root.path("quoteSummary").path("result").get(0).path("summaryDetail");

            // Parse basic info from Yahoo Finance
            String exchange = summaryDetail.path("exchange").asText("NSE");
            String name = summaryDetail.path("longName").asText(symbol);

            return InstrumentDetails.of(
                symbol,
                name,
                exchange,
                "EQUITY",
                null, // Lot size not available from Yahoo Finance
                null, // Tick size not available
                null  // ISIN not available
            );

        } catch (Exception e) {
            logger.warn("Failed to fetch instrument details for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches a list of all available stocks.
     * Note: Yahoo Finance doesn't provide a complete list of all stocks.
     * This returns a limited set or empty list.
     *
     * @return list of stock symbols
     */
    @Override
    public Iterable<String> fetchAllStockSymbols() {
        // Yahoo Finance doesn't provide a complete stock list endpoint
        // Return empty - use a predefined list instead
        return List.of();
    }

    /**
     * Checks if the client is connected and working.
     *
     * @return true if connected
     */
    @Override
    public boolean isConnected() {
        try {
            // Test connection by fetching a known stock (RELIANCE)
            CandleData candle = fetchCandle("RELIANCE", LocalDate.now().minusDays(1));
            return candle != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Formats a stock symbol for Yahoo Finance format.
     * NSE symbols get .NS suffix (e.g., RELIANCE -> RELIANCE.NS)
     * BSE symbols get .BO suffix (e.g., RELIANCE -> RELIANCE.BO)
     *
     * @param symbol the stock symbol
     * @return formatted symbol for Yahoo Finance
     */
    private String formatSymbolForYahoo(String symbol) {
        // Check if symbol already has exchange suffix
        if (symbol.endsWith(".NS") || symbol.endsWith(".BO")) {
            return symbol;
        }

        // Default to NSE for Indian stocks
        return symbol + ".NS";
    }

    /**
     * Parses a BigDecimal from a JsonNode, handling null/missing values.
     *
     * @param node the JSON node
     * @return parsed BigDecimal or BigDecimal.ZERO if null/missing
     */
    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
