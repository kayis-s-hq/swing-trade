package com.swingtrade.api.service;

import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CompositeAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(CompositeAnalysisService.class);
    private static final int MIN_CANDLES_FOR_BACKTEST = 100;

    private final SentimentService sentimentService;
    private final NewsIngestionService newsService;
    private final CandleStore candleStore;
    private final DataIngestionService dataIngestionService;
    private final TechnicalAnalysisService technicalService;
    private final FundamentalScorer fundamentalScorer;
    private final BacktestScorer backtestScorer;

    public CompositeAnalysisService(SentimentService sentimentService,
                                    NewsIngestionService newsService,
                                    CandleStore candleStore,
                                    DataIngestionService dataIngestionService,
                                    TechnicalAnalysisService technicalService,
                                    FundamentalScorer fundamentalScorer,
                                    BacktestScorer backtestScorer) {
        this.sentimentService = sentimentService;
        this.newsService = newsService;
        this.candleStore = candleStore;
        this.dataIngestionService = dataIngestionService;
        this.technicalService = technicalService;
        this.fundamentalScorer = fundamentalScorer;
        this.backtestScorer = backtestScorer;
    }

    /**
     * Backward-compatible: compute all sub-analyses internally then compose.
     * Used by POST /api/analysis/analyze endpoint.
     */
    public CompositeAnalysis analyze(String symbol) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        CompositeAnalysis.TechnicalScore technical = technicalService.compute(sym);
        CompositeAnalysis.FundamentalScore fundamentals = fundamentalScorer.compute(sym);
        CompositeAnalysis.BacktestScore backtest = backtestScorer.compute(sym);
        SentimentResult sentiment = safeSentiment(sym);
        return analyze(sym, technical, fundamentals, backtest, sentiment);
    }

    /**
     * Compose from pre-computed sub-analyses.
     * Used by the orchestrator pipeline where each stage runs independently.
     */
    public CompositeAnalysis analyze(String symbol,
                                     CompositeAnalysis.TechnicalScore technical,
                                     CompositeAnalysis.FundamentalScore fundamentals,
                                     CompositeAnalysis.BacktestScore backtest,
                                     SentimentResult sentiment) {
        String sym = symbol.toUpperCase(Locale.ROOT);

        // Auto-pull data if insufficient candles for backtest
        ensureData(sym);

        // News score from sentiment
        CompositeAnalysis.NewsScore news = buildNewsScore(sym, sentiment);

        // Weighted composite
        int composite = computeComposite(news, technical, fundamentals);

        // Signal determination
        String signal = composite > 20 ? "BUY" : composite < -20 ? "SELL" : "HOLD";
        BigDecimal confidence = BigDecimal.valueOf(Math.abs(composite) / 100.0);

        // Source scores for display
        List<CompositeAnalysis.SourceScore> sources = new ArrayList<>();
        if (news != null) {
            sources.add(new CompositeAnalysis.SourceScore("News Sentiment", news.score(), 0.40, "LLM analysis of news articles"));
        }
        sources.add(new CompositeAnalysis.SourceScore("Technical Signal", technical.score(), 0.40, "TA4j indicators"));
        sources.add(new CompositeAnalysis.SourceScore("Fundamentals", fundamentals.score(), 0.20, "Price-based fundamentals"));

        String reasoning = buildReasoning(news, technical, fundamentals, backtest, composite);

        return new CompositeAnalysis(sym, LocalDate.now(), composite, signal, confidence, sources,
            news, technical, fundamentals, backtest, reasoning, null);
    }

    private void ensureData(String symbol) {
        List<OhlcvCandle> candles = candleStore.findBySymbol(symbol);
        if (candles.size() >= MIN_CANDLES_FOR_BACKTEST) {
            return;
        }

        logger.info("Insufficient candles for {} ({}), auto-pulling 3 years of data", symbol, candles.size());
        try {
            dataIngestionService.processStockData(symbol,
                LocalDate.now().minusYears(3),
                LocalDate.now());
            logger.info("Data pull completed for {}, now has {} candles", symbol,
                candleStore.countBySymbol(symbol));
        } catch (Exception e) {
            logger.warn("Auto-pull failed for {}: {}", symbol, e.getMessage());
        }
    }

    private int computeComposite(CompositeAnalysis.NewsScore news,
                                 CompositeAnalysis.TechnicalScore technical,
                                 CompositeAnalysis.FundamentalScore fundamentals) {
        double weightedSum = 0;

        if (news != null && news.articleCount() > 0) {
            weightedSum += news.score() * 0.30;
        }

        weightedSum += technical.score() * 0.40;
        weightedSum += fundamentals.score() * 0.30;

        return Math.round((int) weightedSum);
    }

    private CompositeAnalysis.NewsScore buildNewsScore(String symbol, SentimentResult sentiment) {
        try {
            if (sentiment == null) {
                return new CompositeAnalysis.NewsScore(0, "No sentiment data", List.of(), List.of(), 0);
            }

            int score = switch (sentiment.score()) {
                case POSITIVE -> 75;
                case NEGATIVE -> -75;
                default -> 0;
            };

            List<String> catalysts = sentiment.catalysts() != null ? sentiment.catalysts() : List.of();
            List<String> redFlags = sentiment.redFlags() != null ? sentiment.redFlags() : List.of();

            return new CompositeAnalysis.NewsScore(
                score,
                sentiment.summary() != null ? sentiment.summary() : "No summary available",
                catalysts,
                redFlags,
                sentiment.articleCount()
            );
        } catch (Exception e) {
            logger.warn("News sentiment fetch failed for {}: {}", symbol, e.getMessage());
            return new CompositeAnalysis.NewsScore(0, "News fetch failed", List.of(), List.of(), 0);
        }
    }

    private SentimentResult safeSentiment(String symbol) {
        try {
            return sentimentService.analyzeStockSentiment(symbol, LocalDate.now());
        } catch (Exception e) {
            logger.warn("Sentiment fallback for {}: {}", symbol, e.getMessage());
            return SentimentResult.create(symbol, LocalDate.now(),
                SentimentResult.SentimentScore.NEUTRAL, "Sentiment analysis unavailable", "", 0.3, List.of(), List.of());
        }
    }

    private String buildReasoning(CompositeAnalysis.NewsScore news,
                                  CompositeAnalysis.TechnicalScore technical,
                                  CompositeAnalysis.FundamentalScore fundamentals,
                                  CompositeAnalysis.BacktestScore backtest,
                                  int composite) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Composite score: %d (%s). ", composite, composite > 20 ? "BUY zone" : composite < -20 ? "SELL zone" : "HOLD zone"));

        if (news != null) {
            sb.append(String.format("News sentiment is %s (score: %d). ",
                news.score() > 0 ? "positive" : news.score() < 0 ? "negative" : "neutral", news.score()));
        }
        sb.append(String.format("Technical signal: %s (score: %d, confidence: %.0f%%). ",
            technical.signal(), technical.score(), technical.confidence() * 100));
        sb.append(String.format("Fundamentals: %s (score: %d). ",
            fundamentals.score() > 0 ? "bullish" : fundamentals.score() < 0 ? "bearish" : "neutral", fundamentals.score()));

        if (backtest != null && backtest.hasEnoughData()) {
            sb.append(String.format("Backtest: win rate %.0f%%, profit factor %.2f, max drawdown %.1f%%.",
                backtest.winRate(), backtest.profitFactor(), backtest.maxDrawdown()));
        } else {
            sb.append("Backtest: insufficient data for backtest.");
        }

        return sb.toString();
    }
}