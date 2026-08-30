package com.swingtrade.api.service;

import com.swingtrade.core.metrics.JobOrchestratorMetrics;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.LlmAnalysisResult;
import com.swingtrade.domain.SynthesisResult;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.*;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.llm.service.*;
import com.swingtrade.strategy.BacktestEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobOrchestratorLlmAnalysisTest {
    @Test void fallbackIsPersistedAndStageCompletes() throws Exception {
        var compositeService = mock(CompositeAnalysisService.class);
        var synthesisService = mock(SynthesisService.class);
        var resultStore = mock(LlmAnalysisResultStore.class);
        var backtestStore = mock(BacktestResultStore.class);
        var sentimentStore = mock(SentimentStore.class);
        var composite = new CompositeAnalysis("TCS", LocalDate.now(), 10, "HOLD", java.math.BigDecimal.TEN,
            List.of(), new CompositeAnalysis.NewsScore(0, "none", List.of(), List.of(), 0),
            new CompositeAnalysis.TechnicalScore(0, "HOLD", 0, List.of()),
            new CompositeAnalysis.FundamentalScore(0, List.of()),
            new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false), "reason", null);
        when(backtestStore.findBySymbolAndDate(any(), any())).thenReturn(Optional.empty());
        when(sentimentStore.findBySymbolAndDate(any(), any())).thenReturn(Optional.empty());
        when(compositeService.analyze(any(), any(), any(), any(), any())).thenReturn(composite);
        when(synthesisService.synthesize(composite)).thenReturn(new SynthesisResult(null, null, 0,
            List.of(), List.of(), List.of(), false));

        var service = new JobOrchestratorService(mock(DataIngestionService.class), mock(NewsIngestionService.class),
            mock(SentimentService.class), mock(SignalPipeline.class), mock(SentimentGate.class), mock(BacktestEngine.class),
            mock(TradingService.class), mock(JobRunRepository.class), mock(JobRunStageRepository.class), mock(SignalStore.class),
            mock(WatchlistStore.class), mock(CandleStore.class), mock(JobOrchestratorMetrics.class),
            mock(TechnicalAnalysisService.class), mock(FundamentalScorer.class), compositeService, synthesisService,
            backtestStore, resultStore, mock(LlmAnalysisGate.class), sentimentStore, 1, 1000, false, true, true);

        Method stage = JobOrchestratorService.class.getDeclaredMethod("stageLlmAnalysis", UUID.class, String.class, LocalDate.class);
        stage.setAccessible(true);
        stage.invoke(service, UUID.randomUUID(), "TCS", LocalDate.now());

        verify(resultStore).saveOrUpdate(argThat(r -> !r.success() && r.fallbackUsed()));
    }

    @Test void successfulRecommendationIsPersisted() throws Exception {
        var compositeService = mock(CompositeAnalysisService.class);
        var synthesisService = mock(SynthesisService.class);
        var resultStore = mock(LlmAnalysisResultStore.class);
        var backtestStore = mock(BacktestResultStore.class);
        var sentimentStore = mock(SentimentStore.class);
        var composite = composite();
        when(compositeService.analyze(any(), any(), any(), any(), any())).thenReturn(composite);
        when(synthesisService.synthesize(composite)).thenReturn(new SynthesisResult("n", "BUY", 0.9,
            List.of("driver"), List.of("bullish"), List.of(), true));
        invokeStage(compositeService, synthesisService, resultStore, backtestStore, sentimentStore, composite);
        verify(resultStore).saveOrUpdate(argThat(r -> r.success() && !r.fallbackUsed()
            && r.recommendation().equals("BUY")));
    }

    @Test void enforcingModeBlocksSellVerdict() throws Exception {
        var gate = mock(LlmAnalysisGate.class);
        when(gate.evaluatePersisted(any(), any())).thenReturn(
            new LlmAnalysisGate.LlmVerdict(LlmAnalysisGate.LlmVerdict.Action.SUPPRESS, "SELL", null));
        var signals = mock(SignalStore.class);
        var candles = mock(CandleStore.class);
        var trading = mock(TradingService.class);
        when(signals.findUnprocessed()).thenReturn(List.of(buySignal()));
        when(candles.findLatestBySymbol(any())).thenReturn(Optional.of(OhlcvCandle.of("TCS", LocalDate.now(),
            BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1L)));
        var sentiment = mock(SentimentGate.class);
        when(sentiment.evaluatePersisted(any(), any())).thenReturn(SentimentGate.SentimentVerdict.allow());
        invokePaperTrade(newService(gate, sentiment, signals, candles, trading, true, false));
        verify(trading, never()).executeSignal(any(), any());
        verify(signals).markProcessed(7L);
    }

    @Test void advisoryModeDoesNotBlockSellVerdict() throws Exception {
        var gate = mock(LlmAnalysisGate.class);
        when(gate.evaluatePersisted(any(), any())).thenReturn(
            new LlmAnalysisGate.LlmVerdict(LlmAnalysisGate.LlmVerdict.Action.SUPPRESS, "SELL", null));
        var signals = mock(SignalStore.class);
        var candles = mock(CandleStore.class);
        var trading = mock(TradingService.class);
        when(signals.findUnprocessed()).thenReturn(List.of(buySignal()));
        when(candles.findLatestBySymbol(any())).thenReturn(Optional.of(OhlcvCandle.of("TCS", LocalDate.now(),
            BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1L)));
        var sentiment = mock(SentimentGate.class);
        when(sentiment.evaluatePersisted(any(), any())).thenReturn(SentimentGate.SentimentVerdict.allow());
        invokePaperTrade(newService(gate, sentiment, signals, candles, trading, true, true));
        verify(trading).executeSignal(any(), eq(BigDecimal.TEN));
    }

    private static Signal buySignal() {
        return new Signal(7L, "TCS", LocalDate.now(), Signal.SignalType.BUY, BigDecimal.ONE,
            "reason", null, null, null, null, null, LocalDate.now(), null, null);
    }

    private static CompositeAnalysis composite() {
        return new CompositeAnalysis("TCS", LocalDate.now(), 10, "HOLD", BigDecimal.TEN, List.of(),
            new CompositeAnalysis.NewsScore(0, "none", List.of(), List.of(), 0),
            new CompositeAnalysis.TechnicalScore(0, "HOLD", 0, List.of()),
            new CompositeAnalysis.FundamentalScore(0, List.of()),
            new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false), "reason", null);
    }

    private static void invokeStage(CompositeAnalysisService cs, SynthesisService ss, LlmAnalysisResultStore rs,
                                    BacktestResultStore bs, SentimentStore st, CompositeAnalysis composite) throws Exception {
        var service = newService(mock(LlmAnalysisGate.class), mock(SentimentGate.class), mock(SignalStore.class),
            mock(CandleStore.class), mock(TradingService.class), true, true, cs, ss, rs, bs, st);
        Method stage = JobOrchestratorService.class.getDeclaredMethod("stageLlmAnalysis", UUID.class, String.class, LocalDate.class);
        stage.setAccessible(true);
        stage.invoke(service, UUID.randomUUID(), "TCS", LocalDate.now());
    }

    private static String invokePaperTrade(JobOrchestratorService service) throws Exception {
        Method stage = JobOrchestratorService.class.getDeclaredMethod("stagePaperTrade", String.class);
        stage.setAccessible(true);
        return (String) stage.invoke(service, "TCS");
    }

    private static JobOrchestratorService newService(LlmAnalysisGate gate, SentimentGate sentiment,
            SignalStore signals, CandleStore candles, TradingService trading, boolean enabled, boolean advisory) {
        return newService(gate, sentiment, signals, candles, trading, enabled, advisory,
            mock(CompositeAnalysisService.class), mock(SynthesisService.class), mock(LlmAnalysisResultStore.class),
            mock(BacktestResultStore.class), mock(SentimentStore.class));
    }

    private static JobOrchestratorService newService(LlmAnalysisGate gate, SentimentGate sentiment,
            SignalStore signals, CandleStore candles, TradingService trading, boolean enabled, boolean advisory,
            CompositeAnalysisService composite, SynthesisService synthesis, LlmAnalysisResultStore results,
            BacktestResultStore backtests, SentimentStore sentiments) {
        return new JobOrchestratorService(mock(DataIngestionService.class), mock(NewsIngestionService.class),
            mock(SentimentService.class), mock(SignalPipeline.class), sentiment, mock(BacktestEngine.class), trading,
            mock(JobRunRepository.class), mock(JobRunStageRepository.class), signals, mock(WatchlistStore.class),
            candles, mock(JobOrchestratorMetrics.class), mock(TechnicalAnalysisService.class), mock(FundamentalScorer.class),
            composite, synthesis, backtests, results, gate, sentiments, 1, 1000, false, enabled, advisory);
    }
}
