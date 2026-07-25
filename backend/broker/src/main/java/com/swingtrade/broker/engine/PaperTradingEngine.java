package com.swingtrade.broker.engine;

import com.swingtrade.broker.config.BrokerMode;
import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.broker.service.PaperTradingStateService;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.Signal;
import com.swingtrade.broker.model.Portfolio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core trading engine for paper trading functionality.
 * Integrates order management, position management, and risk controls.
 * Orchestrates trade execution based on signals from the SignalEngine.
 */
@Component
public class PaperTradingEngine {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingEngine.class);

    // Managers
    private final OrderManager orderManager;
    private final PositionManager positionManager;

    // Portfolio state
    private final Portfolio portfolio;

    // Configuration
    private final PaperTradingProperties properties;
    private final BigDecimal commissionRate;
    private final BigDecimal initialCapital;

    // Position counter for unique IDs
    private final AtomicLong positionCounter;
    private final AtomicLong orderCounter;

    // DB persistence bridge (setter-injected to avoid circular dependency)
    private PaperTradingStateService stateService;

    @Autowired
    public void setStateService(PaperTradingStateService stateService) {
        this.stateService = stateService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initState() {
        if (stateService != null) {
            stateService.loadState();
        }
    }

    /**
     * Creates a new PaperTradingEngine with specified configuration.
     *
     * @param orderManager the order manager
     * @param positionManager the position manager
     * @param initialCapital the initial capital
     */
    @Autowired
    public PaperTradingEngine(OrderManager orderManager,
                              PositionManager positionManager,
                              PaperTradingProperties properties) {
        this.orderManager = orderManager;
        this.positionManager = positionManager;
        this.properties = properties;
        this.initialCapital = properties.getInitialBalance();
        this.portfolio = new Portfolio("default-portfolio", this.initialCapital);
        this.commissionRate = BigDecimal.valueOf(0.05); // 5 paise per share
        this.positionCounter = new AtomicLong(0);
        this.orderCounter = new AtomicLong(0);
    }

    /**
     * Places a market buy order based on a signal.
     * This is the primary method for initiating trades.
     *
     * @param signal the trading signal
     * @param currentPrice the current market price
     * @return the created order if valid
     * @throws IllegalStateException if position limits are reached
     */
    public Order executeSignal(Signal signal, BigDecimal currentPrice) {
        if (signal == null || !signal.isBuySignal()) {
            logger.debug("Ignoring non-BUY signal: {}", signal);
            return null;
        }

        // Validate position capacity (default 100 shares if no specific quantity in signal)
        int defaultQuantity = 100;
        if (!validatePositionCapacity(currentPrice, defaultQuantity)) {
            logger.warn("Cannot execute signal for {}: position capacity exceeded", signal.symbol());
            return null;
        }

        // Calculate position size based on risk
        BigDecimal quantity = calculatePositionSize(signal.symbol(), currentPrice, signal.stopLoss());

        // Create order
        Order order = orderManager.createBuyOrder(signal.symbol(), quantity.intValue(), currentPrice);

        // Attach signal metadata
        java.util.Map<String, Object> signalProps = new java.util.HashMap<>();
        signalProps.put("signalId", signal.id() != null ? signal.id().toString() : "");
        signalProps.put("signalReason", signal.reasoning() != null ? signal.reasoning() : "");
        signalProps.put("confidence", signal.confidence() != null ? signal.confidence().toString() : "0");
        signalProps.put("stopLoss", signal.stopLoss() != null ? signal.stopLoss().toString() : "0");
        signalProps.put("target", signal.target() != null ? signal.target().toString() : "0");
        signalProps.put("riskReward", signal.riskReward() != null ? signal.riskReward().toString() : "0");
        signalProps.put("entryDate", LocalDate.now().toString());
        order.setAdditionalProperties(signalProps);

        logger.info("Executing BUY order for {} at {}: signal confidence={}, SL={}, Target={}",
            signal.symbol(), currentPrice, signal.confidence(), signal.stopLoss(), signal.target());

        return order;
    }

    /**
     * Validates if new position can be opened based on capacity constraints.
     *
     * @param entryPrice the intended entry price
     * @param quantity the intended quantity
     * @return true if position can be opened
     */
    public boolean validatePositionCapacity(BigDecimal entryPrice, int quantity) {
        if (positionManager.hasReachedPositionLimit(properties.getMaxConcurrentPositions())) {
            logger.warn("Cannot open position: maximum positions ({}) reached", properties.getMaxConcurrentPositions());
            return false;
        }

        BigDecimal positionValue = entryPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal capitalRatio = positionValue.divide(portfolio.getCurrentCapital(), 4, RoundingMode.HALF_UP);

        BigDecimal maxCapitalRatio = maxCapitalPerPositionDiv100();
        if (capitalRatio.compareTo(maxCapitalRatio) > 0) {
            logger.warn("Cannot open position for {}: position value {} exceeds {}% of capital",
                entryPrice, positionValue, properties.getMaxCapitalPerPosition());
            return false;
        }

        return true;
    }

    /**
     * Calculates position size based on risk parameters.
     * Uses ATR-based position sizing to limit risk to a fixed percentage of capital.
     *
     * @param symbol the trading symbol
     * @param entryPrice the entry price
     * @param stopLoss the stop loss price
     * @return the calculated position size
     */
    public BigDecimal calculatePositionSize(String symbol, BigDecimal entryPrice, BigDecimal stopLoss) {
        // Default position size calculation (simplified)
        // In production, this would use ATR and risk-per-trade parameters

        BigDecimal riskPerTrade = BigDecimal.valueOf(1).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP); // 1% max risk per trade
        BigDecimal riskPerShare = entryPrice.subtract(stopLoss);
        BigDecimal maxRiskAmount = portfolio.getCurrentCapital().multiply(riskPerTrade);

        if (riskPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            // Default to 100 shares if risk calculation is invalid
            return BigDecimal.valueOf(100);
        }

        BigDecimal quantity = maxRiskAmount.divide(riskPerShare, 0, RoundingMode.DOWN);
        return quantity.max(BigDecimal.valueOf(100)); // Minimum 100 shares
    }

    /**
     * Updates positions with new candle data and checks for SL/TP triggers.
     *
     * @param candleData the OHLCV candle data
     */
    public void updatePositions(OhlcvCandle candleData) {
        List<Position> updatedPositions = positionManager.updatePositionsWithCandleData(
            candleData.symbol(), candleData);

        for (Position position : updatedPositions) {
            logger.debug("Updated position {} for {} at price {}: P&L={}",
                position.positionId(), position.symbol(), position.currentPrice(),
                position.unrealizedPnL());
        }
    }

    /**
     * Checks all open positions against candle data for SL/TP triggers.
     *
     * @param symbol the trading symbol
     * @param candle the candle data
     */
    public void checkPositionTriggers(String symbol, OhlcvCandle candle) {
        List<Position> openPositions = positionManager.getOpenPositions();
        for (Position position : openPositions) {
            if (position.symbol().equals(symbol)) {
                positionManager.checkPositionTriggers(position, candle);
            }
        }
    }

    /**
     * Executes a pending order at the given price.
     *
     * @param orderId the order to execute
     * @param executionPrice the price at which to execute
     * @return the executed order
     */
    public Order executePendingOrder(String orderId, BigDecimal executionPrice) {
        Order order = orderManager.executeOrder(orderId, executionPrice);

        // Create position from filled order
        if (order.getStatus() == OrderStatus.FILLED) {
            Position position = createPositionFromOrder(order);

            // Update portfolio
            updatePortfolioAfterEntry(order, position);

            logger.info("Position {} created from order {} at {}",
                position.positionId(), orderId, executionPrice);

            // Persist
            if (stateService != null) {
                stateService.saveOrder(order);
                stateService.savePosition(position);
                stateService.savePortfolio();
            }
        }

        return order;
    }

    /**
     * Creates a position from an executed order.
     *
     * @param order the executed order
     * @return the created position
     */
    public Position createPositionFromOrder(Order order) {
        if (order.getStatus() != OrderStatus.FILLED) {
            throw new IllegalStateException("Can only create position from FILLED order: " + order.getStatus());
        }

        String positionId = generatePositionId();

        // Calculate stop loss and target from order metadata
        BigDecimal stopLoss = calculateStopLossFromOrder(order);
        BigDecimal target = calculateTargetFromOrder(order);

        // Extract ATR if available, otherwise use default
        BigDecimal atr = getATRFromOrder(order);

        return positionManager.createPosition(
            positionId,
            order.getSymbol(),
            order.getDirection(),
            order.getQuantity().intValue(),
            order.getPrice(),
            atr,
            order.getAdditionalProperties() != null
                ? (String) order.getAdditionalProperties().get("signalReason")
                : null
        );
    }

    /**
     * Calculates stop loss price based on ATR.
     *
     * @param order the order
     * @return calculated stop loss
     */
    public BigDecimal calculateStopLossFromOrder(Order order) {
        if (order == null || order.getAdditionalProperties() == null) {
            return null;
        }

        // Check if stop loss is provided in signal metadata
        Object stopLossObj = order.getAdditionalProperties().get("stopLoss");
        if (stopLossObj instanceof String) {
            return new BigDecimal((String) stopLossObj);
        }

        // Calculate from ATR if available
        BigDecimal atr = getATRFromOrder(order);
        if (atr != null && atr.compareTo(BigDecimal.ZERO) > 0) {
            return positionManager.calculateStopLoss(order.getPrice(), order.getDirection(), atr);
        }

        return null;
    }

    /**
     * Calculates target price based on risk-reward ratio.
     *
     * @param order the order
     * @return calculated target
     */
    public BigDecimal calculateTargetFromOrder(Order order) {
        if (order == null || order.getAdditionalProperties() == null) {
            return null;
        }

        // Check if target is provided in signal metadata
        Object targetObj = order.getAdditionalProperties().get("target");
        if (targetObj instanceof String) {
            return new BigDecimal((String) targetObj);
        }

        // Calculate from stop loss if available
        BigDecimal stopLoss = calculateStopLossFromOrder(order);
        if (stopLoss != null) {
            return positionManager.calculateTarget(order.getPrice(), stopLoss, order.getDirection());
        }

        return null;
    }

    /**
     * Extracts ATR from order metadata.
     *
     * @param order the order
     * @return ATR value or null
     */
    public BigDecimal getATRFromOrder(Order order) {
        if (order == null || order.getAdditionalProperties() == null) {
            return null;
        }

        Object atrObj = order.getAdditionalProperties().get("atr");
        if (atrObj instanceof String) {
            return new BigDecimal((String) atrObj);
        }

        return null;
    }

    /**
     * Updates portfolio after position entry.
     *
     * @param order the filled order
     * @param position the created position
     */
    private void updatePortfolioAfterEntry(Order order, Position position) {
        BigDecimal positionValue = order.getPrice().multiply(order.getQuantity());
        BigDecimal commission = calculateCommission(order);

        // Update portfolio capital
        portfolio.setCurrentCapital(
            portfolio.getCurrentCapital()
                .subtract(positionValue)
                .subtract(commission)
        );
    }

    /**
     * Calculates commission for an order.
     *
     * @param order the order
     * @return calculated commission
     */
    public BigDecimal calculateCommission(Order order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal quantity = order.getQuantity() != null ? order.getQuantity() : BigDecimal.ZERO;
        return quantity.multiply(commissionRate);
    }

    /**
     * Performs partial exit of a position.
     *
     * @param positionId the position to exit
     * @param exitRatio the ratio to exit (0.5 = 50%)
     * @param exitPrice the exit price
     * @return the updated position
     */
    public Position partialExitPosition(String positionId, BigDecimal exitRatio, BigDecimal exitPrice) {
        // Capture quantity before partial exit (this is the original at call time)
        BigDecimal currentQty = BigDecimal.valueOf(positionManager.getPosition(positionId).quantity());
        BigDecimal exitedQuantity = currentQty.multiply(exitRatio);

        Position position = positionManager.partialExitPosition(positionId, exitRatio, exitPrice);

        // Update portfolio with cash proceeds from exited shares
        BigDecimal exitValue = exitedQuantity.multiply(exitPrice);
        BigDecimal commission = exitedQuantity.multiply(commissionRate);
        portfolio.setCurrentCapital(portfolio.getCurrentCapital().add(exitValue.subtract(commission)));

        logger.info("Partial exit completed for position {}: exited={}, remaining={}",
            positionId, exitedQuantity, position.quantity());

        // Persist
        if (stateService != null) {
            stateService.savePosition(position);
            stateService.savePortfolio();
        }

        return position;
    }

    /**
     * Closes a position by its database ID.
     * Looks up the position using "POS_{id}" format and closes it at current market price.
     *
     * @param positionId the database position ID
     * @return the closed position
     * @throws IllegalArgumentException if position not found
     */
    public Position closePosition(Long positionId) {
        String posId = "POS_" + String.format("%08d", positionId);
        Optional<Position> position = getPosition(posId);
        if (position.isEmpty()) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        BigDecimal exitPrice = position.get().currentPrice();
        String reason = "manual_close";

        closePosition(posId, exitPrice, reason);
        return position.get();
    }

    /**
     * Closes a position completely.
     *
     * @param positionId the position to close
     * @param exitPrice the exit price
     * @param reason the reason for closing
     */
    public void closePosition(String positionId, BigDecimal exitPrice, String reason) {
        Position position = positionManager.closePosition(positionId, exitPrice, reason);

        // Update portfolio
        BigDecimal exitValue = exitPrice.multiply(BigDecimal.valueOf(position.quantity()));
        BigDecimal commission = calculateCommissionForPosition(position);
        BigDecimal netProceeds = exitValue.subtract(commission);

        portfolio.setCurrentCapital(portfolio.getCurrentCapital().add(netProceeds));

        logger.info("Position {} closed: P&L={}, Reason={}",
            positionId, position.unrealizedPnL(), reason);

        // Persist
        if (stateService != null) {
            stateService.closePosition(positionId, position);
            stateService.savePortfolio();
        }
    }

    /**
     * Calculates commission for position closure.
     *
     * @param position the position
     * @return commission amount
     */
    private BigDecimal calculateCommissionForPosition(Position position) {
        BigDecimal quantity = BigDecimal.valueOf(position.quantity() != null ? position.quantity() : 0);
        return quantity.multiply(commissionRate);
    }

    /**
     * Generates a unique position ID.
     *
     * @return unique position ID
     */
    private String generatePositionId() {
        return "POS_" + String.format("%08d", positionCounter.incrementAndGet());
    }

    /**
     * Gets the current portfolio.
     *
     * @return the portfolio
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
        return positionManager.getOpenPositions();
    }

    /**
     * Finds an open position by symbol.
     *
     * @param symbol the trading symbol
     * @return the position if found, null otherwise
     */
    public Position findOpenPositionBySymbol(String symbol) {
        return positionManager.findOpenPositionBySymbol(symbol);
    }

    /**
     * Gets a specific position by ID.
     *
     * @param positionId the position ID
     * @return the position if found
     */
    public Optional<Position> getPosition(String positionId) {
        return Optional.ofNullable(positionManager.getPosition(positionId));
    }

    /**
     * Gets all closed positions.
     *
     * @return list of closed positions
     */
    public List<Position> getClosedPositions() {
        return positionManager.getClosedPositions();
    }

    /**
     * Gets the total unrealized P&L.
     *
     * @return total unrealized P&L
     */
    public BigDecimal getTotalUnrealizedPnL() {
        return positionManager.getTotalUnrealizedPnL();
    }

    /**
     * Gets the total realized P&L.
     *
     * @return total realized P&L
     */
    public BigDecimal getTotalRealizedPnL() {
        return positionManager.getTotalRealizedPnL();
    }

    /**
     * Gets the total P&L (realized + unrealized).
     *
     * @return total P&L
     */
    public BigDecimal getTotalPnL() {
        return getTotalRealizedPnL().add(getTotalUnrealizedPnL());
    }

    /**
     * Gets the return percentage.
     *
     * @return return percentage
     */
    public BigDecimal getReturnPercentage() {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getTotalPnL()
            .divide(initialCapital, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Gets the current cash balance.
     *
     * @return current cash balance
     */
    public BigDecimal getCurrentCash() {
        return portfolio.getCurrentCapital();
    }

    /**
     * Gets the maximum number of concurrent positions.
     *
     * @return maximum positions
     */
    public int getMaxConcurrentPositions() {
        return properties.getMaxConcurrentPositions();
    }

    /**
     * Gets the maximum capital percentage per position.
     *
     * @return max capital percentage
     */
    public BigDecimal getMaxCapitalPerPosition() {
        return properties.getMaxCapitalPerPosition();
    }

    private BigDecimal maxCapitalPerPositionDiv100() {
        return properties.getMaxCapitalPerPosition().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    /**
     * Checks if there is capacity for a new position.
     *
     * @param entryPrice the entry price
     * @param quantity the quantity
     * @return true if position can be opened
     */
    public boolean canOpenPosition(BigDecimal entryPrice, int quantity) {
        return validatePositionCapacity(entryPrice, quantity);
    }

    /**
     * Gets the number of open positions.
     *
     * @return position count
     */
    public int getOpenPositionCount() {
        return positionManager.getOpenPositionCount();
    }

    /**
     * Gets the initial capital.
     *
     * @return initial capital
     */
    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    /**
     * Updates all positions with new candle data from domain object.
     *
     * @param candle the domain candle
     */
    public void updatePositionsFromDomain(OhlcvCandle candle) {
        List<Position> updated = positionManager.updatePositionsWithCandleData(candle.symbol(), candle);

        // Check triggers for each updated position
        for (Position position : updated) {
            if (position.status() == PositionStatus.OPEN) {
                // Persist updated price/P&L
                if (stateService != null) {
                    stateService.savePosition(position);
                }
                continue;
            }

            logger.info("Position {} status changed to: {}",
                position.positionId(), position.status());

            // Persist closed position
            if (stateService != null) {
                stateService.closePosition(position.positionId(), position);
                stateService.savePortfolio();
            }
        }
    }

    /**
     * Gets pending orders.
     *
     * @return list of pending orders
     */
    public List<Order> getPendingOrders() {
        return orderManager.getPendingOrders();
    }

    /**
     * Cancels a pending order.
     *
     * @param orderId the order ID
     * @return true if cancelled
     */
    public boolean cancelOrder(String orderId) {
        return orderManager.cancelOrder(orderId);
    }

    /**
     * Clears all state (for testing).
     */
    public void clearAll() {
        positionManager.clearAllPositions();
        orderManager.clearAllOrders();
        portfolio.setCurrentCapital(initialCapital);
        positionCounter.set(0);
        orderCounter.set(0);
    }
}
