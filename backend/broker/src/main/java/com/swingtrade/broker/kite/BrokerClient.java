package com.swingtrade.broker.kite;

import com.swingtrade.broker.model.OrderResponse;
import com.swingtrade.broker.model.Portfolio;
import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.TradeDirection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Interface for broker client operations.
 * Used to decouple service layer from concrete KiteConnectClient implementation.
 */
public interface BrokerClient {

    /**
     * Place an order through the broker.
     * @param orderResponse the order to place
     * @return updated order response with broker-assigned ID
     */
    OrderResponse placeOrder(OrderResponse orderResponse);

    /**
     * Place a market order.
     * @param symbol trading symbol
     * @param exchange exchange
     * @param direction trade direction
     * @param quantity order quantity
     * @return order response
     */
    OrderResponse placeMarketOrder(String symbol, Exchange exchange, TradeDirection direction, BigDecimal quantity);

    /**
     * Place a limit order.
     * @param symbol trading symbol
     * @param exchange exchange
     * @param direction trade direction
     * @param quantity order quantity
     * @param limitPrice limit price
     * @return order response
     */
    OrderResponse placeLimitOrder(String symbol, Exchange exchange, TradeDirection direction,
                                   BigDecimal quantity, BigDecimal limitPrice);

    /**
     * Place a stop-loss market order.
     * @param symbol trading symbol
     * @param exchange exchange
     * @param direction trade direction
     * @param quantity order quantity
     * @param stopPrice stop price
     * @return order response
     */
    OrderResponse placeStopLossMarketOrder(String symbol, Exchange exchange, TradeDirection direction,
                                            BigDecimal quantity, BigDecimal stopPrice);

    /**
     * Cancel an existing order.
     * @param orderId the order to cancel
     * @return true if cancelled, false otherwise
     */
    boolean cancelOrder(String orderId);

    /**
     * Modify an existing order.
     * @param orderId the order to modify
     * @param exchange exchange
     * @param symbol trading symbol
     * @param quantity new quantity
     * @param price new price
     * @return true if modified, false otherwise
     */
    boolean modifyOrder(String orderId, Exchange exchange, String symbol,
                        BigDecimal quantity, BigDecimal price);

    /**
     * Get all open positions.
     * @return list of positions
     */
    List<Position> getPositions();

    /**
     * Get a specific position.
     * @param symbol trading symbol
     * @return optional position
     */
    java.util.Optional<Position> getPosition(String symbol);

    /**
     * Get the portfolio.
     * @return portfolio object
     */
    Portfolio getPortfolio();

    /**
     * Get order history.
     * @return list of order responses
     */
    List<OrderResponse> getOrderHistory();

    /**
     * Get current market price for a symbol.
     * @param symbol trading symbol
     * @param exchange exchange
     * @return market price
     */
    BigDecimal getMarketPrice(String symbol, Exchange exchange);

    /**
     * Get market prices for multiple symbols.
     * @param symbols list of trading symbols
     * @param exchange exchange
     * @return map of symbol to price
     */
    Map<String, BigDecimal> getMarketPrices(List<String> symbols, Exchange exchange);

    /**
     * Test connection to the broker.
     * @return true if connected, false otherwise
     */
    boolean testConnection();

    /**
     * Check if the client is configured.
     * @return true if configured, false otherwise
     */
    boolean isConfigured();

    /**
     * Get the access token.
     * @return access token
     */
    String getAccessToken();

    /**
     * Get login URL for OAuth flow.
     * @return login URL
     */
    String generateLoginUrl();
}
