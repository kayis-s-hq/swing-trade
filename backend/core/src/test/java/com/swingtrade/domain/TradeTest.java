package com.swingtrade.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for the Trade domain model.
 */
class TradeTest {

    private static final Long POSITION_ID = 1L;
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate ENTRY_DATE = LocalDate.of(2024, 1, 15);
    private static final BigDecimal ENTRY_PRICE = BigDecimal.valueOf(100.00);
    private static final Integer QUANTITY = 100;
    private static final String ENTRY_REASON = "Technical breakout pattern detected";
    private static final BigDecimal FEES = BigDecimal.valueOf(10.00);

    @Nested
    class RequiredFields {

        @Test
        void shouldHaveAllRequiredFields() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade.id()).isNull();
            assertThat(trade.positionId()).isEqualTo(POSITION_ID);
            assertThat(trade.symbol()).isEqualTo(SYMBOL);
            assertThat(trade.entryDate()).isEqualTo(ENTRY_DATE);
            assertThat(trade.exitDate()).isNull();
            assertThat(trade.entryPrice()).isEqualTo(ENTRY_PRICE);
            assertThat(trade.exitPrice()).isNull();
            assertThat(trade.quantity()).isEqualTo(QUANTITY);
            assertThat(trade.totalPnL()).isNull();
            assertThat(trade.durationDays()).isNull();
            assertThat(trade.tradeStatus()).isEqualTo(Trade.TradeStatus.OPEN);
            assertThat(trade.entryReason()).isEqualTo(ENTRY_REASON);
            assertThat(trade.exitReason()).isNull();
            assertThat(trade.fees()).isEqualTo(FEES);
        }

        @Test
        void shouldAcceptClosedTradeWithAllFields() {
            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            Trade closedTrade = new Trade(
                1L,
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                exitDate,
                ENTRY_PRICE,
                BigDecimal.valueOf(110.00),
                QUANTITY,
                BigDecimal.valueOf(1000.00),
                5,
                Trade.TradeStatus.CLOSED,
                ENTRY_REASON,
                "Target reached",
                FEES
            );

            assertThat(closedTrade.id()).isEqualTo(1L);
            assertThat(closedTrade.positionId()).isEqualTo(POSITION_ID);
            assertThat(closedTrade.symbol()).isEqualTo(SYMBOL);
            assertThat(closedTrade.entryDate()).isEqualTo(ENTRY_DATE);
            assertThat(closedTrade.exitDate()).isEqualTo(exitDate);
            assertThat(closedTrade.entryPrice()).isEqualTo(ENTRY_PRICE);
            assertThat(closedTrade.exitPrice()).isEqualTo(BigDecimal.valueOf(110.00));
            assertThat(closedTrade.quantity()).isEqualTo(QUANTITY);
            assertThat(closedTrade.totalPnL()).isEqualTo(BigDecimal.valueOf(1000.00));
            assertThat(closedTrade.durationDays()).isEqualTo(5);
            assertThat(closedTrade.tradeStatus()).isEqualTo(Trade.TradeStatus.CLOSED);
            assertThat(closedTrade.entryReason()).isEqualTo(ENTRY_REASON);
            assertThat(closedTrade.exitReason()).isEqualTo("Target reached");
            assertThat(closedTrade.fees()).isEqualTo(FEES);
        }
    }

    @Nested
    class PriceAndQuantityValidation {

        @Test
        void shouldAcceptZeroEntryPrice() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                BigDecimal.ZERO,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade.entryPrice()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void shouldAcceptNegativeEntryPrice() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                BigDecimal.valueOf(-100.00),
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade.entryPrice()).isEqualTo(BigDecimal.valueOf(-100.00));
        }

        @Test
        void shouldAcceptZeroQuantity() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                0,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade.quantity()).isEqualTo(0);
        }

        @Test
        void shouldAcceptNegativeQuantity() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                -100,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade.quantity()).isEqualTo(-100);
        }
    }

    @Nested
    class OpenFactoryMethod {

        @Test
        void shouldCreateTradeWithOpenStatus() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade.tradeStatus()).isEqualTo(Trade.TradeStatus.OPEN);
            assertThat(trade.isOpen()).isTrue();
        }

        @Test
        void shouldSetNullExitFields() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade.exitDate()).isNull();
            assertThat(trade.exitPrice()).isNull();
            assertThat(trade.totalPnL()).isNull();
            assertThat(trade.durationDays()).isNull();
            assertThat(trade.exitReason()).isNull();
        }

        @Test
        void shouldSetNullId() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade.id()).isNull();
        }

        @Test
        void shouldPreserveAllInputValues() {
            BigDecimal customFees = BigDecimal.valueOf(25.50);
            LocalDate customEntryDate = LocalDate.of(2024, 6, 1);
            BigDecimal customEntryPrice = BigDecimal.valueOf(2500.00);
            Integer customQuantity = 20;
            String customEntryReason = "Large cap momentum";

            Trade trade = Trade.open(
                5L,
                "HDFCBANK",
                customEntryDate,
                customEntryPrice,
                customQuantity,
                customEntryReason,
                customFees
            );

            assertThat(trade.positionId()).isEqualTo(5L);
            assertThat(trade.symbol()).isEqualTo("HDFCBANK");
            assertThat(trade.entryDate()).isEqualTo(customEntryDate);
            assertThat(trade.entryPrice()).isEqualTo(customEntryPrice);
            assertThat(trade.quantity()).isEqualTo(customQuantity);
            assertThat(trade.entryReason()).isEqualTo(customEntryReason);
            assertThat(trade.fees()).isEqualTo(customFees);
        }
    }

    @Nested
    class CloseFactoryMethod {

        private Trade openTrade;

        @Test
        void shouldCloseTradeWithProfit() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            BigDecimal exitPrice = BigDecimal.valueOf(110.00);
            Trade closedTrade = Trade.close(openTrade, exitDate, exitPrice, "Target reached");

            assertThat(closedTrade.id()).isEqualTo(openTrade.id());
            assertThat(closedTrade.positionId()).isEqualTo(POSITION_ID);
            assertThat(closedTrade.symbol()).isEqualTo(SYMBOL);
            assertThat(closedTrade.entryDate()).isEqualTo(ENTRY_DATE);
            assertThat(closedTrade.exitDate()).isEqualTo(exitDate);
            assertThat(closedTrade.entryPrice()).isEqualTo(ENTRY_PRICE);
            assertThat(closedTrade.exitPrice()).isEqualTo(exitPrice);
            assertThat(closedTrade.quantity()).isEqualTo(QUANTITY);
            assertThat(closedTrade.totalPnL()).isEqualTo(BigDecimal.valueOf(1000.00));
            assertThat(closedTrade.tradeStatus()).isEqualTo(Trade.TradeStatus.CLOSED);
            assertThat(closedTrade.entryReason()).isEqualTo(ENTRY_REASON);
            assertThat(closedTrade.exitReason()).isEqualTo("Target reached");
            assertThat(closedTrade.fees()).isEqualTo(FEES);
        }

        @Test
        void shouldCloseTradeWithLoss() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            BigDecimal exitPrice = BigDecimal.valueOf(90.00);
            Trade closedTrade = Trade.close(openTrade, exitDate, exitPrice, "Stop loss hit");

            assertThat(closedTrade.exitPrice()).isEqualTo(exitPrice);
            assertThat(closedTrade.totalPnL()).isEqualTo(BigDecimal.valueOf(-1000.00));
            assertThat(closedTrade.tradeStatus()).isEqualTo(Trade.TradeStatus.STOPPED);
        }

        @Test
        void shouldCalculatePnLCorrectlyForProfit() {
            openTrade = Trade.open(
                POSITION_ID,
                "TCS",
                ENTRY_DATE,
                BigDecimal.valueOf(3500.00),
                10,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 25);
            BigDecimal exitPrice = BigDecimal.valueOf(3700.00);
            Trade closedTrade = Trade.close(openTrade, exitDate, exitPrice, "Target reached");

            BigDecimal expectedPnL = exitPrice.subtract(BigDecimal.valueOf(3500.00)).multiply(BigDecimal.valueOf(10));
            assertThat(closedTrade.totalPnL()).isEqualTo(expectedPnL);
        }

        @Test
        void shouldCalculatePnLCorrectlyForLoss() {
            openTrade = Trade.open(
                POSITION_ID,
                "INFY",
                ENTRY_DATE,
                BigDecimal.valueOf(1400.00),
                25,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 25);
            BigDecimal exitPrice = BigDecimal.valueOf(1350.00);
            Trade closedTrade = Trade.close(openTrade, exitDate, exitPrice, "Stop loss hit");

            BigDecimal expectedPnL = exitPrice.subtract(BigDecimal.valueOf(1400.00)).multiply(BigDecimal.valueOf(25));
            assertThat(closedTrade.totalPnL()).isEqualTo(expectedPnL);
            assertThat(closedTrade.tradeStatus()).isEqualTo(Trade.TradeStatus.STOPPED);
        }

        @Test
        void shouldCalculateDurationInDays() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 25);
            BigDecimal exitPrice = BigDecimal.valueOf(110.00);
            Trade closedTrade = Trade.close(openTrade, exitDate, exitPrice, "Target reached");

            assertThat(closedTrade.durationDays()).isEqualTo(10);
        }

        @Test
        void shouldSetStatusToStoppedForLossEvenIfCalledClose() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            BigDecimal exitPrice = BigDecimal.valueOf(95.00);
            Trade closedTrade = Trade.close(openTrade, exitDate, exitPrice, "Manual exit at loss");

            assertThat(closedTrade.tradeStatus()).isEqualTo(Trade.TradeStatus.STOPPED);
        }

        @Test
        void shouldPreserveIdOnClose() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            BigDecimal exitPrice = BigDecimal.valueOf(110.00);
            Trade closedTrade = Trade.close(openTrade, exitDate, exitPrice, "Target reached");

            assertThat(closedTrade.id()).isEqualTo(openTrade.id());
        }
    }

    @Nested
    class PnLCalculation {

        @Test
        void shouldCalculateProfitForLongPositionBoughtLowSoldHigh() {
            Trade trade = Trade.open(
                POSITION_ID,
                "RELIANCE",
                ENTRY_DATE,
                BigDecimal.valueOf(100.00),
                50,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            BigDecimal exitPrice = BigDecimal.valueOf(120.00);
            Trade closedTrade = Trade.close(trade, exitDate, exitPrice, "Profit taking");

            BigDecimal expectedPnL = BigDecimal.valueOf(20.00).multiply(BigDecimal.valueOf(50));
            assertThat(closedTrade.totalPnL()).isEqualTo(expectedPnL);
        }

        @Test
        void shouldCalculateLossForLongPositionBoughtHighSoldLow() {
            Trade trade = Trade.open(
                POSITION_ID,
                "RELIANCE",
                ENTRY_DATE,
                BigDecimal.valueOf(100.00),
                50,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            BigDecimal exitPrice = BigDecimal.valueOf(85.00);
            Trade closedTrade = Trade.close(trade, exitDate, exitPrice, "Stop loss");

            BigDecimal expectedPnL = BigDecimal.valueOf(-15.00).multiply(BigDecimal.valueOf(50));
            assertThat(closedTrade.totalPnL()).isEqualTo(expectedPnL);
        }

        @Test
        void shouldCalculateZeroPnLWhenEntryEqualsExit() {
            Trade trade = Trade.open(
                POSITION_ID,
                "TCS",
                ENTRY_DATE,
                BigDecimal.valueOf(3500.00),
                10,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            BigDecimal exitPrice = BigDecimal.valueOf(3500.00);
            Trade closedTrade = Trade.close(trade, exitDate, exitPrice, "Break even exit");

            assertThat(closedTrade.totalPnL()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldHandleLargePositionSizing() {
            Trade trade = Trade.open(
                POSITION_ID,
                "HDFCBANK",
                ENTRY_DATE,
                BigDecimal.valueOf(1500.00),
                200,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 25);
            BigDecimal exitPrice = BigDecimal.valueOf(1650.00);
            Trade closedTrade = Trade.close(trade, exitDate, exitPrice, "Target reached");

            BigDecimal expectedPnL = BigDecimal.valueOf(150.00).multiply(BigDecimal.valueOf(200));
            assertThat(closedTrade.totalPnL()).isEqualTo(expectedPnL);
        }

        @Test
        void shouldHandleSmallPositionSizing() {
            Trade trade = Trade.open(
                POSITION_ID,
                "WIPRO",
                ENTRY_DATE,
                BigDecimal.valueOf(450.00),
                5,
                ENTRY_REASON,
                FEES
            );

            LocalDate exitDate = LocalDate.of(2024, 1, 20);
            BigDecimal exitPrice = BigDecimal.valueOf(460.00);
            Trade closedTrade = Trade.close(trade, exitDate, exitPrice, "Small profit");

            BigDecimal expectedPnL = BigDecimal.valueOf(10.00).multiply(BigDecimal.valueOf(5));
            assertThat(closedTrade.totalPnL()).isEqualTo(expectedPnL);
        }
    }

    @Nested
    class StatusMethods {

        private Trade openTrade;
        private Trade closedProfitTrade;
        private Trade closedLossTrade;
        private Trade targetHitTrade;
        private Trade timeStopTrade;

        @Test
        void shouldReturnTrueForOpenTradeIsOpen() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(openTrade.isOpen()).isTrue();
        }

        @Test
        void shouldReturnFalseForClosedTradeIsOpen() {
            closedProfitTrade = new Trade(
                1L,
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                LocalDate.of(2024, 1, 20),
                ENTRY_PRICE,
                BigDecimal.valueOf(110.00),
                QUANTITY,
                BigDecimal.valueOf(1000.00),
                5,
                Trade.TradeStatus.CLOSED,
                ENTRY_REASON,
                "Target reached",
                FEES
            );

            assertThat(closedProfitTrade.isOpen()).isFalse();
        }

        @Test
        void shouldReturnFalseForStoppedTradeIsOpen() {
            closedLossTrade = new Trade(
                1L,
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                LocalDate.of(2024, 1, 20),
                ENTRY_PRICE,
                BigDecimal.valueOf(95.00),
                QUANTITY,
                BigDecimal.valueOf(-500.00),
                5,
                Trade.TradeStatus.STOPPED,
                ENTRY_REASON,
                "Stop loss hit",
                FEES
            );

            assertThat(closedLossTrade.isOpen()).isFalse();
        }

        @Test
        void shouldReturnTrueForProfitableTrade() {
            closedProfitTrade = new Trade(
                1L,
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                LocalDate.of(2024, 1, 20),
                ENTRY_PRICE,
                BigDecimal.valueOf(110.00),
                QUANTITY,
                BigDecimal.valueOf(1000.00),
                5,
                Trade.TradeStatus.CLOSED,
                ENTRY_REASON,
                "Target reached",
                FEES
            );

            assertThat(closedProfitTrade.isProfitable()).isTrue();
        }

        @Test
        void shouldReturnFalseForLossTrade() {
            closedLossTrade = new Trade(
                1L,
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                LocalDate.of(2024, 1, 20),
                ENTRY_PRICE,
                BigDecimal.valueOf(95.00),
                QUANTITY,
                BigDecimal.valueOf(-500.00),
                5,
                Trade.TradeStatus.STOPPED,
                ENTRY_REASON,
                "Stop loss hit",
                FEES
            );

            assertThat(closedLossTrade.isProfitable()).isFalse();
        }

        @Test
        void shouldReturnFalseForOpenTradeIsProfitable() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(openTrade.isProfitable()).isFalse();
        }

        @Test
        void shouldReturnTrueForLossTrade() {
            closedLossTrade = new Trade(
                1L,
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                LocalDate.of(2024, 1, 20),
                ENTRY_PRICE,
                BigDecimal.valueOf(95.00),
                QUANTITY,
                BigDecimal.valueOf(-500.00),
                5,
                Trade.TradeStatus.STOPPED,
                ENTRY_REASON,
                "Stop loss hit",
                FEES
            );

            assertThat(closedLossTrade.isLoss()).isTrue();
        }

        @Test
        void shouldReturnFalseForProfitTradeIsLoss() {
            closedProfitTrade = new Trade(
                1L,
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                LocalDate.of(2024, 1, 20),
                ENTRY_PRICE,
                BigDecimal.valueOf(110.00),
                QUANTITY,
                BigDecimal.valueOf(1000.00),
                5,
                Trade.TradeStatus.CLOSED,
                ENTRY_REASON,
                "Target reached",
                FEES
            );

            assertThat(closedProfitTrade.isLoss()).isFalse();
        }

        @Test
        void shouldReturnFalseForOpenTradeIsLoss() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(openTrade.isLoss()).isFalse();
        }

        @Test
        void shouldReturnFalseForTargetHitTradeIsLoss() {
            targetHitTrade = new Trade(
                1L,
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                LocalDate.of(2024, 1, 20),
                ENTRY_PRICE,
                BigDecimal.valueOf(115.00),
                QUANTITY,
                BigDecimal.valueOf(1500.00),
                5,
                Trade.TradeStatus.TARGET_HIT,
                ENTRY_REASON,
                "Target hit",
                FEES
            );

            assertThat(targetHitTrade.isLoss()).isFalse();
            assertThat(targetHitTrade.isProfitable()).isTrue();
        }

        @Test
        void shouldHandleNullPnLForOpenTrade() {
            openTrade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(openTrade.isProfitable()).isFalse();
            assertThat(openTrade.isLoss()).isFalse();
        }
    }

    @Nested
    class TradeStatusEnum {

        @Test
        void shouldHaveAllEnumValues() {
            assertThat(Trade.TradeStatus.OPEN).isNotNull();
            assertThat(Trade.TradeStatus.CLOSED).isNotNull();
            assertThat(Trade.TradeStatus.STOPPED).isNotNull();
            assertThat(Trade.TradeStatus.TARGET_HIT).isNotNull();
            assertThat(Trade.TradeStatus.TIME_STOP).isNotNull();
        }

        @Test
        void shouldHaveCorrectDisplayNameForOpen() {
            assertThat(Trade.TradeStatus.OPEN.getDisplayName()).isEqualTo("Open");
        }

        @Test
        void shouldHaveCorrectDisplayNameForClosed() {
            assertThat(Trade.TradeStatus.CLOSED.getDisplayName()).isEqualTo("Closed - Profit");
        }

        @Test
        void shouldHaveCorrectDisplayNameForStopped() {
            assertThat(Trade.TradeStatus.STOPPED.getDisplayName()).isEqualTo("Stopped - Loss");
        }

        @Test
        void shouldHaveCorrectDisplayNameForTargetHit() {
            assertThat(Trade.TradeStatus.TARGET_HIT.getDisplayName()).isEqualTo("Target Hit - Profit");
        }

        @Test
        void shouldHaveCorrectDisplayNameForTimeStop() {
            assertThat(Trade.TradeStatus.TIME_STOP.getDisplayName()).isEqualTo("Time Stop");
        }

        @Test
        void shouldIterateOverAllStatuses() {
            Trade.TradeStatus[] statuses = Trade.TradeStatus.values();

            assertThat(statuses).hasSize(5);
            assertThat(statuses).contains(Trade.TradeStatus.OPEN);
            assertThat(statuses).contains(Trade.TradeStatus.CLOSED);
            assertThat(statuses).contains(Trade.TradeStatus.STOPPED);
            assertThat(statuses).contains(Trade.TradeStatus.TARGET_HIT);
            assertThat(statuses).contains(Trade.TradeStatus.TIME_STOP);
        }
    }

    @Nested
    class EqualsAndHashCode {

        private Trade trade1;
        private Trade trade2;
        private Trade trade3;

        @Test
        void shouldReturnTrueWhenComparingSameObject() {
            trade1 = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade1).isEqualTo(trade1);
            assertThat(trade1.hashCode()).isEqualTo(trade1.hashCode());
        }

        @Test
        void shouldReturnTrueWhenComparingEqualOpenTrades() {
            trade1 = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            trade2 = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade1).isEqualTo(trade2);
            assertThat(trade1.hashCode()).isEqualTo(trade2.hashCode());
        }

        @Test
        void shouldReturnTrueWhenComparingEqualClosedTrades() {
            trade1 = Trade.close(
                Trade.open(POSITION_ID, SYMBOL, ENTRY_DATE, ENTRY_PRICE, QUANTITY, ENTRY_REASON, FEES),
                LocalDate.of(2024, 1, 20),
                BigDecimal.valueOf(110.00),
                "Target reached"
            );

            trade2 = Trade.close(
                Trade.open(POSITION_ID, SYMBOL, ENTRY_DATE, ENTRY_PRICE, QUANTITY, ENTRY_REASON, FEES),
                LocalDate.of(2024, 1, 20),
                BigDecimal.valueOf(110.00),
                "Target reached"
            );

            assertThat(trade1).isEqualTo(trade2);
            assertThat(trade1.hashCode()).isEqualTo(trade2.hashCode());
        }

        @Test
        void shouldReturnFalseWhenSymbolsDiffer() {
            trade1 = Trade.open(
                POSITION_ID,
                "RELIANCE",
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            trade2 = Trade.open(
                POSITION_ID,
                "TCS",
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade1).isNotEqualTo(trade2);
            assertThat(trade1).isNotEqualTo("RELIANCE");
        }

        @Test
        void shouldReturnFalseWhenEntryPricesDiffer() {
            trade1 = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                BigDecimal.valueOf(100.00),
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            trade2 = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                BigDecimal.valueOf(105.00),
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade1).isNotEqualTo(trade2);
        }

        @Test
        void shouldReturnFalseWhenQuantitiesDiffer() {
            trade1 = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                100,
                ENTRY_REASON,
                FEES
            );

            trade2 = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                50,
                ENTRY_REASON,
                FEES
            );

            assertThat(trade1).isNotEqualTo(trade2);
        }

        @Test
        void shouldReturnFalseWhenExitPricesDifferForClosedTrades() {
            trade1 = Trade.close(
                Trade.open(POSITION_ID, SYMBOL, ENTRY_DATE, ENTRY_PRICE, QUANTITY, ENTRY_REASON, FEES),
                LocalDate.of(2024, 1, 20),
                BigDecimal.valueOf(110.00),
                "Target reached"
            );

            trade2 = Trade.close(
                Trade.open(POSITION_ID, SYMBOL, ENTRY_DATE, ENTRY_PRICE, QUANTITY, ENTRY_REASON, FEES),
                LocalDate.of(2024, 1, 20),
                BigDecimal.valueOf(115.00),
                "Target reached"
            );

            assertThat(trade1).isNotEqualTo(trade2);
        }
    }

    @Nested
    class ToString {

        @Test
        void shouldIncludeAllFieldsInOpenTradeToString() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            String toString = trade.toString();

            assertThat(toString).contains(SYMBOL);
            assertThat(toString).contains(ENTRY_PRICE.toString());
            assertThat(toString).contains(ENTRY_DATE.toString());
            assertThat(toString).contains(QUANTITY.toString());
            assertThat(toString).contains(Trade.TradeStatus.OPEN.toString());
            assertThat(toString).contains(ENTRY_REASON);
        }

        @Test
        void shouldIncludeAllFieldsInClosedTradeToString() {
            Trade trade = Trade.close(
                Trade.open(POSITION_ID, SYMBOL, ENTRY_DATE, ENTRY_PRICE, QUANTITY, ENTRY_REASON, FEES),
                LocalDate.of(2024, 1, 20),
                BigDecimal.valueOf(110.00),
                "Target reached"
            );

            String toString = trade.toString();

            assertThat(toString).contains(SYMBOL);
            assertThat(toString).contains(ENTRY_PRICE.toString());
            assertThat(toString).contains("110.0");
            assertThat(toString).contains(QUANTITY.toString());
            assertThat(toString).contains("1000.0");
            assertThat(toString).contains(Trade.TradeStatus.CLOSED.toString());
        }

        @Test
        void shouldHandleNullIdInToString() {
            Trade trade = Trade.open(
                POSITION_ID,
                SYMBOL,
                ENTRY_DATE,
                ENTRY_PRICE,
                QUANTITY,
                ENTRY_REASON,
                FEES
            );

            String toString = trade.toString();

            assertThat(toString).contains(SYMBOL);
            assertThat(toString).contains("RELIANCE");
        }

        @Test
        void shouldIncludeTradeStatusDisplayName() {
            Trade trade = Trade.close(
                Trade.open(POSITION_ID, SYMBOL, ENTRY_DATE, ENTRY_PRICE, QUANTITY, ENTRY_REASON, FEES),
                LocalDate.of(2024, 1, 20),
                BigDecimal.valueOf(110.00),
                "Target reached"
            );

            String toString = trade.toString();

            assertThat(toString).contains("CLOSED");
        }
    }

    // Helper methods for test data generation
    private Trade createTestOpenTrade(Long positionId, String symbol, BigDecimal entryPrice,
                                      Integer quantity, String entryReason) {
        return Trade.open(
            positionId,
            symbol,
            ENTRY_DATE,
            entryPrice,
            quantity,
            entryReason,
            BigDecimal.valueOf(10.00)
        );
    }

    private Trade createTestClosedTrade(Long id, Long positionId, String symbol, BigDecimal entryPrice,
                                        BigDecimal exitPrice, Integer quantity, BigDecimal pnl,
                                        int durationDays, Trade.TradeStatus status, String exitReason) {
        return new Trade(
            id,
            positionId,
            symbol,
            ENTRY_DATE,
            LocalDate.of(2024, 1, 20),
            entryPrice,
            exitPrice,
            quantity,
            pnl,
            durationDays,
            status,
            ENTRY_REASON,
            exitReason,
            FEES
        );
    }
}
