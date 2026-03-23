package com.swingtrade.broker.service;

import com.swingtrade.broker.factory.LiveTradingService;
import com.swingtrade.broker.kite.KiteConnectClient;
import com.swingtrade.broker.model.*;
import com.swingtrade.broker.risk.RiskCheckResult;
import com.swingtrade.broker.risk.RiskControlsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LiveTradingService.
 * Tests that it delegates to KiteConnectClient and enforces risk controls.
 */
@ExtendWith(MockitoExtension.class)
class LiveTradingServiceTest {

    @Mock
    private KiteConnectClient kiteConnectClient;

    @Mock
    private RiskControlsService riskControlsService;

    private LiveTradingService service;

    @BeforeEach
    void setUp() {
        service = new LiveTradingService(kiteConnectClient, riskControlsService);
    }

    @Test
    void testPlaceOrder_callsKiteClient() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("RELIANCE-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("100"));
        order.setType(OrderType.MARKET);

        OrderResponse mockResponse = new OrderResponse();
        mockResponse.setBrokerOrderId("kite-order-123");
        mockResponse.setStatus(OrderStatus.ACCEPTED);

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("2500"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));
        when(kiteConnectClient.placeOrder(any())).thenReturn(mockResponse);

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getOrderId()).isEqualTo("kite-order-123");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(kiteConnectClient).getMarketPrice("RELIANCE-EQ", Exchange.NSE);
        verify(kiteConnectClient).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testPlaceOrder_validatesRiskControls() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("TCS-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("50"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("3500"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));

        // When
        Order result = service.placeOrder(order);

        // Then
        verify(riskControlsService).preTradeCheck(any(OrderResponse.class), any(BigDecimal.class));
    }

    @Test
    void testPlaceOrder_riskLimitExceeded() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("HDFC-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("100"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1450"));
        RiskCheckResult riskResult = new RiskCheckResult(false, "Position limit exceeded");
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(riskResult);

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(kiteConnectClient, never()).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testPlaceOrder_circuitBreakerActive() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("INFY-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("75"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1350"));
        RiskCheckResult riskResult = new RiskCheckResult(false, "Daily loss circuit breaker active");
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(riskResult);

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(kiteConnectClient, never()).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testPlaceOrder_killSwitchActive() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("WIPRO-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("60"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("450"));
        RiskCheckResult riskResult = new RiskCheckResult(false, "KILL SWITCH ACTIVE");
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(riskResult);

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(kiteConnectClient, never()).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testCancelOrder_callsKiteClient() {
        // Given
        String orderId = "kite-order-123";
        when(kiteConnectClient.cancelOrder(orderId)).thenReturn(true);

        // When
        boolean result = service.cancelOrder(orderId);

        // Then
        assertThat(result).isTrue();
        verify(kiteConnectClient).cancelOrder(orderId);
    }

    @Test
    void testGetPortfolio_callsKiteClient() {
        // Given
        Portfolio mockPortfolio = new Portfolio("portfolio-1", new BigDecimal("100000"));
        when(kiteConnectClient.getPortfolio()).thenReturn(mockPortfolio);

        // When
        Portfolio result = service.getPortfolio();

        // Then
        assertThat(result).isEqualTo(mockPortfolio);
        verify(kiteConnectClient).getPortfolio();
    }

    @Test
    void testGetPositions_callsKiteClient() {
        // Given
        List<Position> mockPositions = List.of(
            new Position("pos-1", "RELIANCE-EQ", TradeDirection.LONG,
                new BigDecimal("100"), new BigDecimal("2500"), null, null)
        );
        when(kiteConnectClient.getPositions()).thenReturn(mockPositions);

        // When
        List<Position> result = service.getOpenPositions();

        // Then
        assertThat(result).isEqualTo(mockPositions);
        verify(kiteConnectClient).getPositions();
    }

    @Test
    void testGetPosition_callsKiteClient() {
        // Given
        Position position = new Position("pos-1", "RELIANCE-EQ", TradeDirection.LONG,
            new BigDecimal("100"), new BigDecimal("2500"), null, null);
        when(kiteConnectClient.getPositions()).thenReturn(List.of(position));

        // When
        var result = service.getPosition("pos-1");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getSymbol()).isEqualTo("RELIANCE-EQ");
    }

    @Test
    void testCalculateProfitLoss_callsKiteClient() {
        // Given
        Position position = new Position();
        position.setSymbol("RELIANCE-EQ");
        position.setDirection(TradeDirection.LONG);
        position.setQuantity(new BigDecimal("100"));
        position.setEntryPrice(new BigDecimal("2500"));
        position.setCurrentPrice(new BigDecimal("2550"));

        // When
        BigDecimal result = service.calculateProfitLoss(position);

        // Then
        assertThat(result).isEqualTo(new BigDecimal("5000"));
    }

    @Test
    void testGetMaxConcurrentPositions_returns3() {
        assertThat(service.getMaxConcurrentPositions()).isEqualTo(5);
    }

    @Test
    void testGetMaxCapitalPerPosition_returnsConfigValue() {
        assertThat(service.getMaxCapitalPerPosition()).isEqualTo(new BigDecimal("200000"));
    }

    @Test
    void testPlaceOrder_apiException() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("AXISBANK-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("80"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1100"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));
        when(kiteConnectClient.placeOrder(any()))
                .thenThrow(new RuntimeException("API Error: Connection timeout"));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void testPlaceOrder_orderTimeout() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("SBIN-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("90"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("550"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));
        when(kiteConnectClient.placeOrder(any()))
                .thenThrow(new RuntimeException("Request timeout"));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void testPlaceOrder_orderRejected() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("KOTAKBANK-EQ");
        order.setDirection(TradeDirection.SHORT);
        order.setQuantity(new BigDecimal("40"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1700"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));
        when(kiteConnectClient.placeOrder(any()))
                .thenThrow(new RuntimeException("Order rejected: Insufficient funds"));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void testCancelOrder_failure() {
        // Given
        String orderId = "kite-order-999";
        when(kiteConnectClient.cancelOrder(orderId)).thenReturn(false);

        // When
        boolean result = service.cancelOrder(orderId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testGetPortfolio_null() {
        // Given
        when(kiteConnectClient.getPortfolio()).thenReturn(null);

        // When
        Portfolio result = service.getPortfolio();

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testGetOpenPositions_empty() {
        // Given
        when(kiteConnectClient.getPositions()).thenReturn(List.of());

        // When
        List<Position> result = service.getOpenPositions();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testCalculateProfitLoss_shortPosition() {
        // Given
        Position position = new Position();
        position.setSymbol("TCS-EQ");
        position.setDirection(TradeDirection.SHORT);
        position.setQuantity(new BigDecimal("50"));
        position.setEntryPrice(new BigDecimal("3500"));
        position.setCurrentPrice(new BigDecimal("3450"));

        // When
        BigDecimal result = service.calculateProfitLoss(position);

        // Then
        assertThat(result).isEqualTo(new BigDecimal("2500"));
    }
}
