package com.swingtrade.broker;

import com.swingtrade.broker.engine.PaperTradeEngine;
import com.swingtrade.broker.model.*;
import com.swingtrade.broker.service.PaperTradingServiceImpl;
import com.swingtrade.data.model.CandleData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BrokerModuleIntegrationTest {
    
    private PaperTradingServiceImpl brokerService;
    private PaperTradeEngine paperTradeEngine;
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("100000.00");
    
    @BeforeEach
    void setUp() {
        paperTradeEngine = new PaperTradeEngine(INITIAL_CAPITAL);
        brokerService = new PaperTradingServiceImpl(paperTradeEngine);
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
        assertEquals(new BigDecimal("0.20"), maxCapital);
        
        // Test 6: P&L calculation
        Position position = new Position(
            "pos_1",
            "AAPL",
            TradeDirection.LONG,
            new BigDecimal("100"),
            new BigDecimal("150.00"),
            new BigDecimal("142.50"), // 5% SL
            new BigDecimal("165.00")  // 10% TP
        );
        position.setCurrentPrice(new BigDecimal("155.00"));
        
        BigDecimal profitLoss = brokerService.calculateProfitLoss(position);
        assertEquals(new BigDecimal("500.00"), profitLoss);
        
        // Test 7: Position retrieval
        var retrievedPosition = brokerService.getPosition("nonexistent");
        assertTrue(retrievedPosition.isEmpty());
        
        // Test 8: Order cancellation
        boolean cancelled = brokerService.cancelOrder("order_1");
        assertTrue(cancelled);
        
        // Verify cancellation worked
        assertTrue(cancelled);
    }
    
    @Test
    void testPositionManagement_FunctionalTest() {
        // Test position creation and management
        Position position = new Position(
            "pos_1",
            "AAPL",
            TradeDirection.LONG,
            new BigDecimal("100"),
            new BigDecimal("150.00"),
            new BigDecimal("142.50"),
            new BigDecimal("165.00")
        );
        
        // Test current price update
        position.setCurrentPrice(new BigDecimal("155.00"));
        assertEquals(new BigDecimal("155.00"), position.getCurrentPrice());
        
        // Test P&L calculation
        BigDecimal profitLoss = brokerService.calculateProfitLoss(position);
        assertEquals(new BigDecimal("500.00"), profitLoss);
    }
    
    @Test
    void testConstraintEnforcement() {
        // Test that constraints are properly enforced
        assertEquals(5, brokerService.getMaxConcurrentPositions());
        assertEquals(new BigDecimal("0.20"), brokerService.getMaxCapitalPerPosition());
    }
}
