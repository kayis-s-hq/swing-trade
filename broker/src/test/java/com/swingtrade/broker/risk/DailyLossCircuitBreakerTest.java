package com.swingtrade.broker.risk;

import com.swingtrade.broker.model.Position;
import com.swingtrade.broker.manager.PositionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DailyLossCircuitBreaker.
 */
@ExtendWith(MockitoExtension.class)
class DailyLossCircuitBreakerTest {

    @Mock
    private PositionManager positionManager;

    @InjectMocks
    private DailyLossCircuitBreaker dailyLossCircuitBreaker;

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("1000000");
    private static final BigDecimal LOSS_THRESHOLD = new BigDecimal("2.0"); // 2%

    @BeforeEach
    void setUp() {
        reset(positionManager);
        // Reset circuit breaker state
        dailyLossCircuitBreaker.closeCircuit();
    }

    @Test
    void testIsTradingAllowed_CircuitClosed() {
        // When
        boolean allowed = dailyLossCircuitBreaker.isTradingAllowed();

        // Then
        assertThat(allowed).isTrue();
    }

    @Test
    void testOpenAndCloseCircuit() {
        // When
        dailyLossCircuitBreaker.openCircuit();

        // Then
        assertThat(dailyLossCircuitBreaker.isCircuitOpen()).isTrue();
        assertThat(dailyLossCircuitBreaker.isTradingAllowed()).isFalse();

        // When
        dailyLossCircuitBreaker.closeCircuit();

        // Then
        assertThat(dailyLossCircuitBreaker.isCircuitOpen()).isFalse();
        assertThat(dailyLossCircuitBreaker.isTradingAllowed()).isTrue();
    }

    @Test
    void testCanPlaceTrade_CircuitClosed() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(0);

        // When
        RiskCheckResult result = dailyLossCircuitBreaker.canPlaceTrade(
                new BigDecimal("50000"),
                new BigDecimal("1000")
        );

        // Then
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void testCanPlaceTrade_CircuitOpen() {
        // Given
        dailyLossCircuitBreaker.openCircuit();

        // When
        RiskCheckResult result = dailyLossCircuitBreaker.canPlaceTrade(
                new BigDecimal("50000"),
                new BigDecimal("1000")
        );

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("CIRCUIT BREAKER is OPEN"));
    }

    @Test
    void testGetCurrentDailyPnL() {
        // When
        BigDecimal pnl = dailyLossCircuitBreaker.getCurrentDailyPnL();

        // Then
        assertThat(pnl).isNotNull();
    }

    @Test
    void testGetLossPercent() {
        // When
        BigDecimal percent = dailyLossCircuitBreaker.getLossPercent();

        // Then
        assertThat(percent).isNotNull();
    }

    @Test
    void testCircuitOpensOnExcessiveLoss() {
        // Given - simulate large losses
        Position lossPosition = mock(Position.class);
        when(lossPosition.getProfitLoss()).thenReturn(new BigDecimal("-25000")); // -2.5% loss

        when(positionManager.getOpenPositions()).thenReturn(List.of(lossPosition));
        when(positionManager.getOpenPositionCount()).thenReturn(1);

        // When
        dailyLossCircuitBreaker.updateWithCurrentPositions();

        // Then
        assertThat(dailyLossCircuitBreaker.isCircuitOpen()).isTrue();
    }

    @Test
    void testCircuitStaysClosedOnNormalLoss() {
        // Given - simulate normal losses
        Position lossPosition = mock(Position.class);
        when(lossPosition.getProfitLoss()).thenReturn(new BigDecimal("-5000")); // -0.5% loss

        when(positionManager.getOpenPositions()).thenReturn(List.of(lossPosition));
        when(positionManager.getOpenPositionCount()).thenReturn(1);

        // When
        dailyLossCircuitBreaker.updateWithCurrentPositions();

        // Then
        assertThat(dailyLossCircuitBreaker.isCircuitOpen()).isFalse();
    }

    @Test
    void testGetCircuitOpenTime() {
        // Given
        LocalDateTime expectedTime = LocalDateTime.now();

        // When
        LocalDateTime actualTime = dailyLossCircuitBreaker.getCircuitOpenTime();

        // Then
        assertThat(actualTime).isNull(); // Should be null before opening circuit
    }

    @Test
    void testGetLossAtCircuitOpen() {
        // When
        BigDecimal loss = dailyLossCircuitBreaker.getLossAtCircuitOpen();

        // Then
        assertThat(loss).isNull(); // Should be null before opening circuit
    }
}
