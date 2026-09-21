package com.swingtrade.api.service;

import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.domain.service.VariantTradingService;
import com.swingtrade.domain.store.BacktestResultStore;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.LlmAnalysisResultStore;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.WatchlistStore;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.LegacyPriceActionAdapter;
import com.swingtrade.strategy.ParamSchemaValidator;
import com.swingtrade.strategy.PriceActionConfluenceStrategy;
import com.swingtrade.strategy.PriceActionStrategy;
import com.swingtrade.strategy.PullbackStrategy;
import com.swingtrade.strategy.SqueezeStrategy;
import com.swingtrade.strategy.StrategyRegistry;
import com.swingtrade.strategy.StrategyResolver;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("JobOrchestratorService configured-strategy evaluation via the StrategyResolver")
class JobOrchestratorConfiguredStrategiesTest {

    private static final String SYMBOL = "TCS";

    private SignalPipeline signalPipeline;
    private StrategyConfigRepository configRepository;
    private VariantTradingService variantTrading;
    private CandleStore candleStore;
    private SignalStore signalStore;
    private JobOrchestratorService service;

    @BeforeEach
    void setUp() {
        signalPipeline = mock(SignalPipeline.class);
        configRepository = mock(StrategyConfigRepository.class);
        variantTrading = mock(VariantTradingService.class);
        candleStore = mock(CandleStore.class);
        signalStore = mock(SignalStore.class);
        var resolver = new StrategyResolver(
            new StrategyTypeRegistry(List.of(new LegacyPriceActionAdapter(new PriceActionStrategy()),
                new PullbackStrategy(), new SqueezeStrategy())),
            new StrategyRegistry(List.of(new PriceActionStrategy(), new PriceActionConfluenceStrategy()),
                new PriceActionStrategy()),
            new ParamSchemaValidator());
        service = new JobOrchestratorService(mock(DataIngestionService.class), mock(NewsIngestionService.class),
            mock(SentimentService.class), signalPipeline, mock(SentimentGate.class), mock(BacktestEngine.class),
            mock(TradingService.class), mock(JobRunRepository.class), mock(JobRunStageRepository.class),
            signalStore, mock(WatchlistStore.class), candleStore, mock(JobOrchestratorMetrics.class),
            mock(TechnicalAnalysisService.class), mock(FundamentalScorer.class),
            mock(CompositeAnalysisService.class), mock(com.swingtrade.llm.service.SynthesisService.class),
            mock(BacktestResultStore.class), mock(LlmAnalysisResultStore.class), mock(LlmAnalysisGate.class),
            mock(SentimentStore.class), configRepository, resolver, mock(LiveEligibilityService.class),
            variantTrading, 1, 1000, false, false, true);
    }

    @Test
    void pilotSignalStrategyTypesAreResolvedAndEvaluatedNotSkipped() {
        givenConfigs(config("breakout-v1", "BREAKOUT"), config("pullback-v1", "PULLBACK"),
            config("squeeze-v1", "SQUEEZE"));
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean()))
            .thenReturn(ConfiguredEvaluation.evaluated(null, new BigDecimal("0.30"), "no entry"));

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1]);

        assertThat(result.summary()).startsWith("3 evaluated, 0 signal(s), 0 skipped, 0 error(s)");
        verify(signalPipeline, org.mockito.Mockito.times(3))
            .generateConfiguredSignal(eq(SYMBOL), any(), any(com.swingtrade.strategy.ResolvedStrategy.Signal.class),
                anyBoolean());
    }

    @Test
    void unknownTypeIsReportedAsSkippedWithReasonNotDropped() {
        givenConfigs(config("pullback-v1", "PULLBACK"), config("bogus-v1", "BOGUS"));
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(2) instanceof
                com.swingtrade.strategy.ResolvedStrategy.Unresolved u
                ? ConfiguredEvaluation.skipped(u.reason())
                : ConfiguredEvaluation.evaluated(null, BigDecimal.ZERO, "no entry"));

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1]);

        assertThat(result.summary()).contains("1 evaluated", "1 skipped")
            .contains("bogus-v1").contains("BOGUS");
    }

    @Test
    void signalFromSignalStrategyIsReportedAndMarkedTradeableForChampion() {
        var champion = config("breakout-v1", "BREAKOUT", StrategyConfig.Mode.CHAMPION);
        givenConfigs(champion);
        var buy = new Signal(42L, SYMBOL, LocalDate.now(), Signal.SignalType.BUY, new BigDecimal("0.9"), "r",
            BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, null, LocalDate.now(), null, null);
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), eq(true)))
            .thenReturn(ConfiguredEvaluation.evaluated(buy, new BigDecimal("0.9"), "buy"));
        Set<Long> tradeable = new HashSet<>();
        var type = new Signal.SignalType[1];

        var result = service.stageSignal(SYMBOL, type, tradeable, new boolean[1]);

        assertThat(result.summary()).startsWith("1 evaluated, 1 signal(s), 0 skipped");
        assertThat(type[0]).isEqualTo(Signal.SignalType.BUY);
        assertThat(tradeable).containsExactly(42L);
    }

    @Test
    void pipelineFailureIsCountedAsErrorAndOtherVariantsStillRun() {
        givenConfigs(config("breakout-v1", "BREAKOUT"), config("pullback-v1", "PULLBACK"));
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean()))
            .thenThrow(new IllegalStateException("db down"))
            .thenReturn(ConfiguredEvaluation.evaluated(null, BigDecimal.ZERO, "no entry"));

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1]);

        assertThat(result.summary()).contains("1 evaluated", "1 error(s)");
    }

    @Test
    void openSignalStrategyPositionIsClosedViaEvaluateExit() {
        var pullback = config("pullback-v1", "PULLBACK");
        givenConfigs(pullback);
        List<OhlcvCandle> chronological = crashingSeries();
        OhlcvCandle latest = chronological.get(chronological.size() - 1);
        when(candleStore.findLatestBySymbol(SYMBOL)).thenReturn(Optional.of(latest));
        var descending = new ArrayList<>(chronological);
        Collections.reverse(descending);
        when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), any(Integer.class))).thenReturn(descending);
        // Stop and target far away so only the strategy's own signal exit (close < trend EMA) fires.
        Position open = openPosition(new BigDecimal("150"), chronological.get(100).date(),
            new BigDecimal("1"), new BigDecimal("10000"));
        when(variantTrading.findOpenPositions("pullback-v1", SYMBOL)).thenReturn(List.of(open));

        service.stageVariantPaperTrade(SYMBOL);

        verify(variantTrading).closePosition(eq("pullback-v1"), eq(SYMBOL), any(BigDecimal.class), anyString());
    }

    @Test
    void openPositionIsKeptWhenStrategyDoesNotSignalExit() {
        givenConfigs(config("pullback-v1", "PULLBACK"));
        List<OhlcvCandle> rising = risingSeries();
        OhlcvCandle latest = rising.get(rising.size() - 1);
        when(candleStore.findLatestBySymbol(SYMBOL)).thenReturn(Optional.of(latest));
        var descending = new ArrayList<>(rising);
        Collections.reverse(descending);
        when(candleStore.findTopBySymbolOrderByDateDesc(eq(SYMBOL), any(Integer.class))).thenReturn(descending);
        Position open = openPosition(rising.get(155).close(), rising.get(155).date(),
            new BigDecimal("1"), new BigDecimal("100000"));
        when(variantTrading.findOpenPositions("pullback-v1", SYMBOL)).thenReturn(List.of(open));

        service.stageVariantPaperTrade(SYMBOL);

        verify(variantTrading, never()).closePosition(anyString(), anyString(), any(), anyString());
    }

    private static Position openPosition(BigDecimal entry, LocalDate entryDate, BigDecimal stop,
                                         BigDecimal target) {
        return Position.of(1L, "PAPER", SYMBOL, entry, entryDate, 10, stop, target,
            com.swingtrade.domain.PositionStatus.OPEN, "shadow", entry, null, null, null, null, null,
            null, null, null, null, null, null, null);
    }

    private void givenConfigs(StrategyConfig... configs) {
        when(configRepository.findAll()).thenReturn(java.util.Arrays.stream(configs)
            .map(StrategyConfigEntity::fromDomain).toList());
    }

    private static StrategyConfig config(String variantId, String type) {
        return config(variantId, type, StrategyConfig.Mode.SHADOW);
    }

    private static StrategyConfig config(String variantId, String type, StrategyConfig.Mode mode) {
        return StrategyConfig.create(variantId, 1, type, Map.of(), Map.of(), mode,
            BigDecimal.valueOf(100000), true, null, LocalDateTime.now());
    }

    private static List<OhlcvCandle> risingSeries() {
        return series(160, i -> 100 + i * 0.5);
    }

    /** Rises for 130 bars then collapses so close falls well below the trend EMA. */
    private static List<OhlcvCandle> crashingSeries() {
        return series(160, i -> i < 130 ? 100 + i * 0.5 : 165 - (i - 130) * 3.0);
    }

    private static List<OhlcvCandle> series(int count, java.util.function.IntToDoubleFunction closeAt) {
        List<OhlcvCandle> candles = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < count; i++) {
            double close = closeAt.applyAsDouble(i);
            candles.add(OhlcvCandle.of(SYMBOL, date.plusDays(i), BigDecimal.valueOf(close),
                BigDecimal.valueOf(close * 1.001), BigDecimal.valueOf(close * 0.999), BigDecimal.valueOf(close),
                1_000_000L));
        }
        return candles;
    }
}
