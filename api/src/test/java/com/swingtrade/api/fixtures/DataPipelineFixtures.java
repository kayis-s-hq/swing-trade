package com.swingtrade.api.fixtures;

import com.swingtrade.data.entity.OhlcvCandleEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Test fixtures for Phase 3 E2E data pipeline tests.
 */
public class DataPipelineFixtures {

    public static OhlcvCandleEntity createValidCandle(String symbol, LocalDate date) {
        OhlcvCandleEntity candle = new OhlcvCandleEntity();
        candle.setSymbol(symbol);
        candle.setDate(date);
        candle.setOpenPrice(new BigDecimal("1000.00"));
        candle.setHighPrice(new BigDecimal("1020.00"));
        candle.setLowPrice(new BigDecimal("990.00"));
        candle.setClosePrice(new BigDecimal("1015.00"));
        candle.setVolume(1000000L);
        candle.setAdjClosePrice(new BigDecimal("1015.00"));
        return candle;
    }

    public static OhlcvCandleEntity createInvalidCandle(String symbol, LocalDate date) {
        OhlcvCandleEntity candle = new OhlcvCandleEntity();
        candle.setSymbol(symbol);
        candle.setDate(date);
        candle.setOpenPrice(new BigDecimal("1000.00"));
        candle.setHighPrice(new BigDecimal("900.00")); // Invalid: High < Open
        candle.setLowPrice(new BigDecimal("950.00"));
        candle.setClosePrice(new BigDecimal("980.00"));
        candle.setVolume(1000000L);
        candle.setAdjClosePrice(new BigDecimal("980.00"));
        return candle;
    }

    public static List<LocalDate> generateTradingDays(LocalDate start, LocalDate end) {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            int dayOfWeek = current.getDayOfWeek().getValue();
            if (dayOfWeek <= 5) { // Monday = 1, Friday = 5
                tradingDays.add(current);
            }
            current = current.plusDays(1);
        }

        return tradingDays;
    }
}
