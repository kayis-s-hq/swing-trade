package com.swingtrade.api;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.WatchlistStore;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestResult;
import com.swingtrade.strategy.BacktestTrade;
import com.swingtrade.strategy.ExitReason;
import com.swingtrade.strategy.PriceActionSignalEngine;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Throwaway diagnostic: connects directly to the live dev Postgres (bypassing the full
 * Spring Boot app, which has an unrelated environment issue preventing it from staying
 * up in this sandbox) to answer a specific question about the 2026-08-29 backtest run:
 * is TREND_BREAK (2 consecutive closes below EMA20) net-destructive to the strategy's
 * performance? Not part of the permanent test suite - remove after the investigation.
 */
@Disabled("Ad-hoc investigation against a live local Postgres, not a repeatable CI test")
class TrendBreakExperimentTest {

    private static final List<String> SYMBOLS = List.of(
        "AXISBANK", "BHARTIARTL", "HDFCBANK", "ICICIBANK", "INFY",
        "ITC", "RELIANCE", "SBIN", "TCS", "WIPRO"
    );

    @Test
    void compareTrendBreakEnabledVsDisabled() throws Exception {
        Map<String, List<OhlcvCandle>> candlesBySymbol = loadCandles();

        CandleStore candleStore = new InMemoryCandleStore(candlesBySymbol);
        WatchlistStore watchlistStore = mock(WatchlistStore.class);
        PriceActionSignalEngine priceActionSignalEngine =
            new PriceActionSignalEngine(candleStore, mock(com.swingtrade.core.metrics.SignalMetrics.class));
        BacktestEngine engine = new BacktestEngine(candleStore, watchlistStore, priceActionSignalEngine,
            new ObjectMapper(), "/tmp/backtest-experiment-reports");

        BacktestConfig baseline = BacktestConfig.defaults(); // trendBreakStreakDays=2
        BacktestConfig noTrendBreak = new BacktestConfig(
            baseline.slippagePct(), baseline.brokeragePerTrade(), baseline.riskPerTradePct(),
            baseline.initialCapital(), baseline.maxConcurrentPositions(), baseline.atrMultiplierStop(),
            baseline.rewardRiskRatio(), baseline.maxHoldingDays(), baseline.signalExitEnabled(),
            999 // effectively disables TREND_BREAK - only STOP_LOSS/TARGET_HIT/TIME_STOP remain
        );

        List<BacktestResult> baselineResults = engine.runBacktestAll(SYMBOLS, "NSE", baseline);
        List<BacktestResult> experimentResults = engine.runBacktestAll(SYMBOLS, "NSE", noTrendBreak);

        System.out.println("=== BASELINE (trendBreakStreakDays=2, current default) ===");
        printSummary(baselineResults);
        System.out.println();
        System.out.println("=== EXPERIMENT (trendBreakStreakDays=999, TREND_BREAK disabled) ===");
        printSummary(experimentResults);

        assertThat(baselineResults).isNotEmpty();
        assertThat(experimentResults).isNotEmpty();
    }

    private void printSummary(List<BacktestResult> results) {
        int totalTrades = 0;
        int totalWins = 0;
        double totalPnl = 0;
        Map<ExitReason, int[]> byReason = new EnumMap<>(ExitReason.class); // [count, wins]
        Map<ExitReason, Double> pnlByReason = new EnumMap<>(ExitReason.class);
        int symbolsAbove45 = 0;

        for (BacktestResult r : results) {
            totalTrades += r.totalTrades();
            totalWins += r.winningTrades();
            if (r.winRate() > 45.0) symbolsAbove45++;
            for (BacktestTrade t : r.trades()) {
                totalPnl += t.pnl();
                byReason.computeIfAbsent(t.exitReason(), k -> new int[2]);
                byReason.get(t.exitReason())[0]++;
                if (t.pnl() > 0) byReason.get(t.exitReason())[1]++;
                pnlByReason.merge(t.exitReason(), t.pnl(), Double::sum);
            }
        }

        double overallWinRate = totalTrades > 0 ? (100.0 * totalWins / totalTrades) : 0.0;
        System.out.printf("symbols=%d totalTrades=%d overallWinRate=%.1f%% totalPnl=%.1f symbolsWinRate>45%%=%d/%d%n",
            results.size(), totalTrades, overallWinRate, totalPnl, symbolsAbove45, results.size());
        for (var e : byReason.entrySet()) {
            int count = e.getValue()[0];
            int wins = e.getValue()[1];
            double pnl = pnlByReason.get(e.getKey());
            System.out.printf("  %-12s count=%-4d wins=%-4d winRate=%5.1f%% pnl=%.1f%n",
                e.getKey(), count, wins, count > 0 ? (100.0 * wins / count) : 0.0, pnl);
        }
        for (BacktestResult r : results) {
            System.out.printf("  %-12s trades=%-3d winRate=%5.1f%% totalReturn=%6.2f%%%n",
                r.symbol(), r.totalTrades(), r.winRate(), r.totalReturn());
        }
    }

    private Map<String, List<OhlcvCandle>> loadCandles() throws Exception {
        Map<String, List<OhlcvCandle>> result = new HashMap<>();
        String url = "jdbc:postgresql://192.168.0.100:5435/swingtrade_db";
        try (Connection conn = DriverManager.getConnection(url, "swingtrade_user", "swingtrade_password")) {
            for (String symbol : SYMBOLS) {
                List<OhlcvCandle> candles = new ArrayList<>();
                String sql = "SELECT date, open_price, high_price, low_price, close_price, volume, adj_close_price "
                    + "FROM ohlcv_candles WHERE symbol = ? ORDER BY date ASC";
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
                            candles.add(new OhlcvCandle(symbol, date, open, high, low, close, volume,
                                adjClose != null ? adjClose : close));
                        }
                    }
                }
                result.put(symbol, candles);
                System.out.println("Loaded " + candles.size() + " candles for " + symbol);
            }
        }
        return result;
    }

    /** Minimal read-only CandleStore backed by pre-loaded in-memory data. */
    private record InMemoryCandleStore(Map<String, List<OhlcvCandle>> data) implements CandleStore {
        @Override
        public Optional<OhlcvCandle> findBySymbolAndDate(String symbol, LocalDate date) {
            return data.getOrDefault(symbol, List.of()).stream()
                .filter(c -> c.date().equals(date)).findFirst();
        }

        @Override
        public List<OhlcvCandle> findBySymbol(String symbol) {
            return data.getOrDefault(symbol, List.of());
        }

        @Override
        public List<OhlcvCandle> findBySymbolAndDateRange(String symbol, LocalDate from, LocalDate to) {
            return data.getOrDefault(symbol, List.of()).stream()
                .filter(c -> !c.date().isBefore(from) && !c.date().isAfter(to)).toList();
        }

        @Override
        public List<OhlcvCandle> findTopBySymbolOrderByDateDesc(String symbol, int limit) {
            List<OhlcvCandle> all = data.getOrDefault(symbol, List.of());
            List<OhlcvCandle> reversed = new ArrayList<>(all);
            java.util.Collections.reverse(reversed);
            return reversed.size() > limit ? reversed.subList(0, limit) : reversed;
        }

        @Override
        public Optional<OhlcvCandle> findLatestBySymbol(String symbol) {
            List<OhlcvCandle> all = data.getOrDefault(symbol, List.of());
            return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
        }

        @Override
        public void save(OhlcvCandle candle) {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public boolean existsBySymbolAndDate(String symbol, LocalDate date) {
            return findBySymbolAndDate(symbol, date).isPresent();
        }

        @Override
        public List<String> findAllDistinctSymbols() {
            return new ArrayList<>(data.keySet());
        }

        @Override
        public Optional<OhlcvCandle> findEarliestBySymbol(String symbol) {
            return data.getOrDefault(symbol, List.of()).stream().findFirst();
        }

        @Override
        public long countBySymbol(String symbol) {
            return data.getOrDefault(symbol, List.of()).size();
        }

        @Override
        public void deleteBySymbol(String symbol) {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public Optional<OhlcvCandle> findLatestBySymbolBeforeDate(String symbol, LocalDate date) {
            return findBySymbol(symbol).stream()
                .filter(c -> c.date().isBefore(date)).reduce((first, second) -> second);
        }

        @Override
        public Optional<OhlcvCandle> findFirstBySymbolAndDateAfterOrderByDateAsc(String symbol,
                                                                                   LocalDate date) {
            return findBySymbol(symbol).stream().filter(c -> !c.date().isBefore(date)).findFirst();
        }

        @Override
        public Optional<OhlcvCandle> findNthBySymbolAndDateAfterOrderByDateAsc(String symbol,
                                                                                LocalDate after, int n) {
            return findBySymbol(symbol).stream().filter(c -> c.date().isAfter(after)).skip(n).findFirst();
        }

        @Override
        public List<OhlcvCandle> findAllBySymbolOrderByDateDesc(String symbol) {
            return findTopBySymbolOrderByDateDesc(symbol, Integer.MAX_VALUE);
        }

        @Override
        public List<OhlcvCandle> findLastNBySymbolBeforeDateAsc(String symbol, LocalDate before, int n) {
            return findBySymbol(symbol).stream().filter(c -> !c.date().isAfter(before)).limit(n).toList();
        }
    }
}
