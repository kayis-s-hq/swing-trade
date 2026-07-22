package com.swingtrade.api.scheduler;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.entity.SentimentAccuracyEntity;
import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.SentimentAccuracyRepository;
import com.swingtrade.data.repository.SentimentResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Nightly job that evaluates LLM sentiment accuracy against actual market returns.
 * Picks up unevaluated sentiment results, computes returns over 1/5/21-day windows,
 * determines ground truth labels, and stores accuracy records.
 */
@Component
public class SentimentEvaluationJob {

    private static final Logger log = LoggerFactory.getLogger(SentimentEvaluationJob.class);
    private static final BigDecimal RETURN_THRESHOLD = new BigDecimal("0.005");

    private final OhlcvCandleRepository candleRepo;
    private final SentimentAccuracyRepository accuracyRepo;
    private final SentimentResultRepository sentimentRepo;

    public SentimentEvaluationJob(OhlcvCandleRepository candleRepo,
                                  SentimentAccuracyRepository accuracyRepo,
                                  SentimentResultRepository sentimentRepo) {
        this.candleRepo = candleRepo;
        this.accuracyRepo = accuracyRepo;
        this.sentimentRepo = sentimentRepo;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void evaluatePendingSentiments() {
        log.info("Starting sentiment evaluation job");
        LocalDate today = LocalDate.now();
        int processed = 0;

        List<SentimentResultEntity> pending = sentimentRepo.findAllByDateBeforeOrderByDateAsc(today);

        for (SentimentResultEntity sentiment : pending) {
            // Skip if already evaluated
            if (accuracyRepo.existsBySymbolAndAnalysisDate(sentiment.getSymbol(), sentiment.getDate())) {
                continue;
            }

            try {
                evaluateSingle(sentiment);
                processed++;
            } catch (Exception e) {
                log.warn("Failed to evaluate sentiment for {} on {}: {}",
                    sentiment.getSymbol(), sentiment.getDate(), e.getMessage());
            }
        }

        log.info("Sentiment evaluation job complete: processed {} records", processed);
    }

    private void evaluateSingle(SentimentResultEntity sentiment) {
        String symbol = sentiment.getSymbol();
        LocalDate analysisDate = sentiment.getDate();

        // Find entry candle (first trading day on or after analysis date)
        Optional<OhlcvCandleEntity> entryOpt = candleRepo.findFirstBySymbolAndDateAfterOrderByDateAsc(symbol, analysisDate);
        if (entryOpt.isEmpty()) return;
        OhlcvCandleEntity entry = entryOpt.get();

        // Find exit candles at +1, +5, +21 trading days
        OhlcvCandleEntity exit1 = findNthTradingDay(symbol, entry.getDate(), 1);
        OhlcvCandleEntity exit5 = findNthTradingDay(symbol, entry.getDate(), 5);
        OhlcvCandleEntity exit21 = findNthTradingDay(symbol, entry.getDate(), 21);

        if (exit1 == null) return; // need at least 1-day return

        // Compute returns
        BigDecimal return1d = computeReturn(entry, exit1);
        BigDecimal return5d = exit5 != null ? computeReturn(entry, exit5) : null;
        BigDecimal return21d = exit21 != null ? computeReturn(entry, exit21) : null;

        // Determine ground truth from 1-day return
        String label = classifyReturn(return1d);
        boolean wasCorrect = wasCorrect(sentiment.getSentimentScore(), label);

        // Compute market regime
        String regime = computeMarketRegime(symbol, entry);

        // Map LLM score to numeric
        float numericScore = mapToNumeric(sentiment.getSentimentScore());

        // Build and save accuracy record
        SentimentAccuracyEntity record = new SentimentAccuracyEntity();
        record.setSymbol(symbol);
        record.setAnalysisDate(analysisDate);
        record.setLlmScore(sentiment.getSentimentScore());
        record.setLlmConfidence(sentiment.getConfidence() != null ? sentiment.getConfidence().floatValue() : 0.0f);
        record.setNumericScore(numericScore);
        record.setActualReturn1d(return1d);
        record.setActualReturn5d(return5d);
        record.setActualReturn21d(return21d);
        record.setGroundTruthLabel(label);
        record.setWasCorrect(wasCorrect);
        record.setMarketRegime(regime);
        record.setPromptHash(sentiment.getPromptHash());
        record.setModelVersion(sentiment.getModelVersion());
        record.setEvaluatedAt(java.time.LocalDateTime.now());

        accuracyRepo.save(record);
        log.debug("Evaluated sentiment for {} on {}: {} (correct={})",
            symbol, analysisDate, label, wasCorrect);
    }

    private OhlcvCandleEntity findNthTradingDay(String symbol, LocalDate after, int n) {
        return candleRepo.findNthBySymbolAndDateAfterOrderByDateAsc(symbol, after, n).orElse(null);
    }

    private BigDecimal computeReturn(OhlcvCandleEntity entry, OhlcvCandleEntity exit) {
        if (entry.getClosePrice() == null || exit.getClosePrice() == null) return null;
        BigDecimal diff = exit.getClosePrice().subtract(entry.getClosePrice());
        return diff.divide(entry.getClosePrice(), 6, RoundingMode.HALF_UP);
    }

    private String classifyReturn(BigDecimal returnVal) {
        if (returnVal == null) return null;
        int cmp = returnVal.compareTo(RETURN_THRESHOLD);
        if (cmp >= 0) return "UP";
        if (cmp <= -1) return "DOWN";
        return "FLAT";
    }

    private boolean wasCorrect(String llmScore, String groundTruth) {
        if (llmScore == null || groundTruth == null) return false;
        if ("FLAT".equals(groundTruth)) return true; // neutral prediction is always "correct" for flat
        boolean llmUp = "POSITIVE".equals(llmScore);
        boolean llmDown = "NEGATIVE".equals(llmScore);
        boolean actualUp = "UP".equals(groundTruth);
        boolean actualDown = "DOWN".equals(groundTruth);
        return (llmUp && actualUp) || (llmDown && actualDown);
    }

    private float mapToNumeric(String llmScore) {
        return switch (llmScore) {
            case "POSITIVE" -> 0.5f;
            case "NEGATIVE" -> -0.5f;
            default -> 0.0f;
        };
    }

    private String computeMarketRegime(String symbol, OhlcvCandleEntity reference) {
        List<OhlcvCandleEntity> candles = candleRepo.findAllBySymbolOrderByDateDesc(symbol);
        if (candles.size() < 200) return "NEUTRAL";

        // Compute SMA of last 200 close prices
        BigDecimal sma200 = candles.stream()
            .limit(200)
            .map(OhlcvCandleEntity::getClosePrice)
            .filter(p -> p != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(200), 4, RoundingMode.HALF_UP);

        BigDecimal price = reference.getClosePrice();
        if (price == null) return "NEUTRAL";

        BigDecimal bullThreshold = sma200.multiply(new BigDecimal("1.02"));
        BigDecimal bearThreshold = sma200.multiply(new BigDecimal("0.98"));

        if (price.compareTo(bullThreshold) > 0) return "BULL";
        if (price.compareTo(bearThreshold) < 0) return "BEAR";
        return "NEUTRAL";
    }
}