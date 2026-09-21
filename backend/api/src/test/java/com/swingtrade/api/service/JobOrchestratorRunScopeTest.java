package com.swingtrade.api.service;

import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.JobRun;
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
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("JobOrchestratorService run scoping, dry run and champion guard")
class JobOrchestratorRunScopeTest {

    private static final String SYMBOL = "TCS";

    private SignalPipeline signalPipeline;
    private StrategyConfigRepository configRepository;
    private WatchlistStore watchlistStore;
    private JobRunRepository jobRunRepository;
    private JobOrchestratorService service;

    @BeforeEach
    void setUp() {
        signalPipeline = mock(SignalPipeline.class);
        configRepository = mock(StrategyConfigRepository.class);
        watchlistStore = mock(WatchlistStore.class);
        jobRunRepository = mock(JobRunRepository.class);
        var resolver = new StrategyResolver(
            new StrategyTypeRegistry(List.of(new LegacyPriceActionAdapter(new PriceActionStrategy()),
                new PullbackStrategy(), new SqueezeStrategy())),
            new StrategyRegistry(List.of(new PriceActionStrategy(), new PriceActionConfluenceStrategy()),
                new PriceActionStrategy()),
            new ParamSchemaValidator());
        service = new JobOrchestratorService(mock(DataIngestionService.class), mock(NewsIngestionService.class),
            mock(SentimentService.class), signalPipeline, mock(SentimentGate.class), mock(BacktestEngine.class),
            mock(TradingService.class), jobRunRepository, mock(JobRunStageRepository.class),
            mock(SignalStore.class), watchlistStore, mock(CandleStore.class), mock(JobOrchestratorMetrics.class),
            mock(TechnicalAnalysisService.class), mock(FundamentalScorer.class), mock(CompositeAnalysisService.class),
            mock(com.swingtrade.llm.service.SynthesisService.class), mock(BacktestResultStore.class),
            mock(LlmAnalysisResultStore.class), mock(LlmAnalysisGate.class), mock(SentimentStore.class),
            configRepository, resolver, mock(LiveEligibilityService.class), mock(VariantTradingService.class),
            1, 1000, false, false, true);
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean()))
            .thenReturn(ConfiguredEvaluation.evaluated(null, BigDecimal.ZERO, "no entry"));
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(ConfiguredEvaluation.evaluated(null, BigDecimal.ZERO, "no entry"));
    }

    @Test
    void variantScopeEvaluatesOnlySelectedVariants() {
        givenConfigs(config("breakout-v1", StrategyConfig.Mode.CHAMPION), config("pullback-v1", StrategyConfig.Mode.SHADOW));
        var scope = RunScope.resolve(new RunRequest(null, List.of("pullback-v1"), null, null, null),
            List.of(SYMBOL), java.util.Set.of("breakout-v1", "pullback-v1"));
        var evaluated = new HashSet<String>();

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1],
            evaluated, scope);

        assertThat(evaluated).containsExactly("pullback-v1");
        assertThat(result.summary()).startsWith("1 evaluated");
        verify(signalPipeline, times(1)).generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean());
    }

    @Test
    void dryRunEvaluatesWithoutPersistence() {
        givenConfigs(config("breakout-v1", StrategyConfig.Mode.CHAMPION));
        var scope = RunScope.resolve(new RunRequest(null, null, null, null, true), List.of(SYMBOL),
            java.util.Set.of("breakout-v1"));

        service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1], new HashSet<>(), scope);

        verify(signalPipeline).generateConfiguredSignal(eq(SYMBOL), any(), any(), eq(true), eq(true));
        verify(signalPipeline, never()).generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean());
    }

    @Test
    void zeroChampionsIsSurfacedAsAWarningInStageDetailsWithoutDegrading() {
        givenConfigs(config("breakout-v1", StrategyConfig.Mode.SHADOW));

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1]);

        assertThat(result.status()).isEqualTo(com.swingtrade.domain.JobRunStage.Status.COMPLETED);
        assertThat(result.details()).contains("\"warnings\"", "Expected exactly 1 CHAMPION variant but found 0");
    }

    @Test
    void twoChampionsIsAlsoWarned() {
        givenConfigs(config("a-v1", StrategyConfig.Mode.CHAMPION), config("b-v1", StrategyConfig.Mode.CHAMPION));

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1]);

        assertThat(result.details()).contains("found 2");
    }

    @Test
    void oneChampionHasNoWarning() {
        givenConfigs(config("a-v1", StrategyConfig.Mode.CHAMPION), config("b-v1", StrategyConfig.Mode.SHADOW));

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1]);

        assertThat(result.details()).doesNotContain("warnings");
    }

    @Test
    void unknownIdsAreRejectedBeforeAnyRunIsCreated() {
        givenConfigs(config("breakout-v1", StrategyConfig.Mode.CHAMPION));
        when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));

        assertThatThrownBy(() -> service.startRun(JobRun.TriggerType.MANUAL,
            new RunRequest(List.of("NOPE"), List.of("ghost-v1"), null, null, null)))
            .isInstanceOf(InvalidRunRequestException.class)
            .hasMessageContaining("NOPE").hasMessageContaining("ghost-v1");
        verify(jobRunRepository, never()).save(any());
    }

    @Test
    void scopedRunPersistsItsRequestAsTriggerOptions() {
        givenConfigs(config("breakout-v1", StrategyConfig.Mode.CHAMPION));
        when(watchlistStore.getActiveWatchlistSymbols()).thenReturn(List.of(SYMBOL));
        when(jobRunRepository.findByStatusOrderByStartedAtDesc(any())).thenReturn(List.of());
        when(jobRunRepository.save(any(JobRunEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(jobRunRepository.findByRunId(any())).thenAnswer(i -> Optional.of(new JobRunEntity()));

        service.startRun(JobRun.TriggerType.MANUAL,
            new RunRequest(List.of("tcs"), List.of("breakout-v1"), null, true, null));

        var saved = ArgumentCaptor.forClass(JobRunEntity.class);
        verify(jobRunRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getTriggerOptions())
            .contains("\"symbols\":[\"tcs\"]", "\"variantIds\":[\"breakout-v1\"]", "\"skipLlm\":true");
    }

    private void givenConfigs(StrategyConfig... configs) {
        when(configRepository.findAll()).thenReturn(java.util.Arrays.stream(configs)
            .map(StrategyConfigEntity::fromDomain).toList());
    }

    private static StrategyConfig config(String variantId, StrategyConfig.Mode mode) {
        return StrategyConfig.create(variantId, 1, "BREAKOUT", Map.of(), Map.of(), mode,
            BigDecimal.valueOf(100000), true, null, LocalDateTime.now());
    }
}
