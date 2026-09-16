package com.swingtrade.data.service;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NiftyBenchmarkDataAdapterTest {

    private final CandleStore candleStore = mock(CandleStore.class);
    private final NiftyBenchmarkDataAdapter adapter = new NiftyBenchmarkDataAdapter(candleStore);

    @Test
    void returnsPersistedCandlesInChronologicalOrder() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 3);
        OhlcvCandle later = candle("NIFTY50", to, "101");
        OhlcvCandle earlier = candle("NIFTY50", from, "100");
        when(candleStore.findBySymbolAndDateRange("NIFTY50", from, to))
                .thenReturn(List.of(later, earlier));

        var result = adapter.findNifty50(from, to);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().candles()).containsExactly(earlier, later);
        verify(candleStore).findBySymbolAndDateRange("NIFTY50", from, to);
    }

    @Test
    void failsClosedWhenPersistedBenchmarkIsAbsent() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 3);
        when(candleStore.findBySymbolAndDateRange("NIFTY50", from, to)).thenReturn(List.of());

        assertThat(adapter.findNifty50(from, to)).isEmpty();
    }

    @Test
    void failsClosedWhenOnlyOneObservationIsAvailable() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 3);
        when(candleStore.findBySymbolAndDateRange("NIFTY50", from, to))
                .thenReturn(List.of(candle("NIFTY50", from, "100")));

        assertThat(adapter.findNifty50(from, to)).isEmpty();
    }

    @Test
    void ignoresOutOfWindowAndWrongSymbolObservations() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 3);
        when(candleStore.findBySymbolAndDateRange("NIFTY50", from, to)).thenReturn(List.of(
                candle("OTHER", from, "100"),
                candle("NIFTY50", from.minusDays(1), "99"),
                candle("NIFTY50", from, "100"),
                candle("NIFTY50", to, "101")));

        assertThat(adapter.findNifty50(from, to)).isPresent();
        assertThat(adapter.findNifty50(from, to).orElseThrow().candles())
                .extracting(OhlcvCandle::symbol)
                .containsOnly("NIFTY50");
    }

    @Test
    void failsClosedOnDuplicateObservationDates() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        when(candleStore.findBySymbolAndDateRange("NIFTY50", date, date.plusDays(1)))
                .thenReturn(List.of(candle("NIFTY50", date, "100"),
                        candle("NIFTY50", date, "101")));

        assertThat(adapter.findNifty50(date, date.plusDays(1))).isEmpty();
    }

    @Test
    void rejectsInvalidOrUnboundedWindowsWithoutReadingStore() {
        LocalDate date = LocalDate.of(2025, 1, 1);

        assertThat(adapter.findNifty50(date, date.minusDays(1))).isEmpty();
        assertThat(adapter.findNifty50(date, date.plusDays(NiftyBenchmarkDataAdapter.MAX_WINDOW_DAYS + 1L)))
                .isEmpty();
        assertThat(adapter.findNifty50(null, date)).isEmpty();

        verifyNoInteractions(candleStore);
    }

    @Test
    void ignoresMalformedPersistedCandlesAndFailsClosedIfNoneAreUsable() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        when(candleStore.findBySymbolAndDateRange("NIFTY50", date, date))
                .thenReturn(Arrays.asList(null, candle("NIFTY50", date, "0")));

        assertThat(adapter.findNifty50(date, date)).isEmpty();
    }

    private static OhlcvCandle candle(String symbol, LocalDate date, String close) {
        BigDecimal value = new BigDecimal(close);
        return OhlcvCandle.of(symbol, date, value, value, value, value, 1L);
    }
}
