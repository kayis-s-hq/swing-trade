package com.swingtrade.broker.risk;

import com.swingtrade.broker.model.OrderResponse;
import com.swingtrade.broker.model.TradeDirection;
import com.swingtrade.broker.kite.KiteConnectClient;
import com.swingtrade.broker.manager.PositionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RiskControlsService.
 */
@ExtendWith(MockitoExtension.class)
class RiskControlsServiceTest {

    @Mock
    private PositionLimitChecker positionLimitChecker;

    @Mock
    private DailyLossCircuitBreaker dailyLossCircuitBreaker;

    @Mock
    private PositionSizeValidator positionSizeValidator;

    @Mock
    private PositionManager positionManager;

    @Mock
    private KiteConnectClient kiteConnectClient;

    @Mock
    private KillSwitchService killSwitchService;

    @Mock
    private CapitalTracker capitalTracker;

    private RiskControlsService riskControlsService;

    @BeforeEach
    void setUp() {
        // Mock kill switch as inactive by default
        when(killSwitchService.isActive()).thenReturn(false);

        // Mock capital tracker to pass limits
        when(capitalTracker.enforceLimits(any(), any())).thenReturn(new RiskCheckResult(true));

        riskControlsService = new RiskControlsService(positionLimitChecker, dailyLossCircuitBreaker,
                positionSizeValidator, positionManager, kiteConnectClient, killSwitchService, capitalTracker);
    }

    @Test
    void testPreTradeCheck_AllChecksPass() {
        // Given
        OrderResponse order = new OrderResponse();
        order.setSymbol("RELIANCE");
        order.setDirection(TradeDirection.LONG);
        order.setQuantity(new BigDecimal("10"));
        order.setLimitPrice(new BigDecimal("2500"));

        BigDecimal marketPrice = new BigDecimal("2500");

        // Mock all checks to pass
        when(dailyLossCircuitBreaker.canPlaceTrade(any(), any())).thenReturn(new RiskCheckResult(true));
        when(positionLimitChecker.canAddPosition(any())).thenReturn(new RiskCheckResult(true));
        when(positionSizeValidator.validatePositionSize(any())).thenReturn(new RiskCheckResult(true));

        // When
        RiskCheckResult result = riskControlsService.preTradeCheck(order, marketPrice);

        // Then
        assertThat(result.isPassed()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void testPreTradeCheck_KillSwitchActive() {
        // Given - activate kill switch
        when(killSwitchService.isActive()).thenReturn(true);

        OrderResponse order = new OrderResponse();
        order.setSymbol("RELIANCE");

        BigDecimal marketPrice = new BigDecimal("2500");

        // When
        RiskCheckResult result = riskControlsService.preTradeCheck(order, marketPrice);

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testPreTradeCheck_DailyLossCircuitOpen() {
        // Given
        OrderResponse order = new OrderResponse();
        order.setSymbol("RELIANCE");

        BigDecimal marketPrice = new BigDecimal("2500");

        // Mock daily loss circuit to be open
        RiskCheckResult circuitResult = new RiskCheckResult(false);
        circuitResult.addError("Circuit breaker is OPEN");
        when(dailyLossCircuitBreaker.canPlaceTrade(any(), any())).thenReturn(circuitResult);

        // When
        RiskCheckResult result = riskControlsService.preTradeCheck(order, marketPrice);

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testPreTradeCheck_PositionLimitReached() {
        // Given
        OrderResponse order = new OrderResponse();
        order.setSymbol("RELIANCE");

        BigDecimal marketPrice = new BigDecimal("2500");

        // Mock all checks to pass except position limit
        when(dailyLossCircuitBreaker.canPlaceTrade(any(), any())).thenReturn(new RiskCheckResult(true));

        // Mock position limit to fail
        RiskCheckResult positionResult = new RiskCheckResult(false);
        positionResult.addError("Position limit reached");
        when(positionLimitChecker.canAddPosition(any())).thenReturn(positionResult);

        // When
        RiskCheckResult result = riskControlsService.preTradeCheck(order, marketPrice);

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testSetKillSwitchActive() {
        // When enabled
        riskControlsService.setKillSwitchActive(true);

        // Then - verify the kill switch methods were called
        verify(killSwitchService).enableKillSwitch("Set via API");
        verify(dailyLossCircuitBreaker).openCircuit();

        // When disabled
        riskControlsService.setKillSwitchActive(false);

        // Then - verify the kill switch methods were called
        verify(killSwitchService).disableKillSwitch();
        verify(dailyLossCircuitBreaker).closeCircuit();
    }

    @Test
    void testGetCurrentPositionCount() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(3);

        // When
        int count = riskControlsService.getCurrentPositionCount();

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    void testGetRemainingPositionCapacity() {
        // Given
        when(positionLimitChecker.getRemainingPositionCapacity()).thenReturn(2);

        // When
        int remaining = riskControlsService.getRemainingPositionCapacity();

        // Then
        assertThat(remaining).isEqualTo(2);
    }

    @Test
    void testGetCurrentDailyPnL() {
        // Given
        when(dailyLossCircuitBreaker.getCurrentDailyPnL()).thenReturn(new BigDecimal("-5000"));

        // When
        BigDecimal pnl = riskControlsService.getCurrentDailyPnL();

        // Then
        assertThat(pnl).isEqualTo(new BigDecimal("-5000"));
    }

    @Test
    void testGetDailyLossPercent() {
        // Given
        when(dailyLossCircuitBreaker.getLossPercent()).thenReturn(new BigDecimal("-0.5"));

        // When
        BigDecimal percent = riskControlsService.getDailyLossPercent();

        // Then
        assertThat(percent).isEqualTo(new BigDecimal("-0.5"));
    }

    @Test
    void testUpdateRiskState() {
        // When
        riskControlsService.updateRiskState();

        // Then
        verify(dailyLossCircuitBreaker).updateWithCurrentPositions();
    }
}
