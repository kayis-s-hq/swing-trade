package com.swingtrade.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Stock;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.WatchlistStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
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
import org.ta4j.core.num.Num;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Simulates the Phase 2 price-action entry rules bar-by-bar against historical candles to
 * produce trade-level and aggregate backtest performance metrics.
 * <p>
 * Entry rule thresholds and indicator periods are reused directly from
 * {@link PriceActionSignalEngine} (package-private constants) so the backtest can never drift
 * from the live signal engine's rules.
 */
@Service
public class BacktestEngine {

    private static final Logger logger = LoggerFactory.getLogger(BacktestEngine.class);

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Warm-up (50, from PriceActionSignalEngine.EMA_SLOW_PERIOD) plus a handful of tradable days.
     */
    private static final int MIN_CANDLES_FOR_BACKTEST = 60;

    private final CandleStore candleStore;
    private final WatchlistStore watchlistStore;
    private final PriceActionSignalEngine priceActionSignalEngine;
    private final ObjectMapper objectMapper;
    private final String reportsDir;

    public BacktestEngine(CandleStore candleStore,
                          WatchlistStore watchlistStore,
                          PriceActionSignalEngine priceActionSignalEngine,
                          ObjectMapper objectMapper,
                          @Value("${backtest.reports.dir:reports}") String reportsDir) {
        this.candleStore = candleStore;
        this.watchlistStore = watchlistStore;
        this.priceActionSignalEngine = priceActionSignalEngine;
        this.objectMapper = objectMapper;
        this.reportsDir = reportsDir;
    }

    /**
     * Runs a single-symbol backtest.
     *
     * @param symbol   the stock symbol
     * @param exchange accepted for API symmetry/future filtering; candles are not currently
     *                 partitioned by exchange, so this does not affect which candles are loaded
     * @param config   backtest tuning parameters
     * @return aggregate performance metrics and the full trade list
     * @throws IllegalArgumentException if symbol is null/blank or config is null
     * @throws IllegalStateException    if there isn't enough candle history to backtest
     */
    public BacktestResult runBacktest(String symbol, String exchange, BacktestConfig config) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        logger.debug("Running backtest for {} on {}", symbol, exchange);

        List<OhlcvCandle> chronologicalCandles = getDescendingCandles(symbol, candleStore, MIN_CANDLES_FOR_BACKTEST);

        return simulate(symbol, chronologicalCandles, config);
    }

    static List<OhlcvCandle> getDescendingCandles(String symbol, CandleStore candleStore, int minCandles) {
        List<OhlcvCandle> descendingCandles = candleStore.findTopBySymbolOrderByDateDesc(symbol, 1000);
        if (descendingCandles.size() < minCandles) {
            throw new IllegalStateException(
                    "Insufficient candle history for " + symbol + ": need at least "
                            + minCandles + " candles, found " + descendingCandles.size());
        }

        List<OhlcvCandle> chronologicalCandles = new ArrayList<>(descendingCandles);
        Collections.reverse(chronologicalCandles);
        return chronologicalCandles;
    }

    /**
     * Runs a backtest for each symbol in the given list, skipping (and logging) any symbol that
     * fails, e.g. due to insufficient candle history.
     */
    public List<BacktestResult> runBacktestAll(List<String> symbols, String exchange, BacktestConfig config) {
        List<BacktestResult> results = new ArrayList<>();
        int processed = 0;

        for (String symbol : symbols) {
            processed++;
            try {
                results.add(runBacktest(symbol, exchange, config));
            } catch (Exception e) {
                logger.warn("Skipping {} in backtest run: {}", symbol, e.getMessage());
            }

            if (processed % 10 == 0) {
                logger.info("Backtested {}/{} symbols", processed, symbols.size());
            }
        }

        logger.info("Backtested {}/{} symbols", processed, symbols.size());
        return results;
    }

    /**
     * Runs a backtest across every symbol in the active watchlist.
     */
    public List<BacktestResult> runBacktestAll(String exchange, BacktestConfig config) {
        List<String> symbols = watchlistStore.getWatchlist().stream()
                .map(Stock::symbol)
                .toList();
        return runBacktestAll(symbols, exchange, config);
    }

    /**
     * Aggregates a set of backtest results into a portfolio-level summary and persists it as
     * JSON (full summary) and CSV (flattened trade list) under the configured reports' directory.
     */
    public BacktestReportSummary generateReport(List<BacktestResult> results) {
        List<BacktestResult> top10ByWinRate = topN(results, Comparator.comparingDouble(BacktestResult::winRate).reversed(), 10);
        List<BacktestResult> top10ByTotalReturn = topN(results, Comparator.comparingDouble(BacktestResult::totalReturn).reversed(), 10);

        int totalWins = results.stream().mapToInt(BacktestResult::winningTrades).sum();
        int totalTrades = results.stream().mapToInt(BacktestResult::totalTrades).sum();
        double overallWinRate = totalTrades > 0 ? (totalWins / (double) totalTrades) * 100.0 : 0.0;

        double overallSharpeRatio = results.stream()
                .filter(r -> r.totalTrades() > 0)
                .mapToDouble(BacktestResult::sharpeRatio)
                .average()
                .orElse(0.0);

        BacktestReportSummary summary = new BacktestReportSummary(
                LocalDateTime.now(MARKET_ZONE), results.size(), top10ByWinRate, top10ByTotalReturn,
                overallWinRate, overallSharpeRatio, results);

        saveReport(summary);
        return summary;
    }

    // -----------------------------------------------------------------------
    // Simulation
    // -----------------------------------------------------------------------

    private BacktestResult simulate(String symbol, List<OhlcvCandle> chronologicalCandles, BacktestConfig config) {
        BarSeries series = priceActionSignalEngine.buildBarSeries(symbol, chronologicalCandles);
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
                    capital += trade.pnl();
                    open = null;
                } else {
                    open = new OpenPosition(open.entryIndex(), open.entryDate(), open.entryPrice(),
                            open.stopLoss(), open.target(), open.quantity(), streak);
                }
            }

            if (open == null && i + 1 < barCount) {
                open = tryEnter(chronologicalCandles, series, closePrice, openPrice, ema20, ema50, rsi, atr, volume, volumeMa,
                        weeklyHigh, i, capital, config);
            }
        }

        if (open != null) {
            int lastIndex = barCount - 1;
            BigDecimal exitPrice = numToBigDecimal(closePrice.getValue(lastIndex));
            LocalDate exitDate = chronologicalCandles.get(lastIndex).date();
            BacktestTrade trade = closeTrade(symbol, open, exitPrice, exitDate, lastIndex, ExitReason.TIME_STOP, config);
            trades.add(trade);
            capital += trade.pnl();
        }
        capitalCurve.add(capital);

        return buildResult(symbol, trades, capitalCurve, capital, config);
    }

    private OpenPosition tryEnter(List<OhlcvCandle> chronologicalCandles,
                                  BarSeries series,
                                  ClosePriceIndicator closePrice, OpenPriceIndicator openPrice,
                                  EMAIndicator ema20, EMAIndicator ema50, RSIIndicator rsi, ATRIndicator atr,
                                  VolumeIndicator volume, SMAIndicator volumeMa, HighestValueIndicator weeklyHigh,
                                  int i, double capital, BacktestConfig config) {
        Indicators ind = Indicators.from(series, closePrice, openPrice, ema20, ema50, rsi, atr, volume, volumeMa,
                weeklyHigh, i);

        boolean trendAligned = ind.price().compareTo(ind.ema20()) > 0 && ind.ema20().compareTo(ind.ema50()) > 0;
        boolean rsiInRange = ind.rsi().compareTo(PriceActionSignalEngine.RSI_LOWER_BOUND) >= 0
                && ind.rsi().compareTo(PriceActionSignalEngine.RSI_UPPER_BOUND) <= 0;
        boolean volumeSurge = ind.volume().compareTo(ind.volumeMa().multiply(PriceActionSignalEngine.VOLUME_MULTIPLIER)) > 0;
        boolean nearWeeklyHigh = ind.price().compareTo(ind.weeklyHigh().multiply(PriceActionSignalEngine.HIGH_PROXIMITY_THRESHOLD)) >= 0;

        int rulesPassed = (trendAligned ? 1 : 0)
                + (rsiInRange ? 1 : 0)
                + (volumeSurge ? 1 : 0)
                + (nearWeeklyHigh ? 1 : 0);
        if (rulesPassed < 3) {
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

    private BacktestTrade closeTrade(String symbol, OpenPosition open, BigDecimal exitPrice, LocalDate exitDate,
                                     int exitIndex, ExitReason reason, BacktestConfig config) {
        double grossPnl = exitPrice.subtract(open.entryPrice).doubleValue() * open.quantity;
        double netPnl = grossPnl - config.brokeragePerTrade();
        double entryCost = open.entryPrice.doubleValue() * open.quantity;
        double pnlPct = entryCost != 0 ? (netPnl / entryCost) * 100.0 : 0.0;
        int holdingDays = exitIndex - open.entryIndex;

        return new BacktestTrade(symbol, open.entryDate, exitDate, open.entryPrice, exitPrice,
                open.stopLoss, open.target, open.quantity, reason, netPnl, pnlPct, holdingDays);
    }

    private BacktestResult buildResult(String symbol, List<BacktestTrade> trades, List<Double> capitalCurve,
                                       double finalCapital, BacktestConfig config) {
        int totalTrades = trades.size();
        List<BacktestTrade> wins = trades.stream().filter(t -> t.pnl() > 0).toList();
        List<BacktestTrade> losses = trades.stream().filter(t -> t.pnl() <= 0).toList();

        double winRate = totalTrades > 0 ? (wins.size() / (double) totalTrades) * 100.0 : 0.0;
        double avgGainPct = wins.isEmpty() ? 0.0 : wins.stream().mapToDouble(BacktestTrade::pnlPct).average().orElse(0.0);
        double avgLossPct = losses.isEmpty() ? 0.0
                : Math.abs(losses.stream().mapToDouble(BacktestTrade::pnlPct).average().orElse(0.0));
        double maxDrawdownPct = computeMaxDrawdownPct(capitalCurve);
        double sharpeRatio = computeSharpeRatio(capitalCurve);
        double totalReturn = ((finalCapital - config.initialCapital()) / config.initialCapital()) * 100.0;
        double winRatio = winRate / 100.0;
        double expectancy = (winRatio * avgGainPct) - ((1 - winRatio) * avgLossPct);

        return new BacktestResult(symbol, totalTrades, wins.size(), losses.size(), winRate, avgGainPct, avgLossPct,
                maxDrawdownPct, sharpeRatio, totalReturn, expectancy, trades);
    }

    private double computeSharpeRatio(List<Double> capitalCurve) {
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

    private double computeMaxDrawdownPct(List<Double> capitalCurve) {
        if (capitalCurve.isEmpty()) {
            return 0.0;
        }

        double peak = capitalCurve.getFirst();
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
        return BigDecimal.valueOf(value.doubleValue());
    }

    private <T> List<T> topN(List<T> items, Comparator<T> comparator, int n) {
        return items.stream()
                .sorted(comparator)
                .limit(n)
                .toList();
    }

    // -----------------------------------------------------------------------
    // Report persistence
    // -----------------------------------------------------------------------

    private void saveReport(BacktestReportSummary summary) {
        try {
            Path dir = Path.of(reportsDir);
            Files.createDirectories(dir);
            String timestamp = summary.generatedAt().format(TIMESTAMP_FORMAT);

            Path jsonPath = dir.resolve("backtest_" + timestamp + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), summary);

            Path csvPath = dir.resolve("backtest_" + timestamp + ".csv");
            writeTradesCsv(csvPath, summary.results());

            logger.info("Saved backtest report: {} and {}", jsonPath.getFileName(), csvPath.getFileName());
        } catch (IOException e) {
            logger.error("Failed to save backtest report: {}", e.getMessage(), e);
        }
    }

    private void writeTradesCsv(Path csvPath, List<BacktestResult> results) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("symbol,entryDate,exitDate,entryPrice,exitPrice,stopLoss,target,quantity,exitReason,pnl,pnlPct,holdingDays\n");
        for (BacktestResult result : results) {
            for (BacktestTrade trade : result.trades()) {
                csv.append(trade.symbol()).append(',')
                        .append(trade.entryDate()).append(',')
                        .append(trade.exitDate()).append(',')
                        .append(trade.entryPrice()).append(',')
                        .append(trade.exitPrice()).append(',')
                        .append(trade.stopLoss()).append(',')
                        .append(trade.target()).append(',')
                        .append(trade.quantity()).append(',')
                        .append(trade.exitReason()).append(',')
                        .append(trade.pnl()).append(',')
                        .append(trade.pnlPct()).append(',')
                        .append(trade.holdingDays())
                        .append('\n');
            }
        }
        Files.writeString(csvPath, csv.toString());
    }

    record Indicators(BigDecimal price, BigDecimal ema20, BigDecimal ema50, BigDecimal rsi,
                      BigDecimal volume, BigDecimal volumeMa, BigDecimal weeklyHigh) {
        static Indicators from(BarSeries series, ClosePriceIndicator closePrice, OpenPriceIndicator openPrice,
                               EMAIndicator ema20, EMAIndicator ema50, RSIIndicator rsi, ATRIndicator atr,
                               VolumeIndicator volume, SMAIndicator volumeMa, HighestValueIndicator weeklyHigh,
                               int bar) {
            return new Indicators(
                    numToBigDecimal(closePrice.getValue(bar)),
                    numToBigDecimal(ema20.getValue(bar)),
                    numToBigDecimal(ema50.getValue(bar)),
                    numToBigDecimal(rsi.getValue(bar)),
                    numToBigDecimal(volume.getValue(bar)),
                    numToBigDecimal(volumeMa.getValue(bar)),
                    numToBigDecimal(weeklyHigh.getValue(bar)));
        }
    }

    // -----------------------------------------------------------------------
    // Open position tracking
    // -----------------------------------------------------------------------

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
