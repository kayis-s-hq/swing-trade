package com.swingtrade.broker.manager;

import com.swingtrade.broker.config.PaperTradingProperties;
import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.PriceBand;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PositionManager covering position creation, SL/TP calculation,
 * price updates, candle triggers, partial exits, full close, P&L, and limits.
 */
@ExtendWith(MockitoExtension.class)
class PositionManagerTest {

    private PositionManager positionManager;

    @Mock
    private PaperTradingProperties paperTradingProperties;

    private PaperTradingProperties testProperties;

    @BeforeEach
    void setUp() {
        testProperties = new PaperTradingProperties();
        testProperties.setMaxConcurrentPositions(5);
        positionManager = new PositionManager(testProperties);
    }

    // Helper: build a 24-arg Position for testing
    private Position makePosition(
            Long id, String brokerType, String symbol, BigDecimal entryPrice, LocalDate entryDate,
            Integer quantity, BigDecimal stopLoss, BigDecimal target, PositionStatus status,
            String entryReason, BigDecimal currentPrice, String positionId, String brokerPositionId,
            Exchange exchange, TradeDirection direction, BigDecimal averagePrice,
            BigDecimal unrealizedPnL, BigDecimal realizedPnL, BigDecimal marginUtilized,
            java.time.LocalDateTime entryTime, java.time.LocalDateTime exitTime,
            String exitReason, List<Order> orders) {
        return new Position(id, brokerType, symbol, entryPrice, entryDate, quantity,
                stopLoss, target, status, entryReason, currentPrice,
                positionId, brokerPositionId, exchange, direction, averagePrice,
                unrealizedPnL, realizedPnL, marginUtilized,
                entryTime, exitTime, exitReason, orders);
    }

    // ==================== Position Creation ====================

    @Nested
    class PositionCreation {

        @Test
        void createPosition_long() {
            // Given: A long position request
            String positionId = "POS_00000001";
            String symbol = "RELIANCE-EQ";
            TradeDirection direction = TradeDirection.LONG;
            int quantity = 10;
            BigDecimal entryPrice = new BigDecimal("2500.00");
            BigDecimal atr = new BigDecimal("50.00");
            String entryReason = "Golden cross on daily";

            // When
            Position position = positionManager.createPosition(positionId, symbol, direction, quantity, entryPrice, atr, entryReason);

            // Then
            assertThat(position).isNotNull();
            assertThat(position.symbol()).isEqualTo(symbol);
            assertThat(position.direction()).isEqualTo(direction);
            assertThat(position.quantity()).isEqualTo(quantity);
            assertThat(position.entryPrice()).isEqualTo(entryPrice);
            assertThat(position.status()).isEqualTo(PositionStatus.OPEN);
            assertThat(position.brokerType()).isEqualTo("PAPER");
            assertThat(position.positionId()).isEqualTo(positionId);
            assertThat(position.unrealizedPnL()).isEqualTo(BigDecimal.ZERO);
            assertThat(position.realizedPnL()).isEqualTo(BigDecimal.ZERO);
            assertThat(position.entryTime()).isNotNull();
        }

        @Test
        void createPosition_short() {
            // Given: A short position request
            String positionId = "POS_00000002";
            String symbol = "TCS-EQ";
            TradeDirection direction = TradeDirection.SHORT;
            int quantity = 5;
            BigDecimal entryPrice = new BigDecimal("3500.00");
            BigDecimal atr = new BigDecimal("60.00");

            // When
            Position position = positionManager.createPosition(positionId, symbol, direction, quantity, entryPrice, atr, "Bearish breakout");

            // Then
            assertThat(position).isNotNull();
            assertThat(position.direction()).isEqualTo(direction);
            assertThat(position.quantity()).isEqualTo(quantity);
        }

        @Test
        void createPosition_slAndTpAutoCalculated() {
            // Given: A long position with ATR
            String positionId = "POS_00000003";
            BigDecimal entryPrice = new BigDecimal("100.00");
            BigDecimal atr = new BigDecimal("5.00");

            // When
            Position position = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, entryPrice, atr, "Test");

            // Then
            // SL = entry - 2*ATR = 100 - 10 = 90
            assertThat(position.stopLoss()).isEqualByComparingTo(new BigDecimal("90.00"));
            // Risk = 10, Target = entry + 2.5*risk = 100 + 25 = 125
            assertThat(position.target()).isEqualByComparingTo(new BigDecimal("125.00"));
        }

        @Test
        void createPosition_shortSlAndTpAutoCalculated() {
            // Given: A short position with ATR
            String positionId = "POS_00000004";
            BigDecimal entryPrice = new BigDecimal("100.00");
            BigDecimal atr = new BigDecimal("5.00");

            // When
            Position position = positionManager.createPosition(positionId, "TCS-EQ", TradeDirection.SHORT, 10, entryPrice, atr, "Test");

            // Then
            // SL = entry + 2*ATR = 100 + 10 = 110
            assertThat(position.stopLoss()).isEqualByComparingTo(new BigDecimal("110.00"));
            // Risk = 10, Target = entry - 2.5*risk = 100 - 25 = 75
            assertThat(position.target()).isEqualByComparingTo(new BigDecimal("75.00"));
        }
    }

    // ==================== SL/TP Calculation ====================

    @Nested
    class SlTpCalc {

        @Test
        void calculateStopLoss_longWithAtr() {
            // Given: A long position with ATR
            BigDecimal entryPrice = new BigDecimal("200.00");
            BigDecimal atr = new BigDecimal("8.00");

            // When
            BigDecimal sl = positionManager.calculateStopLoss(entryPrice, TradeDirection.LONG, atr);

            // Then
            assertThat(sl).isEqualByComparingTo(new BigDecimal("184.00"));
        }

        @Test
        void calculateStopLoss_shortWithAtr() {
            // Given: A short position with ATR
            BigDecimal entryPrice = new BigDecimal("200.00");
            BigDecimal atr = new BigDecimal("8.00");

            // When
            BigDecimal sl = positionManager.calculateStopLoss(entryPrice, TradeDirection.SHORT, atr);

            // Then
            assertThat(sl).isEqualByComparingTo(new BigDecimal("216.00"));
        }

        @Test
        void calculateStopLoss_nullAtr_fallback() {
            // Given: A long position with null ATR
            BigDecimal entryPrice = new BigDecimal("100.00");

            // When
            BigDecimal sl = positionManager.calculateStopLoss(entryPrice, TradeDirection.LONG, null);

            // Then
            assertThat(sl).isEqualByComparingTo(new BigDecimal("97.00"));
        }

        @Test
        void calculateStopLoss_zeroAtr_fallback() {
            // Given: A long position with zero ATR
            BigDecimal entryPrice = new BigDecimal("100.00");

            // When
            BigDecimal sl = positionManager.calculateStopLoss(entryPrice, TradeDirection.LONG, BigDecimal.ZERO);

            // Then
            assertThat(sl).isEqualByComparingTo(new BigDecimal("97.00"));
        }

        @Test
        void calculateStopLoss_negativeAtr_fallback() {
            // Given: A long position with negative ATR
            BigDecimal entryPrice = new BigDecimal("100.00");

            // When
            BigDecimal sl = positionManager.calculateStopLoss(entryPrice, TradeDirection.LONG, new BigDecimal("-5.00"));

            // Then
            assertThat(sl).isEqualByComparingTo(new BigDecimal("97.00"));
        }

        @Test
        void calculateTarget_long() {
            // Given: Entry 100, SL 90 (risk = 10)
            BigDecimal entryPrice = new BigDecimal("100.00");
            BigDecimal stopLoss = new BigDecimal("90.00");

            // When
            BigDecimal target = positionManager.calculateTarget(entryPrice, stopLoss, TradeDirection.LONG);

            // Then
            // Target = entry + 2.5 * risk = 100 + 25 = 125
            assertThat(target).isEqualByComparingTo(new BigDecimal("125.00"));
        }

        @Test
        void calculateTarget_short() {
            // Given: Entry 100, SL 110 (risk = 10)
            BigDecimal entryPrice = new BigDecimal("100.00");
            BigDecimal stopLoss = new BigDecimal("110.00");

            // When
            BigDecimal target = positionManager.calculateTarget(entryPrice, stopLoss, TradeDirection.SHORT);

            // Then
            // Target = entry - 2.5 * risk = 100 - 25 = 75
            assertThat(target).isEqualByComparingTo(new BigDecimal("75.00"));
        }

        @Test
        void calculateTarget_2_5_ratio() {
            // Given: Entry 500, SL 480 (risk = 20)
            BigDecimal entryPrice = new BigDecimal("500.00");
            BigDecimal stopLoss = new BigDecimal("480.00");

            // When
            BigDecimal target = positionManager.calculateTarget(entryPrice, stopLoss, TradeDirection.LONG);

            // Then
            // Target = 500 + 2.5 * 20 = 500 + 50 = 550
            assertThat(target).isEqualByComparingTo(new BigDecimal("550.00"));
        }
    }

    // ==================== Price Update ====================

    @Nested
    class PriceUpdate {

        @Test
        void updatePositionPrice_long_profit() {
            // Given: A long position with entry at 100
            String positionId = "POS_00000001";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When
            Position updated = positionManager.updatePositionPrice(positionId, new BigDecimal("110.00"));

            // Then
            assertThat(updated.unrealizedPnL()).isEqualTo(new BigDecimal("100.00")); // (110-100)*10
            assertThat(updated.currentPrice()).isEqualTo(new BigDecimal("110.00"));
        }

        @Test
        void updatePositionPrice_long_loss() {
            // Given: A long position with entry at 100
            String positionId = "POS_00000002";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When
            Position updated = positionManager.updatePositionPrice(positionId, new BigDecimal("90.00"));

            // Then
            assertThat(updated.unrealizedPnL()).isEqualTo(new BigDecimal("-100.00")); // (90-100)*10
        }

        @Test
        void updatePositionPrice_short_profit() {
            // Given: A short position with entry at 100
            String positionId = "POS_00000003";
            positionManager.createPosition(positionId, "TCS-EQ", TradeDirection.SHORT, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When
            Position updated = positionManager.updatePositionPrice(positionId, new BigDecimal("90.00"));

            // Then
            assertThat(updated.unrealizedPnL()).isEqualTo(new BigDecimal("100.00")); // (100-90)*10
        }

        @Test
        void updatePositionPrice_short_loss() {
            // Given: A short position with entry at 100
            String positionId = "POS_00000004";
            positionManager.createPosition(positionId, "TCS-EQ", TradeDirection.SHORT, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When
            Position updated = positionManager.updatePositionPrice(positionId, new BigDecimal("110.00"));

            // Then
            assertThat(updated.unrealizedPnL()).isEqualTo(new BigDecimal("-100.00")); // (100-110)*10
        }

        @Test
        void updatePositionPrice_notFound_throwsException() {
            // Given: A non-existent position ID
            String nonExistentId = "POS_NONEXIST";

            // When / Then
            assertThatThrownBy(() -> positionManager.updatePositionPrice(nonExistentId, new BigDecimal("100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Position not found: " + nonExistentId);
        }

        @Test
        void updatePositionPrice_preservesFields() {
            // Given: A position
            String positionId = "POS_00000005";
            Position original = positionManager.createPosition(positionId, "INFY-EQ", TradeDirection.LONG, 20, new BigDecimal("150.00"), new BigDecimal("3.00"), "Test");

            // When
            Position updated = positionManager.updatePositionPrice(positionId, new BigDecimal("155.00"));

            // Then
            assertThat(updated.symbol()).isEqualTo(original.symbol());
            assertThat(updated.entryPrice()).isEqualTo(original.entryPrice());
            assertThat(updated.quantity()).isEqualTo(original.quantity());
            assertThat(updated.stopLoss()).isEqualTo(original.stopLoss());
            assertThat(updated.target()).isEqualTo(original.target());
            assertThat(updated.direction()).isEqualTo(original.direction());
            assertThat(updated.status()).isEqualTo(original.status());
        }
    }

    // ==================== Candle Updates ====================

    @Nested
    class CandleUpdates {

        @Test
        void updatePositionsWithCandleData_oneMatch() {
            // Given: A position for RELIANCE-EQ
            String positionId = "POS_00000001";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("98"),
                    new BigDecimal("103"), 100000L, new BigDecimal("103"));

            // When
            List<Position> updated = positionManager.updatePositionsWithCandleData("RELIANCE-EQ", candle);

            // Then
            assertThat(updated).hasSize(1);
            assertThat(updated.get(0).currentPrice()).isEqualByComparingTo(new BigDecimal("103.00"));
        }

        @Test
        void updatePositionsWithCandleData_noMatch() {
            // Given: A position for RELIANCE-EQ
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            OhlcvCandle candle = new OhlcvCandle("TCS-EQ", LocalDate.now(),
                    new BigDecimal("3500"), new BigDecimal("3550"), new BigDecimal("3480"),
                    new BigDecimal("3520"), 50000L, new BigDecimal("3520"));

            // When
            List<Position> updated = positionManager.updatePositionsWithCandleData("TCS-EQ", candle);

            // Then
            assertThat(updated).isEmpty();
        }

        @Test
        void updatePositionsWithCandleData_multipleSymbols() {
            // Given: Positions for two symbols
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.createPosition("POS_00000002", "TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("200.00"), new BigDecimal("8.00"), "Test");
            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("98"),
                    new BigDecimal("103"), 100000L, new BigDecimal("103"));

            // When
            List<Position> updated = positionManager.updatePositionsWithCandleData("RELIANCE-EQ", candle);

            // Then
            assertThat(updated).hasSize(1);
            assertThat(updated.get(0).symbol()).isEqualTo("RELIANCE-EQ");
        }

        @Test
        void updatePositionsWithCandleData_closedPositionIgnored() {
            // Given: A closed position
            String positionId = "POS_00000001";
            Position pos = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            // Manually close it
            Position closed = new Position(
                    pos.id(), pos.brokerType(), pos.symbol(), pos.entryPrice(), pos.entryDate(),
                    pos.quantity(), pos.stopLoss(), pos.target(), PositionStatus.CLOSED,
                    pos.entryReason(), pos.currentPrice(), pos.positionId(), pos.brokerPositionId(),
                    pos.exchange(), pos.direction(), pos.averagePrice(), pos.unrealizedPnL(),
                    pos.realizedPnL(), pos.marginUtilized(), pos.entryTime(), pos.exitTime(),
                    pos.exitReason(), pos.orders());
            positionManager.getPositions().put(positionId, closed);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("98"),
                    new BigDecimal("103"), 100000L, new BigDecimal("103"));

            // When
            List<Position> updated = positionManager.updatePositionsWithCandleData("RELIANCE-EQ", candle);

            // Then
            assertThat(updated).isEmpty();
        }

        @Test
        void updatePositionsWithCandleData_stopLossHit_returnsClosedStatus() {
            // Given: A long position, entry 100, ATR 5 -> stopLoss 90, target 125
            String positionId = "POS_00000001";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10,
                    new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // Candle low (85) breaches the stop loss (90)
            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("92"), new BigDecimal("95"), new BigDecimal("85"),
                    new BigDecimal("88"), 100000L, new BigDecimal("88"));

            // When
            List<Position> updated = positionManager.updatePositionsWithCandleData("RELIANCE-EQ", candle);

            // Then: the caller must see the final (closed) status, not the pre-trigger
            // OPEN snapshot - PaperTradingEngine.updatePositionsFromDomain branches on
            // this to decide whether to persist a close and credit portfolio cash.
            assertThat(updated).hasSize(1);
            assertThat(updated.get(0).status()).isEqualTo(PositionStatus.STOPPED);
            assertThat(updated.get(0).realizedPnL()).isEqualByComparingTo(new BigDecimal("-100.00"));
        }

        @Test
        void updatePositionsWithCandleData_lowerCircuitDefersExit() {
            String positionId = "POS_00000001";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10,
                new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                new BigDecimal("92"), new BigDecimal("95"), new BigDecimal("85"),
                new BigDecimal("88"), 100000L, new BigDecimal("88"));
            PriceBand band = new PriceBand("RELIANCE-EQ", candle.date(),
                new BigDecimal("88"), new BigDecimal("110"));

            List<Position> updated = positionManager.updatePositionsWithCandleData(
                "RELIANCE-EQ", candle, band);

            assertThat(updated).hasSize(1);
            assertThat(updated.get(0).status()).isEqualTo(PositionStatus.OPEN);
            assertThat(updated.get(0).realizedPnL()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ==================== SL/TP Triggers ====================

    @Nested
    class SlTpTriggers {

        @Test
        void longPosition_slTriggered() {
            // Given: A long position with entry 100, SL 90
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // Store in manager map so closePosition can look it up by positionId
            positionManager.getPositions().put("POS_00000001", position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("92"), new BigDecimal("95"), new BigDecimal("88"),
                    new BigDecimal("91"), 100000L, new BigDecimal("91"));

            // When
            positionManager.checkPositionTriggers(position, candle);

            // Then
            // The manager stores the updated position returned by closePosition
            Position stored = positionManager.getPosition("POS_00000001");
            assertThat(stored.status()).isEqualTo(PositionStatus.STOPPED);
            // Realized P&L must be booked at the stop-loss level (90), not the
            // candle's low (88) or the stale currentPrice (100).
            assertThat(stored.realizedPnL()).isEqualByComparingTo(new BigDecimal("-100.00"));
        }

        @Test
        void longPosition_stopGapUsesOpeningPrice() {
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"), PositionStatus.OPEN, "Test",
                    new BigDecimal("100.00"), "POS_00000001", null, Exchange.NSE, TradeDirection.LONG,
                    new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);
            positionManager.getPositions().put(position.positionId(), position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("80"), new BigDecimal("85"), new BigDecimal("70"),
                    new BigDecimal("75"), 100000L, new BigDecimal("75"));

            positionManager.checkPositionTriggers(position, candle);

            assertThat(positionManager.getPosition(position.positionId()).realizedPnL())
                .isEqualByComparingTo(new BigDecimal("-200.00"));
        }

        @Test
        void longPosition_tpTriggered() {
            // Given: A long position with entry 100, target 125
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // Store in manager map
            positionManager.getPositions().put("POS_00000001", position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("120"), new BigDecimal("128"), new BigDecimal("121"),
                    new BigDecimal("126"), 100000L, new BigDecimal("126"));

            // When
            positionManager.checkPositionTriggers(position, candle);

            // Then
            Position stored = positionManager.getPosition("POS_00000001");
            assertThat(stored.status()).isEqualTo(PositionStatus.TARGET_HIT);
        }

        @Test
        void longPosition_targetGapUsesOpeningPrice() {
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"), PositionStatus.OPEN, "Test",
                    new BigDecimal("100.00"), "POS_00000001", null, Exchange.NSE, TradeDirection.LONG,
                    new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);
            positionManager.getPositions().put(position.positionId(), position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("130"), new BigDecimal("135"), new BigDecimal("129"),
                    new BigDecimal("132"), 100000L, new BigDecimal("132"));

            positionManager.checkPositionTriggers(position, candle);

            assertThat(positionManager.getPosition(position.positionId()).realizedPnL())
                .isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        void shortPosition_slTriggered() {
            // Given: A short position with entry 100, SL 110
            Position position = makePosition(1L, "PAPER", "TCS-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("110.00"), new BigDecimal("75.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.SHORT, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // Store in manager map
            positionManager.getPositions().put("POS_00000001", position);

            OhlcvCandle candle = new OhlcvCandle("TCS-EQ", LocalDate.now(),
                    new BigDecimal("108"), new BigDecimal("112"), new BigDecimal("109"),
                    new BigDecimal("111"), 50000L, new BigDecimal("111"));

            // When
            positionManager.checkPositionTriggers(position, candle);

            // Then
            Position stored = positionManager.getPosition("POS_00000001");
            assertThat(stored.status()).isEqualTo(PositionStatus.STOPPED);
        }

        @Test
        void shortPosition_tpTriggered() {
            // Given: A short position with entry 100, target 75
            Position position = makePosition(1L, "PAPER", "TCS-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("110.00"), new BigDecimal("75.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.SHORT, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // Store in manager map
            positionManager.getPositions().put("POS_00000001", position);

            OhlcvCandle candle = new OhlcvCandle("TCS-EQ", LocalDate.now(),
                    new BigDecimal("70"), new BigDecimal("78"), new BigDecimal("72"),
                    new BigDecimal("74"), 50000L, new BigDecimal("74"));

            // When
            positionManager.checkPositionTriggers(position, candle);

            // Then
            Position stored = positionManager.getPosition("POS_00000001");
            assertThat(stored.status()).isEqualTo(PositionStatus.TARGET_HIT);
        }

        @Test
        void noTrigger() {
            // Given: A long position with entry 100, SL 90, target 125
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // Store in manager map
            positionManager.getPositions().put("POS_00000001", position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("98"), new BigDecimal("103"), new BigDecimal("97"),
                    new BigDecimal("101"), 100000L, new BigDecimal("101"));

            // When
            positionManager.checkPositionTriggers(position, candle);

            // Then
            Position stored = positionManager.getPosition("POS_00000001");
            assertThat(stored.status()).isEqualTo(PositionStatus.OPEN);
        }

        @Test
        void slPriorityOverTp() {
            // Given: A long position with entry 100, SL 90, target 125
            // Candle low hits SL before high hits TP
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // Store in manager map
            positionManager.getPositions().put("POS_00000001", position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("88"), new BigDecimal("130"), new BigDecimal("85"),
                    new BigDecimal("89"), 200000L, new BigDecimal("89"));

            // When
            positionManager.checkPositionTriggers(position, candle);

            // Then
            // SL triggers first (low 85 <= SL 90), so status is STOPPED, not TARGET_HIT
            Position stored = positionManager.getPosition("POS_00000001");
            assertThat(stored.status()).isEqualTo(PositionStatus.STOPPED);
        }

        @Test
        void closedPosition_noTrigger() {
            // Given: A closed position
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.CLOSED, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // Store in manager map
            positionManager.getPositions().put("POS_00000001", position);

            OhlcvCandle candle = new OhlcvCandle("RELIANCE-EQ", LocalDate.now(),
                    new BigDecimal("80"), new BigDecimal("85"), new BigDecimal("82"),
                    new BigDecimal("83"), 100000L, new BigDecimal("83"));

            // When
            positionManager.checkPositionTriggers(position, candle);

            // Then
            Position stored = positionManager.getPosition("POS_00000001");
            assertThat(stored.status()).isEqualTo(PositionStatus.CLOSED);
        }
    }

    // ==================== Partial Exit ====================

    @Nested
    class PartialExit {

        @Test
        void partialExitLongProfit() {
            // Given: A long position with entry 100
            String positionId = "POS_00000001";
            Position original = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            // Update price to 110
            positionManager.updatePositionPrice(positionId, new BigDecimal("110.00"));

            // When: Exit 50% at 110
            Position result = positionManager.partialExitPosition(positionId, new BigDecimal("0.5"), new BigDecimal("110.00"));

            // Then
            assertThat(result.quantity()).isEqualTo(5);
            // Realized P&L = (110-100)*5 = 50
            assertThat(result.realizedPnL()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        void partialExitShortLoss() {
            // Given: A short position with entry 100
            String positionId = "POS_00000002";
            positionManager.createPosition(positionId, "TCS-EQ", TradeDirection.SHORT, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            // Update price to 110
            positionManager.updatePositionPrice(positionId, new BigDecimal("110.00"));

            // When: Exit 50% at 110
            Position result = positionManager.partialExitPosition(positionId, new BigDecimal("0.5"), new BigDecimal("110.00"));

            // Then
            assertThat(result.quantity()).isEqualTo(5);
            // Realized P&L = (100-110)*5 = -50
            assertThat(result.realizedPnL()).isEqualByComparingTo(new BigDecimal("-50.00"));
        }

        @Test
        void partialExit_fullExit() {
            // Given: A long position with quantity 1
            String positionId = "POS_00000003";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 1, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When: Exit all of the one-share position
            Position result = positionManager.partialExitPosition(positionId, BigDecimal.ONE, new BigDecimal("105.00"));

            // Then
            assertThat(result.status()).isEqualTo(PositionStatus.CLOSED);
            assertThat(result.quantity()).isZero();
            assertThat(result.exitTime()).isNotNull();
            assertThat(result.exitReason()).contains("fully exited");
        }

        @Test
        void partialExit_invalidRatio_zero() {
            // Given: A valid position
            String positionId = "POS_00000004";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When / Then
            assertThatThrownBy(() -> positionManager.partialExitPosition(positionId, BigDecimal.ZERO, new BigDecimal("105.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Exit ratio must be between 0 and 1");
        }

        @Test
        void partialExit_invalidRatio_overOne() {
            // Given: A valid position
            String positionId = "POS_00000005";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            assertThatThrownBy(() -> positionManager.partialExitPosition(positionId, new BigDecimal("3.0"), new BigDecimal("105.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Exit ratio must be between 0 and 1");
        }

        @Test
        void partialExit_notFound_throwsException() {
            // When / Then
            assertThatThrownBy(() -> positionManager.partialExitPosition("POS_NONEXIST", new BigDecimal("0.5"), new BigDecimal("105.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Position not found: POS_NONEXIST");
        }

        @Test
        void partialExit_alreadyClosed_throwsException() {
            // Given: A closed position
            String positionId = "POS_00000006";
            Position pos = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.closePosition(pos, PositionStatus.CLOSED, "Manual close");

            // When / Then
            assertThatThrownBy(() -> positionManager.partialExitPosition(positionId, new BigDecimal("0.5"), new BigDecimal("105.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Position is not open");
        }
    }

    // ==================== Full Close ====================

    @Nested
    class FullClose {

        @Test
        void closePosition_manual() {
            // Given: An open position
            String positionId = "POS_00000001";
            Position original = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.updatePositionPrice(positionId, new BigDecimal("105.00"));

            // When
            Position result = positionManager.closePosition(positionId, new BigDecimal("105.00"), "Manual exit");

            // Then
            assertThat(result.status()).isEqualTo(PositionStatus.CLOSED);
            assertThat(result.exitReason()).isEqualTo("Manual exit");
            assertThat(result.exitTime()).isNotNull();
            // Realized P&L = (105-100)*10 = 50
            assertThat(result.realizedPnL()).isEqualTo(new BigDecimal("50.00"));
        }

        @Test
        void closePosition_stopped() {
            // Given: An open position
            String positionId = "POS_00000002";
            Position original = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When
            Position result = positionManager.closePosition(positionId, PositionStatus.STOPPED, "Stop loss hit");

            // Then
            assertThat(result.status()).isEqualTo(PositionStatus.STOPPED);
            assertThat(result.exitReason()).isEqualTo("Stop loss hit");
        }

        @Test
        void closePosition_targetHit() {
            // Given: An open position
            String positionId = "POS_00000003";
            Position original = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When
            Position result = positionManager.closePosition(positionId, PositionStatus.TARGET_HIT, "Target reached");

            // Then
            assertThat(result.status()).isEqualTo(PositionStatus.TARGET_HIT);
            assertThat(result.exitReason()).isEqualTo("Target reached");
        }

        @Test
        void closePosition_notFound_throwsException() {
            // When / Then
            assertThatThrownBy(() -> positionManager.closePosition("POS_NONEXIST", new BigDecimal("100"), "Manual"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Position not found: POS_NONEXIST");
        }

        @Test
        void closePosition_idempotent() {
            // Given: A position that's already closed
            String positionId = "POS_00000004";
            Position pos = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            Position firstClose = positionManager.closePosition(pos, PositionStatus.CLOSED, "First close");

            // When
            Position secondClose = positionManager.closePosition(positionId, PositionStatus.CLOSED, "Second close");

            // Then
            assertThat(secondClose.status()).isEqualTo(PositionStatus.CLOSED);
            assertThat(secondClose).isSameAs(firstClose);
        }

        @Test
        void closePosition_short_profit() {
            // Given: A short position
            String positionId = "POS_00000005";
            Position original = positionManager.createPosition(positionId, "TCS-EQ", TradeDirection.SHORT, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.updatePositionPrice(positionId, new BigDecimal("90.00"));

            // When
            Position result = positionManager.closePosition(positionId, new BigDecimal("90.00"), "Manual exit");

            // Then
            assertThat(result.status()).isEqualTo(PositionStatus.CLOSED);
            // Realized P&L = (100-90)*10 = 100
            assertThat(result.realizedPnL()).isEqualTo(new BigDecimal("100.00"));
        }

        @Test
        void closePosition_short_loss() {
            // Given: A short position
            String positionId = "POS_00000006";
            Position original = positionManager.createPosition(positionId, "TCS-EQ", TradeDirection.SHORT, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.updatePositionPrice(positionId, new BigDecimal("110.00"));

            // When
            Position result = positionManager.closePosition(positionId, new BigDecimal("110.00"), "Manual exit");

            // Then
            assertThat(result.status()).isEqualTo(PositionStatus.CLOSED);
            // Realized P&L = (100-110)*10 = -100
            assertThat(result.realizedPnL()).isEqualTo(new BigDecimal("-100.00"));
        }
    }

    // ==================== P&L Calculation ====================

    @Nested
    class PnLCalc {

        @Test
        void calculatePositionPnL_long_profit() {
            // Given: A long position
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("110.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // When
            BigDecimal pnl = positionManager.calculatePositionPnL(position, new BigDecimal("110.00"));

            // Then
            assertThat(pnl).isEqualTo(new BigDecimal("100.00"));
        }

        @Test
        void calculatePositionPnL_long_loss() {
            // Given: A long position
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("90.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // When
            BigDecimal pnl = positionManager.calculatePositionPnL(position, new BigDecimal("90.00"));

            // Then
            assertThat(pnl).isEqualTo(new BigDecimal("-100.00"));
        }

        @Test
        void calculatePositionPnL_short_profit() {
            // Given: A short position
            Position position = makePosition(1L, "PAPER", "TCS-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("110.00"), new BigDecimal("75.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("90.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.SHORT, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // When
            BigDecimal pnl = positionManager.calculatePositionPnL(position, new BigDecimal("90.00"));

            // Then
            assertThat(pnl).isEqualTo(new BigDecimal("100.00"));
        }

        @Test
        void calculatePositionPnL_short_loss() {
            // Given: A short position
            Position position = makePosition(1L, "PAPER", "TCS-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("110.00"), new BigDecimal("75.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("110.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.SHORT, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // When
            BigDecimal pnl = positionManager.calculatePositionPnL(position, new BigDecimal("110.00"));

            // Then
            assertThat(pnl).isEqualTo(new BigDecimal("-100.00"));
        }

        @Test
        void calculatePositionPnL_nullEntryPrice_returnsZero() {
            // Given: A position with null entry price
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    null, LocalDate.now(), 10,
                    null, null,
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // When
            BigDecimal pnl = positionManager.calculatePositionPnL(position, new BigDecimal("100.00"));

            // Then
            assertThat(pnl).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void calculatePnLPercentage_long_profit() {
            // Given: A long position with entry 100, current 110
            Position position = makePosition(1L, "PAPER", "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("110.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.LONG, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // When
            BigDecimal pct = positionManager.calculatePnLPercentage(position, new BigDecimal("110.00"));

            // Then
            assertThat(pct).isEqualTo(new BigDecimal("10.0000"));
        }

        @Test
        void calculatePnLPercentage_short_loss() {
            // Given: A short position with entry 100, current 120
            Position position = makePosition(1L, "PAPER", "TCS-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("110.00"), new BigDecimal("75.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("120.00"),
                    "POS_00000001", null, Exchange.NSE,
                    TradeDirection.SHORT, new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    java.time.LocalDateTime.now(), null, null, null);

            // When
            BigDecimal pct = positionManager.calculatePnLPercentage(position, new BigDecimal("120.00"));

            // Then
            assertThat(pct).isEqualTo(new BigDecimal("-20.0000"));
        }

        @Test
        void getTotalUnrealizedPnL() {
            // Given: Multiple open positions
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.updatePositionPrice("POS_00000001", new BigDecimal("110.00")); // +100
            positionManager.createPosition("POS_00000002", "TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("200.00"), new BigDecimal("8.00"), "Test");
            positionManager.updatePositionPrice("POS_00000002", new BigDecimal("190.00")); // +50

            // When
            BigDecimal total = positionManager.getTotalUnrealizedPnL();

            // Then
            assertThat(total).isEqualTo(new BigDecimal("150.00"));
        }

        @Test
        void getTotalUnrealizedPnL_negative() {
            // Given: A losing position
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.updatePositionPrice("POS_00000001", new BigDecimal("90.00")); // -100

            // When
            BigDecimal total = positionManager.getTotalUnrealizedPnL();

            // Then
            assertThat(total).isEqualTo(new BigDecimal("-100.00"));
        }

        @Test
        void getTotalUnrealizedPnL_empty() {
            // When
            BigDecimal total = positionManager.getTotalUnrealizedPnL();

            // Then
            assertThat(total).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void getTotalRealizedPnL() {
            // Given: A closed position with realized P&L
            String positionId = "POS_00000001";
            Position pos = positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.updatePositionPrice(positionId, new BigDecimal("110.00"));
            positionManager.closePosition(positionId, new BigDecimal("110.00"), "Manual");

            // When
            BigDecimal total = positionManager.getTotalRealizedPnL();

            // Then
            assertThat(total).isEqualTo(new BigDecimal("100.00"));
        }

        @Test
        void getTotalRealizedPnL_empty() {
            // When
            BigDecimal total = positionManager.getTotalRealizedPnL();

            // Then
            assertThat(total).isEqualTo(BigDecimal.ZERO);
        }
    }

    // ==================== Position Limits ====================

    @Nested
    class PositionLimits {

        @Test
        void withinLimit() {
            // Given: 3 open positions, max is 5
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.createPosition("POS_00000002", "TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("200.00"), new BigDecimal("8.00"), "Test");
            positionManager.createPosition("POS_00000003", "INFY-EQ", TradeDirection.LONG, 20, new BigDecimal("150.00"), new BigDecimal("3.00"), "Test");

            // When / Then
            assertThat(positionManager.hasReachedPositionLimit()).isFalse();
            assertThat(positionManager.getOpenPositionCount()).isEqualTo(3);
        }

        @Test
        void atMax() {
            // Given: 5 open positions, max is 5
            for (int i = 1; i <= 5; i++) {
                positionManager.createPosition("POS_0000000" + i, "STOCK" + i + "-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            }

            // When / Then
            assertThat(positionManager.hasReachedPositionLimit()).isTrue();
            assertThat(positionManager.getOpenPositionCount()).isEqualTo(5);
        }

        @Test
        void queries_openPositions() {
            // Given: Mix of open and closed positions
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.createPosition("POS_00000002", "TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("200.00"), new BigDecimal("8.00"), "Test");
            Position closed = positionManager.createPosition("POS_00000003", "INFY-EQ", TradeDirection.LONG, 20, new BigDecimal("150.00"), new BigDecimal("3.00"), "Test");
            positionManager.closePosition(closed, PositionStatus.CLOSED, "Manual");

            // When
            List<Position> open = positionManager.getOpenPositions();
            List<Position> closedList = positionManager.getClosedPositions();
            List<Position> all = positionManager.getAllPositions();

            // Then
            assertThat(open).hasSize(2);
            assertThat(closedList).hasSize(1);
            assertThat(all).hasSize(3);
        }

        @Test
        void clearAll() {
            // Given: Multiple positions
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.createPosition("POS_00000002", "TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("200.00"), new BigDecimal("8.00"), "Test");

            // When
            positionManager.clearAllPositions();

            // Then
            assertThat(positionManager.getOpenPositionCount()).isZero();
            assertThat(positionManager.getAllPositions()).isEmpty();
        }

        @Test
        void findOpenPositionBySymbol() {
            // Given: A position for RELIANCE-EQ
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.createPosition("POS_00000002", "TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("200.00"), new BigDecimal("8.00"), "Test");

            // When
            Position found = positionManager.findOpenPositionBySymbol("RELIANCE-EQ");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.symbol()).isEqualTo("RELIANCE-EQ");
        }

        @Test
        void findOpenPositionBySymbol_notFound() {
            // Given: No position for the symbol
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When
            Position found = positionManager.findOpenPositionBySymbol("TCS-EQ");

            // Then
            assertThat(found).isNull();
        }

        @Test
        void hasOpenPosition() {
            // Given: A position for RELIANCE-EQ
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When / Then
            assertThat(positionManager.hasOpenPosition("RELIANCE-EQ")).isTrue();
            assertThat(positionManager.hasOpenPosition("TCS-EQ")).isFalse();
        }

        @Test
        void maxPositions() {
            // When
            int max = positionManager.getMaxPositions();

            // Then
            assertThat(max).isEqualTo(5);
        }

        @Test
        void removePosition() {
            // Given: A position
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When
            boolean removed = positionManager.removePosition("POS_00000001");

            // Then
            assertThat(removed).isTrue();
            assertThat(positionManager.getPosition("POS_00000001")).isNull();
        }

        @Test
        void removePosition_notFound() {
            // When
            boolean removed = positionManager.removePosition("POS_NONEXIST");

            // Then
            assertThat(removed).isFalse();
        }

        @Test
        void positionsInsideMap() {
            // Given: Multiple positions
            positionManager.createPosition("POS_00000001", "RELIANCE-EQ", TradeDirection.LONG, 10, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");
            positionManager.createPosition("POS_00000002", "TCS-EQ", TradeDirection.SHORT, 5, new BigDecimal("200.00"), new BigDecimal("8.00"), "Test");

            // When / Then
            assertThat(positionManager.getPositions()).hasSize(2);
        }

        @Test
        void edge_case_partialExitUsesActualSharesAndKeepsRemainder() {
            // Given: A position with quantity 7
            String positionId = "POS_00000007";
            positionManager.createPosition(positionId, "RELIANCE-EQ", TradeDirection.LONG, 7, new BigDecimal("100.00"), new BigDecimal("5.00"), "Test");

            // When: Exit 30% => 7 * 0.3 = 2.1 => sell 2 whole shares
            Position result = positionManager.partialExitPosition(positionId, new BigDecimal("0.3"), new BigDecimal("105.00"));

            // Then
            assertThat(result.quantity()).isEqualTo(5);
            assertThat(result.realizedPnL()).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        void edge_case_positionWithNullFields() {
            // Given: A position with null broker-enriched fields
            Position position = makePosition(1L, null, "RELIANCE-EQ",
                    new BigDecimal("100.00"), LocalDate.now(), 10,
                    new BigDecimal("90.00"), new BigDecimal("125.00"),
                    PositionStatus.OPEN, "Test", new BigDecimal("100.00"),
                    null, null, null,
                    null, null,
                    null, null, null,
                    null, null, null, null);

            // When / Then: The compact constructor should fill defaults
            assertThat(position.brokerType()).isEqualTo("PAPER");
            assertThat(position.exchange()).isEqualTo(Exchange.NSE);
            assertThat(position.direction()).isEqualTo(TradeDirection.LONG);
            assertThat(position.unrealizedPnL()).isEqualTo(BigDecimal.ZERO);
        }
    }
}
