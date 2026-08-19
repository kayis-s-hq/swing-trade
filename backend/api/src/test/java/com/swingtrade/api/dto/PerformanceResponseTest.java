package com.swingtrade.api.dto;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceResponseTest {

    @Nested
    class OfFactory {

        @Test
        void of_populatesTotalPnL() {
            var response = PerformanceResponse.of(
                BigDecimal.valueOf(10), BigDecimal.valueOf(8),
                BigDecimal.valueOf(1.5), BigDecimal.valueOf(5),
                10, 6,
                BigDecimal.valueOf(50000), null, null, null, null
            );

            assertThat(response.getTotalPnL()).isEqualTo(BigDecimal.valueOf(50000));
        }

        @Test
        void of_populatesTotalValue() {
            var response = PerformanceResponse.of(
                BigDecimal.valueOf(10), BigDecimal.valueOf(8),
                BigDecimal.valueOf(1.5), BigDecimal.valueOf(5),
                10, 6,
                null, BigDecimal.valueOf(1050000), null, null, null
            );

            assertThat(response.getTotalValue()).isEqualTo(BigDecimal.valueOf(1050000));
        }

        @Test
        void of_populatesAverageWin() {
            var response = PerformanceResponse.of(
                BigDecimal.valueOf(10), BigDecimal.valueOf(8),
                BigDecimal.valueOf(1.5), BigDecimal.valueOf(5),
                10, 6,
                null, null, BigDecimal.valueOf(3000), null, null
            );

            assertThat(response.getAverageWin()).isEqualTo(BigDecimal.valueOf(3000));
        }

        @Test
        void of_populatesAverageLoss() {
            var response = PerformanceResponse.of(
                BigDecimal.valueOf(10), BigDecimal.valueOf(8),
                BigDecimal.valueOf(1.5), BigDecimal.valueOf(5),
                10, 6,
                null, null, null, BigDecimal.valueOf(2000), null
            );

            assertThat(response.getAverageLoss()).isEqualTo(BigDecimal.valueOf(2000));
        }

        @Test
        void of_populatesProfitFactor() {
            var response = PerformanceResponse.of(
                BigDecimal.valueOf(10), BigDecimal.valueOf(8),
                BigDecimal.valueOf(1.5), BigDecimal.valueOf(5),
                10, 6,
                null, null, null, null, BigDecimal.valueOf(1.5)
            );

            assertThat(response.getProfitFactor()).isEqualTo(BigDecimal.valueOf(1.5));
        }

        @Test
        void of_preservesOriginalFields() {
            var response = PerformanceResponse.of(
                BigDecimal.valueOf(12.5), BigDecimal.valueOf(10),
                BigDecimal.valueOf(2.0), BigDecimal.valueOf(8),
                20, 15,
                BigDecimal.valueOf(50000), BigDecimal.valueOf(1100000),
                BigDecimal.valueOf(3000), BigDecimal.valueOf(2000), BigDecimal.valueOf(1.5)
            );

            assertThat(response.getTotalReturn()).isEqualTo(BigDecimal.valueOf(12.5));
            assertThat(response.getAnnualizedReturn()).isEqualTo(BigDecimal.valueOf(10));
            assertThat(response.getSharpeRatio()).isEqualTo(BigDecimal.valueOf(2.0));
            assertThat(response.getMaxDrawdown()).isEqualTo(BigDecimal.valueOf(8));
            assertThat(response.getTotalTrades()).isEqualTo(20);
            assertThat(response.getWinningTrades()).isEqualTo(15);
            assertThat(response.getLosingTrades()).isEqualTo(5);
            assertThat(response.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(75));
        }
    }
}