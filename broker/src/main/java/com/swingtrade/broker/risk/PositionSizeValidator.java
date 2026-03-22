package com.swingtrade.broker.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validates position sizing before trade execution.
 * Ensures trades are appropriately sized relative to account and risk parameters.
 */
@Component
public class PositionSizeValidator {

    private static final Logger logger = LoggerFactory.getLogger(PositionSizeValidator.class);

    @Value("${broker.max-capital-per-trade:50000}")
    private BigDecimal maxCapitalPerTrade;

    @Value("${broker.initial-capital:1000000}")
    private BigDecimal initialCapital;

    @Value("${broker.max-position-size-percentage:10}")
    private BigDecimal maxPositionSizePercentage;

    @Value("${broker.min-position-size-percentage:1}")
    private BigDecimal minPositionSizePercentage;

    @Autowired
    public PositionSizeValidator() {
        logger.info("PositionSizeValidator initialized with max ₹{} per trade, {}% max position size",
                maxCapitalPerTrade, maxPositionSizePercentage);
    }

    /**
     * Validate if a trade size is within acceptable limits.
     *
     * @param tradeValue the total value of the trade (quantity * price)
     * @return RiskCheckResult indicating if position size is valid
     */
    public RiskCheckResult validatePositionSize(BigDecimal tradeValue) {
        logger.debug("Validating position size: {}", tradeValue);

        RiskCheckResult result = new RiskCheckResult();
        result.setCheckType("POSITION_SIZE_VALIDATION");

        if (tradeValue == null || tradeValue.compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("Invalid trade value: {}. Must be positive.", tradeValue);
            return result;
        }

        // Check maximum capital per trade
        if (tradeValue.compareTo(maxCapitalPerTrade) > 0) {
            result.addError("Trade value exceeds maximum per trade: ₹{} > ₹{}",
                    tradeValue, maxCapitalPerTrade);
            return result;
        }

        // Check maximum percentage of capital
        BigDecimal maxAllowed = initialCapital.multiply(maxPositionSizePercentage).divide(BigDecimal.valueOf(100));
        if (tradeValue.compareTo(maxAllowed) > 0) {
            result.addError("Trade size exceeds maximum position size: {}% > {}%",
                    calculatePositionPercent(tradeValue), maxPositionSizePercentage);
            return result;
        }

        // Check minimum position size (optional, prevents trivial trades)
        BigDecimal minAllowed = initialCapital.multiply(minPositionSizePercentage).divide(BigDecimal.valueOf(100));
        if (tradeValue.compareTo(minAllowed) < 0) {
            result.addWarning("Trade size is below minimum recommended: {}% < {}%",
                    calculatePositionPercent(tradeValue), minPositionSizePercentage);
        }

        result.addInfo("Position size validated: ₹{} ({}% of capital)",
                tradeValue, calculatePositionPercent(tradeValue));

        return result;
    }

    /**
     * Calculate the position size as a percentage of total capital.
     *
     * @param tradeValue the trade value
     * @return percentage of capital
     */
    public BigDecimal calculatePositionPercent(BigDecimal tradeValue) {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return tradeValue.multiply(BigDecimal.valueOf(100)).divide(initialCapital, 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate maximum allowed position size in currency.
     *
     * @return maximum position size
     */
    public BigDecimal getMaxPositionSize() {
        return initialCapital.multiply(maxPositionSizePercentage).divide(BigDecimal.valueOf(100));
    }

    /**
     * Calculate recommended position size based on risk parameters.
     *
     * @param riskPerTrade percentage of capital to risk on this trade
     * @return recommended position size
     */
    public BigDecimal calculateRecommendedPositionSize(BigDecimal riskPerTrade) {
        // Position size = Capital * Risk % / Stop Loss %
        // For now, return a simple calculation based on risk percentage
        return initialCapital.multiply(riskPerTrade).divide(BigDecimal.valueOf(100));
    }

    /**
     * Get the maximum capital allowed per trade.
     */
    public BigDecimal getMaxCapitalPerTrade() {
        return maxCapitalPerTrade;
    }

    /**
     * Get the maximum position size percentage.
     */
    public BigDecimal getMaxPositionSizePercentage() {
        return maxPositionSizePercentage;
    }
}
