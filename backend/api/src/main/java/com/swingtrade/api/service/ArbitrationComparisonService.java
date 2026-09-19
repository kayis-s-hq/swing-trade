package com.swingtrade.api.service;

import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.service.PortfolioQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Offline replay of recorded tournaments under each {@link ArbitrationRule}: for every stored
 * candidate slate, which variant would each rule have picked, and how did that variant's own
 * shadow trade on the symbol actually turn out. Track-record evidence is computed as of each
 * selection date, so replays never use trades that had not yet closed (no look-ahead).
 */
@Service
public class ArbitrationComparisonService {

    private final SignalArbiter arbiter;
    private final PortfolioQueryService paperPortfolioService;

    public ArbitrationComparisonService(SignalArbiter arbiter, PortfolioQueryService paperPortfolioService) {
        this.arbiter = arbiter;
        this.paperPortfolioService = paperPortfolioService;
    }

    public record RuleResult(String rule, int decisions, int decisionsWithOutcome, Double avgReturnPct,
                             Double winRatePct, int differsFromHighestConfidence) {}

    public record Comparison(LocalDate from, LocalDate to, int tournaments, List<RuleResult> rules) {}

    public Comparison compare(LocalDate from, LocalDate to) {
        List<SignalSelectionEntity> selections = arbiter.findBetween(from, to);
        Map<String, List<ShadowClosedTrade>> trades = new HashMap<>();
        for (SignalSelectionEntity sel : selections) {
            for (Map<String, Object> c : sel.getCandidates()) {
                String id = String.valueOf(c.get("variantId"));
                trades.computeIfAbsent(id, paperPortfolioService::findClosedTrades);
            }
        }
        return replay(from, to, selections, trades);
    }

    static Comparison replay(LocalDate from, LocalDate to, List<SignalSelectionEntity> selections,
                             Map<String, List<ShadowClosedTrade>> shadowTrades) {
        Map<ArbitrationRule, List<Double>> outcomes = new LinkedHashMap<>();
        Map<ArbitrationRule, Integer> decisions = new LinkedHashMap<>();
        Map<ArbitrationRule, Integer> differs = new LinkedHashMap<>();
        for (ArbitrationRule rule : ArbitrationRule.values()) {
            outcomes.put(rule, new ArrayList<>());
            decisions.put(rule, 0);
            differs.put(rule, 0);
        }

        for (SignalSelectionEntity sel : selections) {
            List<SignalArbiter.Candidate> buys = new ArrayList<>();
            Map<String, Double> evidence = new HashMap<>();
            for (Map<String, Object> c : sel.getCandidates()) {
                if (!"BUY".equals(c.get("signal")) || c.get("confidence") == null) continue;
                String id = String.valueOf(c.get("variantId"));
                buys.add(new SignalArbiter.Candidate(id, new BigDecimal(String.valueOf(c.get("confidence")))));
                SignalArbiter.evidence(shadowTrades.getOrDefault(id, List.of()), sel.getSelectionDate())
                    .ifPresent(v -> evidence.put(id, v));
            }
            if (buys.isEmpty()) continue;

            Optional<String> baseline = SignalArbiter.pickVariant(buys, ArbitrationRule.HIGHEST_CONFIDENCE, evidence);
            for (ArbitrationRule rule : ArbitrationRule.values()) {
                Optional<String> pick = SignalArbiter.pickVariant(buys, rule, evidence);
                if (pick.isEmpty()) continue;
                decisions.merge(rule, 1, Integer::sum);
                if (!pick.equals(baseline)) differs.merge(rule, 1, Integer::sum);
                StrategyAttributionService.returnPct(pick.get(), sel, shadowTrades)
                    .ifPresent(outcomes.get(rule)::add);
            }
        }

        List<RuleResult> results = new ArrayList<>();
        for (ArbitrationRule rule : ArbitrationRule.values()) {
            List<Double> r = outcomes.get(rule);
            Double avg = r.isEmpty() ? null : round(r.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            Double winRate = r.isEmpty() ? null : round(100.0 * r.stream().filter(v -> v > 0).count() / r.size());
            results.add(new RuleResult(rule.name(), decisions.get(rule), r.size(), avg, winRate, differs.get(rule)));
        }
        return new Comparison(from, to, selections.size(), results);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
