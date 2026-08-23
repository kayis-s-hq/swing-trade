package com.swingtrade.broker;

import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.broker.model.*;
import com.swingtrade.broker.service.PaperTradingServiceImpl;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.Exchange;
import com.swingtrade.broker.service.PaperTradingStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.junit.jupiter.api.Disabled("Integration test - can be run separately with full Spring context")
public class BrokerModuleIntegrationTest {

    private PaperTradingServiceImpl brokerService;
    private PaperTradingEngine paperTradingEngine;
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("100000.00");

    @BeforeEach
    void setUp() {
        PaperTradingProperties props = new PaperTradingProperties();
        props.setInitialBalance(INITIAL_CAPITAL);
        OrderManager orderManager = new OrderManager();
        PositionManager positionManager = new PositionManager(props);
        paperTradingEngine = new PaperTradingEngine(orderManager, positionManager, props,
                org.mockito.Mockito.mock(com.swingtrade.core.metrics.TradeMetrics.class));
        brokerService = new PaperTradingServiceImpl(paperTradingEngine, orderManager, null);
    }

    @Test
    void testPaperTradingFunctionality_ComprehensiveTest() {
        // Test 1: Place order
        Order order = new Order(
            "order_1",
            "AAPL",
            OrderType.MARKET,
            TradeDirection.LONG,
            new BigDecimal("100"),
            new BigDecimal("150.00"),
            null,
            null
        );

        Order placedOrder = brokerService.placeOrder(order);
        assertNotNull(placedOrder);
        assertEquals(OrderStatus.ACCEPTED, placedOrder.getStatus());

        // Test 2: Get portfolio
        var portfolio = brokerService.getPortfolio();
        assertNotNull(portfolio);
        assertEquals(INITIAL_CAPITAL, portfolio.getCurrentCapital());

        // Test 3: Get open positions
        List<Position> openPositions = brokerService.getOpenPositions();
        assertNotNull(openPositions);

        // Test 4: Max positions constraint
        int maxPositions = brokerService.getMaxConcurrentPositions();
        assertEquals(5, maxPositions);

        // Test 5: Max capital per position constraint
        BigDecimal maxCapital = brokerService.getMaxCapitalPerPosition();
        assertEquals(new BigDecimal("20"), maxCapital);

        // Test 6: P&L calculation
        Position position = Position.of(1L, "PAPER", "AAPL", new BigDecimal("150.00"), null, 100,
            new BigDecimal("140.00"), new BigDecimal("170.00"),
            PositionStatus.OPEN, "Signal", new BigDecimal("155.00"),
            "pos_1", null, Exchange.NSE, TradeDirection.LONG,
            null, null, null, null, null, null, null, null);

        BigDecimal profitLoss = brokerService.calculateProfitLoss(position);
        assertEquals(new BigDecimal("500.00"), profitLoss);

        // Test 7: Position retrieval
        var retrievedPosition = brokerService.getPosition("nonexistent");
        assertTrue(retrievedPosition.isEmpty());

        // Test 8: Order cancellation
        boolean cancelled = brokerService.cancelOrder("order_1");
        assertTrue(cancelled);
    }

    @Test
    void testPositionManagement_FunctionalTest() {
        Position position = Position.of(1L, "PAPER", "AAPL", new BigDecimal("150.00"), null, 100,
            new BigDecimal("140.00"), new BigDecimal("170.00"),
            PositionStatus.OPEN, "Signal", new BigDecimal("155.00"),
            "pos_1", null, Exchange.NSE, TradeDirection.LONG,
            null, null, null, null, null, null, null, null);

        BigDecimal profitLoss = brokerService.calculateProfitLoss(position);
        assertEquals(new BigDecimal("500.00"), profitLoss);
    }

    @Test
    void testConstraintEnforcement() {
        assertEquals(5, brokerService.getMaxConcurrentPositions());
        assertEquals(new BigDecimal("20"), brokerService.getMaxCapitalPerPosition());
    }
}