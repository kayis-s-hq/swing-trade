package com.swingtrade.api.service;

import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
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

    private static OhlcvCandleEntity makeCandle(BigDecimal open, BigDecimal high, BigDecimal low,
                                                 BigDecimal close, long volume, LocalDate date) {
        OhlcvCandleEntity c = new OhlcvCandleEntity();
        c.setOpenPrice(open);
        c.setHighPrice(high);
        c.setLowPrice(low);
        c.setClosePrice(close);
        c.setVolume(volume);
        c.setDate(date);
        return c;
    }

    @Test
    void compute_bullish_fundamentals_returns_score_100() {
        // Setup: create mocks directly
        OhlcvCandleRepository candleRepository = mock(OhlcvCandleRepository.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        FundamentalScorer scorer = new FundamentalScorer(candleRepository, technicalIndicators);

        // Setup: 60 candles with increasing volume trend
        // Historical (first 50): volume=500000, Recent (last 10): volume=1000000
        // ratio=2.0 > 1.2 -> +25
        // ATR mock: 0.4, price=15.9 -> ATR/Price=2.5% < 3% -> +25
        // Momentum: candle[29]=12.9, candle[59]=15.9 -> 23.3% > 5% -> +25
        // SMA mock: 12.0, price=15.9 > 12.0 -> +25
        // Total: 25+25+25+25 = 100
        List<OhlcvCandleEntity> candles = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10 + i / 10.0),
                    BigDecimal.valueOf(10 + i / 10.0 + 2),
                    BigDecimal.valueOf(10 + i / 10.0 - 2),
                    BigDecimal.valueOf(10 + i / 10.0),
                    500000L,
                    LocalDate.now().minusDays(59 - i)
            ));
        }
        for (int i = 50; i < 60; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(15 + i / 10.0),
                    BigDecimal.valueOf(15 + i / 10.0 + 2),
                    BigDecimal.valueOf(15 + i / 10.0 - 2),
                    BigDecimal.valueOf(15 + i / 10.0),
                    1000000L,
                    LocalDate.now().minusDays(59 - i)
            ));
        }
        when(candleRepository.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        when(technicalIndicators.calculateATR(anyList(), eq(14))).thenReturn(0.4);
        when(technicalIndicators.calculateSMA(anyList(), eq(50))).thenReturn(12.0);

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
        OhlcvCandleRepository candleRepository = mock(OhlcvCandleRepository.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        FundamentalScorer scorer = new FundamentalScorer(candleRepository, technicalIndicators);

        // Setup: 60 candles with declining volume trend
        // Historical (first 50): volume=500000, Recent (last 10): volume=200000
        // ratio=0.4 < 0.8 -> -25
        // ATR mock: 6.0, price=10.9 -> ATR/Price=55% > 5% -> -25
        // Momentum: candle[29]=12.9, candle[59]=10.9 -> -15.5% < -5% -> -25
        // SMA mock: 15.0, price=10.9 < 15.0 -> -25
        // Total: -25-25-25-25 = -100
        List<OhlcvCandleEntity> candles = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10 + i / 10.0),
                    BigDecimal.valueOf(10 + i / 10.0 + 2),
                    BigDecimal.valueOf(10 + i / 10.0 - 2),
                    BigDecimal.valueOf(10 + i / 10.0),
                    500000L,
                    LocalDate.now().minusDays(59 - i)
            ));
        }
        for (int i = 50; i < 60; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10 + i / 10.0),
                    BigDecimal.valueOf(10 + i / 10.0 + 2),
                    BigDecimal.valueOf(10 + i / 10.0 - 2),
                    BigDecimal.valueOf(10 + i / 10.0),
                    200000L,
                    LocalDate.now().minusDays(59 - i)
            ));
        }
        when(candleRepository.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        when(technicalIndicators.calculateATR(anyList(), eq(14))).thenReturn(6.0);
        when(technicalIndicators.calculateSMA(anyList(), eq(50))).thenReturn(15.0);

        // Act
        CompositeAnalysis.FundamentalScore result = scorer.compute("test");

        // Assert
        assertEquals(-100, result.score());
        assertEquals("BEARISH", result.score() < 0 ? "BEARISH" : result.score() > 0 ? "BULLISH" : "NEUTRAL");
    }

    @Test
    void compute_insufficient_data_returns_score_0() {
        // Setup: create mocks directly
        OhlcvCandleRepository candleRepository = mock(OhlcvCandleRepository.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        FundamentalScorer scorer = new FundamentalScorer(candleRepository, technicalIndicators);

        // Setup: only 20 candles, below threshold of 30
        List<OhlcvCandleEntity> candles = new ArrayList<>();
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
        when(candleRepository.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        // Act
        CompositeAnalysis.FundamentalScore result = scorer.compute("test");

        // Assert
        assertEquals(0, result.score());
        assertTrue(result.factors().contains("Insufficient data"));
    }

    @Test
    void compute_factors_are_not_empty() {
        // Setup: create mocks directly
        OhlcvCandleRepository candleRepository = mock(OhlcvCandleRepository.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        FundamentalScorer scorer = new FundamentalScorer(candleRepository, technicalIndicators);

        // Setup: 60 candles
        List<OhlcvCandleEntity> candles = new ArrayList<>();
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
        when(candleRepository.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);
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