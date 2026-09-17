package com.swingtrade.api.dto.strategy;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for {@code POST /api/backtest/compare} (plan §6.8).
 *
 * @param variants   the variant/version pairs to compare
 * @param start      evaluation range start (inclusive)
 * @param end        evaluation range end (inclusive)
 * @param symbols    optional explicit symbol universe; when null/empty, every symbol with candle
 *                   history is used
 * @param walkForward optional walk-forward config; when null, a single plain backtest is run per
 *                    variant instead of rolling folds
 * @param costsOn    when false, brokerage/slippage are zeroed out for an idealised comparison
 */
public record BacktestCompareRequest(
    List<VariantRef> variants,
    LocalDate start,
    LocalDate end,
    List<String> symbols,
    WalkForwardRequest walkForward,
    Boolean costsOn
) {
    public boolean costsEnabled() {
        return costsOn == null || costsOn;
    }

    public record VariantRef(String id, Integer version) {
    }

    public record WalkForwardRequest(Integer trainM, Integer testM, Integer stepM, Integer holdoutM,
                                      Boolean unlockHoldout) {
    }
}
