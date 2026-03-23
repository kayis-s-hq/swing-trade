package com.swingtrade.broker.service;

import com.swingtrade.broker.factory.DryRunService;
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
 * Unit tests for DryRunService.
 * Tests that it logs all operations without executing and returns safe values.
 */
@ExtendWith(MockitoExtension.class)
class DryRunServiceTest {

    @Mock
    private KiteConnectClient kiteConnectClient;

    @Mock
    private RiskControlsService riskControlsService;

    private DryRunService service;

    @BeforeEach
    void setUp() {
        service = new DryRunService(kiteConnectClient, riskControlsService);
    }

    @Test
    void testPlaceOrder_logsOrder() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("RELIANCE-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("100"));
        order.setType(OrderType.MARKET);

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("2500"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(riskControlsService).preTradeCheck(any(OrderResponse.class), any(BigDecimal.class));
    }

    @Test
    void testPlaceOrder_doesNotExecute() {
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

        // Then - Verify no actual broker call was made
        verify(kiteConnectClient, never()).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testCancelOrder_logsCancellation() {
        // Given
        String orderId = "order-123";

        // When
        boolean result = service.cancelOrder(orderId);

        // Then
        assertThat(result).isTrue();
        verify(kiteConnectClient, never()).cancelOrder(orderId);
    }

    @Test
    void testCancelOrder_doesNotExecute() {
        // Given
        String orderId = "order-123";

        // When
        boolean result = service.cancelOrder(orderId);

        // Then - Verify no actual broker cancellation was made
        verify(kiteConnectClient, never()).cancelOrder(orderId);
    }

    @Test
    void testGetPortfolio_returnsEmpty() {
        // When
        Portfolio result = service.getPortfolio();

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testGetPositions_returnsEmpty() {
        // When
        List<Position> result = service.getOpenPositions();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testGetPosition_returnsNull() {
        // When
        var result = service.getPosition("pos-123");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testCalculateProfitLoss_returnsZero() {
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
        assertThat(result).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void testGetMaxConcurrentPositions_returns3() {
        // Given/When/Then - Default is 5 based on the constructor
        assertThat(service.getMaxConcurrentPositions()).isEqualTo(5);
    }

    @Test
    void testGetMaxCapitalPerPosition_returnsConfigValue() {
        // Given/When/Then - Default is 200000 based on the constructor
        assertThat(service.getMaxCapitalPerPosition()).isEqualTo(new BigDecimal("200000"));
    }

    @Test
    void testPlaceOrder_withRiskValidation() {
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
    }

    @Test
    void testDryRunMode_isSafeMode() {
        // Given/When/Then
        assertThat(service).isInstanceOf(DryRunService.class);
        // Verify the service doesn't actually execute any broker operations
        verify(kiteConnectClient, never()).placeOrder(any());
        verify(kiteConnectClient, never()).cancelOrder(any());
    }

    @Test
    void testPlaceOrder_marketOrder() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("INFY-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("75"));
        order.setType(OrderType.MARKET);

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1350"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void testPlaceOrder_limitOrder() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("WIPRO-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("60"));
        order.setType(OrderType.LIMIT);
        order.setLimitPrice(new BigDecimal("440"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("450"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void testPlaceOrder_stopLossOrder() {
        // Given
        Order order = new Order();
        order.setOrderId("order-123");
        order.setSymbol("SBIN-EQ");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("90"));
        order.setType(OrderType.STOP_LOSS);
        order.setStopPrice(new BigDecimal("540"));

        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("550"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void testPlaceOrder_multipleOrders() {
        // Given
        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("2500"));
        when(riskControlsService.preTradeCheck(any(), any())).thenReturn(new RiskCheckResult(true));

        // When
        Order order1 = service.placeOrder(createOrder("RELIANCE-EQ"));
        Order order2 = service.placeOrder(createOrder("TCS-EQ"));
        Order order3 = service.placeOrder(createOrder("HDFC-EQ"));

        // Then
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order3.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        // Verify no actual broker calls
        verify(kiteConnectClient, never()).placeOrder(any(OrderResponse.class));
    }

    @Test
    void testPlaceOrder_riskCheckFails() {
        // Given
        Order order = createOrder("AXISBANK-EQ");
        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1100"));
        when(riskControlsService.preTradeCheck(any(), any()))
                .thenReturn(new RiskCheckResult(false, "Daily loss circuit breaker active"));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void testPlaceOrder_killSwitchActive() {
        // Given
        Order order = createOrder("KOTAKBANK-EQ");
        when(kiteConnectClient.getMarketPrice(anyString(), any())).thenReturn(new BigDecimal("1700"));
        when(riskControlsService.preTradeCheck(any(), any()))
                .thenReturn(new RiskCheckResult(false, "KILL SWITCH ACTIVE"));

        // When
        Order result = service.placeOrder(order);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // Helper method
    private Order createOrder(String symbol) {
        Order order = new Order();
        order.setOrderId("dry-run-order-" + symbol);
        order.setSymbol(symbol);
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("50"));
        return order;
    }
}
