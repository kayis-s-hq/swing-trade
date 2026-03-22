package com.swingtrade.broker.kite;

import com.kiteconnect.KiteConnect;
import com.kiteconnect.models.*;
import com.swingtrade.broker.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Zerodha Kite Connect client for live trading integration.
 * Handles order placement, status polling, positions, and portfolio management.
 */
@Component
public class KiteConnectClient {

    private static final Logger logger = LoggerFactory.getLogger(KiteConnectClient.class);

    private final KiteConnect kiteConnect;
    private final KiteConfig kiteConfig;

    @Autowired
    public KiteConnectClient(KiteConfig kiteConfig) {
        this.kiteConfig = kiteConfig;

        // Initialize KiteConnect with API key
        this.kiteConnect = new KiteConnect();
        this.kiteConnect.setApiKey(kiteConfig.getApiKey());

        logger.info("KiteConnectClient initialized with API key: {}",
                kiteConfig.getApiKey().substring(0, Math.min(8, kiteConfig.getApiKey().length())) + "...");
    }

    /**
     * Set the access token for authenticated requests.
     * This is obtained after user authorization flow.
     */
    public void setAccessToken(String accessToken) {
        kiteConnect.setAccessToken(accessToken);
        logger.info("Access token set for KiteConnectClient");
    }

    /**
     * Generate login URL for OAuth authorization flow.
     */
    public String generateLoginUrl() {
        return kiteConnect.generateLoginUrl();
    }

    /**
     * Place a new order through Kite Connect.
     *
     * @param orderResponse the order to place
     * @return updated order response with broker-assigned ID
     */
    public OrderResponse placeOrder(OrderResponse orderResponse) {
        logger.info("Placing order for symbol: {}, type: {}, quantity: {}",
                orderResponse.getSymbol(), orderResponse.getType(), orderResponse.getQuantity());

        try {
            // Prepare order variables
            OrderVariables orderVars = new OrderVariables();
            orderVars.setExchange(orderResponse.getExchange().getExchangeCode());
            orderVars.setSymbol(orderResponse.getSymbol());
            orderVars.setProduct(getProductType(orderResponse.getDirection()));

            // Set order type
            orderVars.setOrderType(getKiteOrderType(orderResponse.getType()));

            // Set quantity
            orderVars.setQuantity(orderResponse.getQuantity().toString());

            // Set price based on order type
            if (orderResponse.getType() == OrderType.LIMIT && orderResponse.getLimitPrice() != null) {
                orderVars.setPrice(orderResponse.getLimitPrice().toString());
            }

            // Set transaction type (BUY/SELL)
            orderVars.setTransactionType(getTransactionType(orderResponse.getDirection()));

            // Place the order
            OrdersResponse response = kiteConnect.placeOrder(null, orderVars);

            // Update order response with broker details
            orderResponse.setBrokerOrderId(response.getOrder_id());
            orderResponse.setStatus(OrderStatus.ACCEPTED);
            orderResponse.setMessage("Order placed successfully with broker ID: " + response.getOrder_id());

            logger.info("Order placed successfully. Broker Order ID: {}", response.getOrder_id());

            return orderResponse;

        } catch (Exception e) {
            logger.error("Failed to place order: {}", e.getMessage(), e);
            orderResponse.setStatus(OrderStatus.CANCELLED);
            orderResponse.setMessage("Order placement failed: " + e.getMessage());
            return orderResponse;
        }
    }

    /**
     * Place a market order.
     */
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
     * Cancel an existing order.
     */
    public boolean cancelOrder(String orderId) {
        logger.info("Cancelling order: {}", orderId);

        try {
            kiteConnect.cancelOrder(orderId, null);
            logger.info("Order cancelled successfully: {}", orderId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to cancel order {}: {}", orderId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Modify an existing order.
     */
    public boolean modifyOrder(String orderId, Exchange exchange, String symbol,
                               BigDecimal quantity, BigDecimal price) {
        logger.info("Modifying order: {}", orderId);

        try {
            OrderVariables modifyVars = new OrderVariables();
            modifyVars.setExchange(exchange.getExchangeCode());
            modifyVars.setSymbol(symbol);
            modifyVars.setOrderType(getKiteOrderType(OrderType.LIMIT));

            if (price != null) {
                modifyVars.setPrice(price.toString());
            }

            if (quantity != null) {
                modifyVars.setQuantity(quantity.toString());
            }

            kiteConnect.modifyOrder(orderId, null, modifyVars);
            logger.info("Order modified successfully: {}", orderId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to modify order {}: {}", orderId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all open positions.
     */
    public List<Position> getPositions() {
        logger.info("Fetching open positions");

        try {
            HoldingsResponse response = kiteConnect.getHoldings();
            List<Position> positions = new ArrayList<>();

            for (HoldingsData data : response.getData()) {
                Position position = new Position();
                position.setPositionId(data.getInstrument_key());
                position.setSymbol(data.getInstrument_key());
                position.setExchange(Exchange.fromCode(data.getExchange()));
                position.setQuantity(new BigDecimal(data.getQuantity()));
                position.setAveragePrice(getAveragePrice(data));
                position.setStatus(PositionStatus.OPEN);
                position.setLastUpdated(new Date().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());

                positions.add(position);
            }

            logger.info("Fetched {} open positions", positions.size());
            return positions;

        } catch (Exception e) {
            logger.error("Failed to fetch positions: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get a specific position by symbol.
     */
    public Optional<Position> getPosition(String symbol) {
        List<Position> positions = getPositions();
        return positions.stream()
                .filter(p -> p.getSymbol().equals(symbol))
                .findFirst();
    }

    /**
     * Get the portfolio (holdings and cash).
     */
    public Portfolio getPortfolio() {
        logger.info("Fetching portfolio");

        try {
            Portfolio portfolio = new Portfolio();
            portfolio.setPortfolioId("kite_portfolio");

            // Get holdings
            HoldingsResponse holdingsResponse = kiteConnect.getHoldings();
            Map<String, Position> positions = new HashMap<>();

            BigDecimal totalValue = BigDecimal.ZERO;

            for (HoldingsData data : holdingsResponse.getData()) {
                Position position = new Position();
                position.setPositionId(data.getInstrument_key());
                position.setSymbol(data.getInstrument_key());
                position.setExchange(Exchange.fromCode(data.getExchange()));
                position.setQuantity(new BigDecimal(data.getQuantity()));
                position.setAveragePrice(getAveragePrice(data));

                positions.put(data.getInstrument_key(), position);
                totalValue = totalValue.add(getPositionValue(data));
            }

            portfolio.setPositions(positions);
            portfolio.setCurrentCapital(totalValue);

            logger.info("Portfolio fetched. Total value: {}", totalValue);
            return portfolio;

        } catch (Exception e) {
            logger.error("Failed to fetch portfolio: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get order history.
     */
    public List<OrderResponse> getOrderHistory() {
        logger.info("Fetching order history");

        try {
            OrdersResponse response = kiteConnect.getOrders(null);
            List<OrderResponse> orders = new ArrayList<>();

            if (response.getData() != null) {
                for (OrderData orderData : response.getData()) {
                    OrderResponse order = new OrderResponse();
                    order.setBrokerOrderId(orderData.getOrder_id());
                    order.setSymbol(orderData.getSymbol());
                    order.setExchange(Exchange.fromCode(orderData.getExchange()));
                    order.setType(parseOrderType(orderData.getOrder_type()));
                    order.setDirection(parseTradeDirection(orderData.getTransaction_type()));
                    order.setQuantity(new BigDecimal(orderData.getQuantity()));
                    order.setStatus(parseOrderStatus(orderData.getStatus()));
                    order.setPrice(new BigDecimal(orderData.getPrice()));
                    order.setMessage("Order status: " + orderData.getStatus());

                    orders.add(order);
                }
            }

            logger.info("Fetched {} orders from history", orders.size());
            return orders;

        } catch (Exception e) {
            logger.error("Failed to fetch order history: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get current market price for a symbol.
     */
    public BigDecimal getMarketPrice(String symbol, Exchange exchange) {
        logger.debug("Fetching market price for: {} on {}", symbol, exchange.getExchangeCode());

        try {
            // Kite Connect quote endpoint
            String instrumentKey = symbol + "-" + exchange.getExchangeCode().toLowerCase();
            QuoteResponse response = kiteConnect.getQuotes(Collections.singletonList(instrumentKey));

            if (response.getData() != null && response.getData().containsKey(instrumentKey)) {
                QuoteData quoteData = response.getData().get(instrumentKey);
                return new BigDecimal(quoteData.getLtp());
            }

            logger.warn("No price data available for: {}", symbol);
            return null;

        } catch (Exception e) {
            logger.error("Failed to fetch market price for {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get market quotes for multiple symbols.
     */
    public Map<String, BigDecimal> getMarketPrices(List<String> symbols, Exchange exchange) {
        logger.debug("Fetching market prices for {} symbols", symbols.size());

        Map<String, BigDecimal> prices = new HashMap<>();
        List<String> instrumentKeys = symbols.stream()
                .map(s -> s + "-" + exchange.getExchangeCode().toLowerCase())
                .collect(Collectors.toList());

        try {
            QuoteResponse response = kiteConnect.getQuotes(instrumentKeys);

            if (response.getData() != null) {
                for (Map.Entry<String, QuoteData> entry : response.getData().entrySet()) {
                    prices.put(entry.getKey(), new BigDecimal(entry.getValue().getLtp()));
                }
            }

            logger.info("Fetched prices for {} symbols", prices.size());
            return prices;

        } catch (Exception e) {
            logger.error("Failed to fetch market prices: {}", e.getMessage(), e);
            return prices;
        }
    }

    // Helper Methods

    private String getProductType(TradeDirection direction) {
        // For equity delivery
        return "CNC";
    }

    private String getKiteOrderType(OrderType orderType) {
        switch (orderType) {
            case MARKET:
                return "MARKET";
            case LIMIT:
                return "LIMIT";
            case STOP_LOSS:
                return "SL";
            default:
                return "MARKET";
        }
    }

    private String getTransactionType(TradeDirection direction) {
        return direction == TradeDirection.LONG ? "BUY" : "SELL";
    }

    private OrderType parseOrderType(String kiteOrderType) {
        if (kiteOrderType == null) return OrderType.MARKET;

        switch (kiteOrderType.toUpperCase()) {
            case "LIMIT":
                return OrderType.LIMIT;
            case "SL":
            case "SL-M":
                return OrderType.STOP_LOSS;
            default:
                return OrderType.MARKET;
        }
    }

    private TradeDirection parseTradeDirection(String transactionType) {
        if (transactionType == null) return TradeDirection.LONG;

        return transactionType.toUpperCase().equals("SELL")
                ? TradeDirection.SHORT
                : TradeDirection.LONG;
    }

    private OrderStatus parseOrderStatus(String kiteStatus) {
        if (kiteStatus == null) return OrderStatus.PENDING;

        switch (kiteStatus.toUpperCase()) {
            case "COMPLETE":
                return OrderStatus.FILLED;
            case "TRADED":
                return OrderStatus.FILLED;
            case "OPEN":
            case "PENDING":
                return OrderStatus.PENDING;
            case "CANCELLED":
            case "REJECTED":
                return OrderStatus.CANCELLED;
            case "PARTIAL":
                return OrderStatus.PARTIALLY_FILLED;
            default:
                return OrderStatus.PENDING;
        }
    }

    private BigDecimal getAveragePrice(HoldingsData data) {
        try {
            return new BigDecimal(data.getAvgPrice());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal getPositionValue(HoldingsData data) {
        try {
            BigDecimal quantity = new BigDecimal(data.getQuantity());
            BigDecimal ltp = new BigDecimal(data.getLtp());
            return quantity.multiply(ltp);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Test connection to Kite Connect.
     */
    public boolean testConnection() {
        try {
            // Try to get user profile
            UserProfileResponse response = kiteConnect.getUserProfile();
            logger.info("Kite Connect connection test successful. User: {}", response.getData().getName());
            return true;
        } catch (Exception e) {
            logger.error("Kite Connect connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
