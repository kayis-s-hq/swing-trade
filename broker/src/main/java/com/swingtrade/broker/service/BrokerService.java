package com.swingtrade.broker.service;

import com.swingtrade.broker.model.Order;
import com.swingtrade.broker.model.Position;
import com.swingtrade.broker.model.Portfolio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Interface for the broker service that manages paper trading operations.
 * Provides methods for placing orders, managing positions, and calculating P&L.
 */
public interface BrokerService {
    
    /**
     * Places a new order for a security.
     * 
     * @param order the order to place
     * @return the placed order with updated status
     */
    Order placeOrder(Order order);
    
    /**
     * Cancels an existing order.
     * 
     * @param orderId the ID of the order to cancel
     * @return true if order was successfully cancelled, false otherwise
     */
    boolean cancelOrder(String orderId);
    
    /**
     * Gets the current portfolio.
     * 
     * @return the portfolio object
     */
    Portfolio getPortfolio();
    
    /**
     * Gets all open positions.
     * 
     * @return list of open positions
     */
    List<Position> getOpenPositions();
    
    /**
     * Gets a specific position by ID.
     * 
     * @param positionId the ID of the position to retrieve
     * @return the position if found, Optional.empty() otherwise
     */
    Optional<Position> getPosition(String positionId);
    
    /**
     * Calculates the current profit and loss for a position.
     * 
     * @param position the position to calculate P&L for
     * @return the calculated profit and loss
     */
    BigDecimal calculateProfitLoss(Position position);
    
    /**
     * Gets the maximum number of concurrent positions allowed.
     * 
     * @return maximum number of concurrent positions
     */
    int getMaxConcurrentPositions();
    
    /**
     * Gets the maximum percentage of capital allowed per position.
     * 
     * @return maximum percentage of capital per position
     */
    BigDecimal getMaxCapitalPerPosition();
}
