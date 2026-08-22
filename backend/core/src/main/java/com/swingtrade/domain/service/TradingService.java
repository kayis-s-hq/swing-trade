package com.swingtrade.domain.service;

import com.swingtrade.domain.Order;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.Signal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Abstraction over trading engine operations.
 * Implemented by {@code PaperTradingEngine} in the broker module.
 */
public interface TradingService {

    List<Position> getOpenPositions();

    Position findOpenPositionBySymbol(String symbol);

    List<Position> getClosedPositions();

    Position closePosition(Long positionId, BigDecimal exitPrice, String reason);

    Position createPositionFromOrder(Order order);

    Order executePendingOrder(String orderId, BigDecimal executionPrice);

    BigDecimal getTotalUnrealizedPnL();

    BigDecimal getTotalRealizedPnL();

    BigDecimal getCurrentCash();

    BigDecimal getInitialCapital();

    int getOpenPositionCount();

    boolean canOpenPosition(BigDecimal entryPrice, int quantity);

    List<Order> getPendingOrders();

    Order executeSignal(Signal signal, BigDecimal currentPrice);

    BigDecimal getTotalPnL();

    BigDecimal getTotalValue();
}