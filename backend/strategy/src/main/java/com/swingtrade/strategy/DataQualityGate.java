package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Blocks (rather than warns about) symbols with poor-quality candle data before they enter a
 * {@link PortfolioBacktestEngine} run (plan §6.6). A symbol failing any gate is excluded from the
 * run entirely and logged in {@code PortfolioBacktestResult.excludedSymbols()} - it never
 * silently contributes bad trades to the leaderboard.
 *
 * <p><b>Corporate-action limitation (documented per plan A7):</b> the repository has no
 * corporate-action / stock-split table (verified by grepping for "corporate action", "split" and
 * {@code OhlcvCandle}/migrations - none exist beyond {@link OhlcvCandle#adjustedForAnalysis()},
 * which only fixes bonus-issue face-value jumps, not general splits/bonuses at the source). This
 * gate's split check is therefore a <b>best-effort heuristic</b>: a single-bar close-to-close move
 * larger than 40% is flagged as a suspected unadjusted split, whether or not it is a genuine split.
 * A real implementation would cross-check against a corporate-action feed; that is deferred to
 * whenever RS_NIFTY (plan §5.4) requires ingesting a benchmark/corporate-action data source
 * (plan finding F16).
 */
public final class DataQualityGate {

    /**
     * Missing-bars threshold: symbol excluded if more than this fraction of expected bars are
     * absent. {@code checkMissingBars}'s expected-bar count is Mon-Fri calendar days, not NSE
     * trading days, so real NSE holidays (~45-50/year) always show up as "missing" bars - roughly
     * 6% of weekdays over a multi-year window. 0.02 rejected every symbol in practice; 0.08 keeps
     * a margin above that baseline holiday rate while still catching genuine ingestion gaps.
     */
    static final double MAX_MISSING_BARS_FRACTION = 0.08;

    /** Single-bar move fraction beyond which an unadjusted-split heuristic fires. */
    static final BigDecimal SPLIT_JUMP_THRESHOLD = BigDecimal.valueOf(0.40);

    private DataQualityGate() {
    }

    /**
     * Evaluates a symbol's candle history over {@code [start, end]}.
     *
     * @return empty if the symbol passes every gate, or the exclusion reason otherwise
     */
    public static Optional<String> evaluate(String symbol, List<OhlcvCandle> chronologicalCandlesInWindow,
                                             LocalDate start, LocalDate end) {
        if (chronologicalCandlesInWindow == null || chronologicalCandlesInWindow.isEmpty()) {
            return Optional.of("No candles in evaluation window");
        }

        Optional<String> priceCheck = checkPrices(chronologicalCandlesInWindow);
        if (priceCheck.isPresent()) {
            return priceCheck;
        }

        Optional<String> missingBarsCheck = checkMissingBars(chronologicalCandlesInWindow, start, end);
        if (missingBarsCheck.isPresent()) {
            return missingBarsCheck;
        }

        return checkUnadjustedSplitJumps(chronologicalCandlesInWindow);
    }

    private static Optional<String> checkPrices(List<OhlcvCandle> candles) {
        for (OhlcvCandle candle : candles) {
            if (isNonPositive(candle.open()) || isNonPositive(candle.high())
                || isNonPositive(candle.low()) || isNonPositive(candle.close())) {
                return Optional.of("Zero or negative price on " + candle.date());
            }
        }
        return Optional.empty();
    }

    private static boolean isNonPositive(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }

    /**
     * Expected bar count approximated as the count of Mon-Fri calendar days in the window - not
     * exchange-holiday-aware, so this is a conservative (slightly over-counts expected bars, i.e.
     * slightly stricter than the true NSE trading calendar) approximation, documented here rather
     * than silently assumed.
     */
    private static Optional<String> checkMissingBars(List<OhlcvCandle> candles, LocalDate start, LocalDate end) {
        long expectedBars = countWeekdays(start, end);
        long actualBars = candles.stream()
            .filter(c -> !c.date().isBefore(start) && !c.date().isAfter(end))
            .count();
        if (expectedBars == 0) {
            return Optional.empty();
        }
        double missingFraction = 1.0 - (actualBars / (double) expectedBars);
        if (missingFraction > MAX_MISSING_BARS_FRACTION) {
            return Optional.of(String.format(
                "Missing bars %.1f%% exceeds %.1f%% threshold (expected ~%d, found %d)",
                missingFraction * 100.0, MAX_MISSING_BARS_FRACTION * 100.0, expectedBars, actualBars));
        }
        return Optional.empty();
    }

    private static long countWeekdays(LocalDate start, LocalDate end) {
        long count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    private static Optional<String> checkUnadjustedSplitJumps(List<OhlcvCandle> chronologicalCandles) {
        for (int i = 1; i < chronologicalCandles.size(); i++) {
            BigDecimal prevClose = chronologicalCandles.get(i - 1).close();
            BigDecimal close = chronologicalCandles.get(i).close();
            if (prevClose.signum() == 0) {
                continue;
            }
            BigDecimal move = close.subtract(prevClose).abs().divide(prevClose, 6, java.math.RoundingMode.HALF_UP);
            if (move.compareTo(SPLIT_JUMP_THRESHOLD) > 0) {
                return Optional.of(String.format(
                    "Suspected unadjusted split/bonus: %.1f%% single-bar move on %s (heuristic, no "
                        + "corporate-action feed available to confirm)",
                    move.doubleValue() * 100.0, chronologicalCandles.get(i).date()));
            }
        }
        return Optional.empty();
    }
}
