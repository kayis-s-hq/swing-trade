package com.swingtrade.broker.risk;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.broker.model.OrderResponse;
import com.swingtrade.domain.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Concrete implementation of RiskControls that orchestrates all risk checks.
 * Delegates to PositionLimitChecker, PositionSizeValidator, DailyLossCircuitBreaker,
 * CapitalTracker, and KillSwitchService.
 */
@Service
public class RiskControlsService implements RiskControls {

    private static final Logger logger = LoggerFactory.getLogger(RiskControlsService.class);

    private final PositionLimitChecker positionLimitChecker;
    private final PositionSizeValidator positionSizeValidator;
    private final DailyLossCircuitBreaker dailyLossCircuitBreaker;
    private final CapitalTracker capitalTracker;
    private final KillSwitchService killSwitchService;
    private final PositionManager positionManager;
    private final BrokerProperties props;

    @Autowired
    public RiskControlsService(PositionLimitChecker positionLimitChecker,
                               PositionSizeValidator positionSizeValidator,
                               DailyLossCircuitBreaker dailyLossCircuitBreaker,
                               CapitalTracker capitalTracker,
                               KillSwitchService killSwitchService,
                               PositionManager positionManager,
                               BrokerProperties props) {
        this.positionLimitChecker = positionLimitChecker;
        this.positionSizeValidator = positionSizeValidator;
        this.dailyLossCircuitBreaker = dailyLossCircuitBreaker;
        this.capitalTracker = capitalTracker;
        this.killSwitchService = killSwitchService;
        this.positionManager = positionManager;
        this.props = props;
    }

    @Override
    public RiskCheckResult preTradeCheck(OrderResponse orderResponse, BigDecimal marketPrice) {
        if (orderResponse == null) {
            return new RiskCheckResult(false);
        }

        if (killSwitchService.isActive()) {
            logger.warn("Pre-trade check blocked: kill switch is active");
            return new RiskCheckResult(false, "Trading halted — kill switch is active");
        }

        if (!dailyLossCircuitBreaker.isTradingAllowed()) {
            logger.warn("Pre-trade check blocked: daily loss circuit breaker is open");
            return new RiskCheckResult(false, "Daily loss limit exceeded — trading halted");
        }

        BigDecimal orderValue = calculateOrderValue(orderResponse, marketPrice);
        if (orderValue == null || orderValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new RiskCheckResult(false, "Invalid order value: " + orderValue);
        }

        List<Position> openPositions = positionManager.getOpenPositions();

        RiskCheckResult result = new RiskCheckResult();
        result.setCheckType("PRE_TRADE");

        // Position limit check
        RiskCheckResult positionCheck = positionLimitChecker.canAddPosition(orderValue);
        if (!positionCheck.isPassed()) {
            result.addError("Position limit: " + firstMessage(positionCheck, "ERROR"));
            return result;
        }

        // Position size validation
        RiskCheckResult sizeCheck = positionSizeValidator.validatePositionSize(orderValue);
        if (!sizeCheck.isPassed()) {
            result.addError("Position size: " + firstMessage(sizeCheck, "ERROR"));
            return result;
        }

        // Capital limits
        RiskCheckResult capitalCheck = capitalTracker.enforceLimits(openPositions, orderValue);
        if (!capitalCheck.isPassed()) {
            result.addError("Capital limit: " + firstMessage(capitalCheck, "ERROR"));
            return result;
        }

        // Daily loss circuit breaker check
        BigDecimal estimatedRisk = orderValue.multiply(props.getMaxPositionSizePercentage().divide(BigDecimal.valueOf(100)));
        RiskCheckResult circuitCheck = dailyLossCircuitBreaker.canPlaceTrade(orderValue, estimatedRisk);
        if (!circuitCheck.isPassed()) {
            result.addError("Daily loss circuit: " + firstMessage(circuitCheck, "ERROR"));
            return result;
        }

        result.addInfo("All pre-trade checks passed for " + orderResponse.getSymbol());
        return result;
    }

    @Override
    public RiskCheckResult quickPreTradeCheck(OrderResponse orderResponse) {
        if (orderResponse == null) {
            return new RiskCheckResult(false, "Order response must not be null");
        }

        if (killSwitchService.isActive()) {
            return new RiskCheckResult(false, "Trading halted — kill switch is active");
        }

        if (!dailyLossCircuitBreaker.isTradingAllowed()) {
            return new RiskCheckResult(false, "Daily loss limit exceeded");
        }

        BigDecimal orderValue = calculateOrderValue(orderResponse, null);
        if (orderValue == null || orderValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new RiskCheckResult(false, "Invalid order value");
        }

        int remainingCapacity = positionLimitChecker.getRemainingPositionCapacity();
        if (remainingCapacity <= 0) {
            return new RiskCheckResult(false, "Maximum concurrent positions reached");
        }

        return new RiskCheckResult(true, "Quick pre-trade check passed");
    }

    @Override
    public void updateRiskState() {
        dailyLossCircuitBreaker.updateWithCurrentPositions();
        logger.debug("Risk state updated");
    }

    @Override
    public boolean isKillSwitchActive() {
        return killSwitchService.isActive();
    }

    @Override
    public void setKillSwitchActive(boolean active) {
        if (active) {
            killSwitchService.enableKillSwitch("Set via RiskControls");
        } else {
            killSwitchService.disableKillSwitch();
        }
    }

    @Override
    public int getCurrentPositionCount() {
        return positionLimitChecker.getCurrentPositionCount();
    }

    @Override
    public int getRemainingPositionCapacity() {
        return positionLimitChecker.getRemainingPositionCapacity();
    }

    @Override
    public BigDecimal getCurrentDailyPnL() {
        return dailyLossCircuitBreaker.getCurrentDailyPnL();
    }

    @Override
    public BigDecimal getDailyLossPercent() {
        return dailyLossCircuitBreaker.getLossPercent();
    }

    private BigDecimal calculateOrderValue(OrderResponse order, BigDecimal marketPrice) {
        BigDecimal quantity = order.getQuantity();
        BigDecimal price = order.getPrice();

        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                return quantity.multiply(price);
            }
            if (marketPrice != null && marketPrice.compareTo(BigDecimal.ZERO) > 0) {
                return quantity.multiply(marketPrice);
            }
        }
        return null;
    }

    private String firstMessage(RiskCheckResult result, String prefix) {
        return result.getMessages().stream()
                .filter(m -> m.startsWith(prefix + ":"))
                .map(m -> m.substring(prefix.length() + 2))
                .findFirst()
                .orElse(null);
    }
}
