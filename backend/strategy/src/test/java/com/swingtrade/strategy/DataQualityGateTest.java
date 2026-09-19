package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataQualityGate")
class DataQualityGateTest {

    @Test
    @DisplayName("passes clean daily candles with no gaps")
    void passesCleanData() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = start.plusWeeks(4);
        List<OhlcvCandle> candles = weekdayCandles(start, end, 100.0, 0.0);
        assertThat(DataQualityGate.evaluate("CLEAN", candles, start, end)).isEmpty();
    }

    @Test
    @DisplayName("blocks a symbol with a zero/negative price")
    void blocksNonPositivePrice() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = start.plusWeeks(2);
        List<OhlcvCandle> candles = new ArrayList<>(weekdayCandles(start, end, 100.0, 0.0));
        OhlcvCandle bad = candles.get(3);
        candles.set(3, new OhlcvCandle(bad.symbol(), bad.date(), bad.open(), bad.high(), bad.low(),
            BigDecimal.ZERO, bad.volume(), bad.adjClose()));
        Optional<String> reason = DataQualityGate.evaluate("BADPRICE", candles, start, end);
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("Zero or negative price");
    }

    @Test
    @DisplayName("blocks a symbol with more than 2% missing bars in the window")
    void blocksMissingBars() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = start.plusWeeks(8); // ~40 weekdays expected
        List<OhlcvCandle> candles = weekdayCandles(start, end, 100.0, 0.0);
        // Drop 6 of ~40 bars (~15% missing), well above the 2% threshold.
        List<OhlcvCandle> sparse = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            if (i % 7 != 0) {
                sparse.add(candles.get(i));
            }
        }
        Optional<String> reason = DataQualityGate.evaluate("SPARSE", sparse, start, end);
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("Missing bars");
    }

    @Test
    @DisplayName("flags a >40% single-bar move as a suspected unadjusted split (heuristic)")
    void flagsSuspectedSplit() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = start.plusWeeks(2);
        List<OhlcvCandle> candles = new ArrayList<>(weekdayCandles(start, end, 100.0, 0.0));
        // Halve the price on one bar without changing adjClose - a classic unadjusted 2:1 split.
        OhlcvCandle victim = candles.get(5);
        BigDecimal halved = victim.close().multiply(BigDecimal.valueOf(0.5));
        candles.set(5, new OhlcvCandle(victim.symbol(), victim.date(), halved, halved, halved, halved,
            victim.volume(), halved));
        Optional<String> reason = DataQualityGate.evaluate("SPLIT", candles, start, end);
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("Suspected unadjusted split");
    }

    private static List<OhlcvCandle> weekdayCandles(LocalDate start, LocalDate end, double basePrice, double drift) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = basePrice;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() >= 6) {
                continue;
            }
            BigDecimal p = BigDecimal.valueOf(price);
            candles.add(OhlcvCandle.of("SYM", d, p, p.multiply(BigDecimal.valueOf(1.01)),
                p.multiply(BigDecimal.valueOf(0.99)), p, 1_000_000L));
            price += drift;
        }
        return candles;
    }
}
