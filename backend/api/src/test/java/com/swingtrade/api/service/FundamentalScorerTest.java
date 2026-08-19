package com.swingtrade.api.service;

import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.strategy.TechnicalIndicators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FundamentalScorerTest {

    private static OhlcvCandle makeCandle(BigDecimal open, BigDecimal high, BigDecimal low,
                                                 BigDecimal close, long volume, LocalDate date) {
        return new OhlcvCandle("TEST", date, open, high, low, close, volume, close);
    }

    @Test
    void compute_bullish_fundamentals_returns_score_100() {
        // Setup: create mocks directly
        CandleStore candleStore = mock(CandleStore.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        FundamentalScorer scorer = new FundamentalScorer(candleStore, technicalIndicators);

        // candles[0] = latest (after reversal). Flat data for predictable calculations.
        // Historical (candles 50-59): close=10.0, volume=500000
        // Recent (candles 0-9): close=15.0, volume=1000000
        // Momentum: (15.0 - 10.0)/10.0 = 50% > 5% -> +25
        // Volume trend: 1000000/500000 = 2.0 > 1.2 -> +25
        // ATR mock: 0.3, price=15.0 -> ATR/Price=2.0% < 3% -> +25
        // SMA mock: 12.0, price=15.0 > 12.0 -> +25
        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(15), BigDecimal.valueOf(17),
                    BigDecimal.valueOf(13), BigDecimal.valueOf(15),
                    1000000L, LocalDate.now().minusDays(9 - i)));
        }
        for (int i = 10; i < 60; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10), BigDecimal.valueOf(12),
                    BigDecimal.valueOf(8), BigDecimal.valueOf(10),
                    500000L, LocalDate.now().minusDays(59 - i)));
        }
        when(candleStore.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        when(technicalIndicators.calculateATR(any(), eq(14))).thenReturn(0.3);
        when(technicalIndicators.calculateSMA(any(), eq(50))).thenReturn(12.0);

        // Act
        CompositeAnalysis.FundamentalScore result = scorer.compute("test");

        // Assert
        assertEquals(100, result.score());
        assertEquals("BULLISH", result.score() > 0 ? "BULLISH" : result.score() < 0 ? "BEARISH" : "NEUTRAL");
        assertTrue(result.factors().size() >= 4);
    }

    @Test
    void compute_bearish_fundamentals_returns_score_minus100() {
        // Setup: create mocks directly
        CandleStore candleStore = mock(CandleStore.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        FundamentalScorer scorer = new FundamentalScorer(candleStore, technicalIndicators);

        // candles[0] = latest. Flat data for predictable calculations.
        // Historical (candles 50-59): close=20.0, volume=500000
        // Recent (candles 0-9): close=10.0, volume=200000
        // Momentum: (10.0 - 20.0)/20.0 = -50% < -5% -> -25
        // Volume trend: 200000/500000 = 0.4 < 0.8 -> -25
        // ATR mock: 6.0, price=10.0 -> ATR/Price=60% > 5% -> -25
        // SMA mock: 15.0, price=10.0 < 15.0 -> -25
        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10), BigDecimal.valueOf(12),
                    BigDecimal.valueOf(8), BigDecimal.valueOf(10),
                    200000L, LocalDate.now().minusDays(9 - i)));
        }
        for (int i = 10; i < 60; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(20), BigDecimal.valueOf(22),
                    BigDecimal.valueOf(18), BigDecimal.valueOf(20),
                    500000L, LocalDate.now().minusDays(59 - i)));
        }
        when(candleStore.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        when(technicalIndicators.calculateATR(any(), eq(14))).thenReturn(6.0);
        when(technicalIndicators.calculateSMA(any(), eq(50))).thenReturn(15.0);

        // Act
        CompositeAnalysis.FundamentalScore result = scorer.compute("test");

        // Assert
        assertEquals(-100, result.score());
        assertEquals("BEARISH", result.score() < 0 ? "BEARISH" : result.score() > 0 ? "BULLISH" : "NEUTRAL");
    }

    @Test
    void compute_insufficient_data_returns_score_0() {
        // Setup: create mocks directly
        CandleStore candleStore = mock(CandleStore.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        FundamentalScorer scorer = new FundamentalScorer(candleStore, technicalIndicators);

        // Setup: only 20 candles, below threshold of 30
        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(102),
                    BigDecimal.valueOf(98),
                    BigDecimal.valueOf(101),
                    500000L,
                    LocalDate.now().minusDays(19 - i)
            ));
        }
        when(candleStore.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        // Act
        CompositeAnalysis.FundamentalScore result = scorer.compute("test");

        // Assert
        assertEquals(0, result.score());
        assertTrue(result.factors().contains("Insufficient data"));
    }

    @Test
    void compute_factors_are_not_empty() {
        // Setup: create mocks directly
        CandleStore candleStore = mock(CandleStore.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        FundamentalScorer scorer = new FundamentalScorer(candleStore, technicalIndicators);

        // Setup: 60 candles
        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(102 + i),
                    BigDecimal.valueOf(98 + i),
                    BigDecimal.valueOf(105 + i),
                    500000L,
                    LocalDate.now().minusDays(59 - i)
            ));
        }
        when(candleStore.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);
        when(technicalIndicators.calculateATR(anyList(), eq(14))).thenReturn(2.5);
        when(technicalIndicators.calculateSMA(anyList(), eq(50))).thenReturn(102.0);

        // Act
        CompositeAnalysis.FundamentalScore result = scorer.compute("test");

        // Assert
        assertFalse(result.factors().isEmpty());
        for (String factor : result.factors()) {
            assertFalse(factor.isBlank());
        }
    }
}
