package com.swingtrade.strategy;

import tools.jackson.databind.ObjectMapper;
import com.swingtrade.domain.BenchmarkComparison;
import com.swingtrade.domain.BenchmarkCandleSeries;
import com.swingtrade.domain.CorporateAction;
import com.swingtrade.domain.HistoricalCandleAdjuster;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.OhlcvDataQuality;
import com.swingtrade.domain.PriceBand;
import com.swingtrade.domain.PriceBandPolicy;
import com.swingtrade.domain.RiskManagementPolicy;
import com.swingtrade.domain.Stock;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.BenchmarkDataAdapter;
import com.swingtrade.domain.store.PriceBandStore;
import com.swingtrade.domain.store.CorporateActionStore;
import com.swingtrade.domain.store.UniverseSnapshotStore;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private static final BacktestCostModel DEFAULT_COST_MODEL = new ZerodhaDeliveryCostModel();
    private final PortfolioBacktestEngine portfolioBacktestEngine = new PortfolioBacktestEngine();

    private final CandleStore candleStore;
    private final WatchlistStore watchlistStore;
    private final PriceActionSignalEngine priceActionSignalEngine;
    private final StrategyRegistry strategyRegistry;
    private final ObjectMapper objectMapper;
    private final String reportsDir;
    private final PriceBandStore priceBandStore;
    private final UniverseSnapshotStore universeSnapshotStore;
    private final CorporateActionStore corporateActionStore;
    private final BenchmarkDataAdapter benchmarkDataAdapter;

    public BacktestEngine(CandleStore candleStore,
                          WatchlistStore watchlistStore,
                          PriceActionSignalEngine priceActionSignalEngine,
                          StrategyRegistry strategyRegistry,
                          ObjectMapper objectMapper,
                          @Value("${backtest.reports.dir:reports}") String reportsDir) {
        this(candleStore, watchlistStore, priceActionSignalEngine, strategyRegistry, objectMapper,
            reportsDir, emptyPriceBandStore());
    }

    public BacktestEngine(CandleStore candleStore,
                          WatchlistStore watchlistStore,
                          PriceActionSignalEngine priceActionSignalEngine,
                          StrategyRegistry strategyRegistry,
                          ObjectMapper objectMapper,
                          @Value("${backtest.reports.dir:reports}") String reportsDir,
                          PriceBandStore priceBandStore) {
        this(candleStore, watchlistStore, priceActionSignalEngine, strategyRegistry, objectMapper, reportsDir,
            priceBandStore, permissiveUniverseStore(), permissiveCorporateActionStore());
    }

    /** Production constructor: historical analytics are fail-closed on missing provenance. */
    public BacktestEngine(CandleStore candleStore, WatchlistStore watchlistStore,
                          PriceActionSignalEngine priceActionSignalEngine, StrategyRegistry strategyRegistry,
                          ObjectMapper objectMapper,
                          @Value("${backtest.reports.dir:reports}") String reportsDir,
                          PriceBandStore priceBandStore,
                          UniverseSnapshotStore universeSnapshotStore, CorporateActionStore corporateActionStore) {
        this(candleStore, watchlistStore, priceActionSignalEngine, strategyRegistry, objectMapper, reportsDir,
                priceBandStore, universeSnapshotStore, corporateActionStore, emptyBenchmarkDataAdapter());
    }

    /** Production constructor: historical analytics are fail-closed on missing provenance. */
    @org.springframework.beans.factory.annotation.Autowired
    public BacktestEngine(CandleStore candleStore, WatchlistStore watchlistStore,
                          PriceActionSignalEngine priceActionSignalEngine, StrategyRegistry strategyRegistry,
                          ObjectMapper objectMapper,
                          @Value("${backtest.reports.dir:reports}") String reportsDir,
                          PriceBandStore priceBandStore,
                          UniverseSnapshotStore universeSnapshotStore, CorporateActionStore corporateActionStore,
                          BenchmarkDataAdapter benchmarkDataAdapter) {
        this.candleStore = candleStore;
        this.watchlistStore = watchlistStore;
        this.priceActionSignalEngine = priceActionSignalEngine;
        this.strategyRegistry = strategyRegistry;
        this.objectMapper = objectMapper;
        this.reportsDir = reportsDir;
        this.priceBandStore = priceBandStore;
        this.universeSnapshotStore = universeSnapshotStore;
        this.corporateActionStore = corporateActionStore;
        this.benchmarkDataAdapter = benchmarkDataAdapter;
    }

    private static UniverseSnapshotStore permissiveUniverseStore() {
        return new UniverseSnapshotStore() {
            public java.util.Optional<com.swingtrade.domain.UniverseSnapshot> findBySymbolAndDate(String s, LocalDate d) { return java.util.Optional.empty(); }
            public java.util.Optional<com.swingtrade.domain.UniverseSnapshot> findLatestBySymbolAndDateOnOrBefore(String s, LocalDate d) { return java.util.Optional.of(new com.swingtrade.domain.UniverseSnapshot(s, d, null, null, true, "legacy-test", java.time.Instant.EPOCH)); }
            public List<com.swingtrade.domain.UniverseSnapshot> findByDate(LocalDate d) { return List.of(); }
            public void save(com.swingtrade.domain.UniverseSnapshot snapshot) {}
        };
    }

    private static CorporateActionStore permissiveCorporateActionStore() {
        return new CorporateActionStore() {
            public List<CorporateAction> findBySymbolAndEffectiveDateBetween(String s, LocalDate f, LocalDate t) { return List.of(); }
            public void save(CorporateAction action) {}
        };
    }

    private static PriceBandStore emptyPriceBandStore() {
        return new PriceBandStore() {
            @Override public java.util.Optional<PriceBand> findBySymbolAndDate(String symbol, LocalDate date) {
                return java.util.Optional.empty();
            }
            @Override public void save(PriceBand priceBand) {}
        };
    }

    private static BenchmarkDataAdapter emptyBenchmarkDataAdapter() {
        return (from, to) -> Optional.empty();
    }

    /**
     * Runs a single-symbol backtest using the default (production) strategy.
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
        return runBacktest(symbol, exchange, config, strategyRegistry.defaultStrategy());
    }

    /**
     * Runs a single-symbol backtest against an explicitly chosen {@link TradingStrategy} -
     * for backtest/testing comparison only; live signal generation stays pinned to the
     * default strategy and never takes this parameter.
     */
    public BacktestResult runBacktest(String symbol, String exchange, BacktestConfig config, TradingStrategy strategy) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }
        logger.debug("Running backtest for {} on {} using strategy {}", symbol, exchange, strategy.name());

        List<OhlcvCandle> chronologicalCandles = historicalCandles(symbol, exchange,
            getDescendingCandles(symbol, candleStore, MIN_CANDLES_FOR_BACKTEST));

        return simulate(symbol, qualityChecked(symbol, chronologicalCandles), config, strategy);
    }

    /**
     * Runs a backtest whose metrics include only trades and equity observations inside the given
     * evaluation window. Indicator warm-up candles before {@code evaluationStart} are retained,
     * but no position may be opened before the boundary.
     */
    public BacktestResult runBacktestWindow(String symbol, String exchange, BacktestConfig config,
                                            LocalDate evaluationStart, LocalDate evaluationEnd) {
        return runBacktestWindow(symbol, exchange, config, strategyRegistry.defaultStrategy(),
            evaluationStart, evaluationEnd);
    }

    public BacktestResult runBacktestWindow(String symbol, String exchange, BacktestConfig config,
                                            TradingStrategy strategy, LocalDate evaluationStart,
                                            LocalDate evaluationEnd) {
        if (evaluationStart == null || evaluationEnd == null || evaluationStart.isAfter(evaluationEnd)) {
            throw new IllegalArgumentException("Evaluation window must be non-empty and ordered");
        }
        if (config == null || strategy == null) {
            throw new IllegalArgumentException("Config and strategy cannot be null");
        }
        List<OhlcvCandle> descending = candleStore.findBySymbolAndDateRange(
            symbol, evaluationStart.minusDays(400), evaluationEnd);
        List<OhlcvCandle> chronological = new ArrayList<>(descending);
        chronological.sort(Comparator.comparing(OhlcvCandle::date));
        chronological = historicalCandles(symbol, exchange, chronological);
        chronological = qualityChecked(symbol, chronological);
        if (chronological.size() < MIN_CANDLES_FOR_BACKTEST) {
            throw new IllegalStateException("Insufficient candle history for evaluation window");
        }
        int start = 0;
        while (start < chronological.size() && chronological.get(start).date().isBefore(evaluationStart)) start++;
        int end = chronological.size() - 1;
        while (end >= 0 && chronological.get(end).date().isAfter(evaluationEnd)) end--;
        if (start > end || end - start < 2) {
            throw new IllegalStateException("Evaluation window contains insufficient candles");
        }
        return simulate(symbol, chronological, config, strategy, start, end);
    }

    private List<OhlcvCandle> historicalCandles(String symbol, String exchange, List<OhlcvCandle> candles) {
        if (candles.isEmpty()) return candles;
        LocalDate from = candles.get(0).date();
        LocalDate to = candles.get(candles.size() - 1).date();
        List<CorporateAction> actions = corporateActionStore
            .findBySymbolAndEffectiveDateBetween(symbol, from, to);
        return candles.stream().map(candle -> {
            var snapshot = universeSnapshotStore.findLatestBySymbolAndDateOnOrBefore(symbol, candle.date());
            if (snapshot.isEmpty() || !snapshot.get().included()
                    || (exchange != null && !exchange.isBlank() && snapshot.get().exchange() != null
                        && !exchange.equalsIgnoreCase(snapshot.get().exchange()))) {
                throw new IllegalStateException("No included historical universe membership for "
                    + symbol + " on " + candle.date());
            }
            return HistoricalCandleAdjuster.adjust(candle, actions);
        }).toList();
    }

    /**
     * Evaluates bounded, non-overlapping trailing OOS folds. Folds are returned in
     * chronological order; each fold retains the normal indicator warm-up behavior.
     */
    public WalkForwardEvaluation runWalkForward(String symbol, String exchange, BacktestConfig config,
                                                 int oosDays, int requestedFolds) {
        return runWalkForward(symbol, exchange, config, strategyRegistry.defaultStrategy(), oosDays,
            requestedFolds);
    }

    public WalkForwardEvaluation runWalkForward(String symbol, String exchange, BacktestConfig config,
                                                 TradingStrategy strategy, int oosDays, int requestedFolds) {
        if (oosDays < 60 || oosDays > 1000) {
            throw new IllegalArgumentException("oosDays must be between 60 and 1000");
        }
        if (requestedFolds < 1 || requestedFolds > 8) {
            throw new IllegalArgumentException("requestedFolds must be between 1 and 8");
        }
        List<OhlcvCandle> candles = new ArrayList<>(candleStore.findAllBySymbolOrderByDateDesc(symbol));
        candles.sort(Comparator.comparing(OhlcvCandle::date));
        int required = oosDays * requestedFolds;
        if (candles.size() < required) {
            throw new IllegalStateException("Insufficient candle history for " + requestedFolds
                + " OOS folds: need at least " + required + " candles, found " + candles.size());
        }

        List<WalkForwardEvaluation.Fold> folds = new ArrayList<>(requestedFolds);
        for (int fold = requestedFolds - 1; fold >= 0; fold--) {
            int startIndex = candles.size() - ((fold + 1) * oosDays);
            int endIndex = startIndex + oosDays - 1;
            LocalDate startDate = candles.get(startIndex).date();
            LocalDate endDate = candles.get(endIndex).date();
            BacktestResult result = runBacktestWindow(symbol, exchange, config, strategy, startDate, endDate);
            folds.add(new WalkForwardEvaluation.Fold(startDate, endDate, result));
        }
        double averageWinRate = folds.stream().mapToDouble(f -> f.result().winRate()).average().orElse(0.0);
        double averageTotalReturn = folds.stream().mapToDouble(f -> f.result().totalReturn()).average().orElse(0.0);
        int totalTrades = folds.stream().mapToInt(f -> f.result().totalTrades()).sum();
        return new WalkForwardEvaluation(folds, averageWinRate, averageTotalReturn, totalTrades);
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

    private List<OhlcvCandle> qualityChecked(String symbol, List<OhlcvCandle> candles) {
        OhlcvDataQuality.Assessment quality = OhlcvDataQuality.quarantineUnexplainedGaps(
            candles, PriceActionSignalEngine.MAX_ANALYTICAL_GAP_RATIO);
        if (!quality.quarantined().isEmpty()) {
            logger.warn("Quarantined {} candle(s) from backtest input for {}: {}",
                quality.quarantined().size(), symbol, quality.quarantined().get(0).reason());
        }
        if (quality.accepted().size() < MIN_CANDLES_FOR_BACKTEST) {
            throw new IllegalStateException("Insufficient quality candle history for " + symbol);
        }
        return quality.accepted();
    }

    /**
     * Runs a backtest for each symbol in the given list, skipping (and logging) any symbol that
     * fails, e.g. due to insufficient candle history.
     */
    public List<BacktestResult> runBacktestAll(List<String> symbols, String exchange, BacktestConfig config) {
        return runBacktestAll(symbols, exchange, config, strategyRegistry.defaultStrategy());
    }

    /**
     * Runs a backtest for each symbol against an explicitly chosen {@link TradingStrategy} -
     * for backtest/testing comparison only.
     */
    public List<BacktestResult> runBacktestAll(List<String> symbols, String exchange, BacktestConfig config,
                                               TradingStrategy strategy) {
        List<BacktestResult> results = new ArrayList<>();
        int processed = 0;

        for (String symbol : symbols) {
            processed++;
            try {
                results.add(runBacktest(symbol, exchange, config, strategy));
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
     * Runs a backtest across every symbol in the active watchlist using the default
     * (production) strategy.
     */
    public List<BacktestResult> runBacktestAll(String exchange, BacktestConfig config) {
        return runBacktestAll(exchange, config, strategyRegistry.defaultStrategy());
    }

    /**
     * Runs a backtest across every symbol in the active watchlist against an explicitly
     * chosen {@link TradingStrategy} - for backtest/testing comparison only.
     */
    public List<BacktestResult> runBacktestAll(String exchange, BacktestConfig config, TradingStrategy strategy) {
        List<String> symbols = watchlistStore.getWatchlist().stream()
                .map(Stock::symbol)
                .toList();
        return runBacktestAll(symbols, exchange, config, strategy);
    }

    /**
     * Runs each symbol through the existing windowed backtest, then applies the resulting dated
     * trades to one shared cash account. This is intentionally bounded to the supplied symbols
     * and window; the existing independent-symbol APIs remain unchanged.
     */
    public PortfolioBacktestResult runPortfolioBacktest(List<String> symbols, String exchange,
                                                        BacktestConfig config,
                                                        LocalDate evaluationStart, LocalDate evaluationEnd) {
        return runPortfolioBacktest(symbols, exchange, config, strategyRegistry.defaultStrategy(),
                evaluationStart, evaluationEnd);
    }

    /**
     * Runs the portfolio backtest against an explicitly chosen {@link TradingStrategy}, with the
     * result attributed to {@link TradingStrategy#name()}. The portfolio backtest evaluates a
     * single strategy across every symbol in one run; use
     * {@link #runPortfolioBacktest(List, String, BacktestConfig, TradingStrategy, String, LocalDate, LocalDate)}
     * to attribute the run to a persisted {@code StrategyConfig} variant id instead (e.g. when
     * comparing shadow/champion variants).
     */
    public PortfolioBacktestResult runPortfolioBacktest(List<String> symbols, String exchange,
                                                        BacktestConfig config, TradingStrategy strategy,
                                                        LocalDate evaluationStart, LocalDate evaluationEnd) {
        return runPortfolioBacktest(symbols, exchange, config, strategy,
                strategy == null ? null : strategy.name(), evaluationStart, evaluationEnd);
    }

    /**
     * Runs the portfolio backtest against an explicitly chosen {@link TradingStrategy}, tagging
     * the resulting {@link PortfolioBacktestResult#strategyVariantId()} with the given id so
     * portfolio-level runs across multiple configured strategy variants can be told apart and
     * compared downstream (e.g. by a future portfolio-level analogue of
     * {@code PromotionEligibilityChecker}). Sector exposure limits (see
     * {@link com.swingtrade.domain.PortfolioExposurePolicy}) are evaluated against each symbol's
     * production {@link com.swingtrade.domain.Stock.Sector} taxonomy, sourced from the watchlist.
     */
    public PortfolioBacktestResult runPortfolioBacktest(List<String> symbols, String exchange,
                                                        BacktestConfig config, TradingStrategy strategy,
                                                        String strategyVariantId,
                                                        LocalDate evaluationStart, LocalDate evaluationEnd) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Symbols cannot be null or empty");
        }
        if (config == null || strategy == null) {
            throw new IllegalArgumentException("Config and strategy cannot be null");
        }
        List<BacktestResult> results = new ArrayList<>();
        Map<String, List<OhlcvCandle>> marketData = new HashMap<>();
        for (String symbol : symbols.stream().distinct().sorted().toList()) {
            try {
                List<OhlcvCandle> candles = new ArrayList<>(candleStore.findBySymbolAndDateRange(
                        symbol, evaluationStart.minusDays(400), evaluationEnd));
                candles.sort(Comparator.comparing(OhlcvCandle::date));
                marketData.put(symbol, qualityChecked(symbol, historicalCandles(symbol, exchange, candles)).stream()
                        .filter(candle -> !candle.date().isBefore(evaluationStart)
                                && !candle.date().isAfter(evaluationEnd)).toList());
                results.add(runBacktestWindow(symbol, exchange, config, strategy, evaluationStart, evaluationEnd));
            } catch (RuntimeException e) {
                marketData.remove(symbol);
                logger.warn("Skipping {} in portfolio backtest: {}", symbol, e.getMessage());
            }
        }
        Optional<BenchmarkCandleSeries> benchmark;
        try {
            benchmark = benchmarkDataAdapter.findNifty50(evaluationStart, evaluationEnd);
        } catch (RuntimeException e) {
            logger.warn("NIFTY benchmark unavailable for portfolio backtest: {}", e.getMessage());
            benchmark = Optional.empty();
        }
        return portfolioBacktestEngine.simulate(results, config, evaluationStart, evaluationEnd, marketData,
                sectorsBySymbol(), benchmark, strategyVariantId);
    }

    /**
     * Sector taxonomy for {@link com.swingtrade.domain.PortfolioExposurePolicy} sector-exposure
     * limits, sourced from the production watchlist ({@link Stock#sector()}, populated via
     * fundamental-data ingestion). Stocks with no recorded sector are omitted rather than
     * fabricated, so an unclassified symbol is simply never sector-limited.
     */
    Map<String, String> sectorsBySymbol() {
        Map<String, String> sectors = new HashMap<>();
        for (Stock stock : watchlistStore.getWatchlist()) {
            if (stock.sector() != null) {
                sectors.put(stock.symbol(), stock.sector().name());
            }
        }
        return sectors;
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

    private BacktestResult simulate(String symbol, List<OhlcvCandle> chronologicalCandles, BacktestConfig config,
                                    TradingStrategy strategy) {
        return simulate(symbol, chronologicalCandles, config, strategy,
            PriceActionSignalEngine.MIN_REQUIRED_CANDLES, chronologicalCandles.size() - 1);
    }

    private BacktestResult simulate(String symbol, List<OhlcvCandle> chronologicalCandles, BacktestConfig config,
                                    TradingStrategy strategy, int evaluationStartIndex, int evaluationEndIndex) {
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

        int firstEvaluationBar = Math.max(PriceActionSignalEngine.MIN_REQUIRED_CANDLES, evaluationStartIndex);
        int lastEvaluationBar = Math.min(evaluationEndIndex, barCount - 1);
        for (int i = firstEvaluationBar; i <= lastEvaluationBar; i++) {
            if (open != null) {
                BigDecimal low = numToBigDecimal(lowPrice.getValue(i));
                BigDecimal high = numToBigDecimal(highPrice.getValue(i));
                BigDecimal close = numToBigDecimal(closePrice.getValue(i));
                BigDecimal ema20Val = numToBigDecimal(ema20.getValue(i));
                BigDecimal ema50Val = numToBigDecimal(ema50.getValue(i));
                BigDecimal rsiVal = numToBigDecimal(rsi.getValue(i));
                Indicators exitIndicators = new Indicators(close, ema20Val, ema50Val, rsiVal, null, null, null);

                int streak = close.compareTo(ema20Val) < 0 ? open.belowEma20Streak + 1 : 0;
                BigDecimal exitPrice = null;
                ExitReason reason = null;

                RiskManagementPolicy.RiskManagementDecision managedDecision = config.riskManagementPolicy()
                        .evaluate(new RiskManagementPolicy.RiskManagementContext(
                                open.entryPrice(), open.stopLoss(), open.target(), close, low,
                                high, open.highestCloseBeforeBar(), i - open.entryIndex(),
                                open.partialExitTaken(), numToBigDecimal(atr.getValue(Math.max(0, i - 1)))));
                boolean partialExit = false;
                if (managedDecision.partialExitRatio() != null) {
                    int partialQuantity = (int) Math.floor(open.quantity()
                            * managedDecision.partialExitRatio().doubleValue());
                    if (partialQuantity > 0 && partialQuantity < open.quantity()) {
                        BigDecimal partialPrice = managedDecision.stopPrice();
                        BigDecimal barOpen = openPriceForBar(openPrice, i);
                        if (barOpen.compareTo(partialPrice) >= 0) partialPrice = barOpen;
                        BacktestTrade partialTrade = closeTrade(symbol, open, partialPrice,
                                chronologicalCandles.get(i).date(), i, ExitReason.TARGET_HIT, config,
                                partialQuantity);
                        trades.add(partialTrade);
                        capital += partialTrade.pnl();
                        open = new OpenPosition(open.entryIndex(), open.entryDate(), open.entryPrice(),
                                open.stopLoss(), open.target(), open.quantity() - partialQuantity,
                                streak, open.highestCloseBeforeBar().max(close), true);
                        partialExit = true;
                    }
                }
                if (!partialExit && managedDecision.exit()) {
                    reason = managedExitReason(managedDecision.reason());
                    exitPrice = managedDecision.stopPrice();
                }

                // Evaluated through the same TradingStrategy instance used for entry so the
                // backtest can never drift from its rules.
                boolean signalExitTriggered = strategy.isSignalExit(exitIndicators);

                if (!partialExit && reason == null && low.compareTo(open.stopLoss()) <= 0) {
                    reason = ExitReason.STOP_LOSS;
                    BigDecimal barOpen = openPriceForBar(openPrice, i);
                    exitPrice = barOpen.compareTo(open.stopLoss()) <= 0
                            ? barOpen : open.stopLoss();
                } else if (!partialExit && reason == null && high.compareTo(open.target()) >= 0) {
                    reason = ExitReason.TARGET_HIT;
                    BigDecimal barOpen = openPriceForBar(openPrice, i);
                    exitPrice = barOpen.compareTo(open.target()) >= 0 ? barOpen : open.target();
                } else if (!partialExit && reason == null && config.signalExitEnabled() && signalExitTriggered) {
                    reason = ExitReason.SIGNAL_EXIT;
                    exitPrice = close;
                } else if (!partialExit && reason == null && streak >= config.trendBreakStreakDays()) {
                    reason = ExitReason.TREND_BREAK;
                    exitPrice = close;
                } else if (!partialExit && reason == null && (i - open.entryIndex()) >= config.maxHoldingDays()) {
                    reason = ExitReason.TIME_STOP;
                    exitPrice = close;
                }

                PriceBand band = priceBandStore.findBySymbolAndDate(symbol,
                    chronologicalCandles.get(i).date()).orElse(null);
                if (reason != null && !PriceBandPolicy.blocksLongExit(band, chronologicalCandles.get(i))) {
                    LocalDate exitDate = chronologicalCandles.get(i).date();
                    BacktestTrade trade = closeTrade(symbol, open, exitPrice, exitDate, i, reason, config);
                    trades.add(trade);
                    capital += trade.pnl();
                    open = null;
                } else if (!partialExit) {
                    open = new OpenPosition(open.entryIndex(), open.entryDate(), open.entryPrice(),
                            open.stopLoss(), open.target(), open.quantity(), streak,
                            open.highestCloseBeforeBar().max(close), open.partialExitTaken());
                }
            }

            capitalCurve.add(markToMarket(capital, open, closePrice, i));

            if (open == null && i + 1 <= lastEvaluationBar) {
                PriceBand entryBand = priceBandStore.findBySymbolAndDate(symbol,
                    chronologicalCandles.get(i + 1).date()).orElse(null);
                open = tryEnter(chronologicalCandles, series, closePrice, openPrice, ema20, ema50, rsi, atr, volume, volumeMa,
                        weeklyHigh, i, capital, config, strategy, entryBand);
            }
        }

        if (open != null) {
            int lastIndex = lastEvaluationBar;
            BigDecimal exitPrice = numToBigDecimal(closePrice.getValue(lastIndex));
            LocalDate exitDate = chronologicalCandles.get(lastIndex).date();
            PriceBand finalBand = priceBandStore.findBySymbolAndDate(symbol, exitDate).orElse(null);
            if (!PriceBandPolicy.blocksLongExit(finalBand, chronologicalCandles.get(lastIndex))) {
                BacktestTrade trade = closeTrade(symbol, open, exitPrice, exitDate, lastIndex, ExitReason.TIME_STOP, config);
                trades.add(trade);
                capital += trade.pnl();
            } else {
                // No fill is assumed while the final bar is locked at the lower band.
                // Return marked-to-market capital and leave the trade absent from closed trades.
                capital = markToMarket(capital, open, closePrice, lastIndex);
            }
        }
        capitalCurve.add(capital);

        LocalDate evaluationStart = chronologicalCandles.get(firstEvaluationBar).date();
        LocalDate evaluationEnd = chronologicalCandles.get(lastEvaluationBar).date();
        BigDecimal benchmarkStartClose = chronologicalCandles.get(firstEvaluationBar)
                .adjustedForAnalysis().close();
        BigDecimal benchmarkEndClose = chronologicalCandles.get(lastEvaluationBar)
                .adjustedForAnalysis().close();
        return buildResult(symbol, trades, capitalCurve, capital, config, evaluationStart, evaluationEnd,
                benchmarkStartClose, benchmarkEndClose);
    }

    private OpenPosition tryEnter(List<OhlcvCandle> chronologicalCandles,
                                  BarSeries series,
                                  ClosePriceIndicator closePrice, OpenPriceIndicator openPrice,
                                  EMAIndicator ema20, EMAIndicator ema50, RSIIndicator rsi, ATRIndicator atr,
                                  VolumeIndicator volume, SMAIndicator volumeMa, HighestValueIndicator weeklyHigh,
                                  int i, double capital, BacktestConfig config, TradingStrategy strategy,
                                  PriceBand entryBand) {
        Indicators ind = indicatorsAt(series, closePrice, openPrice, ema20, ema50, rsi, atr, volume, volumeMa,
                weeklyHigh, i);

        // Evaluated through the same TradingStrategy instance PriceActionSignalEngine.analyze()
        // uses, so the backtest can never drift from its live rules (docs/backtesting.md).
        if (!strategy.isEntrySignal(ind)) {
            return null;
        }

        int entryIndex = i + 1;
        BigDecimal nextOpen = numToBigDecimal(openPrice.getValue(entryIndex));
        if (PriceBandPolicy.blocksLongEntry(entryBand, nextOpen)) {
            return null;
        }
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
        return new OpenPosition(entryIndex, entryDate, entryPrice, stopLoss, target, quantity,
                0, entryPrice, false);
    }

    private BacktestTrade closeTrade(String symbol, OpenPosition open, BigDecimal exitPrice, LocalDate exitDate,
                                     int exitIndex, ExitReason reason, BacktestConfig config) {
        return closeTrade(symbol, open, exitPrice, exitDate, exitIndex, reason, config, open.quantity);
    }

    private BacktestTrade closeTrade(String symbol, OpenPosition open, BigDecimal exitPrice, LocalDate exitDate,
                                     int exitIndex, ExitReason reason, BacktestConfig config, int quantity) {
        exitPrice = exitPrice.multiply(BigDecimal.valueOf(1 - config.slippagePct()));
        double grossPnl = exitPrice.subtract(open.entryPrice).doubleValue() * quantity;
        BigDecimal costs = DEFAULT_COST_MODEL.roundTripCost(open.entryPrice, exitPrice, quantity,
            BigDecimal.valueOf(config.brokeragePerTrade()));
        double netPnl = grossPnl - costs.doubleValue();
        double entryCost = open.entryPrice.doubleValue() * quantity;
        double pnlPct = entryCost != 0 ? (netPnl / entryCost) * 100.0 : 0.0;
        int holdingDays = exitIndex - open.entryIndex;

        return new BacktestTrade(symbol, open.entryDate, exitDate, open.entryPrice, exitPrice,
                open.stopLoss, open.target, quantity, reason, netPnl, pnlPct, holdingDays);
    }

    private static ExitReason managedExitReason(String reason) {
        return switch (reason) {
            case "TRAILING_STOP" -> ExitReason.TRAILING_STOP;
            case "BREAKEVEN_STOP" -> ExitReason.BREAKEVEN_STOP;
            default -> throw new IllegalArgumentException("Unsupported risk-management exit: " + reason);
        };
    }

    private static BigDecimal openPriceForBar(OpenPriceIndicator openPrice, int index) {
        return numToBigDecimal(openPrice.getValue(index));
    }

    private static double markToMarket(double realizedCapital, OpenPosition open,
                                       ClosePriceIndicator closePrice, int index) {
        if (open == null) {
            return realizedCapital;
        }
        double unrealized = closePrice.getValue(index).doubleValue() - open.entryPrice.doubleValue();
        return realizedCapital + unrealized * open.quantity;
    }

    private BacktestResult buildResult(String symbol, List<BacktestTrade> trades, List<Double> capitalCurve,
                                       double finalCapital, BacktestConfig config,
                                       LocalDate evaluationStart, LocalDate evaluationEnd,
                                       BigDecimal benchmarkStartClose, BigDecimal benchmarkEndClose) {
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
        double cagrPct = BacktestMetrics.cagrPct(config.initialCapital(), finalCapital,
                evaluationStart, evaluationEnd);
        double sortinoRatio = BacktestMetrics.sortinoRatio(capitalCurve);
        double calmarRatio = BacktestMetrics.calmarRatio(cagrPct, maxDrawdownPct);
        BenchmarkComparison benchmarkComparison = BacktestMetrics.buyAndHoldComparison(
                totalReturn, benchmarkStartClose, benchmarkEndClose);
        double winRatio = winRate / 100.0;
        double expectancy = (winRatio * avgGainPct) - ((1 - winRatio) * avgLossPct);

        return new BacktestResult(symbol, totalTrades, wins.size(), losses.size(), winRate, avgGainPct, avgLossPct,
                maxDrawdownPct, sharpeRatio, totalReturn, expectancy, trades,
                cagrPct, sortinoRatio, calmarRatio, benchmarkComparison);
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
        return (BigDecimal) value.getDelegate();
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

    private static Indicators indicatorsAt(BarSeries series, ClosePriceIndicator closePrice, OpenPriceIndicator openPrice,
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

    // -----------------------------------------------------------------------
    // Open position tracking
    // -----------------------------------------------------------------------

    private record OpenPosition(int entryIndex,
                                LocalDate entryDate,
                                BigDecimal entryPrice,
                                BigDecimal stopLoss,
                                BigDecimal target,
                                int quantity,
                                int belowEma20Streak,
                                BigDecimal highestCloseBeforeBar,
                                boolean partialExitTaken) {
        OpenPosition {
            belowEma20Streak = Math.max(0, belowEma20Streak);
            highestCloseBeforeBar = highestCloseBeforeBar == null ? entryPrice : highestCloseBeforeBar;
        }

        OpenPosition(int entryIndex, LocalDate entryDate, BigDecimal entryPrice, BigDecimal stopLoss,
                     BigDecimal target, int quantity) {
            this(entryIndex, entryDate, entryPrice, stopLoss, target, quantity, 0, entryPrice, false);
        }
    }
}
