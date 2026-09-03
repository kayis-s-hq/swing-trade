package com.swingtrade.broker.kite;

import tools.jackson.databind.ObjectMapper;
import com.swingtrade.broker.model.OrderResponse;
import com.swingtrade.broker.model.Portfolio;
import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.TradeDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * Zerodha Kite Connect client implementation using HTTP API calls.
 * Since KiteConnect SDK is not publicly available, this uses direct REST API calls.
 *
 * API Documentation: https://kite.trade/docs/connect/v3/
 */
@Component
@ConditionalOnProperty(name = "kite.enabled", havingValue = "true", matchIfMissing = false)
public class KiteConnectClient implements BrokerClient {

    private static final Logger logger = LoggerFactory.getLogger(KiteConnectClient.class);

    private static final String KITE_BASE_URL = "https://api.kite.trade";
    private static final String LOGIN_URL = "https://kite.zerodha.com/connect/login";

    private final KiteConfig kiteConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private String accessToken;
    private String sessionId;

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    @Autowired
    public KiteConnectClient(KiteConfig kiteConfig, ObjectMapper objectMapper) {
        this.kiteConfig = kiteConfig;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.accessToken = kiteConfig.getAccessToken();

        String apiKeyDisplay = kiteConfig.getApiKey();
        if (apiKeyDisplay != null && apiKeyDisplay.length() >= 8) {
            apiKeyDisplay = apiKeyDisplay.substring(0, 8);
        } else if (apiKeyDisplay != null && !apiKeyDisplay.isEmpty()) {
            apiKeyDisplay = apiKeyDisplay + "...";
        } else {
            apiKeyDisplay = "not configured";
        }
        logger.info("KiteConnectClient initialized with API key: {}", apiKeyDisplay);
        logger.info("Access token configured: {}", accessToken != null && !accessToken.isEmpty() ? "yes" : "no");
    }

    /**
     * Constructor for testing purposes.
     */
    public KiteConnectClient(KiteConfig kiteConfig, ObjectMapper objectMapper, String accessToken) {
        this.kiteConfig = kiteConfig;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.accessToken = accessToken;
    }

    /**
     * Set the access token for authenticated requests.
     * This is obtained after user authorization flow.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        logger.info("Access token set for KiteConnectClient");
    }

    /**
     * Get the current access token.
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Check if the client is configured with an API key.
     */
    @Override
    public boolean isConfigured() {
        return kiteConfig != null && kiteConfig.isConfigured() && accessToken != null;
    }

    /**
     * Generate login URL for OAuth authorization flow.
     * The user must visit this URL and authorize the application.
     * After authorization, they receive a request_token which must be exchanged
     * for an access token using generateSession().
     */
    @Override
    public String generateLoginUrl() {
        String url = LOGIN_URL + "?api_key=" + kiteConfig.getApiKey();
        logger.info("Generated Kite login URL: {}", url);
        return url;
    }

    /**
     * Generate session using request token obtained from OAuth flow.
     * This must be called after the user completes OAuth authorization.
     *
     * @param requestToken token received after OAuth authorization
     * @return true if session generated successfully
     */
    public boolean generateSession(String requestToken) {
        try {
            String url = KITE_BASE_URL + "/session";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("api_key", kiteConfig.getApiKey());
            body.put("request_token", requestToken);
            body.put("access_token", kiteConfig.getApiKey()); // Placeholder, may need adjustment

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null && data.get("access_token") != null) {
                    this.accessToken = (String) data.get("access_token");
                    this.sessionId = (String) data.get("user_id");
                    logger.info("Session generated successfully for user: {}", sessionId);
                    return true;
                }
            }
            logger.error("Failed to generate session: {}", response.getBody());
            return false;
        } catch (Exception e) {
            logger.error("Error generating session: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Place a new order through Kite Connect.
     *
     * @param orderResponse the order to place
     * @return updated order response with broker-assigned ID
     */
    @Override
    public OrderResponse placeOrder(OrderResponse orderResponse) {
        logger.info("Placing order for symbol: {} direction: {} quantity: {}",
                orderResponse.getSymbol(), orderResponse.getDirection(), orderResponse.getQuantity());

        return withRetry(() -> placeOrderInternal(orderResponse), "placeOrder");
    }

    private OrderResponse placeOrderInternal(OrderResponse orderResponse) {
        if (!isConfigured()) {
            logger.error("KiteConnectClient not configured. Cannot place order.");
            orderResponse.setStatus(OrderStatus.CANCELLED);
            orderResponse.setMessage("Client not configured. API key or access token missing.");
            return orderResponse;
        }

        try {
            String url = KITE_BASE_URL + "/orders";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            Map<String, Object> body = new HashMap<>();
            body.put("exchange", orderResponse.getExchange() == Exchange.NSE ? "NSE" : "BSE");
            body.put("tradingsymbol", orderResponse.getSymbol());
            body.put("symbol", orderResponse.getSymbol());
            body.put("transaction_type", orderResponse.getDirection() == TradeDirection.LONG ? "BUY" : "SELL");
            body.put("qty", orderResponse.getQuantity().toString());
            body.put("type", orderResponse.getType() == OrderType.MARKET ? "market" : "limit");
            body.put("program", "S"); // Systematic (for position trading)

            if (orderResponse.getType() == OrderType.LIMIT && orderResponse.getLimitPrice() != null) {
                body.put("price", orderResponse.getLimitPrice().toString());
                body.put("discarded_qty", "0");
                body.put("discarded_price", "0");
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> responseData = objectMapper.readValue(response.getBody(), Map.class);
                Map<String, Object> data = (Map<String, Object>) responseData.get("data");

                if (data != null) {
                    orderResponse.setBrokerOrderId((String) data.get("order_id"));
                    orderResponse.setStatus(OrderStatus.PENDING);
                    orderResponse.setMessage("Order placed successfully: " + data.get("order_id"));
                    logger.info("Order placed successfully. Order ID: {}", data.get("order_id"));
                } else {
                    orderResponse.setStatus(OrderStatus.CANCELLED);
                    orderResponse.setMessage("Order rejected by broker: " + responseData.get("message"));
                    logger.warn("Order rejected: {}", responseData.get("message"));
                }
            } else {
                orderResponse.setStatus(OrderStatus.CANCELLED);
                orderResponse.setMessage("Failed to place order: " + response.getStatusCode());
                logger.error("Failed to place order. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            }

            return orderResponse;

        } catch (Exception e) {
            logger.error("Error placing order: {}", e.getMessage(), e);
            orderResponse.setStatus(OrderStatus.CANCELLED);
            orderResponse.setMessage("Error placing order: " + e.getMessage());
            return orderResponse;
        }
    }

    /**
     * Cancel an existing order.
     *
     * @param orderId the order to cancel
     * @return true if cancelled, false otherwise
     */
    @Override
    public boolean cancelOrder(String orderId) {
        logger.info("Cancelling order: {}", orderId);

        return withRetry(() -> cancelOrderInternal(orderId), "cancelOrder");
    }

    private boolean cancelOrderInternal(String orderId) {
        if (!isConfigured()) {
            logger.error("KiteConnectClient not configured. Cannot cancel order.");
            return false;
        }

        try {
            String url = KITE_BASE_URL + "/orders/" + orderId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, request, Map.class);

            boolean success = response.getStatusCode() == HttpStatus.OK;
            if (success) {
                logger.info("Order cancelled successfully: {}", orderId);
            } else {
                logger.warn("Failed to cancel order: {}", response.getBody());
            }
            return success;

        } catch (Exception e) {
            logger.error("Error cancelling order: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Modify an existing order.
     *
     * @param orderId the order to modify
     * @param exchange exchange
     * @param symbol trading symbol
     * @param quantity new quantity
     * @param price new price
     * @return true if modified, false otherwise
     */
    @Override
    public boolean modifyOrder(String orderId, Exchange exchange, String symbol,
                               BigDecimal quantity, BigDecimal price) {
        logger.info("Modifying order: {} to qty: {} price: {}", orderId, quantity, price);

        return withRetry(() -> modifyOrderInternal(orderId, exchange, symbol, quantity, price), "modifyOrder");
    }

    private boolean modifyOrderInternal(String orderId, Exchange exchange, String symbol,
                                        BigDecimal quantity, BigDecimal price) {
        if (!isConfigured()) {
            logger.error("KiteConnectClient not configured. Cannot modify order.");
            return false;
        }

        try {
            String url = KITE_BASE_URL + "/orders/" + orderId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            Map<String, Object> body = new HashMap<>();
            body.put("exchange", exchange == Exchange.NSE ? "NSE" : "BSE");
            body.put("tradingsymbol", symbol);
            body.put("symbol", symbol);
            body.put("qty", quantity.toString());
            body.put("type", "limit");
            body.put("price", price.toString());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);

            boolean success = response.getStatusCode() == HttpStatus.OK;
            if (success) {
                logger.info("Order modified successfully: {}", orderId);
            } else {
                logger.warn("Failed to modify order: {}", response.getBody());
            }
            return success;

        } catch (Exception e) {
            logger.error("Error modifying order: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all open positions.
     *
     * @return list of positions
     */
    @Override
    public List<Position> getPositions() {
        logger.debug("Fetching positions");

        return withRetry(() -> getPositionsInternal(), "getPositions");
    }

    private List<Position> getPositionsInternal() {
        if (!isConfigured()) {
            logger.error("KiteConnectClient not configured. Cannot fetch positions.");
            return Collections.emptyList();
        }

        try {
            String url = KITE_BASE_URL + "/holdings";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                List<Map<String, Object>> holdings = objectMapper.readValue(
                        response.getBody(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );

                return holdings.stream()
                        .map(this::mapHoldingToPosition)
                        .collect(Collectors.toList());
            }

            logger.warn("Failed to fetch positions: {}", response.getStatusCode());
            return Collections.emptyList();

        } catch (Exception e) {
            logger.error("Error fetching positions: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get a specific position.
     *
     * @param symbol trading symbol
     * @return optional position
     */
    @Override
    public Optional<Position> getPosition(String symbol) {
        List<Position> positions = getPositions();
        return positions.stream()
                .filter(p -> p.symbol().equals(symbol))
                .findFirst();
    }

    /**
     * Map holding from Kite API to Position model.
     */
    private Position mapHoldingToPosition(Map<String, Object> holding) {
        String exchangeValue = holding.get("exchange") != null ? (String) holding.get("exchange") : "NSE";
        return new Position(
                null, "Fyers",
                (String) holding.get("tradingsymbol"),
                holding.get("average_price") != null ? new BigDecimal(holding.get("average_price").toString()) : BigDecimal.ZERO,
                null,
                holding.get("quantity") != null ? Integer.valueOf(holding.get("quantity").toString()) : 0,
                null, null, null, null, null,
                (String) holding.get("instrument_key"), null,
                exchangeValue.equals("NSE") ? Exchange.NSE : Exchange.BSE,
                TradeDirection.LONG,
                null, null, null, null, null, null, null, null
        );
    }

    /**
     * Get the portfolio.
     *
     * @return portfolio object
     */
    @Override
    public Portfolio getPortfolio() {
        logger.debug("Fetching portfolio");

        try {
            String holdingsUrl = KITE_BASE_URL + "/holdings";
            String marginUrl = KITE_BASE_URL + "/margin/segments";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Get holdings
            ResponseEntity<String> holdingsResponse = restTemplate.exchange(
                    holdingsUrl, HttpMethod.GET, request, String.class
            );

            // Get margins
            ResponseEntity<String> marginResponse = restTemplate.exchange(
                    marginUrl, HttpMethod.GET, request, String.class
            );

            Portfolio portfolio = new Portfolio("kite_portfolio", new BigDecimal("50000"));

            if (holdingsResponse.getStatusCode() == HttpStatus.OK) {
                // Holdings fetched but not used in current portfolio model
                objectMapper.readValue(
                        holdingsResponse.getBody(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );
            }

            if (marginResponse.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> marginData = objectMapper.readValue(
                        marginResponse.getBody(), Map.class
                );
                Map<String, Object> data = (Map<String, Object>) marginData.get("data");

                if (data != null) {
                    // Extract available cash
                    Map<String, Object> cash = (Map<String, Object>) data.get("cash");
                    if (cash != null && cash.get("available_cash") != null) {
                        portfolio.setCurrentCapital(
                                new BigDecimal(cash.get("available_cash").toString())
                        );
                    }
                }
            }

            return portfolio;

        } catch (Exception e) {
            logger.error("Error fetching portfolio: {}", e.getMessage(), e);
            return new Portfolio();
        }
    }

    /**
     * Get order history.
     *
     * @return list of order responses
     */
    @Override
    public List<OrderResponse> getOrderHistory() {
        logger.debug("Fetching order history");

        return withRetry(() -> getOrderHistoryInternal(), "getOrderHistory");
    }

    private List<OrderResponse> getOrderHistoryInternal() {
        if (!isConfigured()) {
            logger.error("KiteConnectClient not configured. Cannot fetch order history.");
            return Collections.emptyList();
        }

        try {
            String url = KITE_BASE_URL + "/orders";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                List<Map<String, Object>> orders = objectMapper.readValue(
                        response.getBody(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );

                return orders.stream()
                        .map(this::mapOrderToOrderResponse)
                        .collect(Collectors.toList());
            }

            logger.warn("Failed to fetch order history: {}", response.getStatusCode());
            return Collections.emptyList();

        } catch (Exception e) {
            logger.error("Error fetching order history: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Map order from Kite API to OrderResponse model.
     */
    private OrderResponse mapOrderToOrderResponse(Map<String, Object> order) {
        OrderResponse response = new OrderResponse();
        response.setBrokerOrderId((String) order.get("order_id"));
        response.setSymbol((String) order.get("tradingsymbol"));
        String exchangeValue = order.get("exchange") != null ? (String) order.get("exchange") : "NSE";
        response.setExchange(exchangeValue.equals("NSE") ? Exchange.NSE : Exchange.BSE);

        String transaction = (String) order.get("transaction_type");
        response.setDirection(transaction != null && transaction.equals("BUY")
                ? TradeDirection.LONG : TradeDirection.SHORT);

        if (order.get("quantity") != null) {
            response.setQuantity(new BigDecimal(order.get("quantity").toString()));
        }
        if (order.get("price") != null) {
            response.setPrice(new BigDecimal(order.get("price").toString()));
        }
        if (order.get("trigger_price") != null) {
            response.setStopPrice(new BigDecimal(order.get("trigger_price").toString()));
        }

        String status = (String) order.get("status");
        response.setStatus(mapOrderStatus(status));

        return response;
    }

    /**
     * Map order status string to OrderStatus enum.
     */
    private OrderStatus mapOrderStatus(String status) {
        if (status == null) return OrderStatus.PENDING;

        switch (status.toLowerCase()) {
            case "open":
            case "complete":
                return OrderStatus.FILLED;
            case "cancelled":
            case "rejected":
                return OrderStatus.CANCELLED;
            case "pending":
            case "triggered":
                return OrderStatus.PENDING;
            case "modifying":
            case "modified":
                return OrderStatus.PENDING;
            default:
                return OrderStatus.PENDING;
        }
    }

    /**
     * Get current market price for a symbol.
     *
     * @param symbol trading symbol
     * @param exchange exchange
     * @return market price
     */
    @Override
    public BigDecimal getMarketPrice(String symbol, Exchange exchange) {
        return withRetry(() -> getMarketPriceInternal(symbol, exchange), "getMarketPrice");
    }

    private BigDecimal getMarketPriceInternal(String symbol, Exchange exchange) {
        if (!isConfigured()) {
            logger.error("KiteConnectClient not configured. Cannot fetch market price.");
            return null;
        }

        try {
            String marketSymbol = exchange == Exchange.NSE ? "NSE:" + symbol : "BSE:" + symbol;
            String url = KITE_BASE_URL + "/market/quotes?" + marketSymbol;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> quoteData = objectMapper.readValue(
                        response.getBody(), Map.class
                );
                Map<String, Object> data = (Map<String, Object>) quoteData.get("data");

                if (data != null) {
                    // Kite returns nested structure like "NSE:RELIANCE" -> { last_price: 2500 }
                    Map<String, Object> priceData = (Map<String, Object>) data.get(marketSymbol);
                    if (priceData != null && priceData.get("last_price") != null) {
                        return new BigDecimal(priceData.get("last_price").toString());
                    }
                }
            }

            logger.debug("No price data for symbol: {}", symbol);
            return null;

        } catch (Exception e) {
            logger.error("Error fetching market price: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get market prices for multiple symbols.
     *
     * @param symbols list of trading symbols
     * @param exchange exchange
     * @return map of symbol to price
     */
    @Override
    public Map<String, BigDecimal> getMarketPrices(List<String> symbols, Exchange exchange) {
        logger.debug("Fetching market prices for {} symbols", symbols.size());

        if (!isConfigured()) {
            logger.error("KiteConnectClient not configured. Cannot fetch market prices.");
            return new HashMap<>();
        }

        Map<String, BigDecimal> prices = new HashMap<>();

        try {
            String marketSymbols = symbols.stream()
                    .map(s -> exchange == Exchange.NSE ? "NSE:" + s : "BSE:" + s)
                    .collect(Collectors.joining(","));

            String url = KITE_BASE_URL + "/market/quotes?" + marketSymbols;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> quoteData = objectMapper.readValue(
                        response.getBody(), Map.class
                );
                Map<String, Object> data = (Map<String, Object>) quoteData.get("data");

                if (data != null) {
                    for (String symbol : symbols) {
                        String marketSymbol = exchange == Exchange.NSE ? "NSE:" + symbol : "BSE:" + symbol;
                        Map<String, Object> priceData = (Map<String, Object>) data.get(marketSymbol);
                        if (priceData != null && priceData.get("last_price") != null) {
                            prices.put(symbol, new BigDecimal(priceData.get("last_price").toString()));
                        }
                    }
                }
            }

            return prices;

        } catch (Exception e) {
            logger.error("Error fetching market prices: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Test connection to Kite Connect.
     *
     * @return true if connected, false otherwise
     */
    @Override
    public boolean testConnection() {
        logger.info("Testing Kite Connect connection");

        try {
            // Try to fetch user profile
            String url = KITE_BASE_URL + "/user";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + kiteConfig.getApiKey() + ":" + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            boolean success = response.getStatusCode() == HttpStatus.OK;
            if (success) {
                logger.info("Kite Connect connection test successful");
            } else {
                logger.warn("Kite Connect connection test failed: {}", response.getStatusCode());
            }
            return success;

        } catch (Exception e) {
            logger.error("Error testing connection: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Place a market order.
     */
    @Override
    public OrderResponse placeMarketOrder(String symbol, Exchange exchange, TradeDirection direction,
                                          BigDecimal quantity) {
        OrderResponse order = new OrderResponse();
        order.setSymbol(symbol);
        order.setExchange(exchange);
        order.setDirection(direction);
        order.setQuantity(quantity);
        order.setType(OrderType.MARKET);
        return placeOrder(order);
    }

    /**
     * Place a limit order.
     */
    @Override
    public OrderResponse placeLimitOrder(String symbol, Exchange exchange, TradeDirection direction,
                                         BigDecimal quantity, BigDecimal limitPrice) {
        OrderResponse order = new OrderResponse();
        order.setSymbol(symbol);
        order.setExchange(exchange);
        order.setDirection(direction);
        order.setQuantity(quantity);
        order.setType(OrderType.LIMIT);
        order.setLimitPrice(limitPrice);
        return placeOrder(order);
    }

    /**
     * Place a stop-loss market order (SL-M).
     */
    @Override
    public OrderResponse placeStopLossMarketOrder(String symbol, Exchange exchange, TradeDirection direction,
                                                  BigDecimal quantity, BigDecimal stopPrice) {
        OrderResponse order = new OrderResponse();
        order.setSymbol(symbol);
        order.setExchange(exchange);
        order.setDirection(direction);
        order.setQuantity(quantity);
        order.setType(OrderType.STOP_LOSS);
        order.setStopPrice(stopPrice);
        return placeOrder(order);
    }

    /**
     * Execute with retry logic for transient failures.
     */
    private <T> T withRetry(java.util.function.Supplier<T> operation, String operationName) {
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                logger.debug("Executing {} (attempt {}/{}))", operationName, attempt + 1, MAX_RETRIES);
                return operation.get();
            } catch (RestClientException e) {
                lastException = e;
                logger.warn("Attempt {} failed for {}: {}", attempt + 1, operationName, e.getMessage());

                // Check for rate limit error
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    logger.info("Rate limit hit, waiting before retry");
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                // Exponential backoff for other errors
                if (attempt < MAX_RETRIES - 1) {
                    long delay = (long) (RETRY_DELAY_MS * Math.pow(2, attempt));
                    try {
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Unexpected error in {}: {}", operationName, e.getMessage(), e);
                throw e;
            }
        }

        logger.error("Operation {} failed after {} attempts", operationName, MAX_RETRIES, lastException);
        throw new RuntimeException(operationName + " failed after " + MAX_RETRIES + " attempts: " +
                (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }
}
