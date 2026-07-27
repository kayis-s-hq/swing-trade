package com.swingtrade.api.service;

import com.swingtrade.api.dto.AnalysisProgress;
import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.api.dto.FullAnalysisResult;
import com.swingtrade.domain.SynthesisResult;
import com.swingtrade.data.service.DataIngestionService;
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
            StageContext ctx = new StageContext();
            ctx.setCandleCount((int) candleStore.countBySymbol(sym));

            runStage1CheckData(emitter, progress, sym, ctx);
            runStage2Backfill(emitter, progress, sym, backfillYears, ctx);
            runStage3FetchNews(emitter, progress, sym, ctx);
            runStage4Sentiment(emitter, progress, sym, ctx);
            runStage5Technical(emitter, progress, sym, ctx);
            runStage6Fundamentals(emitter, progress, sym, ctx);
            runStage7Backtest(emitter, progress, sym, ctx);
            runStage8Composite(emitter, progress, sym, ctx);
            runStage9Synthesis(emitter, progress, sym, ctx);

            long duration = System.currentTimeMillis() - startTime;
            var result = new FullAnalysisResult(ctx.composite, List.copyOf(progress), duration, sym);
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

    private void runStage1CheckData(SseEmitter emitter, List<AnalysisProgress> progress, String sym, StageContext ctx) {
        long s1 = System.currentTimeMillis();
        emitProgress(emitter, progress, AnalysisProgress.completed(1, "checking data",
            String.format("Found %d candles", ctx.candleCount)));
        logger.info("Stage 1 done in {}ms", System.currentTimeMillis() - s1);
    }

    private void runStage2Backfill(SseEmitter emitter, List<AnalysisProgress> progress, String sym, int backfillYears, StageContext ctx) {
        long s2 = System.currentTimeMillis();
        emitProgress(emitter, progress, AnalysisProgress.running(2, "backfilling OHLCV"));
        try {
            dataIngestionService.backfillStockData(sym, backfillYears);
            int newCount = (int) candleStore.countBySymbol(sym);
            emitProgress(emitter, progress, AnalysisProgress.completed(2, "backfilling OHLCV",
                String.format("Backfilled: %d -> %d candles", ctx.candleCount, newCount)));
        } catch (Exception e) {
            emitProgress(emitter, progress, AnalysisProgress.error(2, "backfilling OHLCV", e.getMessage()));
        }
        logger.info("Stage 2 done in {}ms", System.currentTimeMillis() - s2);
    }

    private void runStage3FetchNews(SseEmitter emitter, List<AnalysisProgress> progress, String sym, StageContext ctx) {
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
    }

    private void runStage4Sentiment(SseEmitter emitter, List<AnalysisProgress> progress, String sym, StageContext ctx) {
        long s4 = System.currentTimeMillis();
        try {
            ctx.sentiment = sentimentService.analyzeStockSentiment(sym, LocalDate.now());
            emitProgress(emitter, progress, AnalysisProgress.completed(4, "LLM sentiment",
                String.format("Score: %s, confidence: %.0f%%", ctx.sentiment.score(), ctx.sentiment.confidence() * 100),
                new AnalysisProgress.StageDetails("sentiment", Map.of(
                    "score", ctx.sentiment.score().name(),
                    "confidence", ctx.sentiment.confidence(),
                    "summary", ctx.sentiment.summary(),
                    "catalysts", ctx.sentiment.catalysts() != null ? ctx.sentiment.catalysts() : List.of(),
                    "redFlags", ctx.sentiment.redFlags() != null ? ctx.sentiment.redFlags() : List.of(),
                    "articleCount", ctx.sentiment.articleCount()
                ))));
        } catch (Exception e) {
            ctx.sentiment = SentimentResult.create(sym, LocalDate.now(),
                SentimentResult.SentimentScore.NEUTRAL, "LLM sentiment unavailable", "", 0.3, List.of(), List.of());
            emitProgress(emitter, progress, AnalysisProgress.error(4, "LLM sentiment", e.getMessage()));
        }
        logger.info("Stage 4 done in {}ms", System.currentTimeMillis() - s4);
    }

    private void runStage5Technical(SseEmitter emitter, List<AnalysisProgress> progress, String sym, StageContext ctx) {
        long s5 = System.currentTimeMillis();
        try {
            ctx.technical = technicalService.compute(sym);
            emitProgress(emitter, progress, AnalysisProgress.completed(5, "technical analysis",
                String.format("Signal: %s, score: %d", ctx.technical.signal(), ctx.technical.score()),
                new AnalysisProgress.StageDetails("technical", Map.of(
                    "score", ctx.technical.score(),
                    "signal", ctx.technical.signal(),
                    "confidence", ctx.technical.confidence(),
                    "indicators", ctx.technical.indicators()
                ))));
        } catch (Exception e) {
            ctx.technical = new CompositeAnalysis.TechnicalScore(0, "HOLD", 0.0, List.of());
            emitProgress(emitter, progress, AnalysisProgress.error(5, "technical analysis", e.getMessage()));
        }
        logger.info("Stage 5 done in {}ms", System.currentTimeMillis() - s5);
    }

    private void runStage6Fundamentals(SseEmitter emitter, List<AnalysisProgress> progress, String sym, StageContext ctx) {
        long s6 = System.currentTimeMillis();
        try {
            ctx.fundamentals = fundamentalScorer.compute(sym);
            String signal = ctx.fundamentals.score() > 0 ? "BULLISH" : ctx.fundamentals.score() < 0 ? "BEARISH" : "NEUTRAL";
            emitProgress(emitter, progress, AnalysisProgress.completed(6, "fundamentals",
                String.format("Score: %d, %s", ctx.fundamentals.score(), signal),
                new AnalysisProgress.StageDetails("fundamentals", Map.of(
                    "score", ctx.fundamentals.score(),
                    "factors", ctx.fundamentals.factors()
                ))));
        } catch (Exception e) {
            ctx.fundamentals = new CompositeAnalysis.FundamentalScore(0, List.of("Analysis failed: " + e.getMessage()));
            emitProgress(emitter, progress, AnalysisProgress.error(6, "fundamentals", e.getMessage()));
        }
        logger.info("Stage 6 done in {}ms", System.currentTimeMillis() - s6);
    }

    private void runStage7Backtest(SseEmitter emitter, List<AnalysisProgress> progress, String sym, StageContext ctx) {
        long s7 = System.currentTimeMillis();
        try {
            ctx.backtest = backtestScorer.compute(sym);
            emitProgress(emitter, progress, AnalysisProgress.completed(7, "backtest",
                String.format("%d trades, win rate: %.1f%%, return: %.1f%%",
                    ctx.backtest.totalTrades(), ctx.backtest.winRate(), ctx.backtest.totalReturn()),
                new AnalysisProgress.StageDetails("backtest", Map.of(
                    "totalTrades", ctx.backtest.totalTrades(),
                    "winRate", ctx.backtest.winRate(),
                    "profitFactor", ctx.backtest.profitFactor(),
                    "maxDrawdown", ctx.backtest.maxDrawdown(),
                    "totalReturn", ctx.backtest.totalReturn(),
                    "expectancy", ctx.backtest.expectancy(),
                    "hasEnoughData", ctx.backtest.hasEnoughData()
                ))));
        } catch (Exception e) {
            ctx.backtest = new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false);
            emitProgress(emitter, progress, AnalysisProgress.error(7, "backtest", e.getMessage()));
        }
        logger.info("Stage 7 done in {}ms", System.currentTimeMillis() - s7);
    }

    private void runStage8Composite(SseEmitter emitter, List<AnalysisProgress> progress, String sym, StageContext ctx) {
        long s8 = System.currentTimeMillis();
        try {
            ctx.composite = compositeAnalysisService.analyze(sym, ctx.technical, ctx.fundamentals, ctx.backtest, ctx.sentiment);
            emitProgress(emitter, progress, AnalysisProgress.completed(8, "composite score",
                String.format("Score: %d, signal: %s",
                    ctx.composite.compositeScore(), ctx.composite.compositeSignal()),
                new AnalysisProgress.StageDetails("composite", Map.of(
                    "compositeScore", ctx.composite.compositeScore(),
                    "compositeSignal", ctx.composite.compositeSignal(),
                    "compositeConfidence", ctx.composite.compositeConfidence().doubleValue(),
                    "sources", ctx.composite.sources(),
                    "reasoning", ctx.composite.reasoning()
                ))));
        } catch (Exception e) {
            ctx.composite = null;
            emitProgress(emitter, progress, AnalysisProgress.error(8, "composite score", e.getMessage()));
        }
        logger.info("Stage 8 done in {}ms", System.currentTimeMillis() - s8);
    }

    private void runStage9Synthesis(SseEmitter emitter, List<AnalysisProgress> progress, String sym, StageContext ctx) {
        long s9 = System.currentTimeMillis();
        emitProgress(emitter, progress, AnalysisProgress.running(9, "LLM synthesis"));
        try {
            if (ctx.composite != null) {
                SynthesisResult synthesisResult = synthesisService.synthesize(ctx.composite);
                ctx.composite = new CompositeAnalysis(
                    ctx.composite.symbol(), ctx.composite.date(), ctx.composite.compositeScore(),
                    ctx.composite.compositeSignal(), ctx.composite.compositeConfidence(),
                    ctx.composite.sources(), ctx.composite.news(), ctx.composite.technical(),
                    ctx.composite.fundamentals(), ctx.composite.backtest(), ctx.composite.reasoning(),
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

    /**
     * Mutable context shared across pipeline stages.
     */
    private static class StageContext {
        private int candleCount;
        private SentimentResult sentiment;
        private CompositeAnalysis.TechnicalScore technical;
        private CompositeAnalysis.FundamentalScore fundamentals;
        private CompositeAnalysis.BacktestScore backtest;
        private CompositeAnalysis composite;

        public int getCandleCount() {
            return candleCount;
        }

        public void setCandleCount(int candleCount) {
            this.candleCount = candleCount;
        }

        public SentimentResult getSentiment() {
            return sentiment;
        }

        public void setSentiment(SentimentResult sentiment) {
            this.sentiment = sentiment;
        }

        public CompositeAnalysis.TechnicalScore getTechnical() {
            return technical;
        }

        public void setTechnical(CompositeAnalysis.TechnicalScore technical) {
            this.technical = technical;
        }

        public CompositeAnalysis.FundamentalScore getFundamentals() {
            return fundamentals;
        }

        public void setFundamentals(CompositeAnalysis.FundamentalScore fundamentals) {
            this.fundamentals = fundamentals;
        }

        public CompositeAnalysis.BacktestScore getBacktest() {
            return backtest;
        }

        public void setBacktest(CompositeAnalysis.BacktestScore backtest) {
            this.backtest = backtest;
        }

        public CompositeAnalysis getComposite() {
            return composite;
        }

        public void setComposite(CompositeAnalysis composite) {
            this.composite = composite;
        }
    }
}