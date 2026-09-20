package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EXPLORATORY ANALYSIS — NOT a real assertion-based unit test, and deliberately gated off so it
 * can NEVER run in CI or a normal {@code ./gradlew test} / {@code ./gradlew check} invocation.
 * It self-skips via {@link Assumptions#assumeTrue} unless the {@code RUN_VOLUME_CALIBRATION}
 * environment variable is explicitly set, e.g.:
 *
 * <pre>
 * cd backend
 * RUN_VOLUME_CALIBRATION=1 ./gradlew :strategy:test \
 *     --tests "com.swingtrade.strategy.VolumeThresholdCalibrationExploration" -q --rerun
 * </pre>
 *
 * <p>This is a one-off, read-only calibration analysis of {@code StrategyParams.VOLUME_MULTIPLIER}
 * (live default 1.5x — see {@code docs/backtesting.md} entry rule 3 and
 * {@code PriceActionSignalEngine.VOLUME_MULTIPLIER}). It duplicates {@link BacktestEngine}'s
 * private {@code simulate}/{@code tryEnter}/{@code closeTrade}/{@code buildResult} logic verbatim,
 * with the volume-surge multiplier pulled out as a parameter instead of the hardcoded constant, so
 * several threshold values can be swept against the SAME real candle data — WITHOUT touching the
 * live {@code StrategyParams.VOLUME_MULTIPLIER} default or any production code. There is currently
 * no supported way to override this specific threshold via {@code BacktestConfig} /
 * {@code BacktestController} (only slippage/brokerage/risk/ATR/reward-risk/holding-days are
 * tunable there), which is why this duplication was necessary for the analysis.
 *
 * <p>Reads candles directly (read-only {@code SELECT}) from the shared dev Postgres instance
 * (same DB the live app/backtests use, via the {@code SPRING_DATASOURCE_URL} /
 * {@code SPRING_DATASOURCE_USERNAME} / {@code SPRING_DATASOURCE_PASSWORD} environment variables —
 * see {@code infra/env/.env} for the dev values) for the fixed 14-symbol list that appeared in
 * {@code backend/api/reports/backtest_20260823_020004.json}, using the exact same
 * {@code BacktestConfig.defaults()} the API's {@code /api/backtest/run-all} uses, so results are
 * apples-to-apples comparable with that saved report at the 1.5x baseline. Requires network access
 * to the pi-node dev DB and the three env vars above to be set; will fail fast (self-skipping via
 * {@link Assumptions#assumeTrue}) if either is missing, or if the DB is unreachable.
 */
class VolumeThresholdCalibrationExploration {

    private static final String JDBC_URL = System.getenv("SPRING_DATASOURCE_URL");
    private static final String JDBC_USER = System.getenv("SPRING_DATASOURCE_USERNAME");
    private static final String JDBC_PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");

    // Same 14 symbols as backend/api/reports/backtest_20260823_020004.json.
    private static final List<String> SYMBOLS = List.of(
            "AXISBANK", "BHARTIARTL", "HDFCBANK", "ICICIBANK", "INFY", "ITC", "KOTAKBANK",
            "LT", "MARUTI", "RELIANCE", "SBIN", "SUNPHARMA", "TCS", "WIPRO");

    // Baseline (live default) 1.5x, plus looser candidates, plus 0.0x as a true "no volume
    // filter" control (at 1.0x, volume must still be *above* its own 20-day average, so it is
    // not literally unfiltered).
    private static final List<BigDecimal> THRESHOLDS = List.of(
            BigDecimal.valueOf(1.5), BigDecimal.valueOf(1.3), BigDecimal.valueOf(1.2),
            BigDecimal.valueOf(1.0), BigDecimal.ZERO);

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    @Test
    void compareVolumeThresholds() throws Exception {
        Assumptions.assumeTrue(System.getenv("RUN_VOLUME_CALIBRATION") != null,
                "Skipped by default — set RUN_VOLUME_CALIBRATION=1 to run this exploratory, "
                        + "live-DB-dependent analysis manually. See class Javadoc.");
        Assumptions.assumeTrue(isNotBlank(JDBC_URL), "Skipped — SPRING_DATASOURCE_URL is not set.");
        Assumptions.assumeTrue(isNotBlank(JDBC_USER), "Skipped — SPRING_DATASOURCE_USERNAME is not set.");
        Assumptions.assumeTrue(isNotBlank(JDBC_PASSWORD), "Skipped — SPRING_DATASOURCE_PASSWORD is not set.");

        Map<String, List<OhlcvCandle>> candlesBySymbol = new LinkedHashMap<>();
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            for (String symbol : SYMBOLS) {
                candlesBySymbol.put(symbol, loadCandles(conn, symbol));
            }
        }

        BacktestConfig config = BacktestConfig.defaults();

        System.out.println();
        System.out.println("Per-symbol candle counts loaded:");
        candlesBySymbol.forEach((sym, candles) -> System.out.printf(Locale.ROOT, "  %-10s %d candles (%s -> %s)%n",
                sym, candles.size(),
                candles.isEmpty() ? "-" : candles.get(0).date(),
                candles.isEmpty() ? "-" : candles.get(candles.size() - 1).date()));

        System.out.println();
        System.out.printf(Locale.ROOT, "%-10s %10s %10s %10s %14s %14s %16s %14s%n",
                "threshold", "trades", "winRate%", "avgPnl%/tr", "totalPnl(Rs)", "portfolioRet%", "avgMaxDD%(sym)", "avgSharpe(sym)");

        for (BigDecimal threshold : THRESHOLDS) {
            List<BacktestResult> results = new ArrayList<>();
            for (Map.Entry<String, List<OhlcvCandle>> e : candlesBySymbol.entrySet()) {
                if (e.getValue().size() < 60) {
                    continue;
                }
                results.add(simulate(e.getKey(), e.getValue(), config, threshold));
            }
            printAggregate(threshold, results, config);
        }

        System.out.println();
        System.out.println("Per-symbol detail at each threshold:");
        for (BigDecimal threshold : THRESHOLDS) {
            System.out.println();
            System.out.println("--- threshold=" + threshold + "x ---");
            for (Map.Entry<String, List<OhlcvCandle>> e : candlesBySymbol.entrySet()) {
                if (e.getValue().size() < 60) {
                    continue;
                }
                BacktestResult r = simulate(e.getKey(), e.getValue(), config, threshold);
                System.out.printf(Locale.ROOT, "  %-10s trades=%3d winRate=%6.2f%% totalReturn=%7.2f%% maxDD=%6.2f%%%n",
                        r.symbol(), r.totalTrades(), r.winRate(), r.totalReturn(), r.maxDrawdownPct());
            }
        }
    }

    private static void printAggregate(BigDecimal threshold, List<BacktestResult> results, BacktestConfig config) {
        int totalTrades = results.stream().mapToInt(BacktestResult::totalTrades).sum();
        int totalWins = results.stream().mapToInt(BacktestResult::winningTrades).sum();
        double winRate = totalTrades > 0 ? (totalWins / (double) totalTrades) * 100.0 : 0.0;

        List<BacktestTrade> allTrades = results.stream()
                .flatMap(r -> r.trades().stream())
                .toList();
        double avgPnlPctPerTrade = allTrades.isEmpty() ? 0.0
                : allTrades.stream().mapToDouble(t -> t.pnlPct().doubleValue()).average().orElse(0.0);
        double totalPnl = allTrades.stream().mapToDouble(t -> t.pnl().doubleValue()).sum();

        double aggregateCapital = results.size() * config.initialCapital();
        double portfolioReturnPct = aggregateCapital > 0 ? (totalPnl / aggregateCapital) * 100.0 : 0.0;

        double avgMaxDrawdown = results.stream()
                .filter(r -> r.totalTrades() > 0)
                .mapToDouble(BacktestResult::maxDrawdownPct)
                .average().orElse(0.0);
        double avgSharpe = results.stream()
                .filter(r -> r.totalTrades() > 0)
                .mapToDouble(BacktestResult::sharpeRatio)
                .average().orElse(0.0);

        System.out.printf(Locale.ROOT, "%-10s %10d %9.2f%% %9.2f%% %14.2f %13.2f%% %15.2f%% %14.3f%n",
                threshold + "x", totalTrades, winRate, avgPnlPctPerTrade, totalPnl, portfolioReturnPct,
                avgMaxDrawdown, avgSharpe);
    }

    // -----------------------------------------------------------------------
    // Candle loading (read-only SELECT against the shared dev Postgres instance)
    // -----------------------------------------------------------------------

    private static List<OhlcvCandle> loadCandles(Connection conn, String symbol) throws Exception {
        // Mirrors CandleStore.findTopBySymbolOrderByDateDesc(symbol, 1000) used by
        // BacktestEngine.getDescendingCandles, then reversed to chronological order.
        String sql = "SELECT date, open_price, high_price, low_price, close_price, volume, adj_close_price "
                + "FROM ohlcv_candles WHERE symbol = ? ORDER BY date DESC LIMIT 1000";
        List<OhlcvCandle> descending = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("date").toLocalDate();
                    BigDecimal open = rs.getBigDecimal("open_price");
                    BigDecimal high = rs.getBigDecimal("high_price");
                    BigDecimal low = rs.getBigDecimal("low_price");
                    BigDecimal close = rs.getBigDecimal("close_price");
                    long volume = rs.getLong("volume");
                    BigDecimal adjClose = rs.getBigDecimal("adj_close_price");
                    descending.add(new OhlcvCandle(symbol, date, open, high, low, close, volume,
                            adjClose != null ? adjClose : close));
                }
            }
        }
        List<OhlcvCandle> chronological = new ArrayList<>(descending);
        Collections.reverse(chronological);
        return chronological;
    }

    private static BarSeries buildBarSeries(String symbol, List<OhlcvCandle> chronologicalCandles) {
        BarSeries series = new BaseBarSeries(symbol, DecimalNum.valueOf(0));
        for (OhlcvCandle candle : chronologicalCandles) {
            ZonedDateTime endTime = candle.date().atStartOfDay(MARKET_ZONE);
            Long volume = candle.volume();
            BaseBar bar = new BaseBar(
                    Duration.ofDays(1),
                    endTime,
                    candle.open(),
                    candle.high(),
                    candle.low(),
                    candle.close(),
                    BigDecimal.valueOf(volume != null ? volume : 0L));
            series.addBar(bar);
        }
        return series;
    }

    // -----------------------------------------------------------------------
    // Simulation — duplicated verbatim from BacktestEngine.simulate/tryEnter/closeTrade/
    // buildResult/computeSharpeRatio/computeMaxDrawdownPct, with the volume-surge multiplier
    // pulled out as a parameter instead of the hardcoded PriceActionSignalEngine.VOLUME_MULTIPLIER.
    // -----------------------------------------------------------------------

    private static BacktestResult simulate(String symbol, List<OhlcvCandle> chronologicalCandles,
                                            BacktestConfig config, BigDecimal volumeMultiplier) {
        BarSeries series = buildBarSeries(symbol, chronologicalCandles);
        int barCount = series.getBarCount();

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
        HighPriceIndicator highPrice = new HighPriceIndicator(series);
        LowPriceIndicator lowPrice = new LowPriceIndicator(series);
        EMAIndicator ema20 = new EMAIndicator(closePrice, PriceActionSignalEngine.EMA_FAST_PERIOD);
        EMAIndicator ema50 = new EMAIndicator(closePrice, PriceActionSignalEngine.EMA_SLOW_PERIOD);
        RSIIndicator rsi = new RSIIndicator(closePrice, PriceActionSignalEngine.RSI_PERIOD);
        ATRIndicator atr = new ATRIndicator(series, PriceActionSignalEngine.ATR_PERIOD);
        VolumeIndicator volume = new VolumeIndicator(series);
        SMAIndicator volumeMa = new SMAIndicator(volume, PriceActionSignalEngine.VOLUME_MA_PERIOD);
        int weeklyHighPeriod = Math.min(PriceActionSignalEngine.FIFTY_TWO_WEEK_TRADING_DAYS, barCount);
        HighestValueIndicator weeklyHigh = new HighestValueIndicator(highPrice, weeklyHighPeriod);

        double capital = config.initialCapital();
        List<BacktestTrade> trades = new ArrayList<>();
        List<Double> capitalCurve = new ArrayList<>();
        OpenPosition open = null;

        for (int i = PriceActionSignalEngine.MIN_REQUIRED_CANDLES; i < barCount; i++) {
            capitalCurve.add(capital);

            if (open != null) {
                BigDecimal low = numToBigDecimal(lowPrice.getValue(i));
                BigDecimal high = numToBigDecimal(highPrice.getValue(i));
                BigDecimal close = numToBigDecimal(closePrice.getValue(i));
                BigDecimal ema20Val = numToBigDecimal(ema20.getValue(i));

                int streak = close.compareTo(ema20Val) < 0 ? open.belowEma20Streak + 1 : 0;
                BigDecimal exitPrice = null;
                ExitReason reason = null;

                if (low.compareTo(open.stopLoss()) <= 0) {
                    reason = ExitReason.STOP_LOSS;
                    exitPrice = open.stopLoss();
                } else if (high.compareTo(open.target()) >= 0) {
                    reason = ExitReason.TARGET_HIT;
                    exitPrice = open.target();
                } else if (streak >= 2) {
                    reason = ExitReason.TREND_BREAK;
                    exitPrice = close;
                } else if ((i - open.entryIndex()) >= config.maxHoldingDays()) {
                    reason = ExitReason.TIME_STOP;
                    exitPrice = close;
                }

                if (reason != null) {
                    LocalDate exitDate = chronologicalCandles.get(i).date();
                    BacktestTrade trade = closeTrade(symbol, open, exitPrice, exitDate, i, reason, config);
                    trades.add(trade);
                    capital += trade.pnl().doubleValue();
                    open = null;
                } else {
                    open = new OpenPosition(open.entryIndex(), open.entryDate(), open.entryPrice(),
                            open.stopLoss(), open.target(), open.quantity(), streak);
                }
            }

            if (open == null && i + 1 < barCount) {
                open = tryEnter(chronologicalCandles, closePrice, openPrice, ema20, ema50, rsi, atr, volume, volumeMa,
                        weeklyHigh, i, capital, config, volumeMultiplier);
            }
        }

        if (open != null) {
            int lastIndex = barCount - 1;
            BigDecimal exitPrice = numToBigDecimal(closePrice.getValue(lastIndex));
            LocalDate exitDate = chronologicalCandles.get(lastIndex).date();
            BacktestTrade trade = closeTrade(symbol, open, exitPrice, exitDate, lastIndex, ExitReason.TIME_STOP, config);
            trades.add(trade);
            capital += trade.pnl().doubleValue();
        }
        capitalCurve.add(capital);

        return buildResult(symbol, trades, capitalCurve, capital, config);
    }

    private static OpenPosition tryEnter(List<OhlcvCandle> chronologicalCandles,
                                         ClosePriceIndicator closePrice, OpenPriceIndicator openPrice,
                                         EMAIndicator ema20, EMAIndicator ema50, RSIIndicator rsi, ATRIndicator atr,
                                         VolumeIndicator volume, SMAIndicator volumeMa, HighestValueIndicator weeklyHigh,
                                         int i, double capital, BacktestConfig config, BigDecimal volumeMultiplier) {
        BigDecimal price = numToBigDecimal(closePrice.getValue(i));
        BigDecimal ema20Val = numToBigDecimal(ema20.getValue(i));
        BigDecimal ema50Val = numToBigDecimal(ema50.getValue(i));
        BigDecimal rsiVal = numToBigDecimal(rsi.getValue(i));
        BigDecimal volumeVal = numToBigDecimal(volume.getValue(i));
        BigDecimal volumeMaVal = numToBigDecimal(volumeMa.getValue(i));
        BigDecimal weeklyHighVal = numToBigDecimal(weeklyHigh.getValue(i));

        boolean trendAligned = price.compareTo(ema20Val) > 0 && ema20Val.compareTo(ema50Val) > 0;
        boolean rsiInRange = rsiVal.compareTo(PriceActionSignalEngine.RSI_LOWER_BOUND) >= 0
                && rsiVal.compareTo(PriceActionSignalEngine.RSI_UPPER_BOUND) <= 0;
        boolean volumeSurge = volumeVal.compareTo(volumeMaVal.multiply(volumeMultiplier)) > 0;
        boolean nearWeeklyHigh = price.compareTo(weeklyHighVal.multiply(PriceActionSignalEngine.HIGH_PROXIMITY_THRESHOLD)) >= 0;

        int rulesPassed = (trendAligned ? 1 : 0)
                + (rsiInRange ? 1 : 0)
                + (volumeSurge ? 1 : 0)
                + (nearWeeklyHigh ? 1 : 0);
        if (rulesPassed < 4) {
            return null;
        }

        int entryIndex = i + 1;
        BigDecimal nextOpen = numToBigDecimal(openPrice.getValue(entryIndex));
        BigDecimal entryPrice = nextOpen.multiply(BigDecimal.valueOf(1 + config.slippagePct()));
        BigDecimal atrVal = numToBigDecimal(atr.getValue(i));
        BigDecimal stopLoss = entryPrice.subtract(atrVal.multiply(BigDecimal.valueOf(config.atrMultiplierStop())));
        BigDecimal riskPerShare = entryPrice.subtract(stopLoss);

        if (riskPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal target = entryPrice.add(riskPerShare.multiply(BigDecimal.valueOf(config.rewardRiskRatio())));
        int quantity = (int) Math.floor((capital * config.riskPerTradePct()) / riskPerShare.doubleValue());

        if (quantity <= 0) {
            return null;
        }

        LocalDate entryDate = chronologicalCandles.get(entryIndex).date();
        return new OpenPosition(entryIndex, entryDate, entryPrice, stopLoss, target, quantity);
    }

    private static BacktestTrade closeTrade(String symbol, OpenPosition open, BigDecimal exitPrice, LocalDate exitDate,
                                            int exitIndex, ExitReason reason, BacktestConfig config) {
        double grossPnl = exitPrice.subtract(open.entryPrice).doubleValue() * open.quantity;
        double netPnl = grossPnl - config.brokeragePerTrade();
        double entryCost = open.entryPrice.doubleValue() * open.quantity;
        double pnlPct = entryCost != 0 ? (netPnl / entryCost) * 100.0 : 0.0;
        int holdingDays = exitIndex - open.entryIndex;

        return new BacktestTrade(symbol, open.entryDate, exitDate, open.entryPrice, exitPrice,
                open.stopLoss, open.target, open.quantity, reason, BigDecimal.valueOf(netPnl), BigDecimal.valueOf(pnlPct), holdingDays);
    }

    private static BacktestResult buildResult(String symbol, List<BacktestTrade> trades, List<Double> capitalCurve,
                                              double finalCapital, BacktestConfig config) {
        int totalTrades = trades.size();
        List<BacktestTrade> wins = trades.stream().filter(t -> t.pnl().signum() > 0).toList();
        List<BacktestTrade> losses = trades.stream().filter(t -> t.pnl().signum() <= 0).toList();

        double winRate = totalTrades > 0 ? (wins.size() / (double) totalTrades) * 100.0 : 0.0;
        double avgGainPct = wins.isEmpty() ? 0.0 : wins.stream().mapToDouble(t -> t.pnlPct().doubleValue()).average().orElse(0.0);
        double avgLossPct = losses.isEmpty() ? 0.0
                : Math.abs(losses.stream().mapToDouble(t -> t.pnlPct().doubleValue()).average().orElse(0.0));
        double maxDrawdownPct = computeMaxDrawdownPct(capitalCurve);
        double sharpeRatio = computeSharpeRatio(capitalCurve);
        double totalReturn = ((finalCapital - config.initialCapital()) / config.initialCapital()) * 100.0;
        double winRatio = winRate / 100.0;
        double expectancy = (winRatio * avgGainPct) - ((1 - winRatio) * avgLossPct);

        return new BacktestResult(symbol, totalTrades, wins.size(), losses.size(), winRate, avgGainPct, avgLossPct,
                maxDrawdownPct, sharpeRatio, totalReturn, expectancy, trades);
    }

    private static double computeSharpeRatio(List<Double> capitalCurve) {
        if (capitalCurve.size() < 3) {
            return 0.0;
        }
        double[] dailyReturns = new double[capitalCurve.size() - 1];
        for (int i = 1; i < capitalCurve.size(); i++) {
            double prev = capitalCurve.get(i - 1);
            double curr = capitalCurve.get(i);
            dailyReturns[i - 1] = prev != 0 ? (curr - prev) / prev : 0.0;
        }
        double mean = java.util.Arrays.stream(dailyReturns).average().orElse(0.0);
        double variance = java.util.Arrays.stream(dailyReturns).map(r -> Math.pow(r - mean, 2)).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        return stdDev == 0 ? 0.0 : (mean / stdDev) * Math.sqrt(252);
    }

    private static double computeMaxDrawdownPct(List<Double> capitalCurve) {
        if (capitalCurve.isEmpty()) {
            return 0.0;
        }
        double peak = capitalCurve.get(0);
        double maxDrawdown = 0.0;
        for (double value : capitalCurve) {
            peak = Math.max(peak, value);
            if (peak > 0) {
                maxDrawdown = Math.max(maxDrawdown, (peak - value) / peak * 100.0);
            }
        }
        return maxDrawdown;
    }

    private static BigDecimal numToBigDecimal(Num value) {
        return (BigDecimal) value.getDelegate();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record OpenPosition(int entryIndex,
                                LocalDate entryDate,
                                BigDecimal entryPrice,
                                BigDecimal stopLoss,
                                BigDecimal target,
                                int quantity,
                                int belowEma20Streak) {
        OpenPosition {
            belowEma20Streak = Math.max(0, belowEma20Streak);
        }

        OpenPosition(int entryIndex, LocalDate entryDate, BigDecimal entryPrice, BigDecimal stopLoss,
                     BigDecimal target, int quantity) {
            this(entryIndex, entryDate, entryPrice, stopLoss, target, quantity, 0);
        }
    }
}
