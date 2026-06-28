package com.swingtrade.broker.service;

import com.swingtrade.broker.engine.PaperTradeEngine;
import com.swingtrade.broker.model.Order;
import com.swingtrade.broker.model.Portfolio;
import com.swingtrade.broker.model.Position;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the broker service for paper trading operations.
 * This service coordinates with the paper trading engine to provide trading functionality.
 */
@Primary
@Service
public class PaperTradingServiceImpl implements BrokerService {
    
    private final PaperTradeEngine paperTradeEngine;
    
    /**
     * Creates a new paper trading service implementation.
     * 
     * @param paperTradeEngine the underlying paper trading engine
     */
    public PaperTradingServiceImpl(PaperTradeEngine paperTradeEngine) {
        this.paperTradeEngine = paperTradeEngine;
    }
    
    /**
     * Places a new order for a security.
     * This method validates the order and delegates to the paper trading engine.
     * 
     * @param order the order to place
     * @return the placed order with updated status
     * @throws IllegalArgumentException if order is null
     * @throws IllegalStateException if order validation fails
     */
    @Override
    public Order placeOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        return paperTradeEngine.placeOrder(order);
    }
    
    /**
     * Cancels an existing order.
     * 
     * @param orderId the ID of the order to cancel
     * @return true if order was successfully cancelled, false otherwise
     */
    @Override
    public boolean cancelOrder(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return false;
        }
        
        return paperTradeEngine.cancelOrder(orderId);
    }
    
    /**
     * Gets the current portfolio.
     * 
     * @return the portfolio object
     */
    @Override
    public Portfolio getPortfolio() {
        return paperTradeEngine.getPortfolio();
    }
    
    /**
     * Gets all open positions.
     * 
     * @return list of open positions
     */
    @Override
    public List<Position> getOpenPositions() {
        return paperTradeEngine.getOpenPositions();
    }
    
    /**
     * Gets a specific position by ID.
     * 
     * @param positionId the ID of the position to retrieve
     * @return the position if found, Optional.empty() otherwise
     */
    @Override
    public Optional<Position> getPosition(String positionId) {
        Position position = paperTradeEngine.getPosition(positionId);
        return Optional.ofNullable(position);
    }
    
    /**
     * Calculates the current profit and loss for a position.
     * 
     * @param position the position to calculate P&L for
     * @return the calculated profit and loss
     * @throws IllegalArgumentException if position is null
     */
    @Override
    public BigDecimal calculateProfitLoss(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        
        return paperTradeEngine.calculateProfitLoss(position);
    }
    
    /**
     * Gets the maximum number of concurrent positions allowed.
     * 
     * @return maximum number of concurrent positions
     */
    @Override
    public int getMaxConcurrentPositions() {
        return paperTradeEngine.getMaxConcurrentPositions();
    }
    
    /**
     * Gets the maximum percentage of capital allowed per position.
     * 
     * @return maximum percentage of capital per position
     */
    @Override
    public BigDecimal getMaxCapitalPerPosition() {
        return paperTradeEngine.getMaxCapitalPerPosition();
    }
}
