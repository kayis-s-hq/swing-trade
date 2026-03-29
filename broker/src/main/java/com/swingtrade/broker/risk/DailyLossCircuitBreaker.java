package com.swingtrade.broker.risk;

import com.swingtrade.broker.model.Position;
import com.swingtrade.broker.model.Portfolio;
import com.swingtrade.broker.manager.PositionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit breaker that halts trading if daily losses exceed a threshold.
 * Prevents catastrophic losses from emotional or runaway trading.
 */
@Component
public class DailyLossCircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(DailyLossCircuitBreaker.class);

    private final PositionManager positionManager;
    private BigDecimal dailyLossThresholdPercent;
    private BigDecimal initialCapital;

    // Track daily P&L
    private final Map<LocalDate, BigDecimal> dailyPnLTracker = new ConcurrentHashMap<>();

    // Circuit breaker state
    private volatile boolean isCircuitOpen = false;
    private LocalDateTime circuitOpenTime;
    private BigDecimal lossAtCircuitOpen;

    @Value("${broker.daily-loss-circuit-breaker:2.0}")
    public void setDailyLossThresholdPercent(BigDecimal dailyLossThresholdPercent) {
        this.dailyLossThresholdPercent = dailyLossThresholdPercent;
    }

    @Value("${broker.initial-capital:1000000}")
    public void setInitialCapital(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
    }

    @Autowired
    public DailyLossCircuitBreaker(PositionManager positionManager) {
        this.positionManager = positionManager;
        this.dailyLossThresholdPercent = BigDecimal.valueOf(2.0); // default
        this.initialCapital = BigDecimal.valueOf(1000000); // default
        logger.info("DailyLossCircuitBreaker initialized with {}% daily loss threshold",
                dailyLossThresholdPercent);
        resetDailyTracker();
    }

    /**
     * Constructor for testing purposes.
     */
    public DailyLossCircuitBreaker(PositionManager positionManager, BigDecimal dailyLossThresholdPercent, BigDecimal initialCapital) {
        this.positionManager = positionManager;
        this.dailyLossThresholdPercent = dailyLossThresholdPercent;
        this.initialCapital = initialCapital;
        resetDailyTracker();
    }

    /**
     * Check if trading is allowed today (circuit is closed).
     */
    public boolean isTradingAllowed() {
        LocalDate today = LocalDate.now();

        // Reset tracker if new day
        if (!dailyPnLTracker.containsKey(today)) {
            resetDailyTracker();
        }

        return !isCircuitOpen;
    }

    /**
     * Check if a new trade would exceed the daily loss limit.
     *
     * @param estimatedTradeValue estimated value of the trade
     * @param estimatedRisk estimated risk (potential loss) from the trade
     * @return RiskCheckResult indicating if trade is allowed
     */
    public RiskCheckResult canPlaceTrade(BigDecimal estimatedTradeValue, BigDecimal estimatedRisk) {
        logger.debug("Checking daily loss circuit breaker for trade with risk: {}", estimatedRisk);

        RiskCheckResult result = new RiskCheckResult();
        result.setCheckType("DAILY_LOSS_CIRCUIT_BREAKER");

        // Check if circuit is already open
        if (isCircuitOpen) {
            result.addError("Daily loss circuit breaker is OPEN. Trading halted for today.");
            result.addInfo(String.format("Circuit opened at: %s, Loss: %s (%s%%)",
                    circuitOpenTime, lossAtCircuitOpen, calculateLossPercent()));
            return result;
        }

        // Calculate current daily P&L
        BigDecimal currentDailyPnL = getCurrentDailyPnL();
        BigDecimal lossPercent = calculateLossPercent();

        logger.info("Current daily P&L: {} ({}%)", currentDailyPnL, lossPercent);

        // Check if adding this trade would exceed threshold
        BigDecimal projectedPnL = currentDailyPnL.subtract(estimatedRisk);
        BigDecimal projectedLossPercent = calculateLossPercentFromPnL(projectedPnL);

        BigDecimal thresholdAmount = initialCapital.multiply(dailyLossThresholdPercent).divide(BigDecimal.valueOf(100));

        if (projectedPnL.compareTo(thresholdAmount.negate()) < 0) {
            result.addError(String.format("Trade would exceed daily loss limit. Projected loss: %s (%s%%), Threshold: %s",
                    projectedPnL, projectedLossPercent, thresholdAmount));
            return result;
        }

        result.addInfo(String.format("Trade allowed. Current daily P&L: %s (%s%%), Projected P&L after trade: %s (%s%%)",
                currentDailyPnL, lossPercent,
                projectedPnL, projectedLossPercent));

        return result;
    }

    /**
     * Update the circuit breaker with actual P&L from positions.
     */
    public void updateWithCurrentPositions() {
        BigDecimal totalUnrealizedPnL = calculateTotalUnrealizedPnL();
        BigDecimal totalRealizedPnL = calculateTotalRealizedPnL();

        BigDecimal dailyPnL = totalUnrealizedPnL.add(totalRealizedPnL);
        dailyPnLTracker.put(LocalDate.now(), dailyPnL);

        logger.info("Updated daily P&L: ₹{} ({}%)", dailyPnL, calculateLossPercent());

        // Check if circuit should open
        if (dailyPnL.compareTo(initialCapital.multiply(dailyLossThresholdPercent).divide(BigDecimal.valueOf(100)).negate()) < 0) {
            openCircuit(dailyPnL);
        }
    }

    /**
     * Manually open the circuit breaker (e.g., from kill switch).
     */
    public void openCircuit() {
        openCircuit(getCurrentDailyPnL());
    }

    /**
     * Manually close the circuit breaker (resume trading).
     */
    public void closeCircuit() {
        isCircuitOpen = false;
        circuitOpenTime = null;
        lossAtCircuitOpen = null;
        logger.info("Daily loss circuit breaker CLOSED. Trading resumed.");
    }

    /**
     * Get current daily P&L.
     */
    public BigDecimal getCurrentDailyPnL() {
        LocalDate today = LocalDate.now();
        return dailyPnLTracker.getOrDefault(today, BigDecimal.ZERO);
    }

    /**
     * Get the loss percentage for current day.
     */
    public BigDecimal getLossPercent() {
        return calculateLossPercent();
    }

    /**
     * Check if circuit is open.
     */
    public boolean isCircuitOpen() {
        return isCircuitOpen;
    }

    /**
     * Get the time when circuit was opened.
     */
    public LocalDateTime getCircuitOpenTime() {
        return circuitOpenTime;
    }

    /**
     * Get the loss amount when circuit was opened.
     */
    public BigDecimal getLossAtCircuitOpen() {
        return lossAtCircuitOpen;
    }

    // Private Helper Methods

    private void resetDailyTracker() {
        dailyPnLTracker.clear();
        dailyPnLTracker.put(LocalDate.now(), BigDecimal.ZERO);
        logger.info("Daily P&L tracker reset for {}", LocalDate.now());
    }

    private BigDecimal calculateTotalUnrealizedPnL() {
        BigDecimal total = BigDecimal.ZERO;

        for (Position position : positionManager.getOpenPositions()) {
            total = total.add(position.getProfitLoss());
        }

        return total;
    }

    private BigDecimal calculateTotalRealizedPnL() {
        // For now, return zero. Can be enhanced to track realized P&L from closed positions.
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateLossPercent() {
        BigDecimal dailyPnL = getCurrentDailyPnL();
        return calculateLossPercentFromPnL(dailyPnL);
    }

    private BigDecimal calculateLossPercentFromPnL(BigDecimal pnL) {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return pnL.multiply(BigDecimal.valueOf(100)).divide(initialCapital, 4, BigDecimal.ROUND_HALF_UP);
    }

    private void openCircuit(BigDecimal lossAmount) {
        isCircuitOpen = true;
        circuitOpenTime = LocalDateTime.now();
        lossAtCircuitOpen = lossAmount;

        logger.warn("DAILY LOSS CIRCUIT BREAKER OPENED! Loss: ₹{} ({}%)",
                lossAmount, calculateLossPercent());
    }
}
