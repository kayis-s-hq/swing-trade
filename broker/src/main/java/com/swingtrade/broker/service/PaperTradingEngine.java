package com.swingtrade.broker.service;

import com.swingtrade.broker.model.*;
import com.swingtrade.data.model.CandleData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper trading engine that simulates order execution at next day open.
 * This service handles the core logic for paper trading operations.
 */
@Service
public class PaperTradingEngine {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    // Portfolio management
    private final Portfolio portfolio;
    private final Map<String, Position> activePositions;
    private final Map<String, Order> activeOrders;
    
    // Configuration
    private static final int MAX_CONCURRENT_POSITIONS = 5;
    private static final BigDecimal MAX_CAPITAL_PER_POSITION = new BigDecimal("0.20"); // 20%
    
    /**
     * Creates a new paper trading engine with the specified initial capital.
     * 
     * @param initialCapital the initial capital for the portfolio
     */
    public PaperTradingEngine(BigDecimal initialCapital) {
        this.portfolio = new Portfolio("default-portfolio", initialCapital);
        this.activePositions = new ConcurrentHashMap<>();
        this.activeOrders = new ConcurrentHashMap<>();
    }
    
    /**
     * Places a new order in the paper trading system.
     * 
     * @param order the order to place
     * @return the placed order with updated status
     */
    public Order placeOrder(Order order) {
        // Validate order
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        // Validate portfolio constraints
        if (!validateOrderConstraints(order)) {
            throw new IllegalStateException("Order validation failed - maximum positions or capital exceeded");
        }
        
        // Update order status
        order.setStatus(OrderStatus.ACCEPTED);
        
        // Simulate immediate execution at next day open (in a real system this would wait until market open)
        order.setExecutionTime(LocalDateTime.now().plusDays(1));
        
        // Store the order
        activeOrders.put(order.getOrderId(), order);
        
        return order;
    }
    
    /**
     * Validates that an order complies with system constraints.
     * 
     * @param order the order to validate
     * @return true if validation passes, false otherwise
     */
    private boolean validateOrderConstraints(Order order) {
        // Check concurrent positions limit
        if (activePositions.size() >= MAX_CONCURRENT_POSITIONS) {
            return false;
        }
        
        // Check capital allocation constraint (20% max per position)
        BigDecimal positionValue = order.getPrice().multiply(order.getQuantity());
        BigDecimal capitalAllocation = positionValue.divide(portfolio.getCurrentCapital(), 4, RoundingMode.HALF_UP);
        
        return capitalAllocation.compareTo(MAX_CAPITAL_PER_POSITION) <= 0;
    }
    
    /**
     * Cancels an existing order.
     * 
     * @param orderId the ID of the order to cancel
     * @return true if order was successfully cancelled, false otherwise
     */
    public boolean cancelOrder(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return false;
        }
        
        Order order = activeOrders.get(orderId);
        if (order != null && order.getStatus() != OrderStatus.FILLED && order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
            return true;
        }
        
        return false;
    }
    
    /**
     * Executes an order immediately (simulating execution at next day open).
     * 
     * @param order the order to execute
     * @return the executed order
     */
    public Order executeOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        // Mark order as executing
        order.setStatus(OrderStatus.EXECUTING);
        
        // For simplicity, assume immediate execution
        order.setStatus(OrderStatus.FILLED);
        order.setExecutionTime(LocalDateTime.now());
        
        // Update portfolio based on order
        updatePortfolioFromOrder(order);
        
        return order;
    }
    
    /**
     * Updates the portfolio based on a completed order.
     * 
     * @param order the completed order
     */
    private void updatePortfolioFromOrder(Order order) {
        // In a more advanced system, we would:
        // 1. Calculate position changes
        // 2. Update profit/loss calculations
        // 3. Handle position closing when target or SL hit
        
        // For now, we just track the order in our system
        activeOrders.put(order.getOrderId(), order);
    }
    
    /**
     * Gets the current portfolio.
     * 
     * @return the portfolio object
     */
    public Portfolio getPortfolio() {
        return portfolio;
    }
    
    /**
     * Gets all open positions.
     * 
     * @return list of open positions
     */
    public List<Position> getOpenPositions() {
        return new ArrayList<>(activePositions.values());
    }
    
    /**
     * Gets a specific position by ID.
     * 
     * @param positionId the ID of the position to retrieve
     * @return the position if found, Optional.empty() otherwise
     */
    public Optional<Position> getPosition(String positionId) {
        return Optional.ofNullable(activePositions.get(positionId));
    }
    
    /**
     * Calculates the current profit and loss for a position.
     * 
     * @param position the position to calculate P&L for
     * @return the calculated profit and loss
     */
    public BigDecimal calculateProfitLoss(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        
        return position.getProfitLoss();
    }
    
    /**
     * Gets the maximum number of concurrent positions allowed.
     * 
     * @return maximum number of concurrent positions
     */
    public int getMaxConcurrentPositions() {
        return MAX_CONCURRENT_POSITIONS;
    }
    
    /**
     * Gets the maximum percentage of capital allowed per position.
     * 
     * @return maximum percentage of capital per position
     */
    public BigDecimal getMaxCapitalPerPosition() {
        return MAX_CAPITAL_PER_POSITION;
    }
    
    /**
     * Updates positions based on new candle data (daily price updates).
     * 
     * @param symbol the trading symbol
     * @param candleData the new candle data
     */
    public void updatePositionsWithCandleData(String symbol, CandleData candleData) {
        // In a real implementation, this would:
        // 1. Find any open positions for the symbol
        // 2. Update current prices
        // 3. Check for SL/TARGET hits
        // 4. Close positions if triggers are hit
        // 5. Recalculate P&L
        
        // For now, we'll just update the current price of any matching positions
        for (Position position : activePositions.values()) {
            if (position.getSymbol().equals(symbol)) {
                position.setCurrentPrice(candleData.getClose());
            }
        }
    }
}
