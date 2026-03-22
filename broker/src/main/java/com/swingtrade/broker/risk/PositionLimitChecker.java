package com.swingtrade.broker.risk;

import com.swingtrade.broker.manager.PositionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Checks if position count is within configured limits.
 * Prevents over-diversification and excessive risk exposure.
 */
@Component
public class PositionLimitChecker {

    private static final Logger logger = LoggerFactory.getLogger(PositionLimitChecker.class);

    private final PositionManager positionManager;

    @Value("${broker.max-concurrent-positions:5}")
    private int maxConcurrentPositions;

    @Value("${broker.max-capital-per-position:200000}")
    private BigDecimal maxCapitalPerPosition;

    @Autowired
    public PositionLimitChecker(PositionManager positionManager) {
        this.positionManager = positionManager;
        logger.info("PositionLimitChecker initialized with max {} positions, max ₹{} per position",
                maxConcurrentPositions, maxCapitalPerPosition);
    }

    /**
     * Check if adding a new position would exceed limits.
     *
     * @param estimatedPositionValue estimated value of the new position
     * @return RiskCheckResult indicating if position is allowed
     */
    public RiskCheckResult canAddPosition(BigDecimal estimatedPositionValue) {
        logger.debug("Checking position limits for estimated value: {}", estimatedPositionValue);

        RiskCheckResult result = new RiskCheckResult();
        result.setCheckType("POSITION_LIMIT");

        // Get current open positions
        int currentPositionCount = positionManager.getOpenPositionCount();
        logger.info("Current open positions: {}, Max allowed: {}", currentPositionCount, maxConcurrentPositions);

        // Check concurrent position limit
        if (currentPositionCount >= maxConcurrentPositions) {
            result.addError("Maximum concurrent positions limit reached: {} / {}",
                    currentPositionCount, maxConcurrentPositions);
            return result;
        }

        // Check capital per position limit
        if (estimatedPositionValue != null && estimatedPositionValue.compareTo(maxCapitalPerPosition) > 0) {
            result.addError("Position value exceeds maximum allowed: ₹{} > ₹{}",
                    estimatedPositionValue, maxCapitalPerPosition);
            return result;
        }

        result.addInfo("Position limit check passed. Current positions: {}/{}, " +
                        "Estimated position value: ₹{} (max: ₹{})",
                currentPositionCount, maxConcurrentPositions,
                estimatedPositionValue, maxCapitalPerPosition);

        return result;
    }

    /**
     * Get the maximum number of concurrent positions allowed.
     */
    public int getMaxConcurrentPositions() {
        return maxConcurrentPositions;
    }

    /**
     * Get the maximum capital allowed per position.
     */
    public BigDecimal getMaxCapitalPerPosition() {
        return maxCapitalPerPosition;
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
        return Math.max(0, maxConcurrentPositions - getCurrentPositionCount());
    }
}
