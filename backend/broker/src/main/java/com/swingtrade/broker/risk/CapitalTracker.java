package com.swingtrade.broker.risk;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.model.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Capital tracker for live trading.
 * Enforces capital limits including maximum concurrent positions
 * and maximum capital per position.
 *
 * Configuration for live trading:
 * - Initial Capital: ₹50,000
 * - Max Concurrent Positions: 3
 * - Max Capital Per Position: ₹10,000 (20% of capital)
 */
@Component
public class CapitalTracker {

    private static final Logger logger = LoggerFactory.getLogger(CapitalTracker.class);

    private static final BigDecimal DEFAULT_INITIAL_CAPITAL = new BigDecimal("50000");

    private final BigDecimal initialCapital;
    private final int maxPositions;
    private final BigDecimal maxCapitalPerPosition;
    private final BigDecimal maxTotalExposure;

    private BigDecimal deployedCapital;

    @Autowired
    public CapitalTracker(BrokerProperties props) {
        this(DEFAULT_INITIAL_CAPITAL, props.getMaxConcurrentPositions(), props.getMaxCapitalPerPosition());
    }

    /**
     * Constructor for testing purposes.
     */
    public CapitalTracker(BigDecimal initialCapital, int maxPositions, BigDecimal maxCapitalPerPosition) {
        this.initialCapital = initialCapital;
        this.maxPositions = maxPositions;
        this.maxCapitalPerPosition = maxCapitalPerPosition;
        this.maxTotalExposure = initialCapital.multiply(new BigDecimal("0.8"));

        this.deployedCapital = BigDecimal.ZERO;
        logger.info("CapitalTracker initialized for testing: capital={}, maxPositions={}, maxPerPosition={}",
                initialCapital, maxPositions, maxCapitalPerPosition);
    }

    /**
     * Enforce capital limits before placing a new order.
     *
     * @param currentPositions list of current open positions
     * @param orderValue value of the new order
     * @return RiskCheckResult with validation status
     */
    public RiskCheckResult enforceLimits(List<Position> currentPositions, BigDecimal orderValue) {
        logger.debug("Enforcing capital limits: currentPositions={}, orderValue={}",
                currentPositions.size(), orderValue);

        RiskCheckResult result = new RiskCheckResult();
        result.setCheckType("CAPITAL_LIMITS");

        // 1. Check max positions
        if (currentPositions.size() >= maxPositions) {
            result.addError("Maximum concurrent positions limit reached: {} / {}",
                    currentPositions.size(), maxPositions);
            result.addInfo("Cannot open more than {} positions. Close {} positions before placing new order.",
                    maxPositions, currentPositions.size() - maxPositions + 1);
            return result;
        }

        // 2. Calculate total deployed capital including new order
        BigDecimal totalWithNewOrder = calculateTotalDeployedCapital(currentPositions).add(orderValue);

        // 3. Check max capital per position
        if (orderValue.compareTo(maxCapitalPerPosition) > 0) {
            result.addError("Order value exceeds maximum capital per position: {} / {}",
                    orderValue, maxCapitalPerPosition);
            result.addInfo("Maximum capital per position is {} (20% of initial capital {})",
                    maxCapitalPerPosition, initialCapital);
            return result;
        }

        // 4. Check max total exposure
        if (totalWithNewOrder.compareTo(maxTotalExposure) > 0) {
            result.addError("Total exposure would exceed maximum: {} / {}",
                    totalWithNewOrder, maxTotalExposure);
            result.addInfo("Maximum total exposure is {} (80% of initial capital {})",
                    maxTotalExposure, initialCapital);
            result.addInfo("Current deployed: {}, order value: {}",
                    calculateTotalDeployedCapital(currentPositions), orderValue);
            return result;
        }

        // All checks passed
        result.addInfo("Capital limits validated: position {} / {}, total exposure {} / {}",
                currentPositions.size() + 1, maxPositions,
                totalWithNewOrder, maxTotalExposure);

        return result;
    }

    /**
     * Check if a new position can be added.
     *
     * @param currentPositions list of current open positions
     * @param orderValue value of the new order
     * @return true if position can be added
     */
    public boolean canAddPosition(List<Position> currentPositions, BigDecimal orderValue) {
        return enforceLimits(currentPositions, orderValue).isPassed();
    }

    /**
     * Get the current number of positions.
     *
     * @param positions list of current positions
     * @return current position count
     */
    public int getCurrentPositionCount(List<Position> positions) {
        return positions != null ? positions.size() : 0;
    }

    /**
     * Get the remaining position capacity.
     *
     * @param positions list of current positions
     * @return remaining capacity (max - current)
     */
    public int getRemainingPositionCapacity(List<Position> positions) {
        int current = getCurrentPositionCount(positions);
        return Math.max(0, maxPositions - current);
    }

    /**
     * Get the current deployed capital.
     *
     * @param positions list of current positions
     * @return total deployed capital
     */
    public BigDecimal getDeployedCapital(List<Position> positions) {
        return calculateTotalDeployedCapital(positions);
    }

    /**
     * Get the available capital.
     *
     * @param positions list of current positions
     * @return available capital for new positions
     */
    public BigDecimal getAvailableCapital(List<Position> positions) {
        BigDecimal deployed = calculateTotalDeployedCapital(positions);
        return initialCapital.subtract(deployed);
    }

    /**
     * Get the remaining capital per position.
     *
     * @param positions list of current positions
     * @return maximum capital that can be deployed per new position
     */
    public BigDecimal getRemainingCapitalPerPosition(List<Position> positions) {
        return maxCapitalPerPosition;
    }

    /**
     * Get the initial capital.
     *
     * @return initial capital amount
     */
    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    /**
     * Get the maximum concurrent positions allowed.
     *
     * @return maximum positions
     */
    public int getMaxPositions() {
        return maxPositions;
    }

    /**
     * Get the maximum capital per position.
     *
     * @return maximum capital per position
     */
    public BigDecimal getMaxCapitalPerPosition() {
        return maxCapitalPerPosition;
    }

    /**
     * Calculate total deployed capital from positions.
     */
    private BigDecimal calculateTotalDeployedCapital(List<Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return positions.stream()
                .map(this::calculatePositionValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate value of a single position.
     */
    private BigDecimal calculatePositionValue(Position position) {
        if (position == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal quantity = position.getQuantity() != null ? position.getQuantity() : BigDecimal.ZERO;
        BigDecimal entryPrice = position.getEntryPrice() != null ? position.getEntryPrice() : BigDecimal.ZERO;

        return quantity.multiply(entryPrice);
    }

    /**
     * Update deployed capital based on current positions.
     * Call this method when positions change (open/close).
     */
    public void updateDeployedCapital(List<Position> positions) {
        this.deployedCapital = calculateTotalDeployedCapital(positions);
        logger.debug("Updated deployed capital: {} / {}", deployedCapital, initialCapital);
    }

    /**
     * Get position utilization percentage.
     *
     * @param positions list of current positions
     * @return percentage of capital utilized
     */
    public BigDecimal getUtilizationPercentage(List<Position> positions) {
        BigDecimal deployed = calculateTotalDeployedCapital(positions);
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return deployed.divide(initialCapital, 4, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"));
    }

    /**
     * Get current capital summary.
     *
     * @param positions list of current positions
     * @return CapitalSummary with all capital metrics
     */
    public CapitalSummary getCapitalSummary(List<Position> positions) {
        BigDecimal deployed = calculateTotalDeployedCapital(positions);
        BigDecimal available = initialCapital.subtract(deployed);
        BigDecimal utilization = deployed.divide(initialCapital, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("100"));

        return new CapitalSummary(
                initialCapital,
                deployed,
                available,
                maxPositions,
                getCurrentPositionCount(positions),
                utilization
        );
    }

    /**
     * Simple data class for capital summary.
     */
    public static class CapitalSummary {
        private final BigDecimal initialCapital;
        private final BigDecimal deployedCapital;
        private final BigDecimal availableCapital;
        private final int maxPositions;
        private final int currentPositions;
        private final BigDecimal utilizationPercentage;

        public CapitalSummary(BigDecimal initialCapital, BigDecimal deployedCapital, BigDecimal availableCapital,
                              int maxPositions, int currentPositions, BigDecimal utilizationPercentage) {
            this.initialCapital = initialCapital;
            this.deployedCapital = deployedCapital;
            this.availableCapital = availableCapital;
            this.maxPositions = maxPositions;
            this.currentPositions = currentPositions;
            this.utilizationPercentage = utilizationPercentage;
        }

        public BigDecimal getInitialCapital() {
            return initialCapital;
        }

        public BigDecimal getDeployedCapital() {
            return deployedCapital;
        }

        public BigDecimal getAvailableCapital() {
            return availableCapital;
        }

        public int getMaxPositions() {
            return maxPositions;
        }

        public int getCurrentPositions() {
            return currentPositions;
        }

        public BigDecimal getUtilizationPercentage() {
            return utilizationPercentage;
        }

        @Override
        public String toString() {
            return "CapitalSummary{" +
                    "initialCapital=₹" + initialCapital +
                    ", deployed=₹" + deployedCapital +
                    ", available=₹" + availableCapital +
                    ", positions=" + currentPositions + "/" + maxPositions +
                    ", utilization=" + utilizationPercentage + "%" +
                    '}';
        }
    }
}
