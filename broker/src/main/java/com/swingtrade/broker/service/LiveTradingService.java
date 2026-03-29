package com.swingtrade.broker.service;

import com.swingtrade.broker.kite.KiteConnectClient;
import com.swingtrade.broker.model.*;
import com.swingtrade.broker.risk.RiskCheckResult;
import com.swingtrade.broker.risk.RiskControls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Live trading service implementation using Zerodha Kite Connect.
 * Delegates all broker operations to KiteConnectClient with risk control validation.
 */
@Component
public class LiveTradingService implements BrokerService {

    private static final Logger logger = LoggerFactory.getLogger(LiveTradingService.class);

    private final KiteConnectClient kiteConnectClient;
    private final RiskControls riskControls;

    private static final int MAX_CONCURRENT_POSITIONS = 3;
    private static final BigDecimal MAX_CAPITAL_PER_POSITION = new BigDecimal("10000");

    @Autowired
    public LiveTradingService(KiteConnectClient kiteConnectClient, RiskControls riskControls) {
        this.kiteConnectClient = kiteConnectClient;
        this.riskControls = riskControls;
        logger.info("LiveTradingService initialized with KiteConnectClient");
    }

    /**
     * Constructor for testing purposes.
     */
    public LiveTradingService(KiteConnectClient kiteConnectClient, RiskControls riskControls,
                              int maxPositions, BigDecimal maxCapital) {
        this.kiteConnectClient = kiteConnectClient;
        this.riskControls = riskControls;
    }

    /**
     * Place a new order through Kite Connect.
     * Validates risk controls before executing.
     *
     * @param order the order to place
     * @return the placed order with updated status
     */
    @Override
    public com.swingtrade.broker.model.Order placeOrder(com.swingtrade.broker.model.Order order) {
        logger.info("Placing live order for symbol: {} direction: {} quantity: {}",
                order.getSymbol(), order.getDirection(), order.getQuantity());

        // Create OrderResponse for risk validation
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setSymbol(order.getSymbol());
        orderResponse.setDirection(order.getDirection());
        orderResponse.setQuantity(order.getQuantity());
        orderResponse.setType(order.getType());
        orderResponse.setPrice(order.getPrice());
        orderResponse.setLimitPrice(order.getLimitPrice());
        orderResponse.setStopPrice(order.getStopPrice());

        // Get current market price for risk validation
        BigDecimal marketPrice = kiteConnectClient.getMarketPrice(order.getSymbol(), com.swingtrade.broker.model.Exchange.NSE);

        // Validate risk controls
        RiskCheckResult riskResult = riskControls.preTradeCheck(orderResponse, marketPrice);
        if (!riskResult.isPassed()) {
            logger.warn("Order rejected by risk controls: {}", riskResult.getMessages());
            order.setStatus(com.swingtrade.broker.model.OrderStatus.REJECTED);
            return order;
        }

        // Place the order through Kite Connect
        OrderResponse response = kiteConnectClient.placeOrder(orderResponse);

        // Update the Order with response data
        order.setStatus(response.getStatus());

        logger.info("Order placed. Status: {}", order.getStatus());
        return order;
    }

    /**
     * Cancel an existing order through Kite Connect.
     *
     * @param orderId the order to cancel
     * @return true if cancelled, false otherwise
     */
    @Override
    public boolean cancelOrder(String orderId) {
        logger.info("Cancelling live order: {}", orderId);
        return kiteConnectClient.cancelOrder(orderId);
    }

    /**
     * Get the current portfolio from Kite Connect.
     *
     * @return portfolio object
     */
    @Override
    public com.swingtrade.broker.model.Portfolio getPortfolio() {
        logger.debug("Fetching live portfolio from Kite Connect");
        return kiteConnectClient.getPortfolio();
    }

    /**
     * Get all open positions from Kite Connect.
     *
     * @return list of open positions
     */
    @Override
    public List<com.swingtrade.broker.model.Position> getOpenPositions() {
        logger.debug("Fetching live positions from Kite Connect");
        return kiteConnectClient.getPositions();
    }

    /**
     * Get a specific position from Kite Connect.
     *
     * @param symbol the symbol to retrieve
     * @return optional position
     */
    @Override
    public Optional<com.swingtrade.broker.model.Position> getPosition(String symbol) {
        logger.debug("Fetching position for symbol: {}", symbol);
        return kiteConnectClient.getPosition(symbol);
    }

    /**
     * Calculate profit and loss for a position.
     *
     * @param position the position to calculate P&L for
     * @return the calculated profit and loss
     */
    @Override
    public BigDecimal calculateProfitLoss(com.swingtrade.broker.model.Position position) {
        logger.debug("Calculating P&L for position: {}", position.getSymbol());

        // Get current market price
        BigDecimal currentPrice = kiteConnectClient.getMarketPrice(position.getSymbol(), position.getExchange());
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            logger.warn("Cannot calculate P&L: no market price for {}", position.getSymbol());
            return BigDecimal.ZERO;
        }

        // Calculate P&L based on direction
        BigDecimal priceChange;
        if (position.getDirection() == com.swingtrade.broker.model.TradeDirection.LONG) {
            priceChange = currentPrice.subtract(position.getEntryPrice());
        } else {
            priceChange = position.getEntryPrice().subtract(currentPrice);
        }
        BigDecimal unrealizedPnL = priceChange.multiply(position.getQuantity());
        position.setProfitLoss(unrealizedPnL);

        logger.info("Position P&L for {}: ₹{}, {}%", position.getSymbol(),
                unrealizedPnL, calculatePnLPercent(unrealizedPnL, position.getEntryPrice(), position.getQuantity()));

        return unrealizedPnL;
    }

    /**
     * Calculate P&L percentage.
     */
    private BigDecimal calculatePnLPercent(BigDecimal pnl, BigDecimal entryPrice, BigDecimal quantity) {
        BigDecimal costBasis = entryPrice.multiply(quantity);
        if (costBasis.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return pnl.multiply(BigDecimal.valueOf(100)).divide(costBasis, 4, BigDecimal.ROUND_HALF_UP);
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
}
