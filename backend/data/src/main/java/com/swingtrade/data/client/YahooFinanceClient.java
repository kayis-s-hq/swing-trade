package com.swingtrade.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.data.service.CandleData;
import com.swingtrade.data.service.ChartMeta;
import com.swingtrade.data.service.InstrumentDetails;
import com.swingtrade.data.service.MarketDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance API client for fetching OHLCV market data.
 * Uses Yahoo Finance's public API to retrieve historical stock data.
 *
 * Note: This uses the public yfinance API which is free and doesn't require
 * authentication. It's suitable for development and backfill scenarios.
 */
public class YahooFinanceClient implements MarketDataClient {

    private static final Logger logger = LoggerFactory.getLogger(YahooFinanceClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Yahoo Finance API endpoints
    public YahooFinanceClient() {
        this.objectMapper = new ObjectMapper();

        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);

        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .baseUrl("https://query2.finance.yahoo.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
    }

    // Package-private constructor for testing with MockWebServer
    YahooFinanceClient(String baseUrl) {
        this.objectMapper = new ObjectMapper();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
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

            String uri = String.format("/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d&events=history",
                    yfinanceSymbol, timestamp, nextDay);

            String response = webClient.get()
                    .uri(URI.create(uri))
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

            JsonNode quoteObj = result.get(0).path("indicators").path("quote").get(0);
            JsonNode timestamps = result.get(0).path("timestamp");

            if (!quoteObj.isObject() || !timestamps.isArray() || timestamps.size() == 0) {
                return null;
            }

            JsonNode openArr = quoteObj.path("open");
            JsonNode highArr = quoteObj.path("high");
            JsonNode lowArr = quoteObj.path("low");
            JsonNode closeArr = quoteObj.path("close");
            JsonNode volumeArr = quoteObj.path("volume");
            JsonNode adjcloseArr = result.get(0).path("indicators").path("adjclose");
            JsonNode adjArr = adjcloseArr.isArray() && adjcloseArr.size() > 0
                ? adjcloseArr.get(0).path("adjclose")
                : null;

            // Check if data is valid (not null/empty)
            double closeVal = closeArr.isNull() ? 0 : closeArr.get(0).asDouble(0);
            if (closeVal == 0) {
                return null;
            }

            BigDecimal open = parseBigDecimal(openArr.get(0));
            BigDecimal high = parseBigDecimal(highArr.get(0));
            BigDecimal low = parseBigDecimal(lowArr.get(0));
            BigDecimal close = parseBigDecimal(closeArr.get(0));
            long volume = volumeArr.isNull() ? 0 : volumeArr.get(0).asLong(0);
            BigDecimal adjClose = (adjArr != null && !adjArr.isNull() && adjArr.size() > 0)
                ? parseBigDecimal(adjArr.get(0))
                : close;

            return CandleData.of(
                symbol,
                date,
                open,
                high,
                low,
                close,
                volume,
                adjClose
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

            String uri = String.format("/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d&events=history",
                    yfinanceSymbol, period1, period2);

            String response = webClient.get()
                    .uri(URI.create(uri))
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

            JsonNode quoteObj = result.get(0).path("indicators").path("quote").get(0);
            JsonNode timestamps = result.get(0).path("timestamp");

            if (!quoteObj.isObject() || !timestamps.isArray() || timestamps.size() == 0) {
                return candles;
            }

            JsonNode openArr = quoteObj.path("open");
            JsonNode highArr = quoteObj.path("high");
            JsonNode lowArr = quoteObj.path("low");
            JsonNode closeArr = quoteObj.path("close");
            JsonNode volumeArr = quoteObj.path("volume");
            JsonNode adjcloseArr = result.get(0).path("indicators").path("adjclose");
            JsonNode adjArr = adjcloseArr.isArray() && adjcloseArr.size() > 0
                ? adjcloseArr.get(0).path("adjclose")
                : null;

            for (int i = 0; i < timestamps.size(); i++) {
                // Skip rows with missing close price
                double closeVal = closeArr.isNull() ? 0 : closeArr.get(i).asDouble(0);
                if (closeVal == 0) {
                    continue;
                }

                // Skip pre-market placeholder candles with zero volume
                long volume = volumeArr.isNull() ? 0 : volumeArr.get(i).asLong(0);
                if (volume == 0) {
                    continue;
                }

                BigDecimal open = parseBigDecimal(openArr.get(i));
                BigDecimal high = parseBigDecimal(highArr.get(i));
                BigDecimal low = parseBigDecimal(lowArr.get(i));
                BigDecimal close = parseBigDecimal(closeArr.get(i));
                BigDecimal adjClose = (adjArr != null && !adjArr.isNull() && i < adjArr.size())
                    ? parseBigDecimal(adjArr.get(i))
                    : close;

                long timestamp = timestamps.get(i).asLong();
                LocalDate date = LocalDate.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);

                candles.add(CandleData.of(symbol, date, open, high, low, close, volume, adjClose));
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
     * Uses the chart endpoint's meta field (no separate API call needed).
     */
    @Override
    public InstrumentDetails fetchInstrumentDetails(String symbol) {
        ChartMeta meta = fetchChartMeta(symbol);
        if (meta == null) return null;

        String exchange = meta.exchange().toUpperCase() + "_EQ";
        return InstrumentDetails.of(
            symbol,
            meta.longName(),
            exchange,
            meta.instrumentType(),
            null, // Lot size not available from Yahoo Finance
            null, // Tick size not available
            null  // ISIN not available
        );
    }

    /**
     * Fetches chart metadata for a stock using the chart endpoint meta field.
     * No separate API call needed — meta is returned with every chart response.
     */
    @Override
    public ChartMeta fetchChartMeta(String symbol) {
        try {
            String yfinanceSymbol = formatSymbolForYahoo(symbol);

            String uri = "/v8/finance/chart/" + yfinanceSymbol + "?range=1mo&interval=1d";

            String response = webClient.get()
                    .uri(URI.create(uri))
                    .retrieve()
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

            JsonNode meta = result.get(0).path("meta");

            return ChartMeta.of(
                meta.path("symbol").asText(),
                meta.path("fullExchangeName").asText(),
                meta.path("instrumentType").asText(),
                meta.path("currency").asText(),
                meta.path("longName").asText(),
                meta.path("shortName").asText(),
                parseBigDecimalOrNull(meta.path("regularMarketPrice")),
                parseBigDecimalOrNull(meta.path("fiftyTwoWeekHigh")),
                parseBigDecimalOrNull(meta.path("fiftyTwoWeekLow")),
                parseBigDecimalOrNull(meta.path("chartPreviousClose")),
                meta.has("regularMarketTime")
                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(meta.path("regularMarketTime").asLong()), ZoneOffset.UTC)
                    : null,
                meta.path("firstTradeDate").asInt(),
                meta.path("timezone").asText(),
                meta.path("gmtoffset").asInt()
            );

        } catch (Exception e) {
            logger.warn("Failed to fetch chart meta for {}: {}", symbol, e.getMessage());
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

    /**
     * Parses a BigDecimal from a JsonNode, returning null for missing values.
     */
    private BigDecimal parseBigDecimalOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
