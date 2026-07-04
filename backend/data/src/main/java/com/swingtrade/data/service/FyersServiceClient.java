package com.swingtrade.data.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.data.entity.FyersSymbolEntity;
import com.tts.in.model.*;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Fyers v3 market data and order management client.
 *
 * Market data (candles, quotes) goes through WebClient against the documented REST
 * endpoints (see docs/fyers-api-v3.md). The SDK singleton (FyersClass) is used only
 * for the dormant order-management methods below, which re-set credentials per call.
 *
 * Auth header: {appId}:{accessToken} (colon-separated, no "token" prefix).
 */
public class FyersServiceClient implements MarketDataClient {

    private static final Logger logger = LoggerFactory.getLogger(FyersServiceClient.class);
    private static final String BASE_URL = "https://api-t1.fyers.in";
    private static final int CHUNK_DAYS = 365;

    private final WebClient webClient;
    private final FyersAuthService authService;
    private final FyersSymbolMasterService symbolMaster;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FyersServiceClient(WebClient.Builder webClientBuilder, FyersAuthService authService,
                              FyersSymbolMasterService symbolMaster) {
        this(webClientBuilder, authService, symbolMaster, BASE_URL);
    }

    // Package-private constructor for testing with a custom base URL (MockWebServer)
    FyersServiceClient(WebClient.Builder webClientBuilder, FyersAuthService authService,
                       FyersSymbolMasterService symbolMaster, String baseUrl) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .build();
        this.authService = authService;
        this.symbolMaster = symbolMaster;
    }

    // -----------------------------------------------------------------------
    // Market Data — MarketDataClient interface
    // -----------------------------------------------------------------------

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
        String fyersSymbol = "NSE:" + symbol + "-EQ";
        Map<LocalDate, CandleData> byDate = new LinkedHashMap<>();

        LocalDate windowStart = startDate;
        while (!windowStart.isAfter(endDate)) {
            LocalDate windowEnd = windowStart.plusDays(CHUNK_DAYS - 1);
            if (windowEnd.isAfter(endDate)) windowEnd = endDate;

            URI uri = UriComponentsBuilder.fromPath("/data/history")
                .queryParam("symbol", fyersSymbol)
                .queryParam("resolution", "D")
                .queryParam("date_format", "1")
                .queryParam("date[from]", windowStart)
                .queryParam("date[to]", windowEnd)
                .build()
                .encode()
                .toUri();

            String body = getWithAuthRetry(uri);
            if (body != null) {
                try {
                    for (CandleData candle : parseCandles(symbol, body)) {
                        byDate.put(candle.date(), candle);
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse candles for {}: {}", symbol, e.getMessage());
                }
            }

            windowStart = windowEnd.plusDays(1);
        }

        return new ArrayList<>(byDate.values());
    }

    private List<CandleData> parseCandles(String symbol, String responseBody) throws Exception {
        if (responseBody == null) return Collections.emptyList();

        JsonNode root = objectMapper.readTree(responseBody);
        if (!root.has("candles") || "error".equals(root.path("s").asText())) {
            logger.warn("Fyers API error for {}: {}", symbol, root.has("message") ? root.get("message").asText() : "unknown");
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

            BigDecimal open = node.get(1).decimalValue();
            BigDecimal high = node.get(2).decimalValue();
            BigDecimal low = node.get(3).decimalValue();
            BigDecimal close = node.get(4).decimalValue();
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
        Optional<FyersSymbolEntity> match = symbolMaster.findByTradingSymbol(symbol);
        if (match.isPresent()) {
            FyersSymbolEntity e = match.get();
            return InstrumentDetails.of(e.getTradingSymbol(), e.getName(), "NSE_EQ", "EQ",
                e.getLotSize(), e.getTickSize(), e.getIsin());
        }
        logger.warn("No symbol master entry for {}; returning fallback instrument details", symbol);
        return InstrumentDetails.of(symbol, symbol, "NSE_EQ", "EQ", 1, BigDecimal.valueOf(0.05), null);
    }

    @Override
    public ChartMeta fetchChartMeta(String symbol) {
        QuoteData quote = fetchQuote(symbol);
        if (quote == null) return null;
        Optional<FyersSymbolEntity> match = symbolMaster.findByTradingSymbol(symbol);
        String name = match.map(FyersSymbolEntity::getName).orElse(quote.shortName());
        return ChartMeta.of(
            symbol, "NSE", "EQ", "INR",
            name, quote.shortName(),
            quote.regularMarketPrice(), null, null,
            quote.regularMarketPreviousClose(), null,
            0, "Asia/Kolkata", 19800
        );
    }

    @Override
    public Iterable<String> fetchAllStockSymbols() {
        return symbolMaster.allTradingSymbols();
    }

    // -----------------------------------------------------------------------
    // Quotes
    // -----------------------------------------------------------------------

    @Override
    public QuoteData fetchQuote(String symbol) {
        List<QuoteData> results = fetchQuotes(List.of(symbol));
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<QuoteData> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyList();

        String joined = symbols.stream()
            .map(s -> "NSE:" + s + "-EQ")
            .collect(Collectors.joining(","));

        URI uri = UriComponentsBuilder.fromPath("/data/quotes")
            .queryParam("symbols", joined)
            .build()
            .encode()
            .toUri();

        String body = getWithAuthRetry(uri);
        if (body == null) return Collections.emptyList();

        try {
            return parseQuotes(body);
        } catch (Exception e) {
            logger.error("Failed to parse quotes for {}: {}", symbols, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<QuoteData> parseQuotes(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        if (!"success".equals(root.path("s").asText())) {
            return Collections.emptyList();
        }

        List<QuoteData> results = new ArrayList<>();
        for (JsonNode item : root.path("d")) {
            JsonNode v = item.path("v");
            if (v.isMissingNode()) continue;

            String rawSymbol = v.has("symbol") ? v.get("symbol").asText() : item.path("n").asText(null);
            QuoteData q = parseQuoteFromNode(extractTradingSymbol(rawSymbol), v);
            if (q != null) results.add(q);
        }
        return results;
    }

    private String extractTradingSymbol(String rawSymbol) {
        if (rawSymbol == null) return null;
        String s = rawSymbol.contains(":") ? rawSymbol.substring(rawSymbol.indexOf(':') + 1) : rawSymbol;
        return s.split("-")[0];
    }

    private QuoteData parseQuoteFromNode(String symbol, JsonNode v) {
        try {
            BigDecimal price = decimalOrNull(v, "lp");
            BigDecimal change = decimalOrNull(v, "ch");
            BigDecimal changePct = decimalOrNull(v, "chp");
            BigDecimal high = decimalOrNull(v, "high_price");
            BigDecimal low = decimalOrNull(v, "low_price");
            BigDecimal prevClose = decimalOrNull(v, "prev_close_price");
            Long volume = v.has("volume") ? v.get("volume").asLong() : null;
            String shortName = v.has("short_name") ? v.get("short_name").asText() : null;

            return QuoteData.of(
                symbol, shortName, null,
                price, change, changePct,
                high, low, prevClose,
                null, null, volume, null, null, null, null
            );
        } catch (Exception e) {
            logger.warn("Failed to parse quote for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private BigDecimal decimalOrNull(JsonNode v, String field) {
        JsonNode node = v.get(field);
        return (node == null || node.isNull()) ? null : node.decimalValue();
    }

    // -----------------------------------------------------------------------
    // Symbol search
    // -----------------------------------------------------------------------

    @Override
    public List<SearchResult> searchSymbols(String query) {
        return symbolMaster.search(query, 20).stream()
            .map(e -> SearchResult.of(e.getTradingSymbol(), e.getName(), e.getName(), "EQUITY", "NSE", "NSE"))
            .toList();
    }

    @Override
    public boolean isConnected() {
        return authService.validateToken();
    }

    // -----------------------------------------------------------------------
    // Shared auth-retry GET helper (candles + quotes)
    // -----------------------------------------------------------------------

    private String getWithAuthRetry(URI uri) {
        String token = authService.getAccessToken();
        if (token == null) {
            logger.error("Fyers access token is missing. Please complete authentication.");
            return null;
        }
        String appId = authService.getClientId();

        try {
            String body = doGet(uri, appId, token);
            if (isAuthError(body)) {
                logger.warn("Fyers auth error for {}. Refreshing and retrying...", uri.getPath());
                return retryAfterRefresh(uri, appId);
            }
            return body;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                logger.warn("Token expired (401) for {}. Refreshing and retrying...", uri.getPath());
                return retryAfterRefresh(uri, appId);
            }
            logger.error("Fyers API error for {}: {} {}", uri.getPath(), e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            logger.error("Request failed for {}: {}", uri.getPath(), e.getMessage());
            return null;
        }
    }

    private String retryAfterRefresh(URI uri, String appId) {
        authService.refreshToken();
        String newToken = authService.getAccessToken();
        if (newToken == null) return null;

        try {
            String body = doGet(uri, appId, newToken);
            if (isAuthError(body)) {
                logger.error("Fyers auth error persisted after refresh for {}", uri.getPath());
                return null;
            }
            return body;
        } catch (Exception e) {
            logger.error("Retry failed for {}: {}", uri.getPath(), e.getMessage());
            return null;
        }
    }

    private String doGet(URI uri, String appId, String token) {
        return webClient.get()
            .uri(uri)
            .header("Authorization", appId + ":" + token)
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }

    /**
     * Fyers returns HTTP 200 with a body like {"s":"error","code":-16} for bad/expired
     * tokens on some endpoints instead of a 401 — treat those codes as auth failures too.
     */
    private boolean isAuthError(String body) {
        if (body == null) return false;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"error".equals(root.path("s").asText())) return false;
            int code = root.path("code").asInt(0);
            return code == -15 || code == -16 || code == -17;
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Order Management
    // -----------------------------------------------------------------------

    public String placeOrder(String symbol, int qty, int side, int orderType,
                              double limitPrice, String productType,
                              Double stopLoss, Double takeProfit, String orderTag) {
        String token = authService.getAccessToken();
        if (token == null) {
            logger.error("Fyers access token is missing.");
            return null;
        }

        PlaceOrderModel model = new PlaceOrderModel();
        model.Symbol = symbol;
        model.Qty = qty;
        model.Side = side;
        model.OrderType = orderType;
        model.LimitPrice = limitPrice;
        model.ProductType = productType;
        model.OrderValidity = "DAY";
        if (stopLoss != null) model.StopLoss = stopLoss;
        if (takeProfit != null) model.TakeProfit = takeProfit;
        if (orderTag != null) model.OrderTag = orderTag;

        try {
            Tuple<JSONObject, JSONObject> result = getSdk().PlaceOrder(model);
            if (result == null) return null;

            JSONObject metadata = result.Item1();
            if (!"success".equals(metadata.optString("s"))) {
                logger.error("Place order failed: {}", metadata.optString("message", "unknown"));
                return null;
            }

            JSONObject body = result.Item2();
            return body.has("id") ? body.getString("id") : null;
        } catch (Exception e) {
            logger.error("Place order error: {}", e.getMessage());
            return null;
        }
    }

    public boolean cancelOrder(String orderId) {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().CancelOrder(orderId);
            if (result == null) return false;
            return "success".equals(result.Item1().optString("s"));
        } catch (Exception e) {
            logger.error("Cancel order error: {}", e.getMessage());
            return false;
        }
    }

    public List<OrderModel> getAllOrders() {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetAllOrders();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseOrderList(result.Item2());
        } catch (Exception e) {
            logger.error("Get orders error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<OrderModel> getOrderHistory(String symbols) {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetOrderHistory(symbols);
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseOrderList(result.Item2());
        } catch (Exception e) {
            logger.error("Get order history error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<OrderModel> parseOrderList(JSONObject data) {
        List<OrderModel> orders = new ArrayList<>();
        if (data == null) return orders;

        JSONObject nodes = data.has("orderBook") ? data.optJSONObject("orderBook") : data;
        if (nodes == null) return orders;

        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;

            OrderModel model = new OrderModel();
            model.OrderId = node.optString("id", null);
            model.Symbol = node.optString("symbol", null);
            model.Qty = node.optInt("qty", 0);
            model.RemainingQuantity = node.optInt("remainingQuantity", 0);
            model.FilledQty = node.optInt("filledQty", 0);
            model.LimitPrice = node.optDouble("limitPrice", 0);
            model.StopPrice = node.optDouble("stopPrice", 0);
            model.TradedPrice = node.optDouble("tradedPrice", 0);
            model.OrderType = node.optInt("type", 0);
            model.ProductType = node.optString("productType", null);
            model.Side = node.optInt("side", 0);
            model.OrderStatus = node.optInt("status", 0);
            model.OrderDateTime = node.optString("orderDateTime", null);
            model.OrderValidity = node.optString("orderValidity", null);
            model.OrderTag = node.optString("orderTag", null);
            model.Message = node.optString("message", null);
            orders.add(model);
        }
        return orders;
    }

    // -----------------------------------------------------------------------
    // Position Management
    // -----------------------------------------------------------------------

    public List<PositionModel> getPositions() {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetPositions();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parsePositionList(result.Item2());
        } catch (Exception e) {
            logger.error("Get positions error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean exitAllPositions() {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().ExitPositions(true);
            if (result == null) return false;
            return "success".equals(result.Item1().optString("s"));
        } catch (Exception e) {
            logger.error("Exit positions error: {}", e.getMessage());
            return false;
        }
    }

    public boolean exitPosition(String orderId) {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().ExitPositions(List.of(orderId));
            if (result == null) return false;
            return "success".equals(result.Item1().optString("s"));
        } catch (Exception e) {
            logger.error("Exit position error: {}", e.getMessage());
            return false;
        }
    }

    public boolean convertPosition(String symbol, int side, int convertQty,
                                    String fromProduct, String toProduct) {
        PositionConversionModel model = new PositionConversionModel();
        model.Symbol = symbol;
        model.Side = side;
        model.ConvertQty = convertQty;
        model.ConvertFrom = fromProduct;
        model.ConvertTo = toProduct;

        try {
            Tuple<JSONObject, JSONObject> result = getSdk().PositionConversion(model);
            if (result == null) return false;
            return "success".equals(result.Item1().optString("s"));
        } catch (Exception e) {
            logger.error("Position conversion error: {}", e.getMessage());
            return false;
        }
    }

    private List<PositionModel> parsePositionList(JSONObject data) {
        List<PositionModel> positions = new ArrayList<>();
        if (data == null) return positions;

        JSONObject nodes = data.has("netPositions") ? data.optJSONObject("netPositions") : data;
        if (nodes == null) return positions;

        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;

            PositionModel model = new PositionModel();
            model.Symbol = node.optString("symbol", null);
            model.NetQty = node.optInt("netQty", 0);
            model.Side = node.optInt("side", 0);
            model.AvgPrice = node.optDouble("avgPrice", 0);
            model.NetAvg = node.optDouble("netAvg", 0);
            model.RealizedProfit = node.optDouble("realized_profit", 0);
            model.UnRealizedProfit = node.optDouble("unrealized_profit", 0);
            model.LTP = node.optDouble("ltp", 0);
            model.ProductType = node.optString("productType", null);
            model.FyToken = node.optString("fyToken", null);
            positions.add(model);
        }
        return positions;
    }

    // -----------------------------------------------------------------------
    // Trade Book
    // -----------------------------------------------------------------------

    public List<TradeBookModel> getTradeBook() {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetTradeBook();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseTradeBook(result.Item2());
        } catch (Exception e) {
            logger.error("Get trade book error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<TradeBookModel> parseTradeBook(JSONObject data) {
        List<TradeBookModel> trades = new ArrayList<>();
        if (data == null) return trades;

        JSONObject nodes = data.has("tradeBook") ? data.optJSONObject("tradeBook") : data;
        if (nodes == null) return trades;

        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;

            TradeBookModel model = new TradeBookModel();
            model.Symbol = node.optString("symbol", null);
            model.Side = node.optInt("side", 0);
            model.TradedQty = node.optInt("tradedQty", 0);
            model.TradePrice = node.optDouble("tradePrice", 0);
            model.TradeValue = node.optDouble("tradeValue", 0);
            model.OrderNumber = node.optString("orderNumber", null);
            model.FYToken = node.optString("fyToken", null);
            model.ProductType = node.optString("productType", null);
            model.OrderTag = node.optString("orderTag", null);
            trades.add(model);
        }
        return trades;
    }

    // -----------------------------------------------------------------------
    // Holdings
    // -----------------------------------------------------------------------

    public List<HoldingModel> getHoldings() {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetHoldings();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseHoldingList(result.Item2());
        } catch (Exception e) {
            logger.error("Get holdings error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<HoldingModel> parseHoldingList(JSONObject data) {
        List<HoldingModel> holdings = new ArrayList<>();
        if (data == null) return holdings;

        JSONObject nodes = data.has("holdings") ? data.optJSONObject("holdings") : data;
        if (nodes == null) return holdings;

        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;

            HoldingModel model = new HoldingModel();
            model.Symbol = node.optString("symbol", null);
            model.FyToken = node.optString("fyToken", null);
            model.Qty = node.optInt("quantity", 0);
            model.CostPrice = node.optDouble("costPrice", 0);
            model.LTP = node.optDouble("ltp", 0);
            model.PL = node.optDouble("pl", 0);
            model.HoldingType = node.optString("holdingType", null);
            model.ISIN = node.optString("isin", null);
            holdings.add(model);
        }
        return holdings;
    }

    // -----------------------------------------------------------------------
    // Market Depth
    // -----------------------------------------------------------------------

    public MarketDepthModel getMarketDepth(String symbol) {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetMarketDepth(symbol, 1);
            if (result == null) return null;
            if (!"success".equals(result.Item1().optString("s"))) return null;
            return parseMarketDepth(result.Item2());
        } catch (Exception e) {
            logger.error("Get market depth error: {}", e.getMessage());
            return null;
        }
    }

    private MarketDepthModel parseMarketDepth(JSONObject data) {
        MarketDepthModel model = new MarketDepthModel();
        if (data == null) return model;

        JSONObject first = (data.length() > 0) ? data.optJSONObject(data.keySet().iterator().next()) : null;
        if (first == null) return model;

        model.Symbol = first.optString("symbol", null);
        model.Open = toDouble(first, "o");
        model.High = toDouble(first, "h");
        model.Low = toDouble(first, "l");
        model.Close = toDouble(first, "c");
        model.Change = toDouble(first, "ch");
        model.ChangePercent = toDouble(first, "chp");
        model.LTP = toDouble(first, "ltp");
        model.LTQ = first.optInt("ltq", 0);
        model.LTT = toDouble(first, "ltt");
        model.V = toDouble(first, "v");
        model.ATP = toDouble(first, "atp");
        model.TickSize = toDouble(first, "tick_Size");
        model.LowerCircuit = toDouble(first, "lower_ckt");
        model.UpperCircuit = toDouble(first, "upper_ckt");
        model.TotalBuyQty = first.optInt("totalbuyqty", 0);
        model.TotalSellQty = first.optInt("totalsellqty", 0);
        model.OI = toDouble(first, "oi");
        model.OIFlag = first.optBoolean("oiflag", false);

        JSONObject bidsObj = first.optJSONObject("bids");
        if (bidsObj != null) {
            for (int i = 0; i < bidsObj.length(); i++) {
                String key = Integer.toString(i);
                if (!bidsObj.has(key)) break;
                AskBid askBid = new AskBid();
                JSONObject bid = bidsObj.optJSONObject(key);
                if (bid != null) {
                    askBid.Price = bid.optDouble("price", 0);
                    askBid.Volume = bid.optDouble("volume", 0);
                    askBid.Ord = bid.optDouble("ord", 0);
                    model.Bids.add(askBid);
                }
            }
        }

        JSONObject asksObj = first.optJSONObject("ask");
        if (asksObj != null) {
            for (int i = 0; i < asksObj.length(); i++) {
                String key = Integer.toString(i);
                if (!asksObj.has(key)) break;
                AskBid askBid = new AskBid();
                JSONObject ask = asksObj.optJSONObject(key);
                if (ask != null) {
                    askBid.Price = ask.optDouble("price", 0);
                    askBid.Volume = ask.optDouble("volume", 0);
                    askBid.Ord = ask.optDouble("ord", 0);
                    model.Asks.add(askBid);
                }
            }
        }

        return model;
    }

    // -----------------------------------------------------------------------
    // Market Status
    // -----------------------------------------------------------------------

    public List<MarketStatusModel> getMarketStatus() {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetMarketStatus();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseMarketStatusList(result.Item2());
        } catch (Exception e) {
            logger.error("Get market status error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<MarketStatusModel> parseMarketStatusList(JSONObject data) {
        List<MarketStatusModel> statuses = new ArrayList<>();
        if (data == null) return statuses;

        JSONObject nodes = data.has("marketStatus") ? data.optJSONObject("marketStatus") : data;
        if (nodes == null) return statuses;

        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;

            MarketStatusModel model = new MarketStatusModel();
            model.Exchange = node.optInt("exchange", 0);
            model.MarketType = node.optString("market_type", null);
            model.Segment = node.optInt("segment", 0);
            model.Status = node.optString("status", null);
            statuses.add(model);
        }
        return statuses;
    }

    // -----------------------------------------------------------------------
    // Profile & Funds
    // -----------------------------------------------------------------------

    public ProfileModel getProfile() {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetProfile();
            if (result == null) return null;
            if (!"success".equals(result.Item1().optString("s"))) return null;

            JSONObject body = result.Item2();
            ProfileModel model = new ProfileModel();
            model.Name = body.optString("name", null);
            model.DisplayName = body.optString("display_name", null);
            model.EmailId = body.optString("email_id", null);
            model.PAN = body.optString("PAN", null);
            model.FYId = body.optString("fy_id", null);
            model.MobileNumber = body.optString("mobile_number", null);
            return model;
        } catch (Exception e) {
            logger.error("Get profile error: {}", e.getMessage());
            return null;
        }
    }

    public List<FundModel> getFunds() {
        try {
            Tuple<JSONObject, JSONObject> result = getSdk().GetFunds();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseFundList(result.Item2());
        } catch (Exception e) {
            logger.error("Get funds error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FundModel> parseFundList(JSONObject data) {
        List<FundModel> funds = new ArrayList<>();
        if (data == null) return funds;

        JSONObject nodes = data.has("fund_limit") ? data.optJSONObject("fund_limit") : data;
        if (nodes == null) return funds;

        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;

            FundModel model = new FundModel();
            model.Title = node.optString("fund_type", null);
            model.EquityAmount = node.optDouble("equityAmount", 0);
            model.CommodityAmount = node.optDouble("commodityAmount", 0);
            funds.add(model);
        }
        return funds;
    }

    // -----------------------------------------------------------------------
    // GTT Orders
    // -----------------------------------------------------------------------

    public String placeGTTOrder(String symbol, int side, String productType,
                                 double triggerPrice, double limitPrice, int qty) {
        GTTModel model = new GTTModel();
        model.Side = side;
        model.Symbol = symbol;
        model.productType = productType;

        GTTLeg leg = new GTTLeg((int) limitPrice, (int) triggerPrice, qty);
        model.addGTTLeg("leg1", leg);

        try {
            Tuple<JSONObject, JSONObject> result = getSdk().PlaceGTTOrder(List.of(model));
            if (result == null) return null;
            if (!"success".equals(result.Item1().optString("s"))) return null;
            JSONObject body = result.Item2();
            return body.has("id") ? body.getString("id") : null;
        } catch (Exception e) {
            logger.error("GTT order error: {}", e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /**
     * Get the SDK singleton instance, refreshing credentials before each call.
     */
    private FyersClass getSdk() {
        FyersClass sdk = FyersClass.getInstance();
        sdk.clientId = authService.getClientId();
        String token = authService.getAccessToken();
        if (token != null) {
            sdk.accessToken = token;
        }
        return sdk;
    }

    private Double toDouble(JSONObject obj, String key) {
        if (!obj.has(key)) return null;
        try {
            return obj.getDouble(key);
        } catch (Exception e) {
            return null;
        }
    }
}
