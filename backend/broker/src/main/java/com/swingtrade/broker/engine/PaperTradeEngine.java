package com.swingtrade.broker.engine;

import com.swingtrade.broker.model.*;
import com.swingtrade.data.model.CandleData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core engine for paper trading functionality in the swing trading system.
 * Handles order processing, position management, and P&L calculations.
 */
@Component
public class PaperTradeEngine {
    
    // Portfolio management
    private final Map<String, Position> activePositions;
    private final Map<String, Order> activeOrders;
    private Portfolio portfolio;
    
    // Configuration
    private static final int MAX_CONCURRENT_POSITIONS = 5;
    private static final BigDecimal MAX_CAPITAL_PER_POSITION = new BigDecimal("0.20"); // 20%
    
    /**
     * Creates a new paper trade engine with the specified initial capital.
     * 
     * @param initialCapital the initial capital for the portfolio
     */
    public PaperTradeEngine(BigDecimal initialCapital) {
        this.portfolio = new Portfolio("default-portfolio", initialCapital);
        this.activePositions = new ConcurrentHashMap<>();
        this.activeOrders = new ConcurrentHashMap<>();
    }
    
    /**
     * Places a new order for a security.
     * This method validates constraints and simulates order execution.
     * 
     * @param order the order to place
     * @return the placed order with updated status
     * @throws IllegalStateException if order validation fails
     */
    public Order placeOrder(Order order) {
        // Validate input
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        // Validate portfolio constraints
        if (!validateOrderConstraints(order)) {
            throw new IllegalStateException(
                "Order validation failed - maximum positions (" + MAX_CONCURRENT_POSITIONS + 
                ") or capital (" + MAX_CAPITAL_PER_POSITION.multiply(BigDecimal.valueOf(100)) + "%) exceeded"
            );
        }
        
        // Update order status
        order.setStatus(OrderStatus.ACCEPTED);
        
        // Simulate order execution at next day open (this would be scheduled in a real system)
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
     * Processes an order execution (simulating market execution).
     * 
     * @param order the order to execute
     * @return the executed order
     * @throws IllegalArgumentException if order is null
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
        
        // In a more sophisticated system, we would:
        // 1. Update portfolio cash balance
        // 2. Create or update position
        // 3. Apply commissions
        // 4. Record transaction details
        
        // Store the executed order
        activeOrders.put(order.getOrderId(), order);
        
        return order;
    }
    
    /**
     * Creates a new position based on an executed order.
     * 
     * @param order the executed order
     * @return the created position
     */
    public Position createPositionFromOrder(Order order) {
        String positionId = "pos_" + System.currentTimeMillis();
        Position position = new Position(
            positionId,
            order.getSymbol(),
            order.getDirection(),
            order.getQuantity(),
            order.getPrice(),
            // In a real system, we'd calculate actual SL and target
            calculateStopLoss(order.getPrice(), order.getDirection()),
            calculateTakeProfit(order.getPrice(), order.getDirection())
        );
        
        // Set current price to entry price initially
        position.setCurrentPrice(order.getPrice());
        
        // Store position
        activePositions.put(positionId, position);
        
        // Update portfolio capital (simulated)
        portfolio.setCurrentCapital(
            portfolio.getCurrentCapital().subtract(
                order.getPrice().multiply(order.getQuantity())
            )
        );
        
        return position;
    }
    
    /**
     * Calculates stop loss price based on entry price and direction.
     * 
     * @param entryPrice the entry price
     * @param direction the trade direction
     * @return the calculated stop loss price
     */
    private BigDecimal calculateStopLoss(BigDecimal entryPrice, TradeDirection direction) {
        // Simplified: 5% stop loss
        BigDecimal slPercentage = new BigDecimal("0.05");
        if (direction == TradeDirection.LONG) {
            return entryPrice.multiply(BigDecimal.ONE.subtract(slPercentage));
        } else {
            return entryPrice.multiply(BigDecimal.ONE.add(slPercentage));
        }
    }
    
    /**
     * Calculates take profit price based on entry price and direction.
     * 
     * @param entryPrice the entry price
     * @param direction the trade direction
     * @return the calculated take profit price
     */
    private BigDecimal calculateTakeProfit(BigDecimal entryPrice, TradeDirection direction) {
        // Simplified: 10% take profit
        BigDecimal tpPercentage = new BigDecimal("0.10");
        if (direction == TradeDirection.LONG) {
            return entryPrice.multiply(BigDecimal.ONE.add(tpPercentage));
        } else {
            return entryPrice.multiply(BigDecimal.ONE.subtract(tpPercentage));
        }
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
    public Position getPosition(String positionId) {
        return activePositions.get(positionId);
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
                
                // Check if SL or TP is hit
                checkPositionTriggers(position, candleData);
            }
        }
    }
    
    /**
     * Checks if a position hit its SL or TP level.
     * 
     * @param position the position to check
     * @param candleData the latest candle data
     */
    private void checkPositionTriggers(Position position, CandleData candleData) {
        if (position.getDirection() == TradeDirection.LONG) {
            // For long positions, check if price drops below SL
            if (candleData.getLow().compareTo(position.getSlPrice()) <= 0) {
                closePosition(position, "Stop Loss Hit");
            }
            // Check if price reaches target
            else if (candleData.getHigh().compareTo(position.getTargetPrice()) >= 0) {
                closePosition(position, "Take Profit Hit");
            }
        } else {
            // For short positions, check if price rises above SL
            if (candleData.getHigh().compareTo(position.getSlPrice()) >= 0) {
                closePosition(position, "Stop Loss Hit");
            }
            // Check if price drops below target
            else if (candleData.getLow().compareTo(position.getTargetPrice()) <= 0) {
                closePosition(position, "Take Profit Hit");
            }
        }
    }
    
    /**
     * Closes a position and updates portfolio.
     * 
     * @param position the position to close
     * @param reason the reason for closing the position
     */
    private void closePosition(Position position, String reason) {
        // Update position status
        position.setStatus(PositionStatus.CLOSED);
        position.setExitTime(LocalDateTime.now());
        
        // In a real system, we would:
        // 1. Calculate final P&L
        // 2. Update portfolio capital with proceeds
        // 3. Remove from active positions
        
        // For demo purposes, we'll just mark it as closed
        System.out.println("Closed position " + position.getPositionId() + " - " + reason);
    }
    
    /**
     * Calculates the current profit and loss for a position.
     * 
     * @param position the position to calculate P&L for
     * @return the calculated profit and loss
     * @throws IllegalArgumentException if position is null
     */
    public BigDecimal calculateProfitLoss(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        
        // Calculate P&L based on current vs entry price
        BigDecimal priceChange = (position.getDirection() == TradeDirection.LONG) 
            ? position.getCurrentPrice().subtract(position.getEntryPrice()) 
            : position.getEntryPrice().subtract(position.getCurrentPrice());
            
        return priceChange.multiply(position.getQuantity());
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
}
