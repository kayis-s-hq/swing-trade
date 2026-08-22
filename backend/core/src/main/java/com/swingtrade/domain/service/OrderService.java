package com.swingtrade.domain.service;

import com.swingtrade.domain.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Abstraction over order management operations.
 * Implemented by {@code OrderManager} in the broker module.
 */
public interface OrderService {

    Order createBuyOrder(String symbol, int quantity, BigDecimal price);

    Order createSellOrder(String symbol, int quantity, BigDecimal price);

    boolean cancelOrder(String orderId);

    List<Order> getPendingOrders();
}