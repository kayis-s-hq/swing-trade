package com.swingtrade.broker.manager;

import com.swingtrade.broker.model.Order;
import com.swingtrade.broker.model.OrderStatus;
import com.swingtrade.broker.model.OrderType;
import com.swingtrade.broker.model.TradeDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles order creation, validation, and execution for the paper trading system.
 * Manages order lifecycle from creation to execution.
 */
@Component
public class OrderManager {

    private static final Logger logger = LoggerFactory.getLogger(OrderManager.class);

    // Order storage
    private final Map<String, Order> orders;

    // Configuration
    private static final BigDecimal MIN_ORDER_SIZE = BigDecimal.valueOf(1);
    private static final Integer DEFAULT_MAX_SLIPPAGE_PERCENT = 2;

    /**
     * Creates a new OrderManager with empty order storage.
     */
    public OrderManager() {
        this.orders = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new market order for immediate execution.
     *
     * @param symbol the trading symbol
     * @param direction the trade direction (LONG or SHORT)
     * @param quantity the number of shares
     * @param price the market price at order creation
     * @return the created order
     */
    public Order createMarketOrder(String symbol, TradeDirection direction, Integer quantity, BigDecimal price) {
        return createOrder(symbol, OrderType.MARKET, direction, quantity, price, null, null);
    }

    /**
     * Creates a new limit order that will execute at the specified price or better.
     *
     * @param symbol the trading symbol
     * @param direction the trade direction
     * @param quantity the number of shares
     * @param limitPrice the limit price
     * @return the created order
     */
    public Order createLimitOrder(String symbol, TradeDirection direction, Integer quantity, BigDecimal limitPrice) {
        return createOrder(symbol, OrderType.LIMIT, direction, quantity, limitPrice, limitPrice, null);
    }

    /**
     * Creates a stop loss order that becomes a market order when the stop price is reached.
     * This is used to limit losses when the price moves against the position.
     *
     * @param symbol the trading symbol
     * @param direction the trade direction (should match the position direction)
     * @param quantity the number of shares
     * @param stopPrice the stop price trigger (below entry for long, above for short)
     * @return the created stop loss order
     */
    public Order createStopLossOrder(String symbol, TradeDirection direction, Integer quantity, BigDecimal stopPrice) {
        return createOrder(symbol, OrderType.STOP_LOSS, direction, quantity, null, null, stopPrice);
    }

    /**
     * Creates a take profit order that becomes a market order when the target price is reached.
     * This is used to capture gains when the price moves in favor of the position.
     *
     * @param symbol the trading symbol
     * @param direction the trade direction (should match the position direction)
     * @param quantity the number of shares
     * @param targetPrice the target price trigger (above entry for long, below for short)
     * @return the created take profit order
     */
    public Order createTakeProfitOrder(String symbol, TradeDirection direction, Integer quantity, BigDecimal targetPrice) {
        return createOrder(symbol, OrderType.TAKE_PROFIT, direction, quantity, null, null, targetPrice);
    }

    /**
     * Creates a stop order that becomes a market order when the stop price is reached.
     *
     * @param symbol the trading symbol
     * @param direction the trade direction
     * @param quantity the number of shares
     * @param stopPrice the stop price trigger
     * @return the created order
     */
    public Order createStopOrder(String symbol, TradeDirection direction, Integer quantity, BigDecimal stopPrice) {
        return createOrder(symbol, OrderType.STOP, direction, quantity, null, null, stopPrice);
    }

    /**
     * Creates a stop-limit order that becomes a limit order when the stop price is reached.
     *
     * @param symbol the trading symbol
     * @param direction the trade direction
     * @param quantity the number of shares
     * @param stopPrice the stop price trigger
     * @param limitPrice the limit price for the order
     * @return the created order
     */
    public Order createStopLimitOrder(String symbol, TradeDirection direction, Integer quantity,
                                      BigDecimal stopPrice, BigDecimal limitPrice) {
        return createOrder(symbol, OrderType.STOP_LIMIT, direction, quantity, limitPrice, limitPrice, stopPrice);
    }

    /**
     * Creates a buy order (LONG direction).
     *
     * @param symbol the trading symbol
     * @param quantity the number of shares
     * @param price the price
     * @return the created order
     */
    public Order createBuyOrder(String symbol, Integer quantity, BigDecimal price) {
        return createMarketOrder(symbol, TradeDirection.LONG, quantity, price);
    }

    /**
     * Creates a sell order (SHORT direction).
     *
     * @param symbol the trading symbol
     * @param quantity the number of shares
     * @param price the price
     * @return the created order
     */
    public Order createSellOrder(String symbol, Integer quantity, BigDecimal price) {
        return createMarketOrder(symbol, TradeDirection.SHORT, quantity, price);
    }

    /**
     * Creates an order based on a trading signal.
     *
     * @param symbol the trading symbol
     * @param signalType the signal type (BUY, SELL)
     * @param quantity the number of shares
     * @param entryPrice the entry price from signal
     * @param stopLoss the stop loss price from signal
     * @param target the target price from signal
     * @return the created order
     */
    public Order createOrderFromSignal(
        String symbol,
        String signalType,
        Integer quantity,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal target
    ) {
        TradeDirection direction = "BUY".equalsIgnoreCase(signalType)
            ? TradeDirection.LONG : TradeDirection.SHORT;

        Order order = createMarketOrder(symbol, direction, quantity, entryPrice);

        // Attach signal parameters to order (stored as metadata)
        order.setAdditionalProperties(Map.of(
            "stopLoss", stopLoss != null ? stopLoss.toString() : null,
            "target", target != null ? target.toString() : null,
            "signalType", signalType
        ));

        return order;
    }

    /**
     * Internal method to create an order with full parameter control.
     *
     * @param symbol the trading symbol
     * @param type the order type
     * @param direction the trade direction
     * @param quantity the number of shares
     * @param price the price
     * @param limitPrice the limit price (for LIMIT orders)
     * @param stopPrice the stop price (for STOP orders)
     * @return the created order
     */
    private Order createOrder(
        String symbol,
        OrderType type,
        TradeDirection direction,
        Integer quantity,
        BigDecimal price,
        BigDecimal limitPrice,
        BigDecimal stopPrice
    ) {
        // Validate inputs
        validateOrder(symbol, type, direction, quantity, price, limitPrice, stopPrice);

        // Generate unique order ID
        String orderId = generateOrderId();

        // Create order
        Order order = new Order();
        order.setOrderId(orderId);
        order.setSymbol(symbol);
        order.setType(type);
        order.setDirection(direction);
        order.setQuantity(BigDecimal.valueOf(quantity));
        order.setPrice(price);
        order.setLimitPrice(limitPrice);
        order.setStopPrice(stopPrice);
        order.setStatus(OrderStatus.PENDING);
        order.setTimestamp(LocalDateTime.now());
        order.setCommission(BigDecimal.ZERO);

        // Store the order
        orders.put(orderId, order);

        logger.info("Created {} order {} for {}: {} @ {}",
            type.name(), orderId, symbol, quantity, price != null ? price : limitPrice != null ? limitPrice : stopPrice);

        return order;
    }

    /**
     * Validates order parameters.
     *
     * @param symbol the trading symbol
     * @param type the order type
     * @param direction the trade direction
     * @param quantity the number of shares
     * @param price the price
     * @param limitPrice the limit price
     * @param stopPrice the stop price
     * @throws IllegalArgumentException if validation fails
     */
    private void validateOrder(
        String symbol,
        OrderType type,
        TradeDirection direction,
        Integer quantity,
        BigDecimal price,
        BigDecimal limitPrice,
        BigDecimal stopPrice
    ) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }

        if (direction == null) {
            throw new IllegalArgumentException("Trade direction is required");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        if (quantity < MIN_ORDER_SIZE.intValue()) {
            throw new IllegalArgumentException("Minimum order size is " + MIN_ORDER_SIZE);
        }

        if (type == null) {
            throw new IllegalArgumentException("Order type is required");
        }

        // Validate price based on order type
        switch (type) {
            case MARKET:
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Market orders require a valid price");
                }
                break;

            case LIMIT:
                if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Limit orders require a valid limit price");
                }
                break;

            case STOP_LOSS:
            case TAKE_PROFIT:
            case STOP:
            case STOP_LIMIT:
                if (stopPrice == null || stopPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Stop orders require a valid stop price");
                }
                break;
        }
    }

    /**
     * Generates a unique order ID.
     *
     * @return unique order ID
     */
    private String generateOrderId() {
        return "ORD_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Validates an order against current market conditions.
     *
     * @param order the order to validate
     * @param currentPrice the current market price
     * @param maxSlippagePercent the maximum allowed slippage percentage
     * @return true if order can be executed
     */
    public boolean validateOrderForExecution(Order order, BigDecimal currentPrice, int maxSlippagePercent) {
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }

        OrderType type = order.getType();

        switch (type) {
            case MARKET:
                // Market orders always execute
                return true;

            case LIMIT:
                BigDecimal limitPrice = order.getLimitPrice();
                if (limitPrice == null) {
                    return false;
                }

                // For LONG: limit price must be <= current price (buy at or below limit)
                // For SHORT: limit price must be >= current price (sell at or above limit)
                if (order.getDirection() == TradeDirection.LONG) {
                    return limitPrice.compareTo(currentPrice) >= 0;
                } else {
                    return limitPrice.compareTo(currentPrice) <= 0;
                }

            case STOP:
                BigDecimal stopPrice = order.getStopPrice();
                if (stopPrice == null) {
                    return false;
                }

                // Stop order triggers when price reaches stop price
                if (order.getDirection() == TradeDirection.LONG) {
                    // Long: stop triggers when price drops to stopPrice
                    return currentPrice.compareTo(stopPrice) <= 0;
                } else {
                    // Short: stop triggers when price rises to stopPrice
                    return currentPrice.compareTo(stopPrice) >= 0;
                }

            case STOP_LIMIT:
                // Stop-limit combines stop and limit logic
                return validateStopLimit(order, currentPrice, maxSlippagePercent);

            default:
                return false;
        }
    }

    /**
     * Validates a stop-limit order.
     *
     * @param order the order to validate
     * @param currentPrice the current market price
     * @param maxSlippagePercent the maximum slippage allowed
     * @return true if order can be executed
     */
    private boolean validateStopLimit(Order order, BigDecimal currentPrice, int maxSlippagePercent) {
        BigDecimal stopPrice = order.getStopPrice();
        BigDecimal limitPrice = order.getLimitPrice();

        if (stopPrice == null || limitPrice == null) {
            return false;
        }

        // Check if stop has been triggered
        boolean stopTriggered;
        if (order.getDirection() == TradeDirection.LONG) {
            stopTriggered = currentPrice.compareTo(stopPrice) <= 0;
        } else {
            stopTriggered = currentPrice.compareTo(stopPrice) >= 0;
        }

        if (!stopTriggered) {
            return false;
        }

        // Check if limit price is still valid with slippage allowance
        BigDecimal maxSlippage = limitPrice.multiply(BigDecimal.valueOf(maxSlippagePercent).divide(
            BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        if (order.getDirection() == TradeDirection.LONG) {
            // For long: limit + slippage must be >= current price
            return limitPrice.add(maxSlippage).compareTo(currentPrice) >= 0;
        } else {
            // For short: limit - slippage must be <= current price
            return limitPrice.subtract(maxSlippage).compareTo(currentPrice) <= 0;
        }
    }

    /**
     * Executes a pending order.
     *
     * @param orderId the ID of the order to execute
     * @param executionPrice the price at which to execute
     * @return the executed order
     */
    public Order executeOrder(String orderId, BigDecimal executionPrice) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order cannot be executed - status: " + order.getStatus());
        }

        // Update order status
        order.setStatus(OrderStatus.EXECUTING);
        order.setPrice(executionPrice);

        // Simulate execution
        order.setStatus(OrderStatus.FILLED);
        order.setExecutionTime(LocalDateTime.now());

        logger.info("Executed order {} at {}", orderId, executionPrice);

        return order;
    }

    /**
     * Cancels a pending order.
     *
     * @param orderId the ID of the order to cancel
     * @return true if order was cancelled
     */
    public boolean cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            return false;
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.ACCEPTED) {
            logger.warn("Cannot cancel order {} with status: {}", orderId, order.getStatus());
            return false;
        }

        order.setStatus(OrderStatus.CANCELLED);
        logger.info("Cancelled order {}", orderId);

        return true;
    }

    /**
     * Gets an order by ID.
     *
     * @param orderId the order ID
     * @return the order if found
     */
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    /**
     * Gets all pending orders.
     *
     * @return list of pending orders
     */
    public List<Order> getPendingOrders() {
        return orders.values().stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.ACCEPTED)
            .toList();
    }

    /**
     * Gets all orders for a symbol.
     *
     * @param symbol the trading symbol
     * @return list of orders for the symbol
     */
    public List<Order> getOrdersBySymbol(String symbol) {
        return orders.values().stream()
            .filter(o -> o.getSymbol().equals(symbol))
            .toList();
    }

    /**
     * Gets all orders for a symbol and status.
     *
     * @param symbol the trading symbol
     * @param status the order status
     * @return list of matching orders
     */
    public List<Order> getOrdersBySymbolAndStatus(String symbol, OrderStatus status) {
        return orders.values().stream()
            .filter(o -> o.getSymbol().equals(symbol) && o.getStatus() == status)
            .toList();
    }

    /**
     * Gets all orders of a specific type.
     *
     * @param type the order type
     * @return list of orders of that type
     */
    public List<Order> getOrdersByType(OrderType type) {
        return orders.values().stream()
            .filter(o -> o.getType() == type)
            .toList();
    }

    /**
     * Gets the total number of orders.
     *
     * @return order count
     */
    public int getTotalOrderCount() {
        return orders.size();
    }

    /**
     * Gets the number of pending orders.
     *
     * @return pending order count
     */
    public int getPendingOrderCount() {
        return getPendingOrders().size();
    }

    /**
     * Clears all orders (for testing purposes).
     */
    public void clearAllOrders() {
        orders.clear();
    }

    /**
     * Gets the maximum allowed slippage percentage.
     *
     * @return maximum slippage percentage
     */
    public static int getDefaultMaxSlippagePercent() {
        return DEFAULT_MAX_SLIPPAGE_PERCENT;
    }

    /**
     * Gets the minimum order size.
     *
     * @return minimum order size
     */
    public static BigDecimal getMinOrderSize() {
        return MIN_ORDER_SIZE;
    }

    /**
     * Calculates the estimated commission for an order.
     *
     * @param order the order
     * @param commissionRate the commission rate per share
     * @return estimated commission
     */
    public BigDecimal calculateCommission(Order order, BigDecimal commissionRate) {
        if (order == null || commissionRate == null || commissionRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return order.getQuantity().multiply(commissionRate);
    }
}
