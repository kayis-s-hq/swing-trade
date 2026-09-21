package com.swingtrade.api.service;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.PositionStore;
import com.swingtrade.strategy.ExitDecision;
import com.swingtrade.strategy.IndicatorKey;
import com.swingtrade.strategy.MarketContext;
import com.swingtrade.strategy.OpenPosition;
import com.swingtrade.strategy.ParamSchema;
import com.swingtrade.strategy.PriceActionSignalEngine;
import com.swingtrade.strategy.ResolvedStrategy;
import com.swingtrade.strategy.RuleOutcome;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyDecision;
import com.swingtrade.strategy.StrategyParamsView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SignalPipeline.generateConfiguredSignal for SignalStrategy variants")
class SignalPipelineConfiguredSignalStrategyTest {

    private static final String SYMBOL = "PILOT";

    private CandleStore candleStore;
    private SignalPersistenceService persistence;
    private PositionStore positionStore;
    private PositionService positionService;
    private SignalPipeline pipeline;

    @BeforeEach
    void setUp() {
        candleStore = mock(CandleStore.class);
        persistence = mock(SignalPersistenceService.class);
        positionStore = mock(PositionStore.class);
        positionService = mock(PositionService.class);
        pipeline = new SignalPipeline(candleStore, mock(PriceActionSignalEngine.class), persistence,
            mock(SentimentGate.class), positionStore, positionService);
    }

    @Test
    void buyDecisionIsPersistedWithProvenanceAndStrategyRisk() {
        when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), any(Integer.class))).thenReturn(descending(40));
        Signal saved = mock(Signal.class);
        when(persistence.saveConfiguredSignal(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), anyString(), any(Integer.class))).thenReturn(saved);

        ConfiguredEvaluation result = pipeline.generateConfiguredSignal(SYMBOL, config("pullback-v1", 3),
            resolved(StubStrategy.buying()), false);

        assertThat(result.kind()).isEqualTo(ConfiguredEvaluation.Kind.EVALUATED);
        assertThat(result.signal()).isSameAs(saved);
        assertThat(result.score()).isEqualByComparingTo("0.80");
        verify(persistence).saveConfiguredSignal(eq(SYMBOL), any(LocalDate.class), eq(Signal.SignalType.BUY),
            eq(new BigDecimal("0.8000")), any(), any(), any(), eq(new BigDecimal("90.0")),
            eq(new BigDecimal("120.0")), any(), eq("PENDING_SENTIMENT"), eq("pullback-v1"), eq(3));
    }

    @Test
    void holdDecisionIsEvaluatedWithoutPersistingASignal() {
        when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), any(Integer.class))).thenReturn(descending(40));

        ConfiguredEvaluation result = pipeline.generateConfiguredSignal(SYMBOL, config("squeeze-v1", 1),
            resolved(StubStrategy.holding()), false);

        assertThat(result.kind()).isEqualTo(ConfiguredEvaluation.Kind.EVALUATED);
        assertThat(result.signal()).isNull();
        verifyNoInteractions(persistence);
    }

    @Test
    void insufficientHistoryIsSkippedWithReason() {
        when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), any(Integer.class))).thenReturn(descending(3));

        ConfiguredEvaluation result = pipeline.generateConfiguredSignal(SYMBOL, config("pullback-v1", 1),
            resolved(StubStrategy.buying()), false);

        assertThat(result.kind()).isEqualTo(ConfiguredEvaluation.Kind.SKIPPED);
        assertThat(result.detail()).contains("insufficient");
    }

    @Test
    void unresolvedStrategyIsSkippedWithReasonAndNeverThrows() {
        ConfiguredEvaluation result = pipeline.generateConfiguredSignal(SYMBOL, config("x-v1", 1),
            new ResolvedStrategy.Unresolved("unsupported strategy type NOPE"), false);

        assertThat(result.kind()).isEqualTo(ConfiguredEvaluation.Kind.SKIPPED);
        assertThat(result.detail()).contains("NOPE");
        verifyNoInteractions(persistence, candleStore);
    }

    @Test
    void signalStrategyNeverClosesHeldPositions() {
        when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), any(Integer.class))).thenReturn(descending(40));

        pipeline.generateConfiguredSignal(SYMBOL, config("pullback-v1", 1), resolved(StubStrategy.holding()), true);

        verify(positionService, never()).closePosition(anyString(), anyString());
    }

    private static ResolvedStrategy.Signal resolved(SignalStrategy strategy) {
        return new ResolvedStrategy.Signal(strategy, StrategyParamsView.of(Map.of()), Map.of());
    }

    private static StrategyConfig config(String variantId, int version) {
        return StrategyConfig.create(variantId, version, "PULLBACK", Map.of(), Map.of(),
            StrategyConfig.Mode.SHADOW, BigDecimal.valueOf(100000), true, null, java.time.LocalDateTime.now());
    }

    private static List<OhlcvCandle> descending(int count) {
        List<OhlcvCandle> candles = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1).plusDays(count);
        for (int i = 0; i < count; i++) {
            candles.add(OhlcvCandle.of(SYMBOL, date.minusDays(i), BigDecimal.valueOf(100), BigDecimal.valueOf(101),
                BigDecimal.valueOf(99), BigDecimal.valueOf(100), 1_000_000L));
        }
        return candles;
    }

    private record StubStrategy(StrategyDecision decision) implements SignalStrategy {
        static StubStrategy buying() {
            return new StubStrategy(new StrategyDecision(Signal.SignalType.BUY, new BigDecimal("0.80"),
                List.of(new RuleOutcome("r1", true, BigDecimal.ONE, false, "ok")),
                new BigDecimal("90.0"), new BigDecimal("120.0"), "stub buy"));
        }

        static StubStrategy holding() {
            return new StubStrategy(new StrategyDecision(Signal.SignalType.HOLD, new BigDecimal("0.20"),
                List.of(), null, null, "stub hold"));
        }

        @Override public String type() { return "STUB"; }
        @Override public ParamSchema paramSchema() { return new ParamSchema(List.of()); }
        @Override public Set<IndicatorKey> requiredIndicators(StrategyParamsView params) { return Set.of(); }
        @Override public int warmupBars(StrategyParamsView params) { return 5; }
        @Override public StrategyDecision evaluateEntry(MarketContext ctx, int barIndex, StrategyParamsView params) {
            return decision;
        }
        @Override public ExitDecision evaluateExit(MarketContext ctx, int barIndex, OpenPosition position,
                                                   StrategyParamsView params) {
            return ExitDecision.hold();
        }
    }
}
