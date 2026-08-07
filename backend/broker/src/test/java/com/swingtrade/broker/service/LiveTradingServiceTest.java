package com.swingtrade.broker.service;

import com.swingtrade.broker.factory.LiveTradingService;
import com.swingtrade.broker.kite.BrokerClient;
import com.swingtrade.broker.model.*;
import com.swingtrade.broker.risk.RiskCheckResult;
import com.swingtrade.broker.risk.RiskControls;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.PositionStatus;
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
 * Tests that it delegates to BrokerClient and enforces risk controls.
 */
@ExtendWith(MockitoExtension.class)
class LiveTradingServiceTest {

    @Mock
    private BrokerClient brokerClient;

    @Mock
    private RiskControls riskControls;

    private LiveTradingService service;

    @BeforeEach
    void setUp() {
        service = new LiveTradingService(brokerClient, riskControls);
    }

    @Test
    void testPlaceOrder_callsBrokerClient() {
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

        when(brokerClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("2500"));
        when(riskControls.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));
        when(brokerClient.placeOrder(any())).thenReturn(mockResponse);

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getOrderId()).isEqualTo("kite-order-123");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(brokerClient).getMarketPrice("RELIANCE-EQ", Exchange.NSE);
        verify(brokerClient).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testPlaceOrder_validatesRiskControls() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("TCS-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("50"));

        when(brokerClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("3500"));
        when(riskControls.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));

        // When
        Order result = service.placeOrder(order);

        // Then
        verify(riskControls).preTradeCheck(any(OrderResponse.class), any(BigDecimal.class));
    }

    @Test
    void testPlaceOrder_riskLimitExceeded() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("HDFC-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("100"));

        when(brokerClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1450"));
        RiskCheckResult riskResult = new RiskCheckResult(false, "Position limit exceeded");
        when(riskControls.preTradeCheck(any(), any())).thenReturn(riskResult);

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(brokerClient, never()).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testPlaceOrder_circuitBreakerActive() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("INFY-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("75"));

        when(brokerClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1350"));
        RiskCheckResult riskResult = new RiskCheckResult(false, "Daily loss circuit breaker active");
        when(riskControls.preTradeCheck(any(), any())).thenReturn(riskResult);

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(brokerClient, never()).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testPlaceOrder_killSwitchActive() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("WIPRO-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("60"));

        when(brokerClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("450"));
        RiskCheckResult riskResult = new RiskCheckResult(false, "KILL SWITCH ACTIVE");
        when(riskControls.preTradeCheck(any(), any())).thenReturn(riskResult);

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(brokerClient, never()).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testCancelOrder_callsBrokerClient() {
        // Given
        String orderId = "kite-order-123";
        when(brokerClient.cancelOrder(orderId)).thenReturn(true);

        // When
        boolean result = service.cancelOrder(orderId);

        // Then
        assertThat(result).isTrue();
        verify(brokerClient).cancelOrder(orderId);
    }

    @Test
    void testGetPortfolio_callsBrokerClient() {
        // Given
        Portfolio mockPortfolio = new Portfolio("portfolio-1", new BigDecimal("100000"));
        when(brokerClient.getPortfolio()).thenReturn(mockPortfolio);

        // When
        Portfolio result = service.getPortfolio();

        // Then
        assertThat(result).isEqualTo(mockPortfolio);
        verify(brokerClient).getPortfolio();
    }

    @Test
    void testGetPositions_callsBrokerClient() {
        // Given
        List<Position> mockPositions = List.of(
            Position.of(1L, "PAPER", "RELIANCE-EQ", new BigDecimal("2500"), null, 100,
                new BigDecimal("2400"), new BigDecimal("2700"),
                PositionStatus.OPEN, "Signal", new BigDecimal("2550"),
                "pos-1", null, Exchange.NSE, TradeDirection.LONG,
                null, null, null, null, null, null, null, null)
        );
        when(brokerClient.getPositions()).thenReturn(mockPositions);

        // When
        List<Position> result = service.getOpenPositions();

        // Then
        assertThat(result).isEqualTo(mockPositions);
        verify(brokerClient).getPositions();
    }

    @Test
    void testGetPosition_callsBrokerClient() {
        // Given
        Position position = Position.of(1L, "PAPER", "RELIANCE-EQ", new BigDecimal("2500"), null, 100,
            new BigDecimal("2400"), new BigDecimal("2700"),
            PositionStatus.OPEN, "Signal", new BigDecimal("2550"),
            "pos-1", null, Exchange.NSE, TradeDirection.LONG,
            null, null, null, null, null, null, null, null);
        when(brokerClient.getPositions()).thenReturn(List.of(position));

        // When
        var result = service.getPosition("pos-1");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().symbol()).isEqualTo("RELIANCE-EQ");
    }

    @Test
    void testCalculateProfitLoss_calculatesCorrectly() {
        // Given
        Position position = Position.of(1L, "PAPER", "RELIANCE-EQ", new BigDecimal("2500"), null, 100,
            new BigDecimal("2400"), new BigDecimal("2700"),
            PositionStatus.OPEN, "Signal", new BigDecimal("2550"),
            "pos-1", null, Exchange.NSE, TradeDirection.LONG,
            null, null, null, null, null, null, null, null);

        // When
        BigDecimal result = service.calculateProfitLoss(position);

        // Then
        assertThat(result).isEqualTo(new BigDecimal("5000"));
    }

    @Test
    void testGetMaxConcurrentPositions_returnsDefault() {
        assertThat(service.getMaxConcurrentPositions()).isEqualTo(5);
    }

    @Test
    void testGetMaxCapitalPerPosition_returnsDefault() {
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

        when(brokerClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1100"));
        when(riskControls.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));
        when(brokerClient.placeOrder(any()))
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

        when(brokerClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("550"));
        when(riskControls.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));
        when(brokerClient.placeOrder(any()))
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

        when(brokerClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1700"));
        when(riskControls.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));
        when(brokerClient.placeOrder(any()))
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
        when(brokerClient.cancelOrder(orderId)).thenReturn(false);

        // When
        boolean result = service.cancelOrder(orderId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testGetPortfolio_null() {
        // Given
        when(brokerClient.getPortfolio()).thenReturn(null);

        // When
        Portfolio result = service.getPortfolio();

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testGetOpenPositions_empty() {
        // Given
        when(brokerClient.getPositions()).thenReturn(List.of());

        // When
        List<Position> result = service.getOpenPositions();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testCalculateProfitLoss_shortPosition() {
        // Given
        Position position = Position.of(1L, "PAPER", "TCS-EQ", new BigDecimal("3500"), null, 50,
            new BigDecimal("3400"), new BigDecimal("3700"),
            PositionStatus.OPEN, "Signal", new BigDecimal("3450"),
            "pos-1", null, Exchange.NSE, TradeDirection.SHORT,
            null, null, null, null, null, null, null, null);

        // When
        BigDecimal result = service.calculateProfitLoss(position);

        // Then
        assertThat(result).isEqualTo(new BigDecimal("2500"));
    }

    @Test
    void testGetBrokerClient_returnsMock() {
        // When/Then
        assertThat(service.getBrokerClient()).isSameAs(brokerClient);
    }

    @Test
    void testGetRiskControls_returnsMock() {
        // When/Then
        assertThat(service.getRiskControls()).isSameAs(riskControls);
    }

    @Test
    void testGetRiskControlsService_returnsMock() {
        // When/Then
        assertThat(service.getRiskControls()).isSameAs(riskControls);
    }
}
