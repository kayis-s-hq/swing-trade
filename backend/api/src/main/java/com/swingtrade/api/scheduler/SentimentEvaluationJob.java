package com.swingtrade.api.scheduler;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.SentimentAccuracy;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SentimentAccuracyStore;
import com.swingtrade.domain.store.SentimentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Nightly job that evaluates LLM sentiment accuracy against actual market returns.
 * Picks up unevaluated sentiment results, computes returns over 1/5/21-day windows,
 * determines ground truth labels, and stores accuracy records.
 */
@Component
public class SentimentEvaluationJob {

    private static final Logger log = LoggerFactory.getLogger(SentimentEvaluationJob.class);
    private static final BigDecimal RETURN_THRESHOLD = new BigDecimal("0.005");

    private final CandleStore candleStore;
    private final SentimentAccuracyStore accuracyStore;
    private final SentimentStore sentimentStore;
    private final boolean schedulerEnabled;

    // Last run tracking
    private final AtomicReference<LocalDateTime> lastRun = new AtomicReference<>(null);
    private final AtomicReference<String> lastStatus = new AtomicReference<>(null);
    private final AtomicReference<Integer> lastCount = new AtomicReference<>(null);

    public SentimentEvaluationJob(CandleStore candleStore,
                                  SentimentAccuracyStore accuracyStore,
                                  SentimentStore sentimentStore,
                                  @Value("${app.features.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.candleStore = candleStore;
        this.accuracyStore = accuracyStore;
        this.sentimentStore = sentimentStore;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    public void evaluatePendingSentiments() {
        if (!schedulerEnabled) {
            log.debug("Scheduler disabled (app.features.scheduler.enabled=false) — skipping sentiment evaluation");
            return;
        }
        runEvaluation();
    }

    /**
     * Manual trigger for evaluation — callable from API.
     */
    public void triggerEvaluation() {
        log.info("Manual trigger: starting sentiment evaluation job");
        runEvaluation();
    }

    private void runEvaluation() {
        LocalDateTime now = LocalDateTime.now();
        lastRun.set(now);
        try {
            int processed = doEvaluate();
            lastStatus.set("OK");
            lastCount.set(processed);
            log.info("Sentiment evaluation job complete: processed {} records", processed);
        } catch (Exception e) {
            lastStatus.set("FAILED: " + e.getMessage());
            lastCount.set(0);
            log.error("Sentiment evaluation job failed", e);
        }
    }

    private int doEvaluate() {
        LocalDate today = LocalDate.now();
        int processed = 0;

        List<SentimentResult> pending = sentimentStore.findAllByDateBeforeOrderByDateAsc(today);

        for (SentimentResult sentiment : pending) {
            // Skip if already evaluated
            if (accuracyStore.existsBySymbolAndAnalysisDate(sentiment.symbol(), sentiment.date())) {
                continue;
            }

            try {
                evaluateSingle(sentiment);
                processed++;
            } catch (Exception e) {
                log.warn("Failed to evaluate sentiment for {} on {}: {}",
                    sentiment.symbol(), sentiment.date(), e.getMessage());
            }
        }

        return processed;
    }

    private void evaluateSingle(SentimentResult sentiment) {
        String symbol = sentiment.symbol();
        LocalDate analysisDate = sentiment.date();

        // Find entry candle (first trading day on or after analysis date)
        Optional<OhlcvCandle> entryOpt = candleStore.findFirstBySymbolAndDateAfterOrderByDateAsc(symbol, analysisDate);
        if (entryOpt.isEmpty()) return;
        OhlcvCandle entry = entryOpt.get();

        // Find exit candles at +1, +5, +21 trading days
        OhlcvCandle exit1 = findNthTradingDay(symbol, entry.date(), 1);
        OhlcvCandle exit5 = findNthTradingDay(symbol, entry.date(), 5);
        OhlcvCandle exit21 = findNthTradingDay(symbol, entry.date(), 21);

        if (exit1 == null) return; // need at least 1-day return

        // Compute returns
        BigDecimal return1d = computeReturn(entry, exit1);
        BigDecimal return5d = exit5 != null ? computeReturn(entry, exit5) : null;
        BigDecimal return21d = exit21 != null ? computeReturn(entry, exit21) : null;

        // Determine ground truth from 1-day return
        String label = classifyReturn(return1d);
        boolean wasCorrect = wasCorrect(sentiment.score().name(), label);

        // Compute market regime
        String regime = computeMarketRegime(symbol, entry);

        // Map LLM score to numeric
        float numericScore = mapToNumeric(sentiment.score().name());

        // Build and save accuracy record
        SentimentAccuracy record = new SentimentAccuracy(
            null,
            symbol,
            analysisDate,
            sentiment.score().name(),
            sentiment.confidence() != null ? sentiment.confidence().floatValue() : 0.0f,
            numericScore,
            return1d,
            return5d,
            return21d,
            label,
            wasCorrect,
            null, // pnlPct
            regime,
            sentiment.promptHash(),
            sentiment.modelVersion(),
            java.time.LocalDateTime.now()
        );

        accuracyStore.save(record);
        log.debug("Evaluated sentiment for {} on {}: {} (correct={})",
            symbol, analysisDate, label, wasCorrect);
    }

    private OhlcvCandle findNthTradingDay(String symbol, LocalDate after, int n) {
        return candleStore.findNthBySymbolAndDateAfterOrderByDateAsc(symbol, after, n).orElse(null);
    }

    private BigDecimal computeReturn(OhlcvCandle entry, OhlcvCandle exit) {
        if (entry.close() == null || exit.close() == null) return null;
        BigDecimal diff = exit.close().subtract(entry.close());
        return diff.divide(entry.close(), 6, RoundingMode.HALF_UP);
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
        return llmUp && actualUp || llmDown && actualDown;
    }

    private float mapToNumeric(String llmScore) {
        return switch (llmScore) {
            case "POSITIVE" -> 0.5f;
            case "NEGATIVE" -> -0.5f;
            default -> 0.0f;
        };
    }

    private String computeMarketRegime(String symbol, OhlcvCandle reference) {
        List<OhlcvCandle> candles = candleStore.findLastNBySymbolBeforeDateAsc(
            symbol, reference.date(), 200);
        if (candles.size() < 200) return "NEUTRAL";

        // candles are in ascending order — last 200 trading days up to reference
        BigDecimal sma200 = candles.stream()
            .map(OhlcvCandle::close)
            .filter(p -> p != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(200), 4, RoundingMode.HALF_UP);

        BigDecimal price = reference.close();
        if (price == null) return "NEUTRAL";

        BigDecimal bullThreshold = sma200.multiply(new BigDecimal("1.02"));
        BigDecimal bearThreshold = sma200.multiply(new BigDecimal("0.98"));

        if (price.compareTo(bullThreshold) > 0) return "BULL";
        if (price.compareTo(bearThreshold) < 0) return "BEAR";
        return "NEUTRAL";
    }

    // --- Job status ---

    public LocalDateTime getLastRun() {
        return lastRun.get();
    }

    public String getLastStatus() {
        return lastStatus.get();
    }

    public Integer getLastCount() {
        return lastCount.get();
    }
}