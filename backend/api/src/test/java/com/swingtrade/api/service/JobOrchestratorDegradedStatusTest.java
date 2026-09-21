package com.swingtrade.api.service;

import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.JobRunStage;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.SynthesisResult;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("JobOrchestratorService honest stage status (DEGRADED)")
class JobOrchestratorDegradedStatusTest {

    private static final String SYMBOL = "TCS";

    private SignalPipeline signalPipeline;
    private StrategyConfigRepository configRepository;
    private SentimentService sentimentService;
    private JobOrchestratorMetrics metrics;
    private JobRunStageRepository stageRepository;
    private CompositeAnalysisService compositeService;
    private com.swingtrade.llm.service.SynthesisService synthesisService;
    private JobOrchestratorService service;

    @BeforeEach
    void setUp() {
        signalPipeline = mock(SignalPipeline.class);
        configRepository = mock(StrategyConfigRepository.class);
        sentimentService = mock(SentimentService.class);
        metrics = mock(JobOrchestratorMetrics.class);
        stageRepository = mock(JobRunStageRepository.class);
        compositeService = mock(CompositeAnalysisService.class);
        synthesisService = mock(com.swingtrade.llm.service.SynthesisService.class);
        var resolver = new StrategyResolver(
            new StrategyTypeRegistry(List.of(new LegacyPriceActionAdapter(new PriceActionStrategy()),
                new PullbackStrategy(), new SqueezeStrategy())),
            new StrategyRegistry(List.of(new PriceActionStrategy(), new PriceActionConfluenceStrategy()),
                new PriceActionStrategy()),
            new ParamSchemaValidator());
        service = new JobOrchestratorService(mock(DataIngestionService.class), mock(NewsIngestionService.class),
            sentimentService, signalPipeline, mock(SentimentGate.class), mock(BacktestEngine.class),
            mock(TradingService.class), mock(JobRunRepository.class), stageRepository,
            mock(SignalStore.class), mock(WatchlistStore.class), mock(CandleStore.class), metrics,
            mock(TechnicalAnalysisService.class), mock(FundamentalScorer.class), compositeService,
            synthesisService, mock(BacktestResultStore.class), mock(LlmAnalysisResultStore.class),
            mock(LlmAnalysisGate.class), mock(SentimentStore.class), configRepository, resolver,
            mock(LiveEligibilityService.class), mock(VariantTradingService.class), 1, 1000, false, true, true);
    }

    @Test
    void skippedVariantMakesSignalStageDegradedWithDetailsAndMetric() {
        givenConfigs(config("breakout-v1", "BREAKOUT"), config("bogus-v1", "BOGUS"));
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(2) instanceof
                com.swingtrade.strategy.ResolvedStrategy.Unresolved u
                ? ConfiguredEvaluation.skipped(u.reason())
                : ConfiguredEvaluation.evaluated(null, BigDecimal.ZERO, "no entry"));
        var evaluated = new HashSet<String>();

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1], evaluated);

        assertThat(result.status()).isEqualTo(JobRunStage.Status.DEGRADED);
        assertThat(result.reasonCode()).isEqualTo("STRATEGY_SKIPPED");
        assertThat(result.details()).contains("\"variantId\":\"bogus-v1\"", "\"outcome\":\"SKIPPED\"");
        assertThat(evaluated).containsExactly("breakout-v1");
        verify(metrics).recordStrategySkipped("bogus-v1", "UNSUPPORTED_TYPE");
    }

    @Test
    void fullyEvaluatedSignalStageStaysCompleted() {
        givenConfigs(config("breakout-v1", "BREAKOUT"));
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean()))
            .thenReturn(ConfiguredEvaluation.evaluated(null, BigDecimal.ZERO, "no entry"));

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1]);

        assertThat(result.status()).isEqualTo(JobRunStage.Status.COMPLETED);
        assertThat(result.details()).contains("\"outcome\":\"EVALUATED\"");
    }

    @Test
    void failOnStrategySkipFlagTurnsSkipIntoStageError() {
        service.setFailOnStrategySkip(true);
        givenConfigs(config("bogus-v1", "BOGUS"));
        when(signalPipeline.generateConfiguredSignal(eq(SYMBOL), any(), any(), anyBoolean()))
            .thenReturn(ConfiguredEvaluation.skipped("unsupported strategy type BOGUS"));

        var result = service.stageSignal(SYMBOL, new Signal.SignalType[1], new HashSet<>(), new boolean[1]);

        assertThat(result.status()).isEqualTo(JobRunStage.Status.ERROR);
    }

    @Test
    void keywordFallbackSentimentIsDegradedWithSourceAndReason() {
        when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class))).thenReturn(sentiment(
            "KEYWORD_FALLBACK", "LLM returned an empty response"));

        var result = service.stageSentiment(SYMBOL, LocalDate.now());

        assertThat(result.status()).isEqualTo(JobRunStage.Status.DEGRADED);
        assertThat(result.reasonCode()).isEqualTo("KEYWORD_FALLBACK");
        assertThat(result.details()).contains("\"source\":\"KEYWORD_FALLBACK\"",
            "LLM returned an empty response");
    }

    @Test
    void llmSentimentCompletesWithLlmSource() {
        when(sentimentService.analyzeStockSentiment(eq(SYMBOL), any(LocalDate.class)))
            .thenReturn(sentiment("LLM", null));

        var result = service.stageSentiment(SYMBOL, LocalDate.now());

        assertThat(result.status()).isEqualTo(JobRunStage.Status.COMPLETED);
        assertThat(result.details()).contains("\"source\":\"LLM\"");
    }

    @Test
    void llmAnalysisWithoutRecommendationIsDegraded() {
        var composite = new CompositeAnalysis(SYMBOL, LocalDate.now(), 10, "HOLD", BigDecimal.TEN,
            List.of(), new CompositeAnalysis.NewsScore(0, "none", List.of(), List.of(), 0),
            new CompositeAnalysis.TechnicalScore(0, "HOLD", 0, List.of()),
            new CompositeAnalysis.FundamentalScore(0, List.of()),
            new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false), "reason", null);
        when(compositeService.analyze(any(), any(), any(), any(), any())).thenReturn(composite);
        when(synthesisService.synthesize(composite))
            .thenReturn(new SynthesisResult(null, null, 0, List.of(), List.of(), List.of(), false));

        var result = service.stageLlmAnalysis(UUID.randomUUID(), SYMBOL, LocalDate.now());

        assertThat(result.status()).isEqualTo(JobRunStage.Status.DEGRADED);
        assertThat(result.reasonCode()).isEqualTo("NO_RECOMMENDATION");
    }

    @Test
    void degradedResultIsPersistedWithDetailsAndDoesNotBlockLaterStages() {
        UUID runId = UUID.randomUUID();
        var row = new JobRunStageEntity();
        row.setRunId(runId);
        row.setSymbol(SYMBOL);
        row.setStageName("SENTIMENT");
        row.setStatus("PENDING");
        when(stageRepository.findByRunIdAndSymbolAndStageName(runId, SYMBOL, "SENTIMENT")).thenReturn(List.of(row));

        boolean succeeded = service.executeStage(runId, SYMBOL, JobRunStage.StageName.SENTIMENT,
            () -> new JobOrchestratorService.StageExecutionResult(JobRunStage.Status.DEGRADED, "NEUTRAL",
                "{\"source\":\"KEYWORD_FALLBACK\"}", "KEYWORD_FALLBACK"), 5);

        assertThat(succeeded).isTrue();
        var saved = ArgumentCaptor.forClass(JobRunStageEntity.class);
        verify(stageRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        JobRunStageEntity last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo("DEGRADED");
        assertThat(last.getDetails()).isEqualTo("{\"source\":\"KEYWORD_FALLBACK\"}");
        assertThat(last.getCompletedAt()).isNotNull();
        verify(metrics).recordStageDegraded("SENTIMENT", "KEYWORD_FALLBACK");
    }

    private static SentimentResult sentiment(String source, String degradedReason) {
        return new SentimentResult(null, SYMBOL, LocalDate.now(), SentimentResult.SentimentScore.NEUTRAL, "s", "",
            0.5, LocalDate.now(), List.of(), List.of(), null, null, 0, source, List.of(), null, degradedReason);
    }

    private void givenConfigs(StrategyConfig... configs) {
        when(configRepository.findAll()).thenReturn(java.util.Arrays.stream(configs)
            .map(StrategyConfigEntity::fromDomain).toList());
    }

    private static StrategyConfig config(String variantId, String type) {
        return StrategyConfig.create(variantId, 1, type, Map.of(), Map.of(), StrategyConfig.Mode.SHADOW,
            BigDecimal.valueOf(100000), true, null, LocalDateTime.now());
    }
}
