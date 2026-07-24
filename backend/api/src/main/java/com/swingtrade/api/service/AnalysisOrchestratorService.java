package com.swingtrade.api.service;

import com.swingtrade.api.dto.AnalysisProgress;
import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.api.dto.FullAnalysisResult;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AnalysisOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisOrchestratorService.class);
    private static final int MIN_CANDLES = 100;

    private final DataIngestionService dataIngestionService;
    private final NewsIngestionService newsIngestionService;
    private final SentimentService sentimentService;
    private final TechnicalAnalysisService technicalService;
    private final CompositeAnalysisService compositeAnalysisService;
    private final BacktestScorer backtestScorer;
    private final CandleStore candleStore;
    private final SentimentStore sentimentStore;

    public AnalysisOrchestratorService(DataIngestionService dataIngestionService,
                                       NewsIngestionService newsIngestionService,
                                       SentimentService sentimentService,
                                       TechnicalAnalysisService technicalService,
                                       CompositeAnalysisService compositeAnalysisService,
                                       BacktestScorer backtestScorer,
                                       CandleStore candleStore,
                                       SentimentStore sentimentStore) {
        this.dataIngestionService = dataIngestionService;
        this.newsIngestionService = newsIngestionService;
        this.sentimentService = sentimentService;
        this.technicalService = technicalService;
        this.compositeAnalysisService = compositeAnalysisService;
        this.backtestScorer = backtestScorer;
        this.candleStore = candleStore;
        this.sentimentStore = sentimentStore;
    }

    public FullAnalysisResult runFullAnalysis(String symbol, SseEmitter emitter, int backfillYears) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        long startTime = System.currentTimeMillis();
        List<AnalysisProgress> progress = new ArrayList<>();
        AtomicLong stageDurations = new AtomicLong(0);

        emitProgress(emitter, progress, AnalysisProgress.running(0, "pipeline-start"));
        emitProgress(emitter, progress, AnalysisProgress.completed(0, "pipeline-start",
            "Full analysis started for " + sym));

        try {
            // Stage 1: Check data completeness
            long s1 = System.currentTimeMillis();
            int candleCount = (int) candleStore.countBySymbol(sym);
            emitProgress(emitter, progress, AnalysisProgress.completed(1, "checking data",
                String.format("Found %d candles", candleCount)));
            logger.info("Stage 1 done in {}ms", System.currentTimeMillis() - s1);

            // Stage 2: Backfill if needed
            if (candleCount < MIN_CANDLES) {
                long s2 = System.currentTimeMillis();
                emitProgress(emitter, progress, AnalysisProgress.running(2, "backfilling OHLCV"));
                try {
                    dataIngestionService.backfillStockData(sym, backfillYears);
                    int newCount = (int) candleRepository.countBySymbol(sym);
                    emitProgress(emitter, progress, AnalysisProgress.completed(2, "backfilling OHLCV",
                        String.format("Backfilled: %d → %d candles", candleCount, newCount)));
                    stageDurations.addAndGet(System.currentTimeMillis() - s2);
                } catch (Exception e) {
                    emitProgress(emitter, progress, AnalysisProgress.error(2, "backfilling OHLCV", e.getMessage()));
                }
            } else {
                emitProgress(emitter, progress, AnalysisProgress.skipped(2, "backfilling OHLCV",
                    String.format("Already %d candles, skipping", candleCount)));
            }

            // Stage 3: Fetch news if needed
            long s3 = System.currentTimeMillis();
            if (!hasSentimentForToday(sym)) {
                emitProgress(emitter, progress, AnalysisProgress.running(3, "fetching news"));
                try {
                    newsIngestionService.fetchStockNews(sym);
                    emitProgress(emitter, progress, AnalysisProgress.completed(3, "fetching news",
                        "News articles fetched"));
                } catch (Exception e) {
                    emitProgress(emitter, progress, AnalysisProgress.error(3, "fetching news", e.getMessage()));
                }
            } else {
                emitProgress(emitter, progress, AnalysisProgress.skipped(3, "fetching news",
                    "Sentiment already exists for today"));
            }
            logger.info("Stage 3 done in {}ms", System.currentTimeMillis() - s3);

            // Stage 4: LLM Sentiment
            long s4 = System.currentTimeMillis();
            emitProgress(emitter, progress, AnalysisProgress.running(4, "LLM sentiment"));
            SentimentResult sentiment;
            try {
                sentiment = sentimentService.analyzeStockSentiment(sym, LocalDate.now());
                emitProgress(emitter, progress, AnalysisProgress.completed(4, "LLM sentiment",
                    String.format("Score: %s, confidence: %.0f%%",
                        sentiment.score(), sentiment.confidence() * 100)));
            } catch (Exception e) {
                sentiment = null;
                emitProgress(emitter, progress, AnalysisProgress.error(4, "LLM sentiment", e.getMessage()));
            }
            logger.info("Stage 4 done in {}ms", System.currentTimeMillis() - s4);

            // Stage 5: Technical indicators
            long s5 = System.currentTimeMillis();
            emitProgress(emitter, progress, AnalysisProgress.running(5, "technical analysis"));
            CompositeAnalysis.TechnicalScore technical;
            try {
                technical = technicalService.compute(sym);
                emitProgress(emitter, progress, AnalysisProgress.completed(5, "technical analysis",
                    String.format("Signal: %s, score: %d", technical.signal(), technical.score())));
            } catch (Exception e) {
                technical = new CompositeAnalysis.TechnicalScore(0, "HOLD", 0.0, List.of());
                emitProgress(emitter, progress, AnalysisProgress.error(5, "technical analysis", e.getMessage()));
            }
            logger.info("Stage 5 done in {}ms", System.currentTimeMillis() - s5);

            // Stage 6: Composite score
            long s6 = System.currentTimeMillis();
            emitProgress(emitter, progress, AnalysisProgress.running(6, "composite score"));
            CompositeAnalysis composite;
            try {
                composite = compositeAnalysisService.analyze(sym);
                emitProgress(emitter, progress, AnalysisProgress.completed(6, "composite score",
                    String.format("Score: %d, signal: %s",
                        composite.compositeScore(), composite.compositeSignal())));
            } catch (Exception e) {
                composite = null;
                emitProgress(emitter, progress, AnalysisProgress.error(6, "composite score", e.getMessage()));
            }
            logger.info("Stage 6 done in {}ms", System.currentTimeMillis() - s6);

            // Stage 7: Backtest
            long s7 = System.currentTimeMillis();
            emitProgress(emitter, progress, AnalysisProgress.skipped(7, "backtest",
                "Included in composite analysis"));
            logger.info("Stage 7 done in {}ms", System.currentTimeMillis() - s7);

            long duration = System.currentTimeMillis() - startTime;
            var result = new FullAnalysisResult(composite, List.copyOf(progress), duration, sym);
            emitProgress(emitter, progress,
                AnalysisProgress.completed(99, "complete",
                    String.format("Analysis finished in %dms", duration)));
            return result;

        } catch (Exception e) {
            logger.error("Full analysis failed for {}: {}", sym, e.getMessage(), e);
            emitProgress(emitter, progress,
                AnalysisProgress.error(99, "error", e.getMessage()));
            var result = new FullAnalysisResult(null, List.copyOf(progress),
                System.currentTimeMillis() - startTime, sym);
            return result;
        }
    }

    private void emitProgress(SseEmitter emitter, List<AnalysisProgress> progress, AnalysisProgress p) {
        progress.add(p);
        try {
            emitter.send(SseEmitter.event()
                .name("progress")
                .data(p));
        } catch (IOException e) {
            logger.error("Failed to emit progress: {}", e.getMessage());
        }
    }

    private boolean hasSentimentForToday(String symbol) {
        try {
            return sentimentStore.findBySymbolAndDate(symbol, LocalDate.now()).isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}