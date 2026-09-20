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

package com.swingtrade.api.service;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.PositionStore;
import com.swingtrade.strategy.ExitReason;
import com.swingtrade.strategy.PriceActionSignalEngine;
import com.swingtrade.strategy.SignalResult;
import com.swingtrade.api.dto.PositionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignalPipelineTest {

    private static final String SYMBOL = "RELIANCE";

    @Mock
    private CandleStore candleStore;

    @Mock
    private PriceActionSignalEngine priceActionEngine;

    @Mock
    private SignalPersistenceService persistenceService;

    @Mock
    private SentimentGate sentimentGate;

    @Mock
    private PositionStore positionStore;

    @Mock
    private PositionService positionService;

    private SignalPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new SignalPipeline(candleStore, priceActionEngine, persistenceService, sentimentGate, positionStore, positionService);
    }

    private List<OhlcvCandle> descendingCandles(int count) {
        List<OhlcvCandle> candles = new ArrayList<>();
        LocalDate date = LocalDate.now();
        for (int i = 0; i < count; i++) {
            candles.add(OhlcvCandle.of(
                    SYMBOL, date.minusDays(i),
                    BigDecimal.valueOf(100), BigDecimal.valueOf(105),
                    BigDecimal.valueOf(99), BigDecimal.valueOf(102), 1000000L));
        }
        return candles;
    }

    @Nested
    @DisplayName("generatePrimarySignal")
    class GeneratePrimarySignal {

        @Test
        @DisplayName("persists the engine's detailed rule-by-rule reasoning, distinct from the indicators string")
        void persistsDetailedReasoningDistinctFromIndicators() {
            List<OhlcvCandle> candles = descendingCandles(60);
            LocalDate latestDate = candles.get(0).date();

            when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), eq(100))).thenReturn(candles);

            String detailedReasoning = "Entry rules failed (2 of 4 passed): "
                    + "RSI between 50-65 (rsi=74.39); "
                    + "Volume > 1.5x VolumeMA20 (volume=1000.00, threshold=1500.00)";
            SignalResult holdResult = new SignalResult(
                    SYMBOL, latestDate, Signal.SignalType.HOLD,
                    new BigDecimal("74.39"), new BigDecimal("101.5"), new BigDecimal("100.2"), new BigDecimal("2.1"), detailedReasoning);

            when(priceActionEngine.analyze(eq(SYMBOL), anyList())).thenReturn(holdResult);

            Signal savedSignal = Signal.create(SYMBOL, latestDate, Signal.SignalType.HOLD, BigDecimal.ONE, "Wait for confirmation");
            when(persistenceService.buildAndSaveWithWarning(
                    anyString(), any(), any(), any(), anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenReturn(savedSignal);

            pipeline.generatePrimarySignal(SYMBOL);

            ArgumentCaptor<String> reasoningCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> indicatorsCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<BigDecimal> confidenceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(persistenceService).buildAndSaveWithWarning(
                    eq(SYMBOL), eq(latestDate), eq(Signal.SignalType.HOLD), confidenceCaptor.capture(),
                    reasoningCaptor.capture(), indicatorsCaptor.capture(), any(),
                    eq(com.swingtrade.data.entity.SignalEntity.WARNING_NONE), any(), any());

            String persistedReasoning = reasoningCaptor.getValue();
            String persistedIndicators = indicatorsCaptor.getValue();
            assertThat(confidenceCaptor.getValue()).isBetween(BigDecimal.ZERO, BigDecimal.ONE)
                .isNotEqualTo(BigDecimal.ONE);

            assertThat(persistedReasoning).isEqualTo(detailedReasoning);
            assertThat(persistedReasoning).isNotEqualTo(persistedIndicators);
            assertThat(persistedReasoning).contains("of 4 passed");
        }
    }

    @Nested
    @DisplayName("SellSignalPositionLookup")
    class SellSignalPositionLookup {

        private List<OhlcvCandle> candles;
        private LocalDate latestDate;

        @BeforeEach
        void setUpCandlesAndPersistence() {
            candles = descendingCandles(60);
            latestDate = candles.get(0).date();
            when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), eq(100))).thenReturn(candles);
        }

        private void stubSavedSignal(Signal.SignalType type) {
            Signal savedSignal = Signal.create(SYMBOL, latestDate, type, BigDecimal.ONE, "reasoning");
            when(persistenceService.buildAndSaveWithWarning(
                    anyString(), any(), any(), any(), anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenReturn(savedSignal);
        }

        @Test
        void sellSignal_symbolNotHeld_persistsSellSignal() {
            SignalResult sellResult = new SignalResult(
                    SYMBOL, latestDate, Signal.SignalType.SELL,
                    new BigDecimal("45.0"), new BigDecimal("98.0"), new BigDecimal("100.0"), new BigDecimal("2.1"), "Exit rule triggered (1 of 3): RSI < 50 (rsi=45.00)");
            when(priceActionEngine.analyze(eq(SYMBOL), anyList())).thenReturn(sellResult);
            when(positionStore.findBySymbol(SYMBOL)).thenReturn(Optional.empty());
            stubSavedSignal(Signal.SignalType.SELL);

            pipeline.generatePrimarySignal(SYMBOL);

            verify(positionStore).findBySymbol(SYMBOL);
            verify(persistenceService).buildAndSaveWithWarning(
                    eq(SYMBOL), any(), eq(Signal.SignalType.SELL), any(), anyString(), anyString(), any(), anyString(), any(), any());
        }

        @Test
        void sellSignal_symbolHeld_lookupDetectsHeldPosition_stillPersistsSignal() {
            SignalResult sellResult = new SignalResult(
                    SYMBOL, latestDate, Signal.SignalType.SELL,
                    new BigDecimal("45.0"), new BigDecimal("98.0"), new BigDecimal("100.0"), new BigDecimal("2.1"), "Exit rule triggered (1 of 3): RSI < 50 (rsi=45.00)");
            when(priceActionEngine.analyze(eq(SYMBOL), anyList())).thenReturn(sellResult);
            when(positionStore.findBySymbol(SYMBOL)).thenReturn(
                    Optional.of(Position.createWithRisk(SYMBOL, BigDecimal.valueOf(100), latestDate, 10, BigDecimal.valueOf(2.0), "Entry on breakout")));
            stubSavedSignal(Signal.SignalType.SELL);

            pipeline.generatePrimarySignal(SYMBOL);

            verify(positionStore).findBySymbol(SYMBOL);
            verify(persistenceService).buildAndSaveWithWarning(
                    eq(SYMBOL), any(), eq(Signal.SignalType.SELL), any(), anyString(), anyString(), any(), anyString(), any(), any());
        }

        @Test
        void buySignal_doesNotConsultPositionStore() {
            SignalResult buyResult = new SignalResult(
                    SYMBOL, latestDate, Signal.SignalType.BUY,
                    new BigDecimal("58.0"), new BigDecimal("102.0"), new BigDecimal("100.0"), new BigDecimal("2.1"), "All entry rules passed: ...");
            when(priceActionEngine.analyze(eq(SYMBOL), anyList())).thenReturn(buyResult);
            stubSavedSignal(Signal.SignalType.BUY);

            pipeline.generatePrimarySignal(SYMBOL);

            verifyNoInteractions(positionStore);
        }

        @Test
        void holdSignal_doesNotConsultPositionStore() {
            SignalResult holdResult = new SignalResult(
                    SYMBOL, latestDate, Signal.SignalType.HOLD,
                    new BigDecimal("58.0"), new BigDecimal("102.0"), new BigDecimal("100.0"), new BigDecimal("2.1"), "Entry rules failed (2 of 4 passed): ...");
            when(priceActionEngine.analyze(eq(SYMBOL), anyList())).thenReturn(holdResult);
            stubSavedSignal(Signal.SignalType.HOLD);

            pipeline.generatePrimarySignal(SYMBOL);

            verifyNoInteractions(positionStore);
        }
    }

    @Nested
    @DisplayName("SellSignalBrokerCloseOut")
    class SellSignalBrokerCloseOut {

        private List<OhlcvCandle> candles;
        private LocalDate latestDate;

        @BeforeEach
        void setUpCandlesAndPersistence() {
            candles = descendingCandles(60);
            latestDate = candles.get(0).date();
            when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), eq(100))).thenReturn(candles);
        }

        private void stubSavedSignal(Signal.SignalType type) {
            Signal savedSignal = Signal.create(SYMBOL, latestDate, type, BigDecimal.ONE, "reasoning");
            when(persistenceService.buildAndSaveWithWarning(
                    anyString(), any(), any(), any(), anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenReturn(savedSignal);
        }

        private SignalResult sellResult() {
            return new SignalResult(
                    SYMBOL, latestDate, Signal.SignalType.SELL,
                    new BigDecimal("45.0"), new BigDecimal("98.0"), new BigDecimal("100.0"), new BigDecimal("2.1"), "Exit rule triggered (1 of 3): RSI < 50 (rsi=45.00)");
        }

        @Test
        void sellSignal_symbolHeld_closesPositionViaPositionService() {
            when(priceActionEngine.analyze(eq(SYMBOL), anyList())).thenReturn(sellResult());
            when(positionStore.findBySymbol(SYMBOL)).thenReturn(
                    Optional.of(Position.createWithRisk(SYMBOL, BigDecimal.valueOf(100), latestDate, 10, BigDecimal.valueOf(2.0), "Entry on breakout")));
            when(positionService.closePosition(SYMBOL, ExitReason.SIGNAL_EXIT.name())).thenReturn(new PositionResponse());
            stubSavedSignal(Signal.SignalType.SELL);

            pipeline.generatePrimarySignal(SYMBOL);

            verify(positionService).closePosition(SYMBOL, "SIGNAL_EXIT");
        }

        @Test
        void sellSignal_symbolNotHeld_neverCallsPositionServiceClose() {
            when(priceActionEngine.analyze(eq(SYMBOL), anyList())).thenReturn(sellResult());
            when(positionStore.findBySymbol(SYMBOL)).thenReturn(Optional.empty());
            stubSavedSignal(Signal.SignalType.SELL);

            pipeline.generatePrimarySignal(SYMBOL);

            verifyNoInteractions(positionService);
        }

        @Test
        void sellSignal_closeThrows_stillPersistsSignal_doesNotPropagate() {
            when(priceActionEngine.analyze(eq(SYMBOL), anyList())).thenReturn(sellResult());
            when(positionStore.findBySymbol(SYMBOL)).thenReturn(
                    Optional.of(Position.createWithRisk(SYMBOL, BigDecimal.valueOf(100), latestDate, 10, BigDecimal.valueOf(2.0), "Entry on breakout")));
            when(positionService.closePosition(SYMBOL, ExitReason.SIGNAL_EXIT.name())).thenThrow(new RuntimeException("db down"));
            stubSavedSignal(Signal.SignalType.SELL);

            assertThatCode(() -> pipeline.generatePrimarySignal(SYMBOL)).doesNotThrowAnyException();

            verify(persistenceService).buildAndSaveWithWarning(
                    eq(SYMBOL), any(), eq(Signal.SignalType.SELL), any(), anyString(), anyString(), any(), anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("generatePriceActionSignal - SELL closes held position")
    class GeneratePriceActionSignalSellClose {

        private final LocalDate today = LocalDate.now();

        private SignalResult sellResult() {
            return new SignalResult(
                    SYMBOL, today, Signal.SignalType.SELL,
                    new BigDecimal("45.0"), new BigDecimal("98.0"), new BigDecimal("100.0"), new BigDecimal("2.1"), "Exit rule triggered (1 of 3): RSI < 50 (rsi=45.00)");
        }

        private void stubSavedSignal() {
            Signal savedSignal = Signal.create(SYMBOL, today, Signal.SignalType.SELL, BigDecimal.ONE, "reasoning");
            when(persistenceService.buildAndSave(any(), any(), any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(savedSignal);
        }

        // Bug: /api/signals/generate-all (the dashboard's bulk trigger) routes here,
        // via generatePriceActionSignal - which historically had no position-close
        // logic at all, unlike generatePrimarySignal. A SELL signal from this path
        // was persisted but never acted on a held position.
        @Test
        void sellSignal_symbolHeld_closesPositionViaPositionService() {
            when(priceActionEngine.generateSignal(SYMBOL)).thenReturn(sellResult());
            when(positionStore.findBySymbol(SYMBOL)).thenReturn(
                    Optional.of(Position.createWithRisk(SYMBOL, BigDecimal.valueOf(100), today, 10, BigDecimal.valueOf(2.0), "Entry on breakout")));
            when(positionService.closePosition(SYMBOL, ExitReason.SIGNAL_EXIT.name())).thenReturn(new PositionResponse());
            stubSavedSignal();

            pipeline.generatePriceActionSignal(SYMBOL);

            verify(positionService).closePosition(SYMBOL, "SIGNAL_EXIT");
        }

        @Test
        void sellSignal_symbolNotHeld_neverCallsPositionServiceClose() {
            when(priceActionEngine.generateSignal(SYMBOL)).thenReturn(sellResult());
            when(positionStore.findBySymbol(SYMBOL)).thenReturn(Optional.empty());
            stubSavedSignal();

            pipeline.generatePriceActionSignal(SYMBOL);

            verifyNoInteractions(positionService);
        }
    }
}
