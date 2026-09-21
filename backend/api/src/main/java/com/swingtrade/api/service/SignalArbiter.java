package com.swingtrade.api.service;

import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.data.repository.SignalSelectionRepository;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.service.PortfolioQueryService;
import com.swingtrade.domain.store.SignalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/**
 * Signal tournament: for each symbol/day, picks the single BUY signal that the rest of the
 * pipeline - sentiment, LLM analysis and the dedicated "selected" paper portfolio - follows.
 * Existing variant positions take precedence so their strategy owns the next decision, followed by
 * the configured champion and finally the configured arbitration rule. Every variant still trades
 * its own shadow book independently; this only decides which signal drives the selected book.
 */
@Service
public class SignalArbiter {

    private static final Logger logger = LoggerFactory.getLogger(SignalArbiter.class);

    /** A variant needs at least this many closed shadow trades before its track record counts. */
    static final int MIN_EVIDENCE_TRADES = 5;

    private final SignalSelectionRepository repository;
    private final SignalStore signalStore;
    private final PortfolioQueryService paperPortfolioService;
    private final PositionRepository positionRepository;
    private final StrategyConfigRepository strategyConfigRepository;
    private final ArbitrationRule rule;

    @Autowired
    public SignalArbiter(SignalSelectionRepository repository, SignalStore signalStore,
                         PortfolioQueryService paperPortfolioService,
                         PositionRepository positionRepository,
                         StrategyConfigRepository strategyConfigRepository,
                         @Value("${strategy.arbitration.rule:HIGHEST_CONFIDENCE}") String ruleName) {
        this.repository = repository;
        this.signalStore = signalStore;
        this.paperPortfolioService = paperPortfolioService;
        this.positionRepository = positionRepository;
        this.strategyConfigRepository = strategyConfigRepository;
        this.rule = ArbitrationRule.valueOf(ruleName.trim().toUpperCase());
    }

    /** One competing BUY: which variant and how confident. */
    record Candidate(String variantId, BigDecimal confidence) {}

    /**
     * Mean return (%) of a variant's closed shadow trades that exited before {@code asOf}, or empty
     * when it has fewer than {@link #MIN_EVIDENCE_TRADES} - too little to rank on. Filtering on
     * {@code asOf} keeps replays free of look-ahead.
     */
    static Optional<Double> evidence(List<ShadowClosedTrade> trades, LocalDate asOf) {
        List<Double> returns = trades.stream()
            .filter(t -> t.exitDate() != null && t.exitDate().isBefore(asOf) && t.pnl() != null
                && t.entryPrice() != null && t.entryPrice().signum() > 0 && t.quantity() > 0)
            .map(t -> t.pnl().doubleValue() / (t.entryPrice().doubleValue() * t.quantity()) * 100.0)
            .toList();
        if (returns.size() < MIN_EVIDENCE_TRADES) {
            return Optional.empty();
        }
        return Optional.of(returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
    }

    /**
     * Chooses the winning variant among {@code candidates} under {@code rule}. For EVIDENCE_RANKED,
     * a variant without enough track record scores 0.0 (neutral: below proven winners, above proven
     * losers); ties fall back to confidence, then variantId for determinism.
     */
    static Optional<String> pickVariant(List<Candidate> candidates, ArbitrationRule rule,
                                        Map<String, Double> evidenceByVariant) {
        Comparator<Candidate> byConfidence = Comparator.comparing(Candidate::confidence);
        Comparator<Candidate> order = rule == ArbitrationRule.EVIDENCE_RANKED
            ? Comparator.<Candidate>comparingDouble(c -> evidenceByVariant.getOrDefault(c.variantId(), 0.0))
                .thenComparing(byConfidence)
            : byConfidence;
        return candidates.stream()
            .max(order.thenComparing(Candidate::variantId, Comparator.reverseOrder()))
            .map(Candidate::variantId);
    }

    private Map<String, Double> currentEvidence(List<VariantSignalOutcome> outcomes) {
        Map<String, Double> result = new HashMap<>();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        for (VariantSignalOutcome o : outcomes) {
            evidence(paperPortfolioService.findClosedTrades(o.variantId()), tomorrow)
                .ifPresent(v -> result.put(o.variantId(), v));
        }
        return result;
    }

    /**
     * Highest-confidence BUY wins; ties break on variantId (stable, deterministic). Only outcomes
     * whose signal row was persisted are eligible, since the selected book needs a real signal.
     */
    static Optional<VariantSignalOutcome> pick(List<VariantSignalOutcome> outcomes) {
        return pick(outcomes, ArbitrationRule.HIGHEST_CONFIDENCE, Map.of());
    }

    static Optional<VariantSignalOutcome> pick(List<VariantSignalOutcome> outcomes,
                                                              ArbitrationRule rule,
                                                              Map<String, Double> evidenceByVariant) {
        return pick(outcomes, rule, evidenceByVariant, Set.of(), Optional.empty());
    }

    /**
     * Selects a BUY for the selected paper book using the live strategy preference order:
     * an existing variant position first (so the position's strategy owns its exit), then the
     * configured champion, then the normal arbitration rule. The final comparator remains
     * deterministic, including when more than one variant has an open position.
     */
    static Optional<VariantSignalOutcome> pick(List<VariantSignalOutcome> outcomes,
                                               ArbitrationRule rule,
                                               Map<String, Double> evidenceByVariant,
                                               Set<String> openPositionVariants,
                                               Optional<String> championVariant) {
        List<VariantSignalOutcome> eligible = outcomes.stream()
            .filter(o -> o.type() == Signal.SignalType.BUY && o.persisted() && o.confidence() != null)
            .toList();
        Optional<VariantSignalOutcome> openPositionWinner = eligible.stream()
            .filter(o -> openPositionVariants.contains(o.variantId()))
            .sorted(Comparator.comparing(VariantSignalOutcome::variantId))
            .findFirst();
        if (openPositionWinner.isPresent()) {
            return openPositionWinner;
        }
        if (championVariant.isPresent()) {
            Optional<VariantSignalOutcome> championWinner = eligible.stream()
                .filter(o -> championVariant.get().equals(o.variantId()))
                .findFirst();
            if (championWinner.isPresent()) {
                return championWinner;
            }
        }
        return pickVariant(eligible.stream().map(o -> new Candidate(o.variantId(), o.confidence())).toList(),
                rule, evidenceByVariant)
            .flatMap(id -> eligible.stream().filter(o -> o.variantId().equals(id)).findFirst());
    }

    /**
     * Records the tournament for {@code symbol} on {@code date}. Returns the winning selection, or
     * empty when no variant produced an eligible BUY. A selection already EXECUTED or BLOCKED for
     * this symbol/day is left untouched so a re-run cannot double-trade or re-open a blocked pick.
     */
    public Optional<SignalSelectionEntity> arbitrate(String symbol, LocalDate date,
                                                     List<VariantSignalOutcome> outcomes) {
        Optional<VariantSignalOutcome> winner = pick(outcomes, rule,
            rule == ArbitrationRule.EVIDENCE_RANKED ? currentEvidence(outcomes) : Map.of(),
            openPositionVariants(symbol), currentChampionVariant());
        if (winner.isEmpty()) {
            return Optional.empty();
        }
        VariantSignalOutcome w = winner.get();

        Optional<SignalSelectionEntity> existing = repository.findBySymbolAndSelectionDate(symbol, date);
        if (existing.isPresent() && !SignalSelectionEntity.PENDING.equals(existing.get().getStatus())) {
            return existing;
        }

        Long signalId = signalStore.findLatestBySymbolAndStrategy(symbol, w.variantId())
            .filter(s -> date.equals(s.date()))
            .map(Signal::id)
            .orElse(null);

        List<Map<String, Object>> slate = new ArrayList<>();
        for (VariantSignalOutcome o : outcomes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("variantId", o.variantId());
            row.put("version", o.strategyVersion());
            row.put("signal", o.type().name());
            row.put("confidence", o.confidence());
            row.put("selected", o == w);
            slate.add(row);
        }
        long buyCount = outcomes.stream().filter(o -> o.type() == Signal.SignalType.BUY).count();
        String reason = (rule == ArbitrationRule.EVIDENCE_RANKED ? "best track record" : "highest confidence")
            + " (confidence " + w.confidence().toPlainString() + ") among " + buyCount + " BUY(s)";

        SignalSelectionEntity entity = new SignalSelectionEntity(symbol, date, w.variantId(),
            w.strategyVersion(), signalId, w.confidence(), slate, reason);
        existing.ifPresent(e -> repository.delete(e));
        repository.flush();
        SignalSelectionEntity saved = repository.save(entity);
        logger.info("Signal tournament for {} on {}: {} wins ({})", symbol, date, w.variantId(), reason);
        return Optional.of(saved);
    }

    private Set<String> openPositionVariants(String symbol) {
        if (positionRepository == null || symbol == null) return Set.of();
        return positionRepository.findOpenBySymbolAndPortfolioIdIsNotNull(symbol).stream()
            .map(p -> p.getPortfolioId())
            .filter(id -> id != null && !id.isBlank())
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    /** Returns a champion only when the current configuration is unambiguous. */
    private Optional<String> currentChampionVariant() {
        if (strategyConfigRepository == null) return Optional.empty();
        List<String> champions = strategyConfigRepository.findAll().stream()
            .map(StrategyConfigEntity::toDomain)
            .filter(c -> c.current() && c.mode() == com.swingtrade.domain.StrategyConfig.Mode.CHAMPION)
            .map(com.swingtrade.domain.StrategyConfig::variantId)
            .distinct()
            .sorted()
            .toList();
        return champions.size() == 1 ? Optional.of(champions.getFirst()) : Optional.empty();
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

    public List<SignalSelectionEntity> findForSymbol(String symbol) {
        return repository.findBySymbolOrderBySelectionDateDesc(symbol);
    }
}
