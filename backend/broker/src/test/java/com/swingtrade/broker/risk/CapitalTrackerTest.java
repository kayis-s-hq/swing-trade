package com.swingtrade.broker.risk;

import com.swingtrade.broker.risk.CapitalTracker.CapitalSummary;
import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CapitalTracker covering limit enforcement, capital calculations,
 * summary, capacity, and edge cases.
 */
class CapitalTrackerTest {

    private CapitalTracker tracker;

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("50000");
    private static final int MAX_POSITIONS = 3;
    private static final BigDecimal MAX_CAPITAL_PER_POSITION = new BigDecimal("10000");

    @BeforeEach
    void setUp() {
        tracker = new CapitalTracker(INITIAL_CAPITAL, MAX_POSITIONS, MAX_CAPITAL_PER_POSITION);
    }

    // Helper: build a Position for testing
    private Position makePosition(String symbol, int quantity, BigDecimal entryPrice) {
        return Position.of(null, "PAPER", symbol, entryPrice, LocalDate.now(),
                quantity, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                "Test", entryPrice, "POS_00000001", null, Exchange.NSE,
                TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                java.time.LocalDateTime.now(), null, null, null);
    }

    // ==================== Enforce Limits ====================

    @Nested
    class EnforceLimits {

        @Test
        void happyPath_allChecksPass() {
            // Given: No positions, order within limits
            BigDecimal orderValue = new BigDecimal("5000");

            // When
            RiskCheckResult result = tracker.enforceLimits(List.of(), orderValue);

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).anySatisfy(msg -> assertThat(msg).contains("Capital limits validated"));
        }

        @Test
        void maxPositionsExceeded() {
            // Given: 3 open positions (max is 3)
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000")),
                    makePosition("INFY-EQ", 20, new BigDecimal("500"))
            );

            // When
            RiskCheckResult result = tracker.enforceLimits(positions, new BigDecimal("1000"));

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).isNotEmpty();
            assertThat(result.getMessages()).anyMatch(e -> e.contains("Maximum concurrent positions limit reached"));
        }

        @Test
        void maxCapitalPerPositionExceeded() {
            // Given: 0 positions, order exceeds max per position
            BigDecimal orderValue = new BigDecimal("15000");

            // When
            RiskCheckResult result = tracker.enforceLimits(List.of(), orderValue);

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).isNotEmpty();
            assertThat(result.getMessages()).anyMatch(e -> e.contains("exceeds maximum capital per position"));
        }

        @Test
        void maxTotalExposureExceeded() {
            // Given: Positions already using most of the capital
            // maxTotalExposure = 50000 * 0.8 = 40000
            // Per-position max = 10000, order 25000 exceeds that
            // So per-position check fires first
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))
            );

            // When
            RiskCheckResult result = tracker.enforceLimits(positions, new BigDecimal("25000"));

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).isNotEmpty();
            assertThat(result.getMessages()).anyMatch(e -> e.contains("exceeds maximum capital per position"));
        }

        @Test
        void boundaryValues_maxPositionsMinusOne() {
            // Given: 2 open positions (max is 3)
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))
            );

            // When
            RiskCheckResult result = tracker.enforceLimits(positions, new BigDecimal("10000"));

            // Then
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        void nullPositions_throwsNpe() {
            // Given: Null positions list
            BigDecimal orderValue = new BigDecimal("5000");

            // When / Then
            assertThatThrownBy(() -> tracker.enforceLimits(null, orderValue))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void emptyPositions() {
            // Given: Empty positions list
            BigDecimal orderValue = new BigDecimal("5000");

            // When
            RiskCheckResult result = tracker.enforceLimits(List.of(), orderValue);

            // Then
            assertThat(result.isPassed()).isTrue();
        }
    }

    // ==================== canAddPosition ====================

    @Nested
    class CanAddPosition {

        @Test
        void true_whenWithinLimits() {
            // Given: No positions
            // When
            boolean canAdd = tracker.canAddPosition(List.of(), new BigDecimal("5000"));

            // Then
            assertThat(canAdd).isTrue();
        }

        @Test
        void true_whenOnePosition() {
            // Given: One position
            List<Position> positions = List.of(makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")));

            // When
            boolean canAdd = tracker.canAddPosition(positions, new BigDecimal("5000"));

            // Then
            assertThat(canAdd).isTrue();
        }

        @Test
        void false_whenMaxPositionsReached() {
            // Given: 3 positions (max)
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000")),
                    makePosition("INFY-EQ", 20, new BigDecimal("500"))
            );

            // When
            boolean canAdd = tracker.canAddPosition(positions, new BigDecimal("1000"));

            // Then
            assertThat(canAdd).isFalse();
        }

        @Test
        void false_whenOrderTooLarge() {
            // Given: No positions, but order exceeds max per position
            // When
            boolean canAdd = tracker.canAddPosition(List.of(), new BigDecimal("15000"));

            // Then
            assertThat(canAdd).isFalse();
        }

        @Test
        void false_whenExposureTooLarge() {
            // Given: Positions using most of capital
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))
            );

            // When
            boolean canAdd = tracker.canAddPosition(positions, new BigDecimal("25000"));

            // Then
            assertThat(canAdd).isFalse();
        }

        @Test
        void nullPositions_returnsFalse() {
            // When
            boolean canAdd = tracker.canAddPosition(null, new BigDecimal("5000"));

            // Then: canAddPosition guards null
            assertThat(canAdd).isFalse();
        }
    }

    // ==================== Capital Calculations ====================

    @Nested
    class CapitalCalculations {

        @Test
        void getDeployedCapital_emptyPositions() {
            // When
            BigDecimal deployed = tracker.getDeployedCapital(List.of());

            // Then
            assertThat(deployed).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void getDeployedCapital_onePosition() {
            // Given: One position, qty=10, entryPrice=1000 => deployed = 10000
            List<Position> positions = List.of(makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")));

            // When
            BigDecimal deployed = tracker.getDeployedCapital(positions);

            // Then
            assertThat(deployed).isEqualTo(new BigDecimal("10000"));
        }

        @Test
        void getDeployedCapital_multiplePositions() {
            // Given: Two positions
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")), // 10000
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))        // 10000
            );

            // When
            BigDecimal deployed = tracker.getDeployedCapital(positions);

            // Then
            assertThat(deployed).isEqualTo(new BigDecimal("20000"));
        }

        @Test
        void getDeployedCapital_nullPositionInList() {
            // Given: List with null position
            List<Position> positions = new java.util.ArrayList<>(java.util.Arrays.asList(null, makePosition("RELIANCE-EQ", 10, new BigDecimal("1000"))));

            // When
            BigDecimal deployed = tracker.getDeployedCapital(positions);

            // Then: null positions in list are skipped, only valid positions count
            assertThat(deployed).isNotNull();
            assertThat(deployed).isEqualByComparingTo(new BigDecimal("10000"));
        }

        @Test
        void getAvailableCapital_fullCapital() {
            // Given: No positions
            // When
            BigDecimal available = tracker.getAvailableCapital(List.of());

            // Then
            assertThat(available).isEqualTo(INITIAL_CAPITAL);
        }

        @Test
        void getAvailableCapital_partialDeployment() {
            // Given: Positions deployed 20000
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))
            );

            // When
            BigDecimal available = tracker.getAvailableCapital(positions);

            // Then: 50000 - 20000 = 30000
            assertThat(available).isEqualTo(new BigDecimal("30000"));
        }

        @Test
        void utilizationRatio() {
            // Given: Positions deployed 20000 out of 50000
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))
            );

            // When
            BigDecimal utilization = tracker.getUtilizationPercentage(positions);

            // Then: 20000/50000*100 = 40%
            assertThat(utilization).isEqualTo(new BigDecimal("40.0000"));
        }

        @Test
        void utilizationRatio_zero() {
            // When
            BigDecimal utilization = tracker.getUtilizationPercentage(List.of());

            // Then: divide with scale 4 produces 0.0000, not 0
            assertThat(utilization).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void utilizationRatio_fullDeployment() {
            // Given: Positions deployed 50000 (100%)
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 50, new BigDecimal("1000"))
            );

            // When
            BigDecimal utilization = tracker.getUtilizationPercentage(positions);

            // Then: 50000/50000*100 = 100%
            assertThat(utilization).isEqualTo(new BigDecimal("100.0000"));
        }
    }

    // ==================== Capital Summary ====================

    @Nested
    class SummaryTests {

        @Test
        void summaryObjectCreation() {
            // Given: Positions deployed 20000
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))
            );

            // When
            CapitalSummary summary = tracker.getCapitalSummary(positions);

            // Then
            assertThat(summary.getInitialCapital()).isEqualTo(INITIAL_CAPITAL);
            assertThat(summary.getDeployedCapital()).isEqualTo(new BigDecimal("20000"));
            assertThat(summary.getAvailableCapital()).isEqualTo(new BigDecimal("30000"));
            assertThat(summary.getMaxPositions()).isEqualTo(MAX_POSITIONS);
            assertThat(summary.getCurrentPositions()).isEqualTo(2);
            assertThat(summary.getUtilizationPercentage()).isEqualTo(new BigDecimal("40.0000"));
        }

        @Test
        void summaryEmptyPositions() {
            // When
            CapitalSummary summary = tracker.getCapitalSummary(List.of());

            // Then
            assertThat(summary.getInitialCapital()).isEqualTo(INITIAL_CAPITAL);
            assertThat(summary.getDeployedCapital()).isEqualTo(BigDecimal.ZERO);
            assertThat(summary.getAvailableCapital()).isEqualTo(INITIAL_CAPITAL);
            assertThat(summary.getCurrentPositions()).isZero();
            assertThat(summary.getUtilizationPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void summaryAllPositions() {
            // Given: 3 positions (max)
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000")),
                    makePosition("INFY-EQ", 20, new BigDecimal("500"))
            );

            // When
            CapitalSummary summary = tracker.getCapitalSummary(positions);

            // Then
            assertThat(summary.getCurrentPositions()).isEqualTo(3);
            assertThat(summary.getMaxPositions()).isEqualTo(3);
        }

        @Test
        void summaryUtilizationPercentage() {
            // Given: Half capital deployed
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 25, new BigDecimal("1000"))
            );

            // When
            CapitalSummary summary = tracker.getCapitalSummary(positions);

            // Then: 25000/50000*100 = 50%
            assertThat(summary.getUtilizationPercentage()).isEqualTo(new BigDecimal("50.0000"));
        }
    }

    // ==================== Capacity ====================

    @Nested
    class Capacity {

        @Test
        void getRemainingPositionCapacity_fullCapacity() {
            // When
            int remaining = tracker.getRemainingPositionCapacity(List.of());

            // Then
            assertThat(remaining).isEqualTo(MAX_POSITIONS);
        }

        @Test
        void getRemainingPositionCapacity_partialUsed() {
            // Given: 2 positions
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))
            );

            // When
            int remaining = tracker.getRemainingPositionCapacity(positions);

            // Then
            assertThat(remaining).isEqualTo(1);
        }

        @Test
        void getRemainingPositionCapacity_atMax() {
            // Given: 3 positions (max)
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000")),
                    makePosition("INFY-EQ", 20, new BigDecimal("500"))
            );

            // When
            int remaining = tracker.getRemainingPositionCapacity(positions);

            // Then
            assertThat(remaining).isZero();
        }

        @Test
        void getRemainingPositionCapacity_exceedsMax() {
            // Given: 5 positions (exceeds max)
            List<Position> positions = List.of(
                    makePosition("S1", 1, new BigDecimal("1000")),
                    makePosition("S2", 1, new BigDecimal("1000")),
                    makePosition("S3", 1, new BigDecimal("1000")),
                    makePosition("S4", 1, new BigDecimal("1000")),
                    makePosition("S5", 1, new BigDecimal("1000"))
            );

            // When
            int remaining = tracker.getRemainingPositionCapacity(positions);

            // Then: Math.max(0, 3-5) = 0
            assertThat(remaining).isZero();
        }

        @Test
        void getRemainingPositionCapacity_nullPositions() {
            // When
            int remaining = tracker.getRemainingPositionCapacity(null);

            // Then
            assertThat(remaining).isEqualTo(MAX_POSITIONS);
        }

        @Test
        void getCurrentPositionCount() {
            // Given: 2 positions
            List<Position> positions = List.of(
                    makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")),
                    makePosition("TCS-EQ", 5, new BigDecimal("2000"))
            );

            // When
            int count = tracker.getCurrentPositionCount(positions);

            // Then
            assertThat(count).isEqualTo(2);
        }

        @Test
        void getCurrentPositionCount_empty() {
            // When
            int count = tracker.getCurrentPositionCount(List.of());

            // Then
            assertThat(count).isZero();
        }

        @Test
        void getCurrentPositionCount_null() {
            // When
            int count = tracker.getCurrentPositionCount(null);

            // Then
            assertThat(count).isZero();
        }

        @Test
        void getInitialCapital() {
            // When
            BigDecimal capital = tracker.getInitialCapital();

            // Then
            assertThat(capital).isEqualTo(INITIAL_CAPITAL);
        }

        @Test
        void getMaxPositions() {
            // When
            int max = tracker.getMaxPositions();

            // Then
            assertThat(max).isEqualTo(MAX_POSITIONS);
        }

        @Test
        void getMaxCapitalPerPosition() {
            // When
            BigDecimal max = tracker.getMaxCapitalPerPosition();

            // Then
            assertThat(max).isEqualTo(MAX_CAPITAL_PER_POSITION);
        }

        @Test
        void getRemainingCapitalPerPosition() {
            // Given: Some positions
            List<Position> positions = List.of(makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")));

            // When
            BigDecimal remaining = tracker.getRemainingCapitalPerPosition(positions);

            // Then: always returns maxCapitalPerPosition regardless of current usage
            assertThat(remaining).isEqualTo(MAX_CAPITAL_PER_POSITION);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void zeroCapital() {
            // Given: Zero initial capital
            CapitalTracker zeroTracker = new CapitalTracker(BigDecimal.ZERO, MAX_POSITIONS, MAX_CAPITAL_PER_POSITION);

            // When
            BigDecimal utilization = zeroTracker.getUtilizationPercentage(List.of());

            // Then
            assertThat(utilization).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void negativeValues() {
            // Given: Negative initial capital (edge case)
            CapitalTracker negTracker = new CapitalTracker(new BigDecimal("-50000"), MAX_POSITIONS, MAX_CAPITAL_PER_POSITION);

            // When
            BigDecimal deployed = negTracker.getDeployedCapital(List.of());

            // Then
            assertThat(deployed).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void veryLargeNumbers() {
            // Given: Very large capital
            CapitalTracker largeTracker = new CapitalTracker(
                    new BigDecimal("999999999999.99"), MAX_POSITIONS, new BigDecimal("999999999.99"));

            // When
            BigDecimal deployed = largeTracker.getDeployedCapital(List.of());
            BigDecimal available = largeTracker.getAvailableCapital(List.of());

            // Then
            assertThat(deployed).isEqualTo(BigDecimal.ZERO);
            assertThat(available).isEqualTo(new BigDecimal("999999999999.99"));
        }

        @Test
        void defaults() {
            // When
            BigDecimal deployed = tracker.getDeployedCapital(List.of());
            BigDecimal available = tracker.getAvailableCapital(List.of());

            // Then
            assertThat(deployed).isEqualTo(BigDecimal.ZERO);
            assertThat(available).isEqualTo(INITIAL_CAPITAL);
        }

        @Test
        void positionWithNullFields() {
            // Given: Position with null entry price and quantity
            Position pos = Position.of(null, "PAPER", "RELIANCE-EQ", null, LocalDate.now(),
                    null, BigDecimal.ZERO, BigDecimal.ZERO, PositionStatus.OPEN,
                    "Test", null, "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    java.time.LocalDateTime.now(), null, null, null);

            // When
            BigDecimal deployed = tracker.getDeployedCapital(List.of(pos));

            // Then
            assertThat(deployed).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void updateDeployedCapital() {
            // Given: Positions
            List<Position> positions = List.of(makePosition("RELIANCE-EQ", 10, new BigDecimal("1000")));

            // When
            tracker.updateDeployedCapital(positions);

            // Then: No exception, deployed capital updated internally
            BigDecimal deployed = tracker.getDeployedCapital(positions);
            assertThat(deployed).isEqualTo(new BigDecimal("10000"));
        }
    }
}
