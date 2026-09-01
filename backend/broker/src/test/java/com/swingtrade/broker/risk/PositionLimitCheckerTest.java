package com.swingtrade.broker.risk;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.manager.PositionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PositionLimitChecker.
 */
@ExtendWith(MockitoExtension.class)
class PositionLimitCheckerTest {

    @Mock
    private PositionManager positionManager;

    private PositionLimitChecker positionLimitChecker;

    private static final BigDecimal TEST_VALUE = new BigDecimal("50000");

    @BeforeEach
    void setUp() {
        positionLimitChecker = new PositionLimitChecker(positionManager, 5, BigDecimal.valueOf(200000));
    }

    @Test
    void testCanAddPosition_WithinLimits() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(3);

        // When
        RiskCheckResult result = positionLimitChecker.canAddPosition(TEST_VALUE);

        // Then
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("passed"));
    }

    @Test
    void testCanAddPosition_AtMaxPositions() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(5); // max is 5

        // When
        RiskCheckResult result = positionLimitChecker.canAddPosition(TEST_VALUE);

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("Maximum concurrent positions"));
    }

    @Test
    void testCanAddPosition_ExceedsPositionLimit() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(5);

        // When
        RiskCheckResult result = positionLimitChecker.canAddPosition(TEST_VALUE);

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testCanAddPosition_ExceedsCapitalLimit() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(2);
        BigDecimal largeValue = new BigDecimal("500000"); // exceeds default 200000

        // When
        RiskCheckResult result = positionLimitChecker.canAddPosition(largeValue);

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("exceeds maximum allowed"));
    }

    @Test
    void testGetMaxConcurrentPositions() {
        // When
        int max = positionLimitChecker.getMaxConcurrentPositions();

        // Then
        assertThat(max).isEqualTo(5);
    }

    @Test
    void brokerPropertiesDefault_matchesPaperTradingCapacity() {
        PositionLimitChecker configuredChecker =
                new PositionLimitChecker(positionManager, new BrokerProperties());

        assertThat(configuredChecker.getMaxConcurrentPositions()).isEqualTo(5);
    }

    @Test
    void testGetCurrentPositionCount() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(3);

        // When
        int count = positionLimitChecker.getCurrentPositionCount();

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    void testGetRemainingPositionCapacity() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(3);

        // When
        int remaining = positionLimitChecker.getRemainingPositionCapacity();

        // Then
        assertThat(remaining).isEqualTo(2);
    }

    @Test
    void testGetRemainingPositionCapacity_AtMax() {
        // Given
        when(positionManager.getOpenPositionCount()).thenReturn(5);

        // When
        int remaining = positionLimitChecker.getRemainingPositionCapacity();

        // Then
        assertThat(remaining).isEqualTo(0);
    }
}
