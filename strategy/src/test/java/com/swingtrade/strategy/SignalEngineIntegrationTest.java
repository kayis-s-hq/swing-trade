/*
 * Copyright 2026 Swing Trade
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swingtrade.strategy;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.llm.service.SentimentAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for SignalEngine with SentimentAnalysisService.
 * Tests the actual signal filtering logic where sentiment checks suppress or flag signals.
 *
 * Test coverage:
 * - NEGATIVE sentiment suppresses BUY signals (signal is not saved)
 * - NEUTRAL sentiment flags BUY signals with WARNING_NEUTRAL_SENTIMENT
 * - POSITIVE sentiment allows BUY signals to save normally
 * - Sentiment analysis exceptions allow signals to save anyway (graceful degradation)
 */
@DisplayName("SignalEngine Integration Tests")
@ExtendWith(MockitoExtension.class)
class SignalEngineIntegrationTest {

    private SignalEngine signalEngine;

    @Mock
    private OhlcvCandleRepository candleRepository;

    @Mock
    private SignalRepository signalRepository;

    @Mock
    private SwingTradingStrategy strategy;

    @Mock
    private SentimentAnalysisService sentimentAnalysisService;

    @BeforeEach
    void setUp() {
        // Create real SignalEngine with mocked dependencies
        signalEngine = new SignalEngine(
                candleRepository,
                signalRepository,
                strategy,
                sentimentAnalysisService
        );
    }

    @Test
    @DisplayName("testNegativeSentimentSuppressesBUYSignal")
    void testNegativeSentimentSuppressesBUYSignal() {
        // Given: BUY signal from strategy
        String symbol = "RELIANCE.NS";
        LocalDate date = LocalDate.of(2026, 3, 22);

        // Mock candles
        List<OhlcvCandleEntity> candles = createMockCandles(symbol, date, 100);
        when(candleRepository.findTopBySymbolOrderByDateDesc(eq(symbol), any(PageRequest.class)))
                .thenReturn(candles);

        // Mock no existing signal
        when(signalRepository.findBySymbolAndDate(symbol, date))
                .thenReturn(new ArrayList<>());

        // Mock strategy to return BUY signal
        Signal buySignal = createMockSignal(Signal.SignalType.BUY);
        when(strategy.analyze(anyList()))
                .thenReturn(buySignal);

        // Mock sentiment to return NEGATIVE
        SentimentResult negativeSentiment = createMockSentimentResult(
                symbol,
                date,
                SentimentResult.SentimentScore.NEGATIVE,
                "Negative news detected"
        );
        when(sentimentAnalysisService.analyzeStockSentiment(symbol, date))
                .thenReturn(negativeSentiment);

        // When: generating signals
        signalEngine.generateSignalsForSymbol(symbol);

        // Then: signal should NOT be saved (suppressed)
        verify(signalRepository, never()).save(any(SignalEntity.class));
    }

    @Test
    @DisplayName("testNeutralSentimentFlagsBUYSignal")
    void testNeutralSentimentFlagsBUYSignal() {
        // Given: BUY signal from strategy
        String symbol = "INFY.NS";
        LocalDate date = LocalDate.of(2026, 3, 22);

        // Mock candles
        List<OhlcvCandleEntity> candles = createMockCandles(symbol, date, 100);
        when(candleRepository.findTopBySymbolOrderByDateDesc(eq(symbol), any(PageRequest.class)))
                .thenReturn(candles);

        // Mock no existing signal
        when(signalRepository.findBySymbolAndDate(symbol, date))
                .thenReturn(new ArrayList<>());

        // Mock strategy to return BUY signal
        Signal buySignal = createMockSignal(Signal.SignalType.BUY);
        when(strategy.analyze(anyList()))
                .thenReturn(buySignal);

        // Mock sentiment to return NEUTRAL
        SentimentResult neutralSentiment = createMockSentimentResult(
                symbol,
                date,
                SentimentResult.SentimentScore.NEUTRAL,
                "Mixed sentiment signals"
        );
        when(sentimentAnalysisService.analyzeStockSentiment(symbol, date))
                .thenReturn(neutralSentiment);

        // When: generating signals
        signalEngine.generateSignalsForSymbol(symbol);

        // Then: signal SHOULD be saved but with WARNING_NEUTRAL_SENTIMENT flag
        ArgumentCaptor<SignalEntity> captor = ArgumentCaptor.forClass(SignalEntity.class);
        verify(signalRepository, times(1)).save(captor.capture());

        SignalEntity savedSignal = captor.getValue();
        assertThat(savedSignal).isNotNull();
        assertThat(savedSignal.getSymbol()).isEqualTo(symbol);
        assertThat(savedSignal.getWarningFlag()).isEqualTo(SignalEntity.WARNING_NEUTRAL_SENTIMENT);
    }

    @Test
    @DisplayName("testPositiveSentimentAllowsBUYSignal")
    void testPositiveSentimentAllowsBUYSignal() {
        // Given: BUY signal from strategy
        String symbol = "TCS.NS";
        LocalDate date = LocalDate.of(2026, 3, 22);

        // Mock candles
        List<OhlcvCandleEntity> candles = createMockCandles(symbol, date, 100);
        when(candleRepository.findTopBySymbolOrderByDateDesc(eq(symbol), any(PageRequest.class)))
                .thenReturn(candles);

        // Mock no existing signal
        when(signalRepository.findBySymbolAndDate(symbol, date))
                .thenReturn(new ArrayList<>());

        // Mock strategy to return BUY signal
        Signal buySignal = createMockSignal(Signal.SignalType.BUY);
        when(strategy.analyze(anyList()))
                .thenReturn(buySignal);

        // Mock sentiment to return POSITIVE
        SentimentResult positiveSentiment = createMockSentimentResult(
                symbol,
                date,
                SentimentResult.SentimentScore.POSITIVE,
                "Positive outlook confirmed"
        );
        when(sentimentAnalysisService.analyzeStockSentiment(symbol, date))
                .thenReturn(positiveSentiment);

        // When: generating signals
        signalEngine.generateSignalsForSymbol(symbol);

        // Then: signal SHOULD be saved with WARNING_NONE flag
        ArgumentCaptor<SignalEntity> captor = ArgumentCaptor.forClass(SignalEntity.class);
        verify(signalRepository, times(1)).save(captor.capture());

        SignalEntity savedSignal = captor.getValue();
        assertThat(savedSignal).isNotNull();
        assertThat(savedSignal.getSymbol()).isEqualTo(symbol);
        assertThat(savedSignal.getWarningFlag()).isEqualTo(SignalEntity.WARNING_NONE);
    }

    @Test
    @DisplayName("testSentimentCheckExceptionAllowsSignal")
    void testSentimentCheckExceptionAllowsSignal() {
        // Given: BUY signal from strategy
        String symbol = "HDFCBANK.NS";
        LocalDate date = LocalDate.of(2026, 3, 22);

        // Mock candles
        List<OhlcvCandleEntity> candles = createMockCandles(symbol, date, 100);
        when(candleRepository.findTopBySymbolOrderByDateDesc(eq(symbol), any(PageRequest.class)))
                .thenReturn(candles);

        // Mock no existing signal
        when(signalRepository.findBySymbolAndDate(symbol, date))
                .thenReturn(new ArrayList<>());

        // Mock strategy to return BUY signal
        Signal buySignal = createMockSignal(Signal.SignalType.BUY);
        when(strategy.analyze(anyList()))
                .thenReturn(buySignal);

        // Mock sentiment analysis to throw exception (e.g., LLM service unavailable)
        when(sentimentAnalysisService.analyzeStockSentiment(symbol, date))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        // When: generating signals
        signalEngine.generateSignalsForSymbol(symbol);

        // Then: signal SHOULD be saved anyway (graceful degradation with WARNING_NONE)
        ArgumentCaptor<SignalEntity> captor = ArgumentCaptor.forClass(SignalEntity.class);
        verify(signalRepository, times(1)).save(captor.capture());

        SignalEntity savedSignal = captor.getValue();
        assertThat(savedSignal).isNotNull();
        assertThat(savedSignal.getSymbol()).isEqualTo(symbol);
        assertThat(savedSignal.getWarningFlag()).isEqualTo(SignalEntity.WARNING_NONE);
    }

    // ===== Helper Methods =====

    /**
     * Creates mock OHLCV candles for testing.
     * Generates 100 candles to meet the minimum requirement for EMA calculation.
     */
    private List<OhlcvCandleEntity> createMockCandles(String symbol, LocalDate date, int count) {
        List<OhlcvCandleEntity> candles = new ArrayList<>();

        for (int i = count; i > 0; i--) {
            OhlcvCandleEntity candle = new OhlcvCandleEntity();
            candle.setSymbol(symbol);
            candle.setDate(date.minusDays(count - i));
            candle.setOpenPrice(BigDecimal.valueOf(100 + i * 0.5));
            candle.setHighPrice(BigDecimal.valueOf(102 + i * 0.5));
            candle.setLowPrice(BigDecimal.valueOf(98 + i * 0.5));
            candle.setClosePrice(BigDecimal.valueOf(101 + i * 0.5));
            candle.setVolume(1000000L);
            candle.setAdjClosePrice(BigDecimal.valueOf(101 + i * 0.5));

            candles.add(candle);
        }

        return candles;
    }

    /**
     * Creates a mock trading signal.
     */
    private Signal createMockSignal(Signal.SignalType type) {
        return Signal.create(
                "RELIANCE.NS",
                LocalDate.of(2026, 3, 22),
                type,
                BigDecimal.valueOf(0.85),
                "Test signal"
        );
    }

    /**
     * Creates a mock sentiment result with specified sentiment score.
     */
    private SentimentResult createMockSentimentResult(
            String symbol,
            LocalDate date,
            SentimentResult.SentimentScore score,
            String summary) {

        return SentimentResult.create(
                symbol,
                date,
                score,
                summary,
                "Test content",
                0.85
        );
    }
}
