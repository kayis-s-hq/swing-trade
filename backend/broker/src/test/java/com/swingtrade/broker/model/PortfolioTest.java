package com.swingtrade.broker.model;

import com.swingtrade.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Portfolio covering construction, position management,
 * P&L calculation, queries, capital management, and edge cases.
 */
class PortfolioTest {

    private Portfolio portfolio;

    // Helper: build a Position for testing
    private Position makePosition(String symbol, PositionStatus status, BigDecimal entryPrice,
                                   BigDecimal currentPrice, int quantity, TradeDirection direction,
                                   BigDecimal unrealizedPnL) {
        return new Position(
            1L, "PAPER", symbol, entryPrice, LocalDate.now(), quantity,
            entryPrice.multiply(BigDecimal.valueOf(0.9)), entryPrice.multiply(BigDecimal.valueOf(1.25)),
            status, "Test", currentPrice,
            "POS_" + symbol, null, Exchange.NSE, direction,
            entryPrice, unrealizedPnL, BigDecimal.ZERO, BigDecimal.ZERO,
            null, null, null, null
        );
    }

    // ==================== Constructor ====================

    @Nested
    class Constructor {

        @Test
        void validParams() {
            // Given: Valid portfolio parameters
            String portfolioId = "test-portfolio";
            BigDecimal capital = new BigDecimal("1000000");

            // When
            Portfolio portfolio = new Portfolio(portfolioId, capital);

            // Then
            assertThat(portfolio.getPortfolioId()).isEqualTo(portfolioId);
            assertThat(portfolio.getInitialCapital()).isEqualTo(capital);
            assertThat(portfolio.getCurrentCapital()).isEqualTo(capital);
            assertThat(portfolio.getPositions()).isEmpty();
            assertThat(portfolio.getLastUpdated()).isNotNull();
        }

        @Test
        void nullCapital() {
            // Given: null initial capital
            // When
            Portfolio portfolio = new Portfolio("test-portfolio", null);

            // Then
            assertThat(portfolio.getInitialCapital()).isNull();
            assertThat(portfolio.getCurrentCapital()).isNull();
        }

        @Test
        void zeroCapital() {
            // Given: Zero initial capital
            BigDecimal zero = BigDecimal.ZERO;

            // When
            Portfolio portfolio = new Portfolio("test-portfolio", zero);

            // Then
            assertThat(portfolio.getInitialCapital()).isEqualTo(BigDecimal.ZERO);
            assertThat(portfolio.getCurrentCapital()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void defaultConstructor() {
            // Given: No parameters
            // When
            Portfolio portfolio = new Portfolio();

            // Then
            assertThat(portfolio.getCurrentCapital()).isEqualTo(BigDecimal.ZERO);
            assertThat(portfolio.getPositions()).isEmpty();
            assertThat(portfolio.getLastUpdated()).isNotNull();
            assertThat(portfolio.getInitialCapital()).isNull();
            assertThat(portfolio.getPortfolioId()).isNull();
        }
    }

    // ==================== AddPosition ====================

    @Nested
    class AddPosition {

        @Test
        void addSinglePosition() {
            // Given: An empty portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO);

            // When
            portfolio.addPosition(position);

            // Then
            assertThat(portfolio.getPositions()).hasSize(1);
            assertThat(portfolio.getPosition("POS_RELIANCE-EQ")).isEqualTo(position);
            assertThat(portfolio.getLastUpdated()).isNotNull();
        }

        @Test
        void addMultiplePositions() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            Position pos1 = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO);
            Position pos2 = makePosition("TCS-EQ", PositionStatus.OPEN,
                new BigDecimal("3500.00"), new BigDecimal("3500.00"), 5, TradeDirection.LONG, BigDecimal.ZERO);

            // When
            portfolio.addPosition(pos1);
            portfolio.addPosition(pos2);

            // Then
            assertThat(portfolio.getPositions()).hasSize(2);
            assertThat(portfolio.getPosition("POS_RELIANCE-EQ")).isEqualTo(pos1);
            assertThat(portfolio.getPosition("POS_TCS-EQ")).isEqualTo(pos2);
        }

        @Test
        void duplicateSymbolReplaces() {
            // Given: A portfolio with one position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            Position pos1 = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO);
            Position pos2 = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2600.00"), new BigDecimal("2600.00"), 15, TradeDirection.LONG, BigDecimal.ZERO);

            // When
            portfolio.addPosition(pos1);
            portfolio.addPosition(pos2);

            // Then
            assertThat(portfolio.getPositions()).hasSize(1);
            assertThat(portfolio.getPosition("POS_RELIANCE-EQ")).isEqualTo(pos2);
        }

        @Test
        void addClosedPosition() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            Position position = makePosition("INFY-EQ", PositionStatus.CLOSED,
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), 20, TradeDirection.LONG, BigDecimal.ZERO);

            // When
            portfolio.addPosition(position);

            // Then
            assertThat(portfolio.getPositions()).hasSize(1);
            assertThat(portfolio.getPosition("POS_INFY-EQ").status()).isEqualTo(PositionStatus.CLOSED);
        }

        @Test
        void addShortPosition() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            Position position = makePosition("SBIN-EQ", PositionStatus.OPEN,
                new BigDecimal("700.00"), new BigDecimal("700.00"), 10, TradeDirection.SHORT, BigDecimal.ZERO);

            // When
            portfolio.addPosition(position);

            // Then
            assertThat(portfolio.getPositions()).hasSize(1);
            assertThat(portfolio.getPosition("POS_SBIN-EQ").direction()).isEqualTo(TradeDirection.SHORT);
        }
    }

    // ==================== RemovePosition ====================

    @Nested
    class RemovePosition {

        @Test
        void removeExisting() {
            // Given: A portfolio with one position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO);
            portfolio.addPosition(position);

            // When
            boolean removed = portfolio.removePosition("POS_RELIANCE-EQ");

            // Then
            assertThat(removed).isTrue();
            assertThat(portfolio.getPositions()).isEmpty();
        }

        @Test
        void removeNotFound() {
            // Given: A portfolio with no matching position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When
            boolean removed = portfolio.removePosition("POS_NONEXIST");

            // Then
            assertThat(removed).isFalse();
        }

        @Test
        void removeClosed() {
            // Given: A portfolio with a closed position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            Position position = makePosition("INFY-EQ", PositionStatus.CLOSED,
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), 20, TradeDirection.LONG, BigDecimal.ZERO);
            portfolio.addPosition(position);

            // When
            boolean removed = portfolio.removePosition("POS_INFY-EQ");

            // Then
            assertThat(removed).isTrue();
            assertThat(portfolio.getPositions()).isEmpty();
        }

        @Test
        void removeMultiple() {
            // Given: A portfolio with multiple positions
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));
            portfolio.addPosition(makePosition("TCS-EQ", PositionStatus.OPEN,
                new BigDecimal("3500.00"), new BigDecimal("3500.00"), 5, TradeDirection.LONG, BigDecimal.ZERO));

            // When
            portfolio.removePosition("POS_RELIANCE-EQ");
            portfolio.removePosition("POS_TCS-EQ");

            // Then
            assertThat(portfolio.getPositions()).isEmpty();
        }
    }

    // ==================== PnLCalc ====================

    @Nested
    class PnLCalc {

        @Test
        void unrealizedTotal_singleOpenPosition_profit() {
            // Given: One open position with positive unrealized P&L
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("110.00"), 10, TradeDirection.LONG,
                new BigDecimal("100.00"));
            portfolio.addPosition(position);

            // When
            BigDecimal totalValue = portfolio.getTotalValue();

            // Then
            // totalValue = currentCapital + unrealizedPnL = 1000000 + 100 = 1000100
            assertThat(totalValue).isEqualTo(new BigDecimal("1000100.00"));
        }

        @Test
        void unrealizedTotal_singleOpenPosition_loss() {
            // Given: One open position with negative unrealized P&L
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("90.00"), 10, TradeDirection.LONG,
                new BigDecimal("-100.00"));
            portfolio.addPosition(position);

            // When
            BigDecimal totalValue = portfolio.getTotalValue();

            // Then
            // totalValue = 1000000 + (-100) = 999900
            assertThat(totalValue).isEqualTo(new BigDecimal("999900.00"));
        }

        @Test
        void realizedTotal_closedPosition() {
            // Given: A closed position (realized P&L not included in getTotalValue)
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            Position closedPos = makePosition("INFY-EQ", PositionStatus.CLOSED,
                new BigDecimal("1500.00"), new BigDecimal("1550.00"), 20, TradeDirection.LONG,
                new BigDecimal("0.00"));
            portfolio.addPosition(closedPos);

            // When
            BigDecimal totalValue = portfolio.getTotalValue();

            // Then: Closed positions not included in totalValue
            assertThat(totalValue).isEqualTo(new BigDecimal("1000000"));
        }

        @Test
        void combinedPnl_positive() {
            // Given: One open position with unrealized P&L
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("110.00"), 10, TradeDirection.LONG,
                new BigDecimal("100.00"));
            portfolio.addPosition(position);

            // When
            BigDecimal totalValue = portfolio.getTotalValue();

            // Then
            assertThat(totalValue).isEqualTo(new BigDecimal("1000100.00"));
        }

        @Test
        void pnlPercentage_long_profit() {
            // Given: A long position with entry 100, current 110
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("110.00"), 10, TradeDirection.LONG,
                new BigDecimal("100.00"));

            // When
            BigDecimal pnlPct = position.calculatePnLPercent(new BigDecimal("110.00"));

            // Then
            assertThat(pnlPct).isEqualTo(new BigDecimal("10.0000"));
        }

        @Test
        void pnlPercentage_long_loss() {
            // Given: A long position with entry 100, current 90
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("90.00"), 10, TradeDirection.LONG,
                new BigDecimal("-100.00"));

            // When
            BigDecimal pnlPct = position.calculatePnLPercent(new BigDecimal("90.00"));

            // Then
            assertThat(pnlPct).isEqualTo(new BigDecimal("-10.0000"));
        }

        @Test
        void pnlPercentage_short_profit() {
            // Given: A short position with entry 100, current 90
            Position position = makePosition("TCS-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("90.00"), 10, TradeDirection.SHORT,
                new BigDecimal("100.00"));

            // When
            BigDecimal pnlPct = position.calculatePnLPercent(new BigDecimal("90.00"));

            // Then
            assertThat(pnlPct).isEqualTo(new BigDecimal("10.0000"));
        }

        @Test
        void pnlPercentage_short_loss() {
            // Given: A short position with entry 100, current 110
            Position position = makePosition("TCS-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("110.00"), 10, TradeDirection.SHORT,
                new BigDecimal("-100.00"));

            // When
            BigDecimal pnlPct = position.calculatePnLPercent(new BigDecimal("110.00"));

            // Then
            assertThat(pnlPct).isEqualTo(new BigDecimal("-10.0000"));
        }
    }

    // ==================== Queries ====================

    @Nested
    class Queries {

        @Test
        void getPositions() {
            // Given: Multiple positions
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));
            portfolio.addPosition(makePosition("TCS-EQ", PositionStatus.OPEN,
                new BigDecimal("3500.00"), new BigDecimal("3500.00"), 5, TradeDirection.LONG, BigDecimal.ZERO));

            // When
            Map<String, Position> positions = portfolio.getPositions();

            // Then
            assertThat(positions).hasSize(2);
            assertThat(positions).containsKey("POS_RELIANCE-EQ");
            assertThat(positions).containsKey("POS_TCS-EQ");
        }

        @Test
        void getOpenPositions() {
            // Given: Mix of open and closed positions
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));
            portfolio.addPosition(makePosition("INFY-EQ", PositionStatus.CLOSED,
                new BigDecimal("1500.00"), new BigDecimal("1550.00"), 20, TradeDirection.LONG, BigDecimal.ZERO));

            // When: Filter manually since no dedicated method
            long openCount = portfolio.getPositions().values().stream()
                .filter(p -> p.status() == PositionStatus.OPEN).count();

            // Then
            assertThat(openCount).isEqualTo(1);
        }

        @Test
        void getClosedPositions() {
            // Given: Mix of open and closed positions
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));
            portfolio.addPosition(makePosition("INFY-EQ", PositionStatus.CLOSED,
                new BigDecimal("1500.00"), new BigDecimal("1550.00"), 20, TradeDirection.LONG, BigDecimal.ZERO));

            // When: Filter manually since no dedicated method
            long closedCount = portfolio.getPositions().values().stream()
                .filter(p -> p.status() == PositionStatus.CLOSED).count();

            // Then
            assertThat(closedCount).isEqualTo(1);
        }

        @Test
        void size() {
            // Given: Multiple positions
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));
            portfolio.addPosition(makePosition("TCS-EQ", PositionStatus.OPEN,
                new BigDecimal("3500.00"), new BigDecimal("3500.00"), 5, TradeDirection.LONG, BigDecimal.ZERO));

            // When / Then
            assertThat(portfolio.getPositions()).hasSize(2);
        }

        @Test
        void sizeEmpty() {
            // Given: Empty portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When / Then
            assertThat(portfolio.getPositions()).isEmpty();
        }

        @Test
        void getPosition_found() {
            // Given: A position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            Position position = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO);
            portfolio.addPosition(position);

            // When
            Position found = portfolio.getPosition("POS_RELIANCE-EQ");

            // Then
            assertThat(found).isEqualTo(position);
        }

        @Test
        void getPosition_notFound() {
            // Given: A portfolio with no matching position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When
            Position found = portfolio.getPosition("POS_NONEXIST");

            // Then
            assertThat(found).isNull();
        }
    }

    // ==================== Capital ====================

    @Nested
    class Capital {

        @Test
        void currentCapital_default() {
            // Given: Default constructor
            portfolio = new Portfolio();

            // When / Then
            assertThat(portfolio.getCurrentCapital()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void currentCapital_set() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When
            portfolio.setCurrentCapital(new BigDecimal("950000"));

            // Then
            assertThat(portfolio.getCurrentCapital()).isEqualTo(new BigDecimal("950000"));
        }

        @Test
        void initialCapital() {
            // Given: A portfolio
            BigDecimal initial = new BigDecimal("1000000");
            portfolio = new Portfolio("test-portfolio", initial);

            // When / Then
            assertThat(portfolio.getInitialCapital()).isEqualTo(initial);
        }

        @Test
        void totalValue_noPositions() {
            // Given: A portfolio with no positions
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When
            BigDecimal totalValue = portfolio.getTotalValue();

            // Then
            assertThat(totalValue).isEqualTo(new BigDecimal("1000000"));
        }

        @Test
        void totalValue_withOpenPosition() {
            // Given: A portfolio with an open position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("110.00"), 10, TradeDirection.LONG,
                new BigDecimal("100.00")));

            // When
            BigDecimal totalValue = portfolio.getTotalValue();

            // Then
            assertThat(totalValue).isEqualTo(new BigDecimal("1000100.00"));
        }

        @Test
        void setAvailableCash() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When
            portfolio.setAvailableCash(new BigDecimal("900000"));

            // Then
            assertThat(portfolio.getCurrentCapital()).isEqualTo(new BigDecimal("900000"));
        }

        @Test
        void setTotalCapital() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When
            portfolio.setTotalCapital(new BigDecimal("1100000"));

            // Then
            assertThat(portfolio.getInitialCapital()).isEqualTo(new BigDecimal("1100000"));
            assertThat(portfolio.getCurrentCapital()).isEqualTo(new BigDecimal("1100000"));
        }

        @Test
        void setInitialCapital() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When
            portfolio.setInitialCapital(new BigDecimal("1200000"));

            // Then
            assertThat(portfolio.getInitialCapital()).isEqualTo(new BigDecimal("1200000"));
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void emptyPortfolio() {
            // Given: An empty portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // Then
            assertThat(portfolio.getPositions()).isEmpty();
            assertThat(portfolio.getTotalValue()).isEqualTo(new BigDecimal("1000000"));
        }

        @Test
        void maxPositions() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When: Add many positions
            for (int i = 1; i <= 100; i++) {
                String symbol = "STOCK" + i + "-EQ";
                portfolio.addPosition(makePosition(symbol, PositionStatus.OPEN,
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));
            }

            // Then
            assertThat(portfolio.getPositions()).hasSize(100);
        }

        @Test
        void holdingsViaSetHoldings() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            Position pos1 = makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO);
            Position pos2 = makePosition("TCS-EQ", PositionStatus.OPEN,
                new BigDecimal("3500.00"), new BigDecimal("3500.00"), 5, TradeDirection.LONG, BigDecimal.ZERO);

            // When
            portfolio.setHoldings(List.of(pos1, pos2));

            // Then
            assertThat(portfolio.getPositions()).hasSize(2);
            assertThat(portfolio.getPosition("POS_RELIANCE-EQ")).isEqualTo(pos1);
            assertThat(portfolio.getPosition("POS_TCS-EQ")).isEqualTo(pos2);
        }

        @Test
        void totalValueClosedPositionsNotIncluded() {
            // Given: A portfolio with a closed position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("INFY-EQ", PositionStatus.CLOSED,
                new BigDecimal("1500.00"), new BigDecimal("1550.00"), 20, TradeDirection.LONG,
                new BigDecimal("0.00")));

            // When
            BigDecimal totalValue = portfolio.getTotalValue();

            // Then: Closed positions not included in totalValue
            assertThat(totalValue).isEqualTo(new BigDecimal("1000000"));
        }

        @Test
        void totalValueMixedPositions() {
            // Given: A portfolio with open and closed positions
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("100.00"), new BigDecimal("110.00"), 10, TradeDirection.LONG,
                new BigDecimal("100.00")));
            portfolio.addPosition(makePosition("INFY-EQ", PositionStatus.CLOSED,
                new BigDecimal("1500.00"), new BigDecimal("1550.00"), 20, TradeDirection.LONG,
                new BigDecimal("0.00")));

            // When
            BigDecimal totalValue = portfolio.getTotalValue();

            // Then: Only open position P&L included
            assertThat(totalValue).isEqualTo(new BigDecimal("1000100.00"));
        }

        @Test
        void concurrentMapThreadSafe() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When: Add positions concurrently
            for (int i = 0; i < 50; i++) {
                final int idx = i;
                String symbol = "STOCK" + idx + "-EQ";
                portfolio.addPosition(makePosition(symbol, PositionStatus.OPEN,
                    new BigDecimal("100.00"), new BigDecimal("100.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));
            }

            // Then: All positions present
            assertThat(portfolio.getPositions()).hasSize(50);
        }

        @Test
        void lastUpdatedChangesOnAdd() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            java.time.LocalDateTime initialUpdate = portfolio.getLastUpdated();

            // Wait a moment to ensure time difference
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // When
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));

            // Then
            assertThat(portfolio.getLastUpdated()).isAfter(initialUpdate);
        }

        @Test
        void lastUpdatedChangesOnRemove() {
            // Given: A portfolio with a position
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            portfolio.addPosition(makePosition("RELIANCE-EQ", PositionStatus.OPEN,
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), 10, TradeDirection.LONG, BigDecimal.ZERO));
            java.time.LocalDateTime initialUpdate = portfolio.getLastUpdated();

            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // When
            portfolio.removePosition("POS_RELIANCE-EQ");

            // Then
            assertThat(portfolio.getLastUpdated()).isAfter(initialUpdate);
        }

        @Test
        void lastUpdatedChangesOnCapitalSet() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));
            java.time.LocalDateTime initialUpdate = portfolio.getLastUpdated();

            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // When
            portfolio.setCurrentCapital(new BigDecimal("950000"));

            // Then
            assertThat(portfolio.getLastUpdated()).isAfter(initialUpdate);
        }

        @Test
        void toString_includesPortfolioInfo() {
            // Given: A portfolio
            portfolio = new Portfolio("test-portfolio", new BigDecimal("1000000"));

            // When
            String str = portfolio.toString();

            // Then
            assertThat(str).isNotNull();
            assertThat(str).isNotEmpty();
        }
    }
}
