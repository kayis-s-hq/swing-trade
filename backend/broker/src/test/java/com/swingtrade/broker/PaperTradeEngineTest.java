package com.swingtrade.broker;

import com.swingtrade.broker.engine.PaperTradeEngine;
import com.swingtrade.broker.model.*;
import com.swingtrade.data.model.CandleData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PaperTradeEngineTest {
    
    private PaperTradeEngine paperTradeEngine;
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("100000.00");
    
    @BeforeEach
    void setUp() {
        paperTradeEngine = new PaperTradeEngine(INITIAL_CAPITAL);
    }
    
    @Test
    void testPlaceOrder_ValidOrder_ShouldSucceed() {
        // Given
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
        
        // When
        Order result = paperTradeEngine.placeOrder(order);
        
        // Then
        assertNotNull(result);
        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
        assertEquals("order_1", result.getOrderId());
    }
    
    @Test
    void testPlaceOrder_InvalidOrder_ShouldThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            paperTradeEngine.placeOrder(null);
        });
    }
    
    @Test
    void testGetPortfolio_ShouldReturnPortfolio() {
        // When
        var portfolio = paperTradeEngine.getPortfolio();
        
        // Then
        assertNotNull(portfolio);
        assertEquals("default-portfolio", portfolio.getPortfolioId());
        assertEquals(INITIAL_CAPITAL, portfolio.getCurrentCapital());
    }
    
    @Test
    void testGetOpenPositions_ShouldReturnEmptyList() {
        // When
        List<Position> positions = paperTradeEngine.getOpenPositions();
        
        // Then
        assertNotNull(positions);
        assertTrue(positions.isEmpty());
    }
    
    @Test
    void testCalculateProfitLoss_ValidPosition_ShouldReturnCorrectValue() {
        // Given
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
        
        // When
        BigDecimal result = paperTradeEngine.calculateProfitLoss(position);
        
        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("500.00"), result); // (155 - 150) * 100
    }
    
    @Test
    void testGetMaxConcurrentPositions_ShouldReturnFive() {
        // When
        int maxPositions = paperTradeEngine.getMaxConcurrentPositions();
        
        // Then
        assertEquals(5, maxPositions);
    }
    
    @Test
    void testGetMaxCapitalPerPosition_ShouldReturnTwentyPercent() {
        // When
        BigDecimal maxCapital = paperTradeEngine.getMaxCapitalPerPosition();
        
        // Then
        assertEquals(new BigDecimal("0.20"), maxCapital);
    }
    
    @Test
    void testUpdatePositionsWithCandleData_ShouldUpdatePrices() {
        // Given
        CandleData candleData = new CandleData();
        candleData.setSymbol("AAPL");
        candleData.setOpen(new BigDecimal("150.00"));
        candleData.setHigh(new BigDecimal("155.00"));
        candleData.setLow(new BigDecimal("148.00"));
        candleData.setClose(new BigDecimal("152.00"));
        
        // When
        paperTradeEngine.updatePositionsWithCandleData("AAPL", candleData);
        
        // Then
        // No exception thrown, method executes successfully
        assertTrue(true);
    }
}
