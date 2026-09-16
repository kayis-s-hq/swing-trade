package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RiskManagementPolicy;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.math.BigDecimal;
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
        return simulate(symbolResults, config, evaluationStart, evaluationEnd, Map.of());
    }

    PortfolioBacktestResult simulate(List<BacktestResult> symbolResults, BacktestConfig config,
                                     LocalDate evaluationStart, LocalDate evaluationEnd,
                                     Map<String, List<OhlcvCandle>> marketData) {
        if (symbolResults == null || config == null || evaluationStart == null || evaluationEnd == null
                || evaluationStart.isAfter(evaluationEnd)) {
            throw new IllegalArgumentException("Portfolio inputs must be non-null and the window must be ordered");
        }
        Map<String, List<OhlcvCandle>> effectiveMarketData = marketData == null ? Map.of() : marketData;
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
        Map<String, Double> highestClose = new HashMap<>();
        Map<LocalDate, Double> unsettledByDate = new HashMap<>();
        List<LocalDate> observationDates = observationDates(effectiveMarketData, evaluationStart, evaluationEnd);
        Map<LocalDate, LocalDate> nextTradingDate = effectiveMarketData.isEmpty()
                ? Map.of() : nextTradingDates(observationDates);
        double cash = config.initialCapital();

        int rejected = 0;
        for (LocalDate date : observationDates) {
            Double settled = unsettledByDate.remove(date);
            if (settled != null) {
                cash += settled;
            }

            // Apply policy stops before the candidate's original exit. The highest close is
            // deliberately from a completed prior bar, avoiding same-bar look-ahead.
            for (BacktestTrade held : List.copyOf(open.values())) {
                OhlcvCandle candle = candleOn(effectiveMarketData, held.symbol(), date);
                if (candle == null) continue;
                RiskManagementPolicy.RiskManagementDecision decision = config.riskManagementPolicy()
                        .evaluate(new RiskManagementPolicy.RiskManagementContext(
                                held.entryPrice(), held.stopLoss(), held.target(), candle.close(), candle.low(),
                                BigDecimal.valueOf(highestClose.getOrDefault(held.symbol(),
                                        held.entryPrice().doubleValue())),
                                (int) (date.toEpochDay() - held.entryDate().toEpochDay())));
                if (decision.exit()) {
                    open.remove(held.symbol());
                    BacktestTrade managed = managedExit(held, date, decision.stopPrice(), decision.reason());
                    unsettledByDate.merge(nextSettlementDate(date, nextTradingDate),
                            entryNotional(managed) + managed.pnl(), Double::sum);
                    replaceAccepted(accepted, held, managed);
                }
            }

            for (BacktestTrade trade : exitsByDate.getOrDefault(date, List.of())) {
                BacktestTrade held = open.remove(trade.symbol());
                if (held != null) {
                    double proceeds = entryNotional(held) + held.pnl();
                    unsettledByDate.merge(nextSettlementDate(date, nextTradingDate), proceeds, Double::sum);
                }
            }

            for (BacktestTrade held : open.values()) {
                OhlcvCandle candle = candleOn(effectiveMarketData, held.symbol(), date);
                if (candle != null) {
                    highestClose.merge(held.symbol(), candle.close().doubleValue(), Math::max);
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
            LocalDate valuationDate = date;
            double positionValue = open.values().stream()
                    .mapToDouble(trade -> marketValue(trade, valuationDate, effectiveMarketData))
                    .sum();
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

    private static OhlcvCandle candleOn(Map<String, List<OhlcvCandle>> marketData,
                                         String symbol, LocalDate date) {
        List<OhlcvCandle> candles = marketData.get(symbol);
        if (candles == null) return null;
        return candles.stream().filter(candle -> candle.date().equals(date)).findFirst().orElse(null);
    }

    private static BacktestTrade managedExit(BacktestTrade original, LocalDate exitDate,
                                             BigDecimal exitPrice, String reason) {
        double pnl = original.pnl()
                + exitPrice.subtract(original.exitPrice()).doubleValue() * original.quantity();
        ExitReason exitReason = "TRAILING_STOP".equals(reason)
                ? ExitReason.TRAILING_STOP : ExitReason.BREAKEVEN_STOP;
        return new BacktestTrade(original.symbol(), original.entryDate(), exitDate, original.entryPrice(),
                exitPrice, original.stopLoss(), original.target(), original.quantity(), exitReason, pnl,
                original.entryPrice().signum() == 0 ? 0.0
                        : pnl / entryNotional(original) * 100.0,
                (int) (exitDate.toEpochDay() - original.entryDate().toEpochDay()));
    }

    private static void replaceAccepted(List<BacktestTrade> accepted, BacktestTrade original,
                                        BacktestTrade replacement) {
        int index = accepted.indexOf(original);
        if (index >= 0) accepted.set(index, replacement);
    }

    private static double marketValue(BacktestTrade trade, LocalDate date,
                                      Map<String, List<OhlcvCandle>> marketData) {
        List<OhlcvCandle> candles = marketData.get(trade.symbol());
        if (candles == null || candles.isEmpty()) {
            return entryNotional(trade);
        }
        return candles.stream().filter(candle -> candle.date().equals(date)).findFirst()
                .map(candle -> candle.close().doubleValue() * trade.quantity())
                .orElseGet(() -> entryNotional(trade));
    }

    private static LocalDate nextSettlementDate(LocalDate exitDate, Map<LocalDate, LocalDate> nextTradingDate) {
        if (!nextTradingDate.isEmpty()) {
            return nextTradingDate.getOrDefault(exitDate, exitDate.plusDays(1));
        }
        LocalDate settlement = exitDate.plusDays(1);
        while (settlement.getDayOfWeek() == DayOfWeek.SATURDAY
                || settlement.getDayOfWeek() == DayOfWeek.SUNDAY) {
            settlement = settlement.plusDays(1);
        }
        return settlement;
    }

    private static List<LocalDate> observationDates(Map<String, List<OhlcvCandle>> marketData,
                                                    LocalDate start, LocalDate end) {
        if (marketData.isEmpty()) {
            List<LocalDate> dates = new ArrayList<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                dates.add(date);
            }
            return dates;
        }
        return marketData.values().stream().flatMap(List::stream)
                .map(OhlcvCandle::date).filter(date -> !date.isBefore(start) && !date.isAfter(end))
                .distinct().sorted().toList();
    }

    private static Map<LocalDate, LocalDate> nextTradingDates(List<LocalDate> dates) {
        Map<LocalDate, LocalDate> next = new HashMap<>();
        for (int i = 0; i + 1 < dates.size(); i++) {
            next.put(dates.get(i), dates.get(i + 1));
        }
        return next;
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
