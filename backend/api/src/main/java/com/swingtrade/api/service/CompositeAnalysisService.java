package com.swingtrade.api.service;

import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.SentimentResult;
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
    private final OhlcvCandleRepository candleRepository;
    private final DataIngestionService dataIngestionService;
    private final TechnicalAnalysisService technicalService;
    private final FundamentalScorer fundamentalScorer;
    private final BacktestScorer backtestScorer;

    public CompositeAnalysisService(SentimentService sentimentService,
                                    NewsIngestionService newsService,
                                    OhlcvCandleRepository candleRepository,
                                    DataIngestionService dataIngestionService,
                                    TechnicalAnalysisService technicalService,
                                    FundamentalScorer fundamentalScorer,
                                    BacktestScorer backtestScorer) {
        this.sentimentService = sentimentService;
        this.newsService = newsService;
        this.candleRepository = candleRepository;
        this.dataIngestionService = dataIngestionService;
        this.technicalService = technicalService;
        this.fundamentalScorer = fundamentalScorer;
        this.backtestScorer = backtestScorer;
    }

    public CompositeAnalysis analyze(String symbol) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        LocalDate date = LocalDate.now();

        // Auto-pull data if insufficient candles for backtest
        ensureData(sym);

        // Parallel fetch
        CompositeAnalysis.NewsScore news = fetchNewsScore(sym);
        CompositeAnalysis.TechnicalScore technical = technicalService.compute(sym);
        CompositeAnalysis.FundamentalScore fundamentals = fundamentalScorer.compute(sym);
        CompositeAnalysis.BacktestScore backtest = backtestScorer.compute(sym);

        // Weighted composite with graceful degradation
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

        return new CompositeAnalysis(sym, date, composite, signal, confidence, sources,
            news, technical, fundamentals, backtest, reasoning);
    }

    /**
     * Auto-pull historical data if the symbol has insufficient candles for backtesting.
     * Backtest needs at least MIN_CANDLES_FOR_BACKTEST candles; if fewer exist,
     * trigger a 3-year backfill via the data ingestion service.
     */
    private void ensureData(String symbol) {
        List<OhlcvCandleEntity> candles = candleRepository.findAllBySymbolOrderByDateDesc(symbol);
        if (candles.size() >= MIN_CANDLES_FOR_BACKTEST) {
            return; // enough data already
        }

        logger.info("Insufficient candles for {} ({}), auto-pulling 3 years of data", symbol, candles.size());
        try {
            dataIngestionService.processStockData(symbol,
                LocalDate.now().minusYears(3),
                LocalDate.now());
            logger.info("Data pull completed for {}, now has {} candles", symbol,
                candleRepository.countBySymbol(symbol));
        } catch (Exception e) {
            logger.warn("Auto-pull failed for {}: {}", symbol, e.getMessage());
        }
    }

    private int computeComposite(CompositeAnalysis.NewsScore news,
                                 CompositeAnalysis.TechnicalScore technical,
                                 CompositeAnalysis.FundamentalScore fundamentals) {
        double weightedSum = 0;

        // News: 30% weight (only if valid)
        if (news != null && news.articleCount() > 0) {
            weightedSum += news.score() * 0.30;
        }

        // Technical: 40% weight (always available if data exists)
        weightedSum += technical.score() * 0.40;

        // Fundamentals: 30% weight (always available if data exists)
        weightedSum += fundamentals.score() * 0.30;

        return Math.round((int) weightedSum);
    }

    private CompositeAnalysis.NewsScore fetchNewsScore(String symbol) {
        try {
            SentimentResult sentiment = sentimentService.analyzeStockSentiment(symbol, LocalDate.now());
            if (sentiment == null) {
                return new CompositeAnalysis.NewsScore(0, "No sentiment data", List.of(), List.of(), 0);
            }

            // Map categorical score to numeric: POSITIVE=75, NEUTRAL=0, NEGATIVE=-75
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
                0 // article count from sentiment service if available
            );
        } catch (Exception e) {
            logger.warn("News sentiment fetch failed for {}: {}", symbol, e.getMessage());
            return null; // null triggers graceful degradation
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