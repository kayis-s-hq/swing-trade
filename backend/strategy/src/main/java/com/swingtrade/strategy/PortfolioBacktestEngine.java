package com.swingtrade.strategy;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies shared-capital and position-capacity constraints to independently generated trade
 * candidates. Candidates are consumed in date order; exits are processed before same-day entries.
 */
final class PortfolioBacktestEngine {

    PortfolioBacktestResult simulate(List<BacktestResult> symbolResults, BacktestConfig config,
                                     LocalDate evaluationStart, LocalDate evaluationEnd) {
        if (symbolResults == null || config == null || evaluationStart == null || evaluationEnd == null
                || evaluationStart.isAfter(evaluationEnd)) {
            throw new IllegalArgumentException("Portfolio inputs must be non-null and the window must be ordered");
        }
        if (config.initialCapital() <= 0 || config.maxConcurrentPositions() <= 0) {
            throw new IllegalArgumentException("Portfolio capital and max positions must be positive");
        }

        List<BacktestTrade> candidates = symbolResults.stream()
                .filter(result -> result != null)
                .flatMap(result -> result.trades().stream())
                .filter(trade -> !trade.entryDate().isBefore(evaluationStart)
                        && !trade.exitDate().isAfter(evaluationEnd))
                .sorted(Comparator.comparing(BacktestTrade::entryDate)
                        .thenComparing(BacktestTrade::symbol)
                        .thenComparing(BacktestTrade::exitDate))
                .toList();

        Map<LocalDate, List<BacktestTrade>> entriesByDate = new HashMap<>();
        Map<LocalDate, List<BacktestTrade>> exitsByDate = new HashMap<>();
        for (BacktestTrade candidate : candidates) {
            entriesByDate.computeIfAbsent(candidate.entryDate(), ignored -> new ArrayList<>()).add(candidate);
            exitsByDate.computeIfAbsent(candidate.exitDate(), ignored -> new ArrayList<>()).add(candidate);
        }

        List<BacktestTrade> accepted = new ArrayList<>();
        List<PortfolioEquityPoint> equityCurve = new ArrayList<>();
        Map<String, BacktestTrade> open = new HashMap<>();
        Map<LocalDate, Double> unsettledByDate = new HashMap<>();
        double cash = config.initialCapital();

        int rejected = 0;
        for (LocalDate date = evaluationStart; !date.isAfter(evaluationEnd); date = date.plusDays(1)) {
            Double settled = unsettledByDate.remove(date);
            if (settled != null) {
                cash += settled;
            }
            for (BacktestTrade trade : exitsByDate.getOrDefault(date, List.of())) {
                BacktestTrade held = open.remove(trade.symbol());
                if (held != null) {
                    double proceeds = entryNotional(held) + held.pnl();
                    unsettledByDate.merge(nextSettlementDate(date), proceeds, Double::sum);
                }
            }

            for (BacktestTrade candidate : entriesByDate.getOrDefault(date, List.of())) {
                double notional = entryNotional(candidate);
                if (open.containsKey(candidate.symbol())
                        || open.size() >= config.maxConcurrentPositions()
                        || notional <= 0 || notional > cash) {
                    rejected++;
                    continue;
                }
                open.put(candidate.symbol(), candidate);
                accepted.add(candidate);
                cash -= notional;
            }

            double unsettled = unsettledByDate.values().stream().mapToDouble(Double::doubleValue).sum();
            double positionValue = open.values().stream().mapToDouble(PortfolioBacktestEngine::entryNotional).sum();
            equityCurve.add(new PortfolioEquityPoint(date, cash + unsettled + positionValue,
                    cash, unsettled, positionValue));
        }

        double finalCapital = equityCurve.getLast().equity();
        int winners = (int) accepted.stream().filter(trade -> trade.pnl() > 0).count();
        double maxDrawdown = maxDrawdownPct(equityCurve);
        double cagr = BacktestMetrics.cagrPct(config.initialCapital(), finalCapital,
                evaluationStart, evaluationEnd);
        return new PortfolioBacktestResult(evaluationStart, evaluationEnd, config.initialCapital(), finalCapital,
                (finalCapital - config.initialCapital()) / config.initialCapital() * 100.0,
                maxDrawdown, BacktestMetrics.sharpeRatio(equityCurve.stream()
                        .map(PortfolioEquityPoint::equity).toList()), cagr, BacktestMetrics.sortinoRatio(equityCurve.stream()
                        .map(PortfolioEquityPoint::equity).toList()),
                BacktestMetrics.calmarRatio(cagr, maxDrawdown), accepted.size(), winners, rejected,
                accepted, equityCurve);
    }

    private static double entryNotional(BacktestTrade trade) {
        return trade.entryPrice().doubleValue() * trade.quantity();
    }

    private static LocalDate nextSettlementDate(LocalDate exitDate) {
        LocalDate settlement = exitDate.plusDays(1);
        while (settlement.getDayOfWeek() == DayOfWeek.SATURDAY
                || settlement.getDayOfWeek() == DayOfWeek.SUNDAY) {
            settlement = settlement.plusDays(1);
        }
        return settlement;
    }

    private static double maxDrawdownPct(List<PortfolioEquityPoint> curve) {
        double peak = curve.getFirst().equity();
        double maxDrawdown = 0.0;
        for (PortfolioEquityPoint point : curve) {
            peak = Math.max(peak, point.equity());
            if (peak > 0) {
                maxDrawdown = Math.max(maxDrawdown, (peak - point.equity()) / peak * 100.0);
            }
        }
        return maxDrawdown;
    }
}
