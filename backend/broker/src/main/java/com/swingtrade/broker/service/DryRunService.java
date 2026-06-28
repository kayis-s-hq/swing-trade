package com.swingtrade.broker.service;

import com.swingtrade.broker.kite.KiteConnectClient;
import com.swingtrade.broker.model.OrderResponse;
import com.swingtrade.broker.risk.RiskCheckResult;
import com.swingtrade.broker.risk.RiskControls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Dry-run trading service implementation.
 * Logs all order operations without executing any trades.
 * Safe mode for testing and simulation.
 */
@Component
@ConditionalOnProperty(name = "kite.enabled", havingValue = "true", matchIfMissing = false)
public class DryRunService implements BrokerService {

    private static final Logger logger = LoggerFactory.getLogger(DryRunService.class);

    private final KiteConnectClient kiteConnectClient;
    private final RiskControls riskControls;

    private static final int MAX_CONCURRENT_POSITIONS = 3;
    private static final BigDecimal MAX_CAPITAL_PER_POSITION = new BigDecimal("10000");

    @Autowired
    public DryRunService(KiteConnectClient kiteConnectClient, RiskControls riskControls) {
        this.kiteConnectClient = kiteConnectClient;
        this.riskControls = riskControls;
        logger.info("DryRunService initialized (no actual orders will be placed)");
    }

    /**
     * Constructor for testing purposes.
     */
    public DryRunService(KiteConnectClient kiteConnectClient, RiskControls riskControls,
                         int maxPositions, BigDecimal maxCapital) {
        this.kiteConnectClient = kiteConnectClient;
        this.riskControls = riskControls;
    }

    /**
     * Place a new order - dry run only (logs but does not execute).
     *
     * @param order the order to place
     * @return the placed order with simulated status
     */
    @Override
    public com.swingtrade.broker.model.Order placeOrder(com.swingtrade.broker.model.Order order) {
        logger.info("[DRY RUN] Would place order for symbol: {} direction: {} quantity: {}",
                order.getSymbol(), order.getDirection(), order.getQuantity());

        // Log order details without executing
        order.setStatus(com.swingtrade.broker.model.OrderStatus.PENDING);

        // Validate risk controls (logging only)
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setSymbol(order.getSymbol());
        orderResponse.setDirection(order.getDirection());
        orderResponse.setQuantity(order.getQuantity());
        orderResponse.setType(order.getType());

        BigDecimal marketPrice = kiteConnectClient.getMarketPrice(order.getSymbol(), com.swingtrade.broker.model.Exchange.NSE);
        RiskCheckResult riskResult = riskControls.preTradeCheck(orderResponse, marketPrice);
        if (!riskResult.isPassed()) {
            logger.warn("[DRY RUN] Order would be rejected by risk controls: {}", riskResult.getMessages());
            order.setStatus(com.swingtrade.broker.model.OrderStatus.REJECTED);
        } else {
            logger.info("[DRY RUN] Risk checks would pass for this order");
        }

        return order;
    }

    /**
     * Cancel an existing order - dry run only (logs but does not execute).
     *
     * @param orderId the order to cancel
     * @return false (no actual cancellation)
     */
    @Override
    public boolean cancelOrder(String orderId) {
        logger.info("[DRY RUN] Would cancel order: {}", orderId);
        logger.info("[DRY RUN] No actual cancellation performed");
        return false;
    }

    /**
     * Get the portfolio - dry run (returns empty portfolio).
     *
     * @return empty portfolio
     */
    @Override
    public com.swingtrade.broker.model.Portfolio getPortfolio() {
        logger.debug("[DRY RUN] Returning empty portfolio");
        com.swingtrade.broker.model.Portfolio portfolio = new com.swingtrade.broker.model.Portfolio("dry_run_portfolio", BigDecimal.ZERO);
        portfolio.setAvailableCash(BigDecimal.ZERO);
        return portfolio;
    }

    /**
     * Get all open positions - dry run (returns empty list).
     *
     * @return empty list
     */
    @Override
    public List<com.swingtrade.broker.model.Position> getOpenPositions() {
        logger.debug("[DRY RUN] Returning empty positions list");
        return Collections.emptyList();
    }

    /**
     * Get a specific position - dry run (returns empty).
     *
     * @param symbol the symbol to retrieve
     * @return empty optional
     */
    @Override
    public Optional<com.swingtrade.broker.model.Position> getPosition(String symbol) {
        logger.debug("[DRY RUN] Position not found (dry run): {}", symbol);
        return Optional.empty();
    }

    /**
     * Calculate profit and loss - dry run (returns zero).
     *
     * @param position the position to calculate P&L for
     * @return BigDecimal.ZERO
     */
    @Override
    public BigDecimal calculateProfitLoss(com.swingtrade.broker.model.Position position) {
        logger.debug("[DRY RUN] P&L calculation for position: {} (returns 0)", position.getSymbol());
        return BigDecimal.ZERO;
    }

    /**
     * Get the maximum number of concurrent positions allowed.
     *
     * @return maximum number of concurrent positions (3)
     */
    @Override
    public int getMaxConcurrentPositions() {
        return MAX_CONCURRENT_POSITIONS;
    }

    /**
     * Get the maximum capital allowed per position.
     *
     * @return maximum capital per position (10000)
     */
    @Override
    public BigDecimal getMaxCapitalPerPosition() {
        return MAX_CAPITAL_PER_POSITION;
    }

    /**
     * Get the KiteConnectClient for direct access if needed.
     *
     * @return KiteConnectClient
     */
    public KiteConnectClient getKiteConnectClient() {
        return kiteConnectClient;
    }

    /**
     * Get the RiskControls for direct access if needed.
     *
     * @return RiskControls
     */
    public RiskControls getRiskControls() {
        return riskControls;
    }

    /**
     * Check if this is a safe mode (dry run is always safe).
     *
     * @return true
     */
    public boolean isSafeMode() {
        return true;
    }
}
