package com.swingtrade.data.service;

import com.swingtrade.data.entity.SentimentAccuracyEntity;
import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.repository.SentimentAccuracyRepository;
import com.swingtrade.data.repository.SentimentResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Tracks LLM sentiment prediction accuracy against actual trade outcomes.
 * Listens for PositionClosedEvent and records was_correct based on sentiment vs outcome.
 */
@Service
public class SentimentAccuracyService {

    private static final Logger log = LoggerFactory.getLogger(SentimentAccuracyService.class);

    private final SentimentAccuracyRepository accuracyRepo;
    private final SentimentResultRepository sentimentRepo;

    public SentimentAccuracyService(SentimentAccuracyRepository accuracyRepo,
                                    SentimentResultRepository sentimentRepo) {
        this.accuracyRepo = accuracyRepo;
        this.sentimentRepo = sentimentRepo;
    }

    /**
     * Called when a paper position closes.
     */
    @EventListener
    public void onPositionClosed(com.swingtrade.domain.PositionClosedEvent event) {
        try {
            recordOutcome(event.getSymbol(), event.getSignalDate(), event.getOutcome(), event.getPnlPct());
        } catch (Exception e) {
            log.warn("Failed to track sentiment accuracy for {}: {}", event.getSymbol(), e.getMessage());
        }
    }

    public void recordOutcome(String symbol, LocalDate signalDate, String outcome, BigDecimal pnlPct) {
        if (accuracyRepo.findBySymbolAndSignalDate(symbol, signalDate).isPresent()) {
            return; // already recorded
        }

        Optional<SentimentResultEntity> sentimentOpt = sentimentRepo.findBySymbolAndDate(symbol, signalDate);
        boolean wasCorrect = false;

        if (sentimentOpt.isPresent()) {
            String sentimentScore = sentimentOpt.get().getSentimentScore();
            wasCorrect = (sentimentScore.equals("POSITIVE") && "TARGET_HIT".equals(outcome))
                      || (sentimentScore.equals("NEGATIVE") && "STOP_LOSS".equals(outcome));
        }

        SentimentAccuracyEntity entity = new SentimentAccuracyEntity();
        entity.setSymbol(symbol);
        entity.setSignalDate(signalDate);
        entity.setSentimentScore(sentimentScoreFrom(sentimentOpt));
        entity.setActualOutcome(outcome);
        entity.setWasCorrect(wasCorrect);
        entity.setPnlPct(pnlPct);
        entity.setRecordedAt(java.time.LocalDateTime.now());
        accuracyRepo.save(entity);

        log.info("Tracked sentiment accuracy for {} on {}: {} (correct={})",
            symbol, signalDate, outcome, wasCorrect);
    }

    public AccuracyStats getAccuracyStats() {
        long total = accuracyRepo.countAll();
        long correct = accuracyRepo.countCorrect();
        double accuracyPct = total > 0 ? (double) correct / total * 100 : 0.0;

        Map<String, Integer> bySentiment = new HashMap<>();
        Map<String, Integer> bySymbol = new HashMap<>();

        for (SentimentAccuracyEntity e : accuracyRepo.findAll()) {
            bySentiment.merge(e.getSentimentScore(), 1, Integer::sum);
            bySymbol.merge(e.getSymbol(), 1, Integer::sum);
        }

        return new AccuracyStats(
            (int) total,
            (int) correct,
            Math.round(accuracyPct * 100.0) / 100.0,
            bySentiment,
            bySymbol
        );
    }

    private String sentimentScoreFrom(Optional<SentimentResultEntity> opt) {
        return opt.map(e -> e.getSentimentScore()).orElse("NEUTRAL");
    }

    public record AccuracyStats(
        int total,
        int correct,
        double accuracyPct,
        Map<String, Integer> bySentiment,
        Map<String, Integer> bySymbol
    ) {}
}