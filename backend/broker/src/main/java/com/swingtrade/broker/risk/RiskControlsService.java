package com.swingtrade.broker.risk;

import com.swingtrade.broker.kite.KiteConnectClient;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.broker.model.OrderResponse;
import com.swingtrade.domain.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Main risk controls orchestrator.
 * Coordinates all risk checks before trade execution.
 * Integrates KillSwitchService and CapitalTracker for live trading.
 */
@Component
public class RiskControlsService implements RiskControls {

    private static final Logger logger = LoggerFactory.getLogger(RiskControlsService.class);

    private final PositionLimitChecker positionLimitChecker;
    private final DailyLossCircuitBreaker dailyLossCircuitBreaker;
    private final PositionSizeValidator positionSizeValidator;
    private final PositionManager positionManager;
    private final KiteConnectClient kiteConnectClient;
    private final KillSwitchService killSwitchService;
    private final CapitalTracker capitalTracker;

    @Autowired
    public RiskControlsService(PositionLimitChecker positionLimitChecker,
                               DailyLossCircuitBreaker dailyLossCircuitBreaker,
                               PositionSizeValidator positionSizeValidator,
                               PositionManager positionManager,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) KiteConnectClient kiteConnectClient,
                               KillSwitchService killSwitchService,
                               CapitalTracker capitalTracker) {
        this.positionLimitChecker = positionLimitChecker;
        this.dailyLossCircuitBreaker = dailyLossCircuitBreaker;
        this.positionSizeValidator = positionSizeValidator;
        this.positionManager = positionManager;
        this.kiteConnectClient = kiteConnectClient;
        this.killSwitchService = killSwitchService;
        this.capitalTracker = capitalTracker;

        logger.info("RiskControlsService initialized with KillSwitchService and CapitalTracker");
    }

    /**
     * Constructor for testing purposes.
     */
    public RiskControlsService(PositionLimitChecker positionLimitChecker,
                               DailyLossCircuitBreaker dailyLossCircuitBreaker,
                               PositionSizeValidator positionSizeValidator,
                               PositionManager positionManager,
                               KiteConnectClient kiteConnectClient,
                               KillSwitchService killSwitchService,
                               CapitalTracker capitalTracker,
                               boolean killSwitchActive) {
        this.positionLimitChecker = positionLimitChecker;
        this.dailyLossCircuitBreaker = dailyLossCircuitBreaker;
        this.positionSizeValidator = positionSizeValidator;
        this.positionManager = positionManager;
        this.kiteConnectClient = kiteConnectClient;
        this.killSwitchService = killSwitchService;
        this.capitalTracker = capitalTracker;
    }

    /**
     * Full pre-trade risk check before placing an order.
     *
     * @param orderResponse the order to validate
     * @param marketPrice current market price for the symbol
     * @return RiskCheckResult with all check results
     */
    public RiskCheckResult preTradeCheck(OrderResponse orderResponse, BigDecimal marketPrice) {
        logger.info("Starting pre-trade risk check for order: {}", orderResponse.getSymbol());

        RiskCheckResult result = new RiskCheckResult();
        result.setCheckType("PRE_TRADE_CHECK");

        // 1. Check kill switch
        if (killSwitchService.isActive()) {
            result.addError("KILL SWITCH ACTIVE. All trading is halted.");
            result.addInfo("Kill switch reason: {}", killSwitchService.getReason());
            return result;
        }

        // 2. Check daily loss circuit breaker
        RiskCheckResult circuitBreakerResult = dailyLossCircuitBreaker.canPlaceTrade(
                calculateTradeValue(orderResponse, marketPrice),
                calculateEstimatedRisk(orderResponse, marketPrice)
        );
        result.getMessages().addAll(circuitBreakerResult.getMessages());
        result.setPassed(circuitBreakerResult.isPassed());
        if (!circuitBreakerResult.isPassed()) {
            return result;
        }

        // 3. Get current positions and check capital limits
        List<Position> currentPositions = positionManager.getOpenPositions();
        BigDecimal orderValue = calculateTradeValue(orderResponse, marketPrice);

        // 4. Check capital limits
        RiskCheckResult capitalResult = capitalTracker.enforceLimits(currentPositions, orderValue);
        result.getMessages().addAll(capitalResult.getMessages());
        result.setPassed(capitalResult.isPassed());
        if (!capitalResult.isPassed()) {
            return result;
        }

        // 5. Check position limits
        BigDecimal estimatedValue = calculateTradeValue(orderResponse, marketPrice);
        RiskCheckResult positionLimitResult = positionLimitChecker.canAddPosition(estimatedValue);
        result.getMessages().addAll(positionLimitResult.getMessages());
        result.setPassed(positionLimitResult.isPassed());
        if (!positionLimitResult.isPassed()) {
            return result;
        }

        // 6. Check position size
        RiskCheckResult sizeResult = positionSizeValidator.validatePositionSize(estimatedValue);
        result.getMessages().addAll(sizeResult.getMessages());
        if (!sizeResult.isPassed() && sizeResult.hasErrors()) {
            result.setPassed(false);
            return result;
        }

        // All checks passed
        result.addInfo("All pre-trade risk checks passed for: {}", orderResponse.getSymbol());
        return result;
    }

    /**
     * Quick risk check without market price (uses order price if available).
     */
    public RiskCheckResult quickPreTradeCheck(OrderResponse orderResponse) {
        BigDecimal marketPrice = orderResponse.getPrice() != null ? orderResponse.getPrice() : BigDecimal.ZERO;
        return preTradeCheck(orderResponse, marketPrice);
    }

    /**
     * Update risk controls with current portfolio state.
     */
    public void updateRiskState() {
        logger.info("Updating risk control state");

        // Update daily P&L tracker
        dailyLossCircuitBreaker.updateWithCurrentPositions();

        // Log current state
        logger.info("Current positions: {}, Daily P&L: ₹{} ({}%)",
                positionManager.getOpenPositionCount(),
                dailyLossCircuitBreaker.getCurrentDailyPnL(),
                dailyLossCircuitBreaker.getLossPercent());
    }

    /**
     * Calculate the total trade value.
     */
    private BigDecimal calculateTradeValue(OrderResponse order, BigDecimal marketPrice) {
        if (order.getQuantity() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal price = order.getLimitPrice() != null ? order.getLimitPrice() : marketPrice;
        return order.getQuantity().multiply(price != null ? price : BigDecimal.ZERO);
    }

    /**
     * Calculate estimated risk for a trade (potential loss).
     * For long positions: difference between entry and stop loss.
     */
    private BigDecimal calculateEstimatedRisk(OrderResponse order, BigDecimal marketPrice) {
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Simple risk calculation: assume 2% risk from entry price
        BigDecimal entryPrice = order.getLimitPrice() != null ? order.getLimitPrice() : marketPrice;
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal riskPercent = new BigDecimal("0.02"); // 2% risk
        BigDecimal riskPerShare = entryPrice.multiply(riskPercent);
        return order.getQuantity().multiply(riskPerShare);
    }

    /**
     * Get kill switch status.
     */
    public boolean isKillSwitchActive() {
        return killSwitchService.isActive();
    }

    /**
     * Set kill switch status via KillSwitchService.
     */
    public void setKillSwitchActive(boolean active) {
        if (active) {
            killSwitchService.enableKillSwitch("Set via API");
            dailyLossCircuitBreaker.openCircuit();
            logger.warn("Daily loss circuit breaker opened due to kill switch");
        } else {
            killSwitchService.disableKillSwitch();
            dailyLossCircuitBreaker.closeCircuit();
            logger.info("Daily loss circuit breaker closed, trading resumed");
        }
    }

    /**
     * Get current position count.
     */
    public int getCurrentPositionCount() {
        return positionManager.getOpenPositionCount();
    }

    /**
     * Get remaining position capacity.
     */
    public int getRemainingPositionCapacity() {
        return positionLimitChecker.getRemainingPositionCapacity();
    }

    /**
     * Get current daily P&L.
     */
    public BigDecimal getCurrentDailyPnL() {
        return dailyLossCircuitBreaker.getCurrentDailyPnL();
    }

    /**
     * Get current daily loss percentage.
     */
    public BigDecimal getDailyLossPercent() {
        return dailyLossCircuitBreaker.getLossPercent();
    }
}
