package com.swingtrade.data.service;

import com.swingtrade.domain.BenchmarkCandleSeries;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.BenchmarkDataAdapter;
import com.swingtrade.domain.store.CandleStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Historical NIFTY 50 adapter backed exclusively by persisted candles.
 *
 * <p>Data ingestion is intentionally separate from this adapter. In
 * particular, historical backtests cannot accidentally trigger a network
 * request when the benchmark series is missing.</p>
 */
@Service
public final class NiftyBenchmarkDataAdapter implements BenchmarkDataAdapter {

    public static final String NIFTY_50_SYMBOL = "NIFTY50";
    public static final int MAX_WINDOW_DAYS = 3660;

    private final CandleStore candleStore;

    public NiftyBenchmarkDataAdapter(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    @Override
    public Optional<BenchmarkCandleSeries> findNifty50(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) > MAX_WINDOW_DAYS) {
            return Optional.empty();
        }

        List<OhlcvCandle> candles = candleStore.findBySymbolAndDateRange(NIFTY_50_SYMBOL, from, to)
                .stream()
                .filter(candle -> usable(candle, from, to))
                .sorted(Comparator.comparing(OhlcvCandle::date))
                .toList();
        if (candles.size() < 2 || hasDuplicateDates(candles)) {
            return Optional.empty();
        }
        return Optional.of(new BenchmarkCandleSeries(NIFTY_50_SYMBOL, from, to, candles));
    }

    private static boolean usable(OhlcvCandle candle, LocalDate from, LocalDate to) {
        return candle != null && candle.date() != null
                && !candle.date().isBefore(from) && !candle.date().isAfter(to)
                && NIFTY_50_SYMBOL.equals(candle.symbol())
                && candle.close() != null && candle.close().signum() > 0;
    }

    private static boolean hasDuplicateDates(List<OhlcvCandle> candles) {
        Set<LocalDate> dates = new HashSet<>();
        return candles.stream().map(OhlcvCandle::date).anyMatch(date -> !dates.add(date));
    }
}
