package com.swingtrade.api.service;

import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.data.repository.SignalSelectionRepository;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.SignalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Signal tournament: for each symbol/day, picks the single BUY signal (highest confidence across
 * all active variants) that the rest of the pipeline - sentiment, LLM analysis and the
 * dedicated "selected" paper portfolio - follows. Every variant still trades its own shadow book
 * independently; this only decides which signal drives the selected book.
 */
@Service
public class SignalArbiter {

    private static final Logger logger = LoggerFactory.getLogger(SignalArbiter.class);

    private final SignalSelectionRepository repository;
    private final SignalStore signalStore;

    public SignalArbiter(SignalSelectionRepository repository, SignalStore signalStore) {
        this.repository = repository;
        this.signalStore = signalStore;
    }

    /**
     * Highest-confidence BUY wins; ties break on variantId (stable, deterministic). Only outcomes
     * whose signal row was persisted are eligible, since the selected book needs a real signal.
     */
    static Optional<SignalPipeline.VariantSignalOutcome> pick(List<SignalPipeline.VariantSignalOutcome> outcomes) {
        return outcomes.stream()
            .filter(o -> o.type() == Signal.SignalType.BUY && o.persisted() && o.confidence() != null)
            .max(Comparator.<SignalPipeline.VariantSignalOutcome, java.math.BigDecimal>comparing(
                    SignalPipeline.VariantSignalOutcome::confidence)
                .thenComparing(SignalPipeline.VariantSignalOutcome::variantId, Comparator.reverseOrder()));
    }

    /**
     * Records the tournament for {@code symbol} on {@code date}. Returns the winning selection, or
     * empty when no variant produced an eligible BUY. A selection already EXECUTED or BLOCKED for
     * this symbol/day is left untouched so a re-run cannot double-trade or re-open a blocked pick.
     */
    public Optional<SignalSelectionEntity> arbitrate(String symbol, LocalDate date,
                                                     List<SignalPipeline.VariantSignalOutcome> outcomes) {
        Optional<SignalPipeline.VariantSignalOutcome> winner = pick(outcomes);
        if (winner.isEmpty()) {
            return Optional.empty();
        }
        SignalPipeline.VariantSignalOutcome w = winner.get();

        Optional<SignalSelectionEntity> existing = repository.findBySymbolAndSelectionDate(symbol, date);
        if (existing.isPresent() && !SignalSelectionEntity.PENDING.equals(existing.get().getStatus())) {
            return existing;
        }

        Long signalId = signalStore.findLatestBySymbolAndStrategy(symbol, w.variantId())
            .filter(s -> date.equals(s.date()))
            .map(Signal::id)
            .orElse(null);

        List<Map<String, Object>> slate = new ArrayList<>();
        for (SignalPipeline.VariantSignalOutcome o : outcomes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("variantId", o.variantId());
            row.put("version", o.strategyVersion());
            row.put("signal", o.type().name());
            row.put("confidence", o.confidence());
            row.put("selected", o == w);
            slate.add(row);
        }
        long buyCount = outcomes.stream().filter(o -> o.type() == Signal.SignalType.BUY).count();
        String reason = "highest confidence " + w.confidence().toPlainString() + " among " + buyCount + " BUY(s)";

        SignalSelectionEntity entity = new SignalSelectionEntity(symbol, date, w.variantId(),
            w.strategyVersion(), signalId, w.confidence(), slate, reason);
        existing.ifPresent(e -> repository.delete(e));
        repository.flush();
        SignalSelectionEntity saved = repository.save(entity);
        logger.info("Signal tournament for {} on {}: {} wins ({})", symbol, date, w.variantId(), reason);
        return Optional.of(saved);
    }

    /** The most recent selection for {@code symbol} in the given status (PENDING/EXECUTED/BLOCKED). */
    public Optional<SignalSelectionEntity> findLatest(String symbol, String status) {
        return repository.findBySymbolAndStatusOrderBySelectionDateDesc(symbol, status)
            .stream().findFirst();
    }

    public void markStatus(SignalSelectionEntity selection, String status, String detail) {
        selection.markStatus(status, detail);
        repository.save(selection);
    }

    public List<SignalSelectionEntity> findByDate(LocalDate date) {
        return repository.findBySelectionDateOrderBySymbolAsc(date);
    }

    public List<SignalSelectionEntity> findBetween(LocalDate from, LocalDate to) {
        return repository.findBySelectionDateBetweenOrderBySelectionDateDescSymbolAsc(from, to);
    }
}
