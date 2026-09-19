package com.swingtrade.api.service;

import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.service.PaperPortfolioService;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.StrategyConfigStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Attribution report: which strategy variant is winning the per-symbol signal tournament and how
 * its picks perform. All math lives in the pure {@link #compute} method so it is unit-testable
 * without any persistence.
 *
 * <p>Simplifications (documented, not hidden):
 * <ul>
 *   <li>Selected-trade attribution: a closed trade in the {@code selected} portfolio is credited to
 *       the latest EXECUTED selection for that symbol dated on/before the trade's entry.</li>
 *   <li>Regret: for each EXECUTED/PENDING selection, the pick's return is the winner's own closed
 *       shadow trade on that symbol entered within {@value #TRADE_MATCH_DAYS} days of the selection;
 *       each rejected BUY candidate's return comes from its own matching shadow trade. Regret is
 *       {@code max(0, bestRejectedReturn - pickReturn)}, averaged, and only counted when both sides
 *       have a closed trade - open positions are ignored.</li>
 *   <li>Agreement rate: over the days a variant emitted a BUY for a symbol, the share of the other
 *       active variants that also emitted a BUY that day (null with fewer than two active variants).</li>
 * </ul>
 */
@Service
public class StrategyAttributionService {

    static final int TRADE_MATCH_DAYS = 5;
    static final String SELECTED_PORTFOLIO_ID = "selected";
    static final int MAX_RANGE_DAYS = 92;

    private final SignalArbiter arbiter;
    private final SignalStore signalStore;
    private final PaperPortfolioService paperPortfolioService;
    private final StrategyConfigStore strategyConfigStore;

    public StrategyAttributionService(SignalArbiter arbiter, SignalStore signalStore,
                                      PaperPortfolioService paperPortfolioService,
                                      StrategyConfigStore strategyConfigStore) {
        this.arbiter = arbiter;
        this.signalStore = signalStore;
        this.paperPortfolioService = paperPortfolioService;
        this.strategyConfigStore = strategyConfigStore;
    }

    public record VariantInfo(String variantId, String strategyType, String mode) {}

    public record SignalRow(String variantId, String symbol, LocalDate date, Signal.SignalType type) {}

    public record Inputs(LocalDate from, LocalDate to, List<VariantInfo> variants,
                         List<SignalSelectionEntity> selections, List<SignalRow> signals,
                         Map<String, List<ShadowClosedTrade>> shadowTrades,
                         List<ShadowClosedTrade> selectedTrades) {}

    public record VariantReport(String variantId, String strategyType, String mode,
                                int signalsGenerated, int buySignals,
                                int timesSelected, Double selectionRatePct,
                                int selectedTrades, int selectedWins, Double selectedWinRatePct,
                                BigDecimal selectedPnl,
                                int shadowTrades, int shadowWins, Double shadowWinRatePct, BigDecimal shadowPnl,
                                Double agreementRatePct, Double avgRegretPct,
                                int sentimentVetoes, int llmVetoes, int otherBlocks) {}

    public record Totals(int tournaments, int executed, int blocked, int pending,
                         int selectedTradesClosed, BigDecimal selectedPnl, Double avgRegretPct) {}

    public record Report(LocalDate from, LocalDate to, List<VariantReport> variants, Totals totals) {}

    public Report report(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        List<StrategyConfig> active = strategyConfigStore.findAllCurrent().stream()
            .filter(StrategyConfig::isActive).toList();
        List<VariantInfo> variants = active.stream()
            .map(c -> new VariantInfo(c.variantId(), c.strategyType(), c.mode().name())).toList();

        List<SignalRow> signals = new ArrayList<>();
        for (StrategyConfig c : active) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                for (Signal s : signalStore.findByDateAndStrategy(d, c.variantId())) {
                    signals.add(new SignalRow(c.variantId(), s.symbol(), s.date(), s.type()));
                }
            }
        }
        Map<String, List<ShadowClosedTrade>> shadow = new HashMap<>();
        for (StrategyConfig c : active) {
            shadow.put(c.variantId(), paperPortfolioService.findClosedTrades(c.variantId()));
        }
        return compute(new Inputs(from, to, variants, arbiter.findBetween(from, to), signals, shadow,
            paperPortfolioService.findClosedTrades(SELECTED_PORTFOLIO_ID)));
    }

    public static Report compute(Inputs in) {
        Set<String> variantIds = new HashSet<>();
        in.variants().forEach(v -> variantIds.add(v.variantId()));
        int activeCount = in.variants().size();

        Map<String, Set<String>> buyersByDay = new HashMap<>();
        for (SignalRow s : in.signals()) {
            if (s.type() == Signal.SignalType.BUY) {
                buyersByDay.computeIfAbsent(s.symbol() + "|" + s.date(), k -> new HashSet<>()).add(s.variantId());
            }
        }

        int executed = 0;
        int blocked = 0;
        int pending = 0;
        for (SignalSelectionEntity sel : in.selections()) {
            switch (sel.getStatus()) {
                case SignalSelectionEntity.EXECUTED -> executed++;
                case SignalSelectionEntity.BLOCKED -> blocked++;
                default -> pending++;
            }
        }
        int tournaments = in.selections().size();

        Map<String, List<Double>> regretByWinner = new HashMap<>();
        List<Double> allRegret = new ArrayList<>();
        for (SignalSelectionEntity sel : in.selections()) {
            Optional<Double> regret = regretFor(sel, in.shadowTrades());
            regret.ifPresent(r -> {
                regretByWinner.computeIfAbsent(sel.getWinnerVariantId(), k -> new ArrayList<>()).add(r);
                allRegret.add(r);
            });
        }

        Map<String, List<ShadowClosedTrade>> selectedByVariant = new HashMap<>();
        for (ShadowClosedTrade t : in.selectedTrades()) {
            if (t.exitDate() == null || t.exitDate().isBefore(in.from()) || t.exitDate().isAfter(in.to())) {
                continue;
            }
            attribute(t, in.selections()).ifPresent(v ->
                selectedByVariant.computeIfAbsent(v, k -> new ArrayList<>()).add(t));
        }

        List<VariantReport> reports = new ArrayList<>();
        for (VariantInfo v : in.variants()) {
            String id = v.variantId();
            int generated = 0;
            int buys = 0;
            for (SignalRow s : in.signals()) {
                if (s.variantId().equals(id)) {
                    generated++;
                    if (s.type() == Signal.SignalType.BUY) buys++;
                }
            }
            int selected = 0;
            int sentiment = 0;
            int llm = 0;
            int other = 0;
            for (SignalSelectionEntity sel : in.selections()) {
                if (!sel.getWinnerVariantId().equals(id)) continue;
                selected++;
                if (SignalSelectionEntity.BLOCKED.equals(sel.getStatus())) {
                    String detail = sel.getStatusDetail() == null ? "" : sel.getStatusDetail();
                    if (detail.startsWith("SENTIMENT_BLOCK")) sentiment++;
                    else if (detail.startsWith("LLM_BLOCK")) llm++;
                    else other++;
                }
            }
            List<ShadowClosedTrade> sel = selectedByVariant.getOrDefault(id, List.of());
            List<ShadowClosedTrade> own = in.shadowTrades().getOrDefault(id, List.of()).stream()
                .filter(t -> t.exitDate() != null && !t.exitDate().isBefore(in.from()) && !t.exitDate().isAfter(in.to()))
                .toList();

            reports.add(new VariantReport(id, v.strategyType(), v.mode(), generated, buys,
                selected, pct(selected, tournaments),
                sel.size(), wins(sel), pct(wins(sel), sel.size()), pnl(sel),
                own.size(), wins(own), pct(wins(own), own.size()), pnl(own),
                agreement(id, buyersByDay, activeCount), average(regretByWinner.get(id)),
                sentiment, llm, other));
        }
        reports.sort(Comparator.comparing((VariantReport r) -> r.timesSelected()).reversed()
            .thenComparing(VariantReport::variantId));

        List<ShadowClosedTrade> selectedAll = selectedByVariant.values().stream().flatMap(List::stream).toList();
        return new Report(in.from(), in.to(), reports,
            new Totals(tournaments, executed, blocked, pending, selectedAll.size(), pnl(selectedAll),
                average(allRegret)));
    }

    private static Optional<String> attribute(ShadowClosedTrade trade, List<SignalSelectionEntity> selections) {
        return selections.stream()
            .filter(s -> SignalSelectionEntity.EXECUTED.equals(s.getStatus()))
            .filter(s -> s.getSymbol().equals(trade.symbol()))
            .filter(s -> trade.entryDate() != null && !s.getSelectionDate().isAfter(trade.entryDate()))
            .max(Comparator.comparing(SignalSelectionEntity::getSelectionDate))
            .map(SignalSelectionEntity::getWinnerVariantId);
    }

    private static Optional<Double> regretFor(SignalSelectionEntity sel,
                                              Map<String, List<ShadowClosedTrade>> shadowTrades) {
        Optional<Double> pick = returnPct(sel.getWinnerVariantId(), sel, shadowTrades);
        if (pick.isEmpty()) return Optional.empty();
        Double best = null;
        for (Map<String, Object> c : sel.getCandidates()) {
            if (Boolean.TRUE.equals(c.get("selected")) || !"BUY".equals(c.get("signal"))) continue;
            Optional<Double> alt = returnPct(String.valueOf(c.get("variantId")), sel, shadowTrades);
            if (alt.isPresent() && (best == null || alt.get() > best)) best = alt.get();
        }
        if (best == null) return Optional.empty();
        return Optional.of(Math.max(0.0, best - pick.get()));
    }

    static Optional<Double> returnPct(String variantId, SignalSelectionEntity sel,
                                              Map<String, List<ShadowClosedTrade>> shadowTrades) {
        return shadowTrades.getOrDefault(variantId, List.of()).stream()
            .filter(t -> t.symbol().equals(sel.getSymbol()) && t.entryDate() != null && t.pnl() != null
                && t.entryPrice() != null && t.entryPrice().signum() > 0 && t.quantity() > 0)
            .filter(t -> {
                long d = ChronoUnit.DAYS.between(sel.getSelectionDate(), t.entryDate());
                return d >= 0 && d <= TRADE_MATCH_DAYS;
            })
            .min(Comparator.comparing(ShadowClosedTrade::entryDate))
            .map(t -> t.pnl().doubleValue() / (t.entryPrice().doubleValue() * t.quantity()) * 100.0);
    }

    private static Double agreement(String variantId, Map<String, Set<String>> buyersByDay, int activeCount) {
        if (activeCount < 2) return null;
        double sum = 0;
        int days = 0;
        for (Set<String> buyers : buyersByDay.values()) {
            if (buyers.contains(variantId)) {
                sum += (buyers.size() - 1) / (double) (activeCount - 1);
                days++;
            }
        }
        return days == 0 ? null : round(sum / days * 100.0);
    }

    private static int wins(List<ShadowClosedTrade> trades) {
        return (int) trades.stream().filter(ShadowClosedTrade::isWin).count();
    }

    private static BigDecimal pnl(List<ShadowClosedTrade> trades) {
        return trades.stream().map(t -> t.pnl() == null ? BigDecimal.ZERO : t.pnl())
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double pct(int part, int whole) {
        return whole == 0 ? null : round(part * 100.0 / whole);
    }

    private static Double average(List<Double> values) {
        if (values == null || values.isEmpty()) return null;
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
