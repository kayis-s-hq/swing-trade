package com.swingtrade.api.service;

import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.SentimentResult.SentimentScore;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeAnalysisServiceTest {

    @Mock
    private SentimentService sentimentService;

    @Mock
    private NewsIngestionService newsService;

    @Mock
    private TechnicalAnalysisService technicalService;

    @Mock
    private FundamentalScorer fundamentalScorer;

    @Mock
    private BacktestScorer backtestScorer;

    @Mock
    private CandleStore candleStore;

    @Mock
    private DataIngestionService dataIngestionService;

    @InjectMocks
    private CompositeAnalysisService service;

    /**
     * Helper to create a SentimentResult with valid data.
     * Note: fetchNewsScore hardcodes articleCount=0 in NewsScore, so
     * articleCount() > 0 always fails and news is always excluded from
     * the composite. The sentiment score still determines the news
     * score value (75 / 0 / -75) used in reasoning.
     */
    private SentimentResult sentimentResult(SentimentScore score, String summary) {
        return new SentimentResult(
                null, "TEST", LocalDate.now(), score, summary,
                "Raw content", 0.7, LocalDate.now(),
                List.of(), List.of("catalyst"),
                null, null, 0
        );
    }

    @Test
    void analyze_bullish_all_dimensions_returns_buy_composite() {
        // composite = 50*0.4 + 25*0.3 = 20+7.5 = 27.5 -> Math.round(27) = 27 -> BUY
        when(sentimentService.analyzeStockSentiment(eq("TEST"), any()))
                .thenReturn(sentimentResult(SentimentScore.POSITIVE, "Bullish outlook"));
        when(technicalService.compute("TEST"))
                .thenReturn(new CompositeAnalysis.TechnicalScore(50, "BUY", 0.5, List.of("EMA bullish")));
        when(fundamentalScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.FundamentalScore(25, List.of("Volume increasing")));
        when(backtestScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.BacktestScore(10, 60.0, 1.5, 5.0, 10.0, 6.0, true));

        // Act
        CompositeAnalysis result = service.analyze("test");

        // Assert
        assertEquals(27, result.compositeScore());
        assertEquals("BUY", result.compositeSignal());
        assertTrue(result.compositeConfidence().doubleValue() > 0);
    }

    @Test
    void analyze_bearish_all_dimensions_returns_sell_composite() {
        // composite = -50*0.4 + -25*0.3 = -20-7.5 = -27.5 -> Math.round(-27) = -27 -> SELL
        when(sentimentService.analyzeStockSentiment(eq("TEST"), any()))
                .thenReturn(sentimentResult(SentimentScore.NEGATIVE, "Bearish outlook"));
        when(technicalService.compute("TEST"))
                .thenReturn(new CompositeAnalysis.TechnicalScore(-50, "SELL", 0.5, List.of("EMA bearish")));
        when(fundamentalScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.FundamentalScore(-25, List.of("Price below SMA50")));
        when(backtestScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.BacktestScore(10, 40.0, 0.8, 10.0, -5.0, -2.0, true));

        // Act
        CompositeAnalysis result = service.analyze("test");

        // Assert
        assertEquals(-27, result.compositeScore());
        assertEquals("SELL", result.compositeSignal());
        assertTrue(result.compositeConfidence().doubleValue() > 0);
    }

    @Test
    void analyze_neutral_all_dimensions_returns_hold_composite() {
        // composite = 0
        when(sentimentService.analyzeStockSentiment(eq("TEST"), any()))
                .thenReturn(sentimentResult(SentimentScore.NEUTRAL, "Neutral outlook"));
        when(technicalService.compute("TEST"))
                .thenReturn(new CompositeAnalysis.TechnicalScore(0, "HOLD", 0.0, List.of()));
        when(fundamentalScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.FundamentalScore(0, List.of("Mixed signals")));
        when(backtestScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false));

        // Act
        CompositeAnalysis result = service.analyze("test");

        // Assert
        assertEquals(0, result.compositeScore());
        assertEquals("HOLD", result.compositeSignal());
        assertEquals(0.0, result.compositeConfidence().doubleValue());
    }

    @Test
    void analyze_news_null_triggers_graceful_degradation() {
        // sentiment throws -> safeSentiment returns NEUTRAL -> articleCount=0 -> news excluded
        // composite = 100*0.4 + 0*0.3 = 40 -> Math.round(40) = 40 -> BUY
        when(sentimentService.analyzeStockSentiment(eq("TEST"), any()))
                .thenThrow(new RuntimeException("Service unavailable"));
        when(technicalService.compute("TEST"))
                .thenReturn(new CompositeAnalysis.TechnicalScore(100, "BUY", 1.0, List.of("Strong bullish")));
        when(fundamentalScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.FundamentalScore(0, List.of("Flat fundamentals")));
        when(backtestScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.BacktestScore(5, 50.0, 1.0, 5.0, 5.0, 5.0, true));

        // Act
        CompositeAnalysis result = service.analyze("test");

        // Assert
        assertEquals(40, result.compositeScore());
        assertEquals("BUY", result.compositeSignal());
    }

    @Test
    void analyze_signal_threshold_buy_above_20() {
        // composite = 53*0.4 + 0*0.3 = 21.2 -> (int)21 -> Math.round(21) = 21 -> BUY
        when(sentimentService.analyzeStockSentiment(eq("TEST"), any()))
                .thenReturn(sentimentResult(SentimentScore.POSITIVE, "Positive"));
        when(technicalService.compute("TEST"))
                .thenReturn(new CompositeAnalysis.TechnicalScore(53, "BUY", 0.53, List.of()));
        when(fundamentalScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.FundamentalScore(0, List.of()));
        when(backtestScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false));

        // Act
        CompositeAnalysis result = service.analyze("test");

        // Assert: composite > 20 -> BUY
        assertEquals(21, result.compositeScore());
        assertEquals("BUY", result.compositeSignal());
    }

    @Test
    void analyze_signal_threshold_sell_below_minus20() {
        // composite = -53*0.4 + 0*0.3 = -21.2 -> (int)-21 -> Math.round(-21) = -21 -> SELL
        when(sentimentService.analyzeStockSentiment(eq("TEST"), any()))
                .thenReturn(sentimentResult(SentimentScore.NEGATIVE, "Negative"));
        when(technicalService.compute("TEST"))
                .thenReturn(new CompositeAnalysis.TechnicalScore(-53, "SELL", 0.53, List.of()));
        when(fundamentalScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.FundamentalScore(0, List.of()));
        when(backtestScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false));

        // Act
        CompositeAnalysis result = service.analyze("test");

        // Assert: composite < -20 -> SELL
        assertEquals(-21, result.compositeScore());
        assertEquals("SELL", result.compositeSignal());
    }

    @Test
    void analyze_signal_threshold_hold_at_boundary() {
        // composite = 30*0.4 + 0*0.3 = 12.0 -> Math.round(12) = 12 -> HOLD
        when(sentimentService.analyzeStockSentiment(eq("TEST"), any()))
                .thenReturn(sentimentResult(SentimentScore.POSITIVE, "Positive"));
        when(technicalService.compute("TEST"))
                .thenReturn(new CompositeAnalysis.TechnicalScore(30, "BUY", 0.3, List.of()));
        when(fundamentalScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.FundamentalScore(0, List.of()));
        when(backtestScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false));

        // Act
        CompositeAnalysis result = service.analyze("test");

        // Assert: composite = 12 -> HOLD
        assertEquals(12, result.compositeScore());
        assertEquals("HOLD", result.compositeSignal());
    }

    @Test
    void analyze_sources_included_for_all_dimensions() {
        // Setup
        when(sentimentService.analyzeStockSentiment(eq("TEST"), any()))
                .thenReturn(sentimentResult(SentimentScore.POSITIVE, "Positive summary"));
        when(technicalService.compute("TEST"))
                .thenReturn(new CompositeAnalysis.TechnicalScore(50, "BUY", 0.5, List.of("EMA bullish")));
        when(fundamentalScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.FundamentalScore(25, List.of("Volume increasing")));
        when(backtestScorer.compute("TEST"))
                .thenReturn(new CompositeAnalysis.BacktestScore(10, 60.0, 1.5, 5.0, 10.0, 6.0, true));

        // Act
        CompositeAnalysis result = service.analyze("test");

        // Assert
        assertNotNull(result.sources());
        assertFalse(result.sources().isEmpty());
        // Should have 3 source scores: News Sentiment, Technical Signal, Fundamentals
        assertEquals(3, result.sources().size());
        // Verify source names
        assertTrue(result.sources().stream().anyMatch(s -> "News Sentiment".equals(s.name())));
        assertTrue(result.sources().stream().anyMatch(s -> "Technical Signal".equals(s.name())));
        assertTrue(result.sources().stream().anyMatch(s -> "Fundamentals".equals(s.name())));
    }
}
