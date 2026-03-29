package com.swingtrade.broker.factory;

import com.swingtrade.broker.kite.BrokerClient;
import com.swingtrade.broker.kite.KiteConnectClient;
import com.swingtrade.broker.model.*;
import com.swingtrade.broker.risk.RiskCheckResult;
import com.swingtrade.broker.risk.RiskControls;
import com.swingtrade.broker.service.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Dry-run service implementation.
 * Logs all orders without sending to broker.
 * Useful for testing and validation before live trading.
 */
@Component
public class DryRunService implements BrokerService {

    private static final Logger logger = LoggerFactory.getLogger(DryRunService.class);

    private final BrokerClient brokerClient;
    private final RiskControls riskControlsService;

    private final int maxConcurrentPositions = 5;
    private final BigDecimal maxCapitalPerPosition = new BigDecimal("200000");

    public DryRunService(BrokerClient brokerClient,
                         RiskControls riskControlsService) {
        this.brokerClient = brokerClient;
        this.riskControlsService = riskControlsService;

        logger.info("DryRunService initialized - orders will be logged but not executed");
    }

    @Override
    public Order placeOrder(Order order) {
        logger.info("========== DRY RUN ORDER ==========");
        logger.info("Symbol: {}", order.getSymbol());
        logger.info("Type: {}", order.getType());
        logger.info("Direction: {}", order.getDirection());
        logger.info("Quantity: {}", order.getQuantity());
        logger.info("Price: {}", order.getPrice());
        logger.info("Limit Price: {}", order.getLimitPrice());
        logger.info("Stop Price: {}", order.getStopPrice());
        logger.info("===================================");

        // Create order response for logging
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setInternalOrderId(order.getOrderId());
        orderResponse.setSymbol(order.getSymbol());
        orderResponse.setType(order.getType());
        orderResponse.setDirection(order.getDirection());
        orderResponse.setQuantity(order.getQuantity());
        orderResponse.setPrice(order.getPrice());
        orderResponse.setLimitPrice(order.getLimitPrice());
        orderResponse.setStopPrice(order.getStopPrice());
        orderResponse.setExchange(Exchange.NSE);

        // Get market price for risk checks
        BigDecimal marketPrice = brokerClient.getMarketPrice(order.getSymbol(), Exchange.NSE);
        if (marketPrice != null) {
            orderResponse.setPrice(marketPrice);
        }

        // Perform pre-trade risk checks
        RiskCheckResult riskResult = riskControlsService.preTradeCheck(orderResponse, marketPrice);
        if (!riskResult.isPassed()) {
            logger.warn("DRY RUN: Pre-trade risk checks failed: {}", riskResult.getMessages());
            order.setStatus(OrderStatus.CANCELLED);
            return order;
        }

        logger.info("DRY RUN: All risk checks passed");
        logger.info("DRY RUN: Order would be placed with broker");
        logger.info("DRY RUN: No actual order sent (dry-run mode)");

        // Simulate order acceptance
        order.setStatus(OrderStatus.ACCEPTED);

        return order;
    }

    @Override
    public boolean cancelOrder(String orderId) {
        logger.info("DRY RUN: Would cancel order: {}", orderId);
        logger.info("DRY RUN: No actual cancellation performed");
        return true; // Simulate success
    }

    @Override
    public Portfolio getPortfolio() {
        logger.debug("DRY RUN: Portfolio requested");
        return null; // Not applicable in dry-run mode
    }

    @Override
    public List<Position> getOpenPositions() {
        logger.debug("DRY RUN: Open positions requested");
        return List.of(); // No real positions in dry-run mode
    }

    @Override
    public Optional<Position> getPosition(String positionId) {
        logger.debug("DRY RUN: Position {} requested", positionId);
        return Optional.empty(); // No real positions in dry-run mode
    }

    @Override
    public BigDecimal calculateProfitLoss(Position position) {
        logger.debug("DRY RUN: Calculating P&L for position: {}", position.getSymbol());
        return BigDecimal.ZERO; // No real P&L in dry-run mode
    }

    @Override
    public int getMaxConcurrentPositions() {
        return maxConcurrentPositions;
    }

    @Override
    public BigDecimal getMaxCapitalPerPosition() {
        return maxCapitalPerPosition;
    }

    /**
     * Get the underlying broker client (for market data).
     */
    public BrokerClient getBrokerClient() {
        return brokerClient;
    }

    /**
     * Get the risk controls service.
     */
    public RiskControls getRiskControls() {
        return riskControlsService;
    }
}
