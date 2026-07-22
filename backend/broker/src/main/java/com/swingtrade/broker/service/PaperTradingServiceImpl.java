package com.swingtrade.broker.service;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.model.Order;
import com.swingtrade.broker.model.Portfolio;
import com.swingtrade.broker.model.Position;
import com.swingtrade.broker.model.TradeDirection;
import com.swingtrade.broker.service.PaperTradingStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the broker service for paper trading operations.
 * Coordinates with the PaperTradingEngine to provide trading functionality.
 */
@Service
public class PaperTradingServiceImpl implements BrokerService {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingServiceImpl.class);

    private final PaperTradingEngine paperTradingEngine;
    private final OrderManager orderManager;
    private final PaperTradingStateService stateService;

    public PaperTradingServiceImpl(PaperTradingEngine paperTradingEngine,
                                   OrderManager orderManager,
                                   PaperTradingStateService stateService) {
        this.paperTradingEngine = paperTradingEngine;
        this.orderManager = orderManager;
        this.stateService = stateService;
    }

    @Override
    public Order placeOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        Order engineOrder;
        if (order.getDirection() == TradeDirection.LONG) {
            engineOrder = orderManager.createBuyOrder(
                order.getSymbol(), order.getQuantity().intValue(), order.getPrice());
        } else {
            engineOrder = orderManager.createSellOrder(
                order.getSymbol(), order.getQuantity().intValue(), order.getPrice());
        }
        engineOrder = paperTradingEngine.executePendingOrder(engineOrder.getOrderId(), order.getPrice());
        if (stateService != null) {
            stateService.saveOrder(engineOrder);
            stateService.savePortfolio();
        }
        return engineOrder;
    }

    @Override
    public boolean cancelOrder(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return false;
        }
        return paperTradingEngine.cancelOrder(orderId);
    }

    @Override
    public Portfolio getPortfolio() {
        return paperTradingEngine.getPortfolio();
    }

    @Override
    public List<Position> getOpenPositions() {
        return paperTradingEngine.getOpenPositions();
    }

    @Override
    public Optional<Position> getPosition(String positionId) {
        return paperTradingEngine.getPosition(positionId);
    }

    @Override
    public BigDecimal calculateProfitLoss(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        return position.getProfitLoss() != null ? position.getProfitLoss() : BigDecimal.ZERO;
    }

    @Override
    public int getMaxConcurrentPositions() {
        return paperTradingEngine.getMaxConcurrentPositions();
    }

    @Override
    public BigDecimal getMaxCapitalPerPosition() {
        return paperTradingEngine.getMaxCapitalPerPosition();
    }
}