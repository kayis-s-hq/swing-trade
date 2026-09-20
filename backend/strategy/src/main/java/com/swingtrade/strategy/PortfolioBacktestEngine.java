package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.BenchmarkCandleSeries;
import com.swingtrade.domain.BenchmarkComparison;
import com.swingtrade.domain.RiskManagementPolicy;
import com.swingtrade.domain.PortfolioExposureContext;
import com.swingtrade.domain.PortfolioExposureDecision;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies shared-capital and position-capacity constraints to independently generated trade
 * candidates. Candidates are consumed in date order; exits are processed before same-day entries.
 */
final class PortfolioBacktestEngine {

    PortfolioBacktestResult simulate(List<BacktestResult> symbolResults, BacktestConfig config,
                                     LocalDate evaluationStart, LocalDate evaluationEnd) {
        return simulate(symbolResults, config, evaluationStart, evaluationEnd, Map.of(), Map.of(), Optional.empty());
    }

    PortfolioBacktestResult simulate(List<BacktestResult> symbolResults, BacktestConfig config,
                                     LocalDate evaluationStart, LocalDate evaluationEnd,
                                     Map<String, List<OhlcvCandle>> marketData) {
        return simulate(symbolResults, config, evaluationStart, evaluationEnd, marketData, Map.of(), Optional.empty());
    }

    PortfolioBacktestResult simulate(List<BacktestResult> symbolResults, BacktestConfig config,
                                     LocalDate evaluationStart, LocalDate evaluationEnd,
                                     Map<String, List<OhlcvCandle>> marketData,
                                     Map<String, String> sectors) {
        return simulate(symbolResults, config, evaluationStart, evaluationEnd, marketData, sectors, Optional.empty());
    }

    PortfolioBacktestResult simulate(List<BacktestResult> symbolResults, BacktestConfig config,
                                     LocalDate evaluationStart, LocalDate evaluationEnd,
                                     Map<String, List<OhlcvCandle>> marketData,
                                     Map<String, String> sectors,
                                     Optional<BenchmarkCandleSeries> benchmark) {
        return simulate(symbolResults, config, evaluationStart, evaluationEnd, marketData, sectors, benchmark, null);
    }

    PortfolioBacktestResult simulate(List<BacktestResult> symbolResults, BacktestConfig config,
                                     LocalDate evaluationStart, LocalDate evaluationEnd,
                                     Map<String, List<OhlcvCandle>> marketData,
                                     Map<String, String> sectors,
                                     Optional<BenchmarkCandleSeries> benchmark,
                                     String strategyVariantId) {
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
        Map<String, BigDecimal> highestClose = new HashMap<>();
        Map<LocalDate, BigDecimal> unsettledByDate = new HashMap<>();
        List<LocalDate> observationDates = observationDates(effectiveMarketData, evaluationStart, evaluationEnd);
        Map<LocalDate, LocalDate> nextTradingDate = effectiveMarketData.isEmpty()
                ? Map.of() : nextTradingDates(observationDates);
        // initialCapital is a configuration double; convert once to an exact decimal money amount.
        BigDecimal initialCapital = FinancialScale.money(FinancialScale.of(config.initialCapital()));
        BigDecimal cash = initialCapital;

        int rejected = 0;
        List<String> rejectionReasons = new ArrayList<>();
        for (LocalDate date : observationDates) {
            BigDecimal settled = unsettledByDate.remove(date);
            if (settled != null) {
                cash = cash.add(settled);
            }

            // Apply policy stops before the candidate's original exit. The highest close is
            // deliberately from a completed prior bar, avoiding same-bar look-ahead.
            for (BacktestTrade held : List.copyOf(open.values())) {
                OhlcvCandle candle = candleOn(effectiveMarketData, held.symbol(), date);
                if (candle == null) continue;
                RiskManagementPolicy.RiskManagementDecision decision = config.riskManagementPolicy()
                        .evaluate(new RiskManagementPolicy.RiskManagementContext(
                                held.entryPrice(), held.stopLoss(), held.target(), candle.close(), candle.low(),
                                highestClose.getOrDefault(held.symbol(), held.entryPrice()),
                                (int) (date.toEpochDay() - held.entryDate().toEpochDay())));
                if (decision.exit()) {
                    open.remove(held.symbol());
                    BacktestTrade managed = managedExit(held, date, decision.stopPrice(), decision.reason());
                    unsettledByDate.merge(nextSettlementDate(date, nextTradingDate),
                            entryNotional(managed).add(managed.pnl()), BigDecimal::add);
                    replaceAccepted(accepted, held, managed);
                }
            }

            for (BacktestTrade trade : exitsByDate.getOrDefault(date, List.of())) {
                BacktestTrade held = open.remove(trade.symbol());
                if (held != null) {
                    BigDecimal proceeds = entryNotional(held).add(held.pnl());
                    unsettledByDate.merge(nextSettlementDate(date, nextTradingDate), proceeds, BigDecimal::add);
                }
            }

            for (BacktestTrade held : open.values()) {
                OhlcvCandle candle = candleOn(effectiveMarketData, held.symbol(), date);
                if (candle != null) {
                    highestClose.merge(held.symbol(), candle.close(), BigDecimal::max);
                }
            }

            for (BacktestTrade candidate : entriesByDate.getOrDefault(date, List.of())) {
                BigDecimal notional = entryNotional(candidate);
                String baseRejection = open.containsKey(candidate.symbol()) ? "OPEN_POSITION"
                        : open.size() >= config.maxConcurrentPositions() ? "MAX_CONCURRENT_POSITIONS"
                        : notional.signum() <= 0 ? "INVALID_NOTIONAL"
                        : notional.compareTo(cash) > 0 ? "INSUFFICIENT_CAPITAL" : null;
                PortfolioExposureDecision exposure = baseRejection == null
                        ? config.portfolioExposurePolicy().evaluate(new PortfolioExposureContext(
                                candidate.symbol(), sectors.get(candidate.symbol()), notional.doubleValue(),
                                config.initialCapital(), open.values().stream()
                                .map(held -> new PortfolioExposureContext.Holding(held.symbol(),
                                        sectors.get(held.symbol()), entryNotional(held).doubleValue())).toList(),
                                closingPrices(effectiveMarketData)))
                        : PortfolioExposureDecision.accept();
                if (baseRejection != null || !exposure.accepted()) {
                    rejected++;
                    rejectionReasons.add(baseRejection != null ? baseRejection : exposure.reason());
                    continue;
                }
                open.put(candidate.symbol(), candidate);
                accepted.add(candidate);
                cash = cash.subtract(notional);
            }

            BigDecimal unsettled = unsettledByDate.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDate valuationDate = date;
            BigDecimal positionValue = open.values().stream()
                    .map(trade -> marketValue(trade, valuationDate, effectiveMarketData))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            equityCurve.add(new PortfolioEquityPoint(date,
                    cash.add(unsettled).add(positionValue), cash, unsettled, positionValue));
        }

        BigDecimal finalCapital = equityCurve.getLast().equity();
        List<BigDecimal> equityValues = equityCurve.stream().map(PortfolioEquityPoint::equity).toList();
        int winners = (int) accepted.stream().filter(trade -> trade.pnl().signum() > 0).count();
        double maxDrawdown = BacktestMetrics.maxDrawdownPct(equityValues);
        double cagr = BacktestMetrics.cagrPct(initialCapital, finalCapital,
                evaluationStart, evaluationEnd);
        double totalReturn = BacktestMetrics.totalReturnPct(initialCapital, finalCapital);
        BenchmarkComparison benchmarkComparison = benchmark.flatMap(PortfolioBacktestEngine::benchmarkComparison)
                .map(value -> BenchmarkComparison.nifty50Price(totalReturn, value.benchmarkReturnPct()))
                .orElseGet(() -> BenchmarkComparison.unavailable(totalReturn));
        return new PortfolioBacktestResult(evaluationStart, evaluationEnd, initialCapital, finalCapital,
                totalReturn,
                maxDrawdown, BacktestMetrics.sharpeRatio(equityValues), cagr,
                BacktestMetrics.sortinoRatio(equityValues),
                BacktestMetrics.calmarRatio(cagr, maxDrawdown), accepted.size(), winners, rejected,
                accepted, equityCurve, rejectionReasons, benchmarkComparison, strategyVariantId);
    }

    private static Optional<BenchmarkComparison> benchmarkComparison(BenchmarkCandleSeries series) {
        List<OhlcvCandle> candles = series.candles();
        if (candles.size() < 2) return Optional.empty();
        OhlcvCandle first = candles.getFirst();
        OhlcvCandle last = candles.getLast();
        if (first.close() == null || last.close() == null || first.close().signum() <= 0
                || last.close().signum() <= 0) return Optional.empty();
        double returnPct = last.close().subtract(first.close())
                .divide(first.close(), java.math.MathContext.DECIMAL64)
                .doubleValue() * 100.0;
        return Optional.of(BenchmarkComparison.buyAndHold(0.0, returnPct));
    }

    private static BigDecimal entryNotional(BacktestTrade trade) {
        return FinancialScale.money(trade.entryPrice().multiply(BigDecimal.valueOf(trade.quantity())));
    }

    /**
     * Closing prices for {@link PortfolioExposureContext}. The core exposure policy (correlation
     * screening) is a statistical {@code double} API, so this is a documented conversion boundary.
     */
    private static Map<String, List<Double>> closingPrices(Map<String, List<OhlcvCandle>> marketData) {
        Map<String, List<Double>> prices = new HashMap<>();
        marketData.forEach((symbol, candles) -> prices.put(symbol, candles.stream()
                .sorted(Comparator.comparing(OhlcvCandle::date))
                .map(candle -> candle.close().doubleValue()).toList()));
        return prices;
    }

    private static OhlcvCandle candleOn(Map<String, List<OhlcvCandle>> marketData,
                                         String symbol, LocalDate date) {
        List<OhlcvCandle> candles = marketData.get(symbol);
        if (candles == null) return null;
        return candles.stream().filter(candle -> candle.date().equals(date)).findFirst().orElse(null);
    }

    private static BacktestTrade managedExit(BacktestTrade original, LocalDate exitDate,
                                             BigDecimal exitPrice, String reason) {
        BigDecimal pnl = original.pnl().add(
                exitPrice.subtract(original.exitPrice()).multiply(BigDecimal.valueOf(original.quantity())));
        ExitReason exitReason = "TRAILING_STOP".equals(reason)
                ? ExitReason.TRAILING_STOP : ExitReason.BREAKEVEN_STOP;
        return new BacktestTrade(original.symbol(), original.entryDate(), exitDate, original.entryPrice(),
                exitPrice, original.stopLoss(), original.target(), original.quantity(), exitReason, pnl,
                original.entryPrice().signum() == 0 ? BigDecimal.ZERO
                        : FinancialScale.percentOf(pnl, entryNotional(original)),
                (int) (exitDate.toEpochDay() - original.entryDate().toEpochDay()));
    }

    private static void replaceAccepted(List<BacktestTrade> accepted, BacktestTrade original,
                                        BacktestTrade replacement) {
        int index = accepted.indexOf(original);
        if (index >= 0) accepted.set(index, replacement);
    }

    private static BigDecimal marketValue(BacktestTrade trade, LocalDate date,
                                      Map<String, List<OhlcvCandle>> marketData) {
        List<OhlcvCandle> candles = marketData.get(trade.symbol());
        if (candles == null || candles.isEmpty()) {
            return entryNotional(trade);
        }
        return candles.stream().filter(candle -> candle.date().equals(date)).findFirst()
                .map(candle -> FinancialScale.money(candle.close().multiply(BigDecimal.valueOf(trade.quantity()))))
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
}
