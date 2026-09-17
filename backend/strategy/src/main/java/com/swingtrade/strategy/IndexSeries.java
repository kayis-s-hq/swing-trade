package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A minimal, read-only view onto a market index's (e.g. Nifty 50) daily close series and its
 * EMA, used by {@code regimeGate} (plan §5.5).
 *
 * <p><b>Judgment call (plan §5.5 / finding F16):</b> this phase does not plumb real Nifty candle
 * data into {@link MarketContext} - doing so would touch the data/ingestion layer (a market-data
 * client fetching {@code ^NSEI} per assumption A3), which is a bigger, separate lift shared with
 * the deferred RS_NIFTY type (plan §5.4). Instead, {@code regimeGate}'s logic is implemented
 * fully against this small interface, which is trivially fakeable in unit tests (see
 * {@code GateEvaluatorTest}). Wiring a real implementation (backed by ingested Nifty candles,
 * likely as an optional field the orchestrator populates on {@link MarketContext}) is deferred to
 * whichever later phase builds RS_NIFTY / live shadow execution (plan §7), since both need the
 * same underlying Nifty series.
 */
public interface IndexSeries {

    /** True if the index has at least one bar on or before {@code date}. */
    boolean hasDataOn(LocalDate date);

    /** The index's close on {@code date} (or the most recent prior trading day). */
    BigDecimal close(LocalDate date);

    /** The index's EMA(period) on {@code date}. */
    BigDecimal ema(int period, LocalDate date);

    /** The index's EMA(period) as of {@code lookbackDays} calendar days before {@code date}. */
    BigDecimal emaLookback(int period, LocalDate date, int lookbackDays);
}
