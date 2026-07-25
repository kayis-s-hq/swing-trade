package com.swingtrade.broker.risk;

import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DailyLossCircuitBreaker.
 */
@ExtendWith(MockitoExtension.class)
class DailyLossCircuitBreakerTest {

    @Mock
    private PositionManager positionManager;

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("1000000");
    private static final BigDecimal LOSS_THRESHOLD = new BigDecimal("2.0"); // 2%

    private DailyLossCircuitBreaker dailyLossCircuitBreaker;

    @BeforeEach
    void setUp() {
        dailyLossCircuitBreaker = new DailyLossCircuitBreaker(positionManager, LOSS_THRESHOLD, INITIAL_CAPITAL);
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
        // Given - simulate large losses (-2.5% of 1M = -25000)
        Position lossPosition = new Position(
            null, "RELIANCE", new BigDecimal("2000"), java.time.LocalDate.now(),
            100, new BigDecimal("1900"), new BigDecimal("2200"),
            PositionStatus.OPEN, "test", new BigDecimal("1975"),
            null, null, null, TradeDirection.LONG,
            null, new BigDecimal("-25000"), BigDecimal.ZERO, null,
            null, null, null, null
        );
        when(positionManager.getOpenPositions()).thenReturn(java.util.Collections.singletonList(lossPosition));

        // When
        dailyLossCircuitBreaker.updateWithCurrentPositions();

        // Then
        assertThat(dailyLossCircuitBreaker.isCircuitOpen()).isTrue();
    }

    @Test
    void testCircuitStaysClosedOnNormalLoss() {
        // Given - simulate normal losses (-0.5% of 1M = -5000)
        Position lossPosition = new Position(
            null, "TCS", new BigDecimal("3500"), java.time.LocalDate.now(),
            50, new BigDecimal("3400"), new BigDecimal("3700"),
            PositionStatus.OPEN, "test", new BigDecimal("3490"),
            null, null, null, TradeDirection.LONG,
            null, new BigDecimal("-5000"), BigDecimal.ZERO, null,
            null, null, null, null
        );
        when(positionManager.getOpenPositions()).thenReturn(java.util.Collections.singletonList(lossPosition));

        // When
        dailyLossCircuitBreaker.updateWithCurrentPositions();

        // Then
        assertThat(dailyLossCircuitBreaker.isCircuitOpen()).isFalse();
    }

    @Test
    void testGetCircuitOpenTime() {
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
