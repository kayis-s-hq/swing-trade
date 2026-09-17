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

import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.data.repository.StrategyExperimentLogRepository;
import com.swingtrade.data.service.MarketCalendar;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.GateEvaluator;
import com.swingtrade.strategy.RuleOutcome;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyDecision;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the plan §7.3 live-vs-backtest parity check.
 */
@ExtendWith(MockitoExtension.class)
class ParityCheckServiceTest {

    private static final String SYMBOL = "RELIANCE";
    private static final String VARIANT_ID = "PULLBACK_A";

    // A Saturday so lastCompletedTradingDay("today") walks back to the Friday before it -
    // avoids depending on holiday-calendar data for "yesterday" itself.
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 18);

    @Mock
    private StrategyConfigStore strategyConfigStore;
    @Mock
    private StrategyTypeRegistry strategyTypeRegistry;
    @Mock
    private CandleStore candleStore;
    @Mock
    private SignalStore signalStore;
    @Mock
    private GateEvaluator gateEvaluator;
    @Mock
    private MarketCalendar marketCalendar;
    @Mock
    private StrategyExperimentLogRepository experimentLogRepository;
    @Mock
    private DiscordNotificationService discordService;
    @Mock
    private SignalStrategy strategy;

    private ParityCheckService service;

    @BeforeEach
    void setUp() {
        service = new ParityCheckService(strategyConfigStore, strategyTypeRegistry, candleStore, signalStore,
            gateEvaluator, marketCalendar, experimentLogRepository, discordService);

        when(marketCalendar.isNseTradingSession(TARGET_DATE)).thenReturn(true);

        StrategyConfig variant = new StrategyConfig(1L, VARIANT_ID, 1, "PULLBACK", Map.of(), Map.of(),
            "hash-a", StrategyMode.SHADOW, BigDecimal.valueOf(100000), true, null, null, null);
        when(strategyConfigStore.findAllCurrent()).thenReturn(List.of(variant));
        when(strategyTypeRegistry.findByType("PULLBACK")).thenReturn(Optional.of(strategy));
        when(candleStore.findAllDistinctSymbols()).thenReturn(List.of(SYMBOL));
        when(candleStore.findAllBySymbolOrderByDateDesc(SYMBOL)).thenReturn(descendingCandlesEndingAt(TARGET_DATE, 60));
    }

    private List<OhlcvCandle> descendingCandlesEndingAt(LocalDate endDate, int count) {
        List<OhlcvCandle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candles.add(OhlcvCandle.of(
                SYMBOL, endDate.minusDays(i),
                BigDecimal.valueOf(100), BigDecimal.valueOf(105),
                BigDecimal.valueOf(99), BigDecimal.valueOf(102), 1_000_000L));
        }
        return candles;
    }

    private StrategyDecision buyDecision() {
        return new StrategyDecision(Signal.SignalType.BUY, BigDecimal.valueOf(0.8),
            List.of(new RuleOutcome("trendUp", true, BigDecimal.ONE, true, "trend is up")),
            null, null, "BUY reasoning");
    }

    @Test
    @DisplayName("re-evaluated decision differs from recorded -> Discord alert fires naming variant/symbol")
    void mismatchTriggersDiscordAlert() {
        // Recorded (live) decision for the day was HOLD; the re-evaluation says BUY.
        when(signalStore.findByDateAndStrategy(TARGET_DATE, VARIANT_ID)).thenReturn(List.of());
        when(strategy.evaluateEntry(any(), anyInt(), any())).thenReturn(buyDecision());
        when(discordService.sendEmbed(any(), any(), anyInt())).thenReturn(true);

        List<ParityCheckService.ParityMismatch> mismatches = service.runParityCheck(TODAY);

        assertThat(mismatches).hasSize(1);
        ParityCheckService.ParityMismatch mismatch = mismatches.get(0);
        assertThat(mismatch.variantId()).isEqualTo(VARIANT_ID);
        assertThat(mismatch.symbol()).isEqualTo(SYMBOL);
        assertThat(mismatch.recordedType()).isEqualTo(Signal.SignalType.HOLD);
        assertThat(mismatch.reevaluatedType()).isEqualTo(Signal.SignalType.BUY);

        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(discordService).sendEmbed(any(), descriptionCaptor.capture(), anyInt());
        assertThat(descriptionCaptor.getValue())
            .contains(VARIANT_ID)
            .contains(SYMBOL);

        verify(experimentLogRepository).save(any());
    }

    @Test
    @DisplayName("re-evaluated decision matches recorded -> no Discord alert")
    void noMismatchDoesNotAlert() {
        // Recorded (live) decision matches what re-evaluation will produce: BUY == BUY.
        Signal recordedBuy = Signal.create(SYMBOL, TARGET_DATE, Signal.SignalType.BUY, BigDecimal.ONE, "r");
        when(signalStore.findByDateAndStrategy(TARGET_DATE, VARIANT_ID)).thenReturn(List.of(recordedBuy));
        when(strategy.evaluateEntry(any(), anyInt(), any())).thenReturn(buyDecision());

        List<ParityCheckService.ParityMismatch> mismatches = service.runParityCheck(TODAY);

        assertThat(mismatches).isEmpty();
        verify(discordService, never()).sendEmbed(any(), any(), anyInt());
        verify(experimentLogRepository).save(any());
    }
}
