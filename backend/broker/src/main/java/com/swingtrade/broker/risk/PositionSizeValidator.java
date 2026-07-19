package com.swingtrade.broker.risk;

import com.swingtrade.broker.config.BrokerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validates position sizing before trade execution.
 * Ensures trades are appropriately sized relative to account and risk parameters.
 */
@Component
public class PositionSizeValidator {

    private static final Logger logger = LoggerFactory.getLogger(PositionSizeValidator.class);

    private final BrokerProperties props;

    @Autowired
    public PositionSizeValidator(BrokerProperties props) {
        this.props = props;
        logger.info("PositionSizeValidator initialized with max ₹{} per trade, {}% max position size",
                props.getMaxCapitalPerTrade(), props.getMaxPositionSizePercentage());
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
        if (tradeValue.compareTo(props.getMaxCapitalPerTrade()) > 0) {
            result.addError("Trade value exceeds maximum per trade: ₹{} > ₹{}",
                    tradeValue, props.getMaxCapitalPerTrade());
            return result;
        }

        // Check maximum percentage of capital
        BigDecimal maxAllowed = props.getInitialCapital().multiply(props.getMaxPositionSizePercentage()).divide(BigDecimal.valueOf(100));
        if (tradeValue.compareTo(maxAllowed) > 0) {
            result.addError("Trade size exceeds maximum position size: {}% > {}%",
                    calculatePositionPercent(tradeValue), props.getMaxPositionSizePercentage());
            return result;
        }

        // Check minimum position size (optional, prevents trivial trades)
        BigDecimal minAllowed = props.getInitialCapital().multiply(props.getMinPositionSizePercentage()).divide(BigDecimal.valueOf(100));
        if (tradeValue.compareTo(minAllowed) < 0) {
            result.addWarning("Trade size is below minimum recommended: {}% < {}%",
                    calculatePositionPercent(tradeValue), props.getMinPositionSizePercentage());
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
        if (props.getInitialCapital().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return tradeValue.multiply(BigDecimal.valueOf(100)).divide(props.getInitialCapital(), 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate maximum allowed position size in currency.
     *
     * @return maximum position size
     */
    public BigDecimal getMaxPositionSize() {
        return props.getInitialCapital().multiply(props.getMaxPositionSizePercentage()).divide(BigDecimal.valueOf(100));
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
        return props.getInitialCapital().multiply(riskPerTrade).divide(BigDecimal.valueOf(100));
    }
}
