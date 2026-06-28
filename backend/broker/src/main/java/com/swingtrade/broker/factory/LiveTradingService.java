package com.swingtrade.broker.factory;

import com.swingtrade.broker.kite.BrokerClient;
import com.swingtrade.broker.model.*;
import com.swingtrade.broker.risk.RiskCheckResult;
import com.swingtrade.broker.risk.RiskControls;
import com.swingtrade.broker.service.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Live trading service implementation using Zerodha Kite Connect.
 * Wraps KiteConnectClient with risk control integration.
 */
public class LiveTradingService implements BrokerService {

    private static final Logger logger = LoggerFactory.getLogger(LiveTradingService.class);

    private final BrokerClient brokerClient;
    private final RiskControls riskControls;

    private final int maxConcurrentPositions;
    private final BigDecimal maxCapitalPerPosition;

    public LiveTradingService(BrokerClient brokerClient,
                              RiskControls riskControls) {
        this.brokerClient = brokerClient;
        this.riskControls = riskControls;

        this.maxConcurrentPositions = 5; // Default, can be configured
        this.maxCapitalPerPosition = new BigDecimal("200000"); // Default, can be configured

        logger.info("LiveTradingService initialized");
    }

    @Override
    public Order placeOrder(Order order) {
        logger.info("Placing live order for symbol: {}", order.getSymbol());

        try {
            // Create order response
            OrderResponse orderResponse = new OrderResponse();
            orderResponse.setInternalOrderId(order.getOrderId());
            orderResponse.setSymbol(order.getSymbol());
            orderResponse.setType(order.getType());
            orderResponse.setDirection(order.getDirection());
            orderResponse.setQuantity(order.getQuantity());
            orderResponse.setPrice(order.getPrice());
            orderResponse.setLimitPrice(order.getLimitPrice());
            orderResponse.setStopPrice(order.getStopPrice());
            orderResponse.setExchange(Exchange.NSE); // Default to NSE

            // Get current market price
            BigDecimal marketPrice = brokerClient.getMarketPrice(order.getSymbol(), Exchange.NSE);
            if (marketPrice != null) {
                orderResponse.setPrice(marketPrice);
            }

            // Perform pre-trade risk checks
            RiskCheckResult riskResult = riskControls.preTradeCheck(orderResponse, marketPrice);
            if (!riskResult.isPassed()) {
                logger.warn("Pre-trade risk checks failed: {}", riskResult.getMessages());
                orderResponse.setStatus(OrderStatus.CANCELLED);
                orderResponse.setMessage("Risk check failed: " + String.join(", ", riskResult.getMessages()));
                order.setStatus(OrderStatus.CANCELLED); // Update order status before returning
                orderResponse.setMessage("Risk check failed: " + String.join(", ", riskResult.getMessages()));
                return order;
            }

            // Place order through broker
            orderResponse = brokerClient.placeOrder(orderResponse);

            // Update order with response
            order.setOrderId(orderResponse.getBrokerOrderId());
            order.setStatus(orderResponse.getStatus());
            order.setExecutionTime(java.time.LocalDateTime.now());

            logger.info("Live order placed successfully. Broker Order ID: {}",
                    orderResponse.getBrokerOrderId());

            return order;

        } catch (Exception e) {
            logger.error("Failed to place live order: {}", e.getMessage(), e);
            order.setStatus(OrderStatus.CANCELLED);
            return order;
        }
    }

    @Override
    public boolean cancelOrder(String orderId) {
        logger.info("Cancelling live order: {}", orderId);
        return brokerClient.cancelOrder(orderId);
    }

    @Override
    public Portfolio getPortfolio() {
        return brokerClient.getPortfolio();
    }

    @Override
    public List<Position> getOpenPositions() {
        return brokerClient.getPositions();
    }

    @Override
    public Optional<Position> getPosition(String positionId) {
        List<Position> positions = getOpenPositions();
        return positions.stream()
                .filter(p -> p.getPositionId().equals(positionId))
                .findFirst();
    }

    @Override
    public BigDecimal calculateProfitLoss(Position position) {
        if (position.getEntryPrice() != null && position.getCurrentPrice() != null &&
            position.getQuantity() != null) {

            BigDecimal priceChange = position.getCurrentPrice()
                    .subtract(position.getEntryPrice());

            if (position.getDirection() == TradeDirection.SHORT) {
                priceChange = priceChange.negate();
            }

            return priceChange.multiply(position.getQuantity());
        }

        return BigDecimal.ZERO;
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
     * Get the underlying broker client.
     */
    public BrokerClient getBrokerClient() {
        return brokerClient;
    }

    /**
     * Get the risk controls service.
     */
    public RiskControls getRiskControls() {
        return riskControls;
    }
}
