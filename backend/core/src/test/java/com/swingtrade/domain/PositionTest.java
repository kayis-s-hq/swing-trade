package com.swingtrade.domain;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for the Position domain model.
 */
class PositionTest {

    private static final BigDecimal ENTRY_PRICE = BigDecimal.valueOf(100.00);
    private static final BigDecimal ATR = BigDecimal.valueOf(2.00);
    private static final LocalDate ENTRY_DATE = LocalDate.of(2024, 1, 15);
    private static final Integer QUANTITY = 100;
    private static final String ENTRY_REASON = "Technical breakout pattern detected";

    // Expected values based on createWithRisk formula
    private static final BigDecimal EXPECTED_STOP_LOSS = ENTRY_PRICE.subtract(ATR.multiply(BigDecimal.valueOf(2))); // 100 - 4 = 96
    private static final BigDecimal EXPECTED_RISK = ENTRY_PRICE.subtract(EXPECTED_STOP_LOSS); // 4
    private static final BigDecimal EXPECTED_TARGET = ENTRY_PRICE.add(EXPECTED_RISK.multiply(BigDecimal.valueOf(2.5))); // 100 + 10 = 110

    @Nested
    class ValidPositionCreation {

        @Test
        void shouldCreatePositionWithDefaultConstructor() {
            Position position = new Position(
                1L,
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                EXPECTED_STOP_LOSS,
                EXPECTED_TARGET,
                Position.PositionStatus.OPEN,
                ENTRY_REASON,
                ENTRY_PRICE
            );

            assertThat(position.id()).isEqualTo(1L);
            assertThat(position.symbol()).isEqualTo("RELIANCE");
            assertThat(position.entryPrice()).isEqualTo(ENTRY_PRICE);
            assertThat(position.entryDate()).isEqualTo(ENTRY_DATE);
            assertThat(position.quantity()).isEqualTo(QUANTITY);
            assertThat(position.stopLoss()).isEqualTo(EXPECTED_STOP_LOSS);
            assertThat(position.target()).isEqualTo(EXPECTED_TARGET);
            assertThat(position.status()).isEqualTo(Position.PositionStatus.OPEN);
            assertThat(position.entryReason()).isEqualTo(ENTRY_REASON);
            assertThat(position.currentPrice()).isEqualTo(ENTRY_PRICE);
        }

        @Test
        void shouldCreatePositionWithNullId() {
            Position position = new Position(
                null,
                "TCS",
                BigDecimal.valueOf(1500.00),
                ENTRY_DATE,
                50,
                BigDecimal.valueOf(1450.00),
                BigDecimal.valueOf(1600.00),
                Position.PositionStatus.OPEN,
                "Trend following",
                BigDecimal.valueOf(1500.00)
            );

            assertThat(position.id()).isNull();
            assertThat(position.symbol()).isEqualTo("TCS");
        }
    }

    @Nested
    class CreateWithRisk {

        @Test
        void shouldCalculateStopLossCorrectly() {
            Position position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position.stopLoss()).isEqualTo(EXPECTED_STOP_LOSS);
        }

        @Test
        void shouldCalculateTargetCorrectly() {
            Position position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position.target()).isEqualTo(EXPECTED_TARGET);
        }

        @Test
        void shouldSetStatusToOpen() {
            Position position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position.status()).isEqualTo(Position.PositionStatus.OPEN);
        }

        @Test
        void shouldSetCurrentPriceEqualToEntryPrice() {
            Position position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position.currentPrice()).isEqualTo(ENTRY_PRICE);
        }

        @Test
        void shouldSetNullId() {
            Position position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position.id()).isNull();
        }

        @Test
        void shouldCalculateStopLossForHigherEntryPrice() {
            BigDecimal highEntryPrice = BigDecimal.valueOf(5000.00);
            BigDecimal highAtr = BigDecimal.valueOf(50.00);
            BigDecimal expectedStopLoss = highEntryPrice.subtract(highAtr.multiply(BigDecimal.valueOf(2)));

            Position position = Position.createWithRisk(
                "HDFCBANK",
                highEntryPrice,
                ENTRY_DATE,
                10,
                highAtr,
                "Large cap value"
            );

            assertThat(position.stopLoss()).isEqualTo(expectedStopLoss);
        }

        @Test
        void shouldCalculateTargetForHigherEntryPrice() {
            BigDecimal highEntryPrice = BigDecimal.valueOf(5000.00);
            BigDecimal highAtr = BigDecimal.valueOf(50.00);
            BigDecimal stopLoss = highEntryPrice.subtract(highAtr.multiply(BigDecimal.valueOf(2)));
            BigDecimal risk = highEntryPrice.subtract(stopLoss);
            BigDecimal expectedTarget = highEntryPrice.add(risk.multiply(BigDecimal.valueOf(2.5)));

            Position position = Position.createWithRisk(
                "HDFCBANK",
                highEntryPrice,
                ENTRY_DATE,
                10,
                highAtr,
                "Large cap value"
            );

            assertThat(position.target()).isEqualTo(expectedTarget);
        }
    }

    @Nested
    class CalculateUnrealizedPnL {

        private Position position;

        @Test
        void shouldCalculateProfitWhenCurrentPriceGreaterThanEntryPrice() {
            position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            BigDecimal currentPrice = BigDecimal.valueOf(110.00);
            BigDecimal expectedPnL = currentPrice.subtract(ENTRY_PRICE).multiply(BigDecimal.valueOf(QUANTITY));

            assertThat(position.calculateUnrealizedPnL(currentPrice)).isEqualTo(expectedPnL);
        }

        @Test
        void shouldCalculateLossWhenCurrentPriceLessThanEntryPrice() {
            position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            BigDecimal currentPrice = BigDecimal.valueOf(90.00);
            BigDecimal expectedPnL = currentPrice.subtract(ENTRY_PRICE).multiply(BigDecimal.valueOf(QUANTITY));

            assertThat(position.calculateUnrealizedPnL(currentPrice)).isEqualTo(expectedPnL);
        }

        @Test
        void shouldCalculateZeroPnLWhenCurrentPriceEqualsEntryPrice() {
            position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            BigDecimal currentPrice = ENTRY_PRICE;
            BigDecimal actualPnL = position.calculateUnrealizedPnL(currentPrice);

            assertThat(actualPnL.compareTo(BigDecimal.ZERO)).isEqualTo(0);
        }

        @Test
        void shouldCalculatePnLForDifferentQuantity() {
            position = Position.createWithRisk(
                "TCS",
                BigDecimal.valueOf(1500.00),
                ENTRY_DATE,
                50,
                BigDecimal.valueOf(20.00),
                ENTRY_REASON
            );

            BigDecimal currentPrice = BigDecimal.valueOf(1550.00);
            BigDecimal expectedPnL = currentPrice.subtract(BigDecimal.valueOf(1500.00)).multiply(BigDecimal.valueOf(50));

            assertThat(position.calculateUnrealizedPnL(currentPrice)).isEqualTo(expectedPnL);
        }

        @Test
        void shouldReturnNegativePnLForLargeLoss() {
            position = Position.createWithRisk(
                "INFY",
                BigDecimal.valueOf(1200.00),
                ENTRY_DATE,
                25,
                BigDecimal.valueOf(15.00),
                ENTRY_REASON
            );

            BigDecimal currentPrice = BigDecimal.valueOf(1100.00);
            BigDecimal expectedPnL = currentPrice.subtract(BigDecimal.valueOf(1200.00)).multiply(BigDecimal.valueOf(25));

            assertThat(position.calculateUnrealizedPnL(currentPrice)).isEqualTo(expectedPnL);
            assertThat(position.calculateUnrealizedPnL(currentPrice)).isNegative();
        }
    }

    @Nested
    class CalculatePnLPercent {

        private Position position;

        @Test
        void shouldCalculatePositivePercentChange() {
            position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            BigDecimal currentPrice = BigDecimal.valueOf(110.00);
            BigDecimal expectedPnLPercent = BigDecimal.valueOf(10.0);

            assertThat(position.calculatePnLPercent(currentPrice))
                .isCloseTo(expectedPnLPercent, Percentage.withPercentage(0.01));
        }

        @Test
        void shouldCalculateNegativePercentChange() {
            position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            BigDecimal currentPrice = BigDecimal.valueOf(90.00);
            BigDecimal expectedPnLPercent = BigDecimal.valueOf(-10.0);

            assertThat(position.calculatePnLPercent(currentPrice))
                .isCloseTo(expectedPnLPercent, Percentage.withPercentage(0.01));
        }

        @Test
        void shouldCalculateZeroPercentChange() {
            position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            BigDecimal currentPrice = ENTRY_PRICE;
            BigDecimal actualPnLPercent = position.calculatePnLPercent(currentPrice);

            assertThat(actualPnLPercent.compareTo(BigDecimal.ZERO)).isEqualTo(0);
        }

        @Test
        void shouldCalculatePercentChangeWithDifferentQuantity() {
            position = Position.createWithRisk(
                "TCS",
                BigDecimal.valueOf(1500.00),
                ENTRY_DATE,
                50,
                BigDecimal.valueOf(20.00),
                ENTRY_REASON
            );

            BigDecimal currentPrice = BigDecimal.valueOf(1575.00);
            BigDecimal expectedPnLPercent = BigDecimal.valueOf(5.0);

            assertThat(position.calculatePnLPercent(currentPrice))
                .isCloseTo(expectedPnLPercent, Percentage.withPercentage(0.01));
        }

        @Test
        void shouldHandleSmallPriceChanges() {
            position = Position.createWithRisk(
                "WIPRO",
                BigDecimal.valueOf(450.00),
                ENTRY_DATE,
                100,
                BigDecimal.valueOf(5.00),
                ENTRY_REASON
            );

            BigDecimal currentPrice = BigDecimal.valueOf(452.25);
            BigDecimal expectedPnLPercent = BigDecimal.valueOf(0.5);

            assertThat(position.calculatePnLPercent(currentPrice))
                .isCloseTo(expectedPnLPercent, Percentage.withPercentage(0.01));
        }
    }

    @Nested
    class StatusMethods {

        private Position openPosition;
        private Position closedPosition;
        private Position stoppedPosition;
        private Position targetHitPosition;

        @Test
        void shouldReturnTrueForOpenPositionIsOpen() {
            openPosition = new Position(
                1L,
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                EXPECTED_STOP_LOSS,
                EXPECTED_TARGET,
                Position.PositionStatus.OPEN,
                ENTRY_REASON,
                ENTRY_PRICE
            );

            assertThat(openPosition.isOpen()).isTrue();
            assertThat(openPosition.isClosed()).isFalse();
        }

        @Test
        void shouldReturnFalseForClosedPositionIsOpen() {
            closedPosition = new Position(
                1L,
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                EXPECTED_STOP_LOSS,
                EXPECTED_TARGET,
                Position.PositionStatus.CLOSED,
                ENTRY_REASON,
                EXPECTED_TARGET
            );

            assertThat(closedPosition.isOpen()).isFalse();
            assertThat(closedPosition.isClosed()).isTrue();
        }

        @Test
        void shouldReturnFalseForStoppedPositionIsOpen() {
            stoppedPosition = new Position(
                1L,
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                EXPECTED_STOP_LOSS,
                EXPECTED_TARGET,
                Position.PositionStatus.STOPPED,
                ENTRY_REASON,
                EXPECTED_STOP_LOSS
            );

            assertThat(stoppedPosition.isOpen()).isFalse();
            assertThat(stoppedPosition.isClosed()).isTrue();
        }

        @Test
        void shouldReturnFalseForTargetHitPositionIsOpen() {
            targetHitPosition = new Position(
                1L,
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                EXPECTED_STOP_LOSS,
                EXPECTED_TARGET,
                Position.PositionStatus.TARGET_HIT,
                ENTRY_REASON,
                EXPECTED_TARGET
            );

            assertThat(targetHitPosition.isOpen()).isFalse();
            assertThat(targetHitPosition.isClosed()).isTrue();
        }
    }

    @Nested
    class EqualsAndHashCode {

        private Position position1;
        private Position position2;
        private Position position3;

        @Test
        void shouldReturnTrueWhenComparingSameObject() {
            position1 = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position1).isEqualTo(position1);
            assertThat(position1.hashCode()).isEqualTo(position1.hashCode());
        }

        @Test
        void shouldReturnTrueWhenComparingEqualPositions() {
            position1 = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            position2 = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position1).isEqualTo(position2);
            assertThat(position1.hashCode()).isEqualTo(position2.hashCode());
        }

        @Test
        void shouldReturnFalseWhenSymbolsDiffer() {
            position1 = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            position2 = Position.createWithRisk(
                "TCS",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position1).isNotEqualTo(position2);
            assertThat(position1).isNotEqualTo("RELIANCE");
        }

        @Test
        void shouldReturnFalseWhenEntryPricesDiffer() {
            position1 = Position.createWithRisk(
                "RELIANCE",
                BigDecimal.valueOf(100.00),
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            position2 = Position.createWithRisk(
                "RELIANCE",
                BigDecimal.valueOf(105.00),
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            assertThat(position1).isNotEqualTo(position2);
        }

        @Test
        void shouldReturnFalseWhenQuantitiesDiffer() {
            position1 = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                100,
                ATR,
                ENTRY_REASON
            );

            position2 = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                50,
                ATR,
                ENTRY_REASON
            );

            assertThat(position1).isNotEqualTo(position2);
        }

        @Test
        void shouldReturnTrueWhenBothNull() {
            position1 = new Position(null, "RELIANCE", ENTRY_PRICE, ENTRY_DATE, QUANTITY,
                EXPECTED_STOP_LOSS, EXPECTED_TARGET, Position.PositionStatus.OPEN, ENTRY_REASON, ENTRY_PRICE);
            position2 = new Position(null, "RELIANCE", ENTRY_PRICE, ENTRY_DATE, QUANTITY,
                EXPECTED_STOP_LOSS, EXPECTED_TARGET, Position.PositionStatus.OPEN, ENTRY_REASON, ENTRY_PRICE);

            assertThat(position1).isEqualTo(position2);
        }
    }

    @Nested
    class ToString {

        @Test
        void shouldIncludeAllFieldsInToString() {
            Position position = Position.createWithRisk(
                "RELIANCE",
                ENTRY_PRICE,
                ENTRY_DATE,
                QUANTITY,
                ATR,
                ENTRY_REASON
            );

            String toString = position.toString();

            assertThat(toString).contains("RELIANCE");
            assertThat(toString).contains("100.0");
            assertThat(toString).contains(ENTRY_DATE.toString());
            assertThat(toString).contains(QUANTITY.toString());
            assertThat(toString).contains("96.0");
            assertThat(toString).contains("110.0");
            assertThat(toString).contains(Position.PositionStatus.OPEN.toString());
            assertThat(toString).contains(ENTRY_REASON);
        }

        @Test
        void shouldHandleNullIdInToString() {
            Position position = Position.createWithRisk(
                "TCS",
                BigDecimal.valueOf(1500.00),
                ENTRY_DATE,
                50,
                BigDecimal.valueOf(20.00),
                "Trend following"
            );

            String toString = position.toString();

            assertThat(toString).contains("TCS");
            assertThat(toString).contains("1500.0");
        }
    }
}
