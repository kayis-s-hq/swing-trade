package com.swingtrade.broker.telegram;

import com.swingtrade.broker.model.Position;
import com.swingtrade.broker.service.BrokerService;
import com.swingtrade.domain.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Integration service that connects the broker module with Signal notifications.
 * Provides methods to send trade event notifications via Signal messaging app.
 *
 * Per project decision, Signal notifications are limited to trade events only:
 * - Trade open (position entry)
 * - Trade close (position exit with P&L)
 * - Stop loss hit
 * - Target hit
 *
 * Signal notifications do NOT include (deferred):
 * - Signal alerts (BUY/SELL/HOLD)
 * - Position updates
 * - System status
 * - Order notifications
 * - Error notifications
 * - Risk warnings
 * - Daily summaries
 *
 * @author SwingTrade Team
 */
@Component
public class BrokerNotificationIntegration {

    private static final Logger log = LoggerFactory.getLogger(BrokerNotificationIntegration.class);

    private final SignalNotificationService signalService;
    private final BrokerService brokerService;
    private final SignalMessageFormatter messageFormatter;

    public BrokerNotificationIntegration(SignalNotificationService signalService,
                                          BrokerService brokerService,
                                          SignalMessageFormatter messageFormatter) {
        this.signalService = signalService;
        this.brokerService = brokerService;
        this.messageFormatter = messageFormatter;
    }

    /**
     * Sends a notification when a new position is entered (trade open).
     *
     * @param position the position that was entered
     * @return true if message was sent, false otherwise
     */
    public boolean onPositionEntry(Position position) {
        log.debug("Sending position entry notification for {}", position.getSymbol());
        // Note: This method is kept for API compatibility but is not used
        // Trade open notifications are sent via sendTradeOpen in SignalNotificationService
        return false;
    }

    /**
     * Sends a notification when a position is updated with current P&L.
     * (Deferred per project decision - not implemented)
     *
     * @param position the position to update
     * @return true if message was sent, false otherwise
     */
    public boolean onPositionUpdate(Position position) {
        log.debug("Position update notifications are deferred");
        return false;
    }

    /**
     * Sends a notification when a position is closed (trade close).
     *
     * @param position the position that was exited
     * @param trade the completed trade
     * @return true if message was sent, false otherwise
     */
    public boolean onPositionExit(Position position, Trade trade) {
        log.debug("Sending position exit notification for {}", trade.symbol());
        // Note: This method is kept for API compatibility but is not used
        // Trade close notifications are sent via sendTradeClose in SignalNotificationService
        return false;
    }

    /**
     * Sends a notification when a stop loss is hit.
     *
     * @param position the position that hit stop loss
     * @param exitPrice the price at which the position was stopped
     * @param pnl the profit/loss from the stop loss
     * @return true if message was sent, false otherwise
     */
    public boolean onStopLossHit(Position position, BigDecimal exitPrice, BigDecimal pnl) {
        log.warn("Stop loss hit for position {}: exitPrice={}, pnl={}", position.getSymbol(), exitPrice, pnl);
        return signalService.sendStopLossHit(position, exitPrice, pnl);
    }

    /**
     * Sends a notification when a target is hit.
     *
     * @param position the position that hit target
     * @param exitPrice the price at which the position was exited
     * @param pnl the profit/loss from the target hit
     * @return true if message was sent, false otherwise
     */
    public boolean onTargetHit(Position position, BigDecimal exitPrice, BigDecimal pnl) {
        log.info("Target hit for position {}: exitPrice={}, pnl={}", position.getSymbol(), exitPrice, pnl);
        return signalService.sendTargetHit(position, exitPrice, pnl);
    }

    // =================================================================
    // Deferred Methods (not implemented per project decision)
    // =================================================================

    /**
     * Signal notifications (onBuySignal, onSellSignal, onHoldSignal) are deferred.
     */
    @Deprecated
    public boolean onBuySignal(com.swingtrade.domain.Signal signal) {
        log.debug("Signal notifications are deferred");
        return false;
    }

    @Deprecated
    public boolean onSellSignal(com.swingtrade.domain.Signal signal) {
        log.debug("Signal notifications are deferred");
        return false;
    }

    @Deprecated
    public boolean onHoldSignal(com.swingtrade.domain.Signal signal) {
        log.debug("Signal notifications are deferred");
        return false;
    }

    /**
     * Order notifications are deferred.
     */
    @Deprecated
    public boolean onOrderExecution(com.swingtrade.broker.model.Order order) {
        log.debug("Order execution notifications are deferred");
        return false;
    }

    @Deprecated
    public boolean onOrderFill(com.swingtrade.broker.model.Order order) {
        log.debug("Order fill notifications are deferred");
        return false;
    }

    @Deprecated
    public boolean onOrderCancellation(com.swingtrade.broker.model.Order order) {
        log.debug("Order cancellation notifications are deferred");
        return false;
    }

    /**
     * Error notifications are deferred.
     */
    @Deprecated
    public boolean onError(String errorType, String message, String severity) {
        log.error("Broker error [{}]: {}", errorType, message);
        return false;
    }

    /**
     * Position size alerts are deferred.
     */
    @Deprecated
    public boolean onPositionSizeAlert(String symbol, BigDecimal currentSize, BigDecimal maxSize, BigDecimal percentage) {
        log.warn("Position size alerts are deferred");
        return false;
    }

    /**
     * Risk warnings are deferred.
     */
    @Deprecated
    public boolean onRiskWarning(String symbol, BigDecimal riskPercent) {
        log.warn("Risk warnings are deferred");
        return false;
    }

    /**
     * Daily summaries are deferred.
     */
    @Deprecated
    public boolean onDailySummary(Object summary) {
        log.info("Daily summaries are deferred");
        return false;
    }

    /**
     * System status notifications are deferred.
     */
    @Deprecated
    public boolean onSystemStatus(String status) {
        log.debug("System status notifications are deferred");
        return false;
    }

    /**
     * Market status notifications are deferred.
     */
    @Deprecated
    public boolean onMarketStatus(boolean isOpening) {
        log.info("Market status notifications are deferred");
        return false;
    }

    // =================================================================
    // Configuration Methods
    // =================================================================

    /**
     * Check if Signal notifications are enabled.
     *
     * @return true if notifications are enabled
     */
    public boolean isNotificationsEnabled() {
        return signalService.isEnabled();
    }

    /**
     * Get the number of configured chat IDs.
     *
     * @return the number of chat IDs
     */
    public int getChatCount() {
        return signalService.getChatCount();
    }

    /**
     * Check if notifications should be sent for trades.
     *
     * @return true if trade notifications are enabled
     */
    public boolean shouldNotifyOnTrades() {
        return signalService.shouldNotifyOnTrades();
    }

    /**
     * Check if notifications should be sent for signals.
     *
     * @return true if signal notifications are enabled
     */
    public boolean shouldNotifyOnSignals() {
        return signalService.shouldNotifyOnSignals();
    }

    /**
     * Check if notifications should be sent for positions.
     *
     * @return true if position notifications are enabled
     */
    public boolean shouldNotifyOnPositions() {
        return signalService.shouldNotifyOnPositions();
    }

    /**
     * Check if notifications should be sent for errors.
     *
     * @return true if error notifications are enabled
     */
    public boolean shouldNotifyOnErrors() {
        return signalService.shouldNotifyOnErrors();
    }

    /**
     * Get broker service instance (for integration purposes).
     *
     * @return the broker service
     */
    public BrokerService getBrokerService() {
        return brokerService;
    }
}
