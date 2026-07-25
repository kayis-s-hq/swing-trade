package com.swingtrade.api.service;

import com.swingtrade.api.dto.CompositeAnalysis;
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

class TechnicalAnalysisServiceTest {

    private static OhlcvCandle makeCandle(BigDecimal open, BigDecimal high, BigDecimal low,
                                                 BigDecimal close, long volume, LocalDate date) {
        return new OhlcvCandle("TEST", date, open, high, low, close, volume, close);
    }

    @Test
    void compute_bullish_data_returns_buy_score_100() {
        // Setup: 60 candles with bullish price action
        CandleStore candleStore = mock(CandleStore.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        TechnicalAnalysisService service = new TechnicalAnalysisService(candleStore, technicalIndicators);

        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10 + i),
                    BigDecimal.valueOf(12 + i),
                    BigDecimal.valueOf(8 + i),
                    BigDecimal.valueOf(15 + i),
                    500000L + i * 1000L,
                    LocalDate.now().minusDays(59 - i)
            ));
        }
        when(candleStore.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        // Mock indicator returns for bullish scenario
        when(technicalIndicators.calculateEMA(anyList(), eq(20))).thenReturn(12.0);
        when(technicalIndicators.calculateEMA(anyList(), eq(50))).thenReturn(10.0);
        when(technicalIndicators.calculateRSI(anyList(), eq(14))).thenReturn(57.5);
        when(technicalIndicators.calculateVolumeMA(anyList(), eq(20))).thenReturn(100.0);
        when(technicalIndicators.calculateATR(anyList(), eq(14))).thenReturn(1.0);

        // Act
        CompositeAnalysis.TechnicalScore result = service.compute("test");

        // Assert
        assertEquals(100, result.score());
        assertEquals("BUY", result.signal());
        assertEquals(1.0, result.confidence());
        assertEquals(4, result.indicators().size());
        assertTrue(result.indicators().stream().anyMatch(s -> s.contains("bullish")));
    }

    @Test
    void compute_bearish_data_returns_sell_score_minus100() {
        // Setup: 60 candles with bearish price action
        CandleStore candleStore = mock(CandleStore.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        TechnicalAnalysisService service = new TechnicalAnalysisService(candleStore, technicalIndicators);

        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10 + i),
                    BigDecimal.valueOf(12 + i),
                    BigDecimal.valueOf(8 + i),
                    BigDecimal.valueOf(15 + i),
                    200000L + i * 1000L,
                    LocalDate.now().minusDays(59 - i)
            ));
        }
        when(candleStore.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        // Mock indicator returns for bearish scenario
        when(technicalIndicators.calculateEMA(anyList(), eq(20))).thenReturn(20.0);
        when(technicalIndicators.calculateEMA(anyList(), eq(50))).thenReturn(25.0);
        when(technicalIndicators.calculateRSI(anyList(), eq(14))).thenReturn(40.0);
        when(technicalIndicators.calculateVolumeMA(anyList(), eq(20))).thenReturn(100.0);
        when(technicalIndicators.calculateATR(anyList(), eq(14))).thenReturn(2.0);

        // Act
        CompositeAnalysis.TechnicalScore result = service.compute("test");

        // Assert
        assertEquals(-100, result.score());
        assertEquals("SELL", result.signal());
        assertEquals(1.0, result.confidence());
        assertEquals(4, result.indicators().size());
    }

    @Test
    void compute_insufficient_candles_returns_hold_score_0() {
        // Setup: only 30 candles, below StrategyParams.MIN_CANDLES (50)
        CandleStore candleStore = mock(CandleStore.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        TechnicalAnalysisService service = new TechnicalAnalysisService(candleStore, technicalIndicators);

        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(12),
                    BigDecimal.valueOf(8),
                    BigDecimal.valueOf(11),
                    500000L,
                    LocalDate.now().minusDays(29 - i)
            ));
        }
        when(candleStore.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        // Act
        CompositeAnalysis.TechnicalScore result = service.compute("test");

        // Assert
        assertEquals(0, result.score());
        assertEquals("HOLD", result.signal());
        assertEquals(0.0, result.confidence());
        assertTrue(result.indicators().isEmpty());
    }

    @Test
    void compute_mixed_indicators_returns_sum_of_factors() {
        // Setup: 60 candles
        CandleStore candleStore = mock(CandleStore.class);
        TechnicalIndicators technicalIndicators = mock(TechnicalIndicators.class);
        TechnicalAnalysisService service = new TechnicalAnalysisService(candleStore, technicalIndicators);

        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            candles.add(makeCandle(
                    BigDecimal.valueOf(10 + i),
                    BigDecimal.valueOf(12 + i),
                    BigDecimal.valueOf(8 + i),
                    BigDecimal.valueOf(15 + i),
                    500000L + i * 1000L,
                    LocalDate.now().minusDays(59 - i)
            ));
        }
        when(candleStore.findAllBySymbolOrderByDateDesc("TEST")).thenReturn(candles);

        // Mixed: EMA bullish (+25), RSI bearish (-25), volume bullish (+25), 52W high bearish (-25)
        // Expected: 0 -> HOLD
        when(technicalIndicators.calculateEMA(anyList(), eq(20))).thenReturn(12.0);
        when(technicalIndicators.calculateEMA(anyList(), eq(50))).thenReturn(10.0);
        when(technicalIndicators.calculateRSI(anyList(), eq(14))).thenReturn(45.0);
        when(technicalIndicators.calculateVolumeMA(anyList(), eq(20))).thenReturn(100.0);
        when(technicalIndicators.calculateATR(anyList(), eq(14))).thenReturn(1.0);

        // Act
        CompositeAnalysis.TechnicalScore result = service.compute("test");

        // Assert
        assertEquals(0, result.score());
        assertEquals("HOLD", result.signal());
        assertEquals(4, result.indicators().size());
    }
}