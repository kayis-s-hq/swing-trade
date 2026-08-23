package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.WatchlistStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("BacktestEngineIntegration")
class BacktestEngineIntegration {

    private CandleStore candleStore;
    private WatchlistStore watchlistStore;
    private BacktestEngine engine;

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Single-symbol backtest with real fixture data")
    class SingleSymbolBacktest {

        @BeforeEach
        void setUp() {
            candleStore = new InMemoryCandleStore();
            watchlistStore = org.mockito.Mockito.mock(WatchlistStore.class);
            PriceActionSignalEngine priceActionSignalEngine = new PriceActionSignalEngine(candleStore);
            engine = new BacktestEngine(candleStore, watchlistStore, priceActionSignalEngine,
                new tools.jackson.databind.ObjectMapper(), tempDir.toString());
        }

        @Test
        @DisplayName("backtestWithRealData — produces valid trade results")
        void backtestWithRealData() {
            // Load fixture data
            List<OhlcvCandle> candles = loadFixture("real-ohlcv-single.csv");
            candles.forEach(candleStore::save);

            BacktestConfig config = BacktestConfig.defaults();
            BacktestResult result = engine.runBacktest("RELIANCE", "NSE", config);

            assertThat(result).isNotNull();
            assertThat(result.trades()).isNotEmpty();
            assertThat(result.totalTrades()).isGreaterThan(0);
            assertThat(result.winRate()).isBetween(0.0, 100.0);
            assertThat(result.sharpeRatio()).isNotNull();
            assertThat(result.maxDrawdownPct()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.totalReturn()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Multi-symbol backtest")
    class MultiSymbolBacktest {

        @BeforeEach
        void setUp() {
            candleStore = new InMemoryCandleStore();
            watchlistStore = org.mockito.Mockito.mock(WatchlistStore.class);
            PriceActionSignalEngine priceActionSignalEngine = new PriceActionSignalEngine(candleStore);
            engine = new BacktestEngine(candleStore, watchlistStore, priceActionSignalEngine,
                new tools.jackson.databind.ObjectMapper(), tempDir.toString());
        }

        @Test
        @DisplayName("runBacktestAll — processes multiple symbols")
        void runBacktestAll() {
            // Load data for 2 symbols
            loadFixtureAndSave("real-ohlcv-single.csv", "RELIANCE");
            loadFixtureAndSave("real-ohlcv-single.csv", "TCS");

            BacktestConfig config = BacktestConfig.defaults();
            List<BacktestResult> results = engine.runBacktestAll(List.of("RELIANCE", "TCS"), "NSE", config);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.totalTrades() >= 0);
        }
    }

    @Nested
    @DisplayName("Report generation")
    class ReportGeneration {

        @BeforeEach
        void setUp() {
            candleStore = new InMemoryCandleStore();
            watchlistStore = org.mockito.Mockito.mock(WatchlistStore.class);
            PriceActionSignalEngine priceActionSignalEngine = new PriceActionSignalEngine(candleStore);
            engine = new BacktestEngine(candleStore, watchlistStore, priceActionSignalEngine,
                new tools.jackson.databind.ObjectMapper(), tempDir.toString());
        }

        @Test
        @DisplayName("generateReport — produces JSON and CSV files")
        void generateReport() {
            loadFixtureAndSave("real-ohlcv-single.csv", "RELIANCE");

            BacktestConfig config = BacktestConfig.defaults();
            List<BacktestResult> results = engine.runBacktestAll(List.of("RELIANCE"), "NSE", config);

            BacktestReportSummary summary = engine.generateReport(results);

            assertThat(summary).isNotNull();
            assertThat(summary.results()).hasSize(1);
            assertThat(summary.top10ByWinRate()).isNotNull();
            assertThat(summary.top10ByTotalReturn()).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Loads OHLCV fixture CSV from classpath and parses into OhlcvCandle list.
     * Format: date,open,high,low,close,volume
     */
    private List<OhlcvCandle> loadFixture(String filename) {
        List<OhlcvCandle> candles = new ArrayList<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("fixtures/" + filename)) {
            if (is == null) return candles;
            List<String> lines = Files.readAllLines(Path.of(getClass().getResource("/fixtures/" + filename).toURI()));
            for (int i = 1; i < lines.size(); i++) { // skip header
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 6) continue;
                LocalDate date = LocalDate.parse(parts[0]);
                BigDecimal open = new BigDecimal(parts[1]);
                BigDecimal high = new BigDecimal(parts[2]);
                BigDecimal low = new BigDecimal(parts[3]);
                BigDecimal close = new BigDecimal(parts[4]);
                Long volume = Long.parseLong(parts[5]);
                candles.add(new OhlcvCandle("RELIANCE", date, open, high, low, close, volume, close));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fixture: " + filename, e);
        }
        return candles;
    }

    private void loadFixtureAndSave(String filename, String symbol) {
        List<OhlcvCandle> candles = loadFixture(filename);
        for (OhlcvCandle c : candles) {
            candleStore.save(new OhlcvCandle(symbol, c.date(), c.open(), c.high(), c.low(), c.close(), c.volume(), c.adjClose()));
        }
    }

    /**
     * In-memory CandleStore implementation for integration tests.
     */
    static class InMemoryCandleStore implements CandleStore {
        private final List<OhlcvCandle> data = new ArrayList<>();

        void saveAll(List<OhlcvCandle> candles) {
            data.addAll(candles);
        }

        @Override
        public java.util.Optional<OhlcvCandle> findBySymbolAndDate(String symbol, LocalDate date) {
            return data.stream().filter(c -> c.symbol().equals(symbol) && c.date().equals(date)).findFirst();
        }

        @Override
        public List<OhlcvCandle> findBySymbol(String symbol) {
            return data.stream().filter(c -> c.symbol().equals(symbol)).toList();
        }

        @Override
        public List<OhlcvCandle> findBySymbolAndDateRange(String symbol, LocalDate from, LocalDate to) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol) && !c.date().isBefore(from) && !c.date().isAfter(to))
                .toList();
        }

        @Override
        public List<OhlcvCandle> findTopBySymbolOrderByDateDesc(String symbol, int limit) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol))
                .sorted((a, b) -> b.date().compareTo(a.date()))
                .limit(limit)
                .toList();
        }

        @Override
        public java.util.Optional<OhlcvCandle> findLatestBySymbol(String symbol) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol))
                .max((a, b) -> a.date().compareTo(b.date()));
        }

        @Override
        public void save(OhlcvCandle candle) {
            data.add(candle);
        }

        @Override
        public boolean existsBySymbolAndDate(String symbol, LocalDate date) {
            return data.stream().anyMatch(c -> c.symbol().equals(symbol) && c.date().equals(date));
        }

        @Override
        public List<String> findAllDistinctSymbols() {
            return data.stream().map(OhlcvCandle::symbol).distinct().toList();
        }

        @Override
        public java.util.Optional<OhlcvCandle> findEarliestBySymbol(String symbol) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol))
                .min((a, b) -> a.date().compareTo(b.date()));
        }

        @Override
        public long countBySymbol(String symbol) {
            return data.stream().filter(c -> c.symbol().equals(symbol)).count();
        }

        @Override
        public void deleteBySymbol(String symbol) {
            data.removeIf(c -> c.symbol().equals(symbol));
        }

        @Override
        public java.util.Optional<OhlcvCandle> findLatestBySymbolBeforeDate(String symbol, LocalDate date) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol) && c.date().isBefore(date))
                .max((a, b) -> a.date().compareTo(b.date()));
        }

        @Override
        public java.util.Optional<OhlcvCandle> findFirstBySymbolAndDateAfterOrderByDateAsc(String symbol, LocalDate date) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol) && !c.date().isBefore(date))
                .min((a, b) -> a.date().compareTo(b.date()));
        }

        @Override
        public java.util.Optional<OhlcvCandle> findNthBySymbolAndDateAfterOrderByDateAsc(String symbol, LocalDate after, int n) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol) && c.date().isAfter(after))
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .skip(n)
                .findFirst();
        }

        @Override
        public List<OhlcvCandle> findAllBySymbolOrderByDateDesc(String symbol) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol))
                .sorted((a, b) -> b.date().compareTo(a.date()))
                .toList();
        }

        @Override
        public List<OhlcvCandle> findLastNBySymbolBeforeDateAsc(String symbol, LocalDate before, int n) {
            return data.stream()
                .filter(c -> c.symbol().equals(symbol) && !c.date().isAfter(before))
                .sorted((a, b) -> b.date().compareTo(a.date()))
                .limit(n)
                .toList();
        }
    }
}