package com.swingtrade.api.service;

import com.swingtrade.api.dto.AnalysisProgress;
import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.api.dto.FullAnalysisResult;
import com.swingtrade.domain.SynthesisResult;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.NewsArticle;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AnalysisOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisOrchestratorService.class);
    private final DataIngestionService dataIngestionService;
    private final NewsIngestionService newsIngestionService;
    private final SentimentService sentimentService;
    private final TechnicalAnalysisService technicalService;
    private final CompositeAnalysisService compositeAnalysisService;
    private final BacktestScorer backtestScorer;
    private final FundamentalScorer fundamentalScorer;
    private final SynthesisService synthesisService;
    private final CandleStore candleStore;
    private final SentimentStore sentimentStore;

    public AnalysisOrchestratorService(DataIngestionService dataIngestionService,
                                       NewsIngestionService newsIngestionService,
                                       SentimentService sentimentService,
                                       TechnicalAnalysisService technicalService,
                                       CompositeAnalysisService compositeAnalysisService,
                                       BacktestScorer backtestScorer,
                                       FundamentalScorer fundamentalScorer,
                                       SynthesisService synthesisService,
                                       CandleStore candleStore,
                                       SentimentStore sentimentStore) {
        this.dataIngestionService = dataIngestionService;
        this.newsIngestionService = newsIngestionService;
        this.sentimentService = sentimentService;
        this.technicalService = technicalService;
        this.compositeAnalysisService = compositeAnalysisService;
        this.backtestScorer = backtestScorer;
        this.fundamentalScorer = fundamentalScorer;
        this.synthesisService = synthesisService;
        this.candleStore = candleStore;
        this.sentimentStore = sentimentStore;
    }

    public FullAnalysisResult runFullAnalysis(String symbol, SseEmitter emitter, int backfillYears) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        long startTime = System.currentTimeMillis();
        List<AnalysisProgress> progress = new ArrayList<>();

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

            // Stage 2: Backfill 3 years of OHLCV
            long s2 = System.currentTimeMillis();
            emitProgress(emitter, progress, AnalysisProgress.running(2, "backfilling OHLCV"));
            try {
                dataIngestionService.backfillStockData(sym, backfillYears);
                int newCount = (int) candleStore.countBySymbol(sym);
                emitProgress(emitter, progress, AnalysisProgress.completed(2, "backfilling OHLCV",
                    String.format("Backfilled: %d -> %d candles", candleCount, newCount)));
            } catch (Exception e) {
                emitProgress(emitter, progress, AnalysisProgress.error(2, "backfilling OHLCV", e.getMessage()));
            }

            // Stage 3: Fetch news if needed
            long s3 = System.currentTimeMillis();
            if (!hasSentimentForToday(sym)) {
                emitProgress(emitter, progress, AnalysisProgress.running(3, "fetching news"));
                try {
                    List<NewsArticle> articles = newsIngestionService.fetchStockNews(sym);
                    int articleCount = articles.size();
                    int sourceCount = (int) articles.stream()
                        .map(a -> a.source())
                        .distinct()
                        .count();
                    emitProgress(emitter, progress, AnalysisProgress.completed(3, "fetching news",
                        String.format("Fetched %d news articles", articleCount),
                        new AnalysisProgress.StageDetails("news", Map.of(
                            "articleCount", articleCount,
                            "sourceCount", sourceCount,
                            "sources", articles.stream().map(a -> a.source()).distinct().toList()
                        ))));
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
            SentimentResult sentiment;
            try {
                sentiment = sentimentService.analyzeStockSentiment(sym, LocalDate.now());
                int score = switch (sentiment.score()) {
                    case POSITIVE -> 75;
                    case NEGATIVE -> -75;
                    default -> 0;
                };
                emitProgress(emitter, progress, AnalysisProgress.completed(4, "LLM sentiment",
                    String.format("Score: %s, confidence: %.0f%%", sentiment.score(), sentiment.confidence() * 100),
                    new AnalysisProgress.StageDetails("sentiment", Map.of(
                        "score", sentiment.score().name(),
                        "confidence", sentiment.confidence(),
                        "summary", sentiment.summary(),
                        "catalysts", sentiment.catalysts() != null ? sentiment.catalysts() : List.of(),
                        "redFlags", sentiment.redFlags() != null ? sentiment.redFlags() : List.of(),
                        "articleCount", sentiment.articleCount()
                    ))));
            } catch (Exception e) {
                sentiment = SentimentResult.create(sym, LocalDate.now(),
                    SentimentResult.SentimentScore.NEUTRAL, "LLM sentiment unavailable", "", 0.3, List.of(), List.of());
                emitProgress(emitter, progress, AnalysisProgress.error(4, "LLM sentiment", e.getMessage()));
            }
            logger.info("Stage 4 done in {}ms", System.currentTimeMillis() - s4);

            // Stage 5: Technical indicators
            long s5 = System.currentTimeMillis();
            CompositeAnalysis.TechnicalScore technical;
            try {
                technical = technicalService.compute(sym);
                emitProgress(emitter, progress, AnalysisProgress.completed(5, "technical analysis",
                    String.format("Signal: %s, score: %d", technical.signal(), technical.score()),
                    new AnalysisProgress.StageDetails("technical", Map.of(
                        "score", technical.score(),
                        "signal", technical.signal(),
                        "confidence", technical.confidence(),
                        "indicators", technical.indicators()
                    ))));
            } catch (Exception e) {
                technical = new CompositeAnalysis.TechnicalScore(0, "HOLD", 0.0, List.of());
                emitProgress(emitter, progress, AnalysisProgress.error(5, "technical analysis", e.getMessage()));
            }
            logger.info("Stage 5 done in {}ms", System.currentTimeMillis() - s5);

            // Stage 6: Fundamentals (extracted from composite)
            long s6 = System.currentTimeMillis();
            CompositeAnalysis.FundamentalScore fundamentals;
            try {
                fundamentals = fundamentalScorer.compute(sym);
                String signal = fundamentals.score() > 0 ? "BULLISH" : fundamentals.score() < 0 ? "BEARISH" : "NEUTRAL";
                emitProgress(emitter, progress, AnalysisProgress.completed(6, "fundamentals",
                    String.format("Score: %d, %s", fundamentals.score(), signal),
                    new AnalysisProgress.StageDetails("fundamentals", Map.of(
                        "score", fundamentals.score(),
                        "factors", fundamentals.factors()
                    ))));
            } catch (Exception e) {
                fundamentals = new CompositeAnalysis.FundamentalScore(0, List.of("Analysis failed: " + e.getMessage()));
                emitProgress(emitter, progress, AnalysisProgress.error(6, "fundamentals", e.getMessage()));
            }
            logger.info("Stage 6 done in {}ms", System.currentTimeMillis() - s6);

            // Stage 7: Backtest
            long s7 = System.currentTimeMillis();
            CompositeAnalysis.BacktestScore backtest;
            try {
                backtest = backtestScorer.compute(sym);
                emitProgress(emitter, progress, AnalysisProgress.completed(7, "backtest",
                    String.format("%d trades, win rate: %.1f%%, return: %.1f%%",
                        backtest.totalTrades(), backtest.winRate(), backtest.totalReturn()),
                    new AnalysisProgress.StageDetails("backtest", Map.of(
                        "totalTrades", backtest.totalTrades(),
                        "winRate", backtest.winRate(),
                        "profitFactor", backtest.profitFactor(),
                        "maxDrawdown", backtest.maxDrawdown(),
                        "totalReturn", backtest.totalReturn(),
                        "expectancy", backtest.expectancy(),
                        "hasEnoughData", backtest.hasEnoughData()
                    ))));
            } catch (Exception e) {
                backtest = new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false);
                emitProgress(emitter, progress, AnalysisProgress.error(7, "backtest", e.getMessage()));
            }
            logger.info("Stage 7 done in {}ms", System.currentTimeMillis() - s7);

            // Stage 8: Composite score (uses pre-computed values from stages 5-7)
            long s8 = System.currentTimeMillis();
            CompositeAnalysis composite;
            try {
                composite = compositeAnalysisService.analyze(sym, technical, fundamentals, backtest, sentiment);
                emitProgress(emitter, progress, AnalysisProgress.completed(8, "composite score",
                    String.format("Score: %d, signal: %s",
                        composite.compositeScore(), composite.compositeSignal()),
                    new AnalysisProgress.StageDetails("composite", Map.of(
                        "compositeScore", composite.compositeScore(),
                        "compositeSignal", composite.compositeSignal(),
                        "compositeConfidence", composite.compositeConfidence().doubleValue(),
                        "sources", composite.sources(),
                        "reasoning", composite.reasoning()
                    ))));
            } catch (Exception e) {
                composite = null;
                emitProgress(emitter, progress, AnalysisProgress.error(8, "composite score", e.getMessage()));
            }
            logger.info("Stage 8 done in {}ms", System.currentTimeMillis() - s8);

            // Stage 9: LLM Synthesis (auto-trigger after stage 8)
            long s9 = System.currentTimeMillis();
            emitProgress(emitter, progress, AnalysisProgress.running(9, "LLM synthesis"));
            try {
                if (composite != null) {
                    SynthesisResult synthesisResult = synthesisService.synthesize(composite);
                    composite = new CompositeAnalysis(
                        composite.symbol(), composite.date(), composite.compositeScore(),
                        composite.compositeSignal(), composite.compositeConfidence(),
                        composite.sources(), composite.news(), composite.technical(),
                        composite.fundamentals(), composite.backtest(), composite.reasoning(),
                        synthesisResult);
                    emitProgress(emitter, progress, AnalysisProgress.completed(9, "LLM synthesis",
                        String.format("Recommendation: %s (%.0f%% confidence)",
                            synthesisResult.recommendation(), synthesisResult.confidence() * 100),
                        new AnalysisProgress.StageDetails("synthesis", Map.of(
                            "narrative", synthesisResult.narrative(),
                            "recommendation", synthesisResult.recommendation(),
                            "confidence", synthesisResult.confidence(),
                            "keyDrivers", synthesisResult.keyDrivers(),
                            "bullishFactors", synthesisResult.bullishFactors(),
                            "bearishFactors", synthesisResult.bearishFactors()
                        ))));
                } else {
                    emitProgress(emitter, progress, AnalysisProgress.error(9, "LLM synthesis",
                        "Skipped: composite analysis failed"));
                }
            } catch (Exception e) {
                emitProgress(emitter, progress, AnalysisProgress.error(9, "LLM synthesis", e.getMessage()));
            }
            logger.info("Stage 9 done in {}ms", System.currentTimeMillis() - s9);

            long duration = System.currentTimeMillis() - startTime;
            var result = new FullAnalysisResult(composite, List.copyOf(progress), duration, sym);
            emitProgress(emitter, progress, AnalysisProgress.completed(99, "complete",
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