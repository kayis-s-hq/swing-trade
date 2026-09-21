package com.swingtrade.strategy;

import java.util.Map;

/**
 * The outcome of resolving a persisted {@code StrategyConfig} to something the live pipeline can
 * run. Resolution never yields {@code null}: an unknown type or invalid params is an explicit
 * {@link Unresolved} carrying the reason, so callers can report a skip instead of silently
 * dropping the variant.
 */
public sealed interface ResolvedStrategy {

    /** A {@link SignalStrategy} plus its validated, default-filled params. */
    record Signal(SignalStrategy strategy, StrategyParamsView params, Map<String, Object> resolvedParams)
        implements ResolvedStrategy {
    }

    /** A legacy {@link TradingStrategy} (e.g. {@code PRICE_ACTION_3_OF_4}). */
    record Legacy(TradingStrategy strategy) implements ResolvedStrategy {
    }

    /** The config cannot be run; {@code reason} is human-readable and safe to log/show. */
    record Unresolved(String reason) implements ResolvedStrategy {
    }
}
