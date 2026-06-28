package com.swingtrade.broker.risk;

import com.swingtrade.broker.model.OrderResponse;

import java.math.BigDecimal;

/**
 * Interface for risk controls operations.
 * Used to decouple service layer from concrete RiskControlsService implementation.
 */
public interface RiskControls {

    /**
     * Perform pre-trade risk checks.
     * @param orderResponse the order to validate
     * @param marketPrice current market price
     * @return RiskCheckResult with all check results
     */
    RiskCheckResult preTradeCheck(OrderResponse orderResponse, BigDecimal marketPrice);

    /**
     * Quick risk check without market price.
     * @param orderResponse the order to validate
     * @return RiskCheckResult with all check results
     */
    RiskCheckResult quickPreTradeCheck(OrderResponse orderResponse);

    /**
     * Update risk controls with current portfolio state.
     */
    void updateRiskState();

    /**
     * Get kill switch status.
     * @return true if active, false otherwise
     */
    boolean isKillSwitchActive();

    /**
     * Set kill switch status.
     * @param active true to activate, false to deactivate
     */
    void setKillSwitchActive(boolean active);

    /**
     * Get current position count.
     * @return number of open positions
     */
    int getCurrentPositionCount();

    /**
     * Get remaining position capacity.
     * @return remaining position slots
     */
    int getRemainingPositionCapacity();

    /**
     * Get current daily P&L.
     * @return current daily P&L
     */
    BigDecimal getCurrentDailyPnL();

    /**
     * Get current daily loss percentage.
     * @return loss percentage
     */
    BigDecimal getDailyLossPercent();
}
