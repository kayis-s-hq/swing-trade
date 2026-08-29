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

    /**
     * The entry-side commission the engine would charge for a position of this
     * quantity, at the engine's own commission rate. Used to record a real fee
     * on the {@code Trade} audit record at entry, so the audit trail agrees
     * with what the engine actually deducts from portfolio cash.
     */
    BigDecimal calculateEntryCommission(int quantity);
}